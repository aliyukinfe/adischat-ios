-- Focused local-only extension; use after the privacy candidate.
do $$
declare
  variant_id uuid := '40000000-0000-4000-8000-000000000001';
  store_id uuid := '00000000-0000-4000-8000-000000000001';
  pending_id uuid := '20000000-0000-4000-8000-000000000003';
  failed boolean;
  passed integer := 0;
begin
  failed := false;
  begin
    update public.shop_product_variants set title='Serial: ABC123456789' where id=variant_id;
  exception when others then failed := sqlerrm='public-variant-contains-private-identifier'; end;
  if not failed or (select title from public.shop_product_variants where id=variant_id)<>'256 GB' then
    raise exception 'active variant title leaked';
  end if;
  passed := passed+1;
  failed := false;
  begin
    update public.shop_product_variants set options='{"SERIAL":"ABC123456789"}'::jsonb where id=variant_id;
  exception when others then failed := sqlerrm='public-variant-contains-private-identifier'; end;
  if not failed then raise exception 'active variant options leaked'; end if;
  passed := passed+1;
  failed := false;
  begin
    update public.shop_stores set name='IMEI: 123456789012345' where id=store_id;
  exception when others then failed := sqlerrm='public-store-contains-private-identifier'; end;
  if not failed or (select name from public.shop_stores where id=store_id)<>'Synthetic A' then
    raise exception 'store name leaked';
  end if;
  passed := passed+1;
  failed := false;
  begin
    update public.shop_stores set description='MAC address: 01:23:45:67:89:AB' where id=store_id;
  exception when others then failed := sqlerrm='public-store-contains-private-identifier'; end;
  if not failed then raise exception 'store description leaked'; end if;
  passed := passed+1;

  insert into public.shop_products(id,store_id,title,price_minor,status,moderation_status)
  values (pending_id,store_id,'Safe pending laptop',10000,'draft','pending');
  insert into public.shop_product_variants(id,product_id,title,price_minor,available_quantity,options)
  values ('40000000-0000-4000-8000-000000000002',pending_id,
    'Serial: ABC123456789',10000,1,'{}'::jsonb);
  failed := false;
  begin
    update public.shop_products set status='active',moderation_status='approved' where id=pending_id;
  exception when others then failed := sqlerrm='public-product-contains-private-identifier'; end;
  if not failed or (select moderation_status from public.shop_products where id=pending_id)='approved' then
    raise exception 'pending variant leaked on approval';
  end if;
  passed := passed+1;
  update public.shop_product_variants set title='Safe variant' where product_id=pending_id;
  update public.shop_products set status='active',moderation_status='approved' where id=pending_id;
  if (select moderation_status from public.shop_products where id=pending_id)<>'approved' then
    raise exception 'safe variant approval blocked';
  end if;
  passed := passed+1;
  raise notice 'Phase 9B.6 variant/store checks passed: %',passed;
end;
$$;
