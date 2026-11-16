# Phase 8B.10 suspended-account security release plan

Status: **BLOCKED / NO-GO**. Review only, 2026-11-06. No production writes, deployment, migration-ledger change, commit, push, or Android build.

## Scope and source baseline

- Production read-only retrieval confirms `adishop-exchange-adischat-session` v16 and `adishop-refresh-shop-session` v16 both have `verify_jwt=false` and share the same deployed `adishop.ts` (178,879-character source). They use internal HMAC assertion/Shop-token validation. The local 8B.9 candidate shared file is 191,923 bytes and contains unrelated worktree changes; **do not deploy it as a replacement bundle**.
- Required function change: replace only the deployed v16 exchange profile and identity-link unconditional `active` upserts with the 8B.9 status-preserving create/update logic. The deployed v16 `exchange()` body and local baseline body were compared byte-for-byte and matched (2,196 characters). The exact exchange-only review diff is `phase8b10_exchange_v16_review.diff`, SHA-256 `135B9EFCED02A9F95A08777792CBE4C4EC1CA3A31213F29824CA0107B4A27973`; `git apply --check` passed against the local baseline. This is **not** a deployment bundle or a complete security patch. Preserve active-user response shape and existing endpoint URL. Reconstruct a deployed-v16-based scoped bundle and produce a complete file-by-file diff and hash before approval.
- Required transaction change: session issuance, refresh rotation, and suspension revocation need one consistent lock order (profile, identity link, family, session). The 8B.9 SQL draft at SHA-256 `47A319E25ED2A51872DF4BF95329C80C326287AE9BD70359063BF9D413D9E4DA` is **not deployable**. Its session-insert guard covers two issuance/suspension orders, but not refresh/suspension.
- Optional, separate Super Admin enhancement: explicitly authorized account reactivation UI/API. No existing public RPC was found that updates profile/link status for this purpose. The security patch must fail closed for inactive profiles; it must not silently add a reactivation bypass.

## Verified boundaries and gaps

| Boundary | Evidence | Release gap |
| --- | --- | --- |
| AdisChat assertion | `cloudflare/main-worker/src/index.mjs` calls `requireFirebaseUser` or validates desktop session before issuing the Shop assertion. The shared issuer signs HS256 with issuer, audience, subject, JTI, and 10-minute expiry. | Synthetic suite used a nonproduction HMAC issuer, not the live Firebase/desktop verifier. Confirm deployed Worker parity without real suspended-account testing. |
| Edge gateway | Both deployed auth functions use `verify_jwt=false`; deployed handlers verify assertion or refresh token internally. | Real Supabase gateway/Edge runtime was not in the synthetic fixture. [Supabase's Edge auth documentation](https://supabase.com/docs/guides/functions/auth) distinguishes platform JWT checks from handler authorization. |
| Replay and Shop session | Replay JTI is unique; Shop access token is checked against unrevoked session and active joined profile. Family-revoke trigger invalidates sessions. | A disabled identity link is not checked by `currentSession`; reliable revocation on link disable is required. |
| Android compatibility | Existing Android exchange/refresh endpoints and profile/session DTOs can remain unchanged. Existing active buyer/seller/super-admin paths passed the isolated suite. | Test actual Android error handling for 403/401 and no refresh/exchange loop; no Android code changed. |
| Database | Read-only production metadata shows expected tables/columns, active full unique profile/link indexes, RLS enabled, restrictive client policies, and only existing family insert/revoke triggers. | Review exact role grants, trigger ownership, migration ledger drift, account-status transitions, and PostgreSQL 17.6 parity before any SQL. Do not rely on RLS to constrain service-role writes. |

## Critical failed race

Prior 8B.9 synthetic HTTP/SQL suite: **67/67 passed**, including suspension-before-insert and insert-before-suspension; Deno check passed. This does **not** imply all races pass.

An additional two-connection PostgreSQL 17.11 probe reproduced a deadlock:

1. Refresh transaction locked the old `shop_sessions` row by setting `revoked_at`, slept two seconds, then attempted a new insert into the same family.
2. Suspension transaction updated the profile and its trigger tried to revoke the family; the existing family trigger tried to update the old session row.
3. PostgreSQL reported `deadlock detected` and aborted **suspension** (exit 1). Refresh committed (exit 0). Final synthetic state was `status=active`, `active_sessions=1`, `active_families=1`. The disposable database log records the two transaction IDs and both statements. The first attempt used a session ID in place of a profile ID and is discarded; these results are from the corrected probe.

This is a safety failure, not an acceptable transient 500. A retry of suspension could recover, but the patch must not depend on an unverified caller retry. The 8B.9 SQL must not be applied as-is.

## Required revised transaction contract

Prepare and test a narrow database-backed rotation operation that locks the profile (and relevant identity link) **before** family/session rows, verifies active status and family ownership, atomically consumes the old refresh token and inserts its successor, and returns no usable token on failure. The Edge refresh handler may sign/create token material before the RPC but must return it only after the database transaction commits. Concurrent same-token refresh must still yield exactly one success. Session insertion from exchange must use the same profile-first gate. Suspension/link-disable must lock the same profile/link before revoking families and sessions, with audit in the same transaction or in an already reviewed guaranteed audit path. Reject inactive-to-active writes unless an explicit privileged lifecycle operation authorizes them.

Test refresh-first/suspend-first, simultaneous exchange/suspend, link-disable/refresh, family revoke/refresh, two refreshes, and deadlock/statement-timeout behavior with two connections. Assert the suspension commits, all access/refresh tokens are unusable, and prior audit rows remain. Run real HTTP tests over the final scoped deployed-v16 bundles in staging. Until then the revised SQL and complete Edge diff do not exist.

## Separate reactivation design

Require a distinct `shop.profile.reactivate` permission (initially Super Admin only), separate actor and target, reason, case reference, and immutable audit event. A security-definer RPC, if necessary, must have a fixed search path, revoked PUBLIC/anon/authenticated execution, and server-derived actor authorization; never trust caller-supplied role. Lock target profile/link/families in the same order; verify allowed prior state, synchronize only the intended identity link, retain historical revocations and audit, and never resurrect old sessions. Require a fresh verified assertion after reactivation. Seller self-reactivation and role forgery must be denied. This is **not part of the mandatory emergency patch** and is not implemented.

## Deployment sequence once the blocker is repaired and separately approved

1. Fresh read-only preflight: deployed function versions/hashes, schema/trigger/RLS/grants, active/inactive counts, session-family consistency, lock/index behavior, migration history, and rollback artifacts. No personal data in evidence.
2. Build a deployed-v16-based scoped exchange bundle and revised refresh bundle; compare legacy responses and prove only the intended auth paths and required dependencies changed. Validate upstream Worker assertion contract and Android 401/403 handling in isolated staging.
3. Apply only reviewed fail-closed profile/link **reactivation** guards first, then deploy the scoped exchange fix. Existing active exchanges must remain compatible; inactive exchanges through the old handler may temporarily return a generic error but must not reactivate. Verify that boundary before proceeding.
4. Introduce a backward-compatible additive atomic rotation RPC in a separate, checksum-identified SQL change, then deploy the scoped refresh function to use it. Only after the old refresh path is retired, activate the revised session-insert/suspension/link-disable revocation protocol. Do **not** enable the 8B.9 revocation triggers while old refresh still rotates in separate HTTP calls. Re-run all race and HTTP tests after each boundary. Because migration history is already drifted, do not use blind `db push`.
5. Post-check with synthetic or read-only production probes only: missing/malformed assertions rejected, active legitimate buyer/seller exchange and refresh preserved, suspended-status counts unchanged, new session/family invariants, 401/403/500 rates, database deadlock counter, and audit/revocation events. Do not exercise a real suspended account.

## Rollback and emergency containment

- Capture the current v16 function bundles and exact SQL definitions before change. A simple rollback of status guards while old exchange v16 is live would reopen reactivation; it is **not** a safe standalone rollback. Roll back only to another verified secure combination of SQL and functions.
- If a rollout fails, stop further deployment, disable/contain exchange and refresh traffic as operationally approved, and preserve logs. Rotating the Shop-session secret would invalidate all Shop sessions and requires a separate incident decision. Do not silently reinstate old bundles.
- Database rollback must consider sessions already revoked and audit events already written: these cannot be meaningfully "unrevoked". Keep revocation and audit history; recover legitimate users through fresh authentication after a corrected deployment.

## Approval gate

The exchange-only status preservation is a potential emergency mitigation, but it does **not** satisfy the full suspension/revocation objective. The complete patch can ship independently of Super Admin reactivation UI only after the atomic refresh/suspension redesign, staging Auth/gateway parity, scoped bundle hashes/diffs, PostgreSQL 17.6 compatibility, and all race tests pass. Separate explicit SQL and function-deployment approval remains required. Current recommendation: **NO-GO**.
