-- Local-only overlay on the Phase 9B.5 synthetic fixture.
alter table public.shop_stores add column description text;
alter table public.shop_stores add column status text not null default 'active';
alter table public.shop_stores add column approval_status text not null default 'approved';
alter table public.shop_stores add column deleted_at timestamptz;
alter table public.shop_product_variants add column status text not null default 'active';
alter table public.shop_product_variants add column options jsonb not null default '{}'::jsonb;
create table public.shop_product_images (
  id uuid primary key, product_id uuid not null references public.shop_products(id),
  alt_text text, deleted_at timestamptz
);
insert into public.shop_product_images(id,product_id,alt_text) values
  ('50000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','Synthetic laptop');
alter table public.shop_products enable row level security;
alter table public.shop_product_images enable row level security;
create policy synthetic_public_products on public.shop_products for select to anon,authenticated
  using (status='active' and moderation_status='approved' and deleted_at is null);
create policy synthetic_public_images on public.shop_product_images for select to anon,authenticated
  using (deleted_at is null and exists(select 1 from public.shop_products p where p.id=product_id));
grant usage on schema public to anon,authenticated;
grant select on public.shop_products,public.shop_product_images to anon,authenticated;
