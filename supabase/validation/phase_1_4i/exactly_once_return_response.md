# Exactly-Once Return and Refund Response

Date: 2026-08-09

Status: LIVE BUYER, SELLER AND ADMIN FLOW PASSED

## Implementation

- Atomic RPC: `public.adishop_seller_respond_request`.
- Request row is locked before validation and mutation.
- Seller/store ownership is derived server-side.
- A stable idempotency event is checked before stale-version handling.
- Expected version is required and the request version increments once.
- Response, request event, audit event and buyer notification are created in one database transaction.
- RPC is `security invoker`; `anon` and `authenticated` cannot execute it; `service_role` can.
- The Android key is deterministic for request kind, request ID and expected version.

## Automated Evidence

- Same request/version creates the same key.
- A new request version creates a different key.
- Serialized response payload contains `expectedVersion`.
- Focused tests and full debug test suite passed.

## Live Validation

- Controlled order: `ADS-20260725-AFD12AFE`.
- Buyer: controlled Abel account; seller: Hulu Store.
- One refund request was created through the normal Android buyer UI.
- Initial request state: submitted, version 1, one creation event, one audit event.
- Seller A responded through the updated Android Seller Requests UI.
- Final request version: 2.
- Seller response events: exactly 1.
- Seller response audit records: exactly 1.
- Buyer response notifications: exactly 1.
- Stable original-key retry returned `idempotent=true` with version 2 and created no extra row.
- New-key stale version was rejected with `stale-seller-request`.
- Foreign Seller B profile was rejected with `seller-request-not-found`.
- Order and order-group payment states remained `unpaid`; no refund or payout mutation occurred.
- Notification data keys were limited to `requestId` and `mutationId`.

- Buyer UI displayed the single server-provided `buyerVisibleUpdate` after a narrow Android rendering fix.
- Seller B Abel mobile Requests displayed no Hulu Store request.

- Authorized Shop super-admin Refunds displayed the controlled request and seller response.
- Corrected independent database counts confirmed one response audit and one response event.
