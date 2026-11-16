# Phase 1.4H Seller B Isolation

Date: 2026-07-29

## Device Validation

- Logged in with the separate controlled Seller B account.
- Seller B's approved store loaded.
- Returns and Requests displayed an empty state.
- Seller A's refund, buyer data, store data, product data, and timeline were absent.
- No stale Seller A request remained after the account switch.

## Live Protected Probe

`SellerRequestIsolationDeviceTest` reused Seller B's live Shop session without printing or persisting the token.

- Attempted a seller response mutation against Seller A's controlled refund.
- Endpoint returned safe HTTP 404.
- Test passed.
- Seller response remained unchanged.
- Event count remained unchanged.
- Audit count remained unchanged.
- Buyer notification count remained unchanged.

## Remaining Coverage

- No controlled exchange request currently exists, so foreign exchange-detail isolation is not live-validated.
- The current seller API does not expose seller application-detail or private store-detail routes. Unauthorized Admin-route probes remain part of the later non-Admin security checkpoint.

