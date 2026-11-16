-- Keep renamed implementation helpers behind the protected service boundary.
alter function public.adisgebeya_category_id(text) set search_path=public;
alter function public.adishop_seller_product_summary(public.shop_products) set search_path=public;

revoke all on function public.adisgebeya_category_id(text) from public, anon, authenticated;
grant execute on function public.adisgebeya_category_id(text) to service_role;

revoke all on function public.adishop_product_catalog_payload_category_core(uuid) from public, anon, authenticated;
grant execute on function public.adishop_product_catalog_payload_category_core(uuid) to service_role;

revoke all on function public.adishop_seller_update_product_category_core(uuid,uuid,jsonb,text) from public, anon, authenticated;
grant execute on function public.adishop_seller_update_product_category_core(uuid,uuid,jsonb,text) to service_role;
