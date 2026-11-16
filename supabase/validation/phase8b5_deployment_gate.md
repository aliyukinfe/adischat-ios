# Phase 8B.5 candidate and parity gate (2026-11-06)

**Review ready; deployment NO-GO.** The intended candidate is `phase8b2_scoped_bundle/supabase/functions/`, not merely the Phase 8B.4 test copy. The deployed-v10 comparison snapshot in `phase8b2_v10_rollback` was not changed. No production SQL, Edge deployment, migration-ledger change, Android flag, commit, or push occurred.

## Exact local changes

- `supabase/functions/_shared/adishop.ts`: replaced the `currentSession` profile cast with a matching-active-object guard; replaced the unrelated `updateCartItem` product-join cast with a checked minimum-quantity helper. A malformed product relation now returns a retryable 503 instead of silently defaulting to quantity one. The worktree file had extensive pre-existing unrelated edits; none were reverted or refactored.
- `supabase/validation/phase8b2_scoped_bundle/supabase/functions/_shared/adishop.ts`: applied only the already-tested profile guard to the **actual** three-file candidate. The wrapper and advanced handler remain byte-identical to Phase 8B.4. Shared-file SHA-256: `7FECA8D21941D66FD7709D7048CB62C68F3683ABA7EB531719AB83EA35B59FCB`, identical to the guarded copy that passed Phase 8B.4 HTTP testing. Wrapper SHA-256: `C974A5A4BCF756DE8E4B5FA019FED7D7815584D42C0EAFDF81E4966F797541ED`; advanced handler: `092F8A0513317D071E47E7B8594BA1A2AA894009C3ED56D5F96B00E436FD558F`.
- `supabase/validation/phase8b4_profile_guard_test.ts`: now imports the actual candidate. `supabase/validation/phase8b5_cart_relation_test.ts`: focused cart shape/quantity tests.

## Type-check and regression

| Check | Result |
| --- | --- |
| Main worktree `_shared/adishop.ts` Deno 2.9.8 check | PASS, exit 0; prior line-434 cast error removed. |
| Actual scoped candidate entry-point Deno check | PASS, exit 0. |
| Corrected v10 comparison copy Deno check | PASS, exit 0. |
| Preserved original v10 baseline Deno check | Expected pre-existing 2 TS2352 errors; unchanged for provenance. |
| Actual-candidate profile guard unit tests | 9 PASS, 0 FAIL. |
| Main-worktree cart relation unit tests | 8 PASS, 0 FAIL. |
| Phase 8B.4 synthetic HTTP suite | 48 PASS on PostgreSQL 18.6; guarded candidate source was byte-identical to the now-updated actual candidate. This suite was not rerun on PostgreSQL 17. |
| Phase 8B.4 concurrency/performance | 20 simultaneous requests had expected outcomes; 5 warm-cache timing samples per query. Not production-scale evidence. |

## Production-versus-fixture parity

Read-only production metadata reports **PostgreSQL 17.6**, with `pgcrypto` 1.3, `plpgsql` 1.0, and `uuid-ossp` 1.1 among queried relevant extensions. Local runtime is PostgreSQL 18.6, PostgREST 16.5, Deno 2.9.8. Only PostgreSQL 18 binaries are installed under `C:\PostgreSQL`; the retained incomplete PostgreSQL 17 distribution lacks `share/postgres.bki`. No large download was attempted.

| Contract | Production observation | Fixture gap |
| --- | --- | --- |
| Tables/columns | Product lifecycle, category/brand, inventory aggregate, seller SKU, specs, publication, image ownership, and session family/refresh fields exist. | Only queried subset and synthetic rows; no full lifecycle or storage metadata. |
| Constraints/FKs | Product/store/category and media/variant/store links, session family FK, image/variant uniqueness. | Minimal FKs; many production checks and uniqueness rules absent. |
| Indexes | Public/category/moderation/store-status, variant SKU, session JTI/active-session indexes. | Three simplified secondary indexes; planner and search cost differ. Production has no `pg_trgm` among queried extensions, so `%text%` search may scan. |
| RLS/grants | RLS enabled on all relevant tables; product public-read predicate requires approved active product and active approved store. Broad anon/auth grants on many tables are constrained by RLS; sessions are service-role-only. | RLS enabled, but only service-role SELECT granted and no equivalent public policies. Service-role handler path tested; public-RLS parity not tested. |
| Auth/session | Real Firebase-to-Shop bridge, session expiry/revocation/family, role assignment, and security-definer admin RPC. | Same handler/JWT verification logic under synthetic secrets and simplified session/RPC fixture; no real Firebase, Supabase gateway, or production Auth. |
| Store/role authorization | `product.moderation.read` is checked server-side; production RPC/role and store contracts include additional policy/audit fields. | Synthetic roles and stores cover denial/allow decisions, not complete production policy or two-store lifecycle. |
| Query/pagination | Candidate uses exact counts, allowlisted sort, bounded limit/offset, deterministic tie-breaker. | Returned rows/counts passed locally, but no snapshot cursor; inserts can shift offset pages. Production 17.6 planner, indexes, data distribution, and timeout behavior untested. |

## Recommendation

**NO-GO for production deployment.** The smallest next environment is an isolated PostgreSQL 17.x cluster with a complete verified portable distribution (ideally 17.6), the production-relevant schema/RLS/indexes, synthetic identities, and the existing PostgREST/Deno HTTP harness. Alternatively use a separately authorized isolated Supabase staging project with independent Auth/Storage. Do not use the production project as a test fixture. Re-run the 48-check suite, malformed-input and concurrency checks, repeated performance measurements, and corrected-v10 versus candidate compatibility there. No PostgreSQL 17 or complete production Auth/RLS compatibility is claimed from PostgreSQL 18 results.
