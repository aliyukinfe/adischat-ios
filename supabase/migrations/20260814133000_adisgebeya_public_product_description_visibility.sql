-- Add the buyer-safe seller summary to the existing public catalogue contract.
-- The underlying payload continues to enforce approved/active product visibility.
alter function public.adishop_product_catalog_payload(uuid)
  rename to adishop_product_catalog_payload_description_core;

revoke all on function public.adishop_product_catalog_payload_description_core(uuid)
  from public, anon, authenticated;
grant execute on function public.adishop_product_catalog_payload_description_core(uuid)
  to service_role;

create or replace function public.adishop_product_catalog_payload(p_product_id uuid)
returns jsonb
language sql
stable
security definer
set search_path=public
as $$
  select case
    when core.payload is null then null
    else core.payload || jsonb_build_object('shortDescription', p.short_description)
  end
  from (select public.adishop_product_catalog_payload_description_core(p_product_id) payload) core
  left join public.shop_products p on p.id=p_product_id
$$;

revoke all on function public.adishop_product_catalog_payload(uuid) from public;
grant execute on function public.adishop_product_catalog_payload(uuid)
  to anon, authenticated, service_role;
