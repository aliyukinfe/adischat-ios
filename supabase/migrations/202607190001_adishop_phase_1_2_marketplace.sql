-- AdisShop Phase 1.2 native marketplace foundation.
-- Safe to review before running against project mcewjfnkrtiiolzibsuh.
-- This migration intentionally avoids real payment settlement tables.

create extension if not exists pgcrypto;

create or replace function public.set_shop_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.shop_profiles (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid unique,
  adischat_user_id text unique,
  display_name text,
  phone text,
  email text,
  status text not null default 'active' check (status in ('active', 'suspended', 'deleted')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.shop_user_links (
  id uuid primary key default gen_random_uuid(),
  shop_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  provider text not null check (provider in ('firebase', 'cloudflare_session', 'desktop_qr', 'supabase')),
  provider_user_id text not null,
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  unique (provider, provider_user_id)
);

create table if not exists public.shop_categories (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references public.shop_categories(id) on delete set null,
  name text not null,
  slug text not null unique,
  description text,
  icon text,
  image_url text,
  is_active boolean not null default true,
  display_order integer not null default 0,
  is_seed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_stores (
  id uuid primary key default gen_random_uuid(),
  owner_profile_id uuid references public.shop_profiles(id) on delete set null,
  name text not null,
  slug text not null unique,
  description text,
  category text,
  city text,
  logo_url text,
  cover_url text,
  status text not null default 'active' check (status in ('draft', 'active', 'paused', 'suspended', 'closed')),
  approval_status text not null default 'approved' check (approval_status in ('not_applied', 'submitted', 'under_review', 'approved', 'rejected', 'suspended')),
  average_store_rating numeric(3,2) not null default 0 check (average_store_rating >= 0 and average_store_rating <= 5),
  total_sales integer not null default 0 check (total_sales >= 0),
  is_seed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.shop_store_members (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  role text not null default 'owner' check (role in ('owner', 'admin', 'manager', 'staff')),
  status text not null default 'active' check (status in ('active', 'invited', 'removed')),
  created_at timestamptz not null default now(),
  unique (store_id, profile_id)
);

create table if not exists public.shop_seller_applications (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  proposed_store_name text not null,
  business_type text,
  status text not null default 'submitted' check (status in ('draft', 'submitted', 'under_review', 'approved', 'rejected', 'suspended')),
  rejection_reason text,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_products (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  category_id uuid references public.shop_categories(id) on delete set null,
  brand_id uuid,
  brand text,
  title text not null,
  slug text not null,
  category_slug text,
  short_description text,
  description text,
  status text not null default 'draft' check (status in ('draft', 'active', 'paused', 'archived')),
  moderation_status text not null default 'pending' check (moderation_status in ('pending', 'approved', 'rejected')),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  price_minor integer not null check (price_minor >= 0),
  compare_at_price_minor integer check (compare_at_price_minor is null or compare_at_price_minor >= 0),
  discount_percent integer check (discount_percent is null or (discount_percent >= 0 and discount_percent <= 95)),
  stock_status text not null default 'in_stock' check (stock_status in ('in_stock', 'low_stock', 'out_of_stock', 'preorder')),
  rating_average numeric(3,2) not null default 0 check (rating_average >= 0 and rating_average <= 5),
  rating_count integer not null default 0 check (rating_count >= 0),
  completed_sales_count integer not null default 0 check (completed_sales_count >= 0),
  is_featured boolean not null default false,
  is_seed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (store_id, slug)
);

create table if not exists public.shop_product_variants (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  sku text,
  title text not null,
  options jsonb not null default '{}'::jsonb,
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  price_minor integer not null check (price_minor >= 0),
  compare_at_price_minor integer check (compare_at_price_minor is null or compare_at_price_minor >= 0),
  status text not null default 'active' check (status in ('active', 'paused', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  image_url text not null,
  storage_path text,
  alt_text text,
  is_primary boolean not null default false,
  display_order integer not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_inventory (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.shop_products(id) on delete cascade,
  variant_id uuid references public.shop_product_variants(id) on delete cascade,
  quantity_available integer not null default 0 check (quantity_available >= 0),
  quantity_reserved integer not null default 0 check (quantity_reserved >= 0),
  low_stock_threshold integer not null default 3 check (low_stock_threshold >= 0),
  updated_at timestamptz not null default now(),
  unique (product_id, variant_id)
);

create table if not exists public.shop_favorites (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (profile_id, product_id)
);

create table if not exists public.shop_recently_viewed (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete cascade,
  viewed_at timestamptz not null default now(),
  unique (profile_id, product_id)
);

create table if not exists public.shop_carts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  status text not null default 'active' check (status in ('active', 'submitted', 'abandoned')),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_cart_items (
  id uuid primary key default gen_random_uuid(),
  cart_id uuid not null references public.shop_carts(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  quantity integer not null check (quantity > 0 and quantity <= 99),
  unit_price_minor integer not null check (unit_price_minor >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (cart_id, product_id, variant_id)
);

create table if not exists public.shop_addresses (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
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

create table if not exists public.shop_orders (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  address_id uuid references public.shop_addresses(id) on delete set null,
  order_number text not null unique,
  status text not null default 'created' check (status in ('created', 'confirmed', 'preparing', 'ready', 'delivered', 'cancelled', 'rejected')),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  subtotal_minor integer not null default 0 check (subtotal_minor >= 0),
  delivery_fee_minor integer not null default 0 check (delivery_fee_minor >= 0),
  discount_minor integer not null default 0 check (discount_minor >= 0),
  total_minor integer not null default 0 check (total_minor >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_order_groups (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  status text not null default 'created' check (status in ('created', 'accepted', 'preparing', 'ready', 'delivered', 'cancelled', 'rejected')),
  subtotal_minor integer not null default 0 check (subtotal_minor >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (order_id, store_id)
);

create table if not exists public.shop_order_items (
  id uuid primary key default gen_random_uuid(),
  order_group_id uuid not null references public.shop_order_groups(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  title_snapshot text not null,
  quantity integer not null check (quantity > 0),
  unit_price_minor integer not null check (unit_price_minor >= 0),
  line_total_minor integer not null check (line_total_minor >= 0),
  created_at timestamptz not null default now()
);

create table if not exists public.shop_order_status_history (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete cascade,
  status text not null,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  note text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_reviews (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete cascade,
  order_item_id uuid references public.shop_order_items(id) on delete set null,
  rating integer not null check (rating between 1 and 5),
  body text,
  status text not null default 'published' check (status in ('pending', 'published', 'hidden', 'removed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (profile_id, product_id, order_item_id)
);

create table if not exists public.shop_store_reviews (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete set null,
  rating integer not null check (rating between 1 and 5),
  body text,
  status text not null default 'published' check (status in ('pending', 'published', 'hidden', 'removed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (profile_id, store_id, order_group_id)
);

create table if not exists public.shop_banners (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  subtitle text,
  image_url text,
  action_url text,
  is_active boolean not null default true,
  display_order integer not null default 0,
  start_at timestamptz,
  end_at timestamptz,
  is_seed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_campaigns (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  slug text not null unique,
  description text,
  image_url text,
  is_active boolean not null default true,
  start_at timestamptz,
  end_at timestamptz,
  is_seed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_campaign_products (
  campaign_id uuid not null references public.shop_campaigns(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete cascade,
  display_order integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (campaign_id, product_id)
);

create table if not exists public.shop_notifications (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  type text not null,
  title text not null,
  body text,
  data jsonb not null default '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id uuid,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists shop_categories_active_idx on public.shop_categories (is_active, display_order);
create index if not exists shop_stores_public_idx on public.shop_stores (status, approval_status, deleted_at);
create index if not exists shop_products_public_idx on public.shop_products (status, moderation_status, deleted_at, created_at desc);
create index if not exists shop_products_category_idx on public.shop_products (category_id, status, moderation_status, created_at desc);
create index if not exists shop_products_category_slug_idx on public.shop_products (category_slug, status, moderation_status, created_at desc);
create index if not exists shop_products_popular_idx on public.shop_products (completed_sales_count desc, rating_average desc);
create index if not exists shop_product_images_product_idx on public.shop_product_images (product_id, is_primary desc, display_order);
create index if not exists shop_inventory_product_idx on public.shop_inventory (product_id, variant_id);
create index if not exists shop_carts_profile_status_idx on public.shop_carts (profile_id, status, updated_at desc);
create index if not exists shop_cart_items_cart_idx on public.shop_cart_items (cart_id, updated_at desc);
create index if not exists shop_orders_profile_status_idx on public.shop_orders (profile_id, status, created_at desc);
create index if not exists shop_order_groups_store_idx on public.shop_order_groups (store_id, status, created_at desc);
create index if not exists shop_notifications_profile_idx on public.shop_notifications (profile_id, read_at, created_at desc);
create index if not exists shop_recently_viewed_profile_idx on public.shop_recently_viewed (profile_id, viewed_at desc);

do $$
declare
  t text;
begin
  foreach t in array array[
    'shop_profiles','shop_user_links','shop_categories','shop_stores','shop_store_members',
    'shop_seller_applications','shop_products','shop_product_variants','shop_product_images',
    'shop_inventory','shop_favorites','shop_recently_viewed','shop_carts','shop_cart_items',
    'shop_addresses','shop_orders','shop_order_groups','shop_order_items',
    'shop_order_status_history','shop_reviews','shop_store_reviews','shop_banners',
    'shop_campaigns','shop_campaign_products','shop_notifications','shop_audit_logs'
  ] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

create or replace view public.shop_public_products with (security_invoker = true) as
select
  p.id, p.store_id, p.category_id, p.brand_id, p.brand, p.title, p.slug, p.category_slug,
  p.short_description, p.description, p.currency, p.price_minor, p.compare_at_price_minor,
  p.discount_percent, p.stock_status, p.rating_average, p.rating_count,
  p.completed_sales_count, p.is_featured, p.created_at, p.updated_at
from public.shop_products p
join public.shop_stores s on s.id = p.store_id
where p.status = 'active'
  and p.moderation_status = 'approved'
  and p.deleted_at is null
  and s.status = 'active'
  and s.approval_status = 'approved'
  and s.deleted_at is null;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_categories' and policyname = 'public_read_active_categories') then
    create policy public_read_active_categories on public.shop_categories for select to anon, authenticated using (is_active = true);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_stores' and policyname = 'public_read_approved_stores') then
    create policy public_read_approved_stores on public.shop_stores for select to anon, authenticated using (status = 'active' and approval_status = 'approved' and deleted_at is null);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_products' and policyname = 'public_read_approved_products') then
    create policy public_read_approved_products on public.shop_products for select to anon, authenticated using (status = 'active' and moderation_status = 'approved' and deleted_at is null);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_product_images' and policyname = 'public_read_approved_product_images') then
    create policy public_read_approved_product_images on public.shop_product_images for select to anon, authenticated using (
      exists (
        select 1
        from public.shop_products p
        join public.shop_stores s on s.id = p.store_id
        where p.id = shop_product_images.product_id
          and p.status = 'active'
          and p.moderation_status = 'approved'
          and p.deleted_at is null
          and s.status = 'active'
          and s.approval_status = 'approved'
          and s.deleted_at is null
      )
    );
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_reviews' and policyname = 'public_read_published_reviews') then
    create policy public_read_published_reviews on public.shop_reviews for select to anon, authenticated using (status = 'published');
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_store_reviews' and policyname = 'public_read_published_store_reviews') then
    create policy public_read_published_store_reviews on public.shop_store_reviews for select to anon, authenticated using (status = 'published');
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_banners' and policyname = 'public_read_active_banners') then
    create policy public_read_active_banners on public.shop_banners for select to anon, authenticated using (
      is_active = true
      and (start_at is null or start_at <= now())
      and (end_at is null or end_at >= now())
    );
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_campaigns' and policyname = 'public_read_active_campaigns') then
    create policy public_read_active_campaigns on public.shop_campaigns for select to anon, authenticated using (
      is_active = true
      and (start_at is null or start_at <= now())
      and (end_at is null or end_at >= now())
    );
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'shop_campaign_products' and policyname = 'public_read_campaign_products') then
    create policy public_read_campaign_products on public.shop_campaign_products for select to anon, authenticated using (
      exists (select 1 from public.shop_campaigns c where c.id = campaign_id and c.is_active = true)
      and exists (select 1 from public.shop_products p where p.id = product_id and p.status = 'active' and p.moderation_status = 'approved' and p.deleted_at is null)
    );
  end if;
end $$;

grant usage on schema public to anon, authenticated;
grant select on
  public.shop_categories,
  public.shop_stores,
  public.shop_products,
  public.shop_product_images,
  public.shop_reviews,
  public.shop_store_reviews,
  public.shop_banners,
  public.shop_campaigns,
  public.shop_campaign_products,
  public.shop_public_products
to anon, authenticated;
grant select on
  public.shop_profiles,
  public.shop_user_links,
  public.shop_store_members,
  public.shop_seller_applications,
  public.shop_product_variants,
  public.shop_inventory,
  public.shop_favorites,
  public.shop_recently_viewed,
  public.shop_carts,
  public.shop_cart_items,
  public.shop_addresses,
  public.shop_orders,
  public.shop_order_groups,
  public.shop_order_items,
  public.shop_order_status_history,
  public.shop_notifications
to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('shop-product-images', 'shop-product-images', true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('shop-store-assets', 'shop-store-assets', true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('shop-category-images', 'shop-category-images', true, 2097152, array['image/jpeg','image/png','image/webp']),
  ('shop-campaign-assets', 'shop-campaign-assets', true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('shop-seller-documents', 'shop-seller-documents', false, 10485760, array['image/jpeg','image/png','application/pdf']),
  ('shop-dispute-evidence', 'shop-dispute-evidence', false, 10485760, array['image/jpeg','image/png','application/pdf']),
  ('shop-return-evidence', 'shop-return-evidence', false, 10485760, array['image/jpeg','image/png','application/pdf'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname = 'public_read_shop_assets') then
    create policy public_read_shop_assets on storage.objects for select using (
      bucket_id in ('shop-product-images', 'shop-store-assets', 'shop-category-images', 'shop-campaign-assets')
    );
  end if;
end $$;

do $$
declare
  tbl text;
  trigger_name text;
begin
  foreach tbl in array array[
    'shop_profiles','shop_categories','shop_stores','shop_seller_applications','shop_products',
    'shop_product_variants','shop_carts','shop_cart_items','shop_addresses','shop_orders',
    'shop_order_groups','shop_reviews','shop_store_reviews','shop_banners','shop_campaigns'
  ] loop
    trigger_name := format('set_%s_updated_at', tbl);
    if not exists (
      select 1
      from pg_trigger
      where tgname = trigger_name
        and tgrelid = format('public.%I', tbl)::regclass
    ) then
      execute format('create trigger %I before update on public.%I for each row execute function public.set_shop_updated_at()', trigger_name, tbl);
    end if;
  end loop;
end $$;

-- Small removable seed catalog. Replace image_url values with Storage URLs after uploading assets.
do $$
declare
  electronics uuid := '00000000-0000-4000-8000-000000000101';
  fashion uuid := '00000000-0000-4000-8000-000000000102';
  home_living uuid := '00000000-0000-4000-8000-000000000103';
  beauty uuid := '00000000-0000-4000-8000-000000000104';
  groceries uuid := '00000000-0000-4000-8000-000000000105';
  books uuid := '00000000-0000-4000-8000-000000000106';
  sports uuid := '00000000-0000-4000-8000-000000000107';
  toys uuid := '00000000-0000-4000-8000-000000000108';
  store_one uuid := '00000000-0000-4000-8000-000000000201';
  store_two uuid := '00000000-0000-4000-8000-000000000202';
  store_three uuid := '00000000-0000-4000-8000-000000000203';
begin
  if exists (select 1 from public.shop_products limit 1) then
    return;
  end if;

  insert into public.shop_categories (id, name, slug, icon, display_order, is_seed)
  values
    (electronics, 'Electronics', 'electronics', 'TV', 1, true),
    (fashion, 'Fashion', 'fashion', 'Shirt', 2, true),
    (home_living, 'Home', 'home', 'Home', 3, true),
    (beauty, 'Beauty', 'beauty', 'Beauty', 4, true),
    (groceries, 'Groceries', 'groceries', 'Food', 5, true),
    (books, 'Books', 'books', 'Book', 6, true),
    (sports, 'Sports', 'sports', 'Ball', 7, true),
    (toys, 'Toys', 'toys', 'Toy', 8, true)
  on conflict (slug) do nothing;

  insert into public.shop_stores (id, name, slug, category, city, status, approval_status, average_store_rating, total_sales, is_seed)
  values
    (store_one, 'Addis Market Hub', 'addis-market-hub', 'General', 'Addis Ababa', 'active', 'approved', 4.8, 128, true),
    (store_two, 'Habesha Essentials', 'habesha-essentials', 'Home and fashion', 'Addis Ababa', 'active', 'approved', 4.6, 86, true),
    (store_three, 'Tech Addis', 'tech-addis', 'Electronics', 'Addis Ababa', 'active', 'approved', 4.7, 74, true)
  on conflict (slug) do nothing;

  insert into public.shop_products
    (store_id, category_id, brand, title, slug, category_slug, short_description, status, moderation_status, currency, price_minor, compare_at_price_minor, discount_percent, stock_status, rating_average, rating_count, completed_sales_count, is_featured, is_seed)
  values
    (store_three, electronics, 'AdisTech', 'Wireless Earbuds', 'wireless-earbuds', 'electronics', 'Compact Bluetooth earbuds with charging case.', 'active', 'approved', 'ETB', 245000, 290000, 15, 'in_stock', 4.7, 28, 81, true, true),
    (store_three, electronics, 'AdisTech', 'USB-C Fast Charger', 'usb-c-fast-charger', 'electronics', 'Fast wall charger for modern phones.', 'active', 'approved', 'ETB', 85000, null, null, 'in_stock', 4.6, 18, 63, false, true),
    (store_three, electronics, 'AdisTech', 'Smart Watch', 'smart-watch', 'electronics', 'Fitness and notification smartwatch.', 'active', 'approved', 'ETB', 320000, 360000, 11, 'low_stock', 4.5, 15, 42, true, true),
    (store_two, fashion, 'Habesha Essentials', 'Classic Cotton T-Shirt', 'classic-cotton-t-shirt', 'fashion', 'Soft everyday cotton T-shirt.', 'active', 'approved', 'ETB', 78000, null, null, 'in_stock', 4.8, 24, 95, false, true),
    (store_two, fashion, 'Habesha Essentials', 'Light Jacket', 'light-jacket', 'fashion', 'Lightweight jacket for daily wear.', 'active', 'approved', 'ETB', 180000, 210000, 14, 'in_stock', 4.4, 12, 38, false, true),
    (store_two, home_living, 'Habesha Home', 'Ceramic Coffee Cups', 'ceramic-coffee-cups', 'home', 'Set of four ceramic cups.', 'active', 'approved', 'ETB', 125000, null, null, 'in_stock', 4.9, 31, 102, true, true),
    (store_two, home_living, 'Habesha Home', 'Table Organizer', 'table-organizer', 'home', 'Modern desk and table organizer.', 'active', 'approved', 'ETB', 98000, null, null, 'in_stock', 4.6, 10, 27, false, true),
    (store_one, beauty, 'Fresh Care', 'Daily Skin Cream', 'daily-skin-cream', 'beauty', 'Gentle daily-use skin cream.', 'active', 'approved', 'ETB', 115000, 135000, 15, 'in_stock', 4.7, 20, 66, false, true),
    (store_one, groceries, 'Local Choice', 'Premium Coffee Beans', 'premium-coffee-beans', 'groceries', 'Fresh roasted coffee beans.', 'active', 'approved', 'ETB', 160000, null, null, 'in_stock', 4.9, 40, 142, true, true),
    (store_one, groceries, 'Local Choice', 'Honey Jar', 'honey-jar', 'groceries', 'Natural honey jar.', 'active', 'approved', 'ETB', 135000, null, null, 'in_stock', 4.8, 22, 79, false, true),
    (store_one, books, 'Reader', 'Business Notebook', 'business-notebook', 'books', 'Hardcover notebook for work.', 'active', 'approved', 'ETB', 65000, null, null, 'in_stock', 4.5, 9, 35, false, true),
    (store_one, sports, 'Fit Addis', 'Training Water Bottle', 'training-water-bottle', 'sports', 'Durable bottle for training.', 'active', 'approved', 'ETB', 70000, null, null, 'in_stock', 4.6, 14, 51, false, true),
    (store_one, toys, 'Play Addis', 'Kids Puzzle Set', 'kids-puzzle-set', 'toys', 'Educational puzzle set for children.', 'active', 'approved', 'ETB', 90000, null, null, 'in_stock', 4.7, 16, 47, false, true),
    (store_three, electronics, 'AdisTech', 'Phone Stand', 'phone-stand', 'electronics', 'Adjustable desk phone stand.', 'active', 'approved', 'ETB', 45000, null, null, 'in_stock', 4.4, 11, 34, false, true),
    (store_three, electronics, 'AdisTech', 'Bluetooth Speaker', 'bluetooth-speaker', 'electronics', 'Portable speaker for everyday use.', 'active', 'approved', 'ETB', 210000, 245000, 14, 'in_stock', 4.6, 19, 58, true, true),
    (store_two, fashion, 'Habesha Essentials', 'Canvas Tote Bag', 'canvas-tote-bag', 'fashion', 'Durable daily canvas tote bag.', 'active', 'approved', 'ETB', 72000, null, null, 'in_stock', 4.6, 13, 44, false, true),
    (store_two, home_living, 'Habesha Home', 'Cotton Towel Set', 'cotton-towel-set', 'home', 'Soft cotton towel set.', 'active', 'approved', 'ETB', 145000, 165000, 12, 'in_stock', 4.7, 17, 49, false, true),
    (store_one, beauty, 'Fresh Care', 'Natural Soap Pack', 'natural-soap-pack', 'beauty', 'Gentle natural soap pack.', 'active', 'approved', 'ETB', 55000, null, null, 'in_stock', 4.5, 10, 39, false, true),
    (store_one, groceries, 'Local Choice', 'Spice Mix', 'spice-mix', 'groceries', 'Everyday Ethiopian spice mix.', 'active', 'approved', 'ETB', 80000, null, null, 'in_stock', 4.8, 27, 88, true, true),
    (store_one, books, 'Reader', 'Daily Planner', 'daily-planner', 'books', 'Clean daily planner for work and study.', 'active', 'approved', 'ETB', 74000, null, null, 'in_stock', 4.5, 8, 29, false, true)
  on conflict do nothing;

  insert into public.shop_inventory (product_id, quantity_available, low_stock_threshold)
  select id, 25, 3
  from public.shop_products
  where is_seed = true
  on conflict do nothing;

  insert into public.shop_banners (title, subtitle, display_order, is_seed)
  values
    ('Fresh deals from verified sellers', 'Browse approved products from AdisShop stores.', 1, true),
    ('Local favorites', 'Popular items ready for customers in Addis Ababa.', 2, true),
    ('Buyer protection ready', 'Shop foundations are prepared for secure marketplace checkout.', 3, true)
  on conflict do nothing;
end $$;
