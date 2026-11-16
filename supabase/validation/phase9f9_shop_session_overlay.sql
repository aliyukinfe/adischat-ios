-- Disposable Shop-session records for local Edge HTTP checks only.
create table public.shop_profiles (
  id uuid primary key, status text not null, display_name text, email text,
  phone text, adischat_user_id text
);
create table public.shop_sessions (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id),
  session_jti text not null unique, expires_at timestamptz not null,
  revoked_at timestamptz
);
alter table public.shop_profiles enable row level security;
alter table public.shop_sessions enable row level security;
create policy client_access_deferred on public.shop_profiles for all to anon, authenticated using (false) with check (false);
create policy client_access_deferred on public.shop_sessions for all to anon, authenticated using (false) with check (false);
grant select on public.shop_profiles, public.shop_sessions to anon, authenticated;
grant select, insert, update, delete on public.shop_profiles, public.shop_sessions to service_role;
insert into public.shop_profiles(id,status) values
  ('40000000-0000-4000-8000-000000000001','active'),
  ('40000000-0000-4000-8000-000000000002','active'),
  ('20000000-0000-4000-8000-000000000001','active'),
  ('20000000-0000-4000-8000-000000000002','active'),
  ('50000000-0000-4000-8000-000000000001','active');
insert into public.shop_sessions(profile_id,session_jti,expires_at) values
  ('40000000-0000-4000-8000-000000000001','buyer-a',clock_timestamp()+interval '1 hour'),
  ('40000000-0000-4000-8000-000000000002','buyer-b',clock_timestamp()+interval '1 hour'),
  ('20000000-0000-4000-8000-000000000001','seller-a',clock_timestamp()+interval '1 hour'),
  ('20000000-0000-4000-8000-000000000002','seller-b',clock_timestamp()+interval '1 hour'),
  ('50000000-0000-4000-8000-000000000001','admin',clock_timestamp()+interval '1 hour'),
  ('40000000-0000-4000-8000-000000000001','expired',clock_timestamp()-interval '1 hour');
