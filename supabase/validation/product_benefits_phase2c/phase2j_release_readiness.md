# Product Benefits Phase 2J: local stabilization review

Status: local implementation only. No production or isolated-runtime security validation is claimed.

## Scope and changed files

- `07_brand_administration.draft.sql`: aliases retain canonical brand IDs; retirement timestamps and immutable events preserve history and reserve names. Duplicate-brand review requires a second authorized administrator, version checks, and an audit event. A `confirmed_duplicate` decision records review only; it does not transfer authorization, claims, aliases, or products.
- `10_pending_evidence_uploads.draft.sql`: new local draft for pending upload jobs, server-generated private paths, transactional finalization, last-attempt grace period, manual cleanup leases, idempotent completion, immutable events, owner/role checks, RLS, and service-role-only RPCs.
- `adishop-benefits.ts` and the existing `adishop.ts` dispatcher: reserve/upload/finalize/retry flow; disabled-by-default manual cleanup on `adishop-admin-products`; alias retirement and duplicate review on the existing brand-management route. No new function URL.
- Android Shop benefit models, repository, API and brand-admin panel: upload idempotency part and review/retirement UI, all behind the existing disabled management flag.
- Local Node and Android contract tests; `api_contracts.md` updated for pending uploads.

## Local checks

- Node static/mocked contract suite: 30/30 passed. Mocks establish handler order and response behavior, not real Shop-session, RLS, Storage, or concurrency security.
- Android `:app:compileDebugKotlin` and `:app:testDebugUnitTest`: passed; 158/158 unit tests, zero failures/errors. The existing 3 GB heap was unchanged, and in-process Kotlin compilation was command-line only. Neither task enables buyer badges or management UI.
- `ShopBenefitFlags.buyerBadgesEnabled` and `managementEnabled` remain `false`.
- No Edge Function, database, bucket, migration-ledger, checkout, Android release, or production record was changed.

## Remaining security and compatibility gates

1. Run drafts 01, 03-10 against an isolated Supabase stack with synthetic Auth/Shop sessions, PostgreSQL, private Storage, and actual HTTP execution. Draft 02 delivery/pricing must remain disabled. Check SQL syntax, trigger ownership, grants, RLS, service-role-only RPC access, and request/response shapes.
2. With two database connections, exercise alias collision/retirement/duplicate review races; upload reservation versus cleanup; claim submission versus cleanup; concurrent finalize; evidence preview versus session/permission revocation; and expiry. Verify audit rows and absence of automatic authorization transfer.
3. Test Storage upload failure, retry with matching and mismatching bytes, orphan detection, lease expiry, repeated cleanup, and failure after object removal. Cleanup is manual, permissioned, and off unless explicitly enabled. Never delete submitted, approved, or audited evidence. Storage deletion is irreversible; retain audit metadata and require an operator-reviewed dry run before any future enablement.
4. Validate an older Android client that omits `requestKey`: the handler generates a fallback key and matches pending content by claim/hash. New Android includes the key. Test older catalog clients with both benefit flags off; existing catalog JSON remains unchanged.
5. Verify evidence proxy response size/MIME/hash restrictions and private path non-disclosure over actual HTTP. Revoke sessions during preview and confirm a subsequent request fails. Already-delivered bytes cannot be recalled after revocation.
6. Production schema and migration-ledger drift require a separately reviewed migration plan. No `supabase db push` or production test data. Private `shop-benefit-evidence` bucket and restrictive policies must be provisioned only after isolated validation and separate approval. Deploying the new upload handler before its RPC draft and private bucket would break evidence upload; this ordering is a hard gate.

## Proposed deployment sequence, not authorization

1. Complete isolated integration/concurrency tests and reconcile the existing migration-ledger discrepancy through a separately approved process.
2. Review exact SQL files and checksums; deploy additive trust schema, guards, claim transitions, intake, private bucket/policies, brand administration, buyer projection, evidence access audit, then pending-upload lifecycle. Keep intake, cleanup, management UI, and buyer projection disabled.
3. Deploy only reviewed changes to existing function bundles with preserved URLs and explicitly test old-client contracts. Do not enable manual cleanup until orphan-recovery drills pass.
4. Validate seller/admin authorization and evidence Storage with synthetic accounts, then independently approve management activation. Buyer badges require a separate projection and UI gate. Delivery, pricing, and checkout remain out of scope.

Recovery: stop writes and keep all flags off; restore prior Edge bundles if needed. Do not drop evidence, audit events, or Storage objects automatically. Use a reviewed forward SQL repair for schema defects, and reconcile abandoned pending uploads by permissioned manual inspection and lease-based cleanup only after evidence-reference checks. A deleted Storage object cannot be rolled back from PostgreSQL; retain a separate test backup for staging drills. [Supabase Storage documentation](https://supabase.com/docs/guides/storage/management/delete-objects) confirms deletion must use the Storage API and is permanent.

Production blockers: no isolated full-stack runtime validation; SQL drafts unexecuted; private bucket/policies unprovisioned; session/revocation and concurrency tests unverified; migration ledger drift unresolved; production deployment and flag activation lack authorization.
