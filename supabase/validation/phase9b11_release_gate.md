# Phase 9B.11 staged public catalog gate (local only)

No production SQL, Edge deployment, data change, ledger change, APK, commit, or push.

## Stages

1. **Preparation:** `phase9b9_public_catalog_candidate.sql`, then `phase9b11_review_preparation_candidate.sql`. These add private source/review records, revisions, invalidation, and service-only queue/preview/decision RPCs. Existing public RLS, grants, view, and detail RPC stay unchanged. Historical rows receive revision 1, **not approval**. The local `adishop-admin-review-product?action=public-content-queue|public-content-preview|public-content-review` handler derives the actor from the authenticated Shop session; the SQL RPC rechecks active-admin permission, ownership, and expected revision. Legacy moderation remains the default route.
2. **Human review:** inspect every eligible product and its specifications/options, each active image and variant, and each active/approved store. Preview the current revision, decide approve/reject, and re-preview after any edit. The synthetic loop in `phase9b11_synthetic_review_backfill.sql` is test data only; it must never be used for production history. During this window, current public writes can still expose unreviewed edits under the old policy. A controlled public-text edit freeze or separately approved private-draft write path is required.
3. **Cutover, separately authorized:** `phase9b11_public_cutover_candidate.sql` in one transaction. It takes bounded locks, aborts on incomplete historical review or policy drift, then replaces product/image/store SELECT policies, replaces the detail RPC, revokes raw table/view grants, and allows only the known Android columns. The script must remain disabled until preflight, HTTP/Auth tests, and explicit production approval. Older `select=*` and raw-column clients receive permission errors; installed clients cannot be forced to show an update message.

## Fresh read-only production inventory

32 active/approved products in 5 stores; 10 active/approved stores overall; 59 active images and 3 soft-deleted images; 47 active variants; 23 active option groups, 47 active option values, and 34 specification rows. Product review covers its option/specification snapshot; image, variant, and store each require their own decision. These are workload counts, not review approvals or privacy clearance.

## Focused isolated evidence

PostgreSQL 17.11 synthetic fixture: preparation 8/8 new checks passed; pre-cutover anon public row count remained 1. Cutover with incomplete review failed `historical-review-incomplete` and left the legacy SELECT grant intact. After four **synthetic** decisions, cutover succeeded; 4/4 post-cutover checks passed and anon still saw 1 reviewed row and the reviewed detail title. Node request-parser tests passed 5/5. The unchanged Phase 9B.10 28 SQL assertions were not rerun. No real Edge HTTP, Firebase/Shop Auth, PostgREST embedding, Storage URL, or production-schema concurrency test was run.

The native Admin Center now contains a local Public Content queue, current-revision preview, explicit approve/reject confirmation, retry, and pagination. It uses the existing Admin transport and Shop session. `ShopPublicContentReviewFlags.enabled` remains `false`, so the undeployed route is inaccessible in the current app. `:app:compileDebugKotlin --offline` passed; no APK was assembled or installed. Real Admin UI/HTTP behavior remains unverified.

## Recovery and release blockers

Before cutover, do not drop review history if decisions have been made. A failed cutover transaction rolls back policy/grant/RPC changes. After a committed cutover, restore a verified previous policy/RPC/grant snapshot only after privacy review: blindly restoring broad SELECT reopens the leak. Prefer fail-closed temporary catalog unavailability over raw-data exposure. Keep backups, exact hashes, and a tested restore script.

**NO-GO:** human review and an edit-freeze/private-draft policy are absent; real Admin HTTP/Auth authorization, old-client PostgREST queries, and product/store/image/variant Storage paths are untested; the synthetic fixture is not production schema parity; buyer Edge projections and historical cart/order snapshots still need privacy coverage. Phase 9B.1 metadata, Phase 9B.4 atomic seller RPC/caller, and Phase 9B.6 regex guard require their own reviewed ordering. Phase 9B.10 detail wrapper must not overwrite the cutover RPC.
