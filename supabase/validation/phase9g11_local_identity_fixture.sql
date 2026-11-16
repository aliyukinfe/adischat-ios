-- Disposable local identity stub, not Supabase Auth parity.
create table public.shop_profiles (
  id uuid primary key, supabase_user_id uuid unique references auth.users(id),
  adischat_user_id text, status text not null check (status in ('active','suspended','deleted')),
  display_name text, email text, phone text, updated_at timestamptz not null default now(), deleted_at timestamptz
);
create table public.shop_user_links (
  id uuid primary key default gen_random_uuid(), shop_profile_id uuid not null references public.shop_profiles(id),
  provider text not null, provider_user_id text not null, account_status text not null
    check (account_status in ('active','suspended','disabled')),
  unique(provider,provider_user_id)
);
create table public.shop_roles (id uuid primary key default gen_random_uuid(), code text not null unique);
create table public.shop_user_roles (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  role_id uuid not null references public.shop_roles(id), revoked_at timestamptz,
  unique(profile_id,role_id)
);
create table public.shop_session_families (
  id uuid primary key, profile_id uuid not null references public.shop_profiles(id),
  created_at timestamptz not null default now(), revoked_at timestamptz
);
create table public.shop_sessions (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null references public.shop_profiles(id),
  family_id uuid not null references public.shop_session_families(id), session_jti text not null unique,
  refresh_token_hash text not null unique, created_at timestamptz not null default now(),
  expires_at timestamptz not null, refresh_expires_at timestamptz not null, revoked_at timestamptz
);
create table public.shop_audit_logs (
  id uuid primary key default gen_random_uuid(), actor_profile_id uuid references public.shop_profiles(id),
  action text not null, entity_type text not null, entity_id uuid,
  metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
insert into public.shop_roles(code) values ('CUSTOMER'),('SELLER'),('SHOP_ADMIN'),('SHOP_SUPER_ADMIN');
do $$ declare name text; begin
  foreach name in array array['shop_profiles','shop_user_links','shop_roles','shop_user_roles',
    'shop_session_families','shop_sessions','shop_audit_logs'] loop
    execute format('alter table public.%I enable row level security', name);
    execute format('revoke all on public.%I from public,anon,authenticated', name);
    execute format('grant select,insert,update,delete on public.%I to service_role', name);
  end loop;
end $$;
