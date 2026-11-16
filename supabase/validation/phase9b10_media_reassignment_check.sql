-- Focused synthetic check for both-product invalidation on media reassignment.
do $$
declare first_product uuid:='20000000-0000-4000-8000-000000000001';
  second_product uuid:='20000000-0000-4000-8000-000000000002';
  first_variant uuid:='50000000-0000-4000-8000-000000000001';
  second_variant uuid:='50000000-0000-4000-8000-000000000002';
  image_id uuid:='30000000-0000-4000-8000-000000000001';
  before_first bigint; before_second bigint;
begin
  insert into public.shop_products(id,store_id,title,status,moderation_status)
    values(second_product,'10000000-0000-4000-8000-000000000001',
      'Second draft','draft','pending');
  insert into public.shop_product_variants(id,product_id,title,options,status,store_id)
    values(second_variant,second_product,'Second variant','{}','active',
      '10000000-0000-4000-8000-000000000001');
  insert into public.shop_variant_media(variant_id,product_image_id)
    values(first_variant,image_id);
  select revision into before_first from shop_catalog_private.content_revisions
    where entity_kind='product' and entity_id=first_product;
  select revision into before_second from shop_catalog_private.content_revisions
    where entity_kind='product' and entity_id=second_product;
  update public.shop_variant_media set variant_id=second_variant where variant_id=first_variant;
  if (select revision from shop_catalog_private.content_revisions
      where entity_kind='product' and entity_id=first_product)<=before_first
    or (select revision from shop_catalog_private.content_revisions
      where entity_kind='product' and entity_id=second_product)<=before_second then
    raise exception 'media reassignment failed to invalidate both products';
  end if;
  raise notice 'phase9b10 media reassignment checks passed: 2';
end $$;
