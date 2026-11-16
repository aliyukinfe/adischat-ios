-- Isolated candidate SQL only. NOT an active migration or approved production change.
begin;

create function public.adishop_guard_profile_reactivation()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.status <> 'active' and new.status = 'active' then
    raise exception 'profile-reactivation-requires-admin' using errcode = '42501';
  end if;
  return new;
end;
$$;
create trigger adishop_guard_profile_reactivation
  before update of status on public.shop_profiles
  for each row execute function public.adishop_guard_profile_reactivation();

create function public.adishop_guard_link_reactivation()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.account_status <> 'active' and new.account_status = 'active' then
    raise exception 'link-reactivation-requires-admin' using errcode = '42501';
  end if;
  return new;
end;
$$;
create trigger adishop_guard_link_reactivation
  before update of account_status on public.shop_user_links
  for each row execute function public.adishop_guard_link_reactivation();

create function public.adishop_guard_session_insert()
returns trigger language plpgsql set search_path = '' as $$
declare profile_status text; link_row record;
begin
  select status into profile_status from public.shop_profiles
    where id = new.profile_id for share;
  if profile_status is distinct from 'active' then
    raise exception 'invalid-session' using errcode = '42501';
  end if;
  for link_row in select account_status from public.shop_user_links
    where shop_profile_id = new.profile_id for share loop
    if link_row.account_status is distinct from 'active' then
      raise exception 'invalid-session' using errcode = '42501';
    end if;
  end loop;
  return new;
end;
$$;
create trigger adishop_guard_session_insert
  before insert on public.shop_sessions
  for each row execute function public.adishop_guard_session_insert();

create function public.adishop_revoke_inactive_profile_sessions()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.status = 'active' and new.status <> 'active' then
    update public.shop_session_families set revoked_at = now()
      where profile_id = new.id and revoked_at is null;
    update public.shop_sessions set revoked_at = now()
      where profile_id = new.id and revoked_at is null;
  end if;
  return new;
end;
$$;
create trigger adishop_revoke_inactive_profile_sessions
  after update of status on public.shop_profiles
  for each row execute function public.adishop_revoke_inactive_profile_sessions();

create function public.adishop_revoke_inactive_link_sessions()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.account_status = 'active' and new.account_status <> 'active' then
    update public.shop_session_families set revoked_at = now()
      where profile_id = new.shop_profile_id and revoked_at is null;
    update public.shop_sessions set revoked_at = now()
      where profile_id = new.shop_profile_id and revoked_at is null;
  end if;
  return new;
end;
$$;
create trigger adishop_revoke_inactive_link_sessions
  after update of account_status on public.shop_user_links
  for each row execute function public.adishop_revoke_inactive_link_sessions();

revoke all on function public.adishop_guard_profile_reactivation(), public.adishop_guard_link_reactivation(),
  public.adishop_guard_session_insert(), public.adishop_revoke_inactive_profile_sessions(),
  public.adishop_revoke_inactive_link_sessions() from public, anon, authenticated;
commit;
