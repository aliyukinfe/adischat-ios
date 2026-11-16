-- Run only in a fresh disposable local database with phase9g11 identity fixture.
alter table public.shop_user_links add column updated_at timestamptz not null default now();
insert into auth.users(id) values ('81000000-0000-4000-8000-000000000001'),('81000000-0000-4000-8000-000000000002');
insert into public.shop_profiles(id,supabase_user_id,adischat_user_id,status)
values ('82000000-0000-4000-8000-000000000001','81000000-0000-4000-8000-000000000001','qa-firebase-a','active'),
       ('82000000-0000-4000-8000-000000000002','81000000-0000-4000-8000-000000000002','qa-firebase-b','active');
insert into public.shop_user_links(shop_profile_id,provider,provider_user_id,account_status)
values ('82000000-0000-4000-8000-000000000001','firebase','qa-firebase-a','active'),
       ('82000000-0000-4000-8000-000000000002','firebase','qa-firebase-b','active');
insert into public.shop_session_families(id,profile_id)
values ('83000000-0000-4000-8000-000000000001','82000000-0000-4000-8000-000000000001'),
       ('83000000-0000-4000-8000-000000000002','82000000-0000-4000-8000-000000000001'),
       ('83000000-0000-4000-8000-000000000003','82000000-0000-4000-8000-000000000002');
insert into public.shop_sessions(profile_id,family_id,session_jti,refresh_token_hash,expires_at,refresh_expires_at)
values ('82000000-0000-4000-8000-000000000001','83000000-0000-4000-8000-000000000001','qa-session-a1','qa-hash-a1',now()+interval '10 minutes',now()+interval '1 day'),
       ('82000000-0000-4000-8000-000000000001','83000000-0000-4000-8000-000000000002','qa-session-a2','qa-hash-a2',now()+interval '10 minutes',now()+interval '1 day'),
       ('82000000-0000-4000-8000-000000000002','83000000-0000-4000-8000-000000000003','qa-session-b1','qa-hash-b1',now()+interval '10 minutes',now()+interval '1 day');

do $$ declare r jsonb; begin
  if pg_catalog.has_function_privilege('anon','public.adishop_qa_revoke_firebase_identity(text,text)','EXECUTE')
     or pg_catalog.has_function_privilege('authenticated','public.adishop_qa_revoke_firebase_identity(text,text)','EXECUTE')
     or not pg_catalog.has_function_privilege('service_role','public.adishop_qa_revoke_firebase_identity(text,text)','EXECUTE') then
    raise exception 'grant check failed'; end if;
  raise notice 'PASS restricted grants';

  begin perform public.adishop_qa_revoke_firebase_identity('qa-firebase-a','invalid');
    raise exception 'invalid cause accepted';
  exception when sqlstate '22023' then null; end;
  raise notice 'PASS invalid cause denied';

  begin perform public.adishop_qa_revoke_firebase_identity('qa-firebase-missing','firebase_disabled');
    raise exception 'missing identity accepted';
  exception when sqlstate 'P0001' then null; end;
  raise notice 'PASS missing identity denied';

  r := public.adishop_qa_revoke_firebase_identity('qa-firebase-a','firebase_tokens_revoked');
  if r->>'changed' <> 'true' then raise exception 'first revocation unchanged'; end if;
  raise notice 'PASS first revocation';

  if (select account_status from public.shop_user_links where provider_user_id='qa-firebase-a') <> 'disabled' then
    raise exception 'link still active'; end if;
  raise notice 'PASS link disabled';

  if (select count(*) from public.shop_session_families where profile_id='82000000-0000-4000-8000-000000000001' and revoked_at is null) <> 0
     or (select count(*) from public.shop_sessions where profile_id='82000000-0000-4000-8000-000000000001' and revoked_at is null) <> 0 then
    raise exception 'sessions still active'; end if;
  raise notice 'PASS all families and sessions revoked';

  r := public.adishop_qa_revoke_firebase_identity('qa-firebase-a','firebase_tokens_revoked');
  if r->>'changed' <> 'false' then raise exception 'retry changed state'; end if;
  if (select count(*) from public.shop_audit_logs where action='identity.qa_firebase_revoked') <> 1 then
    raise exception 'duplicate audit'; end if;
  raise notice 'PASS idempotent retry and audit';

  if (select account_status from public.shop_user_links where provider_user_id='qa-firebase-b') <> 'active'
     or (select count(*) from public.shop_session_families where profile_id='82000000-0000-4000-8000-000000000002' and revoked_at is null) <> 1 then
    raise exception 'other account affected'; end if;
  raise notice 'PASS cross-account isolation';
end $$;
