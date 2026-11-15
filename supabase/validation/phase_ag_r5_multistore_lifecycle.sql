-- ============================================================================
-- Phase AG-R5: Multi-Store Checkout, Order Lifecycle & Delivery Verification
-- Target: opkqyvttbyezjbqwkvho (AdisGebeya Staging)
-- Strictly Zero Production Impact - Maintain Free Tier $0.00
-- ============================================================================

-- 1. Create Delivery Tables in Staging
-- ============================================================================
create table if not exists public.shop_delivery_confirmation_codes (
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
drop policy if exists client_access_deferred on public.shop_delivery_confirmation_codes;
create policy client_access_deferred on public.shop_delivery_confirmation_codes
  for all to anon, authenticated using (false) with check (false);
grant select on public.shop_delivery_confirmation_codes to anon, authenticated;
grant select, insert, update, delete on public.shop_delivery_confirmation_codes to service_role;

create table if not exists public.shop_delivery_code_attempts (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete cascade,
  seller_profile_id uuid references public.shop_profiles(id) on delete set null,
  success boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.shop_delivery_code_attempts enable row level security;
drop policy if exists client_access_deferred on public.shop_delivery_code_attempts;
create policy client_access_deferred on public.shop_delivery_code_attempts
  for all to anon, authenticated using (false) with check (false);
grant select on public.shop_delivery_code_attempts to anon, authenticated;
grant select, insert, update, delete on public.shop_delivery_code_attempts to service_role;

-- 2. Setup Seller 2 Profile, Store 2, and Membership
-- ============================================================================
do $$
declare
  seller2_profile_id uuid := 'e2b49afa-59ff-4775-a145-ba2dab90eb22'::uuid;
  seller2_user_id uuid := '72b01c22-c661-4418-b5b6-ddd154491622'::uuid;
  seller2_family_id uuid := 'f2b01c22-c661-4418-b5b6-ddd154491622'::uuid;
  store2_id uuid := '92000000-0000-4000-8000-000000000002'::uuid;
  seller1_profile_id uuid := 'e1a39afa-59ff-4775-a145-ba2dab90eb13'::uuid;
  store1_id uuid := '92000000-0000-4000-8000-000000000001'::uuid;
begin
  -- Seller 2 Profile
  insert into public.shop_profiles(id, supabase_user_id, adischat_user_id, display_name, email, status)
  values (seller2_profile_id, null, 'staging-auth:' || seller2_profile_id::text, 'Synthetic QA Seller 2', 'qa.seller2.active@example.com', 'active')
  on conflict (id) do update set status = 'active', display_name = 'Synthetic QA Seller 2';

  insert into public.shop_user_links(shop_profile_id, provider, provider_user_id, account_status)
  values (seller2_profile_id, 'supabase', 'staging-auth:' || seller2_profile_id::text, 'active')
  on conflict do nothing;

  insert into public.shop_session_families(id, profile_id)
  values (seller2_family_id, seller2_profile_id)
  on conflict (id) do nothing;

  insert into public.shop_sessions(session_jti, profile_id, family_id, refresh_token_hash, expires_at, refresh_expires_at)
  values ('qa-contract-test-seller2-session', seller2_profile_id, seller2_family_id, 'qa-contract-hash-seller2', now() + interval '30 days', now() + interval '30 days')
  on conflict (session_jti) do update set expires_at = now() + interval '30 days', revoked_at = null;

  -- Store 2
  insert into public.shop_stores(id, owner_profile_id, name, slug, description, city, status, approval_status)
  values (store2_id, seller2_profile_id, 'Synthetic QA Groceries & Organics', 'synthetic-qa-groceries-organics',
          'Authoritative synthetic staging grocery store for multi-store checkout QA', 'Addis Ababa', 'active', 'approved')
  on conflict (id) do update set status = 'active', approval_status = 'approved', name = 'Synthetic QA Groceries & Organics';

  -- Store Memberships
  insert into public.shop_store_members(store_id, profile_id, role, status)
  values (store1_id, seller1_profile_id, 'owner', 'active')
  on conflict do nothing;

  insert into public.shop_store_members(store_id, profile_id, role, status)
  values (store2_id, seller2_profile_id, 'owner', 'active')
  on conflict do nothing;

  -- Reviewed store registration
  insert into public.shop_qa_reviewed_stores(store_id, store_updated_at, reviewed_by_profile_id, reviewed_at, name, description, city)
  values (store2_id, now(), '6d51194b-6f0d-44ba-84d1-50e812d972ec'::uuid, now(), 'Synthetic QA Groceries & Organics', 'Authoritative synthetic staging grocery store for multi-store checkout QA', 'Addis Ababa')
  on conflict (store_id) do update set name = 'Synthetic QA Groceries & Organics', store_updated_at = now();

  -- Reassign Groceries products to Store 2
  update public.shop_products
  set store_id = store2_id, updated_at = now()
  where id in (
    '93000000-0000-4000-8000-000000000041'::uuid, -- Organic Honey 1kg
    '93000000-0000-4000-8000-000000000042'::uuid, -- Roast Coffee 500g
    '93000000-0000-4000-8000-000000000031'::uuid, -- Electric Kettle 1.8L
    '93000000-0000-4000-8000-000000000032'::uuid  -- French Press Coffee Maker
  );

  -- Update Reviewed Catalog Store Timestamp
  update public.shop_qa_reviewed_catalog
  set store_updated_at = now()
  where product_id in (
    '93000000-0000-4000-8000-000000000041'::uuid,
    '93000000-0000-4000-8000-000000000042'::uuid,
    '93000000-0000-4000-8000-000000000031'::uuid,
    '93000000-0000-4000-8000-000000000032'::uuid
  );
end $$;

-- 3. Update public.shop_qa_public_stores View
-- ============================================================================
create or replace view public.shop_qa_public_stores with (security_invoker = true) as
select
  s.id,
  s.name,
  s.slug,
  s.description,
  s.city,
  s.status,
  s.approval_status,
  s.average_store_rating,
  s.total_sales,
  s.created_at
from public.shop_stores s
join public.shop_qa_reviewed_stores r on r.store_id = s.id
where s.deleted_at is null and s.status = 'active' and s.approval_status = 'approved';

grant select on public.shop_qa_public_stores to anon, authenticated;

-- 4. Update public.shop_qa_public_catalog View
-- ============================================================================
create or replace view public.shop_qa_public_catalog with (security_invoker = true) as
select
  p.id,
  p.store_id,
  p.category_id,
  p.title,
  p.slug,
  p.category_slug,
  p.short_description,
  p.description,
  p.price_minor,
  p.compare_at_price_minor,
  p.currency,
  p.stock_status,
  p.status,
  p.moderation_status,
  p.created_at,
  s.name as store_name,
  jsonb_build_object(
    'id', s.id,
    'name', s.name,
    'slug', s.slug,
    'city', s.city
  ) as store_snapshot,
  coalesce(
    (
      select jsonb_agg(jsonb_build_object(
        'id', pi.id,
        'image_url', pi.image_url,
        'is_primary', pi.is_primary,
        'display_order', pi.display_order
      ) order by pi.is_primary desc, pi.display_order asc)
      from public.shop_product_images pi
      where pi.product_id = p.id and pi.deleted_at is null
    ), '[]'::jsonb
  ) as images
from public.shop_products p
join public.shop_stores s on s.id = p.store_id
join public.shop_qa_reviewed_catalog rc on rc.product_id = p.id
where p.deleted_at is null and p.status = 'active' and p.moderation_status = 'approved'
  and s.deleted_at is null and s.status = 'active' and s.approval_status = 'approved';

grant select on public.shop_qa_public_catalog to anon, authenticated;

-- 5. Helper Function: Check Store Fulfillment Permissions
-- ============================================================================
create or replace function public.adishop_qa_can_fulfill_store(p_profile_id uuid, p_store_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.shop_store_members
    where store_id = p_store_id and profile_id = p_profile_id and status = 'active'
    union
    select 1 from public.shop_stores
    where id = p_store_id and owner_profile_id = p_profile_id and deleted_at is null
  );
$$;

-- 6. Updated Create Checkout RPC Supporting Multi-Store Cart
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
  cart_id_val uuid;
  cart_item record;
  cart_items_count integer := 0;
  checkout_id uuid;
  expires_at_value timestamptz := now() + interval '15 minutes';
  product_record record;
  inventory_record public.shop_inventory%rowtype;
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

  elsif p_source = 'CART' then
    select id into cart_id_val from public.shop_carts
    where profile_id = actor and status = 'active' and currency = 'ETB' for update;

    if not found then raise exception 'cart-empty' using errcode = '22023'; end if;

    for cart_item in
      select ci.*, p.store_id, p.title as product_title, p.price_minor as product_price,
             p.status as product_status, p.moderation_status, p.stock_status,
             s.name as store_name, s.status as store_status, s.approval_status as store_approval_status,
             v.title as variant_title, v.options as variant_options, v.price_minor as variant_price,
             v.status as variant_status,
             (select image_url from public.shop_product_images pi where pi.product_id = p.id order by pi.is_primary desc, pi.display_order asc limit 1) as image_url
      from public.shop_cart_items ci
      join public.shop_products p on p.id = ci.product_id
      join public.shop_stores s on s.id = p.store_id
      left join public.shop_product_variants v on v.id = ci.variant_id and v.product_id = p.id
      where ci.cart_id = cart_id_val
    loop
      cart_items_count := cart_items_count + 1;

      -- Validate product and store status
      if cart_item.product_status <> 'active' or cart_item.moderation_status <> 'approved'
         or cart_item.store_status <> 'active' or cart_item.store_approval_status <> 'approved'
         or cart_item.stock_status = 'out_of_stock'
         or (cart_item.variant_id is not null and cart_item.variant_status <> 'active') then
        raise exception 'unavailable-item' using errcode = '22023'; end if;

      -- Check and reserve inventory
      select * into inventory_record from public.shop_inventory
      where product_id = cart_item.product_id
        and ((cart_item.variant_id is null and variant_id is null) or variant_id = cart_item.variant_id)
      for update;

      if not found or (inventory_record.quantity_available - inventory_record.quantity_reserved) < cart_item.quantity then
        raise exception 'insufficient-stock' using errcode = '22023'; end if;

      update public.shop_inventory
        set quantity_reserved = quantity_reserved + cart_item.quantity, updated_at = now()
        where id = inventory_record.id;

      item_price := coalesce(cart_item.variant_price, cart_item.product_price);

      insert into public.shop_checkout_items(
        checkout_session_id, product_id, variant_id, store_id, inventory_id, quantity,
        currency, unit_price_minor, line_total_minor, product_title_snapshot,
        variant_snapshot, image_snapshot, store_snapshot
      ) values (
        checkout_id, cart_item.product_id, cart_item.variant_id, cart_item.store_id, inventory_record.id,
        cart_item.quantity, 'ETB', item_price, item_price * cart_item.quantity, cart_item.product_title,
        jsonb_build_object('id', cart_item.variant_id, 'title', cart_item.variant_title, 'options', coalesce(cart_item.variant_options, '{}'::jsonb)),
        cart_item.image_url,
        jsonb_build_object('id', cart_item.store_id, 'name', cart_item.store_name)
      );

      insert into public.shop_inventory_reservations(
        checkout_session_id, inventory_id, product_id, variant_id, quantity, status, expires_at
      ) values (
        checkout_id, inventory_record.id, cart_item.product_id, cart_item.variant_id, cart_item.quantity, 'active', expires_at_value
      );
    end loop;

    if cart_items_count = 0 then
      raise exception 'cart-empty' using errcode = '22023';
    end if;
  end if;

  return public.shop_recalculate_checkout(checkout_id);
end;
$$;
revoke all on function public.adishop_qa_create_checkout(text,text,text,uuid,uuid,integer) from public, anon, authenticated;
grant execute on function public.adishop_qa_create_checkout(text,text,text,uuid,uuid,integer) to service_role;

-- 7. Updated Confirm Checkout RPC with Cart Item Clearing and Delivery Code Initializer
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

  -- 4. Create Parent Order
  insert into public.shop_orders(
    profile_id, address_id, checkout_session_id, order_number, status, payment_status,
    fulfillment_status, delivery_status, currency, subtotal_minor, delivery_fee_minor,
    discount_minor, total_minor
  ) values (
    actor, p_address_id, cs.id, order_number_value, 'created', final_payment_status,
    'created', 'not_shipped', cs.currency, cs.subtotal_minor, cs.shipping_total_minor,
    cs.coupon_discount_minor + cs.voucher_applied_minor, cs.grand_total_minor
  ) returning id into order_id_value;

  -- Initialize delivery code container
  insert into public.shop_delivery_confirmation_codes(order_id)
  values (order_id_value)
  on conflict (order_id) do nothing;

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

  -- 5. Create Order Groups (Suborders) and Items per Seller
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

  -- 7. Clear Cart if checkout originated from CART
  if cs.source = 'CART' then
    delete from public.shop_cart_items ci
    using public.shop_carts c
    where ci.cart_id = c.id and c.profile_id = actor and c.status = 'active';
  end if;

  -- 8. Update Session State
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
    'isFullyCoveredByVoucher', (final_payment_status = 'paid'),
    'totalMinor', cs.grand_total_minor,
    'subordersCount', (select count(*) from public.shop_order_groups where order_id = order_id_value),
    'isDuplicate', false
  );
end;
$$;
revoke all on function public.adishop_qa_confirm_checkout(text,uuid,uuid,text,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_confirm_checkout(text,uuid,uuid,text,text) to service_role;

-- 8. RPC: Seller Order Action (Accept, Processing, Packed, Handed to Carrier, Reject)
-- ============================================================================
create or replace function public.adishop_qa_seller_order_action(
  p_session_jti text,
  p_store_id uuid,
  p_order_group_id uuid,
  p_action text,
  p_reason text default null,
  p_metadata jsonb default '{}'::jsonb
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  previous_status text;
  next_status text;
  event_code text;
  public_msg text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;

  -- Validate seller authorization for this store
  if not public.adishop_qa_can_fulfill_store(actor, p_store_id) then
    raise exception 'unauthorized-store-access' using errcode = '42501';
  end if;

  -- Lock and fetch suborder
  select * into group_record from public.shop_order_groups
  where id = p_order_group_id for update;

  if not found or group_record.store_id <> p_store_id then
    raise exception 'seller-order-not-found' using errcode = '22023';
  end if;

  select * into order_record from public.shop_orders
  where id = group_record.order_id for update;

  previous_status := group_record.fulfillment_status;

  if p_action = 'accept' and previous_status in ('created', 'awaiting_seller_confirmation') then
    next_status := 'accepted'; event_code := 'seller_accepted'; public_msg := 'Seller accepted your order.';
  elsif p_action = 'processing' and previous_status in ('accepted', 'created') then
    next_status := 'processing'; event_code := 'seller_processing'; public_msg := 'Seller started preparing your items.';
  elsif p_action = 'packed' and previous_status = 'processing' then
    next_status := 'packed'; event_code := 'seller_packed'; public_msg := 'Your order is packed.';
  elsif p_action = 'handed_to_carrier' and previous_status in ('packed', 'processing') then
    next_status := 'handed_to_carrier'; event_code := 'handed_to_carrier'; public_msg := 'Your order was handed to the carrier.';
  elsif p_action = 'reject' and previous_status in ('created', 'awaiting_seller_confirmation', 'accepted') then
    next_status := 'rejected'; event_code := 'seller_rejected'; public_msg := 'Seller rejected this part of the order.';
  else
    raise exception 'invalid-transition' using errcode = '22023';
  end if;

  -- Update suborder
  update public.shop_order_groups
  set fulfillment_status = next_status,
      status = next_status,
      updated_at = now()
  where id = p_order_group_id;

  -- Release inventory if rejected
  if next_status = 'rejected' then
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity,
        updated_at = now()
    from public.shop_order_items oi
    where oi.order_group_id = p_order_group_id
      and i.product_id = oi.product_id
      and i.variant_id is not distinct from oi.variant_id;
  end if;

  -- Aggregate status to parent order
  update public.shop_orders
  set fulfillment_status = case
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'processing') then 'processing'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'packed') then 'packed'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'handed_to_carrier') then 'shipped'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'delivered') then
        case when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status not in ('delivered', 'rejected', 'cancelled')) then 'partially_delivered' else 'delivered' end
      when not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status not in ('rejected', 'cancelled')) then 'cancelled'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'accepted') then 'accepted'
      else fulfillment_status
    end,
    status = case
      when next_status = 'handed_to_carrier' then 'shipped'
      when next_status in ('processing', 'packed') then 'processing'
      when next_status = 'rejected' and not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.id <> p_order_group_id and g.fulfillment_status not in ('rejected', 'cancelled')) then 'cancelled'
      else status
    end,
    updated_at = now()
  where id = order_record.id;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, public_message, internal_message
  ) values (
    order_record.id, p_order_group_id, next_status, actor, public_msg, coalesce(p_reason, public_msg)
  );

  return jsonb_build_object(
    'orderGroupId', p_order_group_id,
    'previousStatus', previous_status,
    'nextStatus', next_status,
    'parentFulfillmentStatus', (select fulfillment_status from public.shop_orders where id = order_record.id)
  );
end;
$$;
revoke all on function public.adishop_qa_seller_order_action(text,uuid,uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.adishop_qa_seller_order_action(text,uuid,uuid,text,text,jsonb) to service_role;

-- 9. RPC: Reveal Delivery Code (Buyer Auth Only)
-- ============================================================================
create or replace function public.adishop_qa_reveal_delivery_code(
  p_session_jti text,
  p_order_id uuid
)
returns jsonb language plpgsql volatile security definer set search_path = public, extensions as $$
declare
  actor uuid;
  order_rec public.shop_orders%rowtype;
  code_rec public.shop_delivery_confirmation_codes%rowtype;
  code_plain text;
  salt_val text;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;

  select * into order_rec from public.shop_orders
  where id = p_order_id and profile_id = actor for update;

  if not found then raise exception 'order-not-found' using errcode = '22023'; end if;

  select * into code_rec from public.shop_delivery_confirmation_codes
  where order_id = p_order_id for update;

  if not found then
    insert into public.shop_delivery_confirmation_codes(order_id)
    values (p_order_id)
    returning * into code_rec;
  end if;

  if code_rec.verified_at is not null then
    raise exception 'delivery-code-already-verified' using errcode = '22023';
  end if;

  -- Generate 6 digit numeric code
  if code_rec.revealed_at is null or code_rec.code_hash is null then
    code_plain := lpad((floor(random() * 1000000))::int::text, 6, '0');
    salt_val := encode(extensions.gen_random_bytes(16), 'hex');

    update public.shop_delivery_confirmation_codes
    set code_salt = salt_val,
        code_hash = encode(extensions.digest(code_plain || ':' || salt_val, 'sha256'), 'hex'),
        revealed_at = now(),
        delivery_code = code_plain
    where id = code_rec.id;
  else
    code_plain := code_rec.delivery_code;
  end if;

  return jsonb_build_object(
    'orderId', p_order_id,
    'deliveryCode', code_plain,
    'message', 'Show this code to the seller/delivery person only when you receive your order.'
  );
end;
$$;
revoke all on function public.adishop_qa_reveal_delivery_code(text,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_reveal_delivery_code(text,uuid) to service_role;

-- 10. RPC: Verify Delivery Code (Seller Auth Only)
-- ============================================================================
create or replace function public.adishop_qa_verify_delivery_code(
  p_session_jti text,
  p_store_id uuid,
  p_order_group_id uuid,
  p_code text
)
returns jsonb language plpgsql volatile security definer set search_path = public, extensions as $$
declare
  actor uuid;
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  code_record public.shop_delivery_confirmation_codes%rowtype;
  normalized_code text;
  code_ok boolean;
  new_failed integer;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;

  normalized_code := regexp_replace(coalesce(p_code, ''), '\D', '', 'g');
  if length(normalized_code) <> 6 then
    return jsonb_build_object('verified', false, 'error', 'invalid-delivery-code-format');
  end if;

  if not public.adishop_qa_can_fulfill_store(actor, p_store_id) then
    raise exception 'unauthorized-store-access' using errcode = '42501';
  end if;

  select * into group_record from public.shop_order_groups
  where id = p_order_group_id for update;

  if not found or group_record.store_id <> p_store_id then
    raise exception 'seller-order-not-found' using errcode = '22023';
  end if;

  if group_record.fulfillment_status not in ('handed_to_carrier', 'shipped', 'out_for_delivery') then
    return jsonb_build_object('verified', false, 'error', 'delivery-not-in-dispatched-state');
  end if;

  select * into order_record from public.shop_orders
  where id = group_record.order_id for update;

  select * into code_record from public.shop_delivery_confirmation_codes
  where order_id = order_record.id for update;

  if not found or code_record.code_hash is null or code_record.revealed_at is null then
    return jsonb_build_object('verified', false, 'error', 'delivery-code-not-revealed');
  end if;

  if group_record.fulfillment_status = 'delivered' then
    return jsonb_build_object('verified', false, 'error', 'suborder-already-delivered');
  end if;

  if code_record.locked_until is not null and code_record.locked_until > now() then
    return jsonb_build_object('verified', false, 'error', 'delivery-code-locked', 'lockedUntil', code_record.locked_until);
  end if;

  code_ok := encode(extensions.digest(normalized_code || ':' || code_record.code_salt, 'sha256'), 'hex') = code_record.code_hash;

  insert into public.shop_delivery_code_attempts(order_id, order_group_id, seller_profile_id, success)
  values (order_record.id, group_record.id, actor, code_ok);

  if not code_ok then
    new_failed := code_record.failed_attempts + 1;
    update public.shop_delivery_confirmation_codes
    set failed_attempts = new_failed,
        locked_until = case when new_failed >= 5 then now() + interval '10 minutes' else locked_until end
    where id = code_record.id;

    return jsonb_build_object(
      'verified', false,
      'error', case when new_failed >= 5 then 'delivery-code-locked' else 'invalid-delivery-code' end,
      'failedAttempts', new_failed,
      'isLocked', (new_failed >= 5)
    );
  end if;

  -- Code is valid! Mark suborder delivered
  update public.shop_order_groups
  set fulfillment_status = 'delivered',
      status = 'delivered',
      updated_at = now()
  where id = group_record.id;

  -- If all suborders are now delivered/resolved, mark delivery code verified and complete parent order
  if not exists (
    select 1 from public.shop_order_groups g
    where g.order_id = order_record.id
      and g.id <> group_record.id
      and g.fulfillment_status not in ('delivered', 'rejected', 'cancelled')
  ) then
    update public.shop_delivery_confirmation_codes
    set verified_at = now()
    where id = code_record.id;
  end if;

  -- Update parent order if all suborders are delivered
  update public.shop_orders
  set fulfillment_status = case
      when not exists (
        select 1 from public.shop_order_groups g
        where g.order_id = order_record.id
          and g.id <> group_record.id
          and g.fulfillment_status not in ('delivered', 'rejected', 'cancelled')
      ) then 'delivered'
      else 'partially_delivered'
    end,
    status = case
      when not exists (
        select 1 from public.shop_order_groups g
        where g.order_id = order_record.id
          and g.id <> group_record.id
          and g.fulfillment_status not in ('delivered', 'rejected', 'cancelled')
      ) then 'completed'
      else status
    end,
    delivery_status = 'delivered',
    updated_at = now()
  where id = order_record.id;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, public_message, internal_message
  ) values (
    order_record.id, group_record.id, 'delivered', actor,
    'Delivery successfully confirmed with customer verification code.', 'Delivery code verified by seller.'
  );

  return jsonb_build_object(
    'orderGroupId', group_record.id,
    'verified', true,
    'suborderStatus', 'delivered',
    'parentOrderStatus', (select status from public.shop_orders where id = order_record.id),
    'parentFulfillmentStatus', (select fulfillment_status from public.shop_orders where id = order_record.id)
  );
end;
$$;
revoke all on function public.adishop_qa_verify_delivery_code(text,uuid,uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_qa_verify_delivery_code(text,uuid,uuid,text) to service_role;

-- 11. RPC: Get Seller Orders (Enforcing Cross-Seller Isolation)
-- ============================================================================
create or replace function public.adishop_qa_seller_orders(
  p_session_jti text,
  p_store_id uuid
)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  actor uuid;
begin
  actor := shop_qa_private.locked_actor(p_session_jti);
  if actor is null then raise exception 'unauthorized' using errcode = '42501'; end if;

  if not public.adishop_qa_can_fulfill_store(actor, p_store_id) then
    raise exception 'unauthorized-store-access' using errcode = '42501';
  end if;

  return coalesce(
    (
      select jsonb_agg(
        jsonb_build_object(
          'id', g.id,
          'orderId', g.order_id,
          'storeId', g.store_id,
          'status', g.status,
          'fulfillmentStatus', g.fulfillment_status,
          'subtotalMinor', g.subtotal_minor,
          'createdAt', g.created_at,
          'items', coalesce(
            (
              select jsonb_agg(
                jsonb_build_object(
                  'id', oi.id,
                  'productId', oi.product_id,
                  'title', oi.title_snapshot,
                  'quantity', oi.quantity,
                  'unitPriceMinor', oi.unit_price_minor,
                  'lineTotalMinor', oi.line_total_minor
                )
              )
              from public.shop_order_items oi
              where oi.order_group_id = g.id
            ), '[]'::jsonb
          )
        )
        order by g.created_at desc
      )
      from public.shop_order_groups g
      where g.store_id = p_store_id
    ), '[]'::jsonb
  );
end;
$$;
revoke all on function public.adishop_qa_seller_orders(text,uuid) from public, anon, authenticated;
grant execute on function public.adishop_qa_seller_orders(text,uuid) to service_role;
