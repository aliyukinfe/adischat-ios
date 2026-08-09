# Phase 1.4I Final Security Regression

Date: 2026-08-09

Status: REQUEST SECURITY PASS; REALTIME MATRIX BLOCKED

## Passed

- Seller response RPC: anon execute false, authenticated execute false, service-role execute true.
- RPC uses invoker context and locks the request row.
- Refund, exchange, messages, conversations and participant state tables have RLS enabled.
- Only `shop_messages` is in the Shop Realtime publication.
- Response functions are ACTIVE with gateway JWT verification disabled only because complete Shop-session authentication occurs internally.
- Missing Shop session probe returned HTTP 401.
- Malformed Shop session probe returned HTTP 401.
- Mutation input does not trust client seller, store, buyer or profile identity.
- No service-role credential was added to Android.

## Pending

- Secure Realtime Seller A/B live isolation, blocked by the current production auth integration.

## Live Request Security

- Seller A response succeeded once for the owned Hulu Store request.
- Original stable-key retry was idempotent.
- New-key stale-version mutation was rejected.
- Seller B foreign-owner mutation was rejected as not found.
- Exactly one public buyer notification was created with only request and mutation identifiers.
- Payment state remained unchanged and unpaid.
- Buyer displayed only the public seller update.
- Seller B's own Requests screen contained no Hulu Store request.
- Authorized Shop super-admin displayed the request and seller response with one matching audit event.
