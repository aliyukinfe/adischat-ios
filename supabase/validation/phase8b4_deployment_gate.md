# Phase 8B.4 deployment gate (2026-11-06)

**Review ready; production deployment NO-GO.** No production writes, Edge deployment, migration-ledger change, Android flag change, commit, or push occurred. Phase 8B.3 evidence remains unchanged.

## Scoped correction

- Preserved `phase8b2_v10_rollback` and `phase8b2_scoped_bundle` verbatim. Corrected copies are `phase8b4_v10_guarded` and `phase8b4_candidate_guarded`.
- The only candidate source delta is in `_shared/adishop.ts`: `activeSessionProfile` rejects null, arrays, missing/non-string IDs, mismatched IDs, and non-active status; `currentSession` uses the validated profile. Valid active sessions continue with the same response shape. The worktree's main `_shared/adishop.ts` received the same narrow change, but its existing unrelated edits remain untouched.
- Deno 2.9.8 `check` exits 0 for both corrected v10 and corrected candidate entry points. The main worktree `_shared/adishop.ts` still fails on an unrelated pre-existing TS2352 at line 434 (`shop_products` cast). The preserved uncorrected copies still have their original two TS2352 errors.
- Focused profile-guard unit tests: **9 passed, 0 failed** (matching active profile and eight malformed/inactive/mismatched cases).

## Isolated runtime

- Fresh synthetic PostgreSQL 18.6 cluster on `127.0.0.1:55483`, PostgREST 16.5 on `127.0.0.1:55484`, local HTTP proxy on 55485, Deno Edge handler on 8000. SCRAM password was temporary and not committed. Production metadata was queried read-only; no production data was copied into fixtures.
- `phase8b4_http_runtime.cjs`: **48 passed checks**. This includes 5 corrected-v10/corrected-candidate legacy response checks, credential and role denials, active-profile suspension denial, malformed method/filter/page boundaries, concurrent valid/invalid requests, exact count and filtering, SKU punctuation, sort, page-size bound, and count after insertion. One PostgREST startup attempt exited with code 3 before any checks; an immediate retry passed without fixture or candidate change. Startup reliability is not proven by this one successful run.
- Twenty simultaneous requests (10 valid, 10 invalid) returned the expected 200/400 statuses and stable count. This is not a production load test.
- After 5,000 additional synthetic products, five sequential HTTP timing samples in milliseconds: short text `35,36,39,39,41` (median 39); substring `28,31,32,37,38` (median 32); offset 4,000 `21,24,26,27,28` (median 26). Single-machine, warm-cache results only. Offset pagination shifts when concurrent inserts change ordering; no snapshot cursor exists.
- All local services were stopped. Ports 55483, 55484, 55485, and 8000 had no listeners afterward.

## Production parity gap matrix

| Area | Read-only production observation | Synthetic fixture and gap |
| --- | --- | --- |
| PostgreSQL | Production 17.6 | Local 18.6. No PostgreSQL 17 runtime compatibility claim. |
| Products | Production has category, brand, moderation, seller SKU, stock, specification, pricing, and publication columns plus checks and FKs | Fixture contains query-required subset only; constraints and lifecycle rules are incomplete. |
| Images and variants | Production has store/seller links, soft deletion, variant primary-image FK, and uniqueness constraints | Fixture has minimal product relations; image/variant ownership and uniqueness are not reproduced. |
| Sessions | Production includes refresh/family fields, profile FK, revoked/expiry fields, unique JTI, active-session index | Fixture includes queried JTI/profile/expiry/revocation subset, not full family/refresh behavior. |
| Roles and RPCs | Production `adishop_admin_session(uuid)` is SECURITY DEFINER; profile permission RPC is stable. Role assignment/revocation has extra audit fields | Fixture mimics permission decisions but not full RPC body or audit schema. |
| RLS and grants | Production RLS enabled on all relevant tables; products have public approved/active-store read policy. Broad anon/auth grants on most tables are constrained by RLS; sessions are service-role only | Fixture enables RLS but grants only service-role SELECT and has no equivalent anon/auth public policies. Tests exercise privileged service-role path, not public-RLS behavior. |
| Indexes and scale | Production has public/category/moderation/store indexes, SKU/variant and session indexes | Fixture has only three simplified product/image/variant indexes; 5,000 rows do not model production distribution or planner behavior. |
| Auth/HTTP | Production uses Firebase-to-Shop session issuance, real Supabase gateway, secrets, and deployed PostgREST | Local tests use synthetic JWTs and real handler logic behind a loopback proxy. No real Firebase or gateway parity. |
| Pagination/filtering | Candidate uses bounded offset and exact count with deterministic tie-breaker | Local query results passed, but concurrent insertion can shift pages; production indexing, count cost, and timeout behavior remain unverified. |

## Security and next gate

- The handler authenticates a Shop session and checks `product.moderation.read` before privileged queries. Synthetic denials passed, but the local service-role bypass means the fixture cannot establish complete production RLS/Auth isolation.
- **NO-GO for deployment.** Review the corrected three-file bundle against the preserved deployed v10 source, resolve or explicitly scope the unrelated main-worktree type error, and run the same HTTP/Auth/PostgREST tests on an isolated PostgreSQL 17.6-compatible Supabase environment with representative schema, RLS, indexes, and synthetic identities. Recheck startup reliability and search/offset performance at realistic scale. Production remains unchanged until separate approval.
