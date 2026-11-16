# Phase 9B.15 reviewed media candidate (local only)

**NO DEPLOYMENT.** `ADISHOP_REVIEWED_MEDIA_ENABLED` is off unless explicitly set; Android `ShopReviewedMedia.enabled` is `false`. Production buckets, data, functions, and migration ledger remain unchanged.

## Candidate

- Existing `adishop-product-reviews?action=reviewed-media&kind=image|store-logo|store-cover&id=<uuid>` route. It accepts no path/store override. Service-only `adishop_public_reviewed_media_locator` checks active/approved store and product, product/image/store current reviewed revisions, image deletion, and image/product store match. The route checks bucket, origin, path structure, MIME, and 5 MB maximum; it downloads private bytes server-side and rechecks the locator before streaming. It returns no Storage path, service credential, or signed URL. All responses use `no-store`; unavailable media returns 404 and Storage failure returns 503.
- Revised `phase9b11_public_cutover_candidate.sql` removes raw image/store URL column grants and emits null media URLs from detail while retaining media IDs. This supersedes its earlier synthetic evidence and requires fresh review before any execution.
- Android has disabled reviewed-list queries that omit raw URL columns. Its mapper builds same-shape `imageUrl`, `logoUrl`, and `coverUrl` values from IDs, preserving Coil's URL input contract for Home, Trending, Search, Product Details, and store lists when the paired backend is ready. Existing production paths remain unchanged with the flag off.

## Focused evidence

| Check | Result |
| --- | --- |
| Mocked route: disabled, approved, unreviewed/rejected, stale, forged header/params, cross-store/path, store origin, MIME/size/cache | 9/9 pass |
| PostgreSQL 17.11 synthetic locator: service-only grant, reviewed/stale/rejected/store/cross-store | 9/9 pass, test writes rolled back |
| PostgreSQL 17.11 revised cutover: no raw URL grants, reviewed detail IDs and null URLs | 5/5 pass |
| Android reviewed URL and raw-column-free query contracts | 3/3 pass; Gradle compiled affected Kotlin |
| Deno type-check, actual Edge/Storage/Auth/PostgREST HTTP | NOT VERIFIED: Deno/PostgREST binaries and isolated Supabase Storage/Auth stack unavailable |

The disposable PostgreSQL server was stopped. These tests do not prove production PostgreSQL 17.6, gateway, CDN, or Supabase Storage behavior.

## Read-only orphan classification

Forty `shop-product-images` objects have no image row matched by bucket/path. All 40 have `seller-products` paths and point to an existing product. Seventeen point to currently active/approved products; 23 point to nonpublic products. None matched the current image/store URL columns by path. **Zero are confirmed truly unreferenced**: order/cart snapshots, old URLs, CDN caches, and external references are not fully inventoried. Do not delete or move them automatically.

## Release blockers

1. Production buckets remain public, and already-known public URLs/cached bytes are not revoked. A separately approved private-bucket/policy transition, cache plan, and old-URL HTTP tests are required.
2. Existing seller upload still writes `getPublicUrl`; seller, admin, cart/order, messages, and historical snapshot media paths need coordinated migration or explicit exception. The current route cannot safely be enabled alone.
3. Known new Android catalog paths are covered only behind a disabled flag. Older clients selecting `image_url`/`logo_url`/`cover_url` will fail after cutover and cannot be forcibly updated. Complete installed-version inventory and update policy are required.
4. The public function's deployed baseline and actual gateway behavior are unverified. Mocked locator tests do not substitute for real Auth/Storage/HTTP, and the tiny edit-after-final-lookup race still requires an isolated concurrency decision.
5. Historical human review, seller-edit freeze, revised SQL preflight, privacy-safe rollback, and separate deployment authorization remain required.
