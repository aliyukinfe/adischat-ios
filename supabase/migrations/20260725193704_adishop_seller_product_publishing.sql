-- AdisGebeya Phase 1.4F seller product publishing foundation.
-- Uses the existing product, media, variant, inventory and moderation tables.

create table if not exists public.shop_product_status_history (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null default 'seller',
  event_code text not null,
  previous_status text,
  next_status text,
  previous_moderation_status text,
  next_moderation_status text,
  public_message text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

alter table public.shop_product_status_history enable row level security;

alter table public.shop_products add column if not exists seller_sku text;
alter table public.shop_products add column if not exists specifications jsonb not null default '{}'::jsonb;
alter table public.shop_products add column if not exists shipping_details jsonb not null default '{}'::jsonb;
alter table public.shop_products add column if not exists return_policy jsonb not null default '{}'::jsonb;
alter table public.shop_products add column if not exists validation_errors jsonb not null default '[]'::jsonb;
alter table public.shop_products add column if not exists submitted_version integer not null default 0;

create index if not exists shop_products_seller_store_status_idx
  on public.shop_products(store_id, moderation_status, updated_at desc);
create index if not exists shop_product_status_history_product_idx
  on public.shop_product_status_history(product_id, created_at desc);
create unique index if not exists shop_product_status_history_request_idx
  on public.shop_product_status_history(product_id, request_id)
  where request_id is not null;

create or replace function public.adishop_seller_owned_store(p_profile_id uuid)
returns public.shop_stores
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
begin
  select s.* into store_record
  from public.shop_stores s
  left join public.shop_store_members sm
    on sm.store_id = s.id and sm.profile_id = p_profile_id and sm.status = 'active'
  where s.deleted_at is null
    and s.status = 'active'
    and s.approval_status = 'approved'
    and (s.owner_profile_id = p_profile_id or sm.role in ('owner','manager'))
  order by s.created_at asc
  limit 1;

  if not found then
    raise exception 'seller-store-not-found';
  end if;
  return store_record;
end;
$$;

create or replace function public.adishop_seller_product_summary(p public.shop_products)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id', p.id,
    'store_id', p.store_id,
    'category_id', p.category_id,
    'brand', p.brand,
    'title', p.title,
    'slug', p.slug,
    'category_slug', p.category_slug,
    'short_description', p.short_description,
    'description', p.description,
    'status', p.status,
    'moderation_status', p.moderation_status,
    'currency', p.currency,
    'price_minor', p.price_minor,
    'compare_at_price_minor', p.compare_at_price_minor,
    'discount_percent', p.discount_percent,
    'stock_status', p.stock_status,
    'cash_on_delivery_enabled', p.cash_on_delivery_enabled,
    'product_type', p.product_type,
    'seller_sku', p.seller_sku,
    'specifications', p.specifications,
    'shipping_details', p.shipping_details,
    'return_policy', p.return_policy,
    'validation_errors', p.validation_errors,
    'submitted_version', p.submitted_version,
    'submitted_at', p.submitted_at,
    'reviewed_at', p.reviewed_at,
    'published_at', p.published_at,
    'created_at', p.created_at,
    'updated_at', p.updated_at,
    'images', coalesce((select jsonb_agg(to_jsonb(i) order by i.is_primary desc, i.display_order asc) from public.shop_product_images i where i.product_id = p.id), '[]'::jsonb),
    'variants', coalesce((
      select jsonb_agg(
        to_jsonb(v) || jsonb_build_object(
          'inventory', coalesce((select to_jsonb(inv) from public.shop_inventory inv where inv.variant_id = v.id limit 1), '{}'::jsonb)
        )
        order by v.created_at asc
      )
      from public.shop_product_variants v
      where v.product_id = p.id
    ), '[]'::jsonb),
    'inventory', coalesce((select jsonb_agg(to_jsonb(inv)) from public.shop_inventory inv where inv.product_id = p.id), '[]'::jsonb),
    'store', (select to_jsonb(s) - 'owner_profile_id' from public.shop_stores s where s.id = p.store_id)
  )
$$;

create or replace function public.adishop_seller_list_products(
  p_profile_id uuid,
  p_status text default 'all',
  p_limit integer default 30,
  p_offset integer default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  return coalesce((
    select jsonb_agg(public.adishop_seller_product_summary(p) order by p.updated_at desc)
    from (
      select *
      from public.shop_products p
      where p.store_id = store_record.id
        and p.deleted_at is null
        and (
          coalesce(p_status, 'all') = 'all'
          or p.moderation_status = p_status
          or p.status = p_status
          or (p_status = 'draft' and p.status = 'draft')
        )
      order by p.updated_at desc
      limit greatest(1, least(100, p_limit))
      offset greatest(0, p_offset)
    ) p
  ), '[]'::jsonb);
end;
$$;

create or replace function public.adishop_seller_get_product(p_profile_id uuid, p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
declare product_record public.shop_products%rowtype;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  select * into product_record
  from public.shop_products
  where id = p_product_id and store_id = store_record.id and deleted_at is null;
  if not found then raise exception 'seller-product-not-found'; end if;
  return public.adishop_seller_product_summary(product_record);
end;
$$;

create or replace function public.adishop_seller_create_product(
  p_profile_id uuid,
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
declare product_id uuid := gen_random_uuid();
declare product_record public.shop_products%rowtype;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);

  if nullif(trim(coalesce(p_request_id, '')), '') is not null then
    select p.* into product_record
    from public.shop_products p
    join public.shop_product_status_history h on h.product_id = p.id
    where p.store_id = store_record.id and h.request_id = p_request_id and h.event_code = 'seller.product.create'
    limit 1;
    if found then return public.adishop_seller_product_summary(product_record); end if;
  end if;

  insert into public.shop_products(
    id, store_id, title, slug, short_description, description, status, moderation_status,
    currency, price_minor, stock_status, cash_on_delivery_enabled, product_type
  )
  values (
    product_id, store_record.id, 'Untitled product', 'draft-' || replace(product_id::text, '-', ''),
    '', '', 'draft', 'pending', 'ETB', 0, 'out_of_stock', true, 'physical'
  )
  returning * into product_record;

  insert into public.shop_inventory(product_id, quantity_available, low_stock_threshold)
  select product_record.id, 0, 3
  where not exists (
    select 1 from public.shop_inventory inv
    where inv.product_id = product_record.id and inv.variant_id is null
  );

  insert into public.shop_product_status_history(
    product_id, actor_profile_id, actor_role, event_code, next_status, next_moderation_status,
    public_message, request_id
  )
  values (
    product_record.id, p_profile_id, 'seller', 'seller.product.create', 'draft', 'pending',
    'Draft product created.', nullif(trim(coalesce(p_request_id, '')), '')
  )
  on conflict do nothing;

  return public.adishop_seller_product_summary(product_record);
end;
$$;

create or replace function public.adishop_seller_update_product(
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
declare store_record public.shop_stores%rowtype;
declare product_record public.shop_products%rowtype;
declare category_record public.shop_categories%rowtype;
declare new_title text;
declare new_slug text;
declare new_status text;
declare new_moderation_status text;
declare image jsonb;
declare variant jsonb;
declare new_variant_id uuid;
declare image_count integer := 0;
declare previous_status text;
declare previous_moderation_status text;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  select * into product_record from public.shop_products where id = p_product_id and store_id = store_record.id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived') then raise exception 'seller-product-locked'; end if;
  previous_status := product_record.status;
  previous_moderation_status := product_record.moderation_status;

  if p_payload ? 'categoryId' and nullif(trim(p_payload->>'categoryId'), '') is not null then
    select * into category_record from public.shop_categories where id = (p_payload->>'categoryId')::uuid and is_active = true;
    if not found then raise exception 'category-not-found'; end if;
  elsif product_record.category_id is not null then
    select * into category_record from public.shop_categories where id = product_record.category_id;
  end if;

  new_title := nullif(trim(coalesce(p_payload->>'title', product_record.title)), '');
  if new_title is null then new_title := product_record.title; end if;
  new_slug := lower(regexp_replace(regexp_replace(new_title, '[^a-zA-Z0-9]+', '-', 'g'), '(^-|-$)', '', 'g'));
  if new_slug = '' then new_slug := 'product-' || replace(p_product_id::text, '-', ''); end if;
  new_slug := new_slug || '-' || substring(replace(p_product_id::text, '-', '') from 1 for 8);

  new_status := product_record.status;
  new_moderation_status := product_record.moderation_status;
  if product_record.moderation_status in ('approved','rejected','changes_requested') then
    new_status := 'draft';
    new_moderation_status := 'pending';
  end if;

  update public.shop_products
  set title = new_title,
      slug = new_slug,
      brand = nullif(trim(coalesce(p_payload->>'brand', brand)), ''),
      category_id = coalesce(category_record.id, category_id),
      category_slug = coalesce(category_record.slug, category_slug),
      short_description = left(coalesce(p_payload->>'shortDescription', p_payload->>'short_description', short_description, ''), 240),
      description = left(coalesce(p_payload->>'description', description, ''), 5000),
      status = new_status,
      moderation_status = new_moderation_status,
      price_minor = greatest(0, coalesce((p_payload->>'priceMinor')::integer, price_minor)),
      compare_at_price_minor = nullif((p_payload->>'compareAtPriceMinor')::integer, 0),
      discount_percent = null,
      stock_status = case when coalesce((p_payload->>'stockQuantity')::integer, 0) <= 0 then 'out_of_stock' else 'in_stock' end,
      cash_on_delivery_enabled = coalesce((p_payload->>'cashOnDeliveryEnabled')::boolean, cash_on_delivery_enabled),
      product_type = case when p_payload->>'productType' in ('physical','digital') then p_payload->>'productType' else product_type end,
      seller_sku = nullif(trim(coalesce(p_payload->>'sellerSku', seller_sku)), ''),
      specifications = coalesce(p_payload->'specifications', specifications, '{}'::jsonb),
      shipping_details = coalesce(p_payload->'shippingDetails', shipping_details, '{}'::jsonb),
      return_policy = coalesce(p_payload->'returnPolicy', return_policy, '{}'::jsonb),
      updated_at = now()
  where id = p_product_id
  returning * into product_record;

  if p_payload ? 'images' then
    delete from public.shop_product_images where product_id = p_product_id;
    for image in select * from jsonb_array_elements(coalesce(p_payload->'images', '[]'::jsonb)) loop
      if nullif(trim(image->>'imageUrl'), '') is not null and image_count < 8 then
        insert into public.shop_product_images(product_id, image_url, storage_path, alt_text, is_primary, display_order)
        values (
          p_product_id,
          left(trim(image->>'imageUrl'), 2000),
          nullif(trim(coalesce(image->>'storagePath', '')), ''),
          nullif(trim(coalesce(image->>'altText', '')), ''),
          image_count = 0,
          image_count
        );
        image_count := image_count + 1;
      end if;
    end loop;
  end if;

  if p_payload ? 'variants' then
    delete from public.shop_inventory inv where inv.product_id = p_product_id and inv.variant_id is not null;
    delete from public.shop_product_variants v where v.product_id = p_product_id;
    for variant in select * from jsonb_array_elements(coalesce(p_payload->'variants', '[]'::jsonb)) loop
      if nullif(trim(variant->>'title'), '') is not null then
        insert into public.shop_product_variants(product_id, sku, title, options, currency, price_minor, compare_at_price_minor, status)
        values (
          p_product_id,
          nullif(trim(coalesce(variant->>'sku', '')), ''),
          left(trim(variant->>'title'), 120),
          coalesce(variant->'options', '{}'::jsonb),
          'ETB',
          greatest(0, coalesce((variant->>'priceMinor')::integer, product_record.price_minor)),
          nullif((variant->>'compareAtPriceMinor')::integer, 0),
          case when variant->>'status' in ('active','paused','archived') then variant->>'status' else 'active' end
        )
        returning id into new_variant_id;

        insert into public.shop_inventory(product_id, variant_id, quantity_available, low_stock_threshold)
        values (
          p_product_id,
          new_variant_id,
          greatest(0, coalesce((variant->>'quantityAvailable')::integer, 0)),
          greatest(0, coalesce((variant->>'lowStockThreshold')::integer, 3))
        );
      end if;
    end loop;
  end if;

  if p_payload ? 'stockQuantity' then
    update public.shop_inventory
    set quantity_available = greatest(0, coalesce((p_payload->>'stockQuantity')::integer, 0)),
        low_stock_threshold = greatest(0, coalesce((p_payload->>'lowStockThreshold')::integer, 3)),
        updated_at = now()
    where shop_inventory.product_id = p_product_id and shop_inventory.variant_id is null;
    if not found then
      insert into public.shop_inventory(product_id, variant_id, quantity_available, low_stock_threshold, updated_at)
      values (p_product_id, null, greatest(0, coalesce((p_payload->>'stockQuantity')::integer, 0)), greatest(0, coalesce((p_payload->>'lowStockThreshold')::integer, 3)), now());
    end if;
  end if;

  insert into public.shop_product_status_history(
    product_id, actor_profile_id, actor_role, event_code, previous_status, next_status,
    previous_moderation_status, next_moderation_status, public_message, request_id
  )
  values (
    p_product_id, p_profile_id, 'seller', 'seller.product.update',
    previous_status, new_status, previous_moderation_status, new_moderation_status,
    'Product draft updated.', nullif(trim(coalesce(p_request_id, '')), '')
  )
  on conflict do nothing;

  return public.adishop_seller_get_product(p_profile_id, p_product_id);
end;
$$;

create or replace function public.adishop_seller_submit_product(
  p_profile_id uuid,
  p_product_id uuid,
  p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare store_record public.shop_stores%rowtype;
declare product_record public.shop_products%rowtype;
declare image_count integer;
declare stock_count integer;
declare errors jsonb := '[]'::jsonb;
declare previous_status text;
declare previous_moderation_status text;
begin
  store_record := public.adishop_seller_owned_store(p_profile_id);
  select * into product_record from public.shop_products where id = p_product_id and store_id = store_record.id and deleted_at is null for update;
  if not found then raise exception 'seller-product-not-found'; end if;
  if product_record.moderation_status in ('under_review','suspended','archived','submitted') then raise exception 'seller-product-locked'; end if;
  previous_status := product_record.status;
  previous_moderation_status := product_record.moderation_status;

  select count(*) into image_count from public.shop_product_images where product_id = p_product_id;
  select count(*) into stock_count from public.shop_inventory where product_id = p_product_id and quantity_available >= quantity_reserved;

  if length(trim(product_record.title)) < 3 then errors := errors || '["Product name is required."]'::jsonb; end if;
  if product_record.category_id is null then errors := errors || '["Choose a category."]'::jsonb; end if;
  if product_record.price_minor <= 0 then errors := errors || '["Enter a valid price."]'::jsonb; end if;
  if length(trim(coalesce(product_record.description, ''))) < 12 then errors := errors || '["Add a product description."]'::jsonb; end if;
  if image_count < 1 then errors := errors || '["Add at least one product image."]'::jsonb; end if;
  if product_record.stock_status <> 'out_of_stock' and stock_count < 1 then errors := errors || '["Add available stock."]'::jsonb; end if;

  if jsonb_array_length(errors) > 0 then
    update public.shop_products set validation_errors = errors, updated_at = now() where id = p_product_id;
    raise exception 'product-validation-failed';
  end if;

  update public.shop_products
  set status = 'draft',
      moderation_status = 'submitted',
      submitted_at = now(),
      reviewed_at = null,
      reviewed_by_profile_id = null,
      validation_errors = '[]'::jsonb,
      submitted_version = submitted_version + 1,
      updated_at = now()
  where id = p_product_id
  returning * into product_record;

  insert into public.shop_product_status_history(
    product_id, actor_profile_id, actor_role, event_code, previous_status, next_status,
    previous_moderation_status, next_moderation_status, public_message, request_id
  )
  values (
    p_product_id, p_profile_id, 'seller', 'seller.product.submit',
    previous_status, 'draft', previous_moderation_status, 'submitted',
    'Product submitted for review.', nullif(trim(coalesce(p_request_id, '')), '')
  )
  on conflict do nothing;

  perform public.adishop_create_notification(
    p_profile_id,
    'product_submitted',
    'Product submitted',
    'Your product was submitted for AdisGebeya review.',
    jsonb_build_object('productId', p_product_id)
  );

  return public.adishop_seller_product_summary(product_record);
end;
$$;

revoke all on table public.shop_product_status_history from anon, authenticated;
revoke all on function public.adishop_seller_owned_store(uuid) from public;
revoke all on function public.adishop_seller_product_summary(public.shop_products) from public;
revoke all on function public.adishop_seller_list_products(uuid,text,integer,integer) from public;
revoke all on function public.adishop_seller_get_product(uuid,uuid) from public;
revoke all on function public.adishop_seller_create_product(uuid,text) from public;
revoke all on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) from public;
revoke all on function public.adishop_seller_submit_product(uuid,uuid,text) from public;

grant execute on function public.adishop_seller_owned_store(uuid) to service_role;
grant execute on function public.adishop_seller_product_summary(public.shop_products) to service_role;
grant execute on function public.adishop_seller_list_products(uuid,text,integer,integer) to service_role;
grant execute on function public.adishop_seller_get_product(uuid,uuid) to service_role;
grant execute on function public.adishop_seller_create_product(uuid,text) to service_role;
grant execute on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_seller_submit_product(uuid,uuid,text) to service_role;
