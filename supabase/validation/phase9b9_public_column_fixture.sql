-- Disposable PostgreSQL-only role/grant fixture. Not HTTP/Auth parity.
do $$ begin
  if not exists(select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
  if not exists(select 1 from pg_roles where rolname='service_role') then create role service_role nologin; end if;
end $$;
grant anon, authenticated, service_role to postgres;
create schema if not exists public;
grant usage on schema public to anon, authenticated, service_role;

create table public.shop_stores (
  id uuid primary key, name text, slug text, description text, logo_url text,
  cover_url text, category text, city text, approval_status text, status text,
  average_store_rating numeric, total_sales integer, created_at timestamptz,
  deleted_at timestamptz, contact_email text
);
create table public.shop_products (
  id uuid primary key, store_id uuid references public.shop_stores(id), title text,
  slug text, brand text, created_at timestamptz, category_id uuid,
  category_slug text, short_description text, selling_unit_code text,
  minimum_order_quantity integer, price_minor bigint, price_max_minor bigint,
  has_variants boolean, aggregate_available_quantity integer,
  compare_at_price_minor bigint, discount_percent integer,
  rating_average numeric, rating_count integer, completed_sales_count integer,
  stock_status text, status text, moderation_status text, deleted_at timestamptz,
  seller_sku text, specifications jsonb, validation_errors jsonb
);
create table public.shop_product_images (
  id uuid primary key, product_id uuid references public.shop_products(id),
  image_url text, is_primary boolean, display_order integer, alt_text text,
  deleted_at timestamptz, storage_path text
);
create table public.shop_product_variants (
  id uuid primary key, product_id uuid references public.shop_products(id),
  title text, options jsonb, barcode text
);
create view public.shop_public_products as select id,title,short_description from public.shop_products;
grant select on public.shop_products,public.shop_product_images,
  public.shop_product_variants,public.shop_stores,
  public.shop_public_products to anon,authenticated;

alter table public.shop_products enable row level security;
alter table public.shop_product_images enable row level security;
alter table public.shop_product_variants enable row level security;
alter table public.shop_stores enable row level security;
create policy p on public.shop_products for select to anon,authenticated using (status='active' and moderation_status='approved');
create policy i on public.shop_product_images for select to anon,authenticated using (deleted_at is null);
create policy s on public.shop_stores for select to anon,authenticated using (status='active' and approval_status='approved');
create policy v on public.shop_product_variants for select to anon,authenticated using (false);

insert into public.shop_stores(id,name,description,status,approval_status,contact_email)
values ('10000000-0000-4000-8000-000000000001','Reviewed store','Reviewed summary','active','approved','private@example.invalid');
insert into public.shop_products(id,store_id,title,short_description,status,moderation_status,seller_sku,specifications)
values ('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001',
  'Reviewed laptop','Reviewed public description','active','approved','PRIVATE-ID', '{"serial":"PRIVATE-ID"}');
insert into public.shop_product_images(id,product_id,image_url,alt_text,storage_path)
values ('30000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001',
  'https://example.invalid/reviewed.png','Reviewed photo','PRIVATE-ID');

