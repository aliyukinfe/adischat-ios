# Phase 1.4I.3 Final Closure Report

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
- Seller Messages now refreshes immediately on foreground entry and every 10 seconds only while the active conversation lifecycle is started.
- Backgrounding or leaving the conversation cancels the refresh coroutine; overlapping requests and stale-account responses are suppressed.
- A controlled protected seller message was acknowledged and displayed immediately, and database readback remained exactly one row after refresh.
- Missing and malformed Shop sessions returned HTTP 401 without exposing backend internals.
- The private signed attachment path, MIME/size validation, sender idempotency and conversation ownership checks remain intact.

## Secure Realtime Decision

Supabase Realtime was evaluated but was not enabled because the current production Firebase identity tokens are not directly compatible with the required Supabase authenticated Realtime/RLS authorization model. RLS was not weakened. Phase 1.4I uses a validated authenticated 10-second refresh transport for Seller Messages. Realtime remains a future transport enhancement and is not required for correctness of the Seller Business Suite.

## Transport Acceptance

- The authenticated refresh transport is accepted for Phase 1.4I.
- Supabase Realtime is recorded as `FUTURE ENHANCEMENT - NON-BLOCKING`.

## Source Control

- Phase 1.4I.2 source and evidence are intentionally scoped from the larger pre-existing dirty worktree.
- Unrelated Android, desktop, Cloudflare and website changes remain untouched.
- Exactly-once seller-response source is committed as `cbdc7c5`.
- Authenticated Seller Messages transport and lifecycle hardening is committed as `f23973a`.
- Validation evidence is recorded separately so it does not pull unrelated work into the source commit.

Current gate: `PHASE_1_4I_SELLER_BUSINESS_SUITE_PASSED`
