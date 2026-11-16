-- LOCAL CANDIDATE ONLY. Generated from the deployed-hash-matched seller transition body.
begin;
set local lock_timeout = '5s';
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
as $function$
declare
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  previous_status text;
  next_status text;
  event_code text;
  public_message text;
  reason_code text;
  request_id text;
  buyer_visible_reason text;
  expected_version integer;
  payment_result jsonb := jsonb_build_object('status', 'not_required');
  expected_release_rows integer;
  released_rows integer;
begin
  select * into group_record from public.shop_order_groups where id = p_order_group_id for update;
  if not found then raise exception 'seller-order-not-found'; end if;
  if not public.adishop_profile_can_fulfill_store(p_profile_id, group_record.store_id) then
    raise exception 'seller-order-not-found';
  end if;
  select * into order_record from public.shop_orders where id = group_record.order_id for update;

  previous_status := group_record.fulfillment_status;
  request_id := nullif(trim(coalesce(p_metadata->>'requestId', '')), '');
  expected_version := nullif(trim(coalesce(p_metadata->>'expectedVersion', '')), '')::integer;

  if request_id is not null
     and group_record.seller_response_request_id = request_id
     and ((p_action = 'accept' and group_record.seller_response_status = 'accepted')
       or (p_action = 'reject' and group_record.seller_response_status = 'rejected')) then
    return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', group_record.fulfillment_status, 'idempotent', true, 'sellerResponseVersion', group_record.seller_response_version);
  end if;

  if expected_version is not null and expected_version <> group_record.seller_response_version then
    raise exception 'seller-response-version-conflict';
  end if;

  if p_action in ('accept','reject')
     and previous_status in ('awaiting_seller_confirmation','created')
     and (group_record.seller_response_status <> 'pending' or group_record.seller_response_required_at <= clock_timestamp()) then
    raise exception 'seller-response-deadline-expired';
  end if;

  if p_action = 'reject' then
    reason_code := nullif(trim(coalesce(p_metadata->>'reasonCode', '')), '');
    if reason_code is null then raise exception 'rejection-reason-required'; end if;
    if reason_code not in ('out_of_stock','product_unavailable','unable_to_fulfill','store_closed','invalid_delivery_area','buyer_request','duplicate_order','suspected_fraud','pricing_or_listing_error','other') then raise exception 'invalid-rejection-reason'; end if;
    if reason_code = 'other' and length(trim(coalesce(p_reason, ''))) < 10 then raise exception 'rejection-explanation-required'; end if;
    buyer_visible_reason := case when length(trim(coalesce(p_reason, ''))) >= 3 then left(trim(p_reason), 280) else public.adishop_rejection_reason_label(reason_code) end;
  end if;

  if p_action = 'accept' and previous_status in ('awaiting_seller_confirmation','created') then
    next_status := 'accepted'; event_code := 'seller_accepted'; public_message := 'Seller confirmed your order.';
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

  if next_status = 'rejected' then
    payment_result := public.adishop_prepare_group_payment_reversal(group_record, order_record, 'seller_cancelled', 'The seller could not fulfill this order.', 'seller-rejected');
  end if;

  update public.shop_order_groups
  set fulfillment_status = next_status,
      status = next_status,
      rejected_reason = case when next_status = 'rejected' then buyer_visible_reason else rejected_reason end,
      rejected_reason_code = case when next_status = 'rejected' then reason_code else rejected_reason_code end,
      buyer_visible_rejection_reason = case when next_status = 'rejected' then buyer_visible_reason else buyer_visible_rejection_reason end,
      rejected_request_id = case when next_status = 'rejected' then request_id else rejected_request_id end,
      seller_response_status = case when next_status = 'accepted' then 'accepted' when next_status = 'rejected' then 'rejected' else seller_response_status end,
      seller_responded_at = case when next_status in ('accepted','rejected') then clock_timestamp() else seller_responded_at end,
      seller_response_request_id = case when next_status in ('accepted','rejected') then request_id else seller_response_request_id end,
      seller_response_version = case when next_status in ('accepted','rejected') then seller_response_version + 1 else seller_response_version end,
      seller_response_refund_status = case when next_status = 'rejected' then coalesce(payment_result->>'status', 'not_required') else seller_response_refund_status end,
      seller_response_refund_request_id = case when next_status = 'rejected' then nullif(payment_result->>'refundRequestId', '')::uuid else seller_response_refund_request_id end,
      shipment_carrier = coalesce(p_metadata->>'carrier', shipment_carrier),
      shipment_tracking_number = coalesce(p_metadata->>'trackingNumber', shipment_tracking_number),
      shipped_at = case when next_status = 'handed_to_carrier' then clock_timestamp() else shipped_at end,
      updated_at = clock_timestamp()
  where id = p_order_group_id;

  if next_status = 'rejected' and group_record.inventory_released_at is null then
    select count(*) into expected_release_rows
    from (select distinct product_id, variant_id from public.shop_order_items
          where order_group_id = group_record.id) inventory_keys;
    if expected_release_rows = 0 or exists (
      select 1 from public.shop_order_items oi
      where oi.order_group_id = group_record.id
        and not exists (
          select 1 from public.shop_inventory i
          where i.product_id = oi.product_id
            and i.variant_id is not distinct from oi.variant_id
        )
    ) then
      raise exception 'inventory-release-missing';
    end if;

    with release_totals as (
      select i.id as inventory_id, sum(oi.quantity)::bigint as release_quantity
      from public.shop_order_items oi
      join public.shop_inventory i
        on i.product_id = oi.product_id
       and i.variant_id is not distinct from oi.variant_id
      where oi.order_group_id = group_record.id
      group by i.id
    )
    update public.shop_inventory i
    set quantity_available = (i.quantity_available::bigint + r.release_quantity)::integer,
        updated_at = clock_timestamp()
    from release_totals r
    where i.id = r.inventory_id;
    get diagnostics released_rows = row_count;
    if released_rows <> expected_release_rows then
      raise exception 'inventory-release-missing';
    end if;

    update public.shop_order_groups set inventory_released_at = clock_timestamp()
    where id = group_record.id and inventory_released_at is null;
  end if;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, reason, request_id, metadata, occurred_at
  ) values (
    order_record.id, group_record.id, next_status, p_profile_id, 'SELLER', previous_status, next_status,
    event_code, public_message, case when next_status = 'rejected' then reason_code else p_reason end,
    case when next_status = 'rejected' then buyer_visible_reason else p_reason end,
    request_id,
    coalesce(p_metadata, '{}'::jsonb) || jsonb_build_object('sellerResponseVersion', group_record.seller_response_version + case when next_status in ('accepted','rejected') then 1 else 0 end),
    clock_timestamp()
  ) on conflict do nothing;

  perform public.adishop_create_notification(
    order_record.profile_id,
    event_code,
    'Order update',
    case when next_status = 'rejected' then 'Your order was rejected: ' || buyer_visible_reason else public_message end,
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id, 'reasonCode', reason_code, 'requestId', request_id)
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_profile_id, 'seller_order_group.' || p_action, 'shop_order_group', group_record.id,
    coalesce(p_metadata, '{}'::jsonb) || jsonb_build_object('previousStatus', previous_status, 'newStatus', next_status, 'reasonCode', reason_code, 'requestId', request_id)
  ) on conflict do nothing;

  perform public.adishop_refresh_parent_order_status(order_record.id);
  return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', next_status, 'sellerResponseStatus', case when next_status in ('accepted','rejected') then next_status else group_record.seller_response_status end, 'sellerResponseVersion', group_record.seller_response_version + case when next_status in ('accepted','rejected') then 1 else 0 end);
end;
$function$;
revoke all on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) to service_role;
commit;
