alter table public.shop_reviews
  add column if not exists last_moderation_action text,
  add column if not exists last_moderation_previous_status text;

alter table public.shop_store_reviews
  add column if not exists last_moderation_action text,
  add column if not exists last_moderation_previous_status text;

create unique index if not exists shop_review_moderation_request_once_idx
  on public.shop_review_moderation_actions(admin_profile_id, request_id)
  where request_id is not null;

create unique index if not exists shop_review_events_admin_request_once_idx
  on public.shop_review_events(actor_profile_id, request_id)
  where actor_role = 'ADMIN' and request_id is not null;

create unique index if not exists shop_admin_review_audit_once_idx
  on public.shop_audit_logs(action, ((metadata ->> 'requestId')))
  where action in (
    'admin_review.publish',
    'admin_review.hide',
    'admin_review.reject',
    'admin_review.remove'
  )
  and coalesce(metadata ->> 'requestId', '') <> '';
