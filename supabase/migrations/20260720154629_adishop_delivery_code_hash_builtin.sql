create or replace function public.adishop_reveal_delivery_code(p_profile_id uuid, p_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  order_record public.shop_orders%rowtype;
  code_record public.shop_delivery_confirmation_codes%rowtype;
  code_plain text;
  salt_value text;
begin
  select * into order_record
  from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id
  for update;
  if not found then raise exception 'order-not-found'; end if;

  if order_record.fulfillment_status not in ('handed_to_carrier','shipped','out_for_delivery') then
    raise exception 'delivery-code-not-available';
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

  -- Plain codes are intentionally not stored. Re-reveal rotates the code so a
  -- customer who missed the first display can still complete delivery safely.
  code_plain := lpad((floor(random() * 1000000))::int::text, 6, '0');
  salt_value := replace(gen_random_uuid()::text, '-', '') || substr(md5(random()::text || clock_timestamp()::text), 1, 16);

  update public.shop_delivery_confirmation_codes
  set code_salt = salt_value,
      code_hash = md5(code_plain || ':' || salt_value),
      revealed_at = now(),
      failed_attempts = 0,
      locked_until = null
  where id = code_record.id;

  return jsonb_build_object('orderId', p_order_id, 'deliveryCode', code_plain, 'message', 'Show this code to the delivery person only when you receive the order.');
end;
$$;

create or replace function public.adishop_verify_delivery_code(
  p_profile_id uuid,
  p_order_group_id uuid,
  p_code text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  code_record public.shop_delivery_confirmation_codes%rowtype;
  normalized_code text;
  code_ok boolean;
begin
  normalized_code := regexp_replace(coalesce(p_code, ''), '\D', '', 'g');
  if length(normalized_code) <> 6 then
    raise exception 'invalid-delivery-code';
  end if;

  select * into group_record
  from public.shop_order_groups
  where id = p_order_group_id
  for update;
  if not found then raise exception 'seller-order-not-found'; end if;
  if not public.adishop_profile_can_fulfill_store(p_profile_id, group_record.store_id) then
    raise exception 'seller-order-not-found';
  end if;
  if group_record.fulfillment_status not in ('handed_to_carrier','shipped','out_for_delivery') then
    raise exception 'delivery-not-verifiable';
  end if;

  select * into order_record
  from public.shop_orders
  where id = group_record.order_id
  for update;

  select * into code_record
  from public.shop_delivery_confirmation_codes
  where order_id = order_record.id
  for update;
  if not found or code_record.code_hash is null or code_record.code_salt is null or code_record.revealed_at is null then
    raise exception 'delivery-code-not-revealed';
  end if;
  if code_record.verified_at is not null then
    raise exception 'delivery-code-already-used';
  end if;
  if code_record.locked_until is not null and code_record.locked_until > now() then
    raise exception 'delivery-code-locked';
  end if;

  code_ok := md5(normalized_code || ':' || code_record.code_salt) = code_record.code_hash;

  insert into public.shop_delivery_code_attempts(order_id, order_group_id, seller_profile_id, success)
  values (order_record.id, group_record.id, p_profile_id, code_ok);

  if not code_ok then
    update public.shop_delivery_confirmation_codes
    set failed_attempts = failed_attempts + 1,
        locked_until = case when failed_attempts + 1 >= 5 then now() + interval '10 minutes' else locked_until end
    where id = code_record.id;
    raise exception 'invalid-delivery-code';
  end if;

  update public.shop_delivery_confirmation_codes
  set verified_at = now()
  where id = code_record.id;

  update public.shop_order_groups
  set fulfillment_status = 'delivered',
      status = 'delivered',
      delivered_at = now(),
      updated_at = now()
  where id = group_record.id;

  update public.shop_orders
  set fulfillment_status = case
      when not exists (
        select 1 from public.shop_order_groups g
        where g.order_id = order_record.id
          and g.id <> group_record.id
          and g.fulfillment_status not in ('delivered','rejected','cancelled')
      ) then 'delivered'
      else fulfillment_status
    end,
    status = case
      when not exists (
        select 1 from public.shop_order_groups g
        where g.order_id = order_record.id
          and g.id <> group_record.id
          and g.fulfillment_status not in ('delivered','rejected','cancelled')
      ) then 'delivered'
      else status
    end,
    delivery_status = 'delivered',
    completed_at = case
      when not exists (
        select 1 from public.shop_order_groups g
        where g.order_id = order_record.id
          and g.id <> group_record.id
          and g.fulfillment_status not in ('delivered','rejected','cancelled')
      ) then now()
      else completed_at
    end,
    updated_at = now()
  where id = order_record.id;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, occurred_at
  )
  values (
    order_record.id, group_record.id, 'delivered', p_profile_id, 'SELLER',
    group_record.fulfillment_status, 'delivered',
    'delivery_confirmed', 'Your order was delivered.', 'Delivery code verified.', now()
  );

  perform public.adishop_create_notification(
    order_record.profile_id,
    'delivery_confirmed',
    'Order delivered',
    'Your order was delivered.',
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id)
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'seller_order_group.verify_delivery_code', 'shop_order_group', group_record.id, '{}'::jsonb);

  return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', 'delivered');
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
  previous_status text;
begin
  select * into order_record
  from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id
  for update;
  if not found then raise exception 'order-not-found'; end if;

  previous_status := order_record.fulfillment_status;
  if previous_status not in ('awaiting_seller_confirmation','accepted','created') then
    raise exception 'cancellation-not-eligible';
  end if;

  insert into public.shop_order_cancellations(order_id, requested_by, reason_code, reason_text, status, resolved_at)
  values (
    p_order_id,
    p_profile_id,
    coalesce(nullif(p_reason_code, ''), 'customer_request'),
    nullif(p_reason_text, ''),
    'auto_approved',
    now()
  );

  update public.shop_inventory i
  set quantity_available = i.quantity_available + oi.quantity,
      updated_at = now()
  from public.shop_order_groups og
  join public.shop_order_items oi on oi.order_group_id = og.id
  where og.order_id = p_order_id
    and og.inventory_released_at is null
    and og.fulfillment_status not in ('cancelled','rejected')
    and i.product_id = oi.product_id
    and i.variant_id is not distinct from oi.variant_id;

  update public.shop_order_groups
  set status = 'cancelled',
      fulfillment_status = 'cancelled',
      inventory_released_at = coalesce(inventory_released_at, now()),
      updated_at = now()
  where order_id = p_order_id
    and fulfillment_status not in ('cancelled','rejected','delivered');

  update public.shop_orders
  set status = 'cancelled',
      fulfillment_status = 'cancelled',
      delivery_status = 'cancelled',
      cancellation_requested_at = now(),
      updated_at = now()
  where id = p_order_id;

  insert into public.shop_order_status_history(
    order_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, reason, occurred_at
  )
  values (
    p_order_id, 'cancelled', p_profile_id, 'CUSTOMER',
    previous_status, 'cancelled',
    'order_cancelled', 'Order cancelled.', 'Customer cancellation auto-approved before restricted fulfilment.', p_reason_text, now()
  );

  perform public.adishop_create_notification(
    p_profile_id,
    'order_cancelled',
    'Order cancelled',
    'Your order was cancelled.',
    jsonb_build_object('orderId', p_order_id)
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'order.cancel.auto_approved', 'shop_order', p_order_id, jsonb_build_object('reasonCode', p_reason_code));

  return public.adishop_order_public_payload(p_order_id);
end;
$$;

revoke all on function public.adishop_verify_delivery_code(uuid, uuid, text) from public;
revoke execute on function public.adishop_verify_delivery_code(uuid, uuid, text) from anon;
revoke execute on function public.adishop_verify_delivery_code(uuid, uuid, text) from authenticated;
grant execute on function public.adishop_verify_delivery_code(uuid, uuid, text) to service_role;

revoke all on function public.adishop_reveal_delivery_code(uuid, uuid) from public;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from anon;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from authenticated;
grant execute on function public.adishop_reveal_delivery_code(uuid, uuid) to service_role;

revoke all on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from public;
revoke execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from anon;
revoke execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) from authenticated;
grant execute on function public.adishop_request_order_cancellation(uuid, uuid, text, text) to service_role;
