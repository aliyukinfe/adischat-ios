-- REVIEW DRAFT ONLY. Depends on 01 and 03. Do not deploy without isolated tests.
begin;

alter table public.shop_benefit_brands
  add column version integer not null default 1 check (version > 0);
alter table public.shop_benefit_brand_aliases
  add column retired_at timestamptz;

create table public.shop_benefit_brand_events (
  id uuid primary key default gen_random_uuid(),
  brand_id uuid not null references public.shop_benefit_brands(id) on delete restrict,
  actor_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  action text not null check (action in ('create','rename','add_alias','retire_alias','retire')),
  normalized_name text,
  reason text,
  brand_version integer not null check (brand_version > 0),
  request_key text not null unique,
  created_at timestamptz not null default now()
);
alter table public.shop_benefit_brand_events enable row level security;
revoke all on public.shop_benefit_brand_events from public, anon, authenticated, service_role;
grant select on public.shop_benefit_brand_events to service_role;
create trigger shop_benefit_brand_events_immutable before update or delete
  on public.shop_benefit_brand_events for each row
  execute function public.adishop_benefit_events_immutable();

-- All brand/alias writes must go through the reviewed, version-checked RPC.
revoke insert, update, delete on public.shop_benefit_brands,
  public.shop_benefit_brand_aliases from service_role;

create function public.adishop_benefit_brand_manage(
  p_actor uuid, p_action text, p_brand_id uuid, p_name text,
  p_expected_version integer, p_reason text, p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare b public.shop_benefit_brands%rowtype; prior public.shop_benefit_brand_events%rowtype;
  alias_id uuid;
  normalized text;
begin
  if p_actor is null or p_action is null or
     p_action not in ('create','rename','add_alias','retire_alias','retire') or
     p_request_key is null or length(p_request_key) not between 8 and 160 or
     not public.adishop_profile_has_permission(p_actor,'benefit.brand.manage') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-brand-not-authorized'; end if;
  normalized := lower(regexp_replace(trim(coalesce(p_name,'')), '[[:space:]]+', ' ', 'g'));
  if (p_action <> 'retire' and length(normalized) not between 2 and 120) or
     (p_action in ('retire','retire_alias') and length(trim(coalesce(p_reason,''))) < 4) or
     (p_action='create' and (p_brand_id is not null or p_expected_version is not null)) or
     (p_action<>'create' and (p_brand_id is null or p_expected_version is null or p_expected_version < 1))
    then raise exception 'invalid-benefit-brand-request'; end if;

  perform pg_advisory_xact_lock(hashtextextended('benefit-brand-request:' || p_request_key, 0));
  select * into prior from public.shop_benefit_brand_events where request_key=p_request_key;
  if found then
    if prior.action <> p_action or prior.brand_id is distinct from p_brand_id and p_action <> 'create' or
       (p_action <> 'retire' and prior.normalized_name is distinct from normalized)
      then raise exception 'benefit-brand-request-conflict'; end if;
    select * into b from public.shop_benefit_brands where id=prior.brand_id;
    return jsonb_build_object('id',b.id,'canonicalName',b.canonical_name,'status',b.status,'version',b.version);
  end if;

  if p_action <> 'retire' then
    perform pg_advisory_xact_lock(hashtextextended('benefit-brand-name:' || normalized, 0));
    if p_action <> 'retire_alias' and (
       exists (select 1 from public.shop_benefit_brands
         where normalized_name=normalized and (p_action<>'rename' or id<>p_brand_id)) or
       exists (select 1 from public.shop_benefit_brand_aliases where normalized_alias=normalized))
      then raise exception 'benefit-brand-name-conflict'; end if;
  end if;

  if p_action='create' then
    insert into public.shop_benefit_brands(canonical_name,normalized_name)
      values(trim(p_name),normalized) returning * into b;
  else
    select * into b from public.shop_benefit_brands where id=p_brand_id for update;
    if not found or b.version <> p_expected_version or b.status <> 'active'
      then raise exception 'benefit-brand-version-conflict'; end if;
    if p_action='rename' then
      update public.shop_benefit_brands set canonical_name=trim(p_name),normalized_name=normalized,
        version=version+1 where id=b.id returning * into b;
    elsif p_action='add_alias' then
      insert into public.shop_benefit_brand_aliases(brand_id,normalized_alias)
        values(b.id,normalized);
      update public.shop_benefit_brands set version=version+1 where id=b.id returning * into b;
    elsif p_action='retire_alias' then
      select id into alias_id from public.shop_benefit_brand_aliases
        where brand_id=b.id and normalized_alias=normalized and retired_at is null for update;
      if not found then raise exception 'benefit-brand-alias-not-found'; end if;
      update public.shop_benefit_brand_aliases set retired_at=now() where id=alias_id;
      update public.shop_benefit_brands set version=version+1 where id=b.id returning * into b;
    else
      update public.shop_benefit_brands set status='retired',version=version+1
        where id=b.id returning * into b;
    end if;
  end if;
  insert into public.shop_benefit_brand_events(
    brand_id,actor_profile_id,action,normalized_name,reason,brand_version,request_key
  ) values(b.id,p_actor,p_action,nullif(normalized,''),nullif(trim(coalesce(p_reason,'')),''),b.version,p_request_key);
  return jsonb_build_object('id',b.id,'canonicalName',b.canonical_name,'status',b.status,'version',b.version);
end $$;
revoke all on function public.adishop_benefit_brand_manage(
  uuid,text,uuid,text,integer,text,text
) from public, anon, authenticated;
grant execute on function public.adishop_benefit_brand_manage(
  uuid,text,uuid,text,integer,text,text
) to service_role;

-- A duplicate review records a decision only. It never changes canonical IDs,
-- aliases, products, claims, or store-brand authorizations.
create table public.shop_benefit_brand_merge_reviews (
  id uuid primary key default gen_random_uuid(),
  source_brand_id uuid not null references public.shop_benefit_brands(id) on delete restrict,
  target_brand_id uuid not null references public.shop_benefit_brands(id) on delete restrict,
  requested_by_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  reviewed_by_profile_id uuid references public.shop_profiles(id) on delete restrict,
  status text not null default 'pending' check (status in ('pending','confirmed_duplicate','rejected')),
  reason text not null check (length(trim(reason)) between 4 and 500),
  decision_reason text,
  version integer not null default 1 check (version > 0),
  request_key text not null unique,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  check (source_brand_id <> target_brand_id)
);
create unique index shop_benefit_brand_merge_pending_pair_idx
  on public.shop_benefit_brand_merge_reviews(
    least(source_brand_id,target_brand_id),greatest(source_brand_id,target_brand_id))
  where status='pending';
create table public.shop_benefit_brand_merge_events (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.shop_benefit_brand_merge_reviews(id) on delete restrict,
  actor_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  action text not null check (action in ('request','confirm_duplicate','reject')),
  review_version integer not null check (review_version > 0),
  reason text,
  request_key text not null unique,
  created_at timestamptz not null default now()
);
alter table public.shop_benefit_brand_merge_reviews enable row level security;
alter table public.shop_benefit_brand_merge_events enable row level security;
revoke all on public.shop_benefit_brand_merge_reviews,
  public.shop_benefit_brand_merge_events from public, anon, authenticated, service_role;
grant select on public.shop_benefit_brand_merge_reviews,
  public.shop_benefit_brand_merge_events to service_role;
create trigger shop_benefit_brand_merge_events_immutable before update or delete
  on public.shop_benefit_brand_merge_events for each row
  execute function public.adishop_benefit_events_immutable();

create function public.adishop_benefit_brand_merge_request(
  p_actor uuid, p_source uuid, p_target uuid, p_reason text, p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare r public.shop_benefit_brand_merge_reviews%rowtype;
begin
  if p_actor is null or p_source is null or p_target is null or p_source=p_target or
     length(trim(coalesce(p_reason,''))) not between 4 and 500 or
     p_request_key is null or length(p_request_key) not between 8 and 160 or
     not public.adishop_profile_has_permission(p_actor,'benefit.brand.manage') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'invalid-benefit-merge-request'; end if;
  perform pg_advisory_xact_lock(hashtextextended('benefit-merge:' ||
    least(p_source,p_target)::text || ':' || greatest(p_source,p_target)::text,0));
  select * into r from public.shop_benefit_brand_merge_reviews where request_key=p_request_key;
  if found then
    if r.source_brand_id<>p_source or r.target_brand_id<>p_target or
       r.requested_by_profile_id<>p_actor or r.reason<>trim(p_reason)
      then raise exception 'benefit-merge-request-conflict'; end if;
    return jsonb_build_object('id',r.id,'status',r.status,'version',r.version);
  end if;
  if not exists (select 1 from public.shop_benefit_brands b where b.id=p_source and b.status='active') or
     not exists (select 1 from public.shop_benefit_brands b where b.id=p_target and b.status='active')
    then raise exception 'benefit-brand-not-found'; end if;
  insert into public.shop_benefit_brand_merge_reviews(
    source_brand_id,target_brand_id,requested_by_profile_id,reason,request_key)
    values(p_source,p_target,p_actor,trim(p_reason),p_request_key) returning * into r;
  insert into public.shop_benefit_brand_merge_events(
    review_id,actor_profile_id,action,review_version,reason,request_key)
    values(r.id,p_actor,'request',r.version,r.reason,p_request_key);
  return jsonb_build_object('id',r.id,'status',r.status,'version',r.version);
end $$;

create function public.adishop_benefit_brand_merge_decide(
  p_actor uuid, p_review_id uuid, p_expected_version integer,
  p_decision text, p_reason text, p_request_key text
) returns jsonb language plpgsql security definer set search_path = public as $$
declare r public.shop_benefit_brand_merge_reviews%rowtype; decided text;
  active_brand_count integer;
begin
  if p_actor is null or p_review_id is null or p_expected_version is null or
     p_expected_version < 1 or p_decision is null or
     p_decision not in ('confirm_duplicate','reject') or
     length(trim(coalesce(p_reason,''))) not between 4 and 500 or
     p_request_key is null or length(p_request_key) not between 8 and 160 or
     not public.adishop_profile_has_permission(p_actor,'benefit.brand.manage') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'invalid-benefit-merge-decision'; end if;
  select * into r from public.shop_benefit_brand_merge_reviews where id=p_review_id for update;
  if not found then raise exception 'benefit-merge-not-found'; end if;
  if exists (select 1 from public.shop_benefit_brand_merge_events e
      where e.review_id=r.id and e.request_key=p_request_key) then
    return jsonb_build_object('id',r.id,'status',r.status,'version',r.version); end if;
  if r.status<>'pending' or r.version<>p_expected_version or
     r.requested_by_profile_id=p_actor then raise exception 'benefit-merge-version-conflict'; end if;
  perform 1 from public.shop_benefit_brands
    where id in (r.source_brand_id,r.target_brand_id) order by id for update;
  select count(*) into active_brand_count from public.shop_benefit_brands
    where id in (r.source_brand_id,r.target_brand_id) and status='active';
  if active_brand_count<>2 then raise exception 'benefit-brand-not-found'; end if;
  decided := p_decision;
  update public.shop_benefit_brand_merge_reviews set status=decided,
    reviewed_by_profile_id=p_actor,reviewed_at=now(),decision_reason=trim(p_reason),version=version+1
    where id=r.id;
  insert into public.shop_benefit_brand_merge_events(
    review_id,actor_profile_id,action,review_version,reason,request_key)
    values(r.id,p_actor,decided,r.version+1,trim(p_reason),p_request_key);
  return jsonb_build_object('id',r.id,'status',decided,'version',r.version+1);
end $$;
revoke all on function public.adishop_benefit_brand_merge_request(uuid,uuid,uuid,text,text),
  public.adishop_benefit_brand_merge_decide(uuid,uuid,integer,text,text,text)
  from public, anon, authenticated;
grant execute on function public.adishop_benefit_brand_merge_request(uuid,uuid,uuid,text,text),
  public.adishop_benefit_brand_merge_decide(uuid,uuid,integer,text,text,text)
  to service_role;
commit;
