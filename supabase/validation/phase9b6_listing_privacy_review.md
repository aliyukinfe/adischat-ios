# Phase 9B.6 listing privacy review (local only)

No production SQL, Edge deployment, data mutation, or migration-ledger change occurred.

## Buyer-readable paths

| Path | Data exposed today | Local protection proposal |
| --- | --- | --- |
| Home, Trending, Search, older Android `rest/v1/shop_products` | Raw `title`, `short_description`; joined store `name`/`description`, image `alt_text` | `phase9b6_listing_privacy_candidate.sql` checks existing approved rows, then prevents newly public labeled identifiers in product, store, and image text. Existing URL/columns remain unchanged. |
| Product Details `adishop_product_catalog_payload` | Product free text, structured specifications, options, variants | `phase9b4_electronics_atomic_candidate.sql` redacts high-confidence labeled text and omits identifier-coded specifications; Phase 9B.6 guard blocks sensitive variant titles/options from publication. |
| Cart, favorites, recent, checkout/order summaries | Product or variant text and historical snapshots through protected Edge paths | Publication guard protects future source text. Historical snapshots and all external consumers still require a separate review. |

`anon`/`authenticated` have direct SELECT on `shop_products`, `shop_product_images`, and `shop_stores`. They do not have direct SELECT on `shop_product_attribute_values`. `shop_product_variants` has a table grant but RLS is enabled and no public SELECT policy was found; its values are still included by the detail RPC.

## Compatibility and safety

- Older Android clients keep their direct REST endpoint and response shape. RLS alone cannot redact individual columns. Replacing `shop_products` with a view is not proposed here because existing writes, joins, and PostgREST embedding need separate compatibility proof.
- The guard does not delete seller data. Drafts can retain private text; attempts to publish it fail. The migration aborts under a write lock if existing public rows already match the high-confidence identifier rule.
- Read-only live checks found zero matching approved product titles, summaries, descriptions, active image alt text, active variants, and active store name/descriptions. This is a pattern-based preflight, not a guarantee that all private identifiers have been found.
- The rule catches labeled IMEI, serial, S/N, and MAC-address values. Unlabeled or unusual identifiers remain a policy/design blocker. Seller-facing error mapping and moderation guidance are not implemented.

## Isolated evidence

- PostgreSQL 17.11, loopback-only, synthetic records. 10/10 product/image guard checks and 6/6 store/variant checks passed. A pre-existing approved identifier caused the preflight to abort; a clean synthetic fixture passed. `anon` and `authenticated` role reads returned only safe public text after the synthetic remediation.
- Two independent connections: variant-first edit committed and approval rejected; approval-first committed and the later sensitive variant edit rejected. Final states were respectively pending/sensitive and approved/safe.
- Phase 9B.5's 26 synthetic atomic/validation assertions were not rerun. No HTTP/Auth/Storage runtime was available here; the fixture uses simplified seller RPCs and does not prove production parity.

## Deployment blockers

1. Review the exact live function/table definitions and current PostgreSQL 17.6 behavior against both SQL candidates, including trigger cost and lock ordering.
2. Exercise anonymous, buyer, seller, and cross-store PostgREST/Edge HTTP requests with isolated Auth and synthetic identities. Verify RLS, legacy client embedding, and seller/admin write compatibility.
3. Define a reviewed policy for unlabeled device IDs and historical checkout/order snapshots. Confirm the final buyer-safe projection covers every public route.
4. Add seller/admin error mapping and a recovery plan before requesting a separate production deployment approval.
