-- Disposable phase9c5 database only. Minimal dependencies for exact deployed RPC bodies.
alter table public.shop_stores
  add column status text not null default 'active',
  add column approval_status text not null default 'approved';
create table public.shop_store_members (
  store_id uuid not null references public.shop_stores(id),
  profile_id uuid not null, status text not null, role text not null,
  primary key (store_id, profile_id)
);
alter table public.shop_inventory
  add column id uuid not null default gen_random_uuid(),
  add column quantity_reserved integer not null default 0 check (quantity_reserved >= 0),
  add constraint phase9c5_inventory_id_key unique (id),
  add constraint phase9c5_available_check check (quantity_available >= 0),
  add constraint phase9c5_reserved_check check (quantity_reserved <= quantity_available);
alter table public.shop_order_groups
  add column rejected_reason text,
  add column rejected_reason_code text,
  add column buyer_visible_rejection_reason text,
  add column rejected_request_id text,
  add column shipment_carrier text,
  add column shipment_tracking_number text,
  add column shipped_at timestamptz;
alter table public.shop_order_status_history
  add column order_group_id uuid references public.shop_order_groups(id),
  add column request_id text,
  add column metadata jsonb;
alter table public.shop_notifications
  add column created_at timestamptz not null default clock_timestamp();
create unique index phase9c5_notification_dedupe
  on public.shop_notifications(profile_id, type, (data->>'dedupeKey'))
  where data ? 'dedupeKey';
