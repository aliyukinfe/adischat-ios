-- REVIEW DRAFT ONLY. Edge Functions must derive actor IDs from Shop sessions.
-- Do not expose these SECURITY DEFINER RPCs to anon/authenticated.
begin;
create function public.adishop_benefit_submit(
  p_actor uuid, p_store_id uuid, p_case_id uuid, p_expected_version integer, p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare c public.shop_benefit_cases%rowtype;
begin
  if p_actor is null or p_store_id is null or p_case_id is null or
     p_request_key is null or length(p_request_key) < 8 then raise exception 'invalid-benefit-request'; end if;
  select * into c from public.shop_benefit_cases where id=p_case_id for update;
  if not found or c.store_id <> p_store_id then raise exception 'benefit-not-found'; end if;
  if not exists (select 1 from public.shop_stores s where s.id=p_store_id and s.owner_profile_id=p_actor
    and s.status='active' and s.approval_status='approved'
    and s.suspended_at is null and s.deleted_at is null) then raise exception 'benefit-not-found'; end if;
  if not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-not-found'; end if;
  if exists (select 1 from public.shop_benefit_events where case_id=c.id and request_key=p_request_key)
    then return jsonb_build_object('id',c.id,'status',c.status,'version',c.version); end if;
  if c.submitted_by_profile_id <> p_actor or c.status <> 'draft' or c.version <> p_expected_version
    then raise exception 'benefit-version-conflict'; end if;
  if not exists (select 1 from public.shop_benefit_evidence e where e.case_id=c.id and e.deleted_at is null)
    then raise exception 'benefit-evidence-required'; end if;
  if (c.kind in ('original_product','seller_warranty','manufacturer_warranty') and not exists
      (select 1 from public.shop_product_benefit_claims x where x.case_id=c.id)) or
     (c.kind='official_store' and not exists
      (select 1 from public.shop_store_brand_authorizations x where x.case_id=c.id)) or
     (c.kind='delivery_rule' and not exists
      (select 1 from public.shop_delivery_rules x where x.case_id=c.id)) or
     (c.kind='price_offer' and not exists
      (select 1 from public.shop_price_offers x where x.case_id=c.id))
    then raise exception 'benefit-details-required'; end if;
  update public.shop_benefit_cases set status='submitted',submitted_at=now(),updated_at=now(),version=version+1
    where id=c.id;
  insert into public.shop_benefit_events(case_id,actor_profile_id,action,previous_status,new_status,case_version,request_key)
    values(c.id,p_actor,'submit','draft','submitted',c.version+1,p_request_key);
  return jsonb_build_object('id',c.id,'status','submitted','version',c.version+1);
end $$;

create function public.adishop_benefit_decide(
  p_actor uuid, p_case_id uuid, p_expected_version integer, p_action text,
  p_reason text, p_effective_at timestamptz, p_expires_at timestamptz, p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare c public.shop_benefit_cases%rowtype; new_status text;
begin
  if p_actor is null or p_case_id is null or p_request_key is null or length(p_request_key) < 8
    or p_action not in ('approve','reject','revoke') then raise exception 'invalid-benefit-request'; end if;
  select * into c from public.shop_benefit_cases where id=p_case_id for update;
  if not found then raise exception 'benefit-not-found'; end if;
  if not public.adishop_profile_has_permission(p_actor,'benefit.review') then raise exception 'benefit-not-found'; end if;
  if not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-not-found'; end if;
  if p_actor=c.submitted_by_profile_id or exists (
    select 1 from public.shop_stores s where s.id=c.store_id and s.owner_profile_id=p_actor
  ) or exists (
    select 1 from public.shop_store_members m where m.store_id=c.store_id and m.profile_id=p_actor and m.status='active'
  ) then raise exception 'benefit-separation-of-duties'; end if;
  if exists (select 1 from public.shop_benefit_events where case_id=c.id and request_key=p_request_key)
    then return jsonb_build_object('id',c.id,'status',c.status,'version',c.version); end if;
  if c.version <> p_expected_version then raise exception 'benefit-version-conflict'; end if;
  if p_action='revoke' then
    if c.status <> 'approved' or nullif(trim(p_reason),'') is null then raise exception 'benefit-invalid-transition'; end if;
    new_status := 'revoked';
  else
    if c.status not in ('submitted','under_review') then raise exception 'benefit-invalid-transition'; end if;
    new_status := case p_action when 'approve' then 'approved' else 'rejected' end;
    if p_action='reject' and nullif(trim(p_reason),'') is null then raise exception 'benefit-reason-required'; end if;
    if p_action='approve' then
      if not exists (select 1 from public.shop_stores s where s.id=c.store_id
          and s.status='active' and s.approval_status='approved'
          and s.suspended_at is null and s.deleted_at is null)
        then raise exception 'benefit-store-unavailable'; end if;
      if c.kind in ('original_product','manufacturer_warranty','official_store') and
         not exists (select 1 from public.shop_benefit_brands b
           where b.id=c.brand_id and b.status='active')
        then raise exception 'benefit-brand-unavailable'; end if;
      if c.kind in ('original_product','manufacturer_warranty') and
         not exists (select 1 from public.shop_products p where p.id=c.product_id
           and p.store_id=c.store_id and p.brand_id=c.brand_id and p.deleted_at is null)
        then raise exception 'benefit-brand-mismatch'; end if;
      if not exists (select 1 from public.shop_benefit_evidence e where e.case_id=c.id and e.deleted_at is null)
        then raise exception 'benefit-evidence-required'; end if;
      if c.kind in ('original_product','seller_warranty') and not exists
        (select 1 from public.shop_benefit_evidence e where e.case_id=c.id
          and e.deleted_at is null and e.evidence_type in
          (case c.kind when 'original_product' then 'invoice' else 'seller_terms' end,
           case c.kind when 'original_product' then 'authorization_letter' else 'seller_terms' end))
        then raise exception 'benefit-evidence-required'; end if;
      if p_effective_at is null or p_expires_at is null or p_expires_at <= p_effective_at
        then raise exception 'benefit-validity-required'; end if;
      if c.kind in ('official_store','manufacturer_warranty') and
         p_expires_at > least(p_effective_at + interval '12 months', now() + interval '12 months')
        then raise exception 'benefit-validity-too-long'; end if;
      if c.kind in ('official_store','manufacturer_warranty') and (
        not exists (select 1 from public.shop_benefit_evidence e where e.case_id=c.id and e.deleted_at is null
          and e.evidence_type = case c.kind when 'official_store' then 'authorization_letter' else 'manufacturer_terms' end)
        or exists (select 1 from public.shop_benefit_evidence e where e.case_id=c.id and e.deleted_at is null
          and e.evidence_type = case c.kind when 'official_store' then 'authorization_letter' else 'manufacturer_terms' end
          and (e.document_expires_at is null or e.document_expires_at < p_expires_at))
      ) then raise exception 'benefit-document-expiry-mismatch'; end if;
      if c.kind in ('delivery_rule','price_offer') then raise exception 'benefit-quote-integration-pending'; end if;
    end if;
  end if;
  update public.shop_benefit_cases set status=new_status,version=version+1,updated_at=now(),
    reviewed_by_profile_id=p_actor,reviewed_at=now(),
    effective_at=case when p_action='approve' then p_effective_at else effective_at end,
    expires_at=case when p_action='approve' then p_expires_at else expires_at end,
    revoked_at=case when p_action='revoke' then now() else revoked_at end,
    public_reason=case when p_action='approve' then null else trim(p_reason) end
    where id=c.id;
  insert into public.shop_benefit_events(case_id,actor_profile_id,action,previous_status,new_status,case_version,reason,request_key)
    values(c.id,p_actor,p_action,c.status,new_status,c.version+1,p_reason,p_request_key);
  return jsonb_build_object('id',c.id,'status',new_status,'version',c.version+1);
end $$;
revoke all on function public.adishop_benefit_submit(uuid,uuid,uuid,integer,text) from public,anon,authenticated;
revoke all on function public.adishop_benefit_decide(uuid,uuid,integer,text,text,timestamptz,timestamptz,text) from public,anon,authenticated;
grant execute on function public.adishop_benefit_submit(uuid,uuid,uuid,integer,text) to service_role;
grant execute on function public.adishop_benefit_decide(uuid,uuid,integer,text,text,timestamptz,timestamptz,text) to service_role;
commit;
