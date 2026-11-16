# Seller Center V6.4 review handoff (not deployed)

## Defect and correction

Deployed `adishop-seller-delete-product-media` v13 performs three independent
database calls: delete `shop_variant_media`, clear variant `primary_image_id`,
then soft-delete the image. If V6.2's image-lock trigger rejects the final
call, the first two writes remain committed. A concurrent admin approval can
therefore leave variant data changed even though deletion was rejected.

`seller_center_v64_atomic_media_delete_proposal.sql` adds one service-role-only
RPC. It resolves the selected store, verifies the Shop-session-derived seller
profile's ownership, locks the product row in the same order as moderation,
checks editable status and exact image ownership, then soft-deletes the image,
removes variant links, clears variant cover references, promotes an active
cover if needed, writes the audit event, and returns the refreshed product.
Any exception aborts the transaction. A repeated or stale request is denied
without another audit event. Single-store legacy requests may omit `storeId`;
multi-store sellers must specify it.

The Edge candidate calls this RPC once. Storage object removal remains after
the database commit because Supabase Storage is not part of that transaction.
If it fails, the operation returns success with `storageCleanupPending: true`;
the soft-deleted DB row retains the path for a reviewed cleanup job. It never
reports a committed DB deletion as a failed request that encourages retry.

The current public image policy omits `deleted_at is null`. A read-only live
query found 10 soft-deleted images attached to active approved products. The
new V6.4 proposal also narrows that one policy to hide deleted image rows;
its existing product/store checks are retained. This changes no image data.
The fresh read-only preflight counted 31 active approved products, all 31
associated with valid active stores, plus 50 active public images and 10 deleted images that would be
hidden. There were no duplicate active Storage paths or cross-product variant
media links.

## Exact proposals and baseline

| File | SHA-256 |
| --- | --- |
| `seller_center_v62_approval_security_proposal.sql` (unchanged) | `348003C303D5E17B662546A9AD11A9406E4F24ED8731A83756FD3D86F2D4F594` |
| `seller_center_v63_catalog_store_guard_proposal.sql` (unchanged) | `6F4DA035E0E8595B7077332B2AE57E5F52A701F2249A893E59975AFDA2E22871` |
| `seller_center_v64_atomic_media_delete_proposal.sql` (new, separate approval required) | `36A1293992902ED9EA4A7D463388719D5E2FAD4BF6F4909C9FA0E629E4A5B88B` |

The exact deployed source was downloaded through the Supabase connector and
compared character-for-character after line-ending normalization against each `baseline`
folder under `seller_center_v64_bundles/`. At the final comparison:

| Function | Deployed version | Gateway JWT | Deployed bundle hash | Candidate shared-source SHA-256 |
| --- | ---: | --- | --- | --- |
| `adishop-admin-review-product` | 12 | false | `0f23ae95cd3e3280215d1a0511823a44eeda8deab3fc7de2c3f78418e5e70b02` | `F21AD62067578120AA7337144CB9F60C735C5A67CF63A4F3B6C6A32645B040CB` |
| `adishop-seller-delete-product-media` | 13 | false | `ee99935759d76610d1e23a38779cd9c83526b4e2ec5c82a13d5cd0053c5532e2` | `F85E941B486ECF6CACBECFFCE694AEA362185159F0661258A0272DCF7345D113` |

The admin candidate changes only `adminReviewProduct` timestamp forwarding and
two conflict mappings. The media candidate changes only `deleteSellerProductMedia`
and adds one `product-image-locked` mapping. Entry points and the media
notification helper are identical to their deployed baselines. No source from
the much larger local shared file is included in either candidate. The bundle
scope test compares the entire source outside these exact changes.
The canonical local `supabase/functions/_shared/adishop.ts` media-delete route
also matches the scoped candidate, so future source work does not retain the
old independent-write path. That full local shared file is not a deployable
bundle for this rollout.

Complete candidate bundle digests (SHA-256 over sorted relative path bytes,
NUL, file bytes, NUL for each included file):

- Admin review: `EE5415A0EA7402989DAEA0BC35D62CDFD9D329DF2E7AABCB6B884AAAF6C265A9`
- Seller media deletion: `96819558E5D985C42978FD61D7C5B40F4C8EE35E785346792CF6212E9EDBC32A`

Android's delete request now includes the selected `storeId`. The response
still returns the existing `sellerProduct` envelope. No version bump was made.

## Isolated evidence

- Disposable local PostgreSQL 17.11 on `127.0.0.1:55463`, database `adsg_v64`.
  All product, store, seller, admin, image, and variant rows are synthetic.
- Unchanged V6.2 SQL and the V6.4 proposal compiled in that database.
- The V6.4 runner passed 14/14: public image RLS visibility, authorized deletion, wrong store, foreign
  seller, soft-deleted image, other-product image, multi-store omission,
  single-store legacy call, repeat deletion, forced post-image-update trigger
  failure and forced audit failure with full rollback, deterministic cover, and both two-connection
  approval/deletion lock outcomes.
- Focused V5/V5.3/V5.4/V6.2 and bundle-scope contracts passed 13/13.
- Node's TypeScript syntax check passed both candidate shared files. This is
  not a full Deno typecheck or positive live Edge authentication test.
- Android `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and
  `:app:compileDebugAndroidTestKotlin` passed; 144/144 unit tests passed.
  No APK was installed because the server contract is not deployed.

The isolated schema is representative, not a full production clone. The
concurrency tests use two database connections but cannot prove Supabase
Storage cleanup, live Shop-session behavior, or production API compatibility.

## Proposed deployment order after separate approval

1. Recheck all three SQL checksums, function versions/source hashes, RPC
   absence, grants, RLS, product/image counts, and local test results. Use a
   short maintenance window and preserve `verify_jwt=false` with internal
   Shop-session validation.
2. Apply only V6.4's new RPC and public-image-policy SQL. Existing routes do
   not call the RPC yet; it is a dormant addition. The policy immediately
   hides only soft-deleted image rows. Recheck the RPC signature and
   service-role-only grant, and compare active-image visibility.
3. Deploy only the scoped media-delete candidate. Verify authenticated
   single-store and selected multi-store behavior, denials, and that the old
   independent mutations are no longer reachable in that function.
4. Apply the unchanged V6.2 approval SQL, then the unchanged V6.3 catalog
   policy SQL. The seven-argument review wrapper fails closed during the
   interval before the admin Edge update.
5. Deploy only the scoped admin-review candidate. Verify timestamp forwarding,
   denial paths, valid legacy reads, and catalog/store visibility without
   publishing QA products. Recheck gateway settings and exact versions.

This order avoids running the old non-atomic media endpoint under the new
image trigger. It changes neither the migration ledger nor the V6 category
metadata. Do not run `supabase db push`.

## Recovery and residual risks

- A failure while applying one SQL file rolls that file's transaction back.
  Stop before subsequent steps; do not blindly rerun partially reviewed work.
- If the new RPC is present but media deployment fails, it remains dormant.
  Investigate before any further SQL. Revert the media function to v13 only
  while the V6.2 trigger is absent and media deletion traffic is controlled.
- After V6.2 is active, do not restore the old permissive review RPC or the
  non-atomic media function. Disable review/deletion traffic and prepare a
  reviewed roll-forward. Preserve product, image, variant, audit, and Storage
  evidence; do not delete production records to conceal a failure.
- If the catalog guard unexpectedly hides a valid listing, compare the saved
  prior policy and store state; revert the policy only through a reviewed
  correction. Do not rewrite products or weaken store ownership.
- If the V6.4 image policy hides an active image unexpectedly, compare the
  saved pre-rollout policy and the image's `deleted_at`/product/store state.
  Restore the prior policy only through a reviewed correction; do not restore
  soft-deleted media or delete Storage objects as a shortcut.
- Storage deletion can fail after the database commit. `storageCleanupPending`
  and retained soft-deleted paths permit a scoped reconciliation; no automatic
  cleanup worker is included here. Direct Storage deletion outside this
  endpoint remains outside the database locking protocol.
- Production deployment, Android device round-trip, and positive live Edge
  authentication are **not verified**. Seller Center stabilization remains
  blocked until separately authorized rollout and lifecycle validation.
