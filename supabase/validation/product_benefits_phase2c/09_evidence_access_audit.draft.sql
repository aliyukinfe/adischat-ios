-- REVIEW DRAFT ONLY. Depends on 01 and 03. No private path or URL is logged.
begin;

create table public.shop_benefit_evidence_access_events (
  id uuid primary key default gen_random_uuid(),
  evidence_id uuid not null references public.shop_benefit_evidence(id) on delete restrict,
  case_id uuid not null references public.shop_benefit_cases(id) on delete restrict,
  actor_profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  action text not null default 'preview_authorized' check (action='preview_authorized'),
  request_key uuid not null unique,
  created_at timestamptz not null default now()
);
create index shop_benefit_evidence_access_case_idx
  on public.shop_benefit_evidence_access_events(case_id,created_at);
alter table public.shop_benefit_evidence_access_events enable row level security;
revoke all on public.shop_benefit_evidence_access_events from public, anon, authenticated, service_role;
grant select on public.shop_benefit_evidence_access_events to service_role;
create trigger shop_benefit_evidence_access_immutable before update or delete
  on public.shop_benefit_evidence_access_events for each row
  execute function public.adishop_benefit_events_immutable();

create function public.adishop_benefit_record_evidence_preview(
  p_actor uuid, p_evidence_id uuid, p_request_key uuid
) returns boolean language plpgsql security definer set search_path = public as $$
declare target record;
begin
  if p_actor is null or p_evidence_id is null or p_request_key is null or
     not public.adishop_profile_has_permission(p_actor,'benefit.evidence.read') or
     not exists (select 1 from public.shop_profiles p where p.id=p_actor and p.status='active')
    then raise exception 'benefit-evidence-not-found'; end if;
  select e.id,e.case_id,c.store_id into target
    from public.shop_benefit_evidence e
    join public.shop_benefit_cases c on c.id=e.case_id
    where e.id=p_evidence_id and e.deleted_at is null and c.status <> 'draft';
  if not found or exists (select 1 from public.shop_stores s
      where s.id=target.store_id and s.owner_profile_id=p_actor) or
     exists (select 1 from public.shop_store_members m
      where m.store_id=target.store_id and m.profile_id=p_actor and m.status='active')
    then raise exception 'benefit-evidence-not-found'; end if;
  insert into public.shop_benefit_evidence_access_events(
    evidence_id,case_id,actor_profile_id,request_key
  ) values(target.id,target.case_id,p_actor,p_request_key);
  return true;
end $$;
revoke all on function public.adishop_benefit_record_evidence_preview(uuid,uuid,uuid)
  from public, anon, authenticated;
grant execute on function public.adishop_benefit_record_evidence_preview(uuid,uuid,uuid)
  to service_role;
commit;
