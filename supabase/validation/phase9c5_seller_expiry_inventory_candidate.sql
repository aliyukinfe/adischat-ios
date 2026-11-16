-- LOCAL CANDIDATE ONLY. Preserve the deployed expiry contract and group-first lock order.
begin;
set local lock_timeout = '5s';

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
