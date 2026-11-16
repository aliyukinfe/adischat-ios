-- Local proposal only. Activate only in an approved, bounded historical-review window.
begin;
set local lock_timeout = '5s';
lock table public.shop_products, public.shop_stores, public.shop_product_images,
  public.shop_product_variants, public.shop_product_attribute_values,
  public.shop_product_options, public.shop_product_option_values,
  public.shop_variant_media, storage.objects in share row exclusive mode;

create or replace function shop_catalog_private.reject_frozen_catalog_edit()
returns trigger language plpgsql security invoker
set search_path = pg_catalog, shop_catalog_private as $$
begin
  if tg_table_schema = 'storage' then
    if tg_op = 'DELETE' then
      if old.bucket_id not in ('shop-product-images','shop-store-assets') then return old; end if;
    elsif tg_op = 'INSERT' then
      if new.bucket_id not in ('shop-product-images','shop-store-assets') then return new; end if;
    elsif old.bucket_id not in ('shop-product-images','shop-store-assets')
        and new.bucket_id not in ('shop-product-images','shop-store-assets') then
      return new;
    end if;
  end if;
  raise exception 'catalog-review-edit-frozen' using errcode = '55000';
end;
$$;
revoke all on function shop_catalog_private.reject_frozen_catalog_edit() from public;

do $$
declare relation_name text;
begin
  foreach relation_name in array array[
    'shop_products','shop_stores','shop_product_images','shop_product_variants',
    'shop_product_attribute_values','shop_product_options',
    'shop_product_option_values','shop_variant_media'
  ] loop
    execute format('drop trigger if exists phase9b14_review_freeze on public.%I', relation_name);
    execute format('create trigger phase9b14_review_freeze before insert or update or delete on public.%I for each row execute function shop_catalog_private.reject_frozen_catalog_edit()', relation_name);
  end loop;
end $$;
drop trigger if exists phase9b14_review_freeze on storage.objects;
create trigger phase9b14_review_freeze before insert or update or delete on storage.objects
for each row execute function shop_catalog_private.reject_frozen_catalog_edit();
commit;
