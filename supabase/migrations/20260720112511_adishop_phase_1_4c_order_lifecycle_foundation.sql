-- AdisChat Shop Phase 1.4C order lifecycle foundation.
-- Adds an explicit Cash on Delivery path, server-enforced fulfilment transitions,
-- customer notifications, cancellation foundation, and delivery-code foundation.

create extension if not exists pgcrypto;

create table if not exists public.shop_platform_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  is_public boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_payment_method_regions (
  id uuid primary key default gen_random_uuid(),
  method_code text not null references public.shop_payment_methods(code) on delete cascade,
  country_code char(2) not null default 'ET',
  region text,
  enabled boolean not null default true,
  min_amount_minor integer not null default 0 check (min_amount_minor >= 0),
  max_amount_minor integer check (max_amount_minor is null or max_amount_minor >= min_amount_minor),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (method_code, country_code, region)
);

insert into public.shop_platform_settings(key, value, is_public)
values (
  'cash_on_delivery',
  jsonb_build_object(
    'enabled', true,
    'country', 'ET',
    'currency', 'ETB',
    'label', 'Cash on Delivery',
    'customerMessage', 'Order placed - payment due on delivery',
    'requiresOnlineConfirmation', false
  ),
  true
)
on conflict (key) do update set
  value = excluded.value,
  is_public = excluded.is_public,
  updated_at = now();

alter table public.shop_payment_methods add column if not exists currency char(3) not null default 'ETB';

insert into public.shop_payment_methods (
  code, label, provider, enabled, requires_online_provider, currency, metadata, updated_at
)
values (
  'cash_on_delivery',
  'Cash on Delivery',
  'manual_cod',
  true,
  false,
  'ETB',
  jsonb_build_object('publicMessage', 'Payment due on delivery', 'configuredBy', 'phase_1_4c'),
  now()
)
on conflict (code) do update set
  label = excluded.label,
  provider = excluded.provider,
  enabled = true,
  requires_online_provider = false,
  currency = excluded.currency,
  metadata = excluded.metadata,
  updated_at = now();

insert into public.shop_payment_method_regions(method_code, country_code, region, enabled, min_amount_minor, max_amount_minor)
values ('cash_on_delivery', 'ET', null, true, 0, null)
on conflict (method_code, country_code, region) do update set
  enabled = true,
  min_amount_minor = excluded.min_amount_minor,
  max_amount_minor = excluded.max_amount_minor,
  updated_at = now();

alter table public.shop_stores add column if not exists accepts_cash_on_delivery boolean not null default true;
alter table public.shop_products add column if not exists cash_on_delivery_enabled boolean not null default true;
alter table public.shop_products add column if not exists product_type text not null default 'physical';
alter table public.shop_orders add column if not exists confirmation_idempotency_key text;
alter table public.shop_orders add column if not exists cancellation_requested_at timestamptz;
alter table public.shop_orders add column if not exists completed_at timestamptz;
alter table public.shop_order_groups add column if not exists inventory_released_at timestamptz;
alter table public.shop_order_groups add column if not exists rejected_reason text;
alter table public.shop_order_groups add column if not exists shipment_carrier text;
alter table public.shop_order_groups add column if not exists shipment_tracking_number text;
alter table public.shop_order_groups add column if not exists shipped_at timestamptz;
alter table public.shop_order_groups add column if not exists delivered_at timestamptz;
alter table public.shop_order_status_history add column if not exists actor_role text;
alter table public.shop_order_status_history add column if not exists previous_status text;
alter table public.shop_order_status_history add column if not exists next_status text;
alter table public.shop_order_status_history add column if not exists event_code text;
alter table public.shop_order_status_history add column if not exists public_message text;
alter table public.shop_order_status_history add column if not exists internal_message text;
alter table public.shop_order_status_history add column if not exists reason text;
alter table public.shop_order_status_history add column if not exists metadata jsonb not null default '{}'::jsonb;
alter table public.shop_order_status_history add column if not exists request_id text;
alter table public.shop_order_status_history add column if not exists occurred_at timestamptz not null default now();

do $$
declare
  constraint_record record;
begin
  for constraint_record in
    select conname, conrelid::regclass::text as table_name
    from pg_constraint
    where contype = 'c'
      and conrelid in ('public.shop_orders'::regclass, 'public.shop_order_groups'::regclass, 'public.shop_payments'::regclass)
      and pg_get_constraintdef(oid) ilike any (array['%status%', '%payment_status%', '%fulfillment_status%'])
  loop
    execute format('alter table %s drop constraint if exists %I', constraint_record.table_name, constraint_record.conname);
  end loop;
end $$;

alter table public.shop_orders
  add constraint shop_orders_status_1_4c_check
  check (status in ('pending','confirmed','processing','partially_shipped','shipped','delivered','completed','cancelled','disputed','created','preparing','ready','rejected'));

alter table public.shop_orders
  add constraint shop_orders_payment_status_1_4c_check
  check (payment_status in ('unpaid','pending','authorized','paid','failed','expired','partially_refunded','refunded','payment_pending','authorization_pending'));

alter table public.shop_orders
  add constraint shop_orders_fulfillment_status_1_4c_check
  check (fulfillment_status in ('awaiting_seller_confirmation','accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','rejected','cancelled','created'));

alter table public.shop_order_groups
  add constraint shop_order_groups_status_1_4c_check
  check (status in ('created','accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','cancelled','rejected','preparing','ready'));

alter table public.shop_order_groups
  add constraint shop_order_groups_payment_status_1_4c_check
  check (payment_status in ('unpaid','pending','authorized','paid','failed','expired','partially_refunded','refunded','payment_pending','authorization_pending'));

alter table public.shop_order_groups
  add constraint shop_order_groups_fulfillment_status_1_4c_check
  check (fulfillment_status in ('awaiting_seller_confirmation','accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','rejected','cancelled','created'));

alter table public.shop_payments
  add constraint shop_payments_status_1_4c_check
  check (status in ('unpaid','pending','authorized','paid','failed','expired','partially_refunded','refunded','payment_pending','authorization_pending'));

create unique index if not exists shop_orders_profile_confirmation_key_idx
  on public.shop_orders(profile_id, confirmation_idempotency_key)
  where confirmation_idempotency_key is not null;

create table if not exists public.shop_order_cancellations (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete cascade,
  requested_by uuid not null references public.shop_profiles(id) on delete restrict,
  reason_code text not null default 'customer_request',
  reason_text text,
  status text not null default 'requested' check (status in ('requested','auto_approved','seller_review','admin_review','approved','rejected','cancelled')),
  seller_response text,
  admin_response text,
  requested_at timestamptz not null default now(),
  resolved_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists public.shop_delivery_confirmation_codes (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.shop_orders(id) on delete cascade,
  code_hash text,
  code_salt text,
  revealed_at timestamptz,
  verified_at timestamptz,
  failed_attempts integer not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_delivery_code_attempts (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete cascade,
  seller_profile_id uuid references public.shop_profiles(id) on delete set null,
  success boolean not null default false,
  created_at timestamptz not null default now()
);

do $$
declare
  t text;
begin
  foreach t in array array[
    'shop_platform_settings','shop_payment_method_regions','shop_order_cancellations',
    'shop_delivery_confirmation_codes','shop_delivery_code_attempts'
  ] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'shop_payment_method_regions','shop_order_cancellations',
    'shop_delivery_confirmation_codes','shop_delivery_code_attempts'
  ] loop
    if not exists (
      select 1 from pg_policies
      where schemaname = 'public' and tablename = t and policyname = 'client_access_deferred'
    ) then
      execute format('create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)', t);
    end if;
  end loop;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'shop_platform_settings' and policyname = 'public_read_public_platform_settings'
  ) then
    create policy public_read_public_platform_settings
    on public.shop_platform_settings for select
    to anon, authenticated
    using (is_public = true);
  end if;
end $$;

create index if not exists shop_order_cancellations_order_idx on public.shop_order_cancellations(order_id, status, requested_at desc);
create index if not exists shop_notifications_profile_unread_idx on public.shop_notifications(profile_id, read_at, created_at desc);
create index if not exists shop_order_groups_order_fulfillment_idx on public.shop_order_groups(order_id, fulfillment_status);
create index if not exists shop_order_status_history_order_time_idx on public.shop_order_status_history(order_id, occurred_at desc);

create or replace view public.shop_order_seller_groups with (security_invoker = true) as
select * from public.shop_order_groups;

create or replace function public.adishop_customer_can_read_order(p_profile_id uuid, p_order_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1 from public.shop_orders o
    where o.id = p_order_id and o.profile_id = p_profile_id
  );
$$;

create or replace function public.adishop_profile_can_fulfill_store(p_profile_id uuid, p_store_id uuid)
returns boolean
language sql
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.shop_stores s
    where s.id = p_store_id
      and s.status = 'active'
      and s.approval_status = 'approved'
      and s.owner_profile_id = p_profile_id
  )
  or exists (
    select 1
    from public.shop_store_members m
    join public.shop_stores s on s.id = m.store_id
    where m.store_id = p_store_id
      and m.profile_id = p_profile_id
      and m.status = 'active'
      and m.role in ('owner','admin','manager','staff')
      and s.status = 'active'
      and s.approval_status = 'approved'
  );
$$;

create or replace function public.adishop_create_notification(
  p_profile_id uuid,
  p_type text,
  p_title text,
  p_body text,
  p_data jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  notification_id uuid;
begin
  insert into public.shop_notifications(profile_id, type, title, body, data)
  values (p_profile_id, p_type, p_title, p_body, coalesce(p_data, '{}'::jsonb))
  returning id into notification_id;
  return notification_id;
end;
$$;

create or replace function public.adishop_order_public_payload(p_order_id uuid)
returns jsonb
language sql
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', o.id,
    'order_number', o.order_number,
    'status', o.status,
    'payment_status', o.payment_status,
    'fulfillment_status', o.fulfillment_status,
    'delivery_status', o.delivery_status,
    'currency', o.currency,
    'total_minor', o.total_minor,
    'created_at', o.created_at
  )
  from public.shop_orders o
  where o.id = p_order_id;
$$;

create or replace function public.adishop_confirm_checkout(
  p_profile_id uuid,
  p_checkout_session_id uuid,
  p_address_id uuid,
  p_payment_method_code text,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  checkout_record public.shop_checkout_sessions%rowtype;
  address_record public.shop_addresses%rowtype;
  method_record public.shop_payment_methods%rowtype;
  order_id_value uuid;
  order_number_value text;
  group_id_value uuid;
  item_record record;
  reservation_count integer;
begin
  if p_profile_id is null or p_checkout_session_id is null or length(coalesce(p_idempotency_key, '')) < 8 then
    raise exception 'invalid-checkout-request';
  end if;

  select * into checkout_record
  from public.shop_checkout_sessions
  where id = p_checkout_session_id and profile_id = p_profile_id
  for update;
  if not found then raise exception 'checkout-not-found'; end if;

  if checkout_record.state = 'confirmed' and checkout_record.order_id is not null then
    return public.adishop_order_public_payload(checkout_record.order_id);
  end if;

  select id into order_id_value
  from public.shop_orders
  where profile_id = p_profile_id and confirmation_idempotency_key = p_idempotency_key
  limit 1;
  if order_id_value is not null then
    return public.adishop_order_public_payload(order_id_value);
  end if;

  if checkout_record.state <> 'reserved' or checkout_record.expires_at <= now() then
    raise exception 'checkout-expired';
  end if;

  select * into method_record
  from public.shop_payment_methods
  where code = p_payment_method_code and enabled = true;
  if not found then raise exception 'payment-method-unavailable'; end if;
  if method_record.code <> 'cash_on_delivery' then
    raise exception 'payment-method-unavailable';
  end if;

  select * into address_record
  from public.shop_addresses
  where id = p_address_id and profile_id = p_profile_id and deleted_at is null;
  if not found then raise exception 'address-not-found'; end if;
  if upper(coalesce(address_record.city, '')) = '' then raise exception 'address-not-eligible'; end if;

  select count(*) into reservation_count
  from public.shop_inventory_reservations
  where checkout_session_id = checkout_record.id
    and status = 'active'
    and expires_at > now();
  if reservation_count = 0 then raise exception 'checkout-expired'; end if;

  if exists (
    select 1
    from public.shop_checkout_items ci
    join public.shop_products p on p.id = ci.product_id
    join public.shop_stores s on s.id = ci.store_id
    where ci.checkout_session_id = checkout_record.id
      and (
        p.product_type <> 'physical'
        or p.cash_on_delivery_enabled is not true
        or s.accepts_cash_on_delivery is not true
        or s.status <> 'active'
        or s.approval_status <> 'approved'
      )
  ) then
    raise exception 'cod-not-eligible';
  end if;

  order_number_value := 'ADS-' || to_char(now(), 'YYYYMMDD') || '-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));

  insert into public.shop_orders(
    profile_id, address_id, checkout_session_id, confirmation_idempotency_key,
    order_number, status, payment_status, fulfillment_status, delivery_status,
    currency, subtotal_minor, delivery_fee_minor, discount_minor, total_minor
  )
  values (
    p_profile_id, p_address_id, checkout_record.id, p_idempotency_key,
    order_number_value, 'confirmed', 'unpaid', 'awaiting_seller_confirmation', 'not_shipped',
    checkout_record.currency, checkout_record.subtotal_minor, checkout_record.shipping_total_minor,
    checkout_record.discount_total_minor, checkout_record.grand_total_minor
  )
  returning id into order_id_value;

  insert into public.shop_order_addresses(
    order_id, recipient_name, phone, city, address_line1, address_line2, latitude, longitude
  )
  values (
    order_id_value, address_record.recipient_name, address_record.phone, address_record.city,
    address_record.address_line1, address_record.address_line2, address_record.latitude, address_record.longitude
  );

  for item_record in
    select * from public.shop_checkout_items where checkout_session_id = checkout_record.id order by created_at
  loop
    insert into public.shop_order_groups(order_id, store_id, status, subtotal_minor, store_name_snapshot, payment_status, fulfillment_status)
    values (order_id_value, item_record.store_id, 'created', 0, item_record.store_snapshot->>'name', 'unpaid', 'awaiting_seller_confirmation')
    on conflict (order_id, store_id) do update set subtotal_minor = public.shop_order_groups.subtotal_minor
    returning id into group_id_value;

    insert into public.shop_order_items(
      order_group_id, product_id, variant_id, title_snapshot, quantity,
      unit_price_minor, line_total_minor, currency, variant_snapshot, image_snapshot, store_snapshot
    )
    values (
      group_id_value, item_record.product_id, item_record.variant_id, item_record.title_snapshot,
      item_record.quantity, item_record.unit_price_minor, item_record.line_total_minor,
      item_record.currency, item_record.variant_snapshot, item_record.image_snapshot, item_record.store_snapshot
    );

    update public.shop_order_groups
    set subtotal_minor = subtotal_minor + item_record.line_total_minor
    where id = group_id_value;
  end loop;

  insert into public.shop_payments(
    checkout_session_id, order_id, profile_id, provider, method_code, status, amount_minor, currency, metadata
  )
  values (
    checkout_record.id, order_id_value, p_profile_id, method_record.provider, method_record.code,
    'unpaid', checkout_record.grand_total_minor, checkout_record.currency,
    jsonb_build_object('message', 'Payment due on delivery', 'onlineProvider', false)
  );

  update public.shop_inventory_reservations
  set status = 'committed'
  where checkout_session_id = checkout_record.id and status = 'active';

  update public.shop_inventory i
  set quantity_available = greatest(0, i.quantity_available - r.quantity),
      quantity_reserved = greatest(0, i.quantity_reserved - r.quantity),
      updated_at = now()
  from public.shop_inventory_reservations r
  where r.checkout_session_id = checkout_record.id
    and r.status = 'committed'
    and r.inventory_id = i.id;

  insert into public.shop_order_status_history(
    order_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, request_id, metadata, occurred_at
  )
  values (
    order_id_value, 'confirmed', p_profile_id, 'CUSTOMER', null, 'confirmed',
    'order_placed', 'Order placed - payment due on delivery',
    'Cash on Delivery order created from checkout session', p_idempotency_key,
    jsonb_build_object('checkoutSessionId', checkout_record.id, 'paymentMethod', method_record.code), now()
  );

  update public.shop_checkout_sessions
  set state = 'confirmed', order_id = order_id_value, updated_at = now()
  where id = checkout_record.id;

  update public.shop_carts set status = 'submitted', updated_at = now() where id = checkout_record.cart_id;

  perform public.adishop_create_notification(
    p_profile_id,
    'order_placed',
    'Order placed',
    'Payment is due on delivery.',
    jsonb_build_object('orderId', order_id_value, 'orderNumber', order_number_value)
  );

  insert into public.shop_delivery_confirmation_codes(order_id)
  values (order_id_value)
  on conflict (order_id) do nothing;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'checkout.confirm.cod', 'shop_order', order_id_value, jsonb_build_object('checkoutSessionId', checkout_record.id));

  return public.adishop_order_public_payload(order_id_value);
end;
$$;

create or replace function public.adishop_transition_seller_order_group(
  p_profile_id uuid,
  p_order_group_id uuid,
  p_action text,
  p_reason text default null,
  p_metadata jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  previous_status text;
  next_status text;
  event_code text;
  public_message text;
begin
  select * into group_record from public.shop_order_groups where id = p_order_group_id for update;
  if not found then raise exception 'seller-order-not-found'; end if;
  if not public.adishop_profile_can_fulfill_store(p_profile_id, group_record.store_id) then
    raise exception 'seller-order-not-found';
  end if;

  select * into order_record from public.shop_orders where id = group_record.order_id for update;
  previous_status := group_record.fulfillment_status;

  if p_action = 'accept' and previous_status in ('awaiting_seller_confirmation','created') then
    next_status := 'accepted'; event_code := 'seller_accepted'; public_message := 'Seller accepted your order.';
  elsif p_action = 'reject' and previous_status in ('awaiting_seller_confirmation','accepted','created') then
    next_status := 'rejected'; event_code := 'seller_rejected'; public_message := 'Seller rejected this part of the order.';
  elsif p_action = 'processing' and previous_status = 'accepted' then
    next_status := 'processing'; event_code := 'seller_processing'; public_message := 'Seller started preparing your order.';
  elsif p_action = 'packed' and previous_status = 'processing' then
    next_status := 'packed'; event_code := 'seller_packed'; public_message := 'Your order is packed.';
  elsif p_action = 'ready_for_pickup' and previous_status in ('packed','processing') then
    next_status := 'ready_for_pickup'; event_code := 'ready_for_pickup'; public_message := 'Your order is ready for pickup.';
  elsif p_action = 'handed_to_carrier' and previous_status in ('packed','ready_for_pickup') then
    next_status := 'handed_to_carrier'; event_code := 'handed_to_carrier'; public_message := 'Your order was handed to the carrier.';
  else
    raise exception 'invalid-transition';
  end if;

  update public.shop_order_groups
  set fulfillment_status = next_status,
      status = case
        when next_status = 'accepted' then 'accepted'
        when next_status = 'processing' then 'processing'
        when next_status = 'packed' then 'packed'
        when next_status = 'ready_for_pickup' then 'ready_for_pickup'
        when next_status = 'handed_to_carrier' then 'handed_to_carrier'
        when next_status = 'rejected' then 'rejected'
        else status
      end,
      rejected_reason = case when next_status = 'rejected' then p_reason else rejected_reason end,
      shipment_carrier = coalesce(p_metadata->>'carrier', shipment_carrier),
      shipment_tracking_number = coalesce(p_metadata->>'trackingNumber', shipment_tracking_number),
      shipped_at = case when next_status = 'handed_to_carrier' then now() else shipped_at end,
      updated_at = now()
  where id = p_order_group_id;

  if next_status = 'rejected' and group_record.inventory_released_at is null then
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity,
        updated_at = now()
    from public.shop_order_items oi
    where oi.order_group_id = group_record.id
      and i.product_id = oi.product_id
      and i.variant_id is not distinct from oi.variant_id;

    update public.shop_order_groups set inventory_released_at = now() where id = group_record.id;
  end if;

  update public.shop_orders
  set fulfillment_status = case
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'processing') then 'processing'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'packed') then 'packed'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'handed_to_carrier') then 'handed_to_carrier'
      when not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status not in ('rejected','cancelled')) then 'rejected'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'accepted') then 'accepted'
      else fulfillment_status
    end,
    status = case
      when next_status = 'rejected' and not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.id <> group_record.id and g.fulfillment_status not in ('rejected','cancelled')) then 'cancelled'
      when next_status in ('processing','packed') then 'processing'
      when next_status = 'handed_to_carrier' then 'shipped'
      else status
    end,
    updated_at = now()
  where id = order_record.id;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, reason, metadata, occurred_at
  )
  values (
    order_record.id, group_record.id, next_status, p_profile_id, 'SELLER',
    previous_status, next_status, event_code, public_message, p_reason, p_reason,
    coalesce(p_metadata, '{}'::jsonb), now()
  );

  perform public.adishop_create_notification(
    order_record.profile_id,
    event_code,
    'Order update',
    public_message,
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id)
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'seller_order_group.' || p_action, 'shop_order_group', group_record.id, coalesce(p_metadata, '{}'::jsonb));

  return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', next_status);
end;
$$;

create or replace function public.adishop_reveal_delivery_code(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  code_record public.shop_delivery_confirmation_codes%rowtype;
  code_plain text;
  salt_value text;
begin
  if not public.adishop_customer_can_read_order(p_profile_id, p_order_id) then
    raise exception 'order-not-found';
  end if;

  select * into code_record
  from public.shop_delivery_confirmation_codes
  where order_id = p_order_id
  for update;

  if not found then
    insert into public.shop_delivery_confirmation_codes(order_id)
    values (p_order_id)
    returning * into code_record;
  end if;

  if code_record.verified_at is not null then
    raise exception 'delivery-code-already-used';
  end if;
  if code_record.revealed_at is not null then
    raise exception 'delivery-code-already-revealed';
  end if;

  code_plain := lpad((floor(random() * 1000000))::int::text, 6, '0');
  salt_value := encode(gen_random_bytes(16), 'hex');

  update public.shop_delivery_confirmation_codes
  set code_salt = salt_value,
      code_hash = encode(digest(code_plain || ':' || salt_value, 'sha256'), 'hex'),
      revealed_at = now()
  where id = code_record.id;

  return jsonb_build_object('orderId', p_order_id, 'deliveryCode', code_plain, 'message', 'Show this code to the delivery person only when you receive the order.');
end;
$$;

create or replace function public.adishop_request_order_cancellation(
  p_profile_id uuid,
  p_order_id uuid,
  p_reason_code text,
  p_reason_text text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  order_record public.shop_orders%rowtype;
begin
  select * into order_record
  from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id
  for update;
  if not found then raise exception 'order-not-found'; end if;

  if order_record.fulfillment_status not in ('awaiting_seller_confirmation','accepted') then
    raise exception 'cancellation-not-eligible';
  end if;

  insert into public.shop_order_cancellations(order_id, requested_by, reason_code, reason_text, status)
  values (p_order_id, p_profile_id, coalesce(nullif(p_reason_code, ''), 'customer_request'), nullif(p_reason_text, ''), 'requested');

  update public.shop_orders set cancellation_requested_at = now(), updated_at = now() where id = p_order_id;

  insert into public.shop_order_status_history(
    order_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, occurred_at
  )
  values (
    p_order_id, order_record.fulfillment_status, p_profile_id, 'CUSTOMER',
    order_record.fulfillment_status, order_record.fulfillment_status,
    'cancellation_requested', 'Cancellation requested.', 'Customer requested cancellation.', now()
  );

  return public.adishop_order_public_payload(p_order_id);
end;
$$;

revoke all on function public.adishop_create_notification(uuid, text, text, text, jsonb) from public;
revoke all on function public.adishop_customer_can_read_order(uuid, uuid) from public;
revoke all on function public.adishop_profile_can_fulfill_store(uuid, uuid) from public;
revoke all on function public.adishop_order_public_payload(uuid) from public;
revoke all on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from public;
revoke all on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from public;
revoke execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from anon;
revoke execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from authenticated;
revoke all on function public.adishop_reveal_delivery_code(uuid, uuid) from public;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from anon;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from authenticated;
revoke all on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from public;
revoke execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from anon;
revoke execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from authenticated;
grant execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) to service_role;
grant execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) to service_role;
grant execute on function public.adishop_reveal_delivery_code(uuid, uuid) to service_role;
grant execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) to service_role;
grant execute on function public.adishop_create_notification(uuid, text, text, text, jsonb) to service_role;
