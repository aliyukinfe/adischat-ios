-- Review-only against deployed auth v16. Install before switching refresh to the scoped candidate.
begin;
create function public.adishop_rotate_shop_session(
  p_refresh_token_hash text,
  p_session_jti text,
  p_new_refresh_token_hash text,
  p_expires_at timestamptz,
  p_refresh_expires_at timestamptz
)
returns table(profile_id uuid, family_id uuid)
language plpgsql security invoker set search_path = '' as $$
declare
  v_profile_id uuid;
  v_family_id uuid;
  v_profile_status text;
  v_family_revoked_at timestamptz;
  v_old_session_id uuid;
  v_link record;
begin
  if nullif(p_refresh_token_hash, '') is null or nullif(p_session_jti, '') is null
     or nullif(p_new_refresh_token_hash, '') is null
     or p_refresh_token_hash = p_new_refresh_token_hash
     or p_expires_at <= clock_timestamp() or p_expires_at > clock_timestamp() + interval '16 minutes'
     or p_refresh_expires_at <= clock_timestamp()
     or p_refresh_expires_at > clock_timestamp() + interval '31 days' then
    raise exception 'invalid-session' using errcode = 'P0001';
  end if;

  -- Read identifiers without a row lock, then acquire every mutable lock profile first.
  select s.profile_id, s.family_id into v_profile_id, v_family_id
    from public.shop_sessions s where s.refresh_token_hash = p_refresh_token_hash;
  if not found then raise exception 'invalid-session' using errcode = 'P0001'; end if;

  select p.status into v_profile_status from public.shop_profiles p
    where p.id = v_profile_id for update;
  if v_profile_status is distinct from 'active' then
    raise exception 'invalid-session' using errcode = 'P0001';
  end if;
  for v_link in select l.account_status from public.shop_user_links l
    where l.shop_profile_id = v_profile_id for share loop
    if v_link.account_status is distinct from 'active' then
      raise exception 'invalid-session' using errcode = 'P0001';
    end if;
  end loop;

  select f.revoked_at into v_family_revoked_at from public.shop_session_families f
    where f.id = v_family_id and f.profile_id = v_profile_id for update;
  if not found or v_family_revoked_at is not null then
    raise exception 'invalid-session' using errcode = 'P0001';
  end if;
  select s.id into v_old_session_id from public.shop_sessions s
    where s.refresh_token_hash = p_refresh_token_hash
      and s.profile_id = v_profile_id and s.family_id = v_family_id
      and s.revoked_at is null and s.refresh_expires_at > clock_timestamp()
    for update;
  if not found then raise exception 'invalid-session' using errcode = 'P0001'; end if;

  update public.shop_sessions set revoked_at = clock_timestamp() where id = v_old_session_id;
  insert into public.shop_sessions(profile_id, family_id, session_jti, refresh_token_hash,
    expires_at, refresh_expires_at)
    values (v_profile_id, v_family_id, p_session_jti, p_new_refresh_token_hash,
      p_expires_at, p_refresh_expires_at);
  return query select v_profile_id, v_family_id;
end;
$$;
revoke all on function public.adishop_rotate_shop_session(text,text,text,timestamptz,timestamptz) from public, anon, authenticated;
grant execute on function public.adishop_rotate_shop_session(text,text,text,timestamptz,timestamptz) to service_role;
commit;
