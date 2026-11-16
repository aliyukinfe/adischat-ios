-- Synthetic SQL-only checks. Fixture identities are not production accounts.
do $$
declare
  pid uuid := '20000000-0000-4000-8000-000000000001';
  sid uuid := '10000000-0000-4000-8000-000000000001';
  iid uuid := '30000000-0000-4000-8000-000000000001';
  vid uuid := '50000000-0000-4000-8000-000000000001';
  owner_id uuid := '40000000-0000-4000-8000-000000000001';
  admin_id uuid := '40000000-0000-4000-8000-000000000002';
  seller_id uuid := '40000000-0000-4000-8000-000000000003';
  disabled_id uuid := '40000000-0000-4000-8000-000000000004';
  rev bigint; result jsonb; passed integer:=0; denied boolean;
begin
  if shop_catalog_private.is_public_product(pid) then raise exception 'historical auto-approved'; end if; passed:=passed+1;
  if public.adishop_product_catalog_payload(pid) is not null then raise exception 'unreviewed detail exposed'; end if; passed:=passed+1;
  if has_table_privilege('service_role','shop_catalog_private.reviewed_content','INSERT') then
    raise exception 'service role can bypass review RPC'; end if; passed:=passed+1;

  select revision into rev from shop_catalog_private.content_revisions where entity_kind='store' and entity_id=sid;
  denied:=false;
  begin perform public.adishop_review_public_content(owner_id,'store',sid,rev,'approved');
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'self review allowed'; end if; passed:=passed+1;
  denied:=false;
  begin perform public.adishop_review_public_content(seller_id,'store',sid,rev,'approved');
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'seller review allowed'; end if; passed:=passed+1;
  denied:=false;
  begin perform public.adishop_review_public_content(disabled_id,'store',sid,rev,'approved');
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'suspended reviewer allowed'; end if; passed:=passed+1;
  perform public.adishop_review_public_content(admin_id,'store',sid,rev,'approved'); passed:=passed+1;

  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved'); passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='variant' and entity_id=vid;
  perform public.adishop_review_public_content(admin_id,'variant',vid,rev,'approved'); passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='image' and entity_id=iid;
  perform public.adishop_review_public_content(admin_id,'image',iid,rev,'rejected');
  if shop_catalog_private.is_public_product(pid) then raise exception 'rejected image exposed'; end if; passed:=passed+1;
  update public.shop_product_images set alt_text='Reviewed second photo' where id=iid;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='image' and entity_id=iid;
  perform public.adishop_review_public_content(admin_id,'image',iid,rev,'approved');
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'approved listing hidden'; end if; passed:=passed+1;
  result:=public.adishop_product_catalog_payload(pid);
  if result is null or result->>'title'<>'Reviewed laptop'
      or result ? 'seller_sku' or result::text like '%PRIVATE-ID%'
      or result->'shop_product_images'->0->>'alt_text'<>'Reviewed second photo' then
    raise exception 'detail projection leak or missing fields'; end if; passed:=passed+1;

  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  update public.shop_products set title='Seller changed title' where id=pid;
  if shop_catalog_private.is_public_product(pid) or public.adishop_product_catalog_payload(pid) is not null then
    raise exception 'edited product remained public'; end if; passed:=passed+1;
  denied:=false;
  begin perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  exception when others then denied:=sqlerrm='stale-review'; end;
  if not denied then raise exception 'stale review accepted'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'new revision not visible'; end if; passed:=passed+1;

  insert into public.shop_product_attribute_values(id,product_id,value_text)
    values('60000000-0000-4000-8000-000000000001',pid,'Unreviewed specification');
  if shop_catalog_private.is_public_product(pid) then raise exception 'unreviewed specification exposed'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed specification hidden'; end if; passed:=passed+1;

  update public.shop_stores set description='Changed store text' where id=sid;
  if shop_catalog_private.is_public_product(pid) then raise exception 'stale store exposed product'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='store' and entity_id=sid;
  perform public.adishop_review_public_content(admin_id,'store',sid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed store hidden'; end if; passed:=passed+1;

  update public.shop_product_variants set title='Changed variant text' where id=vid;
  if shop_catalog_private.is_public_product(pid) then raise exception 'stale variant exposed'; end if; passed:=passed+1;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='variant' and entity_id=vid;
  perform public.adishop_review_public_content(admin_id,'variant',vid,rev,'approved');
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='product' and entity_id=pid;
  perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed variant hidden'; end if; passed:=passed+1;

  denied:=false;
  select revision into rev from shop_catalog_private.content_revisions where entity_kind='variant' and entity_id=vid;
  begin perform public.adishop_review_public_content(admin_id,'variant',vid,rev,'approved');
  exception when others then denied:=true; end;
  if not denied then raise exception 'duplicate review accepted'; end if; passed:=passed+1;
  raise notice 'phase9b10 focused checks passed: %',passed;
end $$;

set role anon;
select count(*) as public_products from public.shop_products;
select title,short_description from public.shop_products;
reset role;
