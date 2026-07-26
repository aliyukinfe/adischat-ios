-- Phase 1.4G hotfix: delivery-code reveal/verify use Supabase crypto
-- helpers from the extensions schema at runtime. The functions remain
-- service-role only and continue storing only salted hashes, never plaintext.

alter function public.adishop_reveal_delivery_code(uuid, uuid)
  set search_path = public, extensions;

alter function public.adishop_verify_delivery_code(uuid, uuid, text)
  set search_path = public, extensions;
