revoke all on function public.adishop_seller_owned_store(uuid) from public, anon, authenticated;
revoke all on function public.adishop_seller_product_summary(public.shop_products) from public, anon, authenticated;
revoke all on function public.adishop_seller_list_products(uuid,text,integer,integer) from public, anon, authenticated;
revoke all on function public.adishop_seller_get_product(uuid,uuid) from public, anon, authenticated;
revoke all on function public.adishop_seller_create_product(uuid,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) from public, anon, authenticated;
revoke all on function public.adishop_seller_submit_product(uuid,uuid,text) from public, anon, authenticated;

grant execute on function public.adishop_seller_owned_store(uuid) to service_role;
grant execute on function public.adishop_seller_product_summary(public.shop_products) to service_role;
grant execute on function public.adishop_seller_list_products(uuid,text,integer,integer) to service_role;
grant execute on function public.adishop_seller_get_product(uuid,uuid) to service_role;
grant execute on function public.adishop_seller_create_product(uuid,text) to service_role;
grant execute on function public.adishop_seller_update_product(uuid,uuid,jsonb,text) to service_role;
grant execute on function public.adishop_seller_submit_product(uuid,uuid,text) to service_role;
