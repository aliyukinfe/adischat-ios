alter table public.shop_notifications
  add column if not exists recipient_context text,
  add column if not exists store_id uuid;

alter table public.shop_notifications
  add constraint shop_notifications_recipient_context_check
  check (recipient_context in ('buyer', 'seller') or recipient_context is null);

create index if not exists shop_notifications_scoped_unread_idx
  on public.shop_notifications (profile_id, recipient_context, store_id, created_at desc)
  where read_at is null;

create or replace function public.adishop_scope_notification()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  target_id uuid;
  resolved_store uuid;
  customer_id uuid;
  issue_buyer_id uuid;
  issue_seller_id uuid;
  buyer_id uuid;
  seller_id uuid;
  scope text;
begin
-- Existing RPCs emit structured types and entity IDs. Unknown events remain unclassified.
  if new.data->>'notificationContext' in ('buyer', 'seller') then
    scope := new.data->>'notificationContext';
  end if;
  if new.type in ('order_placed', 'order_cancelled', 'seller_accepted', 'seller_rejected',
      'seller_processing', 'seller_packed', 'handed_to_carrier', 'delivery_confirmed',
      'seller_confirmation_pending', 'automatic_refund_initiated',
      'refund_seller_response', 'ready_for_pickup',
      'shipped', 'out_for_delivery') then
    scope := 'buyer';
  elsif new.type in ('seller_order_received', 'product_submitted', 'review_received',
      'store_review_received', 'refund_request_received', 'exchange_request_received',
      'buyer_issue_reported') or new.type like 'product_moderation_%'
      or new.type like 'store_%' or new.type like 'seller_application_%' then
    scope := 'seller';
  elsif new.type in ('seller_response_reminder', 'seller_response_expired') then
    if new.data->>'dedupeKey' like 'buyer-%' then scope := 'buyer';
    elsif new.data->>'dedupeKey' like 'seller-%' then scope := 'seller';
    end if;
  end if;

  if new.type = 'shop_message' and new.data->>'conversationId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    select c.customer_profile_id, c.store_id into customer_id, resolved_store
    from public.shop_conversations c where c.id = (new.data->>'conversationId')::uuid;
    if customer_id = new.profile_id then
      scope := 'buyer';
      resolved_store := null;
    elsif resolved_store is not null and exists (
      select 1 from public.shop_stores s where s.id = resolved_store and s.owner_profile_id = new.profile_id
      union all
      select 1 from public.shop_store_members m where m.store_id = resolved_store and m.profile_id = new.profile_id
    ) then scope := 'seller';
    else scope := null; resolved_store := null;
    end if;
  end if;

  if new.type = 'buyer_issue_status_changed'
      and new.data->>'issueId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    select i.buyer_profile_id, i.seller_profile_id, i.store_id
      into issue_buyer_id, issue_seller_id, resolved_store
    from public.shop_order_issues i where i.id = (new.data->>'issueId')::uuid;
    if new.profile_id = issue_buyer_id and new.profile_id is distinct from issue_seller_id then
      scope := 'buyer'; resolved_store := null;
    elsif new.profile_id = issue_seller_id and new.profile_id is distinct from issue_buyer_id then
      scope := 'seller';
    else scope := null; resolved_store := null;
    end if;
  end if;

  if new.type in ('refund_request_update', 'exchange_request_update')
      and new.data->>'requestId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    target_id := (new.data->>'requestId')::uuid;
    if new.type = 'refund_request_update' then
      select r.buyer_profile_id, r.seller_profile_id, r.store_id
        into buyer_id, seller_id, resolved_store
      from public.shop_refund_requests r where r.id = target_id;
    else
      select r.buyer_profile_id, r.seller_profile_id, r.store_id
        into buyer_id, seller_id, resolved_store
      from public.shop_exchange_requests r where r.id = target_id;
    end if;
    if new.profile_id = buyer_id and new.profile_id is distinct from seller_id then
      scope := 'buyer'; resolved_store := null;
    elsif new.profile_id = seller_id and new.profile_id is distinct from buyer_id then
      scope := 'seller';
    else scope := null; resolved_store := null;
    end if;
  end if;

  if scope = 'seller' and new.type not like 'seller_application_%' and resolved_store is null then
    if new.data->>'storeId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      select id into resolved_store from public.shop_stores where id = (new.data->>'storeId')::uuid;
    elsif new.data->>'orderGroupId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      select store_id into resolved_store from public.shop_order_groups where id = (new.data->>'orderGroupId')::uuid;
    elsif new.data->>'productId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      select store_id into resolved_store from public.shop_products where id = (new.data->>'productId')::uuid;
    elsif new.data->>'refundRequestId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      select store_id into resolved_store from public.shop_refund_requests where id = (new.data->>'refundRequestId')::uuid;
    elsif new.data->>'exchangeRequestId' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      select store_id into resolved_store from public.shop_exchange_requests where id = (new.data->>'exchangeRequestId')::uuid;
    end if;
  end if;

  if scope = 'seller' and new.type not like 'seller_application_%' and resolved_store is null then
    scope := null;
  end if;
  new.recipient_context := scope;
  new.store_id := case when scope = 'seller' then resolved_store else null end;
  return new;
end;
$function$;

revoke all on function public.adishop_scope_notification() from public, anon, authenticated;

drop trigger if exists shop_notifications_scope_insert on public.shop_notifications;
create trigger shop_notifications_scope_insert
before insert on public.shop_notifications
for each row execute function public.adishop_scope_notification();

drop trigger if exists shop_notifications_scope_backfill on public.shop_notifications;
create trigger shop_notifications_scope_backfill
before update of data on public.shop_notifications
for each row execute function public.adishop_scope_notification();

update public.shop_notifications set data = data where recipient_context is null;
