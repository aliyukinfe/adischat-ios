# Phase 9B.10 integrated local candidate

No production SQL/Edge execution, data mutation, ledger change, Android build, commit, or push occurred.

## Components

- `phase9b9_public_catalog_candidate.sql`: private source/review records; unreviewed historical records receive no approval.
- `phase9b10_reviewed_catalog_candidate.sql`: entity revisions, invalidation triggers for product/image/variant/store and product specifications/options/media, service-only reviewer decision RPC with permission and self-owner checks, reviewed-row RLS, and explicit-key buyer detail RPC. Public option/specification text is captured at review rather than read live after approval.
- `phase9b9_public_grant_cutover_candidate.sql`: known-client column allowlists; denies raw columns, variants, and the raw public view. `select=*` legacy requests break intentionally.
- Phase 9B.1 metadata, Phase 9B.4 atomic seller RPC/caller and Phase 9B.6 regex publication guard remain separate local proposals. Phase 9B.10 supersedes Phase 9B.4's catalog wrapper; do not let deployment order overwrite the reviewed detail RPC. The regex guard is defense in depth, not the privacy authority.

## Isolated evidence

PostgreSQL 17.11, disposable loopback-only cluster, synthetic identities and listings: final integrated SQL compiled; 22/22 lifecycle/authorization/projection checks, 4/4 option checks, 2/2 media reassignment checks passed. Earlier unchanged Phase 9B.9 grants passed 12/12 and were not rerun. Admin-first race: review of revision 8 committed, later edit advanced to 9, public gate false. Seller-first race: edit advanced to 10, stale review rejected, public gate false. Anon role read a reviewed product and detail payload without the fixture's private SKU; private raw columns and `select=*` are denied by the grant cutover. No PostgREST, Firebase/Shop Auth, Storage, or actual Edge HTTP request was exercised.

## Safe rollout order (requires a revised deployable split and separate approval)

1. Verify live schema/RPC/grant/policy/trigger hashes and approved-row counts. Deploy Phase 9B.1 metadata, then the Phase 9B.4 atomic RPC and matching existing seller Edge caller only after independent validation. Do not deploy its detail wrapper after Phase 9B.10.
2. Add private source/review schema, revision capture, and authorized reviewer transport **without changing public RLS or grants yet**. This requires splitting the integrated Phase 9B.10 test SQL into two reviewed migrations. Backfill revision/source records, not approvals. Review every existing active product, active image/variant, and active store, including URLs, free text, specifications, and options; preserve all seller originals. Establish a process for new edits during the review window.
3. In one controlled cutover, enable reviewed-row RLS, replace the public detail RPC, revoke raw table/view SELECT, and grant only approved columns. New or stale content fails closed. Verify current and older supported Android queries over real PostgREST/Auth. Unsupported `select=*` or raw-column clients will fail; no remote mechanism can guarantee they show a friendly update message.
4. Add Phase 9B.6 regex guard only if its historical preflight and trigger lock order pass. Recheck all buyer Edge projections and historical cart/order snapshots; the current candidate does not sanitize those copies.

## Recovery and blockers

Before cutover, roll back candidate schema/functions by a reviewed dependency-aware migration only if no review records must be retained. After cutover, emergency recovery should preserve private source/review history and restore the verified prior RPC/grants/policies only after assessing privacy exposure; blindly restoring broad public SELECT would reopen the leak. Backups and old function definitions are mandatory. Never auto-approve existing listings to avoid a catalog blackout.

**NO-GO:** the combined file is a local test artifact, not a deployable migration sequence; no admin Edge transport/review UI or production-equivalent Auth/HTTP/Storage tests exist; the synthetic schema does not prove production trigger/RLS parity; historic and unlabeled identifiers require human review; other buyer Edge projections and snapshots need coverage. Seller Center/Android callers and older-release behavior remain unverified.
