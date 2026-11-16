-- Disposable PostgreSQL 17 fixture. Synthetic only; not a production schema replica.
do $$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname='service_role') then create role service_role nologin; end if;
end $$;
create schema if not exists public;
create table public.shop_orders (
  id uuid primary key, profile_id uuid not null, order_number text unique,
  status text not null, fulfillment_status text not null, delivery_status text not null,
  payment_status text not null default 'unpaid', subtotal_minor integer not null default 0,
  total_minor integer not null default 0, currency text not null default 'ETB',
  created_at timestamptz not null default clock_timestamp(),
  cancellation_requested_at timestamptz, updated_at timestamptz not null default clock_timestamp()
);
create table public.shop_stores (id uuid primary key, owner_profile_id uuid not null);
create table public.shop_order_groups (
  id uuid primary key, order_id uuid not null references public.shop_orders(id),
  store_id uuid not null references public.shop_stores(id),
  status text not null, fulfillment_status text not null, payment_status text not null default 'unpaid',
  subtotal_minor integer not null default 0, store_name_snapshot text,
  seller_response_status text not null default 'pending' check (seller_response_status in ('pending','accepted','rejected','expired')),
  seller_responded_at timestamptz, seller_response_required_at timestamptz,
  seller_response_sla_hours smallint not null default 24 check (seller_response_sla_hours between 1 and 168),
  seller_response_expired_at timestamptz, seller_response_expiration_source text,
  seller_response_request_id text,
  seller_response_version integer not null default 1,
  seller_response_refund_status text not null default 'not_required'
    check (seller_response_refund_status in ('not_required','authorization_void_pending','refund_pending','refund_processing','refunded','refund_failed')),
  seller_response_refund_request_id uuid, inventory_released_at timestamptz,
  updated_at timestamptz not null default clock_timestamp()
);
create table public.shop_order_items (
  id uuid primary key default gen_random_uuid(), order_group_id uuid not null references public.shop_order_groups(id),
  product_id uuid not null, variant_id uuid, quantity integer not null check (quantity > 0)
);
create table public.shop_inventory (
  product_id uuid not null, variant_id uuid, quantity_available integer not null,
  updated_at timestamptz not null default clock_timestamp()
);
create unique index shop_inventory_product_variant_unique on public.shop_inventory(product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid));
create table public.shop_payments (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  profile_id uuid not null, provider text not null, method_code text not null,
  status text not null, amount_minor integer not null, created_at timestamptz not null default clock_timestamp()
);
create table public.shop_payment_events (
  id uuid primary key default gen_random_uuid(), payment_id uuid not null references public.shop_payments(id),
  provider text not null, provider_event_id text not null, event_type text not null, payload jsonb not null,
  unique(provider, provider_event_id)
);
create table public.shop_refund_requests (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  order_group_id uuid not null references public.shop_order_groups(id), buyer_profile_id uuid not null,
  store_id uuid not null, seller_profile_id uuid, reason_code text not null,
  description text not null, requested_amount integer not null, currency text not null,
  status text not null, buyer_visible_update text, idempotency_key text not null,
  updated_at timestamptz not null default clock_timestamp(), unique(buyer_profile_id,idempotency_key)
);
create table public.shop_order_cancellations (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  requested_by uuid not null, reason_code text not null, reason_text text, status text not null, resolved_at timestamptz
);
create table public.shop_order_status_history (
  id uuid primary key default gen_random_uuid(), order_id uuid not null references public.shop_orders(id),
  status text not null, actor_profile_id uuid, actor_role text, previous_status text, next_status text,
  event_code text, public_message text, internal_message text, reason text, occurred_at timestamptz
);
create table public.shop_notifications (
  id uuid primary key default gen_random_uuid(), profile_id uuid not null, type text not null,
  title text not null, body text not null, data jsonb not null
);
create table public.shop_audit_logs (
  id uuid primary key default gen_random_uuid(), actor_profile_id uuid, action text not null,
  entity_type text not null, entity_id uuid not null, metadata jsonb not null
);

create function public.adishop_order_public_payload(p_order_id uuid) returns jsonb
language sql stable as $$ select to_jsonb(o) from public.shop_orders o where o.id = p_order_id $$;
create function public.adishop_create_notification(p_profile_id uuid,p_type text,p_title text,p_body text,p_data jsonb)
returns void language sql as $$
  insert into public.shop_notifications(profile_id,type,title,body,data)
  values (p_profile_id,p_type,p_title,p_body,p_data);
$$;
create function public.adishop_prepare_group_payment_reversal(
  p_group public.shop_order_groups, p_order public.shop_orders,
  p_reason_code text, p_description text, p_dedupe_prefix text
) returns jsonb language plpgsql as $$
declare
  payment_record public.shop_payments%rowtype;
  refund_request_id uuid;
  refund_state text := 'not_required';
  request_key text := p_dedupe_prefix || ':' || p_group.id;
begin
  select * into payment_record from public.shop_payments
  where order_id = p_order.id order by created_at desc limit 1 for update;
  if not found or payment_record.status in ('unpaid','payment_pending','pending','failed','expired') then
    refund_state := 'not_required';
  elsif payment_record.status in ('authorized','authorization_pending') then
    refund_state := 'authorization_void_pending';
    insert into public.shop_payment_events(payment_id,provider,provider_event_id,event_type,payload)
    values(payment_record.id,payment_record.provider,request_key,'authorization_void_requested',
      jsonb_build_object('orderId',p_order.id,'orderGroupId',p_group.id))
    on conflict(provider,provider_event_id) do nothing;
  elsif payment_record.status in ('paid','partially_refunded') then
    refund_state := 'refund_pending';
    insert into public.shop_refund_requests(
      order_id,order_group_id,buyer_profile_id,store_id,seller_profile_id,
      reason_code,description,requested_amount,currency,status,buyer_visible_update,idempotency_key
    ) values(p_order.id,p_group.id,p_order.profile_id,p_group.store_id,
      (select owner_profile_id from public.shop_stores where id=p_group.store_id),
      p_reason_code,p_description,p_group.subtotal_minor,p_order.currency,'submitted',
      'Refund pending provider confirmation.',request_key)
    on conflict(buyer_profile_id,idempotency_key) do update
      set updated_at=public.shop_refund_requests.updated_at returning id into refund_request_id;
    insert into public.shop_payment_events(payment_id,provider,provider_event_id,event_type,payload)
    values(payment_record.id,payment_record.provider,request_key,'refund_requested',
      jsonb_build_object('orderId',p_order.id,'orderGroupId',p_group.id,'refundRequestId',refund_request_id))
    on conflict(provider,provider_event_id) do nothing;
  end if;
  return jsonb_build_object('status',refund_state,'refundRequestId',refund_request_id);
end;
$$;

insert into public.shop_stores(id,owner_profile_id)
values ('10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001');
insert into public.shop_inventory(product_id,variant_id,quantity_available)
values ('30000000-0000-4000-8000-000000000001',null,9);
