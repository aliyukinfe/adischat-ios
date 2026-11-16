-- PHASE 9G.13 STAGING DEPLOYMENT PACKAGE: PREFLIGHT & BACKUP VERIFICATION
-- Target Project: opkqyvttbyezjbqwkvho (QA Supabase)
-- NEVER EXECUTE AGAINST PRODUCTION (mcewjfnkrtiiolzibsuh).
--
-- [MANUAL OPERATOR STEP: Pre-deployment backup commands]
-- Run these via Supabase CLI prior to applying 02_schema_marketplace_qa.sql:
--   supabase db dump --project-ref opkqyvttbyezjbqwkvho --schema public > pre_deploy_schema_opkqyvttbyezjbqwkvho.sql
--   supabase db dump --project-ref opkqyvttbyezjbqwkvho --data-only > pre_deploy_data_opkqyvttbyezjbqwkvho.sql
--
-- [PREFLIGHT INTEGRITY CHECK QUERY]
-- Run this block first to confirm staging readiness without modifying any state:

do $$
declare
  missing_tables text := '';
  existing_marketplace text := '';
begin
  -- 1. Verify existing staging identity foundation is intact
  if to_regclass('public.shop_profiles') is null then missing_tables := missing_tables || ' shop_profiles'; end if;
  if to_regclass('public.shop_user_links') is null then missing_tables := missing_tables || ' shop_user_links'; end if;
  if to_regclass('public.shop_sessions') is null then missing_tables := missing_tables || ' shop_sessions'; end if;
  if to_regclass('public.shop_session_families') is null then missing_tables := missing_tables || ' shop_session_families'; end if;
  if to_regclass('public.shop_roles') is null then missing_tables := missing_tables || ' shop_roles'; end if;
  if to_regclass('public.shop_user_roles') is null then missing_tables := missing_tables || ' shop_user_roles'; end if;
  if to_regclass('public.shop_audit_logs') is null then missing_tables := missing_tables || ' shop_audit_logs'; end if;

  if length(missing_tables) > 0 then
    raise exception 'PREFLIGHT_FAIL: Missing required staging identity foundation tables:%', missing_tables;
  end if;

  -- 2. Verify marketplace tables do not already exist to prevent accidental overwrite
  if to_regclass('public.shop_products') is not null then existing_marketplace := existing_marketplace || ' shop_products'; end if;
  if to_regclass('public.shop_stores') is not null then existing_marketplace := existing_marketplace || ' shop_stores'; end if;
  if to_regclass('public.shop_carts') is not null then existing_marketplace := existing_marketplace || ' shop_carts'; end if;

  if length(existing_marketplace) > 0 then
    raise exception 'PREFLIGHT_FAIL: Marketplace objects already exist on target:%. Review before running migration.', existing_marketplace;
  end if;

  -- 3. Verify roles exist
  if not exists (select 1 from pg_roles where rolname = 'anon') or
     not exists (select 1 from pg_roles where rolname = 'authenticated') or
     not exists (select 1 from pg_roles where rolname = 'service_role') then
    raise exception 'PREFLIGHT_FAIL: Required database roles (anon, authenticated, service_role) missing.';
  end if;

  raise notice 'PREFLIGHT_PASS: Staging environment opkqyvttbyezjbqwkvho is verified ready for 02_schema_marketplace_qa.sql.';
end $$;
