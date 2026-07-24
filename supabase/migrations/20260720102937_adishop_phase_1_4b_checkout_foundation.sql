-- AdisChat Shop Phase 1.4B checkout foundation.
-- Extends the existing Phase 1.2/1.3 marketplace schema. No fake payment provider is enabled.

create table if not exists public.shop_roles (
  id uuid primary key default gen_random_uuid(),
  code text not null unique check (code in ('CUSTOMER','SELLER','SHOP_ADMIN','SHOP_SUPER_ADMIN','SUPPORT_AGENT')),
  name text not null,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists public.shop_user_roles (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  role_id uuid not null references public.shop_roles(id) on delete cascade,
  assigned_by_profile_id uuid references public.shop_profiles(id) on delete set null,
  reason text,
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique (profile_id, role_id)
);

create table if not exists public.shop_role_permissions (
  id uuid primary key default gen_random_uuid(),
  role_id uuid not null references public.shop_roles(id) on delete cascade,
  permission text not null,
  created_at timestamptz not null default now(),
  unique (role_id, permission)
);

insert into public.shop_roles (code, name, description)
values
  ('CUSTOMER', 'Customer', 'Default marketplace customer capability'),
  ('SELLER', 'Seller', 'Approved seller capability'),
  ('SHOP_ADMIN', 'Shop Admin', 'Marketplace review and moderation capability'),
  ('SHOP_SUPER_ADMIN', 'Shop Super Admin', 'Sensitive marketplace administration capability'),
  ('SUPPORT_AGENT', 'Support Agent', 'Limited order and dispute support capability')
on conflict (code) do update set name = excluded.name, description = excluded.description;

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

insert into public.shop_payment_methods (code, label, provider, enabled, requires_online_provider, metadata)
values ('cash_on_delivery', 'Cash on Delivery', 'manual', false, false, '{"reason":"Disabled until platform eligibility rules are configured"}'::jsonb)
on conflict (code) do nothing;

create table if not exists public.shop_checkout_sessions (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  cart_id uuid references public.shop_carts(id) on delete set null,
  idempotency_key text not null,
  state text not null default 'reserved' check (state in ('reserved','confirmed','expired','cancelled')),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  subtotal_minor integer not null default 0 check (subtotal_minor >= 0),
  discount_total_minor integer not null default 0 check (discount_total_minor >= 0),
  shipping_total_minor integer not null default 0 check (shipping_total_minor >= 0),
  tax_total_minor integer not null default 0 check (tax_total_minor >= 0),
  grand_total_minor integer not null default 0 check (grand_total_minor >= 0),
  expires_at timestamptz not null,
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
  status text not null default 'active' check (status in ('active','released','committed','expired')),
  expires_at timestamptz not null,
  released_at timestamptz,
  committed_at timestamptz,
  created_at timestamptz not null default now(),
  unique (checkout_session_id, inventory_id)
);

create table if not exists public.shop_order_addresses (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.shop_orders(id) on delete cascade,
  source_address_id uuid references public.shop_addresses(id) on delete set null,
  label text,
  recipient_name text not null,
  phone text not null,
  city text not null,
  address_line1 text not null,
  address_line2 text,
  latitude numeric(10,7),
  longitude numeric(10,7),
  created_at timestamptz not null default now()
);

create table if not exists public.shop_payments (
  id uuid primary key default gen_random_uuid(),
  checkout_session_id uuid references public.shop_checkout_sessions(id) on delete set null,
  order_id uuid references public.shop_orders(id) on delete cascade,
  profile_id uuid not null references public.shop_profiles(id) on delete restrict,
  provider text not null,
  method_code text not null,
  status text not null default 'payment_pending' check (status in ('payment_pending','authorization_pending','authorized','paid','failed','expired','refunded','partially_refunded')),
  amount_minor integer not null check (amount_minor >= 0),
  currency char(3) not null default 'ETB' check (currency ~ '^[A-Z]{3}$'),
  provider_payment_id text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.shop_payment_events (
  id uuid primary key default gen_random_uuid(),
  payment_id uuid references public.shop_payments(id) on delete cascade,
  provider text not null,
  provider_event_id text not null,
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  processed_at timestamptz not null default now(),
  unique (provider, provider_event_id)
);

alter table public.shop_orders add column if not exists checkout_session_id uuid references public.shop_checkout_sessions(id) on delete set null;
alter table public.shop_orders add column if not exists payment_status text not null default 'payment_pending';
alter table public.shop_orders add column if not exists fulfillment_status text not null default 'created';
alter table public.shop_orders add column if not exists delivery_status text not null default 'not_shipped';

alter table public.shop_order_groups add column if not exists store_name_snapshot text;
alter table public.shop_order_groups add column if not exists payment_status text not null default 'payment_pending';
alter table public.shop_order_groups add column if not exists fulfillment_status text not null default 'created';

alter table public.shop_order_items add column if not exists currency char(3) not null default 'ETB';
alter table public.shop_order_items add column if not exists variant_snapshot jsonb not null default '{}'::jsonb;
alter table public.shop_order_items add column if not exists image_snapshot text;
alter table public.shop_order_items add column if not exists store_snapshot jsonb not null default '{}'::jsonb;

do $$
declare t text;
begin
  foreach t in array ARRAY[
    'shop_roles','shop_user_roles','shop_role_permissions','shop_payment_methods',
    'shop_checkout_sessions','shop_checkout_items','shop_inventory_reservations',
    'shop_order_addresses','shop_payments','shop_payment_events'
  ] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

do $$
declare t text;
begin
  foreach t in array ARRAY[
    'shop_user_roles','shop_role_permissions','shop_payment_methods',
    'shop_checkout_sessions','shop_checkout_items','shop_inventory_reservations',
    'shop_order_addresses','shop_payments','shop_payment_events'
  ] loop
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = t and policyname = 'client_access_deferred') then
      execute format('create policy client_access_deferred on public.%I for all to anon, authenticated using (false) with check (false)', t);
    end if;
  end loop;
end $$;

create index if not exists shop_user_roles_profile_active_idx on public.shop_user_roles(profile_id, revoked_at);
create index if not exists shop_checkout_sessions_profile_state_idx on public.shop_checkout_sessions(profile_id, state, created_at desc);
create index if not exists shop_checkout_items_session_idx on public.shop_checkout_items(checkout_session_id);
create index if not exists shop_inventory_reservations_session_idx on public.shop_inventory_reservations(checkout_session_id, status);
create index if not exists shop_inventory_reservations_expiry_idx on public.shop_inventory_reservations(status, expires_at);
create index if not exists shop_payments_order_idx on public.shop_payments(order_id, status);
create index if not exists shop_order_addresses_order_idx on public.shop_order_addresses(order_id);

create or replace function public.current_shop_roles(target_profile_id uuid)
returns text[]
language sql
stable
as $$
  select coalesce(array_agg(r.code order by r.code), array['CUSTOMER']::text[])
  from public.shop_roles r
  join public.shop_user_roles ur on ur.role_id = r.id
  where ur.profile_id = target_profile_id
    and ur.revoked_at is null
$$;

create or replace function public.has_shop_role(target_profile_id uuid, role_code text)
returns boolean
language sql
stable
as $$
  select role_code = 'CUSTOMER' or exists (
    select 1
    from public.shop_user_roles ur
    join public.shop_roles r on r.id = ur.role_id
    where ur.profile_id = target_profile_id
      and ur.revoked_at is null
      and r.code = role_code
  )
$$;

create or replace function public.release_expired_shop_checkout_reservations()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  released_count integer := 0;
  row_record record;
begin
  for row_record in
    select id, inventory_id, quantity, checkout_session_id
    from public.shop_inventory_reservations
    where status = 'active' and expires_at <= now()
    for update
  loop
    update public.shop_inventory
      set quantity_reserved = greatest(0, quantity_reserved - row_record.quantity),
          updated_at = now()
      where id = row_record.inventory_id;

    update public.shop_inventory_reservations
      set status = 'expired', released_at = now()
      where id = row_record.id;

    update public.shop_checkout_sessions
      set state = 'expired'
      where id = row_record.checkout_session_id and state = 'reserved';

    released_count := released_count + 1;
  end loop;
  return released_count;
end;
$$;

create or replace function public.adishop_create_checkout_session(
  p_profile_id uuid,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_session public.shop_checkout_sessions%rowtype;
  cart_record public.shop_carts%rowtype;
  checkout_id uuid;
  expires_at_value timestamptz := now() + interval '15 minutes';
  subtotal integer := 0;
  cart_item record;
  inventory_record public.shop_inventory%rowtype;
  payment_methods jsonb := '[]'::jsonb;
begin
  if p_profile_id is null or coalesce(length(trim(p_idempotency_key)), 0) < 8 then
    raise exception 'invalid-checkout-request';
  end if;

  perform public.release_expired_shop_checkout_reservations();

  select * into existing_session
  from public.shop_checkout_sessions
  where profile_id = p_profile_id and idempotency_key = p_idempotency_key
  order by created_at desc
  limit 1;

  if found then
    select coalesce(jsonb_agg(jsonb_build_object('code', code, 'label', label, 'provider', provider)), '[]'::jsonb)
      into payment_methods
    from public.shop_payment_methods
    where enabled = true;

    return jsonb_build_object(
      'id', existing_session.id,
      'state', existing_session.state,
      'currency', existing_session.currency,
      'subtotalMinor', existing_session.subtotal_minor,
      'discountMinor', existing_session.discount_total_minor,
      'shippingMinor', existing_session.shipping_total_minor,
      'taxMinor', existing_session.tax_total_minor,
      'grandTotalMinor', existing_session.grand_total_minor,
      'expiresAt', existing_session.expires_at,
      'paymentMethods', payment_methods
    );
  end if;

  select * into cart_record
  from public.shop_carts
  where profile_id = p_profile_id and status = 'active' and currency = 'ETB'
  order by updated_at desc
  limit 1;

  if not found then
    raise exception 'empty-cart';
  end if;

  insert into public.shop_checkout_sessions(profile_id, cart_id, idempotency_key, state, currency, expires_at)
  values (p_profile_id, cart_record.id, p_idempotency_key, 'reserved', cart_record.currency, expires_at_value)
  returning id into checkout_id;

  for cart_item in
    select
      ci.id as cart_item_id,
      ci.product_id,
      ci.variant_id,
      ci.quantity,
      p.store_id,
      p.title,
      p.currency,
      coalesce(v.price_minor, p.price_minor) as price_minor,
      p.status as product_status,
      p.moderation_status,
      p.stock_status,
      p.deleted_at,
      s.name as store_name,
      s.status as store_status,
      s.approval_status as store_approval_status,
      v.title as variant_title,
      v.options as variant_options,
      v.status as variant_status,
      (select image_url from public.shop_product_images pi where pi.product_id = p.id order by pi.is_primary desc, pi.display_order asc limit 1) as image_url
    from public.shop_cart_items ci
    join public.shop_products p on p.id = ci.product_id
    join public.shop_stores s on s.id = p.store_id
    left join public.shop_product_variants v on v.id = ci.variant_id
    where ci.cart_id = cart_record.id
    order by ci.created_at
  loop
    if cart_item.product_status <> 'active'
      or cart_item.moderation_status <> 'approved'
      or cart_item.deleted_at is not null
      or cart_item.store_status <> 'active'
      or cart_item.store_approval_status <> 'approved'
      or cart_item.stock_status = 'out_of_stock'
      or (cart_item.variant_id is not null and cart_item.variant_status <> 'active') then
      raise exception 'unavailable-item';
    end if;

    select * into inventory_record
    from public.shop_inventory
    where product_id = cart_item.product_id
      and ((cart_item.variant_id is null and variant_id is null) or variant_id = cart_item.variant_id)
    for update;

    if not found or inventory_record.quantity_available - inventory_record.quantity_reserved < cart_item.quantity then
      raise exception 'insufficient-stock';
    end if;

    update public.shop_inventory
      set quantity_reserved = quantity_reserved + cart_item.quantity,
          updated_at = now()
      where id = inventory_record.id;

    insert into public.shop_checkout_items(
      checkout_session_id, cart_item_id, product_id, variant_id, store_id, inventory_id,
      quantity, currency, unit_price_minor, line_total_minor, product_title_snapshot,
      variant_snapshot, image_snapshot, store_snapshot
    ) values (
      checkout_id, cart_item.cart_item_id, cart_item.product_id, cart_item.variant_id,
      cart_item.store_id, inventory_record.id, cart_item.quantity, cart_item.currency,
      cart_item.price_minor, cart_item.price_minor * cart_item.quantity, cart_item.title,
      jsonb_build_object('id', cart_item.variant_id, 'title', cart_item.variant_title, 'options', coalesce(cart_item.variant_options, '{}'::jsonb)),
      cart_item.image_url,
      jsonb_build_object('id', cart_item.store_id, 'name', cart_item.store_name)
    );

    insert into public.shop_inventory_reservations(
      checkout_session_id, inventory_id, product_id, variant_id, quantity, status, expires_at
    ) values (
      checkout_id, inventory_record.id, cart_item.product_id, cart_item.variant_id, cart_item.quantity, 'active', expires_at_value
    );

    subtotal := subtotal + (cart_item.price_minor * cart_item.quantity);
  end loop;

  if subtotal <= 0 then
    raise exception 'empty-cart';
  end if;

  update public.shop_checkout_sessions
    set subtotal_minor = subtotal,
        grand_total_minor = subtotal
    where id = checkout_id;

  select coalesce(jsonb_agg(jsonb_build_object('code', code, 'label', label, 'provider', provider)), '[]'::jsonb)
    into payment_methods
  from public.shop_payment_methods
  where enabled = true;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'checkout.create', 'shop_checkout_session', checkout_id, jsonb_build_object('subtotalMinor', subtotal));

  return jsonb_build_object(
    'id', checkout_id,
    'state', 'reserved',
    'currency', cart_record.currency,
    'subtotalMinor', subtotal,
    'discountMinor', 0,
    'shippingMinor', 0,
    'taxMinor', 0,
    'grandTotalMinor', subtotal,
    'expiresAt', expires_at_value,
    'paymentMethods', payment_methods
  );
exception
  when others then
    raise;
end;
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
  group_id_value uuid;
  order_number_value text;
  item_record record;
begin
  if p_profile_id is null or p_checkout_session_id is null then
    raise exception 'invalid-confirm-request';
  end if;

  select * into checkout_record
  from public.shop_checkout_sessions
  where id = p_checkout_session_id and profile_id = p_profile_id
  for update;

  if not found then raise exception 'checkout-not-found'; end if;

  if checkout_record.state = 'confirmed' and checkout_record.order_id is not null then
    return jsonb_build_object('orderId', checkout_record.order_id, 'state', 'created');
  end if;

  if checkout_record.state <> 'reserved' or checkout_record.expires_at <= now() then
    raise exception 'checkout-expired';
  end if;

  select * into method_record
  from public.shop_payment_methods
  where code = p_payment_method_code and enabled = true;
  if not found then raise exception 'payment-method-unavailable'; end if;

  select * into address_record
  from public.shop_addresses
  where id = p_address_id and profile_id = p_profile_id and deleted_at is null;
  if not found then raise exception 'address-not-found'; end if;

  order_number_value := 'AS-' || to_char(now(), 'YYYYMMDD') || '-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));

  insert into public.shop_orders(
    profile_id, address_id, checkout_session_id, order_number, status, payment_status,
    fulfillment_status, delivery_status, currency, subtotal_minor, delivery_fee_minor,
    discount_minor, total_minor
  ) values (
    p_profile_id, p_address_id, checkout_record.id, order_number_value, 'created',
    'payment_pending', 'created', 'not_shipped', checkout_record.currency,
    checkout_record.subtotal_minor, checkout_record.shipping_total_minor,
    checkout_record.discount_total_minor, checkout_record.grand_total_minor
  ) returning id into order_id_value;

  insert into public.shop_order_addresses(
    order_id, source_address_id, label, recipient_name, phone, city, address_line1, address_line2, latitude, longitude
  ) values (
    order_id_value, address_record.id, address_record.label, address_record.recipient_name, address_record.phone,
    address_record.city, address_record.address_line1, address_record.address_line2, address_record.latitude, address_record.longitude
  );

  for item_record in
    select * from public.shop_checkout_items where checkout_session_id = checkout_record.id order by created_at
  loop
    insert into public.shop_order_groups(order_id, store_id, status, subtotal_minor, store_name_snapshot, payment_status, fulfillment_status)
    values (order_id_value, item_record.store_id, 'created', 0, item_record.store_snapshot->>'name', 'payment_pending', 'created')
    on conflict (order_id, store_id) do update set subtotal_minor = public.shop_order_groups.subtotal_minor
    returning id into group_id_value;

    insert into public.shop_order_items(
      order_group_id, product_id, variant_id, title_snapshot, quantity, unit_price_minor,
      line_total_minor, currency, variant_snapshot, image_snapshot, store_snapshot
    ) values (
      group_id_value, item_record.product_id, item_record.variant_id, item_record.product_title_snapshot,
      item_record.quantity, item_record.unit_price_minor, item_record.line_total_minor, item_record.currency,
      item_record.variant_snapshot, item_record.image_snapshot, item_record.store_snapshot
    );

    update public.shop_order_groups
      set subtotal_minor = subtotal_minor + item_record.line_total_minor
      where id = group_id_value;
  end loop;

  insert into public.shop_payments(
    checkout_session_id, order_id, profile_id, provider, method_code, status, amount_minor, currency, metadata
  ) values (
    checkout_record.id, order_id_value, p_profile_id, method_record.provider, method_record.code,
    'payment_pending', checkout_record.grand_total_minor, checkout_record.currency,
    jsonb_build_object('idempotencyKey', p_idempotency_key)
  );

  update public.shop_inventory_reservations
    set status = 'committed', committed_at = now()
    where checkout_session_id = checkout_record.id and status = 'active';

  update public.shop_inventory i
    set quantity_available = greatest(0, i.quantity_available - r.quantity),
        quantity_reserved = greatest(0, i.quantity_reserved - r.quantity),
        updated_at = now()
    from public.shop_inventory_reservations r
    where r.checkout_session_id = checkout_record.id
      and r.inventory_id = i.id
      and r.status = 'committed';

  insert into public.shop_order_status_history(order_id, status, actor_profile_id, note)
  values (order_id_value, 'created', p_profile_id, 'Order created from checkout session');

  update public.shop_checkout_sessions
    set state = 'confirmed', confirmed_at = now(), order_id = order_id_value
    where id = checkout_record.id;

  update public.shop_carts set status = 'submitted', updated_at = now() where id = checkout_record.cart_id;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'checkout.confirm', 'shop_order', order_id_value, jsonb_build_object('checkoutSessionId', checkout_record.id));

  return jsonb_build_object(
    'orderId', order_id_value,
    'orderNumber', order_number_value,
    'state', 'created',
    'paymentStatus', 'payment_pending',
    'fulfillmentStatus', 'created'
  );
end;
$$;

revoke all on function public.release_expired_shop_checkout_reservations() from public;
revoke all on function public.adishop_create_checkout_session(uuid, text) from public;
revoke execute on function public.adishop_create_checkout_session(uuid, text) from anon;
revoke execute on function public.adishop_create_checkout_session(uuid, text) from authenticated;
grant execute on function public.adishop_create_checkout_session(uuid, text) to service_role;
revoke all on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from public;
revoke execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from anon;
revoke execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from authenticated;
grant execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) to service_role;
