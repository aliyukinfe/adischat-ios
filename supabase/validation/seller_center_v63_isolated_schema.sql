-- Local QA only. Load into a disposable PostgreSQL database, never production.
create schema storage;
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then
    create role service_role nologin bypassrls;
  end if;
end;
$$;

create table public.shop_profiles (id uuid primary key);
create table public.shop_stores (
  id uuid primary key,
  owner_profile_id uuid not null references public.shop_profiles(id),
  name text not null default 'Controlled QA store',
  slug text not null default 'controlled-qa-store',
  description text,
  logo_url text,
  cover_url text,
  category text,
  city text,
  status text not null default 'active',
  approval_status text not null default 'approved',
  average_store_rating numeric not null default 0,
  total_sales integer not null default 0,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table public.shop_categories (
  id uuid primary key,
  is_leaf boolean not null,
  seller_selectable boolean not null,
  is_active boolean not null
);
create table public.shop_products (
  id uuid primary key,
  store_id uuid not null references public.shop_stores(id),
  category_id uuid references public.shop_categories(id),
  slug text not null default 'controlled-qa-product',
  brand text,
  category_slug text,
  short_description text,
  title text not null,
  description text,
  price_minor integer not null,
  selling_unit_code text not null default 'PIECE',
  minimum_order_quantity integer not null default 1,
  price_max_minor integer,
  has_variants boolean not null default false,
  aggregate_available_quantity integer,
  compare_at_price_minor integer,
  discount_percent integer,
  rating_average numeric not null default 0,
  rating_count integer not null default 0,
  completed_sales_count integer not null default 0,
  stock_status text not null default 'in_stock',
  status text not null default 'draft',
  moderation_status text not null default 'pending',
  submitted_at timestamptz,
  submitted_version integer not null default 0,
  reviewed_at timestamptz,
  reviewed_by_profile_id uuid,
  published_at timestamptz,
  suspended_at timestamptz,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  deleted_at timestamptz,
  validation_errors jsonb not null default '[]'::jsonb
);
create table public.shop_product_images (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  store_id uuid not null references public.shop_stores(id),
  bucket text not null default 'shop-product-images',
  storage_path text,
  image_url text not null,
  is_primary boolean not null default false,
  display_order integer not null default 0,
  alt_text text,
  deleted_at timestamptz
);
create table storage.objects (
  bucket_id text not null,
  name text not null,
  primary key (bucket_id, name)
);
create table public.shop_inventory (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  variant_id uuid,
  inventory_scope text not null default 'sellable',
  quantity_available integer not null default 0,
  quantity_reserved integer not null default 0
);
create table public.shop_product_options (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  code text not null,
  status text not null default 'active'
);
create table public.shop_product_option_values (
  id uuid primary key,
  product_option_id uuid not null references public.shop_product_options(id)
);
create table public.shop_product_variants (
  id uuid primary key,
  product_id uuid not null references public.shop_products(id),
  price_minor integer not null,
  status text not null default 'active'
);
create table public.shop_variant_option_values (
  id uuid primary key,
  variant_id uuid not null references public.shop_product_variants(id)
);
create table public.shop_attribute_definitions (id uuid primary key, code text not null);
create table public.shop_category_attributes (
  category_id uuid not null references public.shop_categories(id),
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id),
  required boolean not null default false
);
create table public.shop_product_attribute_values (
  product_id uuid not null references public.shop_products(id),
  attribute_definition_id uuid not null references public.shop_attribute_definitions(id)
);
create table public.shop_product_status_history (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id),
  actor_profile_id uuid not null,
  actor_role text not null,
  event_code text not null,
  previous_status text not null,
  next_status text not null,
  previous_moderation_status text not null,
  next_moderation_status text not null,
  public_message text,
  request_id text unique
);
create table public.shop_admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid,
  action_type text not null,
  target_type text not null,
  target_id uuid,
  previous_state jsonb not null default '{}'::jsonb,
  new_state jsonb not null default '{}'::jsonb,
  reason text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text unique
);
create table public.shop_product_moderation_reviews (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id),
  reviewer_profile_id uuid,
  decision text not null,
  reason_code text,
  public_reason text,
  private_notes text,
  previous_status text not null,
  new_status text not null,
  snapshot jsonb not null default '{}'::jsonb
);
create table public.shop_notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null,
  type text not null,
  title text not null,
  body text not null,
  data jsonb not null default '{}'::jsonb
);
create table public.shop_roles (id uuid primary key, code text not null unique);
create table public.shop_user_roles (
  profile_id uuid not null references public.shop_profiles(id),
  role_id uuid not null references public.shop_roles(id),
  revoked_at timestamptz,
  primary key (profile_id, role_id)
);
create table public.shop_role_permissions (
  role_id uuid not null references public.shop_roles(id),
  permission text not null,
  primary key (role_id, permission)
);

alter table public.shop_products enable row level security;
alter table public.shop_stores enable row level security;
alter table public.shop_product_images enable row level security;
create policy public_read_approved_stores on public.shop_stores
  for select to anon, authenticated
  using (status = 'active' and approval_status = 'approved' and deleted_at is null);
create policy public_read_approved_products on public.shop_products
  for select to anon, authenticated
  using (status = 'active' and moderation_status = 'approved' and deleted_at is null);
create policy public_read_approved_product_images on public.shop_product_images
  for select to anon, authenticated using (
    exists (
      select 1 from public.shop_products p
      join public.shop_stores s on s.id = p.store_id
      where p.id = shop_product_images.product_id
        and p.status = 'active' and p.moderation_status = 'approved' and p.deleted_at is null
        and s.status = 'active' and s.approval_status = 'approved' and s.deleted_at is null
    )
  );
grant usage on schema public to anon, authenticated, service_role;
grant select on public.shop_products, public.shop_stores, public.shop_product_images to anon, authenticated;
grant all on all tables in schema public to service_role;

create or replace function public.adishop_profile_has_permission(p_profile_id uuid, p_permission text)
returns boolean language sql stable as $$
  select exists (
    select 1
    from public.shop_user_roles ur
    join public.shop_roles r on r.id = ur.role_id
    join public.shop_role_permissions rp on rp.role_id = r.id
    where ur.profile_id = p_profile_id
      and ur.revoked_at is null
      and (rp.permission = p_permission or r.code = 'SHOP_SUPER_ADMIN')
  )
$$;
create function public.adishop_seller_owned_store(p_profile_id uuid)
returns public.shop_stores language plpgsql stable as $$
declare result public.shop_stores%rowtype;
begin
  select * into result from public.shop_stores
  where owner_profile_id = p_profile_id and status = 'active' and deleted_at is null;
  if not found then raise exception 'seller-store-not-found'; end if;
  return result;
end;
$$;
create function public.adishop_seller_product_summary(p_product public.shop_products)
returns jsonb language sql stable as $$ select to_jsonb(p_product) $$;
create function public.adishop_admin_get_product(p_actor_profile_id uuid, p_product_id uuid)
returns jsonb language sql stable as $$
  select to_jsonb(p) from public.shop_products p where p.id = p_product_id
$$;
create function public.adishop_admin_audit(
  p_actor_profile_id uuid, p_action_type text, p_target_type text, p_target_id uuid,
  p_previous_state jsonb, p_new_state jsonb, p_reason text, p_metadata jsonb, p_request_id text
) returns uuid language plpgsql as $$
declare result uuid;
begin
  insert into public.shop_admin_audit_logs
    (actor_profile_id, action_type, target_type, target_id, previous_state, new_state, reason, metadata, request_id)
  values (p_actor_profile_id, p_action_type, p_target_type, p_target_id,
    p_previous_state, p_new_state, p_reason, p_metadata, p_request_id)
  returning id into result;
  return result;
end;
$$;
create function public.adishop_create_notification(
  p_profile_id uuid, p_type text, p_title text, p_body text, p_data jsonb
) returns uuid language plpgsql as $$
declare result uuid;
begin
  insert into public.shop_notifications(recipient_id, type, title, body, data)
  values(p_profile_id, p_type, p_title, p_body, p_data)
  returning id into result;
  return result;
end;
$$;
