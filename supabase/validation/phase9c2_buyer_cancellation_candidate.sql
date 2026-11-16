-- LOCAL PROPOSAL ONLY. Requires isolated race/payment tests and separate deployment approval.
-- Preserve the deployed signature and service-role-only grant. Group-first locks match seller acceptance.
begin;
set local lock_timeout = '5s';

create or replace function public.adishop_request_order_cancellation(
  p_profile_id uuid, p_order_id uuid, p_reason_code text, p_reason_text text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  order_record public.shop_orders%rowtype;
  group_record public.shop_order_groups%rowtype;
  item_record public.shop_order_items%rowtype;
  previous_status text;
  group_count integer := 0;
  payment_result jsonb;
begin
  if not exists (
    select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id
  ) then raise exception 'order-not-found'; end if;

  -- Lock every group before the parent; seller acceptance and expiry use the same order.
  for group_record in
    select * from public.shop_order_groups
    where order_id = p_order_id order by id for update
  loop
    group_count := group_count + 1;
  end loop;

  select * into order_record from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id for update;
  if not found then raise exception 'order-not-found'; end if;
  if group_count = 0 or order_record.created_at is null
     or clock_timestamp() >= order_record.created_at + interval '30 minutes'
     or order_record.status in ('cancelled','rejected','expired','delivered','completed')
     or order_record.fulfillment_status not in ('awaiting_seller_confirmation','created')
     or exists (
       select 1 from public.shop_order_groups g where g.order_id = p_order_id
         and (g.fulfillment_status not in ('awaiting_seller_confirmation','created')
              or g.seller_response_status <> 'pending'
              or g.seller_responded_at is not null)
     ) then
    raise exception 'cancellation-not-eligible';
  end if;

  previous_status := order_record.fulfillment_status;
  for group_record in
    select * from public.shop_order_groups where order_id = p_order_id order by id
  loop
    payment_result := public.adishop_prepare_group_payment_reversal(
      group_record, order_record, 'other',
      'The buyer cancelled before seller acceptance.', 'buyer-cancelled'
    );
    if group_record.inventory_released_at is null then
      for item_record in
        select * from public.shop_order_items where order_group_id = group_record.id
        order by product_id, variant_id, id
      loop
        update public.shop_inventory i
        set quantity_available = i.quantity_available + item_record.quantity,
            updated_at = clock_timestamp()
        where i.product_id = item_record.product_id
          and i.variant_id is not distinct from item_record.variant_id;
        if not found then raise exception 'inventory-release-missing'; end if;
      end loop;
    end if;
    update public.shop_order_groups
    set status = 'cancelled', fulfillment_status = 'cancelled',
        inventory_released_at = coalesce(inventory_released_at, clock_timestamp()),
        seller_response_version = seller_response_version + 1,
        seller_response_refund_status = coalesce(payment_result->>'status', 'not_required'),
        seller_response_refund_request_id = nullif(payment_result->>'refundRequestId', '')::uuid,
        updated_at = clock_timestamp()
    where id = group_record.id;
  end loop;

  insert into public.shop_order_cancellations(order_id, requested_by, reason_code, reason_text, status, resolved_at)
  values (p_order_id, p_profile_id, coalesce(nullif(p_reason_code, ''), 'customer_request'),
          nullif(p_reason_text, ''), 'auto_approved', clock_timestamp());
  update public.shop_orders
  set status = 'cancelled', fulfillment_status = 'cancelled', delivery_status = 'cancelled',
      cancellation_requested_at = clock_timestamp(), updated_at = clock_timestamp()
  where id = p_order_id;
  insert into public.shop_order_status_history(
    order_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, reason, occurred_at
  ) values (
    p_order_id, 'cancelled', p_profile_id, 'CUSTOMER', previous_status, 'cancelled',
    'order_cancelled', 'Order cancelled.', 'Buyer cancelled before seller acceptance.',
    p_reason_text, clock_timestamp()
  );
  perform public.adishop_create_notification(
    p_profile_id, 'order_cancelled', 'Order cancelled', 'Your order was cancelled.',
    jsonb_build_object('orderId', p_order_id)
  );
  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (p_profile_id, 'order.cancel.auto_approved', 'shop_order', p_order_id,
          jsonb_build_object('reasonCode', p_reason_code));
  return public.adishop_order_public_payload(p_order_id);
end;
$function$;

revoke all on function public.adishop_request_order_cancellation(uuid,uuid,text,text) from public, anon, authenticated;
grant execute on function public.adishop_request_order_cancellation(uuid,uuid,text,text) to service_role;
commit;
