-- Disposable phase9b11 PostgreSQL fixture only. All test writes are rolled back.
begin;
do $$
declare
  image_id uuid:='30000000-0000-4000-8000-000000000001';
  store_id uuid:='10000000-0000-4000-8000-000000000001';
  admin_id uuid:='40000000-0000-4000-8000-000000000002';
  revision bigint; result jsonb; passed integer:=0;
begin
  if has_function_privilege('anon','public.adishop_public_reviewed_media_locator(text,uuid)','EXECUTE')
    or has_function_privilege('authenticated','public.adishop_public_reviewed_media_locator(text,uuid)','EXECUTE')
    or not has_function_privilege('service_role','public.adishop_public_reviewed_media_locator(text,uuid)','EXECUTE')
  then raise exception 'locator grants unsafe'; end if; passed:=passed+1;

  update public.shop_product_images set bucket='shop-product-images',
    storage_path='seller-products/40000000-0000-4000-8000-000000000001/10000000-0000-4000-8000-000000000001/20000000-0000-4000-8000-000000000001/photo.png' where id=image_id;
  select r.revision into revision from shop_catalog_private.content_revisions r
    where r.entity_kind='image' and r.entity_id=image_id;
  if public.adishop_public_reviewed_media_locator('image',image_id) is not null then
    raise exception 'stale image was served'; end if; passed:=passed+1;
  perform public.adishop_review_public_content(admin_id,'image',image_id,revision,'approved');
  result:=public.adishop_public_reviewed_media_locator('image',image_id);
  if result->>'storagePath' not like '%/20000000-0000-4000-8000-000000000001/photo.png'
      or result->>'bucket'<>'shop-product-images'
      or result->>'productId'<>'20000000-0000-4000-8000-000000000001'
      or result->>'storeId'<>'10000000-0000-4000-8000-000000000001' then
    raise exception 'approved image missing'; end if; passed:=passed+1;
  result:=public.adishop_public_reviewed_media_locator('product-cover','20000000-0000-4000-8000-000000000001');
  if result->>'kind'<>'product-cover' or result->>'productId'<>'20000000-0000-4000-8000-000000000001' then
    raise exception 'reviewed product cover missing'; end if; passed:=passed+1;

  update public.shop_product_images set alt_text='edited after review' where id=image_id;
  if public.adishop_public_reviewed_media_locator('image',image_id) is not null then
    raise exception 'edited image was served'; end if; passed:=passed+1;
  select r.revision into revision from shop_catalog_private.content_revisions r
    where r.entity_kind='image' and r.entity_id=image_id;
  perform public.adishop_review_public_content(admin_id,'image',image_id,revision,'rejected');
  if public.adishop_public_reviewed_media_locator('image',image_id) is not null then
    raise exception 'rejected image was served'; end if; passed:=passed+1;

  update public.shop_stores set logo_url=
    'https://example.supabase.co/storage/v1/object/public/shop-store-assets/synthetic/logo.png'
    where id=store_id;
  if public.adishop_public_reviewed_media_locator('store-logo',store_id) is not null then
    raise exception 'unreviewed store logo was served'; end if; passed:=passed+1;
  select r.revision into revision from shop_catalog_private.content_revisions r
    where r.entity_kind='store' and r.entity_id=store_id;
  perform public.adishop_review_public_content(admin_id,'store',store_id,revision,'approved');
  result:=public.adishop_public_reviewed_media_locator('store-logo',store_id);
  if result->>'sourceUrl' not like '%/shop-store-assets/synthetic/logo.png' then
    raise exception 'approved store logo missing'; end if; passed:=passed+1;
  if public.adishop_public_reviewed_media_locator('order',image_id) is not null then
    raise exception 'unknown media kind was accepted'; end if; passed:=passed+1;
  insert into public.shop_stores(id,name,status,approval_status)
    values ('10000000-0000-4000-8000-000000000009','Foreign test store','active','approved');
  update public.shop_product_images set store_id='10000000-0000-4000-8000-000000000009'
    where id=image_id;
  select r.revision into revision from shop_catalog_private.content_revisions r
    where r.entity_kind='image' and r.entity_id=image_id;
  perform public.adishop_review_public_content(admin_id,'image',image_id,revision,'approved');
  if public.adishop_public_reviewed_media_locator('image',image_id) is not null then
    raise exception 'cross-store image association was served'; end if; passed:=passed+1;
  raise notice 'phase9b15 focused locator checks passed: %',passed;
end $$;
rollback;
