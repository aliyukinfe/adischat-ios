create table public.shop_session_families (
  id uuid primary key,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

alter table public.shop_sessions add column family_id uuid;

insert into public.shop_session_families (id, profile_id, revoked_at)
select id, profile_id, revoked_at from public.shop_sessions;

update public.shop_sessions set family_id = id;

alter table public.shop_sessions
  alter column family_id set not null,
  add constraint shop_sessions_family_id_fkey
    foreign key (family_id) references public.shop_session_families(id) on delete cascade;

create index shop_sessions_family_id_idx on public.shop_sessions(family_id);
create index shop_session_families_profile_active_idx
  on public.shop_session_families(profile_id) where revoked_at is null;

alter table public.shop_session_families enable row level security;
revoke all on public.shop_session_families from public, anon, authenticated;
grant select, insert, update on public.shop_session_families to service_role;

create policy client_access_deferred_shop_session_families
  on public.shop_session_families as restrictive for all to anon, authenticated
  using (false) with check (false);

create function public.shop_session_family_insert_guard()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  owner_profile_id uuid;
  family_revoked_at timestamptz;
begin
  if new.family_id is null then
    new.family_id := pg_catalog.gen_random_uuid();
    insert into public.shop_session_families (id, profile_id)
      values (new.family_id, new.profile_id);
  end if;

  select profile_id, revoked_at into owner_profile_id, family_revoked_at
    from public.shop_session_families where id = new.family_id for update;
  if not found or owner_profile_id <> new.profile_id or family_revoked_at is not null then
    raise exception 'shop-session-family-revoked' using errcode = 'P0001';
  end if;
  return new;
end;
$$;

create trigger shop_session_family_insert_guard
  before insert on public.shop_sessions
  for each row execute function public.shop_session_family_insert_guard();

create function public.shop_session_family_revoke_rows()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.revoked_at is null and new.revoked_at is not null then
    update public.shop_sessions
      set revoked_at = new.revoked_at
      where family_id = new.id and revoked_at is null;
  end if;
  return new;
end;
$$;

create trigger shop_session_family_revoke_rows
  after update of revoked_at on public.shop_session_families
  for each row execute function public.shop_session_family_revoke_rows();

revoke all on function public.shop_session_family_insert_guard() from public, anon, authenticated;
revoke all on function public.shop_session_family_revoke_rows() from public, anon, authenticated;
