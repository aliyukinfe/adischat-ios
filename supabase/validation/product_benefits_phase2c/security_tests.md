# Product Benefits Phase 2C: focused isolated test matrix

No test in this file has been executed. Use a separate Supabase/Auth/Storage
environment with synthetic seller A, seller B, dual-role seller/admin, independent
admin, buyer, two stores, two products and two variants. Exercise actual HTTP
contracts as well as database RPCs. Record redacted request IDs and before/after
database snapshots. Never run these fixtures against production.

| Scenario | Expected assertion |
| --- | --- |
| Seller A reads/writes Store B claim or evidence | 403/404; no row or Storage change |
| Forged product/store or variant/campaign association | 4xx before write; ownership trigger also rejects direct service-role error paths |
| Seller approves own claim | denied, unchanged version/events |
| Dual-role seller/admin reviews own store | denied despite admin permission |
| Missing/invalid/expired/revoked Shop session | denied on every protected route |
| Private evidence URL guessed, reused, expired, or accessed cross-store | denied; no public Storage read or path leak |
| Two admins approve/revoke concurrently with same version | exactly one transition wins; one 409, one event |
| Duplicate request key | same result; no second event or upload |
| Evidence removed before approval | approval denied; no partial status change |
| Expired or revoked claim, suspended store, archived product | buyer projection omits benefit immediately |
| Brand alias change, product brand mismatch, authorization scope/expiry | Official badge absent until canonical ID and scope match |
| Addis Ababa subcity edge and unknown location | correct zone; unknown omits delivery badge |
| Threshold one birr below/at/above per seller group | fee and badge agree with quote |
| Seller-funded versus platform-funded delivery | correct funding party; unapproved platform funding denied |
| Variant-specific quantity tier boundaries | only exact eligible variant/quantity receives tier |
| Promotion versus bulk tier, coupon conflict | non-stacking best eligible total; deterministic tie |
| Insufficient 30-day price history/new product | no percentage-off badge |
| Approval/revocation, rule change, clock expiry | cached projection invalidated or bounded by expiry |
| Listing quote versus checkout confirmation after price change | stale quote rejected; refreshed total agrees |
| Existing products, moderation, and checkout without benefit data | unchanged behavior, no fabricated badge |

Add static checks for RLS enabled, no anon/authenticated table grants or sensitive
RPC EXECUTE, private bucket MIME/size/path policy, append-only events, and FK/
ownership guards. Test image/PDF content validation and Storage compensation on
metadata failure. Test both light/dark buyer UI only after API gate passes.
