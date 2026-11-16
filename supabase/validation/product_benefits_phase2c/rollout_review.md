# Product Benefits Phase 2C: review and rollout plan

## Draft inventory

1. `01_trust_schema.draft.sql`: brands, aliases, cases, product claims,
   brand authorization, private evidence metadata and immutable events.
2. `02_delivery_pricing.draft.sql`: Ethiopia delivery-zone hierarchy,
   store shipping methods/rules, price offers and quantity tiers.
3. `03_guards_projection.draft.sql`: ownership/kind guards, admin permission
   seeds, restricted trust projection and event immutability.
4. `04_case_transitions.draft.sql`: service-role-only, row-locked submission
   and admin decision operations with versions, idempotency and dual-role denial.

These are **not executable production migrations**. They intentionally live
outside `supabase/migrations` because the existing remote/local migration ledger
has drift. No `db push`, ledger repair or timestamped migration is authorized.

## Deployment gates

1. Review unresolved policy below. Inventory live schema/RPC signatures and
   migration ordering read-only immediately before assigning real migration IDs.
2. Build isolated staging with separate Postgres, Auth, Storage and Edge runtime;
   synthetic accounts/products only. Reconcile the existing migration ledger
   separately, with explicit approval, before any CLI push workflow.
3. Run all four drafts in a disposable staging database. Verify Postgres version
   supports `UNIQUE NULLS NOT DISTINCT`, syntax, FK behavior, RLS, grants, RPC
   error contracts, transaction/rollback and concurrency. Correct drafts and
   re-review checksums; never treat static review as executed validation.
4. Implement scoped existing Edge Function routes, private bucket policies,
   permission-checked, audited reviewer preview, Storage cleanup and server-side quote integration.
   No new function is assumed; respect project function limits.
5. Exercise `security_tests.md` over HTTP and two DB connections. Verify normal
   seller/admin operations and buyer projection. Compare listing and checkout
   totals across variants, zones, discounts and clock boundaries.
6. Production rollout, only after separate approval: additive schema and grants;
   Edge auth/decision routes behind disabled feature flags; server projection;
   checkout quote; Seller/Admin UI; buyer badges last. Do not enable pricing or
   delivery badges before checkout parity. Preserve version 1.0.121 until an
   independently approved Android release plan.

## Recovery

Flags independently disable claim intake, admin decisions, projection and
badges; they default off. If a post-check fails, stop writes, disable projection,
retain evidence/events for audit, and restore the previous Edge bundle. Additive
tables are not dropped automatically; repair with a reviewed forward migration.
Never rewrite existing order totals or product moderation records. Short TTLs
and revocation invalidation limit stale badge exposure.

## Open policy and compatibility blockers

- Define the 30-day reference-price window, insufficient-history and new-product
  behavior before any percentage-off claim. Historical price snapshots and
  tamper-resistant price change recording are not implemented in these drafts.
- Define who may authorize platform-funded delivery and how it is accounted for.
- Confirm whether store managers may submit claims; draft RPC currently permits
  only the approved store owner.
- Confirm category/brand backfill and alias dispute process. Existing
  `shop_products.brand_id` is not constrained or rewritten by the drafts.
- Confirm evidence retention and removal rules, periodic re-review schedule,
  and precise manufacturer authorization standard (maximum 12 months or earlier
  source-document expiry).
- Delivery-rule overlap, shipping-method fallback and zone precedence require
  a reviewed quote algorithm. Current checkout has a shipping total field but
  does not by itself prove free-delivery eligibility.
- The drafts do not yet include immutable historical price snapshots, funded
  delivery settlement, a full delivery/pricing projection, Edge implementations,
  or private bucket creation/policies. These must be completed and staged before
  deployment. No security validation is claimed here.
