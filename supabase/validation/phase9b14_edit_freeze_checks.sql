-- Run only on the disposable phase9b11 fixture after activation.
do $$
declare denied boolean; n integer:=0; original_title text;
begin
  if (select count(*) from pg_trigger where tgname='phase9b14_review_freeze' and not tgisinternal) <> 9 then
    raise exception 'freeze trigger coverage mismatch'; end if; n:=n+1;
  select title into original_title from public.shop_products where id='20000000-0000-4000-8000-000000000001';
  denied:=false;
  begin
    update public.shop_products set title='should not persist' where id='20000000-0000-4000-8000-000000000001';
  exception when sqlstate '55000' then denied:=true; end;
  if not denied or (select title from public.shop_products where id='20000000-0000-4000-8000-000000000001') is distinct from original_title then
    raise exception 'product edit was not denied atomically'; end if; n:=n+1;
  denied:=false;
  begin
    insert into storage.objects(bucket_id,name) values ('shop-product-images','synthetic/blocked.jpg');
  exception when sqlstate '55000' then denied:=true; end;
  if not denied or exists(select 1 from storage.objects where name='synthetic/blocked.jpg') then
    raise exception 'product Storage insert was not denied'; end if; n:=n+1;
  denied:=false;
  begin
    delete from storage.objects where bucket_id='shop-store-assets' and name='synthetic/logo.jpg';
  exception when sqlstate '55000' then denied:=true; end;
  if not denied or not exists(select 1 from storage.objects where bucket_id='shop-store-assets' and name='synthetic/logo.jpg') then
    raise exception 'store Storage delete was not denied'; end if; n:=n+1;
  insert into storage.objects(bucket_id,name) values ('shop-category-images','synthetic/allowed.jpg');
  if not exists(select 1 from storage.objects where name='synthetic/allowed.jpg') then
    raise exception 'unrelated bucket was blocked'; end if; n:=n+1;
  raise notice 'phase9b14 focused freeze checks passed: %',n;
end $$;
