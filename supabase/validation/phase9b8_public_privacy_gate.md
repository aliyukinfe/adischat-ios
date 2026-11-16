# Phase 9B.8 public listing privacy gate (local design, not deployable)

Production was queried read-only. No SQL or Edge Function was deployed and no product was changed.

## Confirmed exposure

`anon` and `authenticated` have table-level SELECT on `shop_products`, `shop_product_images`, `shop_stores`, and `shop_public_products`. RLS limits rows but not columns. Approved rows can expose product `title`, `slug`, `brand`, `short_description`, `description`, `specifications`, `seller_sku`, `product_type_description`, `shipping_details`, `return_policy`, and `validation_errors`; image `alt_text`, `image_url`, `storage_path`, and `bucket`; store `name`, `slug`, `description`, contact/address/policy/social fields. The `shop_public_products` view repeats raw product title and descriptions. `shop_product_variants` has a table grant, but its current public RLS policy is `false`; the public detail RPC can still include variant title/options. `adishop_product_catalog_payload(uuid)` is anon-executable and SECURITY DEFINER, so it needs its own safe projection.

The current Android Home, Trending, and Search REST queries select raw product title/summary plus joined store description and image alt text. Older clients can request other raw columns regardless of the current Android select list. Of 32 currently active/approved products, 1 has legacy `specifications`, 1 has `seller_sku`, and 12 have a nonempty description; counts do not establish whether any contain identifiers. Prior labeled-regex checks found no matches, but unlabeled and historical identifiers remain unclassified.

## Minimum defensible architecture

1. Maintain private seller/admin source text, including device identifiers, separately from public copy. Public fields must be curated by a versioned moderation decision; use an allowlist of approved public attributes and approved display text, not a regex as the sole gate. Do not delete source values. Unknown or unreviewed fields fail closed.
2. Before publication, copy/review every currently approved public product, image alt/URL, variant display option, and store text. Review historical cart/order snapshots and other Edge projections independently; publication guards do not retroactively sanitize snapshots.
3. Keep legacy `rest/v1/shop_products` columns needed by known old Android clients, but make their stored values public-safe. Replace broad table SELECT grants with column grants limited to the reviewed legacy contract. Remove public SELECT on raw/private columns, including `seller_sku`, `specifications`, `validation_errors`, image `storage_path`/`bucket`, and store contact fields. Revoke or replace the raw `shop_public_products` view grant. Test PostgREST embedded joins and filters with the exact old query strings. All seller/admin writes must use authorized private-source paths; public copy changes only after review.
4. Make Home, Trending, Search, Product Details, and the catalog detail RPC derive from the same curated public snapshot. Preserve existing response keys and approved product information. Keep the Phase 9B.6 labeled-identifier guards as defense in depth, not as proof of privacy.
5. Verify URLs and Storage object names do not embed private identifiers. RLS on variants is currently fail-closed for direct public reads, but the detail RPC's variant projection still requires review.

## Legacy compatibility decision

This cannot be claimed transparent today. Table-level SELECT currently lets old clients request arbitrary raw columns; RLS cannot redact them. Revoking that grant and allowing only curated columns will preserve the **known** Android query shape only after full schema/query and PostgREST-join tests. Unknown older clients using `select=*`, private columns, or raw view fields will receive permission errors. Keeping those requests working would preserve the privacy bypass. A migration must first inventory supported client versions, prepare a safe public-copy backfill, update seller/admin write paths, and publish a deprecation policy for unsupported raw-column clients. Renaming `shop_products` into a view at the same URL is not a minimal alternative: existing FKs, triggers, writes, and embedded joins depend on the table.

## Test and release gate

No new privacy behavior was implemented, so no isolated runtime test is claimed. Required focused tests before an executable SQL/Edge candidate: anon/buyer raw-column denial; known legacy Home/Trending/Search and detail HTTP response compatibility; curated unlabeled ID exclusion; approved historical listing and snapshot review; seller/admin private source access; foreign-store denial; publication and concurrent edit moderation; image URL/path leakage; Store and view grant denial; old-client embedded filters/joins; rollback of a failed backfill. The existing synthetic regex tests do not cover this architecture. A production-equivalent isolated Auth/PostgREST/Edge environment and a reviewed public-copy policy are still unavailable. No deployment-ready SQL/Edge candidate should be generated from guessed field mappings.

**BLOCKED:** broad direct grants and historical/raw data mean the Phase 9B.4/9B.6 candidates do not close the legacy privacy bypass. Separate approval is required for the eventual API/grant and data-migration contract.
