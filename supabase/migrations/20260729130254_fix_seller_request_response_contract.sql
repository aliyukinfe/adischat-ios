alter table public.shop_refund_requests
  add column if not exists seller_response text;

create unique index if not exists shop_refund_events_seller_request_idx
  on public.shop_refund_events(actor_profile_id, request_id)
  where actor_role = 'SELLER' and request_id is not null;

create unique index if not exists shop_exchange_events_seller_request_idx
  on public.shop_exchange_events(actor_profile_id, request_id)
  where actor_role = 'SELLER' and request_id is not null;

revoke all on function public.adishop_buyer_receipt(uuid, uuid) from public, anon, authenticated;
grant execute on function public.adishop_buyer_receipt(uuid, uuid) to service_role;

revoke all on function public.release_expired_shop_checkout_reservations() from public, anon, authenticated;
grant execute on function public.release_expired_shop_checkout_reservations() to service_role;
