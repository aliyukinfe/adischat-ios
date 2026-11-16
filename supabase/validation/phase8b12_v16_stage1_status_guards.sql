-- Review-only against deployed exchange/refresh v16. Stage 1 of 3; not an active migration.
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
revoke all on function public.adishop_guard_profile_reactivation(), public.adishop_guard_link_reactivation() from public, anon, authenticated;
commit;
