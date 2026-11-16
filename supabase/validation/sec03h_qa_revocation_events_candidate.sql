-- Staging-only proposal. Apply after sec03g_qa_firebase_revocation_candidate.sql.
-- Call only from a server that verifies Cloudflare Access and Firebase QA state.
begin;
create table public.shop_qa_firebase_revocation_events (
  event_id uuid primary key,
  firebase_uid text not null,
  cause text not null check (cause in ('firebase_disabled','firebase_tokens_revoked','allowlist_removed')),
  result jsonb not null,
  recorded_at timestamptz not null default clock_timestamp()
);
create index shop_qa_firebase_revocation_events_uid_idx
  on public.shop_qa_firebase_revocation_events(firebase_uid, recorded_at desc);
alter table public.shop_qa_firebase_revocation_events enable row level security;
revoke all on public.shop_qa_firebase_revocation_events from public, anon, authenticated;
grant select, insert on public.shop_qa_firebase_revocation_events to service_role;
create policy deny_client_shop_qa_firebase_revocation_events
  on public.shop_qa_firebase_revocation_events as restrictive for all to anon, authenticated
  using (false) with check (false);

create function public.adishop_qa_record_firebase_revocation(
  p_event_id uuid,
  p_verified_uid text,
  p_cause text
)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_existing public.shop_qa_firebase_revocation_events%rowtype;
  v_result jsonb;
begin
  if p_event_id is null or p_verified_uid is null or p_cause is null then
    raise exception 'invalid-revocation-event' using errcode = '22023';
  end if;
  select * into v_existing from public.shop_qa_firebase_revocation_events
    where event_id = p_event_id;
  if found then
    if v_existing.firebase_uid is distinct from p_verified_uid or v_existing.cause is distinct from p_cause then
      raise exception 'revocation-event-conflict' using errcode = '22023';
    end if;
    return v_existing.result;
  end if;

  v_result := public.adishop_qa_revoke_firebase_identity(p_verified_uid, p_cause);
  v_result := v_result || pg_catalog.jsonb_build_object('eventId', p_event_id);
  insert into public.shop_qa_firebase_revocation_events(event_id, firebase_uid, cause, result)
    values (p_event_id, p_verified_uid, p_cause, v_result)
    on conflict (event_id) do nothing;
  if not found then
    select * into v_existing from public.shop_qa_firebase_revocation_events
      where event_id = p_event_id;
    if v_existing.firebase_uid is distinct from p_verified_uid or v_existing.cause is distinct from p_cause then
      raise exception 'revocation-event-conflict' using errcode = '22023';
    end if;
    return v_existing.result;
  end if;
  return v_result;
end;
$$;
revoke all on function public.adishop_qa_record_firebase_revocation(uuid,text,text)
  from public, anon, authenticated;
grant execute on function public.adishop_qa_record_firebase_revocation(uuid,text,text)
  to service_role;
commit;
