# Phase 9B.7 backend release gate (local review only)

No production write, migration, Edge deployment, Android change, or migration-ledger change was made.

## Live read-only compatibility

Project: `mcewjfnkrtiiolzibsuh`. The live baseline has 45 active attribute definitions, 832 category mappings, seven present target leaves, zero collisions with the 11 proposed codes, zero proposed mapping collisions, 38 saved specification values, and 32 currently active/approved products. The Phase 9B.1 proposal expects 56 definitions and 871 mappings after its 11 definitions and 39 optional mappings; these are proposed counts, not deployed counts.

The candidate's referenced columns exist on `shop_products`, `shop_product_images`, `shop_product_variants`, `shop_stores`, `shop_product_attribute_values`, `shop_product_options`, `shop_attribute_definitions`, and `shop_category_attributes`. Referenced seller update, variant-save, submit, owned-store, and catalog-detail RPCs exist with compatible argument signatures. The public catalog payload currently calls `adishop_product_catalog_payload_description_core` and adds `shortDescription`; the proposed detail wrapper retains that source and response keys while redacting selected text. All seven relevant tables have RLS enabled. `anon` and `authenticated` have SELECT grants on products, images, variants, and stores, but not attribute values. Existing image review and product submission/classification triggers remain in place; no proposed trigger name collision was found.

This is structural compatibility only. Trigger order, full RPC behavior, existing product rendering, grants after applying the proposal, and lock interaction with the existing image-review trigger are **not** proven on the live schema. The Phase 9B.4 Edge caller change is local only and must be bundled and tested with its new atomic RPCs. Its empty-images handling and two-RPC replacement require explicit compatibility tests before release.

## Buyer privacy coverage and policy

Current buyer paths: Home/Trending/Search and older clients read `shop_products` directly (including joined store and image text); Product Details reads `adishop_product_catalog_payload`; protected cart/favorite/recent paths and historical order snapshots may copy source text. Detail redaction alone cannot protect legacy clients. The Phase 9B.6 publication guard blocks future high-confidence **labeled** IMEI, serial/S/N, and MAC values in approved product, active image, active variant, and active store text. Read-only pattern checks previously found no matching currently approved rows. This does not prove the absence of unlabeled or unusual identifiers. Stored seller values remain untouched.

Policy required before release: prohibit public device identifiers in free text, images/alt text, variant options, and structured specifications; keep identifiers only in separately authorized seller/admin data; review historical approved listings and order/checkout snapshots without deleting them; use human moderation and a documented remediation path for unlabeled or ambiguous sequences. Do not claim regex-only detection is complete. Buyer-safe server projections and legacy direct-table access need an independently tested policy so older clients cannot bypass protection. Existing approved listings must be reviewed before a privacy guard or public projection is activated.

## Isolated runtime evidence and gap

Prior isolated PostgreSQL 17.11 synthetic checks: 26 atomic/validation assertions (Phase 9B.5), 16 listing/store/variant privacy checks, and two independent-connection race orders (Phase 9B.6) passed. These were SQL/fixture checks, not production parity. A disposable PostgREST + Auth + Edge HTTP environment is not available now: the previously used Deno and PostgREST binaries are absent, and no complete isolated Supabase Auth/Storage stack is configured. Thus anonymous, buyer, seller, cross-store, and legacy-client HTTP behavior is **NOT VERIFIED**. No production users or tokens were used as test fixtures.

## Integrated candidate and deployment order (not authorized)

1. `phase9b1_electronics_attributes_proposal.sql`: guarded additive metadata, with current counts and code/mapping collisions rechecked immediately before any future execution.
2. `phase9b4_electronics_atomic_candidate.sql`: validation and atomic seller wrappers plus detail redaction. Deploy the matching existing seller Edge caller only after an isolated full-contract test; verify no old two-RPC write path remains. Do not expose new RPCs to anon/authenticated.
3. `phase9b6_listing_privacy_candidate.sql`: only after the historical/public privacy policy and preflight are resolved. Its table locks and trigger interactions require isolated production-equivalent concurrency tests. Then test legacy REST embedding and all buyer-readable paths before enabling traffic.

Each step needs a fresh read-only preflight, reviewed exact file hash, separate deployment approval, and post-check. Do not run `supabase db push` while the migration ledger is known to drift. If a step fails, stop; do not blindly rerun. Recovery requires preserving original RPC definitions and Edge bundle, disabling only the newly introduced triggers/functions where safe, and restoring the prior caller in dependency order. Additive metadata must not be dropped while saved values reference it; investigate and migrate those values under separate approval. No blanket rollback of product data is acceptable.

## Gate

**NO-GO / BLOCKED.** Exact remaining blockers: unlabeled and historical identifier policy; production-equivalent isolated PostgREST/Auth/Edge tests for anon/buyer/seller/cross-store/legacy access; existing trigger and lock-order concurrency parity; atomic Edge caller compatibility; independently reviewed rollback and deployment authorization.
