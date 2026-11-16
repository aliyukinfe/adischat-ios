alter table public.shop_refund_requests
  add column if not exists last_admin_request_id text,
  add column if not exists last_admin_action text,
  add column if not exists last_admin_previous_status text;

alter table public.shop_exchange_requests
  add column if not exists last_admin_request_id text,
  add column if not exists last_admin_action text,
  add column if not exists last_admin_previous_status text;

create unique index if not exists shop_refund_events_admin_request_idx
  on public.shop_refund_events(actor_profile_id, request_id)
  where actor_role = 'ADMIN' and request_id is not null;

create unique index if not exists shop_exchange_events_admin_request_idx
  on public.shop_exchange_events(actor_profile_id, request_id)
  where actor_role = 'ADMIN' and request_id is not null;

create unique index if not exists shop_admin_request_audit_once_idx
  on public.shop_audit_logs(action, ((metadata ->> 'requestId')))
  where action in (
    'admin_refund.review',
    'admin_refund.request_buyer',
    'admin_refund.request_seller',
    'admin_refund.approve',
    'admin_refund.partial_approve',
    'admin_refund.reject',
    'admin_refund.duplicate',
    'admin_refund.complete',
    'admin_refund.close',
    'admin_exchange.review',
    'admin_exchange.request_buyer',
    'admin_exchange.request_seller',
    'admin_exchange.approve',
    'admin_exchange.reject',
    'admin_exchange.awaiting_return',
    'admin_exchange.processing',
    'admin_exchange.shipped',
    'admin_exchange.complete',
    'admin_exchange.cancel',
    'admin_exchange.close'
  )
  and coalesce(metadata ->> 'requestId', '') <> '';

create unique index if not exists shop_admin_request_notification_once_idx
  on public.shop_notifications(profile_id, type, ((data ->> 'mutationId')))
  where type in ('refund_request_update', 'exchange_request_update')
    and coalesce(data ->> 'mutationId', '') <> '';
