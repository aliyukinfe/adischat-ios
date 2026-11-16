-- Disposable local fixture only, after SEC-03G baseline assertions.
do $$ declare
  v_id uuid := '91000000-0000-4000-8000-000000000001';
  v_other uuid := '91000000-0000-4000-8000-000000000002';
  v_result jsonb;
begin
  if pg_catalog.has_function_privilege('anon','public.adishop_qa_record_firebase_revocation(uuid,text,text)','EXECUTE')
    or pg_catalog.has_function_privilege('authenticated','public.adishop_qa_record_firebase_revocation(uuid,text,text)','EXECUTE')
    or not pg_catalog.has_function_privilege('service_role','public.adishop_qa_record_firebase_revocation(uuid,text,text)','EXECUTE') then
    raise exception 'event RPC grant mismatch'; end if;
  raise notice 'PASS service-role-only event RPC';
  if pg_catalog.has_table_privilege('anon','public.shop_qa_firebase_revocation_events','SELECT')
    or pg_catalog.has_table_privilege('authenticated','public.shop_qa_firebase_revocation_events','SELECT') then
    raise exception 'event ledger client readable'; end if;
  raise notice 'PASS event ledger client denial';

  v_result := public.adishop_qa_record_firebase_revocation(v_id,'qa-firebase-b','firebase_disabled');
  if v_result->>'ok' <> 'true' or v_result->>'changed' <> 'true' or v_result->>'eventId' <> v_id::text then
    raise exception 'first event result invalid'; end if;
  raise notice 'PASS first event recorded';
  if (select count(*) from public.shop_session_families
      where profile_id='82000000-0000-4000-8000-000000000002' and revoked_at is null) <> 0
    or (select count(*) from public.shop_sessions
      where profile_id='82000000-0000-4000-8000-000000000002' and revoked_at is null) <> 0 then
    raise exception 'family not revoked'; end if;
  raise notice 'PASS complete family revocation';

  if public.adishop_qa_record_firebase_revocation(v_id,'qa-firebase-b','firebase_disabled') is distinct from v_result
    or (select count(*) from public.shop_qa_firebase_revocation_events where event_id=v_id) <> 1 then
    raise exception 'duplicate event changed result'; end if;
  raise notice 'PASS duplicate event idempotency';
  begin
    perform public.adishop_qa_record_firebase_revocation(v_id,'qa-firebase-a','firebase_disabled');
    raise exception 'conflicting event accepted';
  exception when sqlstate '22023' then null; end;
  raise notice 'PASS conflicting event rejected';
  begin
    perform public.adishop_qa_record_firebase_revocation(v_other,'qa-missing','firebase_disabled');
    raise exception 'missing identity accepted';
  exception when sqlstate 'P0001' then null; end;
  if exists(select 1 from public.shop_qa_firebase_revocation_events where event_id=v_other) then
    raise exception 'failed event committed'; end if;
  raise notice 'PASS failed event atomic rollback';
  if (select count(*) from public.shop_audit_logs where action='identity.qa_firebase_revoked') <> 2 then
    raise exception 'unexpected duplicate audit'; end if;
  raise notice 'PASS audit once per affected identity';
end $$;
