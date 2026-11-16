create unique index if not exists shop_seller_application_submit_once_idx
  on public.shop_seller_application_events(application_id)
  where event_code = 'seller_application.submit';

create unique index if not exists shop_seller_application_submit_audit_once_idx
  on public.shop_audit_logs(entity_id)
  where action = 'seller_application.submit'
    and entity_id is not null;

create unique index if not exists shop_seller_application_submit_notification_once_idx
  on public.shop_notifications(profile_id, ((data ->> 'applicationId')))
  where type = 'seller_application_submitted'
    and data ? 'applicationId';
