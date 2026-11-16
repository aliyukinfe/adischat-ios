-- Release expired checkout reservations even when no later checkout request arrives.
create extension if not exists pg_cron;

do $block$
begin
  if exists (
    select 1
    from cron.job
    where jobname = 'adishop-release-expired-checkout-reservations'
  ) then
    perform cron.unschedule('adishop-release-expired-checkout-reservations');
  end if;

  perform cron.schedule(
    'adishop-release-expired-checkout-reservations',
    '* * * * *',
    $cron$select public.release_expired_shop_checkout_reservations();$cron$
  );
end;
$block$;

