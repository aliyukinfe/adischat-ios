-- Review-only; apply after the 8B.12 status, rotation, and revocation stages.
-- The trusted Edge caller must verify the signed Shop JWT and pass its JTI.
begin;
do $$ begin
  if not exists (select 1 from public.shop_roles where code = 'SHOP_SUPER_ADMIN') then
    raise exception 'missing-super-admin-role';
  end if;
end $$;

insert into public.shop_role_permissions(role_id, permission)
select id, 'admin.profile.suspend' from public.shop_roles where code = 'SHOP_SUPER_ADMIN'
on conflict (role_id, permission) do nothing;

create function public.adishop_admin_suspend_profile(
  p_actor_profile_id uuid,
  p_actor_session_jti text,
  p_target_profile_id uuid,
  p_reason text
)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_actor_status text;
  v_target_status text;
  v_link record;
  v_actor_link_count integer := 0;
  v_actor_family_id uuid;
  v_actor_session_id uuid;
  v_audit_id uuid;
begin
  if p_actor_profile_id is null or p_target_profile_id is null
     or p_actor_profile_id = p_target_profile_id
     or nullif(p_actor_session_jti, '') is null
     or length(p_actor_session_jti) > 256
     or length(trim(coalesce(p_reason, ''))) < 10
     or length(p_reason) > 500 then
    raise exception 'admin-suspension-invalid-request' using errcode = 'P0001';
  end if;

  -- The actor's SHARE lock blocks status changes but remains compatible with
  -- the KEY SHARE taken by the existing role-revocation audit foreign key.
  select status into v_actor_status from public.shop_profiles
    where id = p_actor_profile_id for share;
  select status into v_target_status from public.shop_profiles
    where id = p_target_profile_id for update;
  if v_actor_status is distinct from 'active' or v_target_status is distinct from 'active' then
    raise exception 'admin-suspension-invalid-state' using errcode = 'P0001';
  end if;

  for v_link in select account_status from public.shop_user_links
    where shop_profile_id = p_actor_profile_id order by id for share loop
    v_actor_link_count := v_actor_link_count + 1;
    if v_link.account_status is distinct from 'active' then
      raise exception 'admin-suspension-actor-disabled' using errcode = '42501';
    end if;
  end loop;
  if v_actor_link_count = 0 then
    raise exception 'admin-suspension-actor-unlinked' using errcode = '42501';
  end if;

  -- Hold active role assignments through the decision and audit write.
  perform 1 from public.shop_user_roles ur
    where ur.profile_id = p_actor_profile_id and ur.revoked_at is null
    order by ur.role_id for share;
  if public.adishop_admin_primary_role(p_actor_profile_id) is distinct from 'SHOP_SUPER_ADMIN'
     or not public.adishop_profile_has_permission(p_actor_profile_id, 'admin.profile.suspend')
     or public.adishop_admin_primary_role(p_target_profile_id) = 'SHOP_SUPER_ADMIN' then
    raise exception 'admin-suspension-forbidden' using errcode = '42501';
  end if;

  select f.id into v_actor_family_id from public.shop_session_families f
    join public.shop_sessions s on s.family_id = f.id and s.profile_id = f.profile_id
    where s.profile_id = p_actor_profile_id and s.session_jti = p_actor_session_jti
      and s.revoked_at is null and s.expires_at > clock_timestamp()
      and f.revoked_at is null
    for update of f;
  if v_actor_family_id is null then
    raise exception 'admin-suspension-invalid-session' using errcode = '42501';
  end if;
  select s.id into v_actor_session_id from public.shop_sessions s
    join public.shop_session_families f on f.id = s.family_id and f.profile_id = s.profile_id
    where s.profile_id = p_actor_profile_id and s.session_jti = p_actor_session_jti
      and f.id = v_actor_family_id
      and s.revoked_at is null and s.expires_at > clock_timestamp()
      and f.revoked_at is null
    for update of s;
  if v_actor_session_id is null then
    raise exception 'admin-suspension-invalid-session' using errcode = '42501';
  end if;

  update public.shop_profiles set status = 'suspended'
    where id = p_target_profile_id and status = 'active';
  if not found then raise exception 'admin-suspension-stale' using errcode = 'P0001'; end if;
  -- The 8B.12 trigger also revokes these. Explicit updates make this RPC fail closed
  -- if a future trigger change omits the revocation.
  update public.shop_session_families set revoked_at = clock_timestamp()
    where profile_id = p_target_profile_id and revoked_at is null;
  update public.shop_sessions set revoked_at = clock_timestamp()
    where profile_id = p_target_profile_id and revoked_at is null;

  insert into public.shop_admin_audit_logs(
    actor_profile_id, actor_role, action_type, target_type, target_id,
    previous_state, new_state, reason
  ) values (
    p_actor_profile_id, 'SHOP_SUPER_ADMIN', 'admin_profile.suspend', 'shop_profile',
    p_target_profile_id, jsonb_build_object('status', v_target_status),
    jsonb_build_object('status', 'suspended'), trim(p_reason)
  ) returning id into v_audit_id;
  return jsonb_build_object('ok', true, 'targetProfileId', p_target_profile_id,
    'status', 'suspended', 'auditId', v_audit_id);
end;
$$;

revoke all on function public.adishop_admin_suspend_profile(uuid,text,uuid,text)
  from public, anon, authenticated;
grant execute on function public.adishop_admin_suspend_profile(uuid,text,uuid,text)
  to service_role;
commit;
