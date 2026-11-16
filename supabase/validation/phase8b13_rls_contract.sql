-- Disposable Phase 8B.13 database only. No production connection.
\set ON_ERROR_STOP on
do $$ begin
  if current_setting('server_version_num')::int / 10000 <> 17 then raise exception 'wrong major'; end if;
  raise notice 'PASS PostgreSQL 17 major';
end $$;
do $$ begin
  if (select count(*) from pg_class where relnamespace='public'::regnamespace
    and relname in ('shop_profiles','shop_user_links','shop_sessions','shop_session_families',
      'shop_admin_audit_logs') and relrowsecurity) <> 5 then raise exception 'RLS missing'; end if;
  raise notice 'PASS identity, session, audit RLS';
end $$;
do $$ begin
  if has_function_privilege('anon','public.adishop_admin_suspend_profile(uuid,text,uuid,text)','EXECUTE')
    or has_function_privilege('authenticated','public.adishop_admin_suspend_profile(uuid,text,uuid,text)','EXECUTE')
    or not has_function_privilege('service_role','public.adishop_admin_suspend_profile(uuid,text,uuid,text)','EXECUTE')
  then raise exception 'suspension RPC grants incorrect'; end if;
  if has_function_privilege('anon','public.adishop_rotate_shop_session(text,text,text,timestamptz,timestamptz)','EXECUTE')
    or has_function_privilege('authenticated','public.adishop_rotate_shop_session(text,text,text,timestamptz,timestamptz)','EXECUTE')
  then raise exception 'rotation RPC grants incorrect'; end if;
  raise notice 'PASS service-role-only RPC execution';
end $$;
do $$ begin
  if not exists (select 1 from public.shop_roles r join public.shop_role_permissions rp on rp.role_id=r.id
    where r.code='SHOP_SUPER_ADMIN' and rp.permission='admin.profile.suspend')
    or exists (select 1 from public.shop_roles r join public.shop_role_permissions rp on rp.role_id=r.id
      where r.code<>'SHOP_SUPER_ADMIN' and rp.permission='admin.profile.suspend')
  then raise exception 'suspension permission scope incorrect'; end if;
  raise notice 'PASS dedicated Super Admin permission';
end $$;
set role anon;
do $$ begin
  if (select count(*) from public.shop_profiles) <> 0 then raise exception 'profile RLS leak'; end if;
  if has_table_privilege('anon','public.shop_admin_audit_logs','SELECT') then raise exception 'audit grant leak'; end if;
  raise notice 'PASS anonymous profile and audit isolation';
end $$;
reset role;
do $$ begin
  if not exists (select 1 from pg_trigger where tgname='adishop_guard_session_insert' and not tgisinternal)
    or not exists (select 1 from pg_trigger where tgname='adishop_revoke_inactive_profile_sessions' and not tgisinternal)
    or not exists (select 1 from pg_trigger where tgname='adishop_revoke_inactive_link_sessions' and not tgisinternal)
  then raise exception 'revocation trigger missing'; end if;
  raise notice 'PASS revocation triggers installed';
end $$;
