# Phase 8B.13 final security review

2026-11-06. **Isolated release package ready for final review; production NO-GO.** No production SQL, Edge deployment, data mutation, migration-ledger change, Android release, commit, or push occurred. Production read-only preflight still finds zero `admin.profile.suspend` permissions, zero `adishop_admin_suspend_profile` RPCs, and zero proposed status/revocation triggers.

## Version-scoped package

- Exchange and refresh: preserved deployed v16 sources, isolated candidates, and exact three-hunk diff remain under `phase8b12_deployed_v16_baseline/`, `phase8b12_release_candidate/`, and `phase8b12_v16_scoped_edge.diff`. The deployed exchange/refresh bundle hashes and all source hashes are recorded in `phase8b12_release_review.md`. The final v16 shared candidate SHA-256 remains `BAE9AEAEA1BA95CCDC110A17EFF9A8C196FB883F267E155B7F9A456C7299A348`; this includes the tested cart shape guard.
- Deployed `adishop-admin-revoke-role` **v10** was retrieved read-only; bundle hash `de2522f603f0d6cd70f89b37e6aa9ab2e95901a9d6483b7509a67af499e4400e`. Its exact retrieved wrapper and shared source are preserved under `phase8b13_admin_revoke_v10_baseline/`. Baseline shared SHA-256 `8FA84F8196A717F643EF9B736171B0A95152DCD69CA33CB0BFF93F32A20D0A0E`; wrapper `B886A220E5EEA6CAD68F307A76D220AF214B31395588AC67B571BF03FBE14E75`.
- The separate admin v10 candidate is `phase8b13_admin_revoke_candidate/`. Its wrapper is unchanged; shared SHA-256 `7BA3869CC22A37274D6ED2DDF6FA12425597A7119798372DB08E896450BDFE47`. The exact baseline-to-candidate diff `phase8b13_admin_v10_scoped_edge.diff` is `F35474A04EE90557FBDCA45EAA74EE052B2AEF61C175E6EE3707AC394B2F9377`; reverse `git apply --check` passed. It adds a fail-closed active-profile shape guard (removing two pre-existing v10 Deno cast errors), passes the verified Shop token's JTI to the new RPC, and adds `action=suspend-profile` under the existing revoke-role URL. Legacy requests without that action retain their handler path and response contract.
- SQL stages 1-3 remain checksum-identified in `phase8b12_release_review.md`. The additive review-only Stage 4 SQL is `phase8b13_v16_stage4_admin_suspension.sql`, SHA-256 `EA5A60E8D2C5AE73AED42F540EC6076C972978133356D24AA329984D36356049`. It is under `supabase/validation/`, **not** an active migration. `phase8b13_synthetic_admin_audit_overlay.sql` and `phase8b13_synthetic_legacy_admin_rpc.sql` are disposable fixture-only files; never deploy them.

## Suspension contract and audit

The Stage 4 RPC is `SECURITY INVOKER` and executable only by `service_role`; `anon` and `authenticated` are denied. The Edge adapter validates the signed Shop access token and active joined profile, derives actor profile and JTI server-side, and never accepts a client-supplied actor. Inside the RPC, the actor's Shop session, unrevoked family, active profile/link, active Super Admin assignment, and dedicated `admin.profile.suspend` permission are rechecked. Self-suspension, Super Admin targets, missing/expired/revoked sessions, invalid targets, short reasons, and repeated transitions fail closed.

The RPC locks the actor profile `FOR SHARE`, then target profile `FOR UPDATE`, then actor links and role rows `FOR SHARE`, then actor family and session `FOR UPDATE` separately. This remains profile-first. The actor `FOR SHARE` lock is essential: an initial `FOR UPDATE` draft deadlocked with the existing role-revocation RPC's role-row-then-audit-FK sequence, allowing suspension while role revocation aborted. The corrected interleaving commits role revocation and denies suspension. A successful action changes the target to suspended, revokes all active families/sessions in the same transaction, and inserts one `shop_admin_audit_logs` row with actor, target, prior/new status, reason, and timestamp. Forced audit or revocation failure rolls back all three effects. No reactivation action is included; the 8B.12 status guard continues to block inactive-to-active writes.

The new permission is assigned only to `SHOP_SUPER_ADMIN`; the SQL verifies that role exists before proceeding. Product/customer records and existing role grants are not rewritten. Production read-only schema review found the required audit columns, role-permission uniqueness, profile/session FKs, and PostgreSQL 17.6, but a fresh predeployment comparison is still mandatory.

## Fresh isolated evidence

Fresh SCRAM PostgreSQL **17.11** cluster, bound to `127.0.0.1:55497`, with synthetic users and no production credentials. Loaded the existing 8B.7/8B.8 fixture, existing family revocation migration, 8B.12 Stages 1-3, Stage 4, and fixture-only audit/legacy-role overlays. Local PostgREST **16.5** and Deno **2.9.8** served real HTTP to the exact candidates.

| Test | Result |
| --- | --- |
| Fresh final v16 HTTP/session/authorization/rotation and v10 admin-products compatibility | **80/80 PASS** |
| Two-connection refresh, suspension, family, link, role, and forced-failure races | **50/50 PASS** |
| Admin suspension SQL permission, state, audit, rollback, role-revocation race | **45/45 PASS** after correction |
| New admin HTTP adapter, denied roles, legacy v10 revoke-role response parity | **16/16 PASS** |
| Focused RLS, RPC grants, permission scope, anonymous isolation, trigger assertions | **6/6 PASS** |
| Deno type-check of all three exact candidate entry points | **PASS**, exit 0 |

The first Stage 4 draft failed 4 new role-revocation interleaving assertions (deadlock and aborted revocation). It was corrected and **all suites were rerun on a newly initialized cluster**. That failed draft is not the final candidate. The 8B.13 harnesses use fresh synthetic identities; the 80-check suite ran once against the fresh final database, not against consumed replay JTIs. RLS and service-role behavior were checked separately. These results are local PostgreSQL/PostgREST/Edge evidence, not a claim of production Auth parity.

## Auth, gateway, version, and release decision

- Production `server_version` is **17.6** (project inventory build `17.6.1.147`); the isolated cluster is **17.11**. The relevant SQL, locks, triggers, grants, and role checks pass on 17.11, but patch-level planner/locking and Supabase-specific settings on 17.6 were not reproduced.
- The upstream AdisChat/Firebase assertion verifier and Supabase Auth/API gateway were not run in this disposable stack. The local issuer is synthetic HMAC; `verify_jwt=false` is the deployed metadata, and handler-side validation was exercised, but real gateway/session-family parity is **NOT VERIFIED**. The connected project inventory has no dedicated AdisGebeya staging project. Inactive projects for other applications are not safe substitutes. A separate nonproduction Supabase environment with independent Auth, gateway, Storage, secrets, and synthetic identities is the smallest credible remaining gate; provisioning requires separate authorization.
- The admin `action=suspend-profile` adapter is local only. No Android/Admin UI action was enabled. Legacy v10 revoke-role HTTP response shape was compared using the production RPC definition reproduced in the disposable database; other production external callers were not exercised.
- **NO-GO for production deployment and Phase 9 implementation rollout.** Phase 9 planning can proceed without enabling this patch, but production security closure requires staging Auth/gateway tests, a fresh PostgreSQL 17.6/deployed-schema preflight, external-client compatibility review, and separate approval of every checksum-identified SQL stage and the three specific Edge bundles.

## Deployment and recovery plan (not executed)

1. Capture fresh deployed versions, bundle hashes, function definitions, RLS/grants, active-session counts, and a recovery snapshot. Verify all reviewed file hashes and staging evidence. Do not use blind `supabase db push` while migration history is drifted.
2. Apply Stage 1 status guards; deploy the **v16 exchange-only** scoped bundle; verify active exchange and inactive denial. Old exchange must not remain usable after removing Stage 1.
3. Apply Stage 2 rotation RPC; deploy the **v16 refresh-only** scoped bundle; verify one-success token rotation, revoked-family denial, and legacy response shape. Then apply Stage 3 session/revocation triggers. Never activate Stage 3 while old two-call refresh still serves traffic.
4. Apply checksum-identified Stage 4 permission/RPC; deploy only the **v10 admin-revoke-role** scoped bundle. Verify missing/invalid credentials, Super Admin-only action, audit, revocation, legacy role revocation, and no stale sessions. No automatic reactivation.
5. Monitor denial/500/503 rates, deadlock SQLSTATE, revocation and audit counts, and legacy route behavior. Stop on any mismatch. Roll back the admin adapter first to its preserved v10 bundle, leaving the service-role-only RPC inert; do not delete audit history. If refresh must revert, remove/disable Stage 3 under a reviewed transaction **before** restoring old refresh, or the known deadlock returns. Never resurrect revoked sessions/families. Keep Stage 1 until a status-preserving exchange path is active. Require fresh authorized login after recovery.
