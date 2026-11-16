-- Disposable PostgreSQL 17 fixture only. Apply after Phase 8B.7 base and parity overlay.
begin;

alter table public.shop_profiles alter column id set default gen_random_uuid();
alter table public.shop_profiles add column updated_at timestamptz not null default now();
alter table public.shop_profiles add column deleted_at timestamptz;
create unique index shop_profiles_adischat_user_id_key on public.shop_profiles(adischat_user_id);

alter table public.shop_sessions alter column id set default gen_random_uuid();
alter table public.shop_sessions add column refresh_token_hash text unique;
alter table public.shop_sessions add column refresh_expires_at timestamptz;
alter table public.shop_sessions add column created_at timestamptz not null default now();

create table public.shop_identity_assertion_replays (
  jti text primary key,
  adischat_user_id text not null,
  expires_at timestamptz not null,
  used_at timestamptz not null default now()
);
create index shop_assertion_replays_expires_idx on public.shop_identity_assertion_replays(expires_at);

create table public.shop_user_links (
  id uuid primary key default gen_random_uuid(),
  shop_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  provider text not null check (provider in ('firebase','cloudflare_session','desktop_qr','supabase')),
  provider_user_id text not null,
  external_subject text,
  account_status text not null default 'active',
  display_name text,
  avatar_url text,
  verified_email text,
  verified_at timestamptz,
  updated_at timestamptz not null default now(),
  last_synced_at timestamptz,
  unique(provider, provider_user_id)
);

create table public.shop_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid references public.shop_profiles(id),
  action text not null,
  entity_type text not null,
  entity_id text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

alter table public.shop_identity_assertion_replays enable row level security;
alter table public.shop_user_links enable row level security;
alter table public.shop_audit_logs enable row level security;
revoke all on public.shop_identity_assertion_replays, public.shop_user_links, public.shop_audit_logs from anon, authenticated;
grant all on public.shop_identity_assertion_replays, public.shop_user_links, public.shop_audit_logs to service_role;
create policy client_access_deferred_shop_identity_assertion_replays
  on public.shop_identity_assertion_replays as restrictive for all to anon, authenticated using (false) with check (false);

update public.shop_profiles set adischat_user_id = case id
  when '10000000-0000-4000-8000-000000000001' then 'synthetic-buyer'
  when '10000000-0000-4000-8000-000000000002' then 'synthetic-seller'
  when '10000000-0000-4000-8000-000000000003' then 'synthetic-support'
  when '10000000-0000-4000-8000-000000000004' then 'synthetic-admin'
  when '10000000-0000-4000-8000-000000000005' then 'synthetic-super'
  else adischat_user_id end;

commit;
