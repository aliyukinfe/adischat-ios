-- REVIEW DRAFT ONLY. Depends on 01 and 02. Not production-ready without
-- isolated verification of the complete decision/checkout Edge contracts.
begin;

create function public.adishop_benefit_case_store_guard() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_op='INSERT' and (new.status <> 'draft' or new.version <> 1 or
      new.submitted_at is not null or new.reviewed_at is not null or
      new.reviewed_by_profile_id is not null or new.effective_at is not null or
      new.expires_at is not null or new.revoked_at is not null) then
    raise exception 'benefit-invalid-initial-state';
  end if;
  if new.product_id is not null and not exists (
    select 1 from public.shop_products p where p.id = new.product_id and p.store_id = new.store_id
  ) then raise exception 'benefit-product-store-mismatch'; end if;
  return new;
end $$;
create trigger shop_benefit_case_store_guard before insert or update of store_id,product_id
on public.shop_benefit_cases for each row execute function public.adishop_benefit_case_store_guard();

create function public.adishop_benefit_subject_guard() returns trigger
language plpgsql set search_path = public as $$
declare c public.shop_benefit_cases%rowtype;
begin
  select * into c from public.shop_benefit_cases where id = new.case_id for update;
  if not found then raise exception 'benefit-case-not-found'; end if;
  if c.status <> 'draft' then raise exception 'benefit-subject-frozen'; end if;
  if tg_table_name = 'shop_store_brand_authorizations' then
    if c.kind <> 'official_store' or c.brand_id is distinct from new.brand_id then
      raise exception 'benefit-brand-scope-mismatch'; end if;
    if new.authorization_scope='listed_product_ids' and exists (
      select 1 from unnest(new.covered_product_ids) covered(id)
      where not exists (select 1 from public.shop_products p
        where p.id=covered.id and p.store_id=c.store_id and p.brand_id=new.brand_id)
    ) then raise exception 'benefit-brand-products-mismatch'; end if;
  elsif tg_table_name = 'shop_product_benefit_claims' then
    if c.kind not in ('original_product','seller_warranty','manufacturer_warranty') then
      raise exception 'benefit-kind-mismatch'; end if;
    if (c.kind='seller_warranty' and new.issuer <> 'seller') or
       (c.kind='manufacturer_warranty' and new.issuer <> 'manufacturer') then
      raise exception 'benefit-issuer-mismatch'; end if;
  elsif tg_table_name = 'shop_delivery_rules' then
    if c.kind <> 'delivery_rule' or c.store_id <> new.store_id or
      (new.product_id is not null and not exists
        (select 1 from public.shop_products p where p.id=new.product_id and p.store_id=new.store_id)) then
      raise exception 'benefit-delivery-scope-mismatch'; end if;
  elsif tg_table_name = 'shop_price_offers' then
    if c.kind <> 'price_offer' or c.store_id <> new.store_id or
      c.product_id is distinct from new.product_id or
      not exists (select 1 from public.shop_products p where p.id=new.product_id and p.store_id=new.store_id) or
      (new.variant_id is not null and not exists
        (select 1 from public.shop_product_variants v where v.id=new.variant_id and v.product_id=new.product_id and v.store_id=new.store_id)) or
      (new.campaign_id is not null and not exists
        (select 1 from public.shop_campaigns m where m.id=new.campaign_id and m.store_id=new.store_id)) then
      raise exception 'benefit-price-scope-mismatch'; end if;
  end if;
  return new;
end $$;
create trigger shop_benefit_claim_scope before insert or update on public.shop_product_benefit_claims
for each row execute function public.adishop_benefit_subject_guard();
create trigger shop_benefit_brand_scope before insert or update on public.shop_store_brand_authorizations
for each row execute function public.adishop_benefit_subject_guard();
create trigger shop_benefit_delivery_scope before insert or update on public.shop_delivery_rules
for each row execute function public.adishop_benefit_subject_guard();
create trigger shop_benefit_price_scope before insert or update on public.shop_price_offers
for each row execute function public.adishop_benefit_subject_guard();

create function public.adishop_benefit_subject_freeze() returns trigger
language plpgsql set search_path = public as $$
declare case_status text;
begin
  select status into case_status from public.shop_benefit_cases where id=old.case_id for update;
  if case_status <> 'draft' then raise exception 'benefit-subject-frozen'; end if;
  if tg_op='DELETE' then raise exception 'benefit-subject-delete-denied'; end if;
  return new;
end $$;
create trigger shop_benefit_claim_freeze before update or delete on public.shop_product_benefit_claims
for each row execute function public.adishop_benefit_subject_freeze();
create trigger shop_benefit_brand_freeze before update or delete on public.shop_store_brand_authorizations
for each row execute function public.adishop_benefit_subject_freeze();
create trigger shop_benefit_delivery_freeze before update or delete on public.shop_delivery_rules
for each row execute function public.adishop_benefit_subject_freeze();
create trigger shop_benefit_price_freeze before update or delete on public.shop_price_offers
for each row execute function public.adishop_benefit_subject_freeze();
revoke delete on public.shop_product_benefit_claims, public.shop_store_brand_authorizations,
  public.shop_delivery_rules, public.shop_price_offers from service_role;

create function public.adishop_benefit_evidence_guard() returns trigger
language plpgsql set search_path = public as $$
declare case_status text;
begin
  if tg_op <> 'INSERT' then raise exception 'benefit-evidence-immutable'; end if;
  select status into case_status from public.shop_benefit_cases where id=new.case_id for update;
  if case_status is distinct from 'draft' then raise exception 'benefit-evidence-frozen'; end if;
  return new;
end $$;
create trigger shop_benefit_evidence_guard before insert or update or delete on public.shop_benefit_evidence
for each row execute function public.adishop_benefit_evidence_guard();
revoke update, delete on public.shop_benefit_evidence from service_role;

create function public.adishop_benefit_record_creation() returns trigger
language plpgsql security definer set search_path = public as $$
declare case_version integer;
begin
  if tg_table_name='shop_benefit_cases' then
    insert into public.shop_benefit_events(case_id,actor_profile_id,action,new_status,case_version,request_key)
      values(new.id,new.submitted_by_profile_id,'create','draft',new.version,new.request_key);
  else
    select version into case_version from public.shop_benefit_cases where id=new.case_id;
    insert into public.shop_benefit_events(case_id,actor_profile_id,action,previous_status,new_status,case_version,request_key)
      values(new.case_id,new.uploaded_by_profile_id,'evidence_added','draft','draft',case_version,new.id::text);
  end if;
  return new;
end $$;
create trigger shop_benefit_case_created after insert on public.shop_benefit_cases
for each row execute function public.adishop_benefit_record_creation();
create trigger shop_benefit_evidence_added after insert on public.shop_benefit_evidence
for each row execute function public.adishop_benefit_record_creation();

create function public.adishop_benefit_events_immutable() returns trigger
language plpgsql as $$ begin raise exception 'benefit-events-immutable'; end $$;
create trigger shop_benefit_events_immutable before update or delete on public.shop_benefit_events
for each row execute function public.adishop_benefit_events_immutable();
revoke insert, update, delete on public.shop_benefit_events from service_role;

insert into public.shop_role_permissions(role_id,permission)
select r.id,p.permission from public.shop_roles r
join (values
  ('SHOP_ADMIN','benefit.review'),('SHOP_ADMIN','benefit.evidence.read'),
  ('SHOP_SUPER_ADMIN','benefit.review'),('SHOP_SUPER_ADMIN','benefit.evidence.read'),
  ('SHOP_SUPER_ADMIN','benefit.brand.manage')
) as p(role_code,permission) on p.role_code=r.code
on conflict (role_id,permission) do nothing;

-- Public projection requires an active approved product/store and an approved,
-- effective, non-revoked case. Only service_role may invoke it; Edge Function
-- supplies a product ID, never a client-supplied verification flag.
create function public.adishop_effective_product_trust(p_product_id uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'type',c.kind,'label',case c.kind
      when 'original_product' then 'Original product verified'
      when 'official_store' then 'Official store for ' || b.canonical_name
      when 'seller_warranty' then 'Seller warranty'
      when 'manufacturer_warranty' then 'Manufacturer warranty' end,
    'summary',case when c.kind = 'official_store' then null else cl.terms_summary end,
    'effectiveUntil',c.expires_at
  ) order by case c.kind
      when 'original_product' then 1
      when 'official_store' then 2
      when 'manufacturer_warranty' then 3
      when 'seller_warranty' then 4
      else 5 end,c.reviewed_at desc), '[]'::jsonb)
  from public.shop_products p
  join public.shop_stores s on s.id=p.store_id
  join public.shop_benefit_cases c on c.store_id=s.id
  left join public.shop_product_benefit_claims cl on cl.case_id=c.id
  left join public.shop_store_brand_authorizations a on a.case_id=c.id
  left join public.shop_benefit_brands b on b.id=a.brand_id
  where p.id=p_product_id and p.status='active' and p.moderation_status='approved'
    and p.deleted_at is null and s.status='active' and s.approval_status='approved'
    and s.suspended_at is null and s.deleted_at is null
    and c.status='approved' and c.effective_at <= now()
    and (c.expires_at is null or c.expires_at > now()) and c.revoked_at is null
    and exists (select 1 from public.shop_benefit_evidence e
      where e.case_id=c.id and e.deleted_at is null)
    and (c.product_id=p.id or
      (c.kind='official_store' and c.product_id is null and p.brand_id=a.brand_id
        and b.status='active'
        and (a.authorization_scope='all_brand_products' or p.id=any(a.covered_product_ids))))
    and (c.kind not in ('original_product','manufacturer_warranty') or
      (p.brand_id=c.brand_id and exists (select 1 from public.shop_benefit_brands brand
        where brand.id=c.brand_id and brand.status='active')))
    and c.kind in ('original_product','official_store','seller_warranty','manufacturer_warranty');
$$;
revoke all on function public.adishop_effective_product_trust(uuid) from public, anon, authenticated;
grant execute on function public.adishop_effective_product_trust(uuid) to service_role;
commit;
