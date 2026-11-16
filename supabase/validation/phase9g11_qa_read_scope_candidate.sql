-- Local QA candidate only. Service-role lookup after verified Shop JWT; not a write gate.
begin;
create schema if not exists shop_qa_private;
revoke all on schema shop_qa_private from public, anon, authenticated;
grant usage on schema shop_qa_private to service_role;

create or replace function shop_qa_private.active_read_scope(p_session_jti text)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('profileId', p.id, 'sessionId', sess.id)
  from public.shop_profiles p
  join public.shop_sessions sess on sess.profile_id = p.id
  join public.shop_session_families family on family.id = sess.family_id
    and family.profile_id = p.id
  where sess.session_jti = p_session_jti
    and p.status = 'active'
    and sess.revoked_at is null and sess.expires_at > now()
    and family.revoked_at is null
    and exists (select 1 from public.shop_user_links link
      where link.shop_profile_id = p.id and link.account_status = 'active')
    and not exists (select 1 from public.shop_user_links link
      where link.shop_profile_id = p.id and link.account_status <> 'active')
$$;
revoke all on function shop_qa_private.active_read_scope(text) from public, anon, authenticated;
grant execute on function shop_qa_private.active_read_scope(text) to service_role;

create or replace function public.adishop_qa_active_read_scope(p_session_jti text)
returns jsonb language sql stable set search_path = '' as $$
  select shop_qa_private.active_read_scope(p_session_jti)
$$;
revoke all on function public.adishop_qa_active_read_scope(text) from public, anon, authenticated;
grant execute on function public.adishop_qa_active_read_scope(text) to service_role;
commit;
