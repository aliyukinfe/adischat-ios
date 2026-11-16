# Seller Center V6.2 approval review (not deployed)

## Scope and evidence

- Review-only SQL: `seller_center_v62_approval_security_proposal.sql`, SHA-256 `348003C303D5E17B662546A9AD11A9406E4F24ED8731A83756FD3D86F2D4F594`.
- Existing Edge Functions affected: `adishop-admin-review-product` and `adishop-seller-delete-product-media`. No new Edge Function is proposed.
- The Android request carries the displayed `submitted_at` as an optimistic token. The Edge Function passes it to the new eight-argument RPC. The old seven-argument RPC fails closed for review decisions during rollout; non-review actions continue through a wrapper. Older admin clients without the token must update before review actions resume.
- Current `pending` means an unsubmitted draft: all four live pending products have no submission evidence. All four submitted/under-review products have `submitted_at` and `submitted_version > 0`. Twenty-one already-approved legacy products lack this evidence and remain untouched.
- Current approval counts soft-deleted images and accepts `pending`; the proposed RPC requires a submitted/under-review draft, matching submission token, non-deleted product, authorized admin, and a valid same-store image. Reject and changes-requested decisions use the same state/token guard.
- Existing active media: 84 rows, with no store mismatch or missing URL. Two legacy rows have no storage path; three path-bearing rows have no matching Storage object. The proposal preserves pathless legacy media but requires a Storage object when a path is present. One already-approved product and two submitted products lack an image satisfying the proposed predicate. The migration does not rewrite or unpublish them; review those records before re-review.

## Concurrency and authorization

The admin RPC locks the product row before state and image checks. The image mutation trigger locks the same product row before a soft delete, hard delete, reassignment, or URL/path change. If image removal wins, approval sees no active image and fails. If approval wins, image removal sees the reviewed state and fails. The trigger does not change the existing Storage bucket or media rows. Direct Storage object deletion outside the existing seller media path remains an operational risk to verify separately.

The new RPC retains service-role-only grants. The Edge Function derives the admin profile from the validated Shop session, not the request body. Product/store ownership is resolved from the locked product row. Duplicate request IDs return only the exact prior actor/product/action result; reused IDs for another decision conflict. A new request against an old submission timestamp conflicts.

## Deployment order after separate approval

1. Recheck SQL SHA-256, current function signatures, trigger absence, grants, and the affected product/media counts. Run the full lifecycle matrix on an isolated staging database first. Do not deploy if any test fails.
2. Apply the reviewed SQL in one transaction. It adds the image guard trigger, secure eight-argument RPC, and fail-closed seven-argument compatibility wrapper. Existing admin review actions may temporarily fail closed; the old Edge version may display a generic error until the new version is deployed. Coordinate these steps in a short maintenance window.
3. Deploy only `adishop-admin-review-product` and `adishop-seller-delete-product-media` with the reviewed shared-code changes. Verify missing/invalid/stale tokens, role separation, same-store images, and media-delete races. No category SQL is included.
4. Distribute an Android debug build only after the server contract is live and validated. Keep version 1.0.121; no release or publication is authorized here.

## Isolated validation required before deployment

In one staging or rollback-only PostgreSQL transaction, create controlled draft, submitted, under-review, rejected, changes-requested, and archived fixtures. Verify authorized submission and review, self/foreign denials, absent/soft-deleted/foreign-store/valid active images, duplicate and stale request IDs, and unchanged product/history/audit/notifications on each denial. Run concurrent approval and image deletion from two independent sessions; a single transaction cannot establish the race outcome. Roll back all QA records and verify zero residue.

Rollback-only database tests cannot prove external buyer API publication. That requires a fully isolated staging API with matching RLS and authorization; verify drafts, pending, rejected, and changes-requested products are hidden and a newly approved listing is visible only under real publication rules. No such staging branch currently exists, and local Docker/Postgres tools are unavailable. Do not publish a QA product in production to fill this gap.

## Recovery

The SQL transaction rolls back automatically on preflight or statement failure. If a post-deployment problem appears, first stop admin review traffic or restore the previous Edge version: the new seven-argument wrapper fails closed for review decisions. Inspect product, review, audit, notification, and image rows before a corrective roll-forward. Do not restore the old permissive approval RPC while review actions are available. Remove the trigger only after approval is disabled and a reviewed replacement preserves the image invariant. Existing products and media must not be deleted as rollback.

## Migration ledger

The V6 category SQL was executed directly with SHA-256 `757FA041D2CBE1077AC7B7B6F9F9AAD04018042DC4C5E9DD411A6F96DF3EAE35`; it is not in `supabase/migrations` or the remote migration ledger. The remote ledger has 63 records and the local folder has 62 files, with 12 remote-only and 11 local-only versions. Preserve the V6 execution record, compare each historical version with actual schema effects in an isolated clone, and obtain separate authorization before any ledger repair. Do not copy V6's executable body into a pending migration or run `supabase db push` meanwhile. When approved, create the V6.2 migration through the CLI workflow and reconcile the ledger before applying it.
