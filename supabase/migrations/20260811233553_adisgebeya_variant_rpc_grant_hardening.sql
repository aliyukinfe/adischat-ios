-- Sensitive catalog mutations are reachable only through authenticated Edge Functions.
revoke all on function public.adishop_seller_save_variant_catalog(uuid,uuid,jsonb,text)
  from public, anon, authenticated;
grant execute on function public.adishop_seller_save_variant_catalog(uuid,uuid,jsonb,text)
  to service_role;

revoke all on function public.adishop_validate_variant_option_assignment()
  from public, anon, authenticated;
revoke all on function public.adishop_validate_sellable_selection()
  from public, anon, authenticated;
