-- REVIEW DRAFT ONLY. Depends on 03. Home enrichment only; no checkout changes.
begin;

create function public.adishop_effective_catalog_trust(p_product_ids uuid[])
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare result jsonb;
begin
  if p_product_ids is null or cardinality(p_product_ids) not between 1 and 100 or
     array_position(p_product_ids,null) is not null
    then raise exception 'invalid-benefit-catalog-request'; end if;
  select coalesce(jsonb_object_agg(ids.id::text,
    public.adishop_effective_product_trust(ids.id)), '{}'::jsonb)
    into result from (select distinct id from unnest(p_product_ids) as requested(id)) ids;
  return result;
end $$;
revoke all on function public.adishop_effective_catalog_trust(uuid[]) from public, anon, authenticated;
grant execute on function public.adishop_effective_catalog_trust(uuid[]) to service_role;
commit;
