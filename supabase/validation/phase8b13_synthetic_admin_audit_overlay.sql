-- Disposable fixture only. Production already has the audit table.
begin;
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
alter table public.shop_admin_audit_logs enable row level security;
revoke all on public.shop_admin_audit_logs from anon, authenticated;
grant all on public.shop_admin_audit_logs to service_role;
create policy shop_admin_audit_client_denied on public.shop_admin_audit_logs
  as restrictive for all to anon, authenticated using (false) with check (false);
commit;
