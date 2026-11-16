-- Disposable local PostgreSQL only. Never execute against Supabase.
do $$ begin
  if inet_server_addr() is distinct from inet '127.0.0.1'
     or current_user <> 'sec03m_restore' or current_database() <> 'postgres' then
    raise exception 'local-role-bootstrap-target-mismatch';
  end if;
end $$;
-- Preserve grants to the staging postgres role without adding local login or privileges.
create role postgres nologin;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create role authenticator nologin;
create role dashboard_user nologin;
create role supabase_admin nologin;
create role supabase_auth_admin nologin;
create role supabase_storage_admin nologin;
