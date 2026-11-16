# Phase 9B.13 public catalog privacy gate (local only)

**NO-GO.** No production SQL, Edge deployment, review decision, ledger edit, APK, commit, or push occurred.

## Integration verified locally

- Android Public Content remains disabled (`ShopPublicContentReviewFlags.enabled=false`). Its existing Shop Admin transport points to `adishop-admin-review-product` with separate queue, preview, and revision-checked decision actions. The server derives the actor from the Shop session and requires `product.moderation.approve`; the service-only SQL RPC repeats the permission, active-profile, owner, and revision checks. No client actor/store ID is accepted for a decision.
- Queue filters support store, product, variant, and image. The Android request limit and pagination offset both use 20. A newer page request invalidates an older response. Account/workspace reset clears queue and preview. Preview requires a queued kind/ID/revision; approval and rejection require explicit confirmation. Errors retain the current preview only when safe to retry.
- Focused Android contract tests: 4/4 passed; Gradle compiled the changed Kotlin during that run. Node review-request parser: 5/5 from Phase 9B.12. SQL preparation 8/8 and synthetic cutover 4/4 from Phase 9B.12 were not rerun; the related SQL was unchanged.
- **Not verified:** actual Compose taps or the undeployed Admin Edge route through real Auth, gateway, and PostgREST. The earlier disposable PostgREST/Deno executables are no longer present; PostgreSQL 17.11 alone is not an HTTP/Auth stack. Do not substitute mocked request tests for that gate.

## Historical review order

1. Review all 10 active/approved store records (5 currently have eligible products). Reject private contact or unreviewed text in names, descriptions, and image references.
2. Review 32 eligible active/approved product records, including 23 option groups, 47 option values, and 34 specification rows within product revisions. Examine title, brand, description, and structured/free text for device identifiers and other private data.
3. Review 47 active variant revisions and 59 active image revisions, including text and media references. Recheck product/store eligibility after every decision. Three soft-deleted images are not eligible for buyer display.
4. Re-run a fresh read-only inventory and revision count immediately before cutover. These counts are workload estimates, **not** approvals; never run the synthetic backfill on production.

The final review window needs a **server-enforced seller-edit freeze** across every buyer-visible product, store, image, variant, option, and specification write path, or an approved private-draft mechanism that leaves public revisions unchanged. Hiding Android edit buttons or asking sellers to pause is insufficient. No freeze is implemented or active. The cutover transaction must recheck all current revisions while holding bounded locks; stale approvals fail closed.

## Storage and legacy evidence

- Read-only production metadata: `shop-product-images` and `shop-store-assets` buckets are **public**; they contain 148 and 7 objects respectively. A known public object URL is not revoked by catalog RLS or column grants. Public-bucket access and old image URLs remain a privacy blocker until Storage is inventoried and a reviewed private/signed-URL or media migration plan is validated. Do not flip buckets without a compatibility plan.
- Current `ShopSupabaseApi.kt` uses explicit buyer columns. The local build tree contains a debug APK, release APK, and Android-test APK; none is proven to represent the installed legacy population. No Git tags or confirmed historical `select=*` source snapshot were found in the scoped search. Older clients that call `select=*` or raw private columns may fail after grant cutover; installed clients cannot be forced to update. APK/network-contract verification remains required.
- Historical cart/order snapshots and alternate buyer Edge projections still need privacy review. The prior SQL-only fixture does not establish Storage or external-client safety.

## Deployment checklist (all require separate authorization)

| Gate | Status | Required evidence |
| --- | --- | --- |
| Preparation | LOCAL PASS | Reviewed hashes, production schema/RLS/grant preflight, additive-only preparation, rollback snapshot |
| Authorized historical review | BLOCKED | Human decisions for every eligible current revision; no auto-approval; audit and coverage report |
| Edit-window control | BLOCKED | Server-enforced freeze or private drafts, all write paths covered, rollback tested |
| API/Auth | BLOCKED | Isolated real Edge + Shop Auth + PostgREST tests for anon, buyer, seller, owner, Admin, Super Admin, invalid/revoked sessions, and raw-column denial |
| Storage privacy | BLOCKED | Public URL inventory, safe media-access transition, historical URL behavior, older-client impact |
| Android compatibility | PARTIAL | Disabled local contract tests pass; legacy APKs and actual Admin UI/HTTP flow untested |
| Cutover | LOCAL PASS ONLY | Previous synthetic incomplete-review abort and reviewed-row visibility; fresh production preflight and explicit cutover approval still required |
| Privacy-safe recovery | BLOCKED | Tested restore that does not regrant raw columns or republish unreviewed text; prefer temporary catalog unavailability to data exposure |

Do not execute preparation or cutover, enable the Android flag, or make production review decisions until the blockers above are closed.
