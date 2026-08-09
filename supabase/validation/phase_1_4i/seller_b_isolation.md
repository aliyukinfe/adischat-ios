# Seller B Isolation

- Server ownership is derived from the authenticated Shop profile and store ownership/membership
- Client seller/profile IDs are not trusted
- Database inventory RPC repeats ownership verification
- Foreign-store database probe: denied before mutation
- Live Seller B identity: PASS; connected device showed approved store `Abel mobile`
- Account-switch cache clearing: PASS; Hulu Store's six orders, two inventory rows, requests, customers, reviews, coupons and messages were absent
- Seller B owned data: one draft product, zero orders, zero requests, zero customer notes, zero reviews, zero coupons and zero conversations; device and database matched
- Direct Hulu Store dashboard probe using Seller B's authenticated Shop session: PASS; server returned 403 without private payload data
- Probe-session cleanup: PASS; the temporary session was revoked after validation
- No Seller B mutation was performed
- Final isolation gate: PASS
# Phase 1.4I.2 Addendum - 2026-08-10

- Controlled Abel account opened Seller Account for the separate `Abel mobile` store.
- Seller Requests returned an empty state and did not display the Hulu Store refund request.
- Server-side foreign Seller B response was rejected as not found.
- Buyer order access remained available only in the buyer context and displayed the single public seller update.
