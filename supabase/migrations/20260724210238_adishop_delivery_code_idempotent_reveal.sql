alter table public.shop_delivery_confirmation_codes
add column if not exists delivery_code text;

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

  if code_record.delivery_code is not null
     and code_record.code_hash is not null
     and code_record.code_salt is not null
     and code_record.revealed_at is not null then
    return jsonb_build_object(
      'orderId', p_order_id,
      'deliveryCode', code_record.delivery_code,
      'message', 'Show this code to the delivery person only when you receive the order.'
    );
  end if;

  code_plain := lpad((floor(random() * 1000000))::int::text, 6, '0');
  salt_value := replace(gen_random_uuid()::text, '-', '') || substr(md5(random()::text || clock_timestamp()::text), 1, 16);

  update public.shop_delivery_confirmation_codes
  set delivery_code = code_plain,
      code_salt = salt_value,
      code_hash = md5(code_plain || ':' || salt_value),
      revealed_at = coalesce(revealed_at, now()),
      failed_attempts = 0,
      locked_until = null
  where id = code_record.id;

  return jsonb_build_object(
    'orderId', p_order_id,
    'deliveryCode', code_plain,
    'message', 'Show this code to the delivery person only when you receive the order.'
  );
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
    return jsonb_build_object('ok', false, 'code', 'invalid_delivery_code');
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
    return jsonb_build_object('ok', false, 'code', 'delivery_code_locked');
  end if;

  code_ok := md5(normalized_code || ':' || code_record.code_salt) = code_record.code_hash;

  insert into public.shop_delivery_code_attempts(order_id, order_group_id, seller_profile_id, success)
  values (order_record.id, group_record.id, p_profile_id, code_ok);

  if not code_ok then
    update public.shop_delivery_confirmation_codes
    set failed_attempts = failed_attempts + 1,
        locked_until = case when failed_attempts + 1 >= 5 then now() + interval '10 minutes' else locked_until end
    where id = code_record.id;
    return jsonb_build_object('ok', false, 'code', 'invalid_delivery_code');
  end if;

  update public.shop_delivery_confirmation_codes
  set verified_at = now(),
      delivery_code = null
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

revoke all on function public.adishop_reveal_delivery_code(uuid, uuid) from public;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from anon;
revoke execute on function public.adishop_reveal_delivery_code(uuid, uuid) from authenticated;
grant execute on function public.adishop_reveal_delivery_code(uuid, uuid) to service_role;

revoke all on function public.adishop_verify_delivery_code(uuid, uuid, text) from public;
revoke execute on function public.adishop_verify_delivery_code(uuid, uuid, text) from anon;
revoke execute on function public.adishop_verify_delivery_code(uuid, uuid, text) from authenticated;
grant execute on function public.adishop_verify_delivery_code(uuid, uuid, text) to service_role;
