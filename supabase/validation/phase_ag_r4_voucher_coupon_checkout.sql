-- Phase AG-R4: Voucher, Coupon and Secure Checkout Integration
-- Target: AdisGebeya Staging (opkqyvttbyezjbqwkvho)
-- Supabase Free Tier - $0.00 spend - Strictly untouched production

-- ============================================================================
-- 1. Coupon Schema
-- ============================================================================
create table if not exists public.shop_coupons (
  id uuid primary key default gen_random_uuid(),
  code text not null unique check (code = upper(trim(code)) and length(code) between 3 and 32),
  description text,
  discount_type text not null check (discount_type in ('fixed', 'percent')),
  discount_value integer not null check (discount_value > 0),
  min_order_minor integer not null default 0 check (min_order_minor >= 0),
  max_discount_minor integer check (max_discount_minor is null or max_discount_minor > 0),
  usage_limit integer not null default 100 check (usage_limit > 0),
  usage_count integer not null default 0 check (usage_count >= 0),
  user_limit integer not null default 1 check (user_limit > 0),
  store_id uuid references public.shop_stores(id) on delete cascade,
  start_at timestamptz not null default now(),
  expires_at timestamptz not null check (expires_at > start_at),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_coupon_usages (
  id uuid primary key default gen_random_uuid(),
  coupon_id uuid not null references public.shop_coupons(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  order_id uuid references public.shop_orders(id) on delete cascade,
  checkout_session_id uuid,
  discount_minor integer not null check (discount_minor > 0),
  created_at timestamptz not null default now(),
  unique (coupon_id, profile_id, order_id)
);

alter table public.shop_coupons enable row level security;
alter table public.shop_coupon_usages enable row level security;
revoke all on public.shop_coupons from public, anon, authenticated;
revoke all on public.shop_coupon_usages from public, anon, authenticated;
grant select on public.shop_coupons to service_role;
grant select, insert, update on public.shop_coupon_usages to service_role;

-- ============================================================================
-- 2. Voucher Schema (Prepaid Value)
-- ============================================================================
create table if not exists public.shop_vouchers (
  id uuid primary key default gen_random_uuid(),
  code text not null unique check (code = upper(trim(code)) and length(code) between 6 and 36),
  initial_value_minor integer not null check (initial_value_minor > 0),
  remaining_balance_minor integer not null check (remaining_balance_minor >= 0 and remaining_balance_minor <= initial_value_minor),
  currency char(3) not null default 'ETB' check (currency = 'ETB'),
  status text not null default 'active' check (status in ('active', 'redeemed', 'expired', 'suspended', 'cancelled')),
  expires_at timestamptz not null default (now() + interval '90 days'),
  created_by_profile_id uuid references public.shop_profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_voucher_transactions (
  id uuid primary key default gen_random_uuid(),
  voucher_id uuid not null references public.shop_vouchers(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  order_id uuid references public.shop_orders(id) on delete set null,
  checkout_session_id uuid,
  transaction_type text not null check (transaction_type in ('redemption', 'refund_reversal', 'adjustment')),
  amount_minor integer not null check (amount_minor > 0),
  remaining_balance_after integer not null check (remaining_balance_after >= 0),
  idempotency_key text,
  created_at timestamptz not null default now()
);

alter table public.shop_vouchers enable row level security;
alter table public.shop_voucher_transactions enable row level security;
revoke all on public.shop_vouchers from public, anon, authenticated;
revoke all on public.shop_voucher_transactions from public, anon, authenticated;
grant select, insert, update on public.shop_vouchers to service_role;
grant select, insert on public.shop_voucher_transactions to service_role;

-- ============================================================================
-- 3. Checkout Sessions & Reservations Schema
-- ============================================================================
create table if not exists public.shop_checkout_sessions (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  cart_id uuid references public.shop_carts(id) on delete set null,
  idempotency_key text not null,
  source text not null default 'CART' check (source in ('CART', 'BUY_NOW')),
  state text not null default 'reserved' check (state in ('reserved', 'confirmed', 'expired', 'cancelled')),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  subtotal_minor integer not null default 0 check (subtotal_minor >= 0),
  coupon_id uuid references public.shop_coupons(id) on delete set null,
  coupon_discount_minor integer not null default 0 check (coupon_discount_minor >= 0),
  voucher_id uuid references public.shop_vouchers(id) on delete set null,
  voucher_applied_minor integer not null default 0 check (voucher_applied_minor >= 0),
  shipping_total_minor integer not null default 0 check (shipping_total_minor >= 0),
  tax_total_minor integer not null default 0 check (tax_total_minor >= 0),
  remaining_payable_minor integer not null default 0 check (remaining_payable_minor >= 0),
  grand_total_minor integer not null default 0 check (grand_total_minor >= 0),
  expires_at timestamptz not null default (now() + interval '15 minutes'),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz,
  order_id uuid,
  unique (profile_id, idempotency_key)
);

create table if not exists public.shop_checkout_items (
  id uuid primary key default gen_random_uuid(),
  checkout_session_id uuid not null references public.shop_checkout_sessions(id) on delete cascade,
  cart_item_id uuid references public.shop_cart_items(id) on delete set null,
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  store_id uuid not null references public.shop_stores(id) on delete restrict,
  inventory_id uuid not null references public.shop_inventory(id) on delete restrict,
  quantity integer not null check (quantity > 0 and quantity <= 99),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  unit_price_minor integer not null check (unit_price_minor >= 0),
  line_total_minor integer not null check (line_total_minor >= 0),
  product_title_snapshot text not null,
  variant_snapshot jsonb not null default '{}'::jsonb,
  image_snapshot text,
  store_snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_inventory_reservations (
  id uuid primary key default gen_random_uuid(),
  checkout_session_id uuid not null references public.shop_checkout_sessions(id) on delete cascade,
  inventory_id uuid not null references public.shop_inventory(id) on delete restrict,
  product_id uuid not null references public.shop_products(id) on delete restrict,
  variant_id uuid references public.shop_product_variants(id) on delete restrict,
  quantity integer not null check (quantity > 0),
  status text not null default 'active' check (status in ('active', 'released', 'committed', 'expired')),
  expires_at timestamptz not null,
  released_at timestamptz,
  committed_at timestamptz,
  created_at timestamptz not null default now(),
  unique (checkout_session_id, inventory_id)
);

alter table public.shop_checkout_sessions enable row level security;
alter table public.shop_checkout_items enable row level security;
alter table public.shop_inventory_reservations enable row level security;
revoke all on public.shop_checkout_sessions from public, anon, authenticated;
revoke all on public.shop_checkout_items from public, anon, authenticated;
revoke all on public.shop_inventory_reservations from public, anon, authenticated;
grant select, insert, update on public.shop_checkout_sessions to service_role;
grant select, insert, update on public.shop_checkout_items to service_role;
grant select, insert, update on public.shop_inventory_reservations to service_role;

-- ============================================================================
-- 4. Payment Methods Schema
-- ============================================================================
create table if not exists public.shop_payment_methods (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  label text not null,
  provider text not null default 'manual',
  enabled boolean not null default false,
  requires_online_provider boolean not null default true,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.shop_payment_methods enable row level security;
revoke all on public.shop_payment_methods from public, anon, authenticated;
grant select, insert, update on public.shop_payment_methods to service_role;

insert into public.shop_payment_methods (code, label, provider, enabled, requires_online_provider, metadata)
values
  ('voucher_prepaid', 'AdisGebeya Voucher', 'internal_voucher', true, false, '{"description":"Prepaid electronic store credit"}'::jsonb),
  ('cash_on_delivery', 'Cash on Delivery', 'manual', true, false, '{"description":"Pay cash upon physical package receipt"}'::jsonb),
  ('cbe_birr_manual', 'CBE Birr (Manual Transfer)', 'manual', true, false, '{"description":"Direct bank/wallet confirmation"}'::jsonb)
on conflict (code) do update set enabled = excluded.enabled, label = excluded.label, provider = excluded.provider;

-- ============================================================================
-- 5. Helper Function: Recalculate Checkout Session Totals
-- ============================================================================
create or replace function public.shop_recalculate_checkout(p_checkout_id uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  cs record;
  cp record;
  vc record;
  computed_subtotal integer := 0;
  computed_coupon_discount integer := 0;
  net_after_coupon integer := 0;
  computed_voucher_applied integer := 0;
  remaining_payable integer := 0;
begin
  select * into cs from public.shop_checkout_sessions where id = p_checkout_id for update;
  if not found then raise exception 'checkout-not-found'; end if;

  select coalesce(sum(line_total_minor), 0) into computed_subtotal
  from public.shop_checkout_items where checkout_session_id = p_checkout_id;

  -- 1. Apply coupon if attached
  if cs.coupon_id is not null then
    select * into cp from public.shop_coupons where id = cs.coupon_id;
    if found and cp.is_active and cp.start_at <= now() and cp.expires_at > now()
       and (cp.min_order_minor <= computed_subtotal) then
      if cp.discount_type = 'fixed' then
        computed_coupon_discount := least(cp.discount_value, computed_subtotal);
      else
        computed_coupon_discount := (computed_subtotal * cp.discount_value) / 100;
        if cp.max_discount_minor is not null then
          computed_coupon_discount := least(computed_coupon_discount, cp.max_discount_minor);
        end if;
      end if;
      computed_coupon_discount := least(computed_coupon_discount, computed_subtotal);
    else
      -- Coupon no longer valid: drop it
      cs.coupon_id := null;
      computed_coupon_discount := 0;
    end if;
  end if;

  net_after_coupon := greatest(0, computed_subtotal - computed_coupon_discount + cs.shipping_total_minor + cs.tax_total_minor);

  -- 2. Apply voucher if attached
  if cs.voucher_id is not null then
    select * into vc from public.shop_vouchers where id = cs.voucher_id;
    if found and vc.status = 'active' and vc.expires_at > now() and vc.remaining_balance_minor > 0 then
      computed_voucher_applied := least(vc.remaining_balance_minor, net_after_coupon);
    else
      -- Voucher no longer valid: drop it
      cs.voucher_id := null;
      computed_voucher_applied := 0;
    end if;
  end if;

  remaining_payable := greatest(0, net_after_coupon - computed_voucher_applied);

  update public.shop_checkout_sessions
  set subtotal_minor = computed_subtotal,
      coupon_id = cs.coupon_id,
      coupon_discount_minor = computed_coupon_discount,
      voucher_id = cs.voucher_id,
      voucher_applied_minor = computed_voucher_applied,
      remaining_payable_minor = remaining_payable,
      grand_total_minor = remaining_payable
  where id = p_checkout_id;

  return jsonb_build_object(
    'checkoutId', p_checkout_id,
    'subtotalMinor', computed_subtotal,
    'couponDiscountMinor', computed_coupon_discount,
    'couponCode', cp.code,
    'voucherAppliedMinor', computed_voucher_applied,
    'voucherCode', vc.code,
    'shippingMinor', cs.shipping_total_minor,
    'taxMinor', cs.tax_total_minor,
    'remainingPayableMinor', remaining_payable,
    'grandTotalMinor', remaining_payable,
    'isFullyCoveredByVoucher', (remaining_payable = 0 and computed_voucher_applied > 0)
  );
end;
$$;
revoke all on function public.shop_recalculate_checkout(uuid) from public, anon, authenticated;
grant execute on function public.shop_recalculate_checkout(uuid) to service_role;

-- ============================================================================
-- 6. RPC: Create Synthetic Checkout Session (Buy Now or Cart)
-- ============================================================================
create or replace function public.adishop_qa_create_checkout(
  p_session_jti text,
  p_source text,
  p_idempotency_key text,
  p_product_id uuid default null,
  p_variant_id uuid default null,
  p_quantity integer default 1
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  existing_session public.shop_checkout_sessions%rowtype;
  cart_record public.shop_carts%rowtype;
  checkout_id uuid;
  expires_at_value timestamptz := now() + interval '15 minutes';
  product_record record;
  inventory_record public.shop_inventory%rowtype;
  summary jsonb;
  item_price integer;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_idempotency_key is null or length(trim(p_idempotency_key)) < 8 then
    raise exception 'invalid-idempotency-key' using errcode = '22023'; end if;
  if p_source not in ('CART', 'BUY_NOW') then
    raise exception 'invalid-source' using errcode = '22023'; end if;

  -- Return existing session if idempotency key matches
  select * into existing_session
  from public.shop_checkout_sessions
  where profile_id = actor and idempotency_key = p_idempotency_key
  order by created_at desc limit 1;

  if found then
    return public.shop_recalculate_checkout(existing_session.id);
  end if;

  insert into public.shop_checkout_sessions(
    profile_id, idempotency_key, source, state, currency, expires_at
  ) values (
    actor, p_idempotency_key, p_source, 'reserved', 'ETB', expires_at_value
  ) returning id into checkout_id;

  if p_source = 'BUY_NOW' then
    if p_product_id is null or p_quantity is null or p_quantity < 1 or p_quantity > 99 then
      raise exception 'invalid-buy-now-input' using errcode = '22023'; end if;

    select p.id, p.store_id, p.title, p.price_minor, p.status, p.moderation_status,
           p.stock_status, p.deleted_at, s.name as store_name, s.status as store_status,
           s.approval_status as store_approval_status,
           v.title as variant_title, v.options as variant_options, v.price_minor as variant_price,
           v.status as variant_status,
           (select image_url from public.shop_product_images pi where pi.product_id = p.id order by pi.is_primary desc, pi.display_order asc limit 1) as image_url
    into product_record
    from public.shop_products p
    join public.shop_stores s on s.id = p.store_id
    left join public.shop_product_variants v on v.id = p_variant_id and v.product_id = p.id
    where p.id = p_product_id and p.deleted_at is null and s.deleted_at is null;

    if not found or product_record.status <> 'active' or product_record.moderation_status <> 'approved'
       or product_record.store_status <> 'active' or product_record.store_approval_status <> 'approved'
       or product_record.stock_status = 'out_of_stock'
       or (p_variant_id is not null and product_record.variant_status <> 'active') then
      raise exception 'unavailable-item' using errcode = '22023'; end if;

    select * into inventory_record from public.shop_inventory
    where product_id = p_product_id
      and ((p_variant_id is null and variant_id is null) or variant_id = p_variant_id)
    for update;

    if not found or (inventory_record.quantity_available - inventory_record.quantity_reserved) < p_quantity then
      raise exception 'insufficient-stock' using errcode = '22023'; end if;

    update public.shop_inventory
      set quantity_reserved = quantity_reserved + p_quantity, updated_at = now()
      where id = inventory_record.id;

    item_price := coalesce(product_record.variant_price, product_record.price_minor);

    insert into public.shop_checkout_items(
      checkout_session_id, product_id, variant_id, store_id, inventory_id, quantity,
      currency, unit_price_minor, line_total_minor, product_title_snapshot,
      variant_snapshot, image_snapshot, store_snapshot
    ) values (
      checkout_id, p_product_id, p_variant_id, product_record.store_id, inventory_record.id,
      p_quantity, 'ETB', item_price, item_price * p_quantity, product_record.title,
      jsonb_build_object('id', p_variant_id, 'title', product_record.variant_title, 'options', coalesce(product_record.variant_options, '{}'::jsonb)),
      product_record.image_url,
      jsonb_build_object('id', product_record.store_id, 'name', product_record.store_name)
    );

    insert into public.shop_inventory_reservations(
      checkout_session_id, inventory_id, product_id, variant_id, quantity, status, expires_at
    ) values (
      checkout_id, inventory_record.id, p_product_id, p_variant_id, p_quantity, 'active', expires_at_value
    );
  end if;

  return public.shop_recalculate_checkout(checkout_id);
end;
$$;
revoke all on function public.adishop_qa_create_checkout(text,text,text,uuid,uuid,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_create_checkout(text,text,text,uuid,uuid,integer) to service_role;

-- ============================================================================
-- 7. RPC: Apply Coupon
-- ============================================================================
create or replace function public.adishop_qa_apply_coupon(
  p_session_jti text,
  p_checkout_session_id uuid,
  p_coupon_code text
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  cs record;
  cp record;
  clean_code text;
  order_stores uuid[];
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_coupon_code is null or length(trim(p_coupon_code)) < 3 then
    raise exception 'invalid-coupon-code' using errcode = '22023'; end if;

  clean_code := upper(trim(p_coupon_code));

  select * into cs from public.shop_checkout_sessions
  where id = p_checkout_session_id and profile_id = actor and state = 'reserved' for update;
  if not found then raise exception 'checkout-not-found' using errcode = '22023'; end if;
  if cs.expires_at <= now() then raise exception 'checkout-expired' using errcode = '22023'; end if;

  select * into cp from public.shop_coupons where code = clean_code for share;
  if not found or not cp.is_active then raise exception 'coupon-inactive' using errcode = '22023'; end if;
  if now() < cp.start_at then raise exception 'coupon-not-started' using errcode = '22023'; end if;
  if now() > cp.expires_at then raise exception 'coupon-expired' using errcode = '22023'; end if;
  if cp.usage_count >= cp.usage_limit then raise exception 'coupon-exhausted' using errcode = '22023'; end if;

  if (select count(*) from public.shop_coupon_usages where coupon_id = cp.id and profile_id = actor) >= cp.user_limit then
    raise exception 'coupon-user-limit-exceeded' using errcode = '22023'; end if;

  if cp.store_id is not null then
    select array_agg(distinct store_id) into order_stores
    from public.shop_checkout_items where checkout_session_id = p_checkout_session_id;
    if not (cp.store_id = any(order_stores)) then
      raise exception 'coupon-store-ineligible' using errcode = '22023'; end if;
  end if;

  if cs.subtotal_minor < cp.min_order_minor then
    raise exception 'coupon-min-order-unmet' using errcode = '22023'; end if;

  update public.shop_checkout_sessions
  set coupon_id = cp.id
  where id = p_checkout_session_id;

  return public.shop_recalculate_checkout(p_checkout_session_id);
end;
$$;
revoke all on function public.adishop_qa_apply_coupon(text,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_apply_coupon(text,uuid,text) to service_role;

-- ============================================================================
-- 8. RPC: Apply Voucher (Prepaid Value)
-- ============================================================================
create or replace function public.adishop_qa_apply_voucher(
  p_session_jti text,
  p_checkout_session_id uuid,
  p_voucher_code text
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  cs record;
  vc record;
  clean_code text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_voucher_code is null or length(trim(p_voucher_code)) < 6 then
    raise exception 'invalid-voucher-code' using errcode = '22023'; end if;

  clean_code := upper(trim(p_voucher_code));

  select * into cs from public.shop_checkout_sessions
  where id = p_checkout_session_id and profile_id = actor and state = 'reserved' for update;
  if not found then raise exception 'checkout-not-found' using errcode = '22023'; end if;
  if cs.expires_at <= now() then raise exception 'checkout-expired' using errcode = '22023'; end if;

  select * into vc from public.shop_vouchers where code = clean_code for share;
  if not found then raise exception 'voucher-not-found' using errcode = '22023'; end if;
  if vc.status <> 'active' or vc.remaining_balance_minor <= 0 then
    raise exception 'voucher-exhausted' using errcode = '22023'; end if;
  if now() > vc.expires_at then raise exception 'voucher-expired' using errcode = '22023'; end if;

  update public.shop_checkout_sessions
  set voucher_id = vc.id
  where id = p_checkout_session_id;

  return public.shop_recalculate_checkout(p_checkout_session_id);
end;
$$;
revoke all on function public.adishop_qa_apply_voucher(text,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_apply_voucher(text,uuid,text) to service_role;

-- ============================================================================
-- 9. RPC: Remove Discount (Coupon or Voucher)
-- ============================================================================
create or replace function public.adishop_qa_remove_discount(
  p_session_jti text,
  p_checkout_session_id uuid,
  p_discount_type text
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_discount_type not in ('coupon', 'voucher', 'all') then
    raise exception 'invalid-discount-type' using errcode = '22023'; end if;

  if p_discount_type in ('coupon', 'all') then
    update public.shop_checkout_sessions set coupon_id = null, coupon_discount_minor = 0
    where id = p_checkout_session_id and profile_id = actor;
  end if;

  if p_discount_type in ('voucher', 'all') then
    update public.shop_checkout_sessions set voucher_id = null, voucher_applied_minor = 0
    where id = p_checkout_session_id and profile_id = actor;
  end if;

  return public.shop_recalculate_checkout(p_checkout_session_id);
end;
$$;
revoke all on function public.adishop_qa_remove_discount(text,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_remove_discount(text,uuid,text) to service_role;

-- ============================================================================
-- 10. RPC: Confirm Checkout with Atomic Coupon & Voucher Deduction
-- ============================================================================
create or replace function public.adishop_qa_confirm_checkout(
  p_session_jti text,
  p_checkout_session_id uuid,
  p_address_id uuid,
  p_payment_method_code text,
  p_idempotency_key text
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  cs record;
  order_id_value uuid;
  order_number_value text;
  address_rec record;
  item_rec record;
  group_id_val uuid;
  voucher_rec record;
  coupon_rec record;
  final_payment_status text;
  payment_method_clean text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_checkout_session_id is null or p_address_id is null or p_idempotency_key is null then
    raise exception 'invalid-confirm-input' using errcode = '22023'; end if;

  select * into cs from public.shop_checkout_sessions
  where id = p_checkout_session_id and profile_id = actor for update;
  if not found then raise exception 'checkout-not-found' using errcode = '22023'; end if;

  -- Idempotency check: return order if already confirmed
  if cs.state = 'confirmed' and cs.order_id is not null then
    return jsonb_build_object(
      'orderId', cs.order_id,
      'status', 'created',
      'paymentStatus', (select payment_status from public.shop_orders where id = cs.order_id),
      'isDuplicate', true
    );
  end if;

  if cs.state <> 'reserved' or cs.expires_at <= now() then
    raise exception 'checkout-expired' using errcode = '22023'; end if;

  -- Verify address
  select * into address_rec from public.shop_addresses
  where id = p_address_id and profile_id = actor and deleted_at is null;
  if not found then raise exception 'address-not-found' using errcode = '22023'; end if;

  -- Recalculate totals before final confirmation
  perform public.shop_recalculate_checkout(cs.id);
  select * into cs from public.shop_checkout_sessions where id = p_checkout_session_id for update;

  -- 1. Deduct voucher balance atomically under lock if attached
  if cs.voucher_id is not null and cs.voucher_applied_minor > 0 then
    select * into voucher_rec from public.shop_vouchers where id = cs.voucher_id for update;
    if not found or voucher_rec.status <> 'active' or voucher_rec.remaining_balance_minor < cs.voucher_applied_minor then
      raise exception 'voucher-insufficient-balance' using errcode = '22023'; end if;

    update public.shop_vouchers
    set remaining_balance_minor = remaining_balance_minor - cs.voucher_applied_minor,
        status = case when (remaining_balance_minor - cs.voucher_applied_minor) = 0 then 'redeemed' else 'active' end,
        updated_at = now()
    where id = cs.voucher_id;

    insert into public.shop_voucher_transactions(
      voucher_id, profile_id, checkout_session_id, transaction_type,
      amount_minor, remaining_balance_after, idempotency_key
    ) values (
      cs.voucher_id, actor, cs.id, 'redemption',
      cs.voucher_applied_minor, voucher_rec.remaining_balance_minor - cs.voucher_applied_minor, p_idempotency_key
    );
  end if;

  -- 2. Record coupon usage atomically under lock if attached
  if cs.coupon_id is not null and cs.coupon_discount_minor > 0 then
    select * into coupon_rec from public.shop_coupons where id = cs.coupon_id for update;
    if not found or not coupon_rec.is_active or coupon_rec.usage_count >= coupon_rec.usage_limit then
      raise exception 'coupon-exhausted' using errcode = '22023'; end if;

    update public.shop_coupons
    set usage_count = usage_count + 1, updated_at = now()
    where id = cs.coupon_id;
  end if;

  -- 3. Determine verified payment status
  if cs.remaining_payable_minor = 0 and cs.voucher_applied_minor > 0 then
    final_payment_status := 'paid';
    payment_method_clean := 'voucher_prepaid';
  else
    final_payment_status := 'payment_pending';
    payment_method_clean := coalesce(p_payment_method_code, 'cash_on_delivery');
  end if;

  order_number_value := 'AG-' || to_char(now(), 'YYYYMMDD') || '-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));

  -- 4. Create Order
  insert into public.shop_orders(
    profile_id, address_id, checkout_session_id, order_number, status, payment_status,
    fulfillment_status, delivery_status, currency, subtotal_minor, delivery_fee_minor,
    discount_minor, total_minor
  ) values (
    actor, p_address_id, cs.id, order_number_value, 'created', final_payment_status,
    'created', 'not_shipped', cs.currency, cs.subtotal_minor, cs.shipping_total_minor,
    cs.coupon_discount_minor + cs.voucher_applied_minor, cs.grand_total_minor
  ) returning id into order_id_value;

  -- Link order to coupon usage and voucher transaction
  if cs.coupon_id is not null and cs.coupon_discount_minor > 0 then
    insert into public.shop_coupon_usages(
      coupon_id, profile_id, order_id, checkout_session_id, discount_minor
    ) values (
      cs.coupon_id, actor, order_id_value, cs.id, cs.coupon_discount_minor
    );
  end if;

  if cs.voucher_id is not null and cs.voucher_applied_minor > 0 then
    update public.shop_voucher_transactions
    set order_id = order_id_value
    where checkout_session_id = cs.id and voucher_id = cs.voucher_id;
  end if;

  -- 5. Create Order Groups and Items
  for item_rec in select * from public.shop_checkout_items where checkout_session_id = cs.id loop
    insert into public.shop_order_groups(order_id, store_id, status, subtotal_minor, store_name_snapshot, payment_status, fulfillment_status)
    values (order_id_value, item_rec.store_id, 'created', 0, item_rec.store_snapshot->>'name', final_payment_status, 'created')
    on conflict (order_id, store_id) do update set subtotal_minor = public.shop_order_groups.subtotal_minor
    returning id into group_id_val;

    insert into public.shop_order_items(
      order_group_id, product_id, variant_id, title_snapshot, quantity, unit_price_minor,
      line_total_minor, currency, variant_snapshot, image_snapshot, store_snapshot
    ) values (
      group_id_val, item_rec.product_id, item_rec.variant_id, item_rec.product_title_snapshot,
      item_rec.quantity, item_rec.unit_price_minor, item_rec.line_total_minor, item_rec.currency,
      item_rec.variant_snapshot, item_rec.image_snapshot, item_rec.store_snapshot
    );

    update public.shop_order_groups
    set subtotal_minor = subtotal_minor + item_rec.line_total_minor
    where id = group_id_val;
  end loop;

  -- 6. Commit Inventory Reservations
  update public.shop_inventory_reservations
  set status = 'committed', committed_at = now()
  where checkout_session_id = cs.id and status = 'active';

  update public.shop_inventory i
  set quantity_available = greatest(0, i.quantity_available - r.quantity),
      quantity_reserved = greatest(0, i.quantity_reserved - r.quantity),
      updated_at = now()
  from public.shop_inventory_reservations r
  where r.checkout_session_id = cs.id and r.inventory_id = i.id and r.status = 'committed';

  -- 7. Update Session State
  update public.shop_checkout_sessions
  set state = 'confirmed', confirmed_at = now(), order_id = order_id_value
  where id = cs.id;

  insert into public.shop_order_status_history(order_id, status, actor_profile_id, public_message, internal_message)
  values (order_id_value, 'created', actor, 'Order created (' || final_payment_status || ')', 'Order confirmed from checkout session (payment: ' || final_payment_status || ')');

  return jsonb_build_object(
    'orderId', order_id_value,
    'orderNumber', order_number_value,
    'status', 'created',
    'paymentStatus', final_payment_status,
    'subtotalMinor', cs.subtotal_minor,
    'couponDiscountMinor', cs.coupon_discount_minor,
    'voucherAppliedMinor', cs.voucher_applied_minor,
    'grandTotalMinor', cs.grand_total_minor,
    'isFullyCoveredByVoucher', (cs.remaining_payable_minor = 0 and cs.voucher_applied_minor > 0)
  );
end;
$$;
revoke all on function public.adishop_qa_confirm_checkout(text,uuid,uuid,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_confirm_checkout(text,uuid,uuid,text,text) to service_role;

-- ============================================================================
-- 11. RPC: Cancel Order with Automatic Voucher & Inventory Reversal
-- ============================================================================
create or replace function public.adishop_qa_cancel_order(
  p_session_jti text,
  p_order_id uuid,
  p_reason text default 'Customer cancellation'
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  order_rec record;
  vt record;
  v_rec record;
  cu record;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;

  select * into order_rec from public.shop_orders
  where id = p_order_id and profile_id = actor for update;
  if not found then raise exception 'order-not-found' using errcode = '22023'; end if;

  if order_rec.status in ('cancelled', 'completed', 'delivered') then
    raise exception 'order-not-cancellable' using errcode = '22023'; end if;

  -- 1. Restore voucher balance if order had voucher redemption
  for vt in
    select * from public.shop_voucher_transactions
    where order_id = p_order_id and transaction_type = 'redemption'
  loop
    select * into v_rec from public.shop_vouchers where id = vt.voucher_id for update;
    update public.shop_vouchers
    set remaining_balance_minor = remaining_balance_minor + vt.amount_minor,
        status = 'active',
        updated_at = now()
    where id = vt.voucher_id;

    insert into public.shop_voucher_transactions(
      voucher_id, profile_id, order_id, checkout_session_id,
      transaction_type, amount_minor, remaining_balance_after, idempotency_key
    ) values (
      vt.voucher_id, actor, p_order_id, vt.checkout_session_id,
      'refund_reversal', vt.amount_minor, v_rec.remaining_balance_minor + vt.amount_minor,
      'reversal-' || gen_random_uuid()::text
    );
  end loop;

  -- 2. Restore coupon usage if coupon was applied
  for cu in select * from public.shop_coupon_usages where order_id = p_order_id loop
    update public.shop_coupons
    set usage_count = greatest(0, usage_count - 1), updated_at = now()
    where id = cu.coupon_id;
  end loop;

  -- 3. Release inventory back to available stock
  update public.shop_inventory i
  set quantity_available = i.quantity_available + r.quantity, updated_at = now()
  from public.shop_inventory_reservations r
  where r.checkout_session_id = order_rec.checkout_session_id
    and r.inventory_id = i.id
    and r.status = 'committed';

  update public.shop_inventory_reservations
  set status = 'released', released_at = now()
  where checkout_session_id = order_rec.checkout_session_id;

  -- 4. Update order status
  update public.shop_orders
  set status = 'cancelled', payment_status = case when payment_status = 'paid' then 'refunded' else 'cancelled' end,
      fulfillment_status = 'cancelled', updated_at = now()
  where id = p_order_id;

  update public.shop_order_groups
  set status = 'cancelled', fulfillment_status = 'cancelled', updated_at = now()
  where order_id = p_order_id;

  insert into public.shop_order_status_history(order_id, status, actor_profile_id, public_message, internal_message)
  values (p_order_id, 'cancelled', actor, coalesce(p_reason, 'Order cancelled by customer'), 'Cancelled: ' || coalesce(p_reason, 'Order cancelled by customer'));

  return jsonb_build_object('orderId', p_order_id, 'status', 'cancelled');
end;
$$;
revoke all on function public.adishop_qa_cancel_order(text,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_cancel_order(text,uuid,text) to service_role;

-- ============================================================================
-- 12. Admin RPCs: Create Voucher and Create Coupon
-- ============================================================================
create or replace function public.adishop_qa_create_voucher(
  p_session_jti text,
  p_value_minor integer,
  p_expires_in_days integer default 90,
  p_code_prefix text default 'VCH'
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  random_code text;
  voucher_id uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN', 'SHOP_SUPER_ADMIN') for share of ur;
  if not found then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_value_minor is null or p_value_minor <= 0 then
    raise exception 'invalid-voucher-value' using errcode = '22023'; end if;

  random_code := upper(coalesce(p_code_prefix, 'VCH')) || '-' ||
                 upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 4)) || '-' ||
                 upper(substr(replace(gen_random_uuid()::text, '-', ''), 5, 4)) || '-' ||
                 upper(substr(replace(gen_random_uuid()::text, '-', ''), 9, 4));

  insert into public.shop_vouchers(
    code, initial_value_minor, remaining_balance_minor, currency, status,
    expires_at, created_by_profile_id
  ) values (
    random_code, p_value_minor, p_value_minor, 'ETB', 'active',
    now() + (coalesce(p_expires_in_days, 90) || ' days')::interval, actor
  ) returning id into voucher_id;

  return jsonb_build_object(
    'voucherId', voucher_id,
    'code', random_code,
    'initialValueMinor', p_value_minor,
    'remainingBalanceMinor', p_value_minor,
    'currency', 'ETB',
    'status', 'active'
  );
end;
$$;
revoke all on function public.adishop_qa_create_voucher(text,integer,integer,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_create_voucher(text,integer,integer,text) to service_role;

create or replace function public.adishop_qa_create_coupon(
  p_session_jti text,
  p_code text,
  p_discount_type text,
  p_discount_value integer,
  p_min_order_minor integer default 0,
  p_max_discount_minor integer default null,
  p_usage_limit integer default 100,
  p_store_id uuid default null,
  p_expires_in_days integer default 30
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  clean_code text;
  coupon_id uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  perform 1 from public.shop_user_roles ur join public.shop_roles r on r.id = ur.role_id
    where ur.profile_id = actor and ur.revoked_at is null
      and r.code in ('SHOP_ADMIN', 'SHOP_SUPER_ADMIN', 'SELLER') for share of ur;
  if not found then raise exception 'unauthorized' using errcode = '42501'; end if;
  if p_code is null or length(trim(p_code)) < 3 then
    raise exception 'invalid-coupon-code' using errcode = '22023'; end if;
  if p_discount_type not in ('fixed', 'percent') or p_discount_value is null or p_discount_value <= 0 then
    raise exception 'invalid-discount' using errcode = '22023'; end if;

  clean_code := upper(trim(p_code));

  insert into public.shop_coupons(
    code, discount_type, discount_value, min_order_minor, max_discount_minor,
    usage_limit, store_id, start_at, expires_at, is_active
  ) values (
    clean_code, p_discount_type, p_discount_value, coalesce(p_min_order_minor, 0),
    p_max_discount_minor, coalesce(p_usage_limit, 100), p_store_id, now(),
    now() + (coalesce(p_expires_in_days, 30) || ' days')::interval, true
  ) returning id into coupon_id;

  return jsonb_build_object(
    'couponId', coupon_id,
    'code', clean_code,
    'discountType', p_discount_type,
    'discountValue', p_discount_value
  );
end;
$$;
revoke all on function public.adishop_qa_create_coupon(text,text,text,integer,integer,integer,integer,uuid,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_create_coupon(text,text,text,integer,integer,integer,integer,uuid,integer) to service_role;
