-- AG-Admin A3.2: Forward-only RPC containment migration for AdisGebeya product moderation.
-- Preserves existing AG-R5.1B containment rules by ensuring adishop_admin_review_product
-- is never callable by public, anon, or authenticated clients directly via PostgREST.
-- Execution is strictly granted to service_role, used exclusively by Edge Functions.

revoke all on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) from public;
revoke execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) from anon;
revoke execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) from authenticated;
grant execute on function public.adishop_admin_review_product(uuid, uuid, text, text, text, text, text) to service_role;
