create table if not exists public.shop_identity_assertion_replays (
  jti text primary key,
  adischat_user_id text not null,
  expires_at timestamp with time zone not null,
  used_at timestamp with time zone not null default now()
);

create table if not exists public.shop_sessions (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  session_jti text not null unique,
  refresh_token_hash text not null unique,
  created_at timestamp with time zone not null default now(),
  expires_at timestamp with time zone not null,
  refresh_expires_at timestamp with time zone not null,
  revoked_at timestamp with time zone
);

alter table public.shop_user_links add column if not exists external_subject text;
alter table public.shop_user_links add column if not exists account_status text not null default 'active';
alter table public.shop_user_links add column if not exists display_name text;
alter table public.shop_user_links add column if not exists avatar_url text;
alter table public.shop_user_links add column if not exists verified_phone text;
alter table public.shop_user_links add column if not exists verified_email text;
alter table public.shop_user_links add column if not exists updated_at timestamp with time zone not null default now();
alter table public.shop_user_links add column if not exists last_synced_at timestamp with time zone;

create unique index if not exists shop_profiles_active_adischat_uid
  on public.shop_profiles(adischat_user_id)
  where adischat_user_id is not null and status = 'active' and deleted_at is null;

create unique index if not exists shop_user_links_active_provider_subject
  on public.shop_user_links(provider, provider_user_id)
  where account_status = 'active';

create unique index if not exists shop_user_links_active_profile
  on public.shop_user_links(shop_profile_id)
  where account_status = 'active';

create unique index if not exists shop_carts_one_active_per_profile_currency
  on public.shop_carts(profile_id, currency)
  where status = 'active';

create unique index if not exists shop_addresses_one_default_per_profile
  on public.shop_addresses(profile_id)
  where is_default = true and deleted_at is null;

create index if not exists shop_sessions_profile_active_idx
  on public.shop_sessions(profile_id, expires_at)
  where revoked_at is null;

create index if not exists shop_assertion_replays_expires_idx
  on public.shop_identity_assertion_replays(expires_at);

alter table public.shop_identity_assertion_replays enable row level security;
alter table public.shop_sessions enable row level security;

revoke all on public.shop_identity_assertion_replays from anon, authenticated;
revoke all on public.shop_sessions from anon, authenticated;

drop policy if exists client_access_deferred_shop_identity_assertion_replays on public.shop_identity_assertion_replays;
create policy client_access_deferred_shop_identity_assertion_replays
  on public.shop_identity_assertion_replays
  as restrictive
  for all
  to anon, authenticated
  using (false)
  with check (false);

drop policy if exists client_access_deferred_shop_sessions on public.shop_sessions;
create policy client_access_deferred_shop_sessions
  on public.shop_sessions
  as restrictive
  for all
  to anon, authenticated
  using (false)
  with check (false);
