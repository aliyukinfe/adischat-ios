# Phase 2D static review and staging availability

Date: 2026-11-04. Static review only; **not security validation**.
No production SQL, Storage, Edge, Android, or migration-ledger changes.

## Environment check

- Local `docker` and `psql` commands are unavailable; there is no local full
  Supabase stack to execute the drafts against.
- Read-only Supabase project listing found active `mcewjfnkrtiiolzibsuh`
  (AdisGebeya production) plus unrelated inactive projects. None is an
  identified isolated AdisGebeya staging project. No paid resource was created.
- No separate staging Auth, private Storage, HTTP Edge runtime, or synthetic
  seller/admin identities have been confirmed. Production is not a test target.

## Static review: corrections confined to this directory

1. `adishop_benefit_submit` checked an idempotency event before owner/profile
   authorization. Moved authorization before the event lookup to prevent a
   foreign caller who knows IDs and a request key from learning case status.
2. Approved claim/authorization/rule/offer details and evidence could be
   changed through service-role direct table writes. Added case-row-locked
   subject guards, draft-only edits, immutable evidence, and removed direct
   benefit-case status and event mutation grants from `service_role`.
3. Case and evidence creation lacked automatic audit entries. Added append-only
   triggers; direct service-role event insertion is denied. DB owner remains a
   privileged recovery actor, as in existing audit infrastructure.
4. A price-offer case could have no product and then could never pass its own
   detail guard. The case constraint now requires a product ID.
5. One long-dated authorization document could hide another earlier-expiring
   document. Approval now requires relevant evidence and rejects any relevant
   document missing an expiry or expiring before the approved period.

## Static dependencies and remaining risks

- Draft order is 01 trust schema -> 02 delivery/pricing -> 03 guards/grants/
  projection -> 04 transitions. Each file has one transaction. Postgres 15+
  syntax (`UNIQUE NULLS NOT DISTINCT`) must be tested in isolated staging.
- Existing `shop_products.brand_id` has no proposed backfill/FK. Brand matching
  is fail-closed in the buyer projection; canonicalization and conflicts need
  data review before production.
- RLS is enabled and anon/authenticated grants revoked. There are intentionally
  no client policies; service-role-only functions use Shop-session-validated
  Edge callers. The actual Edge implementation does not yet exist.
- The private `shop-benefit-evidence` bucket, Storage policies, content
  validation, signed reviewer URLs, and orphan cleanup do not yet exist.
  Evidence metadata alone cannot prove private Storage isolation.
- Delivery-zone parentage and overlapping rule precedence need stronger
  constraints and a reviewed quote implementation. Delivery/pricing approval
  remains blocked by the draft transition RPC.
- Historical 30-day price snapshots, new-product/insufficient-history policy,
  platform-funded delivery accounting, price/fee checkout parity, and cache
  invalidation are not implemented. No delivery/pricing badge may be enabled.
- Existing remote/local migration-ledger drift remains untouched; these drafts
  remain outside `supabase/migrations`. Do not use `supabase db push`.

## Validation performed and blocked

- Local static contract test: `node --test draft_contract.test.mjs` (5 checks).
- Manual review: SQL dependency order, FK/store guards, authorization order,
  private evidence design, versioned transitions, audit immutability, projection
  status/expiry/store/brand checks, and existing moderation separation.
- **Not run:** PostgreSQL syntax/migration, RLS grants, HTTP/Auth, private
  Storage, two-connection concurrency, seller/admin isolation, buyer projection
  against committed data, delivery/pricing quote parity. All require the full
  isolated stack and synthetic identities described in `security_tests.md`.

Next action: establish a separately authorized, nonproduction Supabase stack
with Postgres, Auth, private Storage, Edge HTTP and synthetic accounts; run the
drafts and test matrix there. Review and fix any staging failures before seeking
separate production migration/deployment approval.
