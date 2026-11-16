-- Execute once after phase9b10_reviewed_catalog_checks.sql.
do $$
declare pid uuid:='20000000-0000-4000-8000-000000000001';
  admin_id uuid:='40000000-0000-4000-8000-000000000002';
  oid uuid:='70000000-0000-4000-8000-000000000001';
  rev bigint; passed integer:=0;
begin
  insert into public.shop_product_options(id,product_id,code,name,status)
    values(oid,pid,'COLOR','Color','active');
  if shop_catalog_private.is_public_product(pid) then raise exception 'new option remained public'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions
    where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed option hidden'; end if; passed:=passed+1;
  insert into public.shop_product_option_values(id,product_option_id,code,value,display_value,status)
    values('71000000-0000-4000-8000-000000000001',oid,'BLUE','blue','Blue','active');
  if shop_catalog_private.is_public_product(pid) then raise exception 'new option value remained public'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions
    where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed option value hidden'; end if; passed:=passed+1;
  raise notice 'phase9b10 option checks passed: %',passed;
end $$;
