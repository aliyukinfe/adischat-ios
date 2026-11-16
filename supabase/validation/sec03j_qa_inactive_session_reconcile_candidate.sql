-- Local proposal only. Apply after SEC-03G and Stage 3; no hosted execution authorized.
begin;
create function public.adishop_qa_revoke_inactive_sessions(p_verified_uid text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_profile_id uuid;
  v_status text;
  v_link_status text;
  v_family_id uuid;
  v_count integer := 0;
  v_sessions integer;
begin
  if p_verified_uid is null or length(p_verified_uid) < 1 or length(p_verified_uid) > 128 then
    raise exception 'invalid-identity' using errcode = '22023';
  end if;
  select p.id, p.status into v_profile_id, v_status from public.shop_profiles p
    where p.adischat_user_id = p_verified_uid for update;
  if v_profile_id is null then
    raise exception 'identity-not-linked' using errcode = 'P0001';
  end if;
  select l.account_status into v_link_status from public.shop_user_links l
    where l.provider = 'firebase' and l.provider_user_id = p_verified_uid
      and l.shop_profile_id = v_profile_id for update;
  if v_link_status is null then
    raise exception 'identity-not-linked' using errcode = 'P0001';
  end if;
  if v_status = 'active' and v_link_status = 'active' then
    raise exception 'identity-active' using errcode = '42501';
  end if;
  for v_family_id in select f.id from public.shop_session_families f
    where f.profile_id = v_profile_id and f.revoked_at is null order by f.id for update
  loop
    update public.shop_session_families set revoked_at = clock_timestamp()
      where id = v_family_id and revoked_at is null;
    v_count := v_count + 1;
  end loop;
  update public.shop_sessions set revoked_at = clock_timestamp()
    where profile_id = v_profile_id and revoked_at is null;
  get diagnostics v_sessions = row_count;
  if v_count > 0 or v_sessions > 0 then
    insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
      values (null, 'identity.qa_inactive_sessions_reconciled', 'shop_profile', v_profile_id,
        pg_catalog.jsonb_build_object('profile_status', v_status, 'link_status', v_link_status));
  end if;
  return pg_catalog.jsonb_build_object('ok', true, 'changed', v_count > 0 or v_sessions > 0,
    'families', v_count, 'sessions', v_sessions);
end;
$$;
revoke all on function public.adishop_qa_revoke_inactive_sessions(text) from public, anon, authenticated;
grant execute on function public.adishop_qa_revoke_inactive_sessions(text) to service_role;
commit;
