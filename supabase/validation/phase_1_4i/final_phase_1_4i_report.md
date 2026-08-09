# Phase 1.4I.2 Final Closure Report

Date: 2026-08-10

## Passed

- Phase 1.4I production migrations are present locally and remotely, including `20260809192329_adishop_phase_1_4i_2_exactly_once_seller_response`.
- Seller refund and exchange response functions are ACTIVE with `verify_jwt=false` and internal Shop-session authentication.
- The atomic seller-response RPC is `security invoker` and executable by `service_role` only.
- Seller response mutations now require an expected request version and a stable Android idempotency key.
- Controlled live refund response passed: one response event, one audit record and one buyer notification; duplicate retry deduplicated, stale version rejected and foreign Seller B denied.
- Buyer UI displayed the public response after a scoped rendering fix; Seller B's own Requests screen remained empty for the foreign Hulu Store request.
- Authorized Shop super-admin Refunds displayed the controlled request and single seller response; database readback confirmed one audit and one event.
- Refund/exchange request versions are returned to Android and passed back on response.
- Focused contract tests, full debug unit tests, debug assembly, release Kotlin compilation, Vital Lint, R8 and release assembly passed.
- Fresh debug APK installed on device `RZCT7061S7E`; version remains 1.0.121 (121), process stayed alive, and no launch crash or ANR was observed.
- Release APK was freshly generated and APK Signature Scheme v2 verification passed.

## Secure Realtime Decision

`shop_messages` is already the only Shop table in `supabase_realtime`, and message/conversation tables have RLS enabled. Direct Android Realtime is not enabled because the production Firebase token was rejected by PostgREST and lacks the Supabase-compatible `role=authenticated` claim/integration. Using anon Realtime or weakening RLS would violate account and seller isolation. The existing authenticated Shop-session refresh remains the safe fallback.

## Pending Mandatory Live Gates

- Explicit acceptance of the authenticated 10-second refresh fallback, or production Firebase third-party Auth integration plus a compatible role claim followed by Seller A/B Realtime validation.

## Source Control

- Phase 1.4I.2 source and evidence are intentionally scoped from the larger pre-existing dirty worktree.
- Unrelated Android, desktop, Cloudflare and website changes remain untouched.
- Exactly-once seller-response source is committed as `cbdc7c5`.
- Validation evidence is recorded separately so it does not pull unrelated work into the source commit.

Current gate: `PHASE_1_4I_SELLER_BUSINESS_SUITE_BLOCKED`
