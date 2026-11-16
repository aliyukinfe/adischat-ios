-- Phase 1.4J.1: deliver the existing, deduplicated SLA notifications through
-- the established AdisChat FCM bridge. Business state remains in PostgreSQL.

create extension if not exists pg_net with schema extensions;

alter table public.shop_notifications
  add column if not exists push_status text not null default 'pending',
  add column if not exists push_attempts integer not null default 0,
  add column if not exists push_sent_at timestamptz,
  add column if not exists push_last_attempt_at timestamptz,
  add column if not exists push_last_error text;

alter table public.shop_notifications
  drop constraint if exists shop_notifications_push_status_check;
alter table public.shop_notifications
  add constraint shop_notifications_push_status_check
  check (push_status in ('pending', 'processing', 'sent', 'failed'));

create index if not exists shop_notifications_push_pending_idx
  on public.shop_notifications (push_status, created_at)
  where push_status in ('pending', 'failed', 'processing');

create or replace function public.adishop_claim_push_notifications(p_limit integer default 50)
returns table (
  notification_id uuid,
  receiver_uid text,
  notification_type text,
  title text,
  body text,
  notification_data jsonb
)
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  return query
  with candidates as (
    select n.id
    from public.shop_notifications n
    where n.type in (
      'seller_confirmation_pending',
      'seller_order_received',
      'seller_response_reminder',
      'seller_response_expired',
      'automatic_refund_initiated'
    )
      and n.push_sent_at is null
      and n.push_attempts < 5
      and (
        n.push_status in ('pending', 'failed')
        or (n.push_status = 'processing' and n.push_last_attempt_at < clock_timestamp() - interval '5 minutes')
      )
    order by n.created_at
    for update skip locked
    limit greatest(1, least(coalesce(p_limit, 50), 100))
  ), claimed as (
    update public.shop_notifications n
    set push_status = 'processing',
        push_attempts = n.push_attempts + 1,
        push_last_attempt_at = clock_timestamp(),
        push_last_error = null
    from candidates c
    where n.id = c.id
    returning n.*
  )
  select
    n.id,
    p.adischat_user_id,
    n.type,
    n.title,
    n.body,
    n.data
  from claimed n
  join public.shop_profiles p on p.id = n.profile_id
  where nullif(trim(coalesce(p.adischat_user_id, '')), '') is not null;
end;
$function$;

create or replace function public.adishop_finish_push_notification(
  p_notification_id uuid,
  p_sent boolean,
  p_error text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  update public.shop_notifications
  set push_status = case when p_sent then 'sent' else 'failed' end,
      push_sent_at = case when p_sent then clock_timestamp() else null end,
      push_last_error = case when p_sent then null else left(coalesce(p_error, 'push-failed'), 160) end
  where id = p_notification_id
    and push_status = 'processing';
end;
$function$;

revoke all on function public.adishop_claim_push_notifications(integer) from public, anon, authenticated;
revoke all on function public.adishop_finish_push_notification(uuid,boolean,text) from public, anon, authenticated;
grant execute on function public.adishop_claim_push_notifications(integer) to service_role;
grant execute on function public.adishop_finish_push_notification(uuid,boolean,text) to service_role;

do $block$
begin
  if exists (select 1 from cron.job where jobname = 'adishop-dispatch-shop-push-notifications') then
    perform cron.unschedule('adishop-dispatch-shop-push-notifications');
  end if;
  perform cron.schedule(
    'adishop-dispatch-shop-push-notifications',
    '* * * * *',
    $cron$
      select net.http_post(
        url := (select decrypted_secret from vault.decrypted_secrets where name = 'adishop_notification_dispatch_url' limit 1),
        headers := jsonb_build_object(
          'content-type', 'application/json',
          'x-adishop-dispatch-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'adishop_notification_dispatch_secret' limit 1)
        ),
        body := '{"limit":50}'::jsonb,
        timeout_milliseconds := 10000
      )
      where exists (select 1 from vault.decrypted_secrets where name = 'adishop_notification_dispatch_url')
        and exists (select 1 from vault.decrypted_secrets where name = 'adishop_notification_dispatch_secret');
    $cron$
  );
end;
$block$;
