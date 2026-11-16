-- Phase 1.4F.1: seller-owned product media metadata.
-- Reuses the existing shop_product_images table and shop-product-images bucket.

alter table public.shop_product_images
  add column if not exists seller_profile_id uuid references public.shop_profiles(id),
  add column if not exists store_id uuid references public.shop_stores(id),
  add column if not exists bucket text not null default 'shop-product-images',
  add column if not exists mime_type text,
  add column if not exists width integer,
  add column if not exists height integer,
  add column if not exists size_bytes integer,
  add column if not exists moderation_status text not null default 'draft',
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_at timestamptz;

update public.shop_product_images i
set
  store_id = coalesce(i.store_id, p.store_id),
  seller_profile_id = coalesce(i.seller_profile_id, s.owner_profile_id),
  bucket = coalesce(nullif(i.bucket, ''), 'shop-product-images'),
  moderation_status = coalesce(nullif(i.moderation_status, ''), p.moderation_status, 'draft'),
  updated_at = now()
from public.shop_products p
join public.shop_stores s on s.id = p.store_id
where i.product_id = p.id
  and (i.store_id is null or i.seller_profile_id is null or i.bucket is null);

create index if not exists shop_product_images_seller_idx
  on public.shop_product_images(seller_profile_id, store_id, product_id)
  where deleted_at is null;

create index if not exists shop_product_images_storage_path_idx
  on public.shop_product_images(bucket, storage_path)
  where deleted_at is null and storage_path is not null;

create unique index if not exists shop_product_images_one_primary_idx
  on public.shop_product_images(product_id)
  where is_primary = true and deleted_at is null;

create unique index if not exists shop_product_images_storage_path_unique_idx
  on public.shop_product_images(bucket, storage_path)
  where storage_path is not null;

drop trigger if exists shop_product_images_set_updated_at on public.shop_product_images;
create trigger shop_product_images_set_updated_at
before update on public.shop_product_images
for each row execute function public.set_shop_updated_at();
