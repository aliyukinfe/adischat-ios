-- PHASE 9G.13 STAGING DEPLOYMENT PACKAGE: CONSOLIDATED MARKETPLACE SCHEMA
-- Target Project: opkqyvttbyezjbqwkvho (QA Supabase)
-- NEVER APPLY TO PRODUCTION (mcewjfnkrtiiolzibsuh).
-- Dependencies: Requires existing staging identity foundation (shop_profiles, shop_user_links, shop_sessions).
-- Order of application: Run 01_preflight_and_backup.sql first, then execute this file in a single transaction.

begin;

-- ============================================================================
-- 1. DEFENSIVE ENVIRONMENT GUARD
-- ============================================================================
do $$ begin
  if to_regclass('public.shop_profiles') is null or
     to_regclass('public.shop_user_links') is null or
     to_regclass('public.shop_sessions') is null or
     to_regclass('public.shop_session_families') is null or
     to_regclass('public.shop_roles') is null then
    raise exception 'PREFLIGHT_FAIL: Staging identity foundation required before applying marketplace schema.';
  end if;
  if to_regclass('public.shop_products') is not null then
    raise exception 'PREFLIGHT_FAIL: Marketplace schema already exists on target; aborting to prevent overwrite.';
  end if;
end $$;

-- ============================================================================
-- 2. PRIVATE SCHEMA FOR INTERNAL SCOPING & LOCKED ACTOR
-- ============================================================================
create schema if not exists shop_qa_private;
revoke all on schema shop_qa_private from public, anon, authenticated;
grant usage on schema shop_qa_private to service_role;

-- ============================================================================
-- 3. CORE MARKETPLACE TABLES
-- ============================================================================
create table public.shop_categories (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references public.shop_categories(id),
  name text not null,
  slug text not null unique,
  code text unique,
  description text,
  icon text,
  image_url text,
  level integer not null default 1,
  is_leaf boolean not null default true,
  seller_selectable boolean not null default true,
  buyer_visible boolean not null default true,
  search_terms text[] not null default '{}',
  is_active boolean not null default true,
  display_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_stores (
  id uuid primary key default gen_random_uuid(),
  owner_profile_id uuid references public.shop_profiles(id),
  name text not null,
  slug text not null unique,
  description text,
  category text,
  city text,
  logo_url text,
  cover_url text,
  status text not null default 'draft' check (status in ('draft','active','paused','suspended','closed')),
  approval_status text not null default 'not_applied'
    check (approval_status in ('not_applied','submitted','under_review','approved','rejected','suspended')),
  average_store_rating numeric(3,2) not null default 0 check (average_store_rating between 0 and 5),
  total_sales integer not null default 0 check (total_sales >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.shop_store_members (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id),
  profile_id uuid not null references public.shop_profiles(id),
  role text not null default 'owner' check (role in ('owner','admin','manager','staff')),
  status text not null default 'active' check (status in ('active','invited','removed')),
  created_at timestamptz not null default now(),
  unique(store_id, profile_id)
);

create table public.shop_seller_applications (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  proposed_store_name text not null,
  business_type text,
  status text not null default 'draft' check (status in ('draft','submitted','under_review','approved','rejected','suspended')),
  rejection_reason text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_products (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id),
  category_id uuid references public.shop_categories(id),
  brand text,
  title text not null,
  slug text not null,
  category_slug text,
  short_description text,
  description text,
  selling_unit_code text,
  minimum_order_quantity numeric not null default 1,
  status text not null default 'draft' check (status in ('draft','active','paused','archived')),
  moderation_status text not null default 'pending' check (moderation_status in ('pending','approved','rejected')),
  currency char(3) not null default 'ETB',
  price_minor integer not null check (price_minor >= 0),
  price_max_minor integer,
  compare_at_price_minor integer,
  discount_percent integer,
  has_variants boolean not null default false,
  aggregate_available_quantity integer not null default 0,
  stock_status text not null default 'out_of_stock',
  rating_average numeric(3,2) not null default 0,
  rating_count integer not null default 0,
  completed_sales_count integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique(store_id, slug)
);

create table public.shop_product_variants (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id),
  sku text,
  title text not null,
  options jsonb not null default '{}'::jsonb,
  currency char(3) not null default 'ETB',
  price_minor integer not null check (price_minor >= 0),
  compare_at_price_minor integer,
  status text not null default 'active' check (status in ('active','paused','archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id),
  image_url text not null,
  storage_path text,
  alt_text text,
  is_primary boolean not null default false,
  display_order integer not null default 0,
  revision integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_inventory (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id),
  variant_id uuid references public.shop_product_variants(id),
  quantity_available integer not null default 0 check (quantity_available >= 0),
  quantity_reserved integer not null default 0 check (quantity_reserved >= 0),
  low_stock_threshold integer not null default 3 check (low_stock_threshold >= 0),
  updated_at timestamptz not null default now()
);
create unique index shop_qa_inventory_product_variant on public.shop_inventory
  (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table public.shop_favorites (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  product_id uuid not null references public.shop_products(id),
  created_at timestamptz not null default now(),
  unique(profile_id, product_id)
);

create table public.shop_carts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  status text not null default 'active' check (status in ('active','submitted','abandoned')),
  currency char(3) not null default 'ETB',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index shop_qa_one_active_cart on public.shop_carts(profile_id, currency) where status = 'active';

create table public.shop_cart_items (
  id uuid primary key default gen_random_uuid(),
  cart_id uuid not null references public.shop_carts(id),
  product_id uuid not null references public.shop_products(id),
  variant_id uuid references public.shop_product_variants(id),
  quantity integer not null check (quantity between 1 and 99),
  unit_price_minor integer not null check (unit_price_minor >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index shop_qa_one_cart_line on public.shop_cart_items
  (cart_id, product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table public.shop_addresses (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  label text,
  recipient_name text not null,
  phone text not null,
  city text not null,
  address_line1 text not null,
  address_line2 text,
  latitude numeric(10,7),
  longitude numeric(10,7),
  is_default boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.shop_orders (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  address_id uuid references public.shop_addresses(id),
  order_number text not null unique,
  status text not null,
  fulfillment_status text not null,
  delivery_status text not null,
  payment_status text not null,
  currency char(3) not null default 'ETB',
  subtotal_minor integer not null default 0,
  delivery_fee_minor integer not null default 0,
  discount_minor integer not null default 0,
  total_minor integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_order_groups (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id),
  store_id uuid not null references public.shop_stores(id),
  status text not null,
  fulfillment_status text not null,
  seller_response_status text not null default 'pending',
  seller_response_required_at timestamptz,
  seller_responded_at timestamptz,
  subtotal_minor integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.shop_order_items (
  id uuid primary key default gen_random_uuid(),
  order_group_id uuid not null references public.shop_order_groups(id),
  product_id uuid not null references public.shop_products(id),
  variant_id uuid references public.shop_product_variants(id),
  title_snapshot text not null,
  quantity integer not null check (quantity > 0),
  unit_price_minor integer not null,
  line_total_minor integer not null,
  created_at timestamptz not null default now()
);

create table public.shop_order_status_history (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id),
  order_group_id uuid references public.shop_order_groups(id),
  status text not null,
  public_message text,
  internal_message text,
  actor_profile_id uuid references public.shop_profiles(id),
  occurred_at timestamptz not null default now()
);

-- ============================================================================
-- 4. REVIEWED PROJECTION TABLES (Curated & Moderated Public Snapshot)
-- ============================================================================
create table public.shop_qa_reviewed_catalog (
  product_id uuid primary key references public.shop_products(id),
  product_updated_at timestamptz not null,
  store_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  title text not null,
  short_description text,
  price_minor integer not null check (price_minor >= 0),
  currency char(3) not null default 'ETB'
);

create table public.shop_qa_reviewed_stores (
  store_id uuid primary key references public.shop_stores(id),
  store_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  name text not null,
  description text,
  city text
);

create table public.shop_qa_reviewed_variants (
  variant_id uuid primary key references public.shop_product_variants(id),
  variant_updated_at timestamptz not null,
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  title text not null,
  options jsonb not null default '{}'::jsonb,
  price_minor integer not null check (price_minor >= 0)
);

create table public.shop_qa_reviewed_images (
  image_id uuid primary key references public.shop_product_images(id),
  image_revision integer not null check (image_revision > 0),
  reviewed_by_profile_id uuid not null references public.shop_profiles(id),
  reviewed_at timestamptz not null default now(),
  alt_text text,
  is_primary boolean not null default false,
  display_order integer not null default 0
);

-- ============================================================================
-- 5. REVISION TOUCH & INVALIDATION TRIGGERS
-- ============================================================================
create or replace function public.shop_qa_touch_review_revision() returns trigger
language plpgsql as $$ begin new.updated_at := clock_timestamp(); return new; end $$;
revoke all on function public.shop_qa_touch_review_revision() from public, anon, authenticated;

create or replace function public.shop_qa_invalidate_variant_review() returns trigger
language plpgsql as $$ begin
  if tg_op = 'DELETE' then
    update public.shop_products set updated_at = clock_timestamp() where id = old.product_id;
    return old;
  end if;
  update public.shop_products set updated_at = clock_timestamp() where id = new.product_id;
  if tg_op = 'UPDATE' and old.product_id is distinct from new.product_id then
    update public.shop_products set updated_at = clock_timestamp() where id = old.product_id;
  end if;
  return new;
end $$;
revoke all on function public.shop_qa_invalidate_variant_review() from public, anon, authenticated;

create or replace function shop_qa_private.touch_image_revision() returns trigger
language plpgsql set search_path = '' as $$ begin
  new.revision := old.revision + 1;
  new.updated_at := clock_timestamp();
  return new;
end $$;
revoke all on function shop_qa_private.touch_image_revision() from public, anon, authenticated;

create or replace function shop_qa_private.invalidate_product_for_image() returns trigger
language plpgsql set search_path = '' as $$ begin
  if tg_op = 'DELETE' then
    update public.shop_products set updated_at = clock_timestamp() where id = old.product_id;
    return old;
  end if;
  update public.shop_products set updated_at = clock_timestamp() where id = new.product_id;
  if tg_op = 'UPDATE' and old.product_id is distinct from new.product_id then
    update public.shop_products set updated_at = clock_timestamp() where id = old.product_id;
  end if;
  return new;
end $$;
revoke all on function shop_qa_private.invalidate_product_for_image() from public, anon, authenticated;

create trigger shop_qa_store_revision before update on public.shop_stores
  for each row execute function public.shop_qa_touch_review_revision();

create trigger shop_qa_product_revision before update on public.shop_products
  for each row execute function public.shop_qa_touch_review_revision();

create trigger shop_qa_variant_revision after insert or update or delete on public.shop_product_variants
  for each row execute function public.shop_qa_invalidate_variant_review();

create trigger shop_qa_image_revision before update on public.shop_product_images
  for each row execute function shop_qa_private.touch_image_revision();

create trigger shop_qa_image_invalidate after insert or update or delete on public.shop_product_images
  for each row execute function shop_qa_private.invalidate_product_for_image();

-- ============================================================================
-- 6. SECURITY-BARRIER VIEWS
-- ============================================================================
create view public.shop_qa_public_stores with (security_barrier=true) as
select s.id, r.name, r.description, r.city, s.created_at
from public.shop_qa_reviewed_stores r join public.shop_stores s on s.id = r.store_id
where s.updated_at = r.store_updated_at and s.status = 'active'
  and s.approval_status = 'approved' and s.deleted_at is null
  and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id = reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id = ur.role_id
    where reviewer.id = r.reviewed_by_profile_id and reviewer.status = 'active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'));

create view public.shop_qa_public_catalog with (security_barrier=true) as
select p.id, p.store_id, p.category_id, r.title, r.short_description,
       sr.name as store_name, r.price_minor, r.currency, p.stock_status, p.created_at
from public.shop_qa_reviewed_catalog r
join public.shop_products p on p.id = r.product_id
join public.shop_stores s on s.id = p.store_id
join public.shop_qa_reviewed_stores sr on sr.store_id = s.id and sr.store_updated_at = s.updated_at
where p.updated_at = r.product_updated_at and s.updated_at = r.store_updated_at
  and p.status = 'active' and p.moderation_status = 'approved' and p.deleted_at is null
  and s.status = 'active' and s.approval_status = 'approved' and s.deleted_at is null
  and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  and sr.reviewed_by_profile_id is distinct from s.owner_profile_id
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id = reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id = ur.role_id
    where reviewer.id = r.reviewed_by_profile_id and reviewer.status = 'active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'))
  and exists (select 1 from public.shop_profiles reviewer
    join public.shop_user_roles ur on ur.profile_id = reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id = ur.role_id
    where reviewer.id = sr.reviewed_by_profile_id and reviewer.status = 'active'
      and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN'));

-- ============================================================================
-- 7. SESSION LOCKING & IDENTITY READ-SCOPING RPCS
-- ============================================================================
create or replace function shop_qa_private.active_read_scope(p_session_jti text)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('profileId', p.id, 'sessionId', sess.id)
  from public.shop_profiles p
  join public.shop_sessions sess on sess.profile_id = p.id
  join public.shop_session_families family on family.id = sess.family_id
    and family.profile_id = p.id
  where sess.session_jti = p_session_jti
    and p.status = 'active'
    and sess.revoked_at is null and sess.expires_at > now()
    and family.revoked_at is null
    and exists (select 1 from public.shop_user_links link
      where link.shop_profile_id = p.id and link.account_status = 'active')
    and not exists (select 1 from public.shop_user_links link
      where link.shop_profile_id = p.id and link.account_status <> 'active');
$$;
revoke all on function shop_qa_private.active_read_scope(text) from public, anon, authenticated;
grant execute on function shop_qa_private.active_read_scope(text) to service_role;

create or replace function public.adishop_qa_active_read_scope(p_session_jti text)
returns jsonb language sql stable set search_path = '' as $$
  select shop_qa_private.active_read_scope(p_session_jti);
$$;
revoke all on function public.adishop_qa_active_read_scope(text) from public, anon, authenticated;
grant execute on function public.adishop_qa_active_read_scope(text) to service_role;

create or replace function shop_qa_private.locked_actor(p_session_jti text)
returns uuid language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  state text;
  v_family_id uuid;
  family_revoked timestamptz;
  session_revoked timestamptz;
  session_expires timestamptz;
  link record;
  link_count integer := 0;
begin
  select p.id, p.status into actor, state from public.shop_sessions sess
    join public.shop_profiles p on p.id = sess.profile_id
    where sess.session_jti = p_session_jti for share of p;
  if actor is null or state <> 'active' then raise exception 'invalid-session' using errcode = '28000'; end if;

  for link in select account_status from public.shop_user_links
      where shop_profile_id = actor for share loop
    link_count := link_count + 1;
    if link.account_status <> 'active' then raise exception 'invalid-session' using errcode = '28000'; end if;
  end loop;
  if link_count = 0 then raise exception 'invalid-session' using errcode = '28000'; end if;

  select family_id into v_family_id from public.shop_sessions where session_jti = p_session_jti;
  select revoked_at into family_revoked from public.shop_session_families
    where id = v_family_id and profile_id = actor for share;
  if not found then raise exception 'invalid-session' using errcode = '28000'; end if;

  select revoked_at, expires_at into session_revoked, session_expires from public.shop_sessions
    where session_jti = p_session_jti and family_id = v_family_id and profile_id = actor for share;
  if not found or family_revoked is not null or session_revoked is not null or
     session_expires <= now() then raise exception 'invalid-session' using errcode = '28000'; end if;

  return actor;
end $$;
revoke all on function shop_qa_private.locked_actor(text) from public, anon, authenticated;
grant execute on function shop_qa_private.locked_actor(text) to service_role;

-- ============================================================================
-- 8. CATALOG QUERY & PRODUCT DETAIL RPCS
-- ============================================================================
create or replace function public.adishop_qa_variant_reviewed(p_variant_id uuid, p_store_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_qa_reviewed_variants r
    join public.shop_product_variants v on v.id = r.variant_id
    join public.shop_stores s on s.id = p_store_id
    join public.shop_profiles reviewer on reviewer.id = r.reviewed_by_profile_id and reviewer.status = 'active'
    join public.shop_user_roles ur on ur.profile_id = reviewer.id and ur.revoked_at is null
    join public.shop_roles role on role.id = ur.role_id and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    where v.id = p_variant_id and v.updated_at = r.variant_updated_at and v.status = 'active'
      and r.reviewed_by_profile_id is distinct from s.owner_profile_id
  );
$$;
revoke all on function public.adishop_qa_variant_reviewed(uuid, uuid) from public, anon, authenticated;

create or replace function public.adishop_qa_catalog_page(
  p_term text default null, p_category_slug text default null,
  p_min_price integer default null, p_max_price integer default null,
  p_in_stock boolean default false, p_sort text default 'newest',
  p_limit integer default 24, p_offset integer default 0
) returns setof jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if p_limit not between 1 and 100 or p_offset not between 0 and 10000 or
     p_sort not in ('newest','oldest','updated','price_low','price_high') or
     length(coalesce(p_term,'')) > 80 or
     (p_term is not null and length(btrim(p_term)) < 2) or
     (p_min_price is not null and p_min_price < 0) or
     (p_max_price is not null and p_max_price < 0) then
    raise exception 'invalid-qa-catalog-query' using errcode = '22023';
  end if;
  return query
  select jsonb_build_object(
    'id', p.id, 'title', p.title, 'short_description', p.short_description,
    'category_id', p.category_id, 'category_slug', c.slug,
    'created_at', p.created_at, 'price_minor', p.price_minor,
    'stock_status', p.stock_status, 'has_variants', raw.has_variants,
    'shop_stores', jsonb_build_object('id', s.id, 'name', s.name, 'city', s.city),
    'shop_product_images', '[]'::jsonb
  )
  from public.shop_qa_public_catalog p
  join public.shop_products raw on raw.id = p.id
  join public.shop_qa_public_stores s on s.id = p.store_id
  left join public.shop_categories c on c.id = p.category_id
  where (p_term is null or p.title ilike '%' || p_term || '%' or s.name ilike '%' || p_term || '%')
    and (p_category_slug is null or c.slug = p_category_slug)
    and (p_min_price is null or p.price_minor >= p_min_price)
    and (p_max_price is null or p.price_minor <= p_max_price)
    and (not p_in_stock or p.stock_status <> 'out_of_stock')
    and not exists (
      select 1 from public.shop_product_variants v where v.product_id = p.id and v.status = 'active'
        and not public.adishop_qa_variant_reviewed(v.id, p.store_id)
    )
  order by
    case when p_sort = 'newest' then p.created_at end desc,
    case when p_sort = 'oldest' then p.created_at end asc,
    case when p_sort = 'updated' then raw.updated_at end desc,
    case when p_sort = 'price_low' then p.price_minor end asc,
    case when p_sort = 'price_high' then p.price_minor end desc,
    p.id desc
  limit p_limit offset p_offset;
end;
$$;
revoke all on function public.adishop_qa_catalog_page(text,text,integer,integer,boolean,text,integer,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_catalog_page(text,text,integer,integer,boolean,text,integer,integer) to anon, authenticated;

create or replace function public.adishop_qa_product_detail(p_product_id uuid)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', p.id, 'title', p.title, 'short_description', p.short_description,
    'category_id', p.category_id, 'category_slug', c.slug, 'created_at', p.created_at,
    'price_minor', p.price_minor, 'stock_status', p.stock_status,
    'has_variants', raw.has_variants,
    'shop_stores', jsonb_build_object('id', s.id, 'name', s.name, 'city', s.city),
    'shop_product_images', '[]'::jsonb,
    'variants', coalesce((select jsonb_agg(jsonb_build_object(
      'id', v.id, 'title', rv.title, 'options', rv.options, 'price_minor', rv.price_minor,
      'status', v.status, 'availableQuantity', coalesce(i.quantity_available, 0),
      'lowStockThreshold', coalesce(i.low_stock_threshold, 0)) order by v.id)
      from public.shop_product_variants v
      join public.shop_qa_reviewed_variants rv on rv.variant_id = v.id and rv.variant_updated_at = v.updated_at
      left join public.shop_inventory i on i.variant_id = v.id and i.product_id = p.id
      where v.product_id = p.id and v.status = 'active'
        and public.adishop_qa_variant_reviewed(v.id, p.store_id)), '[]'::jsonb)
  )
  from public.shop_qa_public_catalog p
  join public.shop_products raw on raw.id = p.id
  join public.shop_qa_public_stores s on s.id = p.store_id
  left join public.shop_categories c on c.id = p.category_id
  where p.id = p_product_id and not exists (
    select 1 from public.shop_product_variants v where v.product_id = p.id and v.status = 'active'
      and not public.adishop_qa_variant_reviewed(v.id, p.store_id)
  );
$$;
revoke all on function public.adishop_qa_product_detail(uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_product_detail(uuid) to anon, authenticated;

-- ============================================================================
-- 9. ADMIN PRODUCT & MEDIA REVIEW RPCS
-- ============================================================================
create or replace function public.adishop_qa_review_product(
  p_session_jti text, p_product_id uuid, p_expected_updated_at timestamptz,
  p_decision text, p_public_title text default null, p_public_short_description text default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  target record;
  owner_id uuid;
  target_store_id uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_decision not in ('approve','reject') or p_product_id is null or p_expected_updated_at is null then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  select store_id into target_store_id from public.shop_products where id = p_product_id;
  select owner_profile_id into owner_id from public.shop_stores where id = target_store_id for share;
  select id, store_id, updated_at, price_minor, short_description into target
    from public.shop_products where id = p_product_id and store_id = target_store_id for update;
  if target.id is null then raise exception 'product-not-found' using errcode = '22023'; end if;
  if owner_id = actor then raise exception 'self-review-forbidden' using errcode = '42501'; end if;
  if target.updated_at is distinct from p_expected_updated_at then
    raise exception 'stale-revision' using errcode = '40001'; end if;

  if p_decision = 'approve' then
    if length(btrim(coalesce(p_public_title,''))) not between 2 and 160 then
      raise exception 'invalid-public-title' using errcode = '22023'; end if;
    insert into public.shop_qa_reviewed_catalog(product_id, product_updated_at, store_updated_at,
      reviewed_by_profile_id, title, short_description, price_minor)
      select target.id, target.updated_at, store.updated_at, actor,
        btrim(p_public_title), nullif(btrim(p_public_short_description),''), target.price_minor
        from public.shop_stores store where store.id = target.store_id
      on conflict(product_id) do update set product_updated_at = excluded.product_updated_at,
        store_updated_at = excluded.store_updated_at, reviewed_by_profile_id = excluded.reviewed_by_profile_id,
        title = excluded.title, short_description = excluded.short_description, price_minor = excluded.price_minor,
        reviewed_at = now();
  else
    delete from public.shop_qa_reviewed_catalog where product_id = target.id;
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values(actor, 'qa.review.' || p_decision, 'shop_product', target.id::text,
      jsonb_build_object('expectedUpdatedAt', p_expected_updated_at));
  return jsonb_build_object('productId', target.id, 'decision', p_decision, 'revision', target.updated_at);
end $$;
revoke all on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_review_product(text,uuid,timestamptz,text,text,text) to service_role;

create or replace function public.adishop_qa_review_image(
  p_session_jti text, p_image_id uuid, p_expected_revision integer,
  p_decision text, p_alt_text text default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  target_product uuid;
  target_store uuid;
  owner_id uuid;
  image_row record;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
    for share of ur;
  if not found then raise exception 'review-forbidden' using errcode = '42501'; end if;
  if p_decision not in ('approve','reject') or p_expected_revision is null or p_expected_revision < 1 then
    raise exception 'invalid-review-input' using errcode = '22023'; end if;

  select product_id into target_product from public.shop_product_images where id = p_image_id;
  select store_id into target_store from public.shop_products where id = target_product;
  select owner_profile_id into owner_id from public.shop_stores where id = target_store for share;
  if owner_id = actor then raise exception 'self-review-forbidden' using errcode = '42501'; end if;

  perform 1 from public.shop_products where id = target_product and store_id = target_store for update;
  select id, product_id, revision, storage_path, is_primary, display_order into image_row
    from public.shop_product_images where id = p_image_id and product_id = target_product for update;
  if image_row.id is null then raise exception 'image-not-found' using errcode = '22023'; end if;
  if image_row.revision <> p_expected_revision then raise exception 'stale-revision' using errcode = '40001'; end if;

  if p_decision = 'approve' then
    if image_row.storage_path is null or
       split_part(image_row.storage_path, '/', 1) <> 'seller-products' or
       split_part(image_row.storage_path, '/', 3) <> target_store::text or
       split_part(image_row.storage_path, '/', 4) <> target_product::text or
       split_part(image_row.storage_path, '/', 5) = '' or
       image_row.storage_path ~ '(\\|%|(^|/)\.\.?(/|$)|[[:cntrl:]])' then
      raise exception 'invalid-private-media-path' using errcode = '22023';
    end if;
    insert into public.shop_qa_reviewed_images(image_id, image_revision, reviewed_by_profile_id,
      alt_text, is_primary, display_order)
      values(image_row.id, image_row.revision, actor, nullif(btrim(p_alt_text),''),
        image_row.is_primary, image_row.display_order)
      on conflict(image_id) do update set image_revision = excluded.image_revision,
        reviewed_by_profile_id = excluded.reviewed_by_profile_id, reviewed_at = now(),
        alt_text = excluded.alt_text, is_primary = excluded.is_primary, display_order = excluded.display_order;
  else
    delete from public.shop_qa_reviewed_images where image_id = p_image_id;
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values(actor, 'qa.media.' || p_decision, 'shop_product_image', p_image_id::text,
      jsonb_build_object('revision', p_expected_revision));
  return jsonb_build_object('imageId', p_image_id, 'decision', p_decision, 'revision', p_expected_revision);
end $$;
revoke all on function public.adishop_qa_review_image(text,uuid,integer,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_review_image(text,uuid,integer,text,text) to service_role;

create or replace function public.adishop_qa_media_locator(p_kind text, p_id uuid)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('kind', p_kind, 'id', case when p_kind = 'image' then image.id else product.id end,
    'revision', image.revision, 'bucket', 'shop-product-images', 'storagePath', image.storage_path,
    'productId', product.id, 'storeId', product.store_id)
  from public.shop_product_images image
  join public.shop_products product on product.id = image.product_id
  join public.shop_qa_public_catalog reviewed_product on reviewed_product.id = product.id
  join public.shop_qa_reviewed_images review on review.image_id = image.id
    and review.image_revision = image.revision
  join public.shop_profiles reviewer on reviewer.id = review.reviewed_by_profile_id and reviewer.status = 'active'
  join public.shop_user_roles ur on ur.profile_id = reviewer.id and ur.revoked_at is null
  join public.shop_roles role on role.id = ur.role_id and role.code in ('SHOP_ADMIN','SHOP_SUPER_ADMIN')
  join public.shop_stores store on store.id = product.store_id
  where p_kind in ('image','product-cover') and image.storage_path is not null
    and review.reviewed_by_profile_id is distinct from store.owner_profile_id
    and (p_kind = 'image' and image.id = p_id or p_kind = 'product-cover' and product.id = p_id)
  order by image.is_primary desc, image.display_order, image.id limit 1;
$$;
revoke all on function public.adishop_qa_media_locator(text,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_media_locator(text,uuid) to service_role;

-- ============================================================================
-- 10. SELLER PRODUCT MUTATION RPC
-- ============================================================================
create or replace function public.adishop_qa_seller_product_edit(
  p_session_jti text, p_store_id uuid, p_product_id uuid, p_expected_updated_at timestamptz,
  p_action text, p_title text, p_price_minor integer, p_variant_id uuid default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  product_row record;
  variant_row record;
  updated_revision timestamptz;
  owner_id uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles role on role.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null and role.code = 'SELLER' for share of ur;
  if not found then raise exception 'seller-forbidden' using errcode = '42501'; end if;

  select store.owner_profile_id into owner_id from public.shop_stores store where store.id = p_store_id
    and store.status = 'active' and store.approval_status = 'approved' for share;
  if not found then raise exception 'store-forbidden' using errcode = '42501'; end if;

  if owner_id is distinct from actor then
    perform 1 from public.shop_store_members member where member.store_id = p_store_id
      and member.profile_id = actor and member.status = 'active'
      and member.role in ('owner','admin','manager') for share;
    if not found then raise exception 'store-forbidden' using errcode = '42501'; end if;
  end if;

  if p_action not in ('edit_product','edit_variant') or
     length(btrim(coalesce(p_title,''))) not between 2 and 160 or
     p_price_minor is null or p_price_minor < 0 or p_expected_updated_at is null then
    raise exception 'invalid-seller-edit' using errcode = '22023'; end if;

  select id, updated_at into product_row from public.shop_products
    where id = p_product_id and store_id = p_store_id and deleted_at is null for update;
  if product_row.id is null then raise exception 'product-not-found' using errcode = '22023'; end if;

  if p_action = 'edit_product' then
    if product_row.updated_at is distinct from p_expected_updated_at then
      raise exception 'stale-revision' using errcode = '40001'; end if;
    update public.shop_products set title = btrim(p_title), price_minor = p_price_minor
      where id = p_product_id returning updated_at into updated_revision;
  else
    if p_variant_id is null then raise exception 'variant-required' using errcode = '22023'; end if;
    select id, updated_at into variant_row from public.shop_product_variants
      where id = p_variant_id and product_id = p_product_id for update;
    if variant_row.id is null then raise exception 'variant-not-found' using errcode = '22023'; end if;
    if variant_row.updated_at is distinct from p_expected_updated_at then
      raise exception 'stale-revision' using errcode = '40001'; end if;
    update public.shop_product_variants set title = btrim(p_title), price_minor = p_price_minor,
      updated_at = clock_timestamp() where id = p_variant_id returning updated_at into updated_revision;
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
    values(actor, 'qa.seller.' || p_action, case when p_variant_id is null then 'shop_product' else 'shop_product_variant' end,
      coalesce(p_variant_id, p_product_id)::text, jsonb_build_object('storeId', p_store_id));
  return jsonb_build_object('productId', p_product_id, 'variantId', p_variant_id, 'revision', updated_revision);
end $$;
revoke all on function public.adishop_qa_seller_product_edit(text,uuid,uuid,timestamptz,text,text,integer,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_seller_product_edit(text,uuid,uuid,timestamptz,text,text,integer,uuid) to service_role;

-- ============================================================================
-- 11. BUYER ACTIONS (Cart, Favorites, Addresses)
-- ============================================================================
create or replace function public.adishop_qa_favorite_action(
  p_session_jti text, p_action text, p_product_id uuid
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if p_product_id is null or p_action not in ('add','remove') then
    raise exception 'invalid-favorite-input' using errcode = '22023';
  end if;
  if p_action = 'add' then
    if not exists (select 1 from public.shop_qa_public_catalog where id = p_product_id) then
      raise exception 'product-unavailable' using errcode = '22023';
    end if;
    insert into public.shop_favorites(profile_id, product_id)
      values(actor, p_product_id) on conflict(profile_id, product_id) do nothing;
  else
    delete from public.shop_favorites where profile_id = actor and product_id = p_product_id;
  end if;
  select jsonb_build_object('id', id, 'product_id', product_id, 'created_at', created_at)
    into result from public.shop_favorites where profile_id = actor and product_id = p_product_id;
  return coalesce(result, '{}'::jsonb);
end $$;
revoke all on function public.adishop_qa_favorite_action(text,text,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_favorite_action(text,text,uuid) to service_role;

create or replace function public.adishop_qa_address_action(
  p_session_jti text, p_action text, p_address_id uuid default null,
  p_label text default null, p_recipient_name text default null, p_phone text default null,
  p_city text default null, p_address_line1 text default null,
  p_address_line2 text default null, p_is_default boolean default false
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  result jsonb;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if p_action not in ('create','update','delete','set_default') then
    raise exception 'invalid-address-action' using errcode = '22023';
  end if;

  if p_action in ('update','delete','set_default') and not exists (
    select 1 from public.shop_addresses where id = p_address_id and profile_id = actor
      and deleted_at is null for update) then
    raise exception 'address-not-found' using errcode = '22023';
  end if;

  if p_action in ('create','update') then
    if length(btrim(coalesce(p_recipient_name,''))) not between 1 and 120 or
       length(btrim(coalesce(p_phone,''))) not between 1 and 40 or
       length(btrim(coalesce(p_city,''))) not between 1 and 120 or
       length(btrim(coalesce(p_address_line1,''))) not between 1 and 240 or
       (p_label is not null and length(btrim(p_label)) > 60) or
       (p_address_line2 is not null and length(btrim(p_address_line2)) > 240) then
      raise exception 'invalid-address-input' using errcode = '22023';
    end if;
  end if;

  if p_is_default or p_action = 'set_default' then
    update public.shop_addresses set is_default = false, updated_at = now()
      where profile_id = actor and is_default = true;
  end if;

  if p_action = 'create' then
    insert into public.shop_addresses(
      profile_id, label, recipient_name, phone, city, address_line1, address_line2, is_default
    ) values (
      actor, nullif(btrim(p_label),''), btrim(p_recipient_name), btrim(p_phone),
      btrim(p_city), btrim(p_address_line1), nullif(btrim(p_address_line2),''), coalesce(p_is_default, false)
    ) returning jsonb_build_object(
      'id', id, 'label', label, 'recipient_name', recipient_name, 'phone', phone,
      'city', city, 'address_line1', address_line1, 'address_line2', address_line2,
      'is_default', is_default, 'created_at', created_at
    ) into result;
  elsif p_action = 'update' then
    update public.shop_addresses set
      label = nullif(btrim(p_label),''), recipient_name = btrim(p_recipient_name),
      phone = btrim(p_phone), city = btrim(p_city), address_line1 = btrim(p_address_line1),
      address_line2 = nullif(btrim(p_address_line2),''),
      is_default = case when p_is_default is not null then p_is_default else is_default end,
      updated_at = now()
    where id = p_address_id and profile_id = actor
    returning jsonb_build_object(
      'id', id, 'label', label, 'recipient_name', recipient_name, 'phone', phone,
      'city', city, 'address_line1', address_line1, 'address_line2', address_line2,
      'is_default', is_default, 'created_at', created_at
    ) into result;
  elsif p_action = 'set_default' then
    update public.shop_addresses set is_default = true, updated_at = now()
    where id = p_address_id and profile_id = actor
    returning jsonb_build_object(
      'id', id, 'label', label, 'recipient_name', recipient_name, 'phone', phone,
      'city', city, 'address_line1', address_line1, 'address_line2', address_line2,
      'is_default', is_default, 'created_at', created_at
    ) into result;
  elsif p_action = 'delete' then
    update public.shop_addresses set deleted_at = now() where id = p_address_id and profile_id = actor;
    result := jsonb_build_object('id', p_address_id, 'deleted', true);
  end if;
  return result;
end $$;
revoke all on function public.adishop_qa_address_action(text,text,uuid,text,text,text,text,text,text,boolean) from public, anon, authenticated;
grant execute on function public.adishop_qa_address_action(text,text,uuid,text,text,text,text,text,text,boolean) to service_role;

create or replace function public.adishop_qa_cart_action(
  p_session_jti text, p_action text, p_product_id uuid default null,
  p_variant_id uuid default null, p_item_id uuid default null, p_quantity integer default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  actor uuid;
  v_cart_id uuid;
  line record;
  unit_price integer;
  v_product_price integer;
  v_variant_adj integer;
  available integer;
  current_quantity integer;
  variants_exist boolean;
  result jsonb;
  subtotal_calc bigint := 0;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if p_action not in ('read','add','update','remove','clear') then
    raise exception 'invalid-cart-action' using errcode = '22023'; end if;

  select id into v_cart_id from public.shop_carts
    where profile_id = actor and status = 'active' and currency = 'ETB' for update;

  if p_action = 'clear' then
    if v_cart_id is not null then
      delete from public.shop_cart_items where cart_id = v_cart_id;
    end if;
  elsif p_action = 'add' then
    if p_product_id is null or p_quantity not between 1 and 99 then
      raise exception 'invalid-cart-input' using errcode = '22023'; end if;
    select price_minor into v_product_price from public.shop_qa_public_catalog where id = p_product_id;
    if v_product_price is null then raise exception 'product-unavailable' using errcode = '22023'; end if;
    select exists(select 1 from public.shop_product_variants
      where product_id = p_product_id and status = 'active') into variants_exist;
    if variants_exist and p_variant_id is null then
      raise exception 'variant-required' using errcode = '22023'; end if;
    if p_variant_id is not null then
      select reviewed.price_minor into unit_price
        from public.shop_product_variants variant
        join public.shop_qa_reviewed_variants reviewed on reviewed.variant_id = variant.id
          and reviewed.variant_updated_at = variant.updated_at
        where variant.id = p_variant_id and variant.product_id = p_product_id and variant.status = 'active'
          and public.adishop_qa_variant_reviewed(variant.id,
            (select store_id from public.shop_products where id = p_product_id));
      if unit_price is null then raise exception 'variant-unavailable' using errcode = '22023'; end if;
    else
      unit_price := v_product_price;
    end if;

    if v_cart_id is null then
      insert into public.shop_carts(profile_id, status, currency) values(actor, 'active', 'ETB') returning id into v_cart_id;
    end if;

    select id, quantity into line from public.shop_cart_items
      where cart_id = v_cart_id and product_id = p_product_id
        and variant_id is not distinct from p_variant_id for update;
    current_quantity := coalesce(line.quantity, 0) + p_quantity;

  elsif p_action = 'update' or p_action = 'remove' then
    if v_cart_id is null or p_item_id is null then raise exception 'cart-item-not-found' using errcode = '22023'; end if;
    select id, product_id, variant_id, quantity into line from public.shop_cart_items
      where id = p_item_id and cart_id = v_cart_id for update;
    if line.id is null then raise exception 'cart-item-not-found' using errcode = '22023'; end if;
    if p_action = 'update' then
      if p_quantity not between 1 and 99 then raise exception 'invalid-quantity' using errcode = '22023'; end if;
      current_quantity := p_quantity;
      p_product_id := line.product_id;
      p_variant_id := line.variant_id;
    end if;
  end if;

  if p_action in ('add','update') then
    select quantity_available - quantity_reserved into available from public.shop_inventory
      where product_id = p_product_id and variant_id is not distinct from p_variant_id for share;
    if available is null or current_quantity > available then
      raise exception 'insufficient-inventory' using errcode = '22023'; end if;
    if p_action = 'add' then
      if line.id is null then
        insert into public.shop_cart_items(cart_id, product_id, variant_id, quantity, unit_price_minor)
          values(v_cart_id, p_product_id, p_variant_id, current_quantity, unit_price);
      else
        update public.shop_cart_items set quantity = current_quantity, unit_price_minor = unit_price,
          updated_at = now() where id = line.id;
      end if;
    else
      update public.shop_cart_items set quantity = current_quantity, updated_at = now() where id = line.id;
    end if;
  elsif p_action = 'remove' then
    delete from public.shop_cart_items where id = line.id;
  end if;

  if v_cart_id is null then
    return jsonb_build_object(
      'id', gen_random_uuid(), 'currency', 'ETB',
      'totals', jsonb_build_object('subtotalMinor', 0, 'deliveryFeeMinor', 0, 'discountMinor', 0, 'totalMinor', 0, 'itemCount', 0),
      'items', '[]'::jsonb
    );
  end if;

  select coalesce(sum(quantity::bigint * unit_price_minor::bigint), 0)
    into subtotal_calc from public.shop_cart_items where cart_id = v_cart_id;

  select jsonb_build_object(
    'id', v_cart_id,
    'currency', 'ETB',
    'totals', jsonb_build_object(
      'subtotalMinor', subtotal_calc,
      'deliveryFeeMinor', 0,
      'discountMinor', 0,
      'totalMinor', subtotal_calc,
      'itemCount', coalesce((select sum(quantity) from public.shop_cart_items where cart_id = v_cart_id), 0)
    ),
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', item.id,
        'cart_id', item.cart_id,
        'product_id', item.product_id,
        'variant_id', item.variant_id,
        'quantity', item.quantity,
        'unit_price_minor', item.unit_price_minor,
        'created_at', item.created_at,
        'shop_products', (
          select jsonb_build_object(
            'id', cat.id,
            'title', cat.title,
            'price_minor', cat.price_minor,
            'currency', cat.currency,
            'stock_status', cat.stock_status,
            'shop_stores', jsonb_build_object('id', cat.store_id, 'name', cat.store_name),
            'shop_product_images', '[]'::jsonb
          )
          from public.shop_qa_public_catalog cat
          where cat.id = item.product_id
        ),
        'shop_product_variants', (
          select jsonb_build_object(
            'id', v.id,
            'title', rv.title,
            'options', rv.options,
            'price_minor', rv.price_minor,
            'status', v.status
          )
          from public.shop_product_variants v
          join public.shop_qa_reviewed_variants rv on rv.variant_id = v.id and rv.variant_updated_at = v.updated_at
          where v.id = item.variant_id and v.status = 'active'
        )
      ) order by item.created_at)
      from public.shop_cart_items item
      where item.cart_id = v_cart_id
    ), '[]'::jsonb)
  ) into result;

  return result;
end $$;
revoke all on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_cart_action(text,text,uuid,uuid,uuid,integer) to service_role;

-- ============================================================================
-- 12. ROW LEVEL SECURITY & EXPLICIT ROLE GRANTS
-- ============================================================================
do $$ declare relation text; begin
  foreach relation in array array[
    'shop_categories','shop_stores','shop_store_members','shop_seller_applications',
    'shop_products','shop_product_variants','shop_product_images','shop_inventory',
    'shop_favorites','shop_carts','shop_cart_items','shop_addresses',
    'shop_orders','shop_order_groups','shop_order_items','shop_order_status_history',
    'shop_qa_reviewed_catalog','shop_qa_reviewed_stores','shop_qa_reviewed_variants','shop_qa_reviewed_images'
  ] loop
    execute format('alter table public.%I enable row level security', relation);
    execute format('revoke all on public.%I from public, anon, authenticated', relation);
    if relation in ('shop_orders','shop_order_groups','shop_order_items','shop_order_status_history',
                    'shop_qa_reviewed_catalog','shop_qa_reviewed_stores','shop_qa_reviewed_variants','shop_qa_reviewed_images') then
      execute format('grant select on public.%I to service_role', relation);
    else
      execute format('grant select,insert,update,delete on public.%I to service_role', relation);
    end if;
  end loop;
end $$;

revoke all on public.shop_qa_public_catalog from public, anon, authenticated;
revoke all on public.shop_qa_public_stores from public, anon, authenticated;
grant usage on schema public to anon, authenticated;

create policy shop_qa_public_categories on public.shop_categories for select to anon, authenticated
  using (is_active and buyer_visible);
grant select(id, parent_id, name, slug, code, description, icon, level, is_leaf,
  seller_selectable, buyer_visible, search_terms, display_order)
  on public.shop_categories to anon, authenticated;

grant select on public.shop_qa_public_catalog to anon, authenticated, service_role;
grant select on public.shop_qa_public_stores to anon, authenticated, service_role;
grant select on public.shop_qa_reviewed_variants to service_role;

commit;
