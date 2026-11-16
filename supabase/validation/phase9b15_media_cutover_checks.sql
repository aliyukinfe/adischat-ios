-- Focused disposable Postgres check after revised Phase 9B.11 cutover.
do $$
declare payload jsonb; image jsonb; store jsonb; passed integer:=0;
begin
  if has_column_privilege('anon','public.shop_product_images','image_url','SELECT')
      or has_column_privilege('authenticated','public.shop_product_images','image_url','SELECT')
      or has_column_privilege('anon','public.shop_stores','logo_url','SELECT')
      or has_column_privilege('anon','public.shop_stores','cover_url','SELECT') then
    raise exception 'raw media URL grant remains'; end if; passed:=passed+1;
  payload:=public.adishop_product_catalog_payload('20000000-0000-4000-8000-000000000001');
  if payload is null then raise exception 'reviewed product hidden'; end if; passed:=passed+1;
  image:=payload->'shop_product_images'->0;
  if image->>'id'<>'30000000-0000-4000-8000-000000000001'
      or image->>'image_url' is not null then
    raise exception 'detail image URL leaked or ID lost'; end if; passed:=passed+1;
  store:=payload->'shop_stores';
  if store->>'logo_url' is not null or store->>'cover_url' is not null then
    raise exception 'detail store URL leaked'; end if; passed:=passed+1;
  if not has_column_privilege('anon','public.shop_product_images','id','SELECT')
      or not has_column_privilege('anon','public.shop_stores','id','SELECT') then
    raise exception 'reviewed-media IDs inaccessible'; end if; passed:=passed+1;
  raise notice 'phase9b15 focused media cutover checks passed: %',passed;
end $$;
