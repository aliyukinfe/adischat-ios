-- LOCAL REVIEW CANDIDATE ONLY. Target: opkqyvttbyezjbqwkvho, never production.
-- Non-idempotent by design: fail if a marketplace table already exists.
begin;

do $$ begin
  if to_regclass('public.shop_profiles') is null or
     to_regclass('public.shop_user_links') is null or
     to_regclass('public.shop_sessions') is null or
     to_regclass('public.shop_session_families') is null or
     to_regclass('public.shop_roles') is null then
    raise exception 'staging identity foundation required';
  end if;
  if to_regclass('public.shop_products') is not null then
    raise exception 'marketplace schema already exists; review before applying';
  end if;
end $$;

create table public.shop_categories (
  id uuid primary key default gen_random_uuid(), parent_id uuid references public.shop_categories(id),
  name text not null, slug text not null unique, code text unique, description text,
  icon text, image_url text, level integer not null default 1,
  is_leaf boolean not null default true, seller_selectable boolean not null default true,
  buyer_visible boolean not null default true, search_terms text[] not null default '{}',
  is_active boolean not null default true, display_order integer not null default 0,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_stores (
  id uuid primary key default gen_random_uuid(),
  owner_profile_id uuid references public.shop_profiles(id),
  name text not null, slug text not null unique, description text, category text, city text,
  logo_url text, cover_url text,
  status text not null default 'draft' check (status in ('draft','active','paused','suspended','closed')),
  approval_status text not null default 'not_applied'
    check (approval_status in ('not_applied','submitted','under_review','approved','rejected','suspended')),
  average_store_rating numeric(3,2) not null default 0 check (average_store_rating between 0 and 5),
  total_sales integer not null default 0 check (total_sales >= 0),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table public.shop_store_members (
  id uuid primary key default gen_random_uuid(), store_id uuid not null references public.shop_stores(id),
  profile_id uuid not null references public.shop_profiles(id),
  role text not null default 'owner' check (role in ('owner','admin','manager','staff')),
  status text not null default 'active' check (status in ('active','invited','removed')),
  created_at timestamptz not null default now(), unique(store_id,profile_id)
);
create table public.shop_seller_applications (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  proposed_store_name text not null, business_type text,
  status text not null default 'draft' check (status in ('draft','submitted','under_review','approved','rejected','suspended')),
  rejection_reason text, submitted_at timestamptz, reviewed_at timestamptz,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_products (
  id uuid primary key default gen_random_uuid(), store_id uuid not null references public.shop_stores(id),
  category_id uuid references public.shop_categories(id), brand text,
  title text not null, slug text not null, category_slug text, short_description text, description text,
  selling_unit_code text, minimum_order_quantity numeric not null default 1,
  status text not null default 'draft' check (status in ('draft','active','paused','archived')),
  moderation_status text not null default 'pending' check (moderation_status in ('pending','approved','rejected')),
  currency char(3) not null default 'ETB', price_minor integer not null check (price_minor >= 0),
  price_max_minor integer, compare_at_price_minor integer, discount_percent integer,
  has_variants boolean not null default false, aggregate_available_quantity integer not null default 0,
  stock_status text not null default 'out_of_stock',
  rating_average numeric(3,2) not null default 0, rating_count integer not null default 0,
  completed_sales_count integer not null default 0,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  deleted_at timestamptz, unique(store_id,slug)
);
create table public.shop_product_variants (
  id uuid primary key default gen_random_uuid(), product_id uuid not null references public.shop_products(id),
  sku text, title text not null, options jsonb not null default '{}'::jsonb,
  currency char(3) not null default 'ETB', price_minor integer not null check (price_minor >= 0),
  compare_at_price_minor integer, status text not null default 'active'
    check (status in ('active','paused','archived')),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_product_images (
  id uuid primary key default gen_random_uuid(), product_id uuid not null references public.shop_products(id),
  image_url text not null, storage_path text, alt_text text,
  is_primary boolean not null default false, display_order integer not null default 0,
  created_at timestamptz not null default now()
);
create table public.shop_inventory (
  id uuid primary key default gen_random_uuid(), product_id uuid not null references public.shop_products(id),
  variant_id uuid references public.shop_product_variants(id),
  quantity_available integer not null default 0 check (quantity_available >= 0),
  quantity_reserved integer not null default 0 check (quantity_reserved >= 0),
  low_stock_threshold integer not null default 3 check (low_stock_threshold >= 0),
  updated_at timestamptz not null default now()
);
create unique index shop_qa_inventory_product_variant on public.shop_inventory
  (product_id,coalesce(variant_id,'00000000-0000-0000-0000-000000000000'::uuid));
create table public.shop_favorites (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  product_id uuid not null references public.shop_products(id),
  created_at timestamptz not null default now(), unique(profile_id,product_id)
);
create table public.shop_carts (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  status text not null default 'active' check (status in ('active','submitted','abandoned')),
  currency char(3) not null default 'ETB',
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_cart_items (
  id uuid primary key default gen_random_uuid(), cart_id uuid not null references public.shop_carts(id),
  product_id uuid not null references public.shop_products(id),
  variant_id uuid references public.shop_product_variants(id),
  quantity integer not null check (quantity between 1 and 99),
  unit_price_minor integer not null check (unit_price_minor >= 0),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_addresses (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  label text, recipient_name text not null, phone text not null, city text not null,
  address_line1 text not null, address_line2 text, latitude numeric(10,7), longitude numeric(10,7),
  is_default boolean not null default false,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table public.shop_orders (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  address_id uuid references public.shop_addresses(id), order_number text not null unique,
  status text not null, fulfillment_status text not null, delivery_status text not null,
  payment_status text not null, currency char(3) not null default 'ETB',
  subtotal_minor integer not null default 0, delivery_fee_minor integer not null default 0,
  discount_minor integer not null default 0, total_minor integer not null default 0,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.shop_order_groups (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  store_id uuid not null references public.shop_stores(id), status text not null,
  fulfillment_status text not null, seller_response_status text not null default 'pending',
  seller_response_required_at timestamptz, seller_responded_at timestamptz,
  subtotal_minor integer not null default 0, created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.shop_order_items (
  id uuid primary key default gen_random_uuid(), order_group_id uuid not null references public.shop_order_groups(id),
  product_id uuid not null references public.shop_products(id), variant_id uuid references public.shop_product_variants(id),
  title_snapshot text not null, quantity integer not null check (quantity > 0),
  unit_price_minor integer not null, line_total_minor integer not null,
  created_at timestamptz not null default now()
);
create table public.shop_order_status_history (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  order_group_id uuid references public.shop_order_groups(id), status text not null,
  public_message text, internal_message text,
  actor_profile_id uuid references public.shop_profiles(id), occurred_at timestamptz not null default now()
);

-- Only reviewer-controlled snapshots are exposed. Raw seller text and private paths stay private.
create table public.shop_qa_reviewed_catalog (
  product_id uuid primary key references public.shop_products(id),
  product_updated_at timestamptz not null, store_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  title text not null, short_description text,
  price_minor integer not null check (price_minor >= 0), currency char(3) not null default 'ETB'
);
create table public.shop_qa_reviewed_stores (
  store_id uuid primary key references public.shop_stores(id),
  store_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  name text not null, description text, city text
);
create function public.shop_qa_touch_review_revision() returns trigger
language plpgsql as $$ begin new.updated_at := clock_timestamp(); return new; end $$;
revoke all on function public.shop_qa_touch_review_revision() from public,anon,authenticated;
create function public.shop_qa_invalidate_variant_review() returns trigger
language plpgsql as $$ begin
  if tg_op = 'DELETE' then
    update public.shop_products set updated_at=clock_timestamp() where id=old.product_id;
    return old;
  end if;
  update public.shop_products set updated_at=clock_timestamp() where id=new.product_id;
  if tg_op = 'UPDATE' and old.product_id is distinct from new.product_id then
    update public.shop_products set updated_at=clock_timestamp() where id=old.product_id;
  end if;
  return new;
end $$;
revoke all on function public.shop_qa_invalidate_variant_review() from public,anon,authenticated;
create trigger shop_qa_store_revision before update on public.shop_stores
  for each row execute function public.shop_qa_touch_review_revision();
create trigger shop_qa_product_revision before update on public.shop_products
  for each row execute function public.shop_qa_touch_review_revision();
create trigger shop_qa_variant_revision after insert or update or delete on public.shop_product_variants
  for each row execute function public.shop_qa_invalidate_variant_review();
create view public.shop_qa_public_stores with (security_barrier=true) as
select s.id, r.name, r.description, r.city, s.created_at
from public.shop_qa_reviewed_stores r join public.shop_stores s on s.id=r.store_id
where s.updated_at=r.store_updated_at and s.status='active'
  and s.approval_status='approved' and s.deleted_at is null
  and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id=reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id=ur.role_id
    where reviewer.id=r.reviewed_by_profile_id and reviewer.status='active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'));
create view public.shop_qa_public_catalog with (security_barrier=true) as
select p.id, p.store_id, p.category_id, r.title, r.short_description,
       sr.name as store_name, r.price_minor, r.currency, p.stock_status, p.created_at
from public.shop_qa_reviewed_catalog r
join public.shop_products p on p.id=r.product_id
join public.shop_stores s on s.id=p.store_id
join public.shop_qa_reviewed_stores sr on sr.store_id=s.id and sr.store_updated_at=s.updated_at
where p.updated_at=r.product_updated_at and s.updated_at=r.store_updated_at
  and p.status='active' and p.moderation_status='approved' and p.deleted_at is null
  and s.status='active' and s.approval_status='approved' and s.deleted_at is null
  and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  and sr.reviewed_by_profile_id is distinct from s.owner_profile_id
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id=reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id=ur.role_id
    where reviewer.id=r.reviewed_by_profile_id and reviewer.status='active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'))
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id=reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id=ur.role_id
    where reviewer.id=sr.reviewed_by_profile_id and reviewer.status='active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'));

do $$ declare relation text; begin
  foreach relation in array array[
    'shop_categories','shop_stores','shop_store_members','shop_seller_applications',
    'shop_products','shop_product_variants','shop_product_images','shop_inventory',
    'shop_favorites','shop_carts','shop_cart_items','shop_addresses',
    'shop_orders','shop_order_groups','shop_order_items','shop_order_status_history',
    'shop_qa_reviewed_catalog','shop_qa_reviewed_stores'
  ] loop
    execute format('alter table public.%I enable row level security',relation);
    execute format('revoke all on public.%I from public,anon,authenticated',relation);
    if relation in ('shop_orders','shop_order_groups','shop_order_items','shop_order_status_history',
                    'shop_qa_reviewed_catalog','shop_qa_reviewed_stores') then
      execute format('grant select on public.%I to service_role',relation);
    else
      execute format('grant select,insert,update,delete on public.%I to service_role',relation);
    end if;
  end loop;
end $$;
revoke all on public.shop_qa_public_catalog from public,anon,authenticated;
revoke all on public.shop_qa_public_stores from public,anon,authenticated;
grant usage on schema public to anon,authenticated;
create policy shop_qa_public_categories on public.shop_categories for select to anon,authenticated
  using (is_active and buyer_visible);
grant select(id,parent_id,name,slug,code,description,icon,level,is_leaf,
  seller_selectable,buyer_visible,search_terms,display_order)
  on public.shop_categories to anon,authenticated;
grant select on public.shop_qa_public_catalog to anon,authenticated;
grant select on public.shop_qa_public_stores to anon,authenticated;

commit;
