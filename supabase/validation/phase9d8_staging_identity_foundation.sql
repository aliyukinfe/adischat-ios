-- Staging-only foundation for opkqyvttbyezjbqwkvho. Never apply to production.
-- Adapted from Shop identity migrations; does not create Supabase-managed roles.
begin;

create table public.shop_profiles (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid unique references auth.users(id) on delete set null,
  adischat_user_id text unique,
  display_name text,
  phone text,
  email text,
  status text not null default 'active' check (status in ('active','suspended','deleted')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table public.shop_user_links (
  id uuid primary key default gen_random_uuid(),
  shop_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  provider text not null check (provider in ('firebase','cloudflare_session','desktop_qr','supabase')),
  provider_user_id text not null,
  external_subject text,
  account_status text not null default 'active' check (account_status in ('active','suspended','disabled')),
  display_name text,
  avatar_url text,
  verified_phone text,
  verified_email text,
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_synced_at timestamptz,
  unique(provider, provider_user_id)
);
create table public.shop_identity_assertion_replays (
  jti text primary key,
  adischat_user_id text not null,
  expires_at timestamptz not null,
  used_at timestamptz not null default now()
);
create table public.shop_session_families (
  id uuid primary key,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);
create table public.shop_sessions (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  family_id uuid not null references public.shop_session_families(id) on delete cascade,
  session_jti text not null unique,
  refresh_token_hash text not null unique,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  refresh_expires_at timestamptz not null,
  revoked_at timestamptz
);
create table public.shop_roles (
  id uuid primary key default gen_random_uuid(),
  code text not null unique check (code in ('CUSTOMER','SELLER','SHOP_ADMIN','SHOP_SUPER_ADMIN','SUPPORT_AGENT')),
  name text not null,
  description text,
  created_at timestamptz not null default now()
);
create table public.shop_user_roles (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  role_id uuid not null references public.shop_roles(id) on delete cascade,
  assigned_by_profile_id uuid references public.shop_profiles(id) on delete set null,
  reason text,
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique(profile_id, role_id)
);
create table public.shop_role_permissions (
  id uuid primary key default gen_random_uuid(),
  role_id uuid not null references public.shop_roles(id) on delete cascade,
  permission text not null,
  created_at timestamptz not null default now(),
  unique(role_id, permission)
);
create table public.shop_admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text,
  action_type text not null,
  target_type text not null,
  target_id uuid,
  previous_state jsonb not null default '{}'::jsonb,
  new_state jsonb not null default '{}'::jsonb,
  reason text,
  metadata jsonb not null default '{}'::jsonb,
  request_id text,
  created_at timestamptz not null default now()
);

create index shop_sessions_family_id_idx on public.shop_sessions(family_id);
create index shop_sessions_profile_active_idx on public.shop_sessions(profile_id, expires_at) where revoked_at is null;
create index shop_session_families_profile_active_idx on public.shop_session_families(profile_id) where revoked_at is null;
create index shop_assertion_replays_expires_idx on public.shop_identity_assertion_replays(expires_at);

do $$ declare relation text; begin
  foreach relation in array array[
    'shop_profiles','shop_user_links','shop_identity_assertion_replays',
    'shop_session_families','shop_sessions','shop_roles','shop_user_roles',
    'shop_role_permissions','shop_admin_audit_logs'
  ] loop
    execute format('alter table public.%I enable row level security', relation);
    execute format('revoke all on public.%I from public, anon, authenticated', relation);
    execute format('grant select, insert, update, delete on public.%I to service_role', relation);
  end loop;
end $$;

insert into public.shop_roles(code,name) values
  ('CUSTOMER','Customer'),('SELLER','Seller'),('SHOP_ADMIN','Shop Admin'),
  ('SHOP_SUPER_ADMIN','Shop Super Admin'),('SUPPORT_AGENT','Support Agent');
commit;
