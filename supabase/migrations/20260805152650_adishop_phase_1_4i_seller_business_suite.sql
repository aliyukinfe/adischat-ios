-- Phase 1.4I seller-owned business tools. Custom Shop sessions are authenticated
-- by Edge Functions, so these tables intentionally have no direct client grants.

alter table public.shop_stores
  add column if not exists address_line text,
  add column if not exists region_state text,
  add column if not exists country text not null default 'Ethiopia',
  add column if not exists contact_phone text,
  add column if not exists contact_email text,
  add column if not exists business_hours jsonb not null default '{}'::jsonb,
  add column if not exists social_links jsonb not null default '{}'::jsonb;

alter table public.shop_campaigns
  add column if not exists store_id uuid references public.shop_stores(id) on delete cascade,
  add column if not exists campaign_type text not null default 'campaign',
  add column if not exists discount_percent integer,
  add column if not exists status text not null default 'draft';

create table if not exists public.shop_seller_settings (
  store_id uuid primary key references public.shop_stores(id) on delete cascade,
  notifications jsonb not null default '{"orders":true,"messages":true,"reviews":true,"returns":true}'::jsonb,
  shipping jsonb not null default '{}'::jsonb,
  returns jsonb not null default '{}'::jsonb,
  exchanges jsonb not null default '{}'::jsonb,
  security jsonb not null default '{}'::jsonb,
  language text not null default 'en',
  theme text not null default 'system',
  updated_by_profile_id uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_seller_settings_language_check check (language in ('en', 'am')),
  constraint shop_seller_settings_theme_check check (theme in ('system', 'light', 'dark'))
);

create table if not exists public.shop_inventory_history (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  product_id uuid not null references public.shop_products(id) on delete cascade,
  variant_id uuid references public.shop_product_variants(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  previous_quantity integer not null,
  new_quantity integer not null,
  quantity_delta integer not null,
  reason text not null default 'manual_adjustment',
  request_id text not null,
  created_at timestamptz not null default now(),
  constraint shop_inventory_history_quantities_check check (previous_quantity >= 0 and new_quantity >= 0)
);

create table if not exists public.shop_seller_customer_notes (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  customer_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  note text not null default '',
  updated_by_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_seller_customer_notes_unique unique (store_id, customer_profile_id),
  constraint shop_seller_customer_notes_length check (char_length(note) <= 1000)
);

create table if not exists public.shop_seller_coupons (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.shop_stores(id) on delete cascade,
  code text not null,
  description text,
  discount_type text not null default 'percent',
  discount_value integer not null,
  minimum_order_minor integer not null default 0,
  usage_limit integer,
  used_count integer not null default 0,
  starts_at timestamptz,
  ends_at timestamptz,
  status text not null default 'draft',
  created_by_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shop_seller_coupons_store_code_unique unique (store_id, code),
  constraint shop_seller_coupons_type_check check (discount_type in ('percent', 'fixed')),
  constraint shop_seller_coupons_value_check check (discount_value > 0),
  constraint shop_seller_coupons_status_check check (status in ('draft', 'active', 'paused', 'expired'))
);

create index if not exists shop_inventory_history_store_created_idx
  on public.shop_inventory_history(store_id, created_at desc);
create index if not exists shop_inventory_history_product_created_idx
  on public.shop_inventory_history(product_id, created_at desc);
create unique index if not exists shop_inventory_history_request_unique_idx
  on public.shop_inventory_history(
    store_id,
    request_id,
    product_id,
    coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)
  );
create index if not exists shop_seller_customer_notes_store_idx
  on public.shop_seller_customer_notes(store_id, updated_at desc);
create index if not exists shop_seller_coupons_store_status_idx
  on public.shop_seller_coupons(store_id, status, updated_at desc);
create index if not exists shop_campaigns_store_status_idx
  on public.shop_campaigns(store_id, status, start_at desc);

alter table public.shop_seller_settings enable row level security;
alter table public.shop_inventory_history enable row level security;
alter table public.shop_seller_customer_notes enable row level security;
alter table public.shop_seller_coupons enable row level security;

revoke all on table public.shop_seller_settings from anon, authenticated;
revoke all on table public.shop_inventory_history from anon, authenticated;
revoke all on table public.shop_seller_customer_notes from anon, authenticated;
revoke all on table public.shop_seller_coupons from anon, authenticated;
grant all on table public.shop_seller_settings to service_role;
grant all on table public.shop_inventory_history to service_role;
grant all on table public.shop_seller_customer_notes to service_role;
grant all on table public.shop_seller_coupons to service_role;
