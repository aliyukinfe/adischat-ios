-- LOCAL PROPOSAL ONLY. No cron activation or production deployment.
begin;
set local lock_timeout = '5s';

create table if not exists public.shop_seller_response_policy (
  singleton boolean primary key default true check (singleton),
  default_hours smallint not null check (default_hours between 1 and 168),
  updated_at timestamptz not null default clock_timestamp()
);
insert into public.shop_seller_response_policy(singleton,default_hours)
values (true,24) on conflict (singleton) do nothing;
alter table public.shop_seller_response_policy enable row level security;
revoke all on public.shop_seller_response_policy from public, anon, authenticated;
grant select on public.shop_seller_response_policy to service_role;

create or replace function public.adishop_prepare_seller_response_sla()
returns trigger language plpgsql security definer set search_path = public as $function$
declare
  placed_at timestamptz;
  configured_hours smallint;
begin
  if new.fulfillment_status in ('awaiting_seller_confirmation','created')
     and (tg_op = 'INSERT' or old.fulfillment_status not in ('awaiting_seller_confirmation','created')
          or new.seller_response_required_at is null) then
    select o.created_at into placed_at from public.shop_orders o where o.id = new.order_id;
    select p.default_hours into configured_hours from public.shop_seller_response_policy p where p.singleton;
    if placed_at is null or configured_hours is null then raise exception 'seller-response-policy-unavailable'; end if;
    new.seller_response_sla_hours := configured_hours;
    new.seller_response_required_at := placed_at + make_interval(hours => configured_hours);
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
-- Existing production trigger is retained; the fixture creates the same trigger below.
commit;
