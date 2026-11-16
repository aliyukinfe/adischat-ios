-- Isolated PostgreSQL checks for new preparation behavior only.
do $$
declare
  pid uuid:='20000000-0000-4000-8000-000000000001';
  sid uuid:='10000000-0000-4000-8000-000000000001';
  admin_id uuid:='40000000-0000-4000-8000-000000000002';
  seller_id uuid:='40000000-0000-4000-8000-000000000003';
  owner_id uuid:='40000000-0000-4000-8000-000000000001';
  suspended_id uuid:='40000000-0000-4000-8000-000000000004';
  denied boolean; rev bigint; n integer:=0;
begin
  if (public.adishop_admin_public_content_queue(admin_id,null,50,0)->>'total')::int<>4 then
    raise exception 'historical queue mismatch'; end if; n:=n+1;
  rev:=(public.adishop_admin_public_content_preview(admin_id,'product',pid)->>'revision')::bigint;
  if rev<>1 then raise exception 'preview revision mismatch'; end if; n:=n+1;
  denied:=false;
  begin perform public.adishop_admin_public_content_queue(seller_id,null,50,0);
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'seller accessed queue'; end if; n:=n+1;
  denied:=false;
  begin perform public.adishop_admin_public_content_preview(seller_id,'product',pid);
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'foreign seller accessed preview'; end if; n:=n+1;
  denied:=false;
  begin perform public.adishop_admin_public_content_queue(suspended_id,null,50,0);
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'suspended admin accessed queue'; end if; n:=n+1;
  denied:=false;
  begin perform public.adishop_review_public_content(owner_id,'store',sid,1,'approved');
  exception when others then denied:=sqlerrm='admin-forbidden'; end;
  if not denied then raise exception 'store owner approved own content'; end if; n:=n+1;
  update public.shop_products set title='Reviewed after seller edit' where id=pid;
  denied:=false;
  begin perform public.adishop_review_public_content(admin_id,'product',pid,rev,'approved');
  exception when others then denied:=sqlerrm='stale-review'; end;
  if not denied then raise exception 'stale preview approved'; end if; n:=n+1;
  if (public.adishop_admin_public_content_queue(admin_id,'product',50,0)->>'total')::int<>1 then
    raise exception 'edited product missing from queue'; end if; n:=n+1;
  raise notice 'phase9b11 preparation checks passed: %',n;
end $$;
set role anon;
select count(*) as pre_cutover_public_rows from public.shop_products;
reset role;
