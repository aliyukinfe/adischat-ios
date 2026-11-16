-- Run only against the disposable SEC-03G identity fixture.
insert into auth.users(id) values ('81000000-0000-4000-8000-000000000003');
insert into public.shop_profiles(id,supabase_user_id,adischat_user_id,status)
values ('82000000-0000-4000-8000-000000000003','81000000-0000-4000-8000-000000000003','qa-inactive-c','active');
insert into public.shop_user_links(shop_profile_id,provider,provider_user_id,account_status)
values ('82000000-0000-4000-8000-000000000003','firebase','qa-inactive-c','active');
insert into public.shop_session_families(id,profile_id)
values ('83000000-0000-4000-8000-000000000004','82000000-0000-4000-8000-000000000003');
insert into public.shop_sessions(profile_id,family_id,session_jti,refresh_token_hash,expires_at,refresh_expires_at)
values ('82000000-0000-4000-8000-000000000003','83000000-0000-4000-8000-000000000004',
  'qa-inactive-c-session','qa-inactive-c-hash',now()+interval '10 minutes',now()+interval '1 day');
do $$ declare r jsonb; begin
  if pg_catalog.has_function_privilege('anon','public.adishop_qa_revoke_inactive_sessions(text)','EXECUTE')
     or pg_catalog.has_function_privilege('authenticated','public.adishop_qa_revoke_inactive_sessions(text)','EXECUTE')
     or not pg_catalog.has_function_privilege('service_role','public.adishop_qa_revoke_inactive_sessions(text)','EXECUTE') then
    raise exception 'incorrect grants'; end if;
  raise notice 'PASS inactive reconciliation restricted grant';
  begin
    perform public.adishop_qa_revoke_inactive_sessions('qa-inactive-c');
    raise exception 'active user revoked';
  exception when sqlstate '42501' then null; end;
  raise notice 'PASS active identity denied';
  update public.shop_profiles set status='suspended' where adischat_user_id='qa-inactive-c';
  r := public.adishop_qa_revoke_inactive_sessions('qa-inactive-c');
  if r->>'ok' <> 'true' or r->>'changed' <> 'true' or r->>'families' <> '1' or r->>'sessions' <> '1' then
    raise exception 'incomplete inactive revocation'; end if;
  raise notice 'PASS inactive family and session revoked';
  r := public.adishop_qa_revoke_inactive_sessions('qa-inactive-c');
  if r->>'changed' <> 'false' or
    (select count(*) from public.shop_audit_logs where action='identity.qa_inactive_sessions_reconciled') <> 1 then
    raise exception 'duplicate inactive audit'; end if;
  raise notice 'PASS inactive duplicate idempotency';
  if (select status from public.shop_profiles where adischat_user_id='qa-inactive-c') <> 'suspended' or
     (select account_status from public.shop_user_links where provider_user_id='qa-firebase-b') <> 'disabled' or
     (select count(*) from public.shop_session_families
       where profile_id='82000000-0000-4000-8000-000000000002' and revoked_at is null) <> 0 then
    raise exception 'cross-account or status change'; end if;
  raise notice 'PASS status preservation and cross-account isolation';
end $$;
