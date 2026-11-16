-- Local staging proposal only. The caller must verify the Firebase QA UID server-side.
-- Requires the deployed Stage 2 atomic rotation and Stage 3 revocation guards.
begin;
create or replace function public.adishop_qa_revoke_firebase_identity(
  p_verified_uid text,
  p_cause text
)
returns jsonb
language plpgsql security invoker set search_path = '' as $$
declare
  v_profile_id uuid;
  v_link_id uuid;
  v_family_id uuid;
  v_rows integer;
  v_changed boolean := false;
begin
  if p_verified_uid is null or length(p_verified_uid) < 1 or length(p_verified_uid) > 128
     or p_cause is null or p_cause not in ('firebase_disabled', 'firebase_tokens_revoked', 'allowlist_removed') then
    raise exception 'invalid-revocation-request' using errcode = '22023';
  end if;

  select p.id into v_profile_id from public.shop_profiles p
    where p.adischat_user_id = p_verified_uid for update;
  if v_profile_id is null then
    raise exception 'identity-not-linked' using errcode = 'P0001';
  end if;
  select l.id into v_link_id from public.shop_user_links l
    where l.provider = 'firebase' and l.provider_user_id = p_verified_uid
      and l.shop_profile_id = v_profile_id for update;
  if v_link_id is null then
    raise exception 'identity-not-linked' using errcode = 'P0001';
  end if;

  update public.shop_user_links set account_status = 'disabled', updated_at = clock_timestamp()
    where id = v_link_id and account_status = 'active';
  get diagnostics v_rows = row_count;
  v_changed := v_rows > 0;

  for v_family_id in
    select f.id from public.shop_session_families f
      where f.profile_id = v_profile_id and f.revoked_at is null
      order by f.id for update
  loop
    update public.shop_session_families set revoked_at = clock_timestamp()
      where id = v_family_id and revoked_at is null;
    v_changed := true;
  end loop;
  update public.shop_sessions set revoked_at = clock_timestamp()
    where profile_id = v_profile_id and revoked_at is null;
  get diagnostics v_rows = row_count;
  v_changed := v_changed or v_rows > 0;

  if v_changed then
    insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
      values (null, 'identity.qa_firebase_revoked', 'shop_profile', v_profile_id,
        pg_catalog.jsonb_build_object('cause', p_cause));
  end if;
  return pg_catalog.jsonb_build_object('ok', true, 'changed', v_changed);
end;
$$;
revoke all on function public.adishop_qa_revoke_firebase_identity(text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_revoke_firebase_identity(text,text) to service_role;
commit;
