-- Run only after synthetic backfill and a successful isolated cutover.
do $$
declare pid uuid:='20000000-0000-4000-8000-000000000001'; n integer:=0;
  detail jsonb;
begin
  if not shop_catalog_private.is_public_product(pid) then raise exception 'reviewed listing hidden'; end if; n:=n+1;
  detail:=public.adishop_product_catalog_payload(pid);
  if detail->>'title'<>'Reviewed after seller edit' or detail ? 'seller_sku'
      or detail::text like '%PRIVATE-ID%' then raise exception 'detail unsafe'; end if; n:=n+1;
  if has_table_privilege('anon','public.shop_products','SELECT')
      or has_column_privilege('anon','public.shop_products','seller_sku','SELECT')
      or has_table_privilege('anon','public.shop_public_products','SELECT') then
    raise exception 'raw column or view still exposed'; end if; n:=n+1;
  if not has_column_privilege('anon','public.shop_products','title','SELECT') then
    raise exception 'known buyer field inaccessible'; end if; n:=n+1;
  raise notice 'phase9b11 cutover checks passed: %',n;
end $$;
set role anon;
select count(*) as post_cutover_public_rows from public.shop_products;
select public.adishop_product_catalog_payload('20000000-0000-4000-8000-000000000001')->>'title'
  as reviewed_detail_title;
reset role;
