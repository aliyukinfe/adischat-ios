-- AdisGebeya production catalog variants and category-driven attributes.
-- Additive by design: legacy products keep their existing parent inventory row.

create table if not exists public.shop_attribute_definitions (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  name text not null,
  display_name text not null,
  data_type text not null default 'text',
  input_type text not null default 'text',
  is_variant_option boolean not null default false,
  is_filterable boolean not null default false,
  is_searchable boolean not null default false,
  allows_multiple boolean not null default false,
  unit text,
  suggestions jsonb not null default '[]'::jsonb,
  sort_order integer not null default 0,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_attribute_definitions_code_format check (code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  constraint shop_attribute_definitions_data_type check (data_type in ('text','number','boolean','choice','color')),
  constraint shop_attribute_definitions_input_type check (input_type in ('text','number','toggle','select','chips','color')),
  constraint shop_attribute_definitions_status check (status in ('active','inactive')),
  unique (code)
);

create table if not exists public.shop_category_attributes (
  category_id uuid not null references public.shop_categories(id) on delete cascade,
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id) on delete cascade,
  required boolean not null default false,
  variant_capable boolean not null default false,
  filterable boolean not null default false,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (category_id, attribute_definition_id)
);

create table if not exists public.shop_product_attribute_values (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id) on delete restrict,
  value_text text,
  value_number numeric,
  value_boolean boolean,
  value_json jsonb,
  unit text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_product_attribute_one_value check (
    num_nonnulls(value_text, value_number, value_boolean, value_json) = 1
  ),
  unique (product_id, attribute_definition_id)
);

create table if not exists public.shop_product_options (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id) on delete restrict,
  code text not null,
  name text not null,
  position integer not null default 0,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_product_options_code_format check (code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  constraint shop_product_options_status check (status in ('active','inactive')),
  unique (product_id, attribute_definition_id),
  unique (product_id, code)
);

create table if not exists public.shop_product_option_values (
  id uuid primary key default gen_random_uuid(),
  product_option_id uuid not null references public.shop_product_options(id) on delete cascade,
  code text not null,
  value text not null,
  display_value text not null,
  color_hex text,
  position integer not null default 0,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_product_option_values_code_format check (code ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'),
  constraint shop_product_option_values_color check (color_hex is null or color_hex ~ '^#[0-9A-Fa-f]{6}$'),
  constraint shop_product_option_values_status check (status in ('active','inactive')),
  unique (product_option_id, code)
);

alter table public.shop_product_variants add column if not exists store_id uuid references public.shop_stores(id) on delete cascade;
alter table public.shop_product_variants add column if not exists barcode text;
alter table public.shop_product_variants add column if not exists option_signature text;
alter table public.shop_product_variants add column if not exists manage_inventory boolean not null default true;
alter table public.shop_product_variants add column if not exists weight_grams integer;
alter table public.shop_product_variants add column if not exists primary_image_id uuid references public.shop_product_images(id) on delete set null;
alter table public.shop_products add column if not exists has_variants boolean not null default false;
alter table public.shop_products add column if not exists price_max_minor integer;
alter table public.shop_products add column if not exists aggregate_available_quantity integer;
alter table public.shop_conversations add column if not exists product_variant_id uuid
  references public.shop_product_variants(id) on delete set null;
alter table public.shop_products
  add constraint shop_products_price_max_nonnegative check (price_max_minor is null or price_max_minor >= 0) not valid;
alter table public.shop_products validate constraint shop_products_price_max_nonnegative;

update public.shop_product_variants v
set store_id = p.store_id
from public.shop_products p
where p.id = v.product_id and v.store_id is null;

alter table public.shop_product_variants
  add constraint shop_product_variants_price_nonnegative check (price_minor >= 0) not valid;
alter table public.shop_product_variants validate constraint shop_product_variants_price_nonnegative;
alter table public.shop_product_variants
  add constraint shop_product_variants_compare_price_nonnegative check (compare_at_price_minor is null or compare_at_price_minor >= 0) not valid;
alter table public.shop_product_variants validate constraint shop_product_variants_compare_price_nonnegative;
alter table public.shop_product_variants
  add constraint shop_product_variants_weight_nonnegative check (weight_grams is null or weight_grams >= 0) not valid;
alter table public.shop_product_variants validate constraint shop_product_variants_weight_nonnegative;

create table if not exists public.shop_variant_option_values (
  variant_id uuid not null references public.shop_product_variants(id) on delete cascade,
  product_option_id uuid not null references public.shop_product_options(id) on delete restrict,
  product_option_value_id uuid not null references public.shop_product_option_values(id) on delete restrict,
  created_at timestamptz not null default now(),
  primary key (variant_id, product_option_id),
  unique (variant_id, product_option_value_id)
);

create table if not exists public.shop_variant_media (
  variant_id uuid not null references public.shop_product_variants(id) on delete cascade,
  product_image_id uuid not null references public.shop_product_images(id) on delete cascade,
  position integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (variant_id, product_image_id)
);

alter table public.shop_inventory add column if not exists inventory_scope text not null default 'sellable';
alter table public.shop_inventory
  add constraint shop_inventory_scope_check check (inventory_scope in ('sellable','aggregate')) not valid;
alter table public.shop_inventory validate constraint shop_inventory_scope_check;
alter table public.shop_inventory
  add constraint shop_inventory_reserved_not_above_available check (quantity_reserved <= quantity_available) not valid;
alter table public.shop_inventory validate constraint shop_inventory_reserved_not_above_available;

create table if not exists public.shop_inventory_events (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  inventory_id uuid not null references public.shop_inventory(id) on delete restrict,
  event_type text not null,
  quantity_delta integer not null,
  quantity_before integer not null,
  quantity_after integer not null,
  reserved_before integer not null default 0,
  reserved_after integer not null default 0,
  reason text,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  request_id text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint shop_inventory_events_quantity_nonnegative check (quantity_before >= 0 and quantity_after >= 0 and reserved_before >= 0 and reserved_after >= 0),
  constraint shop_inventory_events_type check (event_type in (
    'SELLER_MANUAL_ADJUSTMENT','ORDER_RESERVATION','RESERVATION_RELEASED','ORDER_CONFIRMED',
    'ORDER_CANCELLED','ORDER_EXPIRED','RETURN_RESTOCK','ADMIN_ADJUSTMENT','CATALOG_INITIAL_STOCK'
  ))
);

create unique index if not exists shop_product_variants_product_signature_uidx
  on public.shop_product_variants(product_id, option_signature)
  where option_signature is not null;
create unique index if not exists shop_product_variants_store_sku_uidx
  on public.shop_product_variants(store_id, lower(sku))
  where sku is not null and status <> 'archived';
create index if not exists shop_category_attributes_category_idx
  on public.shop_category_attributes(category_id, sort_order);
create index if not exists shop_product_attribute_values_product_idx
  on public.shop_product_attribute_values(product_id);
create index if not exists shop_product_options_product_idx
  on public.shop_product_options(product_id, position);
create index if not exists shop_product_option_values_option_idx
  on public.shop_product_option_values(product_option_id, position);
create index if not exists shop_variant_option_values_value_idx
  on public.shop_variant_option_values(product_option_value_id);
create index if not exists shop_inventory_events_variant_idx
  on public.shop_inventory_events(variant_id, created_at desc);
create index if not exists shop_conversations_product_variant_idx
  on public.shop_conversations(product_variant_id)
  where product_variant_id is not null;
create unique index if not exists shop_inventory_events_request_uidx
  on public.shop_inventory_events(inventory_id, request_id, event_type)
  where request_id is not null;
create unique index if not exists shop_cart_items_legacy_uidx
  on public.shop_cart_items(cart_id, product_id)
  where variant_id is null;
create unique index if not exists shop_inventory_parent_uidx
  on public.shop_inventory(product_id)
  where variant_id is null;

alter table public.shop_attribute_definitions enable row level security;
alter table public.shop_category_attributes enable row level security;
alter table public.shop_product_attribute_values enable row level security;
alter table public.shop_product_options enable row level security;
alter table public.shop_product_option_values enable row level security;
alter table public.shop_variant_option_values enable row level security;
alter table public.shop_variant_media enable row level security;
alter table public.shop_inventory_events enable row level security;

revoke all on table public.shop_attribute_definitions from anon, authenticated;
revoke all on table public.shop_category_attributes from anon, authenticated;
revoke all on table public.shop_product_attribute_values from anon, authenticated;
revoke all on table public.shop_product_options from anon, authenticated;
revoke all on table public.shop_product_option_values from anon, authenticated;
revoke all on table public.shop_variant_option_values from anon, authenticated;
revoke all on table public.shop_variant_media from anon, authenticated;
revoke all on table public.shop_inventory_events from anon, authenticated;
grant all on table public.shop_attribute_definitions to service_role;
grant all on table public.shop_category_attributes to service_role;
grant all on table public.shop_product_attribute_values to service_role;
grant all on table public.shop_product_options to service_role;
grant all on table public.shop_product_option_values to service_role;
grant all on table public.shop_variant_option_values to service_role;
grant all on table public.shop_variant_media to service_role;
grant all on table public.shop_inventory_events to service_role;

insert into public.shop_attribute_definitions
  (code,name,display_name,data_type,input_type,is_variant_option,is_filterable,is_searchable,allows_multiple,unit,suggestions,sort_order)
values
  ('BRAND','brand','Brand','text','text',false,true,true,false,null,'[]',10),
  ('MODEL','model','Model','text','text',false,true,true,false,null,'[]',20),
  ('COLOR','color','Color','color','color',true,true,true,true,null,'["Black","White","Red","Grey","Blue","Green","Gold","Silver"]',30),
  ('SIZE','size','Size','choice','chips',true,true,true,true,null,'["XS","S","M","L","XL","XXL"]',40),
  ('STORAGE','storage','Storage','choice','chips',true,true,true,true,null,'["64GB","128GB","256GB","512GB","1TB"]',50),
  ('RAM','ram','RAM','choice','chips',true,true,true,true,null,'["4GB","6GB","8GB","12GB","16GB","32GB"]',60),
  ('MATERIAL','material','Material','text','text',true,true,true,false,null,'[]',70),
  ('SHADE','shade','Shade','color','color',true,true,true,true,null,'[]',80),
  ('CAPACITY','capacity','Capacity','choice','chips',true,true,false,true,null,'[]',90),
  ('WEIGHT','weight','Weight','number','number',true,true,false,false,'g','[]',100),
  ('PACK_SIZE','pack_size','Pack size','choice','chips',true,true,false,true,null,'[]',110),
  ('FLAVOR','flavor','Flavor','choice','chips',true,true,true,true,null,'[]',120),
  ('PATTERN','pattern','Pattern','choice','chips',true,true,false,true,null,'[]',130),
  ('SCREEN_SIZE','screen_size','Screen size','number','number',false,true,true,false,'inch','[]',140),
  ('BATTERY_CAPACITY','battery_capacity','Battery capacity','number','number',false,true,true,false,'mAh','[]',150),
  ('PROCESSOR','processor','Processor','text','text',true,true,true,false,null,'[]',160),
  ('NETWORK','network','Network','choice','select',false,true,true,false,null,'["4G","5G"]',170),
  ('WARRANTY','warranty','Warranty','text','text',false,false,true,false,null,'[]',180),
  ('VOLUME','volume','Volume','choice','chips',true,true,false,true,'ml','[]',190)
on conflict (code) do update set
  display_name=excluded.display_name,
  data_type=excluded.data_type,
  input_type=excluded.input_type,
  is_variant_option=excluded.is_variant_option,
  is_filterable=excluded.is_filterable,
  is_searchable=excluded.is_searchable,
  allows_multiple=excluded.allows_multiple,
  unit=excluded.unit,
  suggestions=excluded.suggestions,
  sort_order=excluded.sort_order,
  status='active',
  updated_at=now();

with category_rules(category_slug, attribute_code, required, variant_capable, filterable, sort_order) as (
  values
    ('electronics','BRAND',true,false,true,10),('electronics','MODEL',true,false,true,20),
    ('electronics','COLOR',false,true,true,30),('electronics','STORAGE',false,true,true,40),
    ('electronics','RAM',false,true,true,50),('electronics','SCREEN_SIZE',false,false,true,60),
    ('electronics','BATTERY_CAPACITY',false,false,true,70),('electronics','PROCESSOR',false,true,true,80),
    ('electronics','NETWORK',false,false,true,90),('electronics','WARRANTY',false,false,false,100),
    ('fashion','MATERIAL',false,true,true,10),('fashion','COLOR',false,true,true,20),
    ('fashion','SIZE',false,true,true,30),('fashion','PATTERN',false,true,true,40),
    ('home','COLOR',false,true,true,10),('home','MATERIAL',false,true,true,20),
    ('home','SIZE',false,true,true,30),('home','CAPACITY',false,true,true,40),
    ('beauty','BRAND',false,false,true,10),('beauty','SHADE',false,true,true,20),
    ('beauty','VOLUME',false,true,true,30),('beauty','PACK_SIZE',false,true,true,40),
    ('groceries','FLAVOR',false,true,true,10),('groceries','WEIGHT',false,true,true,20),
    ('groceries','PACK_SIZE',false,true,true,30),
    ('sports','COLOR',false,true,true,10),('sports','SIZE',false,true,true,20),
    ('sports','MATERIAL',false,true,true,30),
    ('toys','COLOR',false,true,true,10),('toys','SIZE',false,true,true,20),
    ('books','BRAND',false,false,true,10)
)
insert into public.shop_category_attributes(category_id,attribute_definition_id,required,variant_capable,filterable,sort_order)
select c.id,a.id,r.required,r.variant_capable,r.filterable,r.sort_order
from category_rules r
join public.shop_categories c on c.slug=r.category_slug
join public.shop_attribute_definitions a on a.code=r.attribute_code
on conflict (category_id,attribute_definition_id) do update set
  required=excluded.required,
  variant_capable=excluded.variant_capable,
  filterable=excluded.filterable,
  sort_order=excluded.sort_order;

create or replace function public.adishop_variant_signature(p_options jsonb)
returns text
language sql
immutable
strict
as $$
  select string_agg(upper(key) || '=' || upper(value), '|' order by upper(key))
  from jsonb_each_text(p_options)
$$;

create or replace function public.adishop_validate_variant_option_assignment()
returns trigger
language plpgsql
set search_path = public
as $$
declare v_product_id uuid;
declare o_product_id uuid;
declare ov_option_id uuid;
begin
  select product_id into v_product_id from public.shop_product_variants where id=new.variant_id;
  select product_id into o_product_id from public.shop_product_options where id=new.product_option_id;
  select product_option_id into ov_option_id from public.shop_product_option_values where id=new.product_option_value_id;
  if v_product_id is null or o_product_id is distinct from v_product_id or ov_option_id is distinct from new.product_option_id then
    raise exception 'variant-option-product-mismatch';
  end if;
  return new;
end;
$$;

drop trigger if exists shop_variant_option_values_validate on public.shop_variant_option_values;
create trigger shop_variant_option_values_validate
before insert or update on public.shop_variant_option_values
for each row execute function public.adishop_validate_variant_option_assignment();

revoke all on function public.adishop_variant_signature(jsonb) from public;
revoke all on function public.adishop_validate_variant_option_assignment() from public;
grant execute on function public.adishop_variant_signature(jsonb) to service_role;

create or replace function public.adishop_category_attribute_schema(p_category_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', a.id,
    'code', a.code,
    'name', a.name,
    'displayName', a.display_name,
    'dataType', a.data_type,
    'inputType', a.input_type,
    'required', ca.required,
    'variantCapable', ca.variant_capable,
    'filterable', ca.filterable,
    'allowsMultiple', a.allows_multiple,
    'unit', a.unit,
    'suggestions', a.suggestions,
    'sortOrder', ca.sort_order
  ) order by ca.sort_order, a.sort_order), '[]'::jsonb)
  from public.shop_category_attributes ca
  join public.shop_attribute_definitions a on a.id=ca.attribute_definition_id
  join public.shop_categories c on c.id=ca.category_id
  where ca.category_id=p_category_id and c.is_active=true and a.status='active'
$$;

create or replace function public.adishop_product_catalog_payload(p_product_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'title', p.title,
    'slug', p.slug,
    'brand', p.brand,
    'categoryId', p.category_id,
    'categorySlug', p.category_slug,
    'description', p.description,
    'priceMinor', p.price_minor,
    'compareAtPriceMinor', p.compare_at_price_minor,
    'stockStatus', p.stock_status,
    'specifications', coalesce((
      select jsonb_agg(jsonb_build_object(
        'code', a.code,
        'name', a.display_name,
        'valueText', pav.value_text,
        'valueNumber', pav.value_number,
        'valueBoolean', pav.value_boolean,
        'valueJson', pav.value_json,
        'unit', coalesce(pav.unit,a.unit)
      ) order by a.sort_order)
      from public.shop_product_attribute_values pav
      join public.shop_attribute_definitions a on a.id=pav.attribute_definition_id
      where pav.product_id=p.id
    ), '[]'::jsonb),
    'options', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', po.id,
        'code', po.code,
        'name', po.name,
        'position', po.position,
        'values', coalesce((
          select jsonb_agg(jsonb_build_object(
            'id', pov.id,
            'code', pov.code,
            'value', pov.value,
            'displayValue', pov.display_value,
            'colorHex', pov.color_hex,
            'position', pov.position
          ) order by pov.position,pov.display_value)
          from public.shop_product_option_values pov
          where pov.product_option_id=po.id and pov.status='active'
        ), '[]'::jsonb)
      ) order by po.position,po.name)
      from public.shop_product_options po
      where po.product_id=p.id and po.status='active'
    ), '[]'::jsonb),
    'variants', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', v.id,
        'title', v.title,
        'options', v.options,
        'priceMinor', v.price_minor,
        'compareAtPriceMinor', v.compare_at_price_minor,
        'status', v.status,
        'availableQuantity', greatest(0,coalesce(inv.quantity_available,0)-coalesce(inv.quantity_reserved,0)),
        'lowStockThreshold', coalesce(inv.low_stock_threshold,0),
        'imageIds', coalesce((select jsonb_agg(vm.product_image_id order by vm.position) from public.shop_variant_media vm where vm.variant_id=v.id),'[]'::jsonb)
      ) order by v.created_at,v.id)
      from public.shop_product_variants v
      left join public.shop_inventory inv on inv.variant_id=v.id
      where v.product_id=p.id and v.status='active'
    ), '[]'::jsonb),
    'priceRange', coalesce((
      select jsonb_build_object('minimumMinor',min(v.price_minor),'maximumMinor',max(v.price_minor))
      from public.shop_product_variants v where v.product_id=p.id and v.status='active'
    ), jsonb_build_object('minimumMinor',p.price_minor,'maximumMinor',p.price_minor)),
    'aggregateAvailable', coalesce((
      select sum(greatest(0,inv.quantity_available-inv.quantity_reserved))
      from public.shop_inventory inv
      join public.shop_product_variants v on v.id=inv.variant_id and v.status='active'
      where inv.product_id=p.id
    ),(
      select greatest(0,inv.quantity_available-inv.quantity_reserved)
      from public.shop_inventory inv
      where inv.product_id=p.id and inv.variant_id is null limit 1
    ),0),
    'has_variants',p.has_variants,
    'price_max_minor',p.price_max_minor,
    'aggregate_available_quantity',p.aggregate_available_quantity
  )
  from public.shop_products p
  join public.shop_stores s on s.id=p.store_id
  where p.id=p_product_id
    and p.status='active' and p.moderation_status='approved' and p.deleted_at is null
    and s.status='active' and s.approval_status='approved' and s.deleted_at is null
$$;

create or replace function public.adishop_seller_save_variant_catalog(
  p_profile_id uuid,
  p_product_id uuid,
  p_payload jsonb,
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  store_record public.shop_stores%rowtype;
  product_record public.shop_products%rowtype;
  specification jsonb;
  option_record jsonb;
  option_value_record jsonb;
  variant_record jsonb;
  assignment record;
  attribute_id uuid;
  option_id uuid;
  option_value_id uuid;
  variant_id_value uuid;
  inventory_record public.shop_inventory%rowtype;
  normalized_options jsonb;
  display_options jsonb;
  signature_value text;
  active_option_count integer;
  assigned_option_count integer;
  quantity_value integer;
  previous_quantity integer;
  seen_option_ids uuid[] := '{}';
  seen_variant_ids uuid[] := '{}';
  total_available integer := 0;
  total_reserved integer := 0;
  minimum_price integer;
  maximum_compare integer;
  image_id_text text;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  select * into product_record
  from public.shop_products
  where id=p_product_id and store_id=store_record.id and deleted_at is null
  for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived') then raise exception 'seller-product-locked'; end if;
  if product_record.category_id is null then raise exception 'category-required'; end if;

  if p_payload ? 'specificationValues' then
    delete from public.shop_product_attribute_values where product_id=p_product_id;
    for specification in select * from jsonb_array_elements(coalesce(p_payload->'specificationValues','[]'::jsonb)) loop
      select a.id into attribute_id
      from public.shop_attribute_definitions a
      join public.shop_category_attributes ca on ca.attribute_definition_id=a.id
      where ca.category_id=product_record.category_id and a.code=upper(trim(specification->>'attributeCode')) and a.status='active';
      if attribute_id is null then raise exception 'invalid-category-attribute'; end if;
      insert into public.shop_product_attribute_values(
        product_id,attribute_definition_id,value_text,value_number,value_boolean,value_json,unit
      ) values (
        p_product_id,attribute_id,
        nullif(trim(specification->>'valueText'),''),
        case when nullif(trim(specification->>'valueNumber'),'') is null then null else (specification->>'valueNumber')::numeric end,
        case when specification ? 'valueBoolean' then (specification->>'valueBoolean')::boolean else null end,
        specification->'valueJson',nullif(trim(specification->>'unit'),'')
      );
    end loop;
  end if;

  for option_record in select * from jsonb_array_elements(coalesce(p_payload->'productOptions','[]'::jsonb)) loop
    select a.id into attribute_id
    from public.shop_attribute_definitions a
    join public.shop_category_attributes ca on ca.attribute_definition_id=a.id
    where ca.category_id=product_record.category_id
      and a.code=upper(trim(option_record->>'attributeCode'))
      and a.is_variant_option=true and ca.variant_capable=true and a.status='active';
    if attribute_id is null then raise exception 'invalid-variant-option'; end if;

    insert into public.shop_product_options(product_id,attribute_definition_id,code,name,position,status)
    values (
      p_product_id,attribute_id,upper(trim(option_record->>'attributeCode')),
      left(trim(coalesce(option_record->>'name',option_record->>'attributeCode')),80),
      greatest(0,coalesce((option_record->>'position')::integer,0)),'active'
    )
    on conflict (product_id,attribute_definition_id) do update set
      code=excluded.code,name=excluded.name,position=excluded.position,status='active',updated_at=now()
    returning id into option_id;
    seen_option_ids := array_append(seen_option_ids,option_id);

    update public.shop_product_option_values set status='inactive',updated_at=now() where product_option_id=option_id;
    for option_value_record in select * from jsonb_array_elements(coalesce(option_record->'values','[]'::jsonb)) loop
      if nullif(trim(option_value_record->>'code'),'') is null or nullif(trim(option_value_record->>'displayValue'),'') is null then
        raise exception 'invalid-option-value';
      end if;
      insert into public.shop_product_option_values(product_option_id,code,value,display_value,color_hex,position,status)
      values (
        option_id,upper(trim(option_value_record->>'code')),
        left(trim(coalesce(option_value_record->>'value',option_value_record->>'displayValue')),80),
        left(trim(option_value_record->>'displayValue'),80),
        nullif(upper(trim(option_value_record->>'colorHex')),''),
        greatest(0,coalesce((option_value_record->>'position')::integer,0)),'active'
      )
      on conflict (product_option_id,code) do update set
        value=excluded.value,display_value=excluded.display_value,color_hex=excluded.color_hex,
        position=excluded.position,status='active',updated_at=now();
    end loop;
    if not exists(select 1 from public.shop_product_option_values where product_option_id=option_id and status='active') then
      raise exception 'variant-option-without-values';
    end if;
  end loop;

  update public.shop_product_options
  set status='inactive',updated_at=now()
  where product_id=p_product_id and not(id=any(seen_option_ids));
  select count(*) into active_option_count from public.shop_product_options where product_id=p_product_id and status='active';

  if active_option_count=0 then
    if jsonb_array_length(coalesce(p_payload->'variants','[]'::jsonb)) > 1 then raise exception 'default-product-one-variant'; end if;
  elsif jsonb_array_length(coalesce(p_payload->'variants','[]'::jsonb)) < 1 then
    raise exception 'variant-required';
  end if;

  for variant_record in select * from jsonb_array_elements(coalesce(p_payload->'variants','[]'::jsonb)) loop
    normalized_options := coalesce(variant_record->'options','{}'::jsonb);
    if jsonb_typeof(normalized_options)<>'object' then raise exception 'invalid-variant-options'; end if;
    if active_option_count <> (select count(*) from jsonb_object_keys(normalized_options)) then raise exception 'incomplete-variant-options'; end if;

    display_options := '{}'::jsonb;
    assigned_option_count := 0;
    for assignment in select upper(key) option_code,upper(value) value_code from jsonb_each_text(normalized_options) loop
      select po.id,pov.id into option_id,option_value_id
      from public.shop_product_options po
      join public.shop_product_option_values pov on pov.product_option_id=po.id
      where po.product_id=p_product_id and po.code=assignment.option_code and po.status='active'
        and pov.code=assignment.value_code and pov.status='active';
      if option_id is null or option_value_id is null then raise exception 'invalid-variant-combination'; end if;
      display_options := display_options || jsonb_build_object(
        assignment.option_code,
        (select display_value from public.shop_product_option_values where id=option_value_id)
      );
      assigned_option_count := assigned_option_count+1;
    end loop;
    if assigned_option_count<>active_option_count then raise exception 'incomplete-variant-options'; end if;

    signature_value := coalesce(public.adishop_variant_signature(normalized_options),'DEFAULT');
    variant_id_value := null;
    if nullif(trim(variant_record->>'id'),'') is not null then
      select id into variant_id_value from public.shop_product_variants
      where id=(variant_record->>'id')::uuid and product_id=p_product_id and store_id=store_record.id;
      if variant_id_value is null then raise exception 'foreign-variant'; end if;
    else
      select id into variant_id_value from public.shop_product_variants
      where product_id=p_product_id and option_signature=signature_value limit 1;
    end if;
    if variant_id_value is null then variant_id_value:=gen_random_uuid(); end if;

    insert into public.shop_product_variants(
      id,product_id,store_id,sku,barcode,title,options,currency,price_minor,compare_at_price_minor,
      status,option_signature,manage_inventory,weight_grams
    ) values (
      variant_id_value,p_product_id,store_record.id,coalesce(nullif(trim(variant_record->>'sku'),''),'AG-'||upper(substring(replace(variant_id_value::text,'-','') from 1 for 12))),
      nullif(trim(variant_record->>'barcode'),''),left(trim(coalesce(variant_record->>'title',signature_value)),120),
      display_options,'ETB',greatest(0,coalesce((variant_record->>'priceMinor')::integer,product_record.price_minor)),
      case when nullif(variant_record->>'compareAtPriceMinor','') is null then null else greatest(0,(variant_record->>'compareAtPriceMinor')::integer) end,
      case when variant_record->>'status' in ('active','paused','archived') then variant_record->>'status' else 'active' end,
      signature_value,coalesce((variant_record->>'manageInventory')::boolean,true),
      case when nullif(variant_record->>'weightGrams','') is null then null else greatest(0,(variant_record->>'weightGrams')::integer) end
    )
    on conflict (id) do update set
      sku=excluded.sku,barcode=excluded.barcode,title=excluded.title,options=excluded.options,
      price_minor=excluded.price_minor,compare_at_price_minor=excluded.compare_at_price_minor,
      status=excluded.status,option_signature=excluded.option_signature,manage_inventory=excluded.manage_inventory,
      weight_grams=excluded.weight_grams,updated_at=now();
    seen_variant_ids:=array_append(seen_variant_ids,variant_id_value);

    delete from public.shop_variant_option_values where variant_id=variant_id_value;
    for assignment in select upper(key) option_code,upper(value) value_code from jsonb_each_text(normalized_options) loop
      select po.id,pov.id into option_id,option_value_id
      from public.shop_product_options po
      join public.shop_product_option_values pov on pov.product_option_id=po.id
      where po.product_id=p_product_id and po.code=assignment.option_code and pov.code=assignment.value_code;
      insert into public.shop_variant_option_values(variant_id,product_option_id,product_option_value_id)
      values(variant_id_value,option_id,option_value_id);
    end loop;

    delete from public.shop_variant_media where variant_id=variant_id_value;
    for image_id_text in select jsonb_array_elements_text(coalesce(variant_record->'imageIds','[]'::jsonb)) loop
      if not exists(select 1 from public.shop_product_images where id=image_id_text::uuid and product_id=p_product_id and deleted_at is null) then
        raise exception 'variant-image-product-mismatch';
      end if;
      insert into public.shop_variant_media(variant_id,product_image_id,position)
      values(variant_id_value,image_id_text::uuid,(select count(*) from public.shop_variant_media where variant_id=variant_id_value));
    end loop;
    update public.shop_product_variants
    set primary_image_id=(select product_image_id from public.shop_variant_media where variant_id=variant_id_value order by position limit 1)
    where id=variant_id_value;

    quantity_value:=greatest(0,coalesce((variant_record->>'quantityAvailable')::integer,0));
    select * into inventory_record from public.shop_inventory where variant_id=variant_id_value for update;
    if found then
      previous_quantity:=inventory_record.quantity_available;
      if quantity_value<inventory_record.quantity_reserved then raise exception 'inventory-below-reserved'; end if;
      update public.shop_inventory set
        quantity_available=quantity_value,
        low_stock_threshold=greatest(0,coalesce((variant_record->>'lowStockThreshold')::integer,3)),
        inventory_scope='sellable',updated_at=now()
      where id=inventory_record.id returning * into inventory_record;
    else
      previous_quantity:=0;
      insert into public.shop_inventory(product_id,variant_id,quantity_available,quantity_reserved,low_stock_threshold,inventory_scope)
      values(p_product_id,variant_id_value,quantity_value,0,greatest(0,coalesce((variant_record->>'lowStockThreshold')::integer,3)),'sellable')
      returning * into inventory_record;
    end if;
    if previous_quantity<>quantity_value then
      insert into public.shop_inventory_events(
        product_id,variant_id,inventory_id,event_type,quantity_delta,quantity_before,quantity_after,
        reserved_before,reserved_after,reason,actor_profile_id,request_id
      ) values (
        p_product_id,variant_id_value,inventory_record.id,
        case when previous_quantity=0 then 'CATALOG_INITIAL_STOCK' else 'SELLER_MANUAL_ADJUSTMENT' end,
        quantity_value-previous_quantity,previous_quantity,quantity_value,
        inventory_record.quantity_reserved,inventory_record.quantity_reserved,'Seller catalog save',p_profile_id,
        nullif(trim(coalesce(p_request_id,'')),'')
      ) on conflict do nothing;
    end if;
  end loop;

  update public.shop_product_variants set status='archived',updated_at=now()
  where product_id=p_product_id and not(id=any(seen_variant_ids));

  select coalesce(sum(i.quantity_available),0),coalesce(sum(i.quantity_reserved),0),min(v.price_minor),max(v.compare_at_price_minor)
    into total_available,total_reserved,minimum_price,maximum_compare
  from public.shop_product_variants v
  join public.shop_inventory i on i.variant_id=v.id
  where v.product_id=p_product_id and v.status='active';

  if coalesce(array_length(seen_variant_ids,1),0)>0 then
    insert into public.shop_inventory(product_id,variant_id,quantity_available,quantity_reserved,low_stock_threshold,inventory_scope)
    values(p_product_id,null,total_available,total_reserved,3,'aggregate')
    on conflict (product_id) where variant_id is null do update set
      quantity_available=excluded.quantity_available,quantity_reserved=excluded.quantity_reserved,
      inventory_scope='aggregate',updated_at=now();
    update public.shop_products set
      price_minor=coalesce(minimum_price,price_minor),compare_at_price_minor=maximum_compare,
      price_max_minor=(select max(price_minor) from public.shop_product_variants where product_id=p_product_id and status='active'),
      has_variants=active_option_count>0,
      aggregate_available_quantity=greatest(0,total_available-total_reserved),
      stock_status=case when total_available-total_reserved>0 then 'in_stock' else 'out_of_stock' end,
      updated_at=now()
    where id=p_product_id;
  else
    update public.shop_inventory set inventory_scope='sellable' where product_id=p_product_id and variant_id is null;
    update public.shop_products set has_variants=false,price_max_minor=null,aggregate_available_quantity=null where id=p_product_id;
  end if;

  return public.adishop_seller_get_product(p_profile_id,p_product_id);
end;
$$;

revoke all on function public.adishop_category_attribute_schema(uuid) from public;
revoke all on function public.adishop_product_catalog_payload(uuid) from public;
revoke all on function public.adishop_seller_save_variant_catalog(uuid,uuid,jsonb,text) from public;
grant execute on function public.adishop_category_attribute_schema(uuid) to service_role;
grant execute on function public.adishop_product_catalog_payload(uuid) to service_role;
grant execute on function public.adishop_seller_save_variant_catalog(uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_category_attribute_schema(uuid) to anon, authenticated;
grant execute on function public.adishop_product_catalog_payload(uuid) to anon, authenticated;

create or replace function public.adishop_validate_sellable_selection()
returns trigger
language plpgsql
set search_path = public
as $$
declare active_variants integer;
begin
  select count(*) into active_variants
  from public.shop_product_variants
  where product_id=new.product_id and status='active';
  if active_variants>0 and new.variant_id is null then raise exception 'variant-required'; end if;
  if new.variant_id is not null and not exists(
    select 1 from public.shop_product_variants
    where id=new.variant_id and product_id=new.product_id and status='active'
  ) then raise exception 'invalid-variant'; end if;
  return new;
end;
$$;

drop trigger if exists shop_cart_items_validate_variant on public.shop_cart_items;
create trigger shop_cart_items_validate_variant before insert or update of product_id,variant_id
on public.shop_cart_items for each row execute function public.adishop_validate_sellable_selection();
drop trigger if exists shop_checkout_items_validate_variant on public.shop_checkout_items;
create trigger shop_checkout_items_validate_variant before insert or update of product_id,variant_id
on public.shop_checkout_items for each row execute function public.adishop_validate_sellable_selection();

revoke all on function public.adishop_validate_sellable_selection() from public;

create or replace function public.adishop_seller_product_summary(p public.shop_products)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id',p.id,'store_id',p.store_id,'category_id',p.category_id,'brand',p.brand,
    'title',p.title,'slug',p.slug,'category_slug',p.category_slug,
    'short_description',p.short_description,'description',p.description,
    'status',p.status,'moderation_status',p.moderation_status,'currency',p.currency,
    'price_minor',p.price_minor,'compare_at_price_minor',p.compare_at_price_minor,
    'discount_percent',p.discount_percent,'stock_status',p.stock_status,
    'cash_on_delivery_enabled',p.cash_on_delivery_enabled,'product_type',p.product_type,
    'seller_sku',p.seller_sku,'specifications',p.specifications,
    'shipping_details',p.shipping_details,'return_policy',p.return_policy,
    'validation_errors',p.validation_errors,'submitted_version',p.submitted_version,
    'submitted_at',p.submitted_at,'reviewed_at',p.reviewed_at,'published_at',p.published_at,
    'created_at',p.created_at,'updated_at',p.updated_at,
    'images',coalesce((select jsonb_agg(to_jsonb(i) order by i.is_primary desc,i.display_order) from public.shop_product_images i where i.product_id=p.id and i.deleted_at is null),'[]'::jsonb),
    'specificationValues',coalesce((
      select jsonb_agg(jsonb_build_object(
        'attributeCode',a.code,'displayName',a.display_name,'valueText',pav.value_text,
        'valueNumber',pav.value_number,'valueBoolean',pav.value_boolean,'valueJson',pav.value_json,
        'unit',coalesce(pav.unit,a.unit)
      ) order by a.sort_order)
      from public.shop_product_attribute_values pav
      join public.shop_attribute_definitions a on a.id=pav.attribute_definition_id
      where pav.product_id=p.id
    ),'[]'::jsonb),
    'productOptions',coalesce((
      select jsonb_agg(jsonb_build_object(
        'id',po.id,'attributeCode',po.code,'name',po.name,'position',po.position,'status',po.status,
        'values',coalesce((select jsonb_agg(jsonb_build_object(
          'id',pov.id,'code',pov.code,'value',pov.value,'displayValue',pov.display_value,
          'colorHex',pov.color_hex,'position',pov.position,'status',pov.status
        ) order by pov.position) from public.shop_product_option_values pov where pov.product_option_id=po.id),'[]'::jsonb)
      ) order by po.position)
      from public.shop_product_options po where po.product_id=p.id and po.status='active'
    ),'[]'::jsonb),
    'variants',coalesce((
      select jsonb_agg(to_jsonb(v)||jsonb_build_object(
        'inventory',coalesce((select to_jsonb(inv) from public.shop_inventory inv where inv.variant_id=v.id limit 1),'{}'::jsonb),
        'imageIds',coalesce((select jsonb_agg(vm.product_image_id order by vm.position) from public.shop_variant_media vm where vm.variant_id=v.id),'[]'::jsonb)
      ) order by v.created_at)
      from public.shop_product_variants v where v.product_id=p.id and v.status<>'archived'
    ),'[]'::jsonb),
    'inventory',coalesce((select jsonb_agg(to_jsonb(inv)) from public.shop_inventory inv where inv.product_id=p.id),'[]'::jsonb),
    'store',(select to_jsonb(s)-'owner_profile_id' from public.shop_stores s where s.id=p.store_id)
  )
$$;

create or replace function public.adishop_admin_get_product(p_profile_id uuid,p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare result jsonb;
begin
  if not public.adishop_profile_has_permission(p_profile_id,'product.moderation.read') then raise exception 'admin-forbidden'; end if;
  select jsonb_build_object(
    'product',to_jsonb(p),'store',to_jsonb(s)-'owner_profile_id',
    'images',coalesce((select jsonb_agg(to_jsonb(i) order by i.is_primary desc,i.display_order) from public.shop_product_images i where i.product_id=p.id and i.deleted_at is null),'[]'::jsonb),
    'inventory',coalesce((select jsonb_agg(to_jsonb(inv)) from public.shop_inventory inv where inv.product_id=p.id),'[]'::jsonb),
    'specificationValues',coalesce((select jsonb_agg(jsonb_build_object('code',a.code,'name',a.display_name,'valueText',pav.value_text,'valueNumber',pav.value_number,'valueBoolean',pav.value_boolean,'unit',coalesce(pav.unit,a.unit)) order by a.sort_order) from public.shop_product_attribute_values pav join public.shop_attribute_definitions a on a.id=pav.attribute_definition_id where pav.product_id=p.id),'[]'::jsonb),
    'productOptions',coalesce((select jsonb_agg(jsonb_build_object('id',po.id,'code',po.code,'name',po.name,'values',coalesce((select jsonb_agg(to_jsonb(pov) order by pov.position) from public.shop_product_option_values pov where pov.product_option_id=po.id),'[]'::jsonb)) order by po.position) from public.shop_product_options po where po.product_id=p.id and po.status='active'),'[]'::jsonb),
    'variants',coalesce((select jsonb_agg(to_jsonb(v)||jsonb_build_object('inventory',(select to_jsonb(inv) from public.shop_inventory inv where inv.variant_id=v.id limit 1),'imageIds',coalesce((select jsonb_agg(vm.product_image_id order by vm.position) from public.shop_variant_media vm where vm.variant_id=v.id),'[]'::jsonb)) order by v.created_at) from public.shop_product_variants v where v.product_id=p.id and v.status<>'archived'),'[]'::jsonb),
    'reviews',coalesce((select jsonb_agg(to_jsonb(r) order by r.created_at desc) from public.shop_product_moderation_reviews r where r.product_id=p.id),'[]'::jsonb)
  ) into result
  from public.shop_products p join public.shop_stores s on s.id=p.store_id where p.id=p_product_id;
  if result is null then raise exception 'product-not-found'; end if;
  return result;
end;
$$;

revoke all on function public.adishop_seller_product_summary(public.shop_products) from public;
grant execute on function public.adishop_seller_product_summary(public.shop_products) to service_role;
revoke all on function public.adishop_admin_get_product(uuid,uuid) from public;
grant execute on function public.adishop_admin_get_product(uuid,uuid) to service_role;

create or replace function public.adishop_seller_submit_product(
  p_profile_id uuid,p_product_id uuid,p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  store_record public.shop_stores%rowtype;
  product_record public.shop_products%rowtype;
  image_count integer;
  active_option_count integer;
  active_variant_count integer;
  invalid_variant_count integer;
  missing_required_count integer;
  legacy_stock integer;
  errors jsonb:='[]'::jsonb;
  previous_status text;
  previous_moderation_status text;
begin
  store_record:=public.adishop_seller_owned_store(p_profile_id);
  select * into product_record from public.shop_products
  where id=p_product_id and store_id=store_record.id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived','submitted') then raise exception 'seller-product-locked'; end if;
  previous_status:=product_record.status;
  previous_moderation_status:=product_record.moderation_status;

  select count(*) into image_count from public.shop_product_images where product_id=p_product_id and deleted_at is null;
  select count(*) into active_option_count from public.shop_product_options where product_id=p_product_id and status='active';
  select count(*) into active_variant_count from public.shop_product_variants where product_id=p_product_id and status='active';
  select coalesce(max(quantity_available-quantity_reserved),0) into legacy_stock
  from public.shop_inventory where product_id=p_product_id and variant_id is null and inventory_scope='sellable';
  select count(*) into missing_required_count
  from public.shop_category_attributes ca
  where ca.category_id=product_record.category_id and ca.required=true
    and not exists(select 1 from public.shop_product_attribute_values pav where pav.product_id=p_product_id and pav.attribute_definition_id=ca.attribute_definition_id);

  select count(*) into invalid_variant_count
  from public.shop_product_variants v
  left join public.shop_inventory inv on inv.variant_id=v.id
  where v.product_id=p_product_id and v.status='active' and (
    v.price_minor<=0 or inv.id is null or inv.quantity_available<inv.quantity_reserved
    or (select count(*) from public.shop_variant_option_values vov where vov.variant_id=v.id)<>active_option_count
  );

  if length(trim(product_record.title))<3 then errors:=errors||'["Product name is required."]'::jsonb; end if;
  if product_record.category_id is null then errors:=errors||'["Choose a category."]'::jsonb; end if;
  if length(trim(coalesce(product_record.description,'')))<12 then errors:=errors||'["Add a product description."]'::jsonb; end if;
  if image_count<1 then errors:=errors||'["Add at least one product image."]'::jsonb; end if;
  if missing_required_count>0 then errors:=errors||'["Complete the required category specifications."]'::jsonb; end if;
  if active_option_count>0 and active_variant_count<1 then errors:=errors||'["Add at least one sellable variant."]'::jsonb; end if;
  if active_variant_count>0 and invalid_variant_count>0 then errors:=errors||'["Check variant price, stock and option combinations."]'::jsonb; end if;
  if active_variant_count=0 and product_record.price_minor<=0 then errors:=errors||'["Enter a valid price."]'::jsonb; end if;
  if active_variant_count=0 and product_record.stock_status<>'out_of_stock' and legacy_stock<1 then errors:=errors||'["Add available stock."]'::jsonb; end if;
  if jsonb_array_length(errors)>0 then raise exception 'product-validation-failed:%',errors::text; end if;

  update public.shop_products set
    status='draft',moderation_status='submitted',submitted_at=now(),reviewed_at=null,
    reviewed_by_profile_id=null,validation_errors='[]'::jsonb,
    submitted_version=submitted_version+1,updated_at=now()
  where id=p_product_id returning * into product_record;

  insert into public.shop_product_status_history(
    product_id,actor_profile_id,actor_role,event_code,previous_status,next_status,
    previous_moderation_status,next_moderation_status,public_message,request_id
  ) values (
    p_product_id,p_profile_id,'seller','seller.product.submit',previous_status,'draft',
    previous_moderation_status,'submitted','Product submitted for review.',nullif(trim(coalesce(p_request_id,'')),'')
  ) on conflict do nothing;
  perform public.adishop_create_notification(
    p_profile_id,'product_submitted','Product submitted',
    'Your product was submitted for AdisGebeya review.',jsonb_build_object('productId',p_product_id)
  );
  return public.adishop_seller_product_summary(product_record);
end;
$$;

revoke all on function public.adishop_seller_submit_product(uuid,uuid,text) from public;
grant execute on function public.adishop_seller_submit_product(uuid,uuid,text) to service_role;
