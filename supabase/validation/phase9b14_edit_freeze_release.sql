-- Local proposal only. Release only after a separately authorized privacy-safe cutover.
begin;
set local lock_timeout = '5s';
lock table public.shop_products, public.shop_stores, public.shop_product_images,
  public.shop_product_variants, public.shop_product_attribute_values,
  public.shop_product_options, public.shop_product_option_values,
  public.shop_variant_media, storage.objects in share row exclusive mode;
do $$
declare relation_name text;
begin
  foreach relation_name in array array[
    'shop_products','shop_stores','shop_product_images','shop_product_variants',
    'shop_product_attribute_values','shop_product_options',
    'shop_product_option_values','shop_variant_media'
  ] loop
    execute format('drop trigger if exists phase9b14_review_freeze on public.%I', relation_name);
  end loop;
end $$;
drop trigger if exists phase9b14_review_freeze on storage.objects;
commit;
