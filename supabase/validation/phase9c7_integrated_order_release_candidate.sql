-- LOCAL REVIEW CANDIDATE ONLY. No cron activation or production approval.
-- Apply as one transaction only after separate production authorization.
begin;
set local lock_timeout = '5s';

-- Source: phase9c3_seller_sla_candidate.sql
create table if not exists public.shop_seller_response_policy (
  singleton boolean primary key default true check (singleton),
  default_hours smallint not null check (default_hours between 1 and 168),
  updated_at timestamptz not null default clock_timestamp()
);
insert into public.shop_seller_response_policy(singleton,default_hours)
values (true,24) on conflict (singleton) do nothing;
alter table public.shop_seller_response_policy enable row level security;
revoke all on public.shop_seller_response_policy from public, anon, authenticated;
grant select on public.shop_seller_response_policy to service_role;

create or replace function public.adishop_prepare_seller_response_sla()
returns trigger language plpgsql security definer set search_path = public as $function$
declare
  placed_at timestamptz;
  configured_hours smallint;
begin
  if new.fulfillment_status in ('awaiting_seller_confirmation','created')
     and (tg_op = 'INSERT' or old.fulfillment_status not in ('awaiting_seller_confirmation','created')
          or new.seller_response_required_at is null) then
    select o.created_at into placed_at from public.shop_orders o where o.id = new.order_id;
    select p.default_hours into configured_hours from public.shop_seller_response_policy p where p.singleton;
    if placed_at is null or configured_hours is null then raise exception 'seller-response-policy-unavailable'; end if;
    new.seller_response_sla_hours := configured_hours;
    new.seller_response_required_at := placed_at + make_interval(hours => configured_hours);
    new.seller_response_status := 'pending';
    new.seller_responded_at := null;
    new.seller_response_expired_at := null;
    new.seller_response_expiration_source := null;
    new.seller_response_request_id := null;
    new.seller_response_version := greatest(coalesce(new.seller_response_version, 1), 1);
    new.seller_response_refund_status := 'not_required';
    new.seller_response_refund_request_id := null;
  end if;
  return new;
end;
$function$;
-- Existing production trigger is retained; the fixture creates the same trigger below.

-- Source: phase9c2_buyer_cancellation_candidate.sql
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

-- Source: phase9c6_seller_rejection_inventory_candidate.sql
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

-- Source: phase9c5_seller_expiry_inventory_candidate.sql
create or replace function public.adishop_expire_seller_order_group(
  p_order_group_id uuid, p_source text default 'automatic'
)
returns jsonb
language plpgsql security definer set search_path = public
as $function$
declare
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  payment_result jsonb;
  seller_profile_id uuid;
  event_request_id text;
  expected_release_rows integer;
  released_rows integer;
begin
  select * into group_record from public.shop_order_groups where id = p_order_group_id for update;
  if not found then return jsonb_build_object('ok', false, 'code', 'seller-order-not-found'); end if;
  if group_record.seller_response_status <> 'pending'
     or group_record.fulfillment_status not in ('awaiting_seller_confirmation','created') then
    return jsonb_build_object('ok', true, 'idempotent', true, 'status', group_record.seller_response_status);
  end if;
  if group_record.seller_response_required_at is null or group_record.seller_response_required_at > clock_timestamp() then
    return jsonb_build_object('ok', false, 'code', 'seller-response-not-due');
  end if;

  select * into order_record from public.shop_orders where id = group_record.order_id for update;
  event_request_id := 'seller-response-expired:' || group_record.id;
  payment_result := public.adishop_prepare_group_payment_reversal(
    group_record, order_record, 'seller_response_expired',
    'The seller did not confirm the order before the response deadline.', event_request_id
  );

  update public.shop_order_groups
  set status = 'expired', fulfillment_status = 'expired', seller_response_status = 'expired',
      seller_response_expired_at = clock_timestamp(),
      seller_response_expiration_source = case when p_source = 'manual' then 'manual' else 'automatic' end,
      seller_response_version = seller_response_version + 1,
      seller_response_request_id = event_request_id,
      seller_response_refund_status = coalesce(payment_result->>'status', 'not_required'),
      seller_response_refund_request_id = nullif(payment_result->>'refundRequestId', '')::uuid,
      updated_at = clock_timestamp()
  where id = group_record.id;

  if group_record.inventory_released_at is null then
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

    update public.shop_order_groups
    set inventory_released_at = clock_timestamp()
    where id = group_record.id and inventory_released_at is null;
  end if;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, request_id, metadata, occurred_at
  ) values (
    order_record.id, group_record.id, 'expired', 'SYSTEM', group_record.fulfillment_status, 'expired',
    'seller_response_expired', 'Seller response deadline expired.',
    'Order group expired automatically after the seller response SLA.', event_request_id,
    jsonb_build_object('deadline', group_record.seller_response_required_at, 'source', p_source,
      'refundStatus', payment_result->>'status'), clock_timestamp()
  ) on conflict do nothing;

  perform public.adishop_create_notification_once(
    order_record.profile_id, 'seller_response_expired', 'Order cancelled automatically',
    'This seller did not confirm your order before the deadline.',
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id,
      'refundStatus', payment_result->>'status'), 'buyer-expired:' || group_record.id
  );
  if payment_result->>'status' = 'refund_pending' then
    perform public.adishop_create_notification_once(
      order_record.profile_id, 'automatic_refund_initiated', 'Refund initiated',
      'Your refund has been initiated and is awaiting payment-provider confirmation.',
      jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id,
        'refundRequestId', payment_result->>'refundRequestId'), 'buyer-refund:' || group_record.id
    );
  end if;

  for seller_profile_id in
    select distinct profile_id from (
      select s.owner_profile_id as profile_id from public.shop_stores s where s.id = group_record.store_id
      union all
      select sm.profile_id from public.shop_store_members sm
      where sm.store_id = group_record.store_id and sm.status = 'active'
    ) sellers where profile_id is not null
  loop
    perform public.adishop_create_notification_once(
      seller_profile_id, 'seller_response_expired', 'Order response deadline expired',
      'This order can no longer be accepted or fulfilled.',
      jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id),
      'seller-expired:' || group_record.id
    );
  end loop;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (null, 'seller_order_group.expire', 'shop_order_group', group_record.id,
    jsonb_build_object('requestId', event_request_id, 'deadline', group_record.seller_response_required_at,
      'source', p_source, 'refundStatus', payment_result->>'status'))
  on conflict do nothing;

  perform public.adishop_refresh_parent_order_status(order_record.id);
  return jsonb_build_object('ok', true, 'orderGroupId', group_record.id,
    'sellerResponseStatus', 'expired', 'refundStatus', payment_result->>'status',
    'refundRequestId', payment_result->>'refundRequestId');
end;
$function$;

revoke all on function public.adishop_expire_seller_order_group(uuid,text) from public, anon, authenticated;
grant execute on function public.adishop_expire_seller_order_group(uuid,text) to service_role;
commit;
