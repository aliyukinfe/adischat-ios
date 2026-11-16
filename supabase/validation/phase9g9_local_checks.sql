-- Focused local SQL assertions. Not hosted Auth/PostgREST validation.
create function pg_temp.qa_assert(ok boolean, label text) returns void
language plpgsql as $$ begin
  if not coalesce(ok,false) then raise exception 'FAIL %',label; end if;
  raise notice 'PASS %',label;
end $$;

select pg_temp.qa_assert((select count(*)=3 from public.shop_profiles), 'identity profile count preserved');
select pg_temp.qa_assert((select count(*)=3 from public.shop_user_links), 'identity links preserved');
select pg_temp.qa_assert((select count(*)=1 from public.shop_profiles where status='suspended'), 'suspended profile preserved');
select pg_temp.qa_assert((select count(*)=1 from public.shop_user_links where account_status='suspended'), 'suspended link preserved');
select pg_temp.qa_assert((select count(*)=2 from pg_trigger where tgname like 'adishop_guard_%' and not tgisinternal), 'identity guards preserved');
select pg_temp.qa_assert((select count(*)=18 from pg_class c join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relname in (
    'shop_categories','shop_stores','shop_store_members','shop_seller_applications','shop_products',
    'shop_product_variants','shop_product_images','shop_inventory','shop_favorites','shop_carts',
    'shop_cart_items','shop_addresses','shop_orders','shop_order_groups','shop_order_items',
    'shop_order_status_history','shop_qa_reviewed_catalog','shop_qa_reviewed_stores') and c.relrowsecurity),
  'all new tables have RLS');
select pg_temp.qa_assert(not has_table_privilege('anon','public.shop_products','SELECT') and
  not has_table_privilege('authenticated','public.shop_stores','SELECT') and
  not has_table_privilege('authenticated','public.shop_inventory','SELECT'), 'raw seller and inventory tables denied');
select pg_temp.qa_assert(not has_table_privilege('anon','public.shop_addresses','SELECT') and
  not has_table_privilege('authenticated','public.shop_carts','SELECT') and
  not has_table_privilege('authenticated','public.shop_favorites','SELECT'), 'cross-user private tables denied');
select pg_temp.qa_assert(has_table_privilege('anon','public.shop_qa_public_catalog','SELECT') and
  has_table_privilege('authenticated','public.shop_qa_public_stores','SELECT'), 'reviewed views readable');
select pg_temp.qa_assert(has_column_privilege('anon','public.shop_categories','name','SELECT') and
  not has_column_privilege('anon','public.shop_categories','image_url','SELECT'), 'category field allowlist');
select pg_temp.qa_assert(not has_table_privilege('service_role','public.shop_orders','INSERT') and
  not has_table_privilege('service_role','public.shop_order_groups','UPDATE') and
  not has_table_privilege('service_role','public.shop_order_items','DELETE'), 'order mutation grants absent');
select pg_temp.qa_assert(not has_table_privilege('service_role','public.shop_qa_reviewed_catalog','INSERT'),
  'service role cannot self-approve snapshots');
select pg_temp.qa_assert((select count(*)=2 and bool_and(not public) from storage.buckets), 'private buckets unchanged');
select pg_temp.qa_assert((select count(*)=0 from pg_policies where schemaname='storage'), 'no public Storage policies');
select pg_temp.qa_assert((select count(*)=2 from public.shop_product_variants) and
  (select count(*)=2 from public.shop_inventory), 'synthetic variants and inventory seeded');
select pg_temp.qa_assert((select count(*)=0 from public.shop_qa_public_catalog), 'unreviewed product withheld');

update public.shop_stores set owner_profile_id='90000000-0000-4000-8000-000000000001',
  status='active',approval_status='approved' where slug='synthetic-qa-store';
update public.shop_products set status='active',moderation_status='approved',stock_status='in_stock',
  has_variants=true,aggregate_available_quantity=6 where slug='synthetic-qa-laptop';
insert into public.shop_qa_reviewed_stores(store_id,store_updated_at,reviewed_by_profile_id,name,city)
select id,updated_at,'90000000-0000-4000-8000-000000000003','Reviewed QA Store','Test City'
from public.shop_stores where slug='synthetic-qa-store';
insert into public.shop_qa_reviewed_catalog(product_id,product_updated_at,store_updated_at,
  reviewed_by_profile_id,title,price_minor)
select p.id,p.updated_at,s.updated_at,'90000000-0000-4000-8000-000000000003',
  'Reviewed Synthetic Laptop',p.price_minor
from public.shop_products p join public.shop_stores s on s.id=p.store_id
where p.slug='synthetic-qa-laptop';
select pg_temp.qa_assert((select count(*)=1 from public.shop_qa_public_catalog), 'reviewed catalog visible');
select pg_temp.qa_assert((select count(*)=1 from public.shop_qa_public_stores), 'reviewed store visible');
select pg_temp.qa_assert((select count(*)=0 from information_schema.columns where table_name='shop_qa_public_catalog'
  and column_name in ('storage_path','description','email','phone','image_url')), 'public catalog omits private columns');

update public.shop_qa_reviewed_catalog set reviewed_by_profile_id='90000000-0000-4000-8000-000000000001';
select pg_temp.qa_assert((select count(*)=0 from public.shop_qa_public_catalog), 'owner review withheld');
update public.shop_qa_reviewed_catalog set reviewed_by_profile_id='90000000-0000-4000-8000-000000000002';
select pg_temp.qa_assert((select count(*)=0 from public.shop_qa_public_catalog), 'suspended reviewer withheld');
update public.shop_qa_reviewed_catalog set reviewed_by_profile_id='90000000-0000-4000-8000-000000000003';
update public.shop_product_variants set price_minor=5700000 where sku='QA-LAP-8-256';
select pg_temp.qa_assert((select count(*)=0 from public.shop_qa_public_catalog), 'variant edit invalidates product review');
update public.shop_qa_reviewed_catalog r set product_updated_at=p.updated_at
from public.shop_products p where p.id=r.product_id;
select pg_temp.qa_assert((select count(*)=1 from public.shop_qa_public_catalog), 'new revision can be reviewed');
update public.shop_stores set description='changed seller text' where slug='synthetic-qa-store';
select pg_temp.qa_assert((select count(*)=0 from public.shop_qa_public_catalog) and
  (select count(*)=0 from public.shop_qa_public_stores), 'store edit invalidates both projections');
