-- Disposable fixture only: relevant deployed role-revocation contract.
begin;
alter table public.shop_user_roles add column reason text;
insert into public.shop_role_permissions(role_id,permission)
values ('11000000-0000-4000-8000-000000000005','admin.role.revoke')
on conflict (role_id,permission) do nothing;

create function public.adishop_admin_audit(
  p_actor_profile_id uuid, p_action_type text, p_target_type text, p_target_id uuid,
  p_previous_state jsonb default '{}'::jsonb, p_new_state jsonb default '{}'::jsonb,
  p_reason text default null, p_metadata jsonb default '{}'::jsonb, p_request_id text default null
) returns uuid language plpgsql security definer set search_path = public as $$
declare audit_id uuid;
begin
  insert into public.shop_admin_audit_logs(actor_profile_id,actor_role,action_type,target_type,
    target_id,previous_state,new_state,reason,metadata,request_id)
  values (p_actor_profile_id,public.adishop_admin_primary_role(p_actor_profile_id),
    p_action_type,p_target_type,p_target_id,coalesce(p_previous_state,'{}'::jsonb),
    coalesce(p_new_state,'{}'::jsonb),nullif(trim(coalesce(p_reason,'')),''),
    coalesce(p_metadata,'{}'::jsonb),p_request_id)
  returning id into audit_id;
  return audit_id;
end;
$$;

create function public.adishop_admin_revoke_role(
  p_actor_profile_id uuid,p_target_profile_id uuid,p_role_code text,p_reason text default ''
) returns jsonb language plpgsql security definer set search_path = public as $$
declare remaining_super_admins integer;
begin
  if not public.adishop_profile_has_permission(p_actor_profile_id,'admin.role.revoke') then
    raise exception 'admin-forbidden';
  end if;
  if p_role_code='SHOP_SUPER_ADMIN' then
    select count(*) into remaining_super_admins
    from public.shop_user_roles ur join public.shop_roles r on r.id=ur.role_id
    where r.code='SHOP_SUPER_ADMIN' and ur.revoked_at is null
      and ur.profile_id<>p_target_profile_id;
    if remaining_super_admins<1 then raise exception 'last-super-admin-protected'; end if;
  end if;
  update public.shop_user_roles ur
    set revoked_at=now(),reason=nullif(trim(coalesce(p_reason,'')),'')
    from public.shop_roles r
    where r.id=ur.role_id and ur.profile_id=p_target_profile_id
      and r.code=p_role_code and ur.revoked_at is null;
  perform public.adishop_admin_audit(p_actor_profile_id,'admin_role.revoke','shop_profile',
    p_target_profile_id,jsonb_build_object('role',p_role_code),'{}'::jsonb,p_reason);
  return jsonb_build_object('ok',true);
end;
$$;
revoke all on function public.adishop_admin_audit(uuid,text,text,uuid,jsonb,jsonb,text,jsonb,text),
  public.adishop_admin_revoke_role(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.adishop_admin_revoke_role(uuid,uuid,text,text) to service_role;
commit;
