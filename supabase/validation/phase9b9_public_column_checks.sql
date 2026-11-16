-- Execute after fixture and both local SQL candidates. Synthetic SQL-only tests.
insert into shop_catalog_private.source_content(entity_kind,entity_id,source_version,private_payload)
values ('product','20000000-0000-4000-8000-000000000001','v1','{"serial":"PRIVATE-ID"}');
insert into shop_catalog_private.reviewed_content(entity_kind,entity_id,source_version,public_payload,reviewer_profile_id)
values ('product','20000000-0000-4000-8000-000000000001','v1','{"title":"Reviewed laptop"}',
  '40000000-0000-4000-8000-000000000001');

do $$
declare n integer := 0;
begin
  if has_table_privilege('anon','public.shop_products','SELECT') then raise exception 'raw product table grant'; end if; n:=n+1;
  if has_column_privilege('anon','public.shop_products','seller_sku','SELECT') then raise exception 'seller sku visible'; end if; n:=n+1;
  if has_column_privilege('authenticated','public.shop_products','specifications','SELECT') then raise exception 'raw specifications visible'; end if; n:=n+1;
  if has_column_privilege('anon','public.shop_products','validation_errors','SELECT') then raise exception 'validation errors visible'; end if; n:=n+1;
  if not has_column_privilege('anon','public.shop_products','title','SELECT') then raise exception 'public title missing'; end if; n:=n+1;
  if not has_column_privilege('authenticated','public.shop_products','short_description','SELECT') then raise exception 'public description missing'; end if; n:=n+1;
  if has_column_privilege('anon','public.shop_product_images','storage_path','SELECT') then raise exception 'storage path visible'; end if; n:=n+1;
  if has_column_privilege('anon','public.shop_stores','contact_email','SELECT') then raise exception 'contact email visible'; end if; n:=n+1;
  if has_table_privilege('anon','public.shop_product_variants','SELECT') then raise exception 'variant grant remains'; end if; n:=n+1;
  if has_table_privilege('anon','public.shop_public_products','SELECT') then raise exception 'raw view grant remains'; end if; n:=n+1;
  if has_table_privilege('anon','shop_catalog_private.source_content','SELECT') then raise exception 'private source visible'; end if; n:=n+1;
  if has_table_privilege('authenticated','shop_catalog_private.reviewed_content','SELECT') then raise exception 'review table visible'; end if; n:=n+1;
  raise notice 'phase9b9 checks passed: %', n;
end $$;

set role anon;
select p.id,p.title,p.short_description,i.image_url,i.alt_text,s.name,s.description
from public.shop_products p
join public.shop_stores s on s.id=p.store_id
left join public.shop_product_images i on i.product_id=p.id;
reset role;
