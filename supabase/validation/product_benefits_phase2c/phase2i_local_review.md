# Product Benefits Phase 2I local review

Status: implementation prepared locally; feature flags remain off. This is not runtime security validation or deployment approval.

## Changed files

- Backend: `supabase/functions/_shared/adishop-benefits.ts`, `supabase/functions/_shared/adishop.ts`.
- SQL drafts outside active migrations: `supabase/validation/product_benefits_phase2c/07_brand_administration.draft.sql`, `08_catalog_projection.draft.sql`, `09_evidence_access_audit.draft.sql`.
- Android: `ShopBenefitsModels.kt`, `ShopBenefitsRepository.kt`, `ShopFunctionApi.kt`, `ShopRepositories.kt`, `ShopBenefitsComponents.kt`, `ShopScreen.kt`, plus new `ShopBenefitEvidenceViewer.kt` and `ShopBenefitBrandAdmin.kt` in their existing Shop data/presentation packages.
- Local tests/docs: `draft_contract.test.mjs`, `phase2h_local.test.mjs`, `ShopBenefitsContractTest.kt`, `api_contracts.md`, `rollout_review.md`, this note.

## Prepared behavior

- Admin evidence metadata contains no Storage path or signed URL. An existing Admin Product Detail action revalidates Shop session and `benefit.evidence.read`, rejects an owner/member reviewer, records an append-only preview authorization, and streams size/MIME/SHA-256-checked bytes with `no-store` headers. Native Android renders JPEG/PNG/PDF inside the app without external URL handoff; approval stays disabled until every evidence page has rendered.
- A draft RPC supports permissioned canonical brand create/rename/alias/retire with normalized cross-table collision checks, version checks, idempotency keys, and append-only events. Retiring a brand removes its trust projection without rewriting products. Official Store claims remain store- and brand-scoped and admin-approved.
- A draft batch RPC delegates to the same server-side effective-product trust projection used by Product Details. The existing buyer catalog response shape stays unchanged. Home enrichment is an optional separate call for at most 100 product IDs; each card is capped at two badges.
- `ShopBenefitFlags.managementEnabled` and `buyerBadgesEnabled` remain `false`. Delivery, pricing, and checkout behavior are unchanged.

## Validation

- Node: 22/22 static and mocked-client tests passed, including preview permission, foreign store, audit failure, corrupted evidence, brand conflicts, badge priority, and buyer projection sanitization.
- Android: final `compileDebugKotlin` and `testDebugUnitTest` rerun passed in 4m23s, 156/156 tests. The existing 3 GB Gradle heap was unchanged; in-process Kotlin compilation was a command-line-only setting.
- No real Auth, Storage, RLS, Edge HTTP, SQL concurrency, or device benefit-flow test has been run. No production evidence was used.

## Remaining gates and risks

1. Run all nine SQL drafts in a separate, fully isolated stack with representative schema, Shop sessions, private Storage, and existing Edge transports. Static/mocked checks cannot verify RLS, grants, trigger permissions, transactions, or session revocation.
2. Exercise actual image/PDF preview, authorization expiry/revocation, account switching, evidence integrity, and audit failure on Android against synthetic data. A page already loaded in device memory cannot be retroactively erased when a session is revoked; the UI must close on account change.
3. Test canonical brand collisions and version races using independent database connections, then test Official Store scope, expiry, revocation, and product-brand changes through real buyer HTTP endpoints.
4. Review alias retirement/reassignment policy. The local draft adds aliases and retires brands, but deliberately does not silently reassign an existing alias.
5. Review Storage-upload cleanup when metadata insertion or compensating deletion fails. The current upload crosses Storage and Postgres and requires an operator reconciliation path.
6. Resolve the known production migration-ledger drift before any future production rollout plan. Do not use `supabase db push` on production for these drafts. Separate approval is mandatory for SQL, bucket, Edge, and flag changes.

No deployed Edge Function count, production record, Android version, checkout calculation, or migration history changed.
