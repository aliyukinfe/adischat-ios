# Phase 2H local implementation review

Status: local prototype only. No SQL, Storage, Edge Function, or Android artifact was deployed.

## Scope prepared

- Existing seller-business, seller-product-detail, admin-products, admin-product-detail, admin-store-detail, admin-review-product, and product-reviews transports have separate `benefit-*` actions in local shared source. No endpoint URL or deployed function count changes.
- Shop-session identity, seller ownership, selected store, admin permission, and decision RPC version checks remain distinct from product moderation.
- Draft SQL 05 adds owner-scoped claim intake. Draft SQL 06 adds a private evidence bucket policy. Drafts 01, 03, and 04 include evidence deduplication, brand/store checks, server-derived public projection, and append-only audit. They remain outside active migrations.
- Android has seller claim entry/status and admin review/history components plus buyer badge/detail components. Both `managementEnabled` and `buyerBadgesEnabled` remain `false`.
- Delivery, pricing, checkout, and promotional badges remain disabled and unchanged.

## Local validation

- Node draft/handler tests: 13 passed; static and mocked-client checks only.
- Android `compileDebugKotlin` and `testDebugUnitTest`: final rerun succeeded in 4m50s, 154 tests, 0 failures. The run used the existing 3 GB heap and a command-line-only in-process Kotlin setting; no Gradle configuration was changed.
- TypeScript syntax checks passed for the two new shared files.
- No runtime SQL, Auth, Storage, Edge HTTP, or device benefit-flow test has been run.

## Release blockers

1. A fully isolated stack with representative schema, Auth/Shop sessions, private Storage, and existing Edge HTTP transports is required for cross-store, self-approval, evidence access, concurrency, expiry, and public-projection tests. Mocked and static checks do not satisfy this gate.
2. Admin evidence metadata loads, but secure in-app document inspection is not implemented. External URL handoff was removed; approval remains unavailable in the disabled UI until a private viewer and its tests exist.
3. Canonical brand registry administration and brand-alias lifecycle are not implemented. Official Store and manufacturer claims cannot be operationally reviewed without them.
4. Product Detail has a disabled projection fetch. Home has a disabled badge component but no catalog-list projection feed; enabling the flag alone would not provide Home badges.
5. Storage upload and metadata insert cross separate services. Compensation is attempted if metadata insertion fails, but cleanup failure needs an operator reconciliation process and isolated failure-path testing.
6. Production schema parity, RLS/grant behavior, and the known migration-ledger drift must be reviewed before any deployment. Never run `supabase db push` against production for these drafts.

No buyer badge or management flag should be enabled, and no draft should be moved to active migrations, until the blockers are resolved and separately authorized.
