-- REVIEW DRAFT ONLY. Apply only in isolated staging after 01-04 and private
-- Storage policy review. Never run against production without new approval.
begin;

create function public.adishop_benefit_create_claim(
  p_actor uuid, p_store_id uuid, p_product_id uuid, p_brand_id uuid,
  p_kind text, p_terms_summary text, p_coverage_details text,
  p_duration_months integer, p_authorization_scope text,
  p_covered_product_ids uuid[], p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare c public.shop_benefit_cases%rowtype; product_brand uuid; product_store uuid;
begin
  if p_actor is null or p_store_id is null or p_request_key is null or
     length(p_request_key) < 8 or length(p_request_key) > 160 or
     p_kind is null or p_kind not in ('original_product','official_store','seller_warranty','manufacturer_warranty')
    then raise exception 'invalid-benefit-request'; end if;
  if not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active') or
     not exists (select 1 from public.shop_stores s where s.id=p_store_id
       and s.owner_profile_id=p_actor and s.status='active' and s.approval_status='approved'
       and s.suspended_at is null and s.deleted_at is null)
    then raise exception 'benefit-not-found'; end if;

  -- Serialize retries for this store/key before checking the unique constraint.
  perform pg_advisory_xact_lock(hashtextextended(p_store_id::text || ':' || p_request_key, 0));

  select * into c from public.shop_benefit_cases
    where store_id=p_store_id and request_key=p_request_key;
  if found then
    if c.submitted_by_profile_id <> p_actor or c.kind <> p_kind or
       c.product_id is distinct from p_product_id or c.brand_id is distinct from p_brand_id
      then raise exception 'benefit-request-conflict'; end if;
    return jsonb_build_object('id',c.id,'status',c.status,'version',c.version);
  end if;

  if p_kind='official_store' then
    if p_product_id is not null or p_brand_id is null or
       p_authorization_scope is null or
       p_authorization_scope not in ('all_brand_products','listed_product_ids') or
       (p_authorization_scope='all_brand_products' and coalesce(cardinality(p_covered_product_ids),0) <> 0) or
       (p_authorization_scope='listed_product_ids' and
        (coalesce(cardinality(p_covered_product_ids),0) < 1 or
         cardinality(p_covered_product_ids) > 1000))
      then raise exception 'invalid-benefit-request'; end if;
  else
    if p_product_id is null or p_authorization_scope is not null or
       (p_kind in ('original_product','manufacturer_warranty') and p_brand_id is null) or
       coalesce(cardinality(p_covered_product_ids),0) <> 0 or
       length(trim(coalesce(p_terms_summary,''))) not between 4 and 500 or
       (p_duration_months is not null and p_duration_months not between 1 and 120)
      then raise exception 'invalid-benefit-request'; end if;
  end if;
  if p_brand_id is not null and not exists
      (select 1 from public.shop_benefit_brands b where b.id=p_brand_id and b.status='active')
    then raise exception 'benefit-brand-not-found'; end if;
  if p_product_id is not null then
    select store_id,brand_id into product_store,product_brand from public.shop_products
      where id=p_product_id and deleted_at is null;
    if product_store is distinct from p_store_id or
       (p_brand_id is not null and product_brand is distinct from p_brand_id)
      then raise exception 'benefit-not-found'; end if;
  end if;
  if p_kind='official_store' and p_authorization_scope='listed_product_ids' and
     exists (select 1 from unnest(p_covered_product_ids) x(id)
       left join public.shop_products p on p.id=x.id
       where p.id is null or p.store_id<>p_store_id or p.brand_id<>p_brand_id or p.deleted_at is not null)
    then raise exception 'benefit-not-found'; end if;

  insert into public.shop_benefit_cases(store_id,product_id,brand_id,kind,submitted_by_profile_id,request_key)
    values(p_store_id,p_product_id,p_brand_id,p_kind,p_actor,p_request_key) returning * into c;
  if p_kind='official_store' then
    insert into public.shop_store_brand_authorizations(case_id,brand_id,authorization_scope,covered_product_ids)
      values(c.id,p_brand_id,p_authorization_scope,coalesce(p_covered_product_ids,'{}'));
  else
    insert into public.shop_product_benefit_claims(case_id,issuer,terms_summary,coverage_details,duration_months)
      values(c.id,case when p_kind='manufacturer_warranty' then 'manufacturer' else 'seller' end,
        trim(p_terms_summary),nullif(trim(coalesce(p_coverage_details,'')),''),p_duration_months);
  end if;
  return jsonb_build_object('id',c.id,'status',c.status,'version',c.version);
end $$;

revoke all on function public.adishop_benefit_create_claim(
  uuid,uuid,uuid,uuid,text,text,text,integer,text,uuid[],text
) from public,anon,authenticated;
grant execute on function public.adishop_benefit_create_claim(
  uuid,uuid,uuid,uuid,text,text,text,integer,text,uuid[],text
) to service_role;
commit;
