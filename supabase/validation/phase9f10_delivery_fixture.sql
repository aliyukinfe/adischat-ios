-- Disposable extension for testing the unchanged delivery-reveal RPC.
create table public.shop_delivery_confirmation_codes (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.shop_orders(id) on delete cascade,
  code_hash text,
  code_salt text,
  revealed_at timestamptz,
  verified_at timestamptz,
  failed_attempts integer not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null default now(),
  delivery_code text
);
alter table public.shop_delivery_confirmation_codes enable row level security;
create policy client_access_deferred on public.shop_delivery_confirmation_codes
  for all to anon, authenticated using (false) with check (false);
grant select on public.shop_delivery_confirmation_codes to anon, authenticated;
grant select, insert, update, delete on public.shop_delivery_confirmation_codes to service_role;
