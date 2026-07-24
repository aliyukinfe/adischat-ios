-- Phase 1.4C checkout 409 fix.
-- Keeps inventory/COD/idempotency validation intact while making expired checkout handling deterministic.

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

  perform public.release_expired_shop_checkout_reservations();

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
    update public.shop_checkout_sessions
    set state = 'expired', updated_at = now()
    where id = checkout_record.id
      and state = 'reserved'
      and expires_at <= now();
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
  if reservation_count = 0 then
    update public.shop_checkout_sessions
    set state = 'expired', updated_at = now()
    where id = checkout_record.id and state = 'reserved';
    raise exception 'checkout-expired';
  end if;

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

revoke all on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from public;
revoke execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from anon;
revoke execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) from authenticated;
grant execute on function public.adishop_confirm_checkout(uuid, uuid, uuid, text, text) to service_role;
