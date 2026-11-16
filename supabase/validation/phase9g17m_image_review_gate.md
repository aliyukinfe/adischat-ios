# Image review gate (local design only)

Image approval remains unavailable. The QA moderation preview returns metadata but no image bytes; the existing reviewed-media route only serves already approved media. Rejection remains allowed after an authorized metadata preview.

To enable visual review later, add a staging-only authenticated reviewer media route:

1. Verify the signed Shop session, active JTI/family/identity link, and active SHOP_ADMIN or SHOP_SUPER_ADMIN role. Reject store-owner self-review.
2. Bind the request to the image UUID and current integer image revision. Resolve bucket and object path server-side from that row; reject deleted products/stores and path mismatches. Never accept a client path or public URL.
3. Download from private Storage using a server-only credential. Prefer streaming bytes through the authenticated route with `Cache-Control: private, no-store`; avoid shareable signed URLs. If signed URLs are necessary, keep their TTL short and treat revocation-before-expiry as an explicit residual risk.
4. Record a server-side, short-lived preview authorization tied to reviewer profile, image ID, revision, and Shop session JTI. The image approval RPC must verify that authorization transactionally under the existing row lock before writing a reviewed projection. A client flag or preview response alone is not proof of viewing.
5. Invalidate preview authorization on image revision change, suspension, role removal, or session revocation. Do not log object paths, tokens, or media bytes.
6. Test old URL/cache behavior, unauthorized roles, self-review, stale revisions, revocation, and Storage failure in isolated HTTP/Auth/Storage before removing the approval gate.

No production or staging Storage policy, bucket, object, or function changes are part of Phase 9G.17m.
