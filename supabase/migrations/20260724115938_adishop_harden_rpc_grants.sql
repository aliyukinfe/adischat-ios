-- Harden direct RPC execution for AdisGebeya protected helper functions.
-- Edge Functions call these through the server-side service role after internal
-- Shop-session validation; clients must not execute them directly.

revoke all on function public.adishop_create_notification(uuid, text, text, text, jsonb) from public;
revoke execute on function public.adishop_create_notification(uuid, text, text, text, jsonb) from anon;
revoke execute on function public.adishop_create_notification(uuid, text, text, text, jsonb) from authenticated;
grant execute on function public.adishop_create_notification(uuid, text, text, text, jsonb) to service_role;

revoke all on function public.adishop_customer_can_read_order(uuid, uuid) from public;
revoke execute on function public.adishop_customer_can_read_order(uuid, uuid) from anon;
revoke execute on function public.adishop_customer_can_read_order(uuid, uuid) from authenticated;
grant execute on function public.adishop_customer_can_read_order(uuid, uuid) to service_role;

revoke all on function public.adishop_order_public_payload(uuid) from public;
revoke execute on function public.adishop_order_public_payload(uuid) from anon;
revoke execute on function public.adishop_order_public_payload(uuid) from authenticated;
grant execute on function public.adishop_order_public_payload(uuid) to service_role;

revoke all on function public.adishop_profile_can_fulfill_store(uuid, uuid) from public;
revoke execute on function public.adishop_profile_can_fulfill_store(uuid, uuid) from anon;
revoke execute on function public.adishop_profile_can_fulfill_store(uuid, uuid) from authenticated;
grant execute on function public.adishop_profile_can_fulfill_store(uuid, uuid) to service_role;
