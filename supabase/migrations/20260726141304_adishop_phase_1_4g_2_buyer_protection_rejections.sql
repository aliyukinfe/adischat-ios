alter table public.shop_order_groups
  add column if not exists rejected_reason_code text,
  add column if not exists buyer_visible_rejection_reason text,
  add column if not exists rejected_request_id text;

create table if not exists public.shop_order_issues (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.shop_orders(id) on delete cascade,
  order_group_id uuid references public.shop_order_groups(id) on delete set null,
  buyer_profile_id uuid not null references public.shop_profiles(id) on delete cascade,
  store_id uuid references public.shop_stores(id) on delete set null,
  seller_profile_id uuid references public.shop_profiles(id) on delete set null,
  reason_code text not null check (reason_code in (
    'item_not_received',
    'wrong_item',
    'damaged_item',
    'incomplete_order',
    'not_as_described',
    'seller_issue',
    'delivery_issue',
    'other'
  )),
  description text not null default '',
  status text not null default 'open' check (status in (
    'open',
    'under_review',
    'awaiting_buyer',
    'awaiting_seller',
    'resolved',
    'rejected',
    'closed'
  )),
  resolution_code text,
  buyer_visible_resolution text,
  idempotency_key text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz
);

create unique index if not exists shop_order_issues_buyer_idempotency_idx
  on public.shop_order_issues(buyer_profile_id, idempotency_key);

create index if not exists shop_order_issues_order_idx
  on public.shop_order_issues(order_id, created_at desc);

create index if not exists shop_order_issues_store_idx
  on public.shop_order_issues(store_id, status, created_at desc);

create unique index if not exists shop_order_issues_one_active_group_reason_idx
  on public.shop_order_issues(buyer_profile_id, order_id, coalesce(order_group_id, '00000000-0000-0000-0000-000000000000'::uuid), reason_code)
  where status in ('open','under_review','awaiting_buyer','awaiting_seller');

create table if not exists public.shop_order_issue_events (
  id uuid primary key default gen_random_uuid(),
  issue_id uuid not null references public.shop_order_issues(id) on delete cascade,
  actor_profile_id uuid references public.shop_profiles(id) on delete set null,
  actor_role text not null check (actor_role in ('BUYER','SELLER','ADMIN','SYSTEM')),
  event_code text not null,
  previous_status text,
  next_status text,
  public_message text,
  private_note text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists shop_order_issue_events_issue_idx
  on public.shop_order_issue_events(issue_id, created_at asc);

alter table public.shop_order_issues enable row level security;
alter table public.shop_order_issue_events enable row level security;

drop policy if exists "shop_order_issues_no_client_access" on public.shop_order_issues;
create policy "shop_order_issues_no_client_access" on public.shop_order_issues
  for all
  using (false)
  with check (false);

drop policy if exists "shop_order_issue_events_no_client_access" on public.shop_order_issue_events;
create policy "shop_order_issue_events_no_client_access" on public.shop_order_issue_events
  for all
  using (false)
  with check (false);

create or replace function public.adishop_issue_reason_label(p_reason_code text)
returns text
language sql
stable
as $$
  select case p_reason_code
    when 'item_not_received' then 'Item not received'
    when 'wrong_item' then 'Wrong item received'
    when 'damaged_item' then 'Item arrived damaged'
    when 'incomplete_order' then 'Incomplete order'
    when 'not_as_described' then 'Item not as described'
    when 'seller_issue' then 'Seller issue'
    when 'delivery_issue' then 'Delivery issue'
    when 'other' then 'Other issue'
    else 'Order issue'
  end
$$;

create or replace function public.adishop_rejection_reason_label(p_reason_code text)
returns text
language sql
stable
as $$
  select case p_reason_code
    when 'out_of_stock' then 'Item is out of stock'
    when 'product_unavailable' then 'Item is unavailable'
    when 'unable_to_fulfill' then 'Seller cannot fulfill this order'
    when 'store_closed' then 'Store is temporarily closed'
    when 'invalid_delivery_area' then 'Delivery area is not supported'
    when 'buyer_request' then 'Rejected at buyer request'
    when 'duplicate_order' then 'Duplicate order'
    when 'suspected_fraud' then 'Order could not be verified'
    when 'pricing_or_listing_error' then 'Listing information needs correction'
    when 'other' then 'Seller provided another reason'
    else 'Seller rejected the order'
  end
$$;

create or replace function public.adishop_buyer_order_issue_options(
  p_profile_id uuid,
  p_order_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  order_record public.shop_orders%rowtype;
  state text;
  options jsonb := '[]'::jsonb;
begin
  select * into order_record
  from public.shop_orders
  where id = p_order_id and profile_id = p_profile_id;
  if not found then raise exception 'order-not-found'; end if;

  state := coalesce(order_record.fulfillment_status, order_record.status);
  if state in ('accepted','processing','packed','ready_for_pickup','handed_to_carrier','shipped','out_for_delivery') then
    options := options || jsonb_build_array(
      jsonb_build_object('code','seller_issue','label','Seller issue','requiresDescription',false),
      jsonb_build_object('code','delivery_issue','label','Delivery issue','requiresDescription',false),
      jsonb_build_object('code','other','label','Other issue','requiresDescription',true)
    );
  elsif state in ('delivered','completed') then
    options := options || jsonb_build_array(
      jsonb_build_object('code','item_not_received','label','Item not received','requiresDescription',false),
      jsonb_build_object('code','wrong_item','label','Wrong item received','requiresDescription',false),
      jsonb_build_object('code','damaged_item','label','Item arrived damaged','requiresDescription',false),
      jsonb_build_object('code','incomplete_order','label','Incomplete order','requiresDescription',false),
      jsonb_build_object('code','not_as_described','label','Item not as described','requiresDescription',false),
      jsonb_build_object('code','other','label','Other issue','requiresDescription',true)
    );
  end if;

  return jsonb_build_object(
    'eligible', jsonb_array_length(options) > 0,
    'state', state,
    'options', options,
    'message', case when jsonb_array_length(options) > 0 then 'Support can review this order.' else 'Buyer Protection is not available for this order state.' end
  );
end;
$$;

create or replace function public.adishop_buyer_create_order_issue(
  p_profile_id uuid,
  p_order_id uuid,
  p_order_group_id uuid,
  p_reason_code text,
  p_description text,
  p_idempotency_key text,
  p_request_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  order_record public.shop_orders%rowtype;
  group_record public.shop_order_groups%rowtype;
  option_contract jsonb;
  reason_allowed boolean := false;
  reason_requires_description boolean := false;
  issue_record public.shop_order_issues%rowtype;
  clean_description text;
begin
  clean_description := left(trim(coalesce(p_description, '')), 1200);
  if length(trim(coalesce(p_idempotency_key, ''))) < 8 then raise exception 'invalid-idempotency-key'; end if;
  if length(trim(coalesce(p_request_id, ''))) < 8 then raise exception 'invalid-request-id'; end if;

  select * into order_record from public.shop_orders where id = p_order_id and profile_id = p_profile_id;
  if not found then raise exception 'order-not-found'; end if;

  if p_order_group_id is not null then
    select * into group_record from public.shop_order_groups where id = p_order_group_id and order_id = p_order_id;
    if not found then raise exception 'order-group-not-found'; end if;
  else
    select * into group_record from public.shop_order_groups where order_id = p_order_id order by created_at asc limit 1;
  end if;

  option_contract := public.adishop_buyer_order_issue_options(p_profile_id, p_order_id);
  select true, coalesce((opt.value->>'requiresDescription')::boolean, false)
    into reason_allowed, reason_requires_description
  from jsonb_array_elements(option_contract->'options') as opt(value)
  where opt.value->>'code' = p_reason_code
  limit 1;

  if not coalesce(reason_allowed, false) then raise exception 'invalid-issue-reason'; end if;
  if reason_requires_description and length(clean_description) < 10 then raise exception 'issue-description-required'; end if;

  select * into issue_record
  from public.shop_order_issues
  where buyer_profile_id = p_profile_id and idempotency_key = p_idempotency_key;
  if found then
    return jsonb_build_object('issue', row_to_json(issue_record), 'idempotent', true);
  end if;

  insert into public.shop_order_issues(
    order_id, order_group_id, buyer_profile_id, store_id, seller_profile_id,
    reason_code, description, status, idempotency_key
  )
  values (
    p_order_id, group_record.id, p_profile_id, group_record.store_id,
    (select owner_profile_id from public.shop_stores where id = group_record.store_id),
    p_reason_code, clean_description, 'open', p_idempotency_key
  )
  returning * into issue_record;

  insert into public.shop_order_issue_events(
    issue_id, actor_profile_id, actor_role, event_code, previous_status, next_status, public_message, metadata
  )
  values (
    issue_record.id, p_profile_id, 'BUYER', 'buyer_issue.create', null, 'open',
    'Buyer Protection issue submitted.',
    jsonb_build_object('orderId', p_order_id, 'orderGroupId', group_record.id, 'reasonCode', p_reason_code, 'requestId', p_request_id)
  );

  perform public.adishop_create_notification(
    p_profile_id,
    'buyer_issue_received',
    'Buyer Protection',
    'Your issue was received. Support will review it.',
    jsonb_build_object('orderId', p_order_id, 'issueId', issue_record.id)
  );

  if issue_record.seller_profile_id is not null then
    perform public.adishop_create_notification(
      issue_record.seller_profile_id,
      'buyer_issue_reported',
      'Buyer Protection',
      'A buyer reported an issue with an order.',
      jsonb_build_object('orderId', p_order_id, 'orderGroupId', group_record.id, 'issueId', issue_record.id)
    );
  end if;

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_profile_id,
    'buyer_issue.create',
    'shop_order_issue',
    issue_record.id,
    jsonb_build_object('orderId', p_order_id, 'orderGroupId', group_record.id, 'reasonCode', p_reason_code, 'requestId', p_request_id)
  );

  return jsonb_build_object('issue', row_to_json(issue_record), 'idempotent', false);
end;
$$;

create or replace function public.adishop_buyer_order_issues(
  p_profile_id uuid,
  p_order_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.shop_orders where id = p_order_id and profile_id = p_profile_id) then
    raise exception 'order-not-found';
  end if;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', i.id,
      'order_id', i.order_id,
      'order_group_id', i.order_group_id,
      'reason_code', i.reason_code,
      'reason_label', public.adishop_issue_reason_label(i.reason_code),
      'description', i.description,
      'status', i.status,
      'buyer_visible_resolution', i.buyer_visible_resolution,
      'created_at', i.created_at,
      'updated_at', i.updated_at,
      'events', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', e.id,
          'event_code', e.event_code,
          'previous_status', e.previous_status,
          'next_status', e.next_status,
          'public_message', e.public_message,
          'created_at', e.created_at
        ) order by e.created_at asc)
        from public.shop_order_issue_events e
        where e.issue_id = i.id
          and e.private_note is null
      ), '[]'::jsonb)
    ) order by i.created_at desc)
    from public.shop_order_issues i
    where i.buyer_profile_id = p_profile_id and i.order_id = p_order_id
  ), '[]'::jsonb);
end;
$$;

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
as $$
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
begin
  select * into group_record from public.shop_order_groups where id = p_order_group_id for update;
  if not found then raise exception 'seller-order-not-found'; end if;
  if not public.adishop_profile_can_fulfill_store(p_profile_id, group_record.store_id) then
    raise exception 'seller-order-not-found';
  end if;

  select * into order_record from public.shop_orders where id = group_record.order_id for update;
  previous_status := group_record.fulfillment_status;
  request_id := nullif(trim(coalesce(p_metadata->>'requestId', '')), '');

  if p_action = 'reject' then
    reason_code := nullif(trim(coalesce(p_metadata->>'reasonCode', '')), '');
    if reason_code is null then raise exception 'rejection-reason-required'; end if;
    if reason_code not in ('out_of_stock','product_unavailable','unable_to_fulfill','store_closed','invalid_delivery_area','buyer_request','duplicate_order','suspected_fraud','pricing_or_listing_error','other') then
      raise exception 'invalid-rejection-reason';
    end if;
    if reason_code = 'other' and length(trim(coalesce(p_reason, ''))) < 10 then
      raise exception 'rejection-explanation-required';
    end if;
    buyer_visible_reason := case
      when length(trim(coalesce(p_reason, ''))) >= 3 then left(trim(p_reason), 280)
      else public.adishop_rejection_reason_label(reason_code)
    end;
    if previous_status = 'rejected' and coalesce(group_record.rejected_request_id, '') = coalesce(request_id, '') and request_id is not null then
      return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', 'rejected', 'idempotent', true);
    end if;
  end if;

  if p_action = 'accept' and previous_status in ('awaiting_seller_confirmation','created') then
    next_status := 'accepted'; event_code := 'seller_accepted'; public_message := 'Seller accepted your order.';
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

  update public.shop_order_groups
  set fulfillment_status = next_status,
      status = case
        when next_status = 'accepted' then 'accepted'
        when next_status = 'processing' then 'processing'
        when next_status = 'packed' then 'packed'
        when next_status = 'ready_for_pickup' then 'ready_for_pickup'
        when next_status = 'handed_to_carrier' then 'handed_to_carrier'
        when next_status = 'rejected' then 'rejected'
        else status
      end,
      rejected_reason = case when next_status = 'rejected' then buyer_visible_reason else rejected_reason end,
      rejected_reason_code = case when next_status = 'rejected' then reason_code else rejected_reason_code end,
      buyer_visible_rejection_reason = case when next_status = 'rejected' then buyer_visible_reason else buyer_visible_rejection_reason end,
      rejected_request_id = case when next_status = 'rejected' then request_id else rejected_request_id end,
      shipment_carrier = coalesce(p_metadata->>'carrier', shipment_carrier),
      shipment_tracking_number = coalesce(p_metadata->>'trackingNumber', shipment_tracking_number),
      shipped_at = case when next_status = 'handed_to_carrier' then now() else shipped_at end,
      updated_at = now()
  where id = p_order_group_id;

  if next_status = 'rejected' and group_record.inventory_released_at is null then
    update public.shop_inventory i
    set quantity_available = i.quantity_available + oi.quantity,
        updated_at = now()
    from public.shop_order_items oi
    where oi.order_group_id = group_record.id
      and i.product_id = oi.product_id
      and i.variant_id is not distinct from oi.variant_id;

    update public.shop_order_groups set inventory_released_at = now() where id = group_record.id;
  end if;

  update public.shop_orders
  set fulfillment_status = case
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'processing') then 'processing'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'packed') then 'packed'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'handed_to_carrier') then 'handed_to_carrier'
      when not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status not in ('rejected','cancelled')) then 'rejected'
      when exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.fulfillment_status = 'accepted') then 'accepted'
      else fulfillment_status
    end,
    status = case
      when next_status = 'rejected' and not exists (select 1 from public.shop_order_groups g where g.order_id = order_record.id and g.id <> group_record.id and g.fulfillment_status not in ('rejected','cancelled')) then 'cancelled'
      when next_status in ('processing','packed') then 'processing'
      when next_status = 'handed_to_carrier' then 'shipped'
      else status
    end,
    updated_at = now()
  where id = order_record.id;

  insert into public.shop_order_status_history(
    order_id, order_group_id, status, actor_profile_id, actor_role, previous_status, next_status,
    event_code, public_message, internal_message, reason, metadata, occurred_at
  )
  values (
    order_record.id, group_record.id, next_status, p_profile_id, 'SELLER',
    previous_status, next_status, event_code, public_message,
    case when next_status = 'rejected' then reason_code else p_reason end,
    case when next_status = 'rejected' then buyer_visible_reason else p_reason end,
    coalesce(p_metadata, '{}'::jsonb) || case when next_status = 'rejected' then jsonb_build_object('reasonCode', reason_code, 'requestId', request_id) else '{}'::jsonb end,
    now()
  );

  perform public.adishop_create_notification(
    order_record.profile_id,
    event_code,
    'Order update',
    case when next_status = 'rejected' then 'Your order was rejected: ' || buyer_visible_reason else public_message end,
    jsonb_build_object('orderId', order_record.id, 'orderGroupId', group_record.id, 'reasonCode', reason_code)
  );

  insert into public.shop_audit_logs(actor_profile_id, action, entity_type, entity_id, metadata)
  values (
    p_profile_id,
    'seller_order_group.' || p_action,
    'shop_order_group',
    group_record.id,
    coalesce(p_metadata, '{}'::jsonb) || case when next_status = 'rejected' then jsonb_build_object('previousStatus', previous_status, 'newStatus', next_status, 'reasonCode', reason_code, 'requestId', request_id) else jsonb_build_object('previousStatus', previous_status, 'newStatus', next_status) end
  );

  return jsonb_build_object('ok', true, 'orderGroupId', group_record.id, 'fulfillmentStatus', next_status);
end;
$$;

revoke all on table public.shop_order_issues from anon, authenticated;
revoke all on table public.shop_order_issue_events from anon, authenticated;

revoke all on function public.adishop_buyer_order_issue_options(uuid, uuid) from public;
revoke execute on function public.adishop_buyer_order_issue_options(uuid, uuid) from anon;
revoke execute on function public.adishop_buyer_order_issue_options(uuid, uuid) from authenticated;
grant execute on function public.adishop_buyer_order_issue_options(uuid, uuid) to service_role;

revoke all on function public.adishop_buyer_create_order_issue(uuid, uuid, uuid, text, text, text, text) from public;
revoke execute on function public.adishop_buyer_create_order_issue(uuid, uuid, uuid, text, text, text, text) from anon;
revoke execute on function public.adishop_buyer_create_order_issue(uuid, uuid, uuid, text, text, text, text) from authenticated;
grant execute on function public.adishop_buyer_create_order_issue(uuid, uuid, uuid, text, text, text, text) to service_role;

revoke all on function public.adishop_buyer_order_issues(uuid, uuid) from public;
revoke execute on function public.adishop_buyer_order_issues(uuid, uuid) from anon;
revoke execute on function public.adishop_buyer_order_issues(uuid, uuid) from authenticated;
grant execute on function public.adishop_buyer_order_issues(uuid, uuid) to service_role;

revoke all on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from public;
revoke execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from anon;
revoke execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) from authenticated;
grant execute on function public.adishop_transition_seller_order_group(uuid, uuid, text, text, jsonb) to service_role;
