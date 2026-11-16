# Phase 8B.12 v16-scoped security release review

2026-11-06. **Local release candidate ready for review; production NO-GO.** No production SQL, Edge deployment, account data, migration ledger, Android artifact, commit, or push was changed.

## Baseline and scope

Read-only retrieval from Supabase project `mcewjfnkrtiiolzibsuh` confirmed deployed `adishop-exchange-adischat-session` v16 and `adishop-refresh-shop-session` v16, both with `verify_jwt=false` and the same shared source. The returned source was preserved byte-for-byte under `phase8b12_deployed_v16_baseline/functions/`; it was not derived from Git HEAD. Supabase bundle hashes were `b9d4b08219d4b1117092aec0e55d2f21a37998d074990dc5fdd8e4f9d73f2942` (exchange) and `87f6ee0a21f857955e6b22cc06a7810d2f095ea5839b21370b088c02e9c128c7` (refresh). Local SHA-256: shared `FDD4CDC65672BF502A0B148222D261B0694C25C548CAC2C548F931EAA99340F5`; notification dependency `F21406E7758474E4F043720DFF1CE11496AC03904B878F0B696B0CF5FCA2A096`; exchange wrapper `702BCDEAAF2BF115BD80BE3C54F2544152AA4B16108C14B78712935A4E899850`; refresh wrapper `198B181644EBA38BFF7D18F499F9718D9B078B711407885E0B0C80FD020B9FD4`. The v10 admin-products reference snapshot remains unchanged.

The isolated candidate is `phase8b12_release_candidate/functions/`. Both wrappers and the notification dependency have the baseline hashes. Its shared source SHA-256 is `BAE9AEAEA1BA95CCDC110A17EFF9A8C196FB883F267E155B7F9A456C7299A348`. The exact baseline-to-candidate diff is `phase8b12_v16_scoped_edge.diff`, SHA-256 `7835D8DE0413151EDB04E90F448677A4C6635CE3259850555644C1D07850562E`; `git apply --reverse --check` passed. It has three hunks only: status-preserving exchange, atomic-RPC refresh, and a type-safe cart joined-relation shape guard that preserves the old minimum-quantity fallback. The third hunk is needed for Deno type-check of the retrieved v16 shared source; it is not an authorization or product-behavior change. No Super Admin All Products route is in this auth candidate.

## Version-scoped SQL proposal

These files are review-only under `supabase/validation/`, not active migrations:

| Deployment stage | File | SHA-256 |
| --- | --- | --- |
| 1: deny inactive-to-active profile/link updates | `phase8b12_v16_stage1_status_guards.sql` | `FAD2292F02B89FB75EDEF88ED859E418A6F33BF0629B249AEA87155A486705E4` |
| 2: service-role-only atomic refresh rotation | `phase8b12_v16_stage2_atomic_rotation.sql` | `BAC828402B772E25928F43EB0A027C7BB8CFA7404B0232D35F202758AD095099` |
| 3: session issuance and inactive-profile/link revocation guards | `phase8b12_v16_stage3_revocation_guards.sql` | `D7273957528CDBBEE78F6C3A9257B0C4AF4B8F82BDDDAB2A73A8EE2C57DE3CC7` |

Each stage has its own `BEGIN`/`COMMIT`. Stage 3 is unsafe with deployed two-call refresh and must not be activated first. The rotation RPC locks profile -> identity links -> family -> old session, then consumes the old refresh and inserts its successor in one transaction. A suspended or disabled profile/link remains inactive; there is no automatic reactivation. SQLSTATE `40P01`/`40001` maps to fail-closed HTTP 503, without an unverified automatic retry. The existing status-update path obtains the profile row lock before the revocation trigger updates families/sessions. Family revocation obtains family then session, without first holding a session lock. The proposals are one-time SQL, not an invitation to run `supabase db push` against the drifted ledger.

Read-only production comparison: PostgreSQL 17.6 has the required profile, link, family, and session columns, unique profile/link/refresh-hash indexes, and RLS on those four tables. No proposed RPC/trigger name collision was found. Existing session triggers are the family insert guard and family-revoke-row trigger. The disposable fixture retained RLS on all four tables; the new RPC's `EXECUTE` is false for `anon`/`authenticated` and true for `service_role`. Service-role writes bypass RLS, so the trigger/RPC checks are security-critical. This is a scoped schema preflight, not proof of exact production behavior.

## Administrative revocation audit

- Deployed v16 `revoke-all` requires a signed, replay-checked identity assertion and revokes all currently unrevoked families for the linked profile. It returns failure if the family update fails. It is user-identity logout, not an administrator suspension action. A later valid assertion can create a new family while the profile remains active.
- `revoke` requires a valid refresh token and revokes that token's family. It returns failure on the update error. Family revocation invalidates associated session rows via the existing trigger. Neither path records a reason or authorized admin actor in the same database transaction.
- Admin role assignment/revocation is reached through `currentSession()` and existing role RPCs. The Edge handler derives the actor profile from the verified Shop session; the RPC checks `admin.role.assign`/`admin.role.revoke`, prevents self-assignment, protects the last Super Admin, and writes `shop_admin_audit_logs` in its transaction. Role removal does not revoke all Shop sessions, but the next admin permission/session check denies the removed role. Synthetic race tests cover role removal versus refresh.
- No production public RPC or role permission for administrative profile suspension/reactivation was found in the read-only inventory. Direct service-role status updates would lack an actor-and-reason audit contract. The proposed Stage 3 trigger revokes sessions atomically with a status change but does not create the missing administrative audit record. **This is an outstanding production release blocker**, not a tested feature.
- Separate future reactivation design: Super Admin-only `shop.profile.reactivate` permission; server-derived actor; required reason/case reference; target profile/link lock in the same order; immutable in-transaction audit; no seller self-reactivation; old families stay revoked; fresh verified assertion required after reactivation. Not implemented here. Until such a controlled path exists, Stage 1 deliberately blocks all inactive-to-active updates, including manual admin writes.

## Isolated results and parity

| Check | Result |
| --- | --- |
| Exact candidate HTTP/session/authorization and v16 active-user response-key compatibility | **80/80 PASS** on fresh disposable PostgreSQL 17.11, PostgREST 16.5, Deno 2.9.8, synthetic HMAC issuer; six checks added to the prior 74-check suite |
| Two-connection races, rollback, revocation failures | **50/50 PASS**, rerun this phase; refresh-first/suspend-first, competing refresh, family revocation, link disable, role removal, forced trigger failure |
| Candidate exchange and refresh Deno type-check | **PASS**, exit 0 |
| RLS / RPC grants in disposable fixture | **PASS**, four tables RLS enabled; service-role-only rotation execute |
| Source/bundle isolation | **PASS**, wrappers and notification dependency unchanged; exact three-hunk review diff verified |
| PostgreSQL production minor version | **NOT VERIFIED**: local 17.11 versus production 17.6 |
| Real Firebase/AdisChat issuer, Supabase Auth/gateway, deployed Edge runtime | **NOT VERIFIED**: isolated tests used synthetic issuer and loopback HTTP, not production Auth or gateway parity |
| Actor-audited administrator suspension/revocation | **BLOCKED**: no dedicated production contract found |

The HTTP harness uses one-time synthetic assertion JTIs and a fresh database. An attempted rerun against its already-used database failed at the expected replay fixtures (18 prior rows), then halted; that run is invalid setup evidence, not a passing regression and not a new product failure. The clean-cluster 80/80 run predates only the cart shape-guard hunk; auth logic was unchanged afterward and the exact final candidate type-checked. The race suite was rerun after the final candidate edit. Do not interpret simulated assertion checks as Firebase or Supabase Auth verification.

## Deployment gate and recovery

**NO-GO.** Before separate production approval: establish isolated real Auth/gateway parity, verify PostgreSQL 17.6/production grant and trigger behavior, design and test actor-audited administrative suspension, rerun the 80 HTTP tests against a fresh final-candidate fixture, and inspect deployed bundle hashes again. Confirm older Android clients handle 401/403/503 without a refresh loop.

Proposed order after all gates and separate authorization: fresh read-only preflight; Stage 1; scoped exchange v16 bundle; Stage 2; scoped refresh v16 bundle; Stage 3 only after old two-call refresh is retired. Verify each boundary with read-only/isolated probes. Do not roll back only refresh while Stage 3 stays active: that can restore the documented deadlock. Stage 1 should not be removed while an old exchange version can reactivate profiles. An interrupted rollout should stop, preserve logs/hashes, and keep fail-closed guards until a reviewed compatible bundle is installed. Never restore revoked families/sessions or erase audit history; legitimate users can obtain new sessions after a corrected authorized login. Production 17.6, live gateway, and admin suspension gaps prevent a release recommendation today.
