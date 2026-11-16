# Phase 9B.14 Storage and isolated API gate (local proposal)

**NO-GO.** No production SQL, Storage, Edge, catalog, or Android changes were made. Do not run the SQL below on a linked project without separate review and authorization.

## Read-only production facts

- `shop-product-images` and `shop-store-assets` are public buckets. The live `public_read_shop_assets` Storage policy permits SELECT for anon/authenticated on these and category/campaign assets. A known public object URL bypasses catalog review visibility.
- Product images: 139 rows total, 113 not soft-deleted; 137 rows have a `shop-product-images` bucket/path, 136 rows contain a public Storage URL, and 108 rows match an object by bucket/path. Storage has 148 product-image objects, **40 without a matching image row**. These are unclassified, not automatically safe to remove. The prior 59-image count was for eligible active/approved listings, not all active images.
- Store assets: 7 objects; one of ten stores has public Storage asset URLs. URL strings and object paths were not read or exposed in this report.
- Seller upload currently calls `getPublicUrl` and persists it in `image_url`. Buyer Home/Search use direct PostgREST image/store URL columns; Product Details and several other existing responses also carry image URLs. Merely changing catalog RLS would leave old Storage URLs accessible.
- The current `phase9b11_public_cutover_candidate.sql` still grants public `image_url`, `logo_url`, and `cover_url` columns and emits reviewed URL strings from its detail projection. It **must be revised and retested** with the media transport/client change; it is not safe to execute as a complete privacy cutover.

## Reviewed-media delivery proposal

1. Inventory every referenced image/store object, storage URL, historical snapshot, and unreferenced object. Human reviewers inspect media content and text. Do not auto-approve, delete, move, or silently rewrite seller data.
2. Add a narrowly scoped `action=reviewed-media` to an **existing public product transport** such as `adishop-product-reviews`, after its deployed baseline and authorization are reviewed. It accepts an image/store entity ID, not a caller-supplied path. The server checks current approved revision, active product/store, non-deleted media, and allowed bucket/path, then serves a private object with `Cache-Control: no-store` and bounded size/MIME. Do not expose a service key or private path. A short signed URL is a lower-cost alternative, but a previously issued URL remains usable until expiry and is not immediate revocation.
3. Update the buyer client/API projection to use this checked media route. Direct table `image_url`, `logo_url`, and `cover_url` are no longer trusted delivery links. Update seller uploads to a private bucket/path and keep admin review access separate. All affected direct PostgREST and snapshot paths need a compatibility plan.
4. In a separately approved cutover, make the old product/store buckets private, remove their broad Storage SELECT policy entries, and deny raw public media URL columns. The bucket/public-URL switch must be coordinated with the reviewed-content grant cutover and new client rollout. Keep category/campaign asset rules separate. Verify old URLs from an unauthenticated client in multiple cache conditions; do **not** claim revocation based on a metadata flip alone.
5. Set short cache lifetimes before cutover where feasible, avoid future immutable public URLs for reviewable media, and plan CDN/browser cache purge plus a wait window. No server can erase screenshots or already downloaded bytes. Test stale/rejected/deleted revisions, previously known URLs, and signed-URL expiry in isolated Storage HTTP before production approval.

This breaks old clients that depend on permanent public URLs or raw `select=*`. Explicit Android 1.0.121 buyer queries use named columns; the local debug/release APKs are both 1.0.121, and the Android-test APK is not a buyer release. No older release artifact or tagged source was confirmed. Installed legacy behavior remains unknown and cannot be forcibly updated by this repository.

## Isolated runtime status

- Reusable PostgreSQL 17.11 synthetic fixture exists. Focused local freeze activation installed nine triggers; **5/5** checks passed (coverage, product mutation denial without changed title, product Storage insert denial, store Storage delete denial, unrelated bucket allowed). Release removed all nine triggers; the loopback PostgreSQL server was stopped. This tests SQL trigger behavior, not Storage API authorization or HTTP caching.
- PostgREST and Deno executables from prior temporary Phase 8B runs are absent; neither is on PATH. Docker is unavailable. The current fixture uses local synthetic trust auth and does not provide Supabase Auth, gateway, Storage HTTP, or Edge execution. Therefore the requested anon/buyer/seller/Admin/Super Admin/cross-store HTTP matrix, raw-column denial over HTTP, and queue/preview/decision responses are **NOT VERIFIED**. Previously passing Phase 9B.11/12 SQL and parser suites were not repeated.
- Minimum next environment: restore verified local PostgREST and Deno, plus isolated Supabase Auth/Storage/gateway or a dedicated nonproduction Supabase project with synthetic identities, private bucket, exact reviewed-content schema, and no production credentials. A PostgreSQL-only mock is not sufficient for the release gate.

## Seller-edit control

`phase9b14_edit_freeze_activate.sql` and `phase9b14_edit_freeze_release.sql` are local, unexecuted production proposals. Activation takes bounded table locks and installs a blanket database trigger across products, stores, images, variants, attributes, options, variant media, and product/store Storage objects. It blocks **all** writes to those catalog tables during the short final review window, including legitimate admin edits; unrelated Storage buckets remain writable. Failed activation rolls back as one transaction. Release is separate and must only occur after a privacy-safe media/catalog cutover or an explicitly reviewed recovery decision. Review can proceed in batches before activation; after activation, recheck every revision and complete final decisions. Android-only controls are insufficient.

## Release blockers

Public Storage URLs/caches, the 40 unclassified objects, missing isolated Auth/Storage/Edge HTTP runtime, unknown older clients, unreviewed historical content, untested media-route baseline, no production-approved edit window, and privacy-safe rollback for post-cutover failures. Do not deploy or enable buyer/admin flags while these remain open.
