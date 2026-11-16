-- Phase 1.4J: server-authoritative seller acceptance SLA.

create extension if not exists pg_cron;

alter table public.shop_order_groups
  add column if not exists seller_response_required_at timestamptz,
  add column if not exists seller_responded_at timestamptz,
  add column if not exists seller_response_expired_at timestamptz,
  add column if not exists seller_response_status text not null default 'pending',
  add column if not exists seller_response_sla_hours smallint not null default 24,
  add column if not exists seller_response_version integer not null default 1,
  add column if not exists seller_response_request_id text,
  add column if not exists seller_response_expiration_source text,
  add column if not exists seller_response_refund_status text not null default 'not_required',
  add column if not exists seller_response_refund_request_id uuid references public.shop_refund_requests(id) on delete set null;

alter table public.shop_order_groups
  drop constraint if exists shop_order_groups_seller_response_status_check,
  add constraint shop_order_groups_seller_response_status_check
    check (seller_response_status in ('pending','accepted','rejected','expired')),
  drop constraint if exists shop_order_groups_seller_response_sla_hours_check,
  add constraint shop_order_groups_seller_response_sla_hours_check
    check (seller_response_sla_hours between 1 and 168),
  drop constraint if exists shop_order_groups_seller_response_version_check,
  add constraint shop_order_groups_seller_response_version_check
    check (seller_response_version > 0),
  drop constraint if exists shop_order_groups_seller_response_expiration_source_check,
  add constraint shop_order_groups_seller_response_expiration_source_check
    check (seller_response_expiration_source is null or seller_response_expiration_source in ('automatic','manual')),
  drop constraint if exists shop_order_groups_seller_response_refund_status_check,
  add constraint shop_order_groups_seller_response_refund_status_check
    check (seller_response_refund_status in ('not_required','authorization_void_pending','refund_pending','refund_processing','refunded','refund_failed'));

alter table public.shop_order_groups drop constraint if exists shop_order_groups_status_1_4c_check;
alter table public.shop_order_groups add constraint shop_order_groups_status_1_4c_check
  check (status in ('created','accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','cancelled','rejected','expired','preparing','ready'));

alter table public.shop_order_groups drop constraint if exists shop_order_groups_fulfillment_status_1_4c_check;
alter table public.shop_order_groups add constraint shop_order_groups_fulfillment_status_1_4c_check
  check (fulfillment_status in ('awaiting_seller_confirmation','accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','rejected','cancelled','expired','created'));

alter table public.shop_refund_requests drop constraint if exists shop_refund_requests_reason_code_check;
alter table public.shop_refund_requests add constraint shop_refund_requests_reason_code_check
  check (reason_code in ('item_not_received','wrong_item','damaged_item','incomplete_order','not_as_described','defective_item','duplicate_charge','seller_cancelled','seller_response_expired','delivery_issue','other'));

create index if not exists shop_order_groups_seller_response_due_idx
  on public.shop_order_groups (seller_response_required_at, id)
  where seller_response_status = 'pending' and fulfillment_status in ('awaiting_seller_confirmation','created');

create unique index if not exists shop_order_groups_seller_response_request_once_idx
  on public.shop_order_groups (id, seller_response_request_id)
  where seller_response_request_id is not null;

create unique index if not exists shop_order_history_seller_response_once_idx
  on public.shop_order_status_history (order_group_id, event_code, request_id)
  where order_group_id is not null
    and request_id is not null
    and event_code in ('waiting_for_seller','seller_accepted','seller_rejected','seller_response_expired','refund_initiated');

create unique index if not exists shop_notifications_sla_once_idx
  on public.shop_notifications (profile_id, type, ((data->>'dedupeKey')))
  where coalesce(data->>'dedupeKey', '') <> ''
    and type in ('seller_confirmation_pending','seller_order_received','seller_response_reminder','seller_response_expired','automatic_refund_initiated');

create unique index if not exists shop_audit_sla_once_idx
  on public.shop_audit_logs (action, entity_id)
  where action in ('seller_order_group.accept','seller_order_group.reject','seller_order_group.expire')
    and entity_id is not null;

update public.shop_order_groups
set seller_response_status = case
      when fulfillment_status in ('accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered') then 'accepted'
      when fulfillment_status = 'rejected' then 'rejected'
      when fulfillment_status = 'expired' then 'expired'
      when fulfillment_status in ('awaiting_seller_confirmation','created') then 'pending'
      else 'rejected'
    end,
    seller_responded_at = case
      when fulfillment_status in ('accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery','delivered','rejected') then coalesce(seller_responded_at, updated_at)
      else seller_responded_at
    end,
    seller_response_required_at = case
      when fulfillment_status in ('awaiting_seller_confirmation','created') then coalesce(seller_response_required_at, clock_timestamp() + interval '24 hours')
      else seller_response_required_at
    end;

create or replace function public.adishop_prepare_seller_response_sla()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if new.fulfillment_status in ('awaiting_seller_confirmation','created')
     and (tg_op = 'INSERT' or old.fulfillment_status not in ('awaiting_seller_confirmation','created') or new.seller_response_required_at is null) then
    new.seller_response_sla_hours := 24;
    new.seller_response_required_at := clock_timestamp() + interval '24 hours';
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

drop trigger if exists shop_order_groups_prepare_seller_response_sla on public.shop_order_groups;
create trigger shop_order_groups_prepare_seller_response_sla
before insert or update of fulfillment_status on public.shop_order_groups
for each row execute function public.adishop_prepare_seller_response_sla();

create or replace function public.adishop_create_notification_once(
  p_profile_id uuid,
  p_type text,
  p_title text,
  p_body text,
  p_data jsonb,
  p_dedupe_key text
)
returns uuid
language plpgsql
security definer
set search_path = public
as $function$
declare
  notification_id uuid;
begin
  if p_profile_id is null or nullif(trim(coalesce(p_dedupe_key, '')), '') is null then
    return null;
  end if;
  insert into public.shop_notifications(profile_id, type, title, body, data)
  values (
    p_profile_id,
    p_type,
    p_title,
    p_body,
    coalesce(p_data, '{}'::jsonb) || jsonb_build_object('dedupeKey', p_dedupe_key)
  )
  on conflict do nothing
  returning id into notification_id;
  if notification_id is null then
    select id into notification_id
    from public.shop_notifications
    where profile_id = p_profile_id
      and type = p_type
      and data->>'dedupeKey' = p_dedupe_key
    order by created_at desc
    limit 1;
  end if;
  return notification_id;
end;
$function$;

create or replace function public.adishop_notify_new_seller_order_group()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  order_record public.shop_orders%rowtype;
  seller_profile_id uuid;
  deadline_text text;
begin
  if new.fulfillment_status not in ('awaiting_seller_confirmation','created') then return new; end if;
  select * into order_record from public.shop_orders where id = new.order_id;
  deadline_text := to_char(new.seller_response_required_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI "UTC"');

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, request_id, metadata, occurred_at
  ) values (
    new.order_id, new.id, 'awaiting_seller_confirmation', 'SYSTEM', null, 'awaiting_seller_confirmation',
    'waiting_for_seller', 'Waiting for seller confirmation.', 'Seller response required by ' || deadline_text,
    'seller-response-pending:' || new.id,
    jsonb_build_object('sellerResponseRequiredAt', new.seller_response_required_at, 'sellerResponseSlaHours', new.seller_response_sla_hours),
    clock_timestamp()
  ) on conflict do nothing;

  perform public.adishop_create_notification_once(
    order_record.profile_id,
    'seller_confirmation_pending',
    'Waiting for seller confirmation',
    coalesce(new.store_name_snapshot, 'The seller') || ' has 24 hours to confirm your order.',
    jsonb_build_object('orderId', new.order_id, 'orderGroupId', new.id, 'deadline', new.seller_response_required_at),
    'buyer-pending:' || new.id
  );

  for seller_profile_id in
    select distinct profile_id from (
      select s.owner_profile_id as profile_id from public.shop_stores s where s.id = new.store_id
      union all
      select sm.profile_id from public.shop_store_members sm where sm.store_id = new.store_id and sm.status = 'active'
    ) sellers where profile_id is not null
  loop
    perform public.adishop_create_notification_once(
      seller_profile_id,
      'seller_order_received',
      'New order received',
      'Please accept or reject this order within 24 hours.',
      jsonb_build_object('orderId', new.order_id, 'orderGroupId', new.id, 'deadline', new.seller_response_required_at),
      'seller-new-order:' || new.id
    );
  end loop;
  return new;
end;
$function$;

drop trigger if exists shop_order_groups_notify_new_seller_order on public.shop_order_groups;
create trigger shop_order_groups_notify_new_seller_order
after insert on public.shop_order_groups
for each row execute function public.adishop_notify_new_seller_order_group();

create or replace function public.adishop_refresh_parent_order_status(p_order_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
  update public.shop_orders o
  set fulfillment_status = case
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status = 'processing') then 'processing'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status = 'packed') then 'packed'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status = 'ready_for_pickup') then 'ready_for_pickup'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status in ('handed_to_carrier','shipped','out_for_delivery')) then 'handed_to_carrier'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status = 'accepted') then 'accepted'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status in ('awaiting_seller_confirmation','created')) then 'awaiting_seller_confirmation'
        when not exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status not in ('rejected','cancelled','expired')) then 'cancelled'
        else o.fulfillment_status
      end,
      status = case
        when not exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status not in ('rejected','cancelled','expired')) then 'cancelled'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status in ('handed_to_carrier','shipped','out_for_delivery')) then 'shipped'
        when exists (select 1 from public.shop_order_groups g where g.order_id = o.id and g.fulfillment_status in ('processing','packed','ready_for_pickup')) then 'processing'
        else o.status
      end,
      updated_at = clock_timestamp()
  where o.id = p_order_id;
end;
$function$;

create or replace function public.adishop_prepare_group_payment_reversal(
  p_group public.shop_order_groups,
  p_order public.shop_orders,
  p_reason_code text,
  p_description text,
  p_dedupe_prefix text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  payment_record public.shop_payments%rowtype;
  refund_request_id uuid;
  refund_state text := 'not_required';
  request_key text := p_dedupe_prefix || ':' || p_group.id;
begin
  select * into payment_record
  from public.shop_payments
  where order_id = p_order.id
  order by created_at desc
  limit 1
  for update;

  if not found or payment_record.status in ('unpaid','payment_pending','pending','failed','expired') then
    refund_state := 'not_required';
  elsif payment_record.status in ('authorized','authorization_pending') then
    refund_state := 'authorization_void_pending';
    insert into public.shop_payment_events(payment_id, provider, provider_event_id, event_type, payload)
    values (
      payment_record.id, payment_record.provider, request_key,
      'authorization_void_requested',
      jsonb_build_object('orderId', p_order.id, 'orderGroupId', p_group.id, 'amountMinor', p_group.subtotal_minor)
    ) on conflict (provider, provider_event_id) do nothing;
  elsif payment_record.status in ('paid','partially_refunded') then
    refund_state := 'refund_pending';
    insert into public.shop_refund_requests(
      order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
      reason_code, description, requested_amount, currency, status,
      buyer_visible_update, idempotency_key
    ) values (
      p_order.id, p_group.id, p_order.profile_id, p_group.store_id,
      (select owner_profile_id from public.shop_stores where id = p_group.store_id),
      p_reason_code, p_description, p_group.subtotal_minor, p_order.currency, 'submitted',
      'Your refund has been initiated and is awaiting payment-provider confirmation.', request_key
    )
    on conflict (buyer_profile_id, idempotency_key) do update
      set updated_at = public.shop_refund_requests.updated_at
    returning id into refund_request_id;

    insert into public.shop_payment_events(payment_id, provider, provider_event_id, event_type, payload)
    values (
      payment_record.id, payment_record.provider, request_key,
      'refund_requested',
      jsonb_build_object('orderId', p_order.id, 'orderGroupId', p_group.id, 'refundRequestId', refund_request_id, 'amountMinor', p_group.subtotal_minor)
    ) on conflict (provider, provider_event_id) do nothing;
  end if;

  return jsonb_build_object('status', refund_state, 'refundRequestId', refund_request_id);
end;
$function$;

create or replace function public.adishop_expire_seller_order_group(
  p_order_group_id uuid,
  p_source text default 'automatic'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  group_record public.shop_order_groups%rowtype;
  order_record public.shop_orders%rowtype;
  payment_result jsonb;
  seller_profile_id uuid;
  event_request_id text;
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
    group_record,
    order_record,
    'seller_response_expired',
    'The seller did not confirm the order before the response deadline.',
    event_request_id
  );

  update public.shop_order_groups
  set status = 'expired',
      fulfillment_status = 'expired',
      seller_response_status = 'expired',
      seller_response_expired_at = clock_timestamp(),
      seller_response_expiration_source = case when p_source = 'manual' then 'manual' else 'automatic' end,
      seller_response_version = seller_response_version + 1,
      seller_response_request_id = event_request_id,
      seller_response_refund_status = coalesce(payment_result->>'status', 'not_required'),
      seller_response_refund_request_id = nullif(payment_result->>'refundRequestId', '')::uuid,
      updated_at = clock_timestamp()
  where id = group_record.id;

  if group_record.inventory_released_at is null then
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity,
        updated_at = clock_timestamp()
    from public.shop_order_items oi
    where oi.order_group_id = group_record.id
      and i.product_id = oi.product_id
      and i.variant_id is not distinct from oi.variant_id;
    update public.shop_order_groups set inventory_released_at = clock_timestamp() where id = group_record.id and inventory_released_at is null;
  end if;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, request_id, metadata, occurred_at
  ) values (
    order_record.id, group_record.id, 'expired', 'SYSTEM', group_record.fulfillment_status, 'expired',
    'seller_response_expired', 'Seller response deadline expired.',
    'Order group expired automatically after the seller response SLA.', event_request_id,
    jsonb_build_object('deadline', group_record.seller_response_required_at, 'source', p_source, 'refundStatus', payment_result->>'status'),
    clock_timestamp()
  ) on conflict do nothing;

  perform public.adishop_create_notification_once(
    order_record.profile_id,
    'seller_response_expired',
    'Order cancelled automatically',
    'This seller did not confirm your order before the deadline.',
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id, 'refundStatus', payment_result->>'status'),
    'buyer-expired:' || group_record.id
  );
  if payment_result->>'status' = 'refund_pending' then
    perform public.adishop_create_notification_once(
      order_record.profile_id,
      'automatic_refund_initiated',
      'Refund initiated',
      'Your refund has been initiated and is awaiting payment-provider confirmation.',
      jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id, 'refundRequestId', payment_result->>'refundRequestId'),
      'buyer-refund:' || group_record.id
    );
  end if;

  for seller_profile_id in
    select distinct profile_id from (
      select s.owner_profile_id as profile_id from public.shop_stores s where s.id = group_record.store_id
      union all
      select sm.profile_id from public.shop_store_members sm where sm.store_id = group_record.store_id and sm.status = 'active'
    ) sellers where profile_id is not null
  loop
    perform public.adishop_create_notification_once(
      seller_profile_id,
      'seller_response_expired',
      'Order response deadline expired',
      'This order can no longer be accepted or fulfilled.',
      jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id),
      'seller-expired:' || group_record.id
    );
  end loop;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    null, 'seller_order_group.expire', 'shop_order_group', group_record.id,
    jsonb_build_object('requestId', event_request_id, 'deadline', group_record.seller_response_required_at, 'source', p_source, 'refundStatus', payment_result->>'status')
  ) on conflict do nothing;

  perform public.adishop_refresh_parent_order_status(order_record.id);
  return jsonb_build_object(
    'ok', true,
    'orderGroupId', group_record.id,
    'sellerResponseStatus', 'expired',
    'refundStatus', payment_result->>'status',
    'refundRequestId', payment_result->>'refundRequestId'
  );
end;
$function$;

create or replace function public.adishop_expire_due_seller_order_groups(p_limit integer default 100)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  group_id uuid;
  seller_profile_id uuid;
  processed integer := 0;
begin
  -- Exactly-once one-hour reminders use the same notification dedupe contract.
  for group_id in
    select g.id
    from public.shop_order_groups g
    where g.seller_response_status = 'pending'
      and g.fulfillment_status in ('awaiting_seller_confirmation','created')
      and g.seller_response_required_at > clock_timestamp()
      and g.seller_response_required_at <= clock_timestamp() + interval '1 hour'
    order by g.seller_response_required_at
    limit greatest(1, least(coalesce(p_limit, 100), 500))
  loop
    perform public.adishop_create_notification_once(
      (select o.profile_id from public.shop_orders o join public.shop_order_groups g on g.order_id = o.id where g.id = group_id),
      'seller_response_reminder',
      'Seller confirmation due soon',
      'The seller has less than one hour to confirm this order.',
      jsonb_build_object('orderGroupId', group_id),
      'buyer-one-hour:' || group_id
    );
    for seller_profile_id in
      select distinct profile_id from (
        select s.owner_profile_id as profile_id from public.shop_stores s join public.shop_order_groups g on g.store_id = s.id where g.id = group_id
        union all
        select sm.profile_id from public.shop_store_members sm join public.shop_order_groups g on g.store_id = sm.store_id where g.id = group_id and sm.status = 'active'
      ) sellers where profile_id is not null
    loop
      perform public.adishop_create_notification_once(
        seller_profile_id,
        'seller_response_reminder',
        'Order expires in less than one hour',
        'Accept or reject this order before its response deadline.',
        jsonb_build_object('orderGroupId', group_id),
        'seller-one-hour:' || group_id
      );
    end loop;
  end loop;

  for group_id in
    select g.id
    from public.shop_order_groups g
    where g.seller_response_status = 'pending'
      and g.fulfillment_status in ('awaiting_seller_confirmation','created')
      and g.seller_response_required_at <= clock_timestamp()
    order by g.seller_response_required_at, g.id
    for update skip locked
    limit greatest(1, least(coalesce(p_limit, 100), 500))
  loop
    perform public.adishop_expire_seller_order_group(group_id, 'automatic');
    processed := processed + 1;
  end loop;
  return jsonb_build_object('ok', true, 'processed', processed, 'checkedAt', clock_timestamp());
end;
$function$;

create or replace function public.adishop_sync_sla_refund_status()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if new.order_group_id is not null and new.reason_code = 'seller_response_expired' then
    update public.shop_order_groups
    set seller_response_refund_status = case
          when new.status = 'completed' then 'refunded'
          when new.status in ('rejected','cancelled','closed') then 'refund_failed'
          when new.status in ('approved','partially_approved','awaiting_return') then 'refund_processing'
          else 'refund_pending'
        end,
        updated_at = clock_timestamp()
    where id = new.order_group_id;
  end if;
  return new;
end;
$function$;

drop trigger if exists shop_refund_requests_sync_sla_status on public.shop_refund_requests;
create trigger shop_refund_requests_sync_sla_status
after insert or update of status on public.shop_refund_requests
for each row execute function public.adishop_sync_sla_refund_status();

-- Harden the existing seller transition transaction with deadline, version and idempotency checks.
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
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity, updated_at = clock_timestamp()
    from public.shop_order_items oi
    where oi.order_group_id = group_record.id and i.product_id = oi.product_id and i.variant_id is not distinct from oi.variant_id;
    update public.shop_order_groups set inventory_released_at = clock_timestamp() where id = group_record.id and inventory_released_at is null;
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

revoke all on function public.adishop_prepare_seller_response_sla() from public, anon, authenticated;
revoke all on function public.adishop_notify_new_seller_order_group() from public, anon, authenticated;
revoke all on function public.adishop_sync_sla_refund_status() from public, anon, authenticated;
revoke all on function public.adishop_create_notification_once(uuid,text,text,text,jsonb,text) from public, anon, authenticated;
revoke all on function public.adishop_refresh_parent_order_status(uuid) from public, anon, authenticated;
revoke all on function public.adishop_prepare_group_payment_reversal(public.shop_order_groups,public.shop_orders,text,text,text) from public, anon, authenticated;
revoke all on function public.adishop_expire_seller_order_group(uuid,text) from public, anon, authenticated;
revoke all on function public.adishop_expire_due_seller_order_groups(integer) from public, anon, authenticated;
revoke all on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) from public, anon, authenticated;

grant execute on function public.adishop_create_notification_once(uuid,text,text,text,jsonb,text) to service_role;
grant execute on function public.adishop_expire_seller_order_group(uuid,text) to service_role;
grant execute on function public.adishop_expire_due_seller_order_groups(integer) to service_role;
grant execute on function public.adishop_transition_seller_order_group(uuid,uuid,text,text,jsonb) to service_role;

do $block$
declare
  existing_job_id bigint;
begin
  select jobid into existing_job_id from cron.job where jobname = 'adishop-expire-seller-response-deadlines';
  if existing_job_id is not null then perform cron.unschedule(existing_job_id); end if;
  perform cron.schedule(
    'adishop-expire-seller-response-deadlines',
    '* * * * *',
    $cron$select public.adishop_expire_due_seller_order_groups(100);$cron$
  );
end;
$block$;
