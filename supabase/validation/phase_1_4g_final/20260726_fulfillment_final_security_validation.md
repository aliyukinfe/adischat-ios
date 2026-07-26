# Phase 1.4G.1 Fulfillment Final Security Validation

Date: 2026-07-26

## Baseline

- Branch: `adisgebeya/phase-1.4d.1-stabilization`
- Starting HEAD: `8a74f58122da5770c64f4eff7f3038644c8f5127`
- Controlled delivered order: `ADS-20260726-95E3DA09`
- Controlled order id: `217a9761-5087-4be7-9f5e-32d48877b1c3`
- Controlled order group id: `a8d99302-d633-4bdb-970c-c7e5f950c775`
- Controlled buyer profile: `6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee`
- Controlled seller profile: `98118bab-7f68-468d-9b28-75bf9ccfe12e`
- Controlled store: `hulu store`

## Delivery-Code Regression

- Order status: `delivered`
- Group status: `delivered`
- Delivery status: `delivered`
- Delivery-code verified: `true`
- Plaintext code rows: `0`
- Delivery confirmed timeline events: `1`
- Delivery verified audit rows: `1`
- Delivered buyer notifications: `1`
- Negative inventory rows: `0`
- Negative reservation rows: `0`
- Delivered order backward transition probe: denied with `invalid-transition`
- Delivered buyer cancellation probe: denied with `cancellation-not-eligible`

## Seller Ownership Isolation

Seller B profile `10000000-0000-4000-8000-000000014e01` was tested against Hulu Store order group `a8d99302-d633-4bdb-970c-c7e5f950c775` inside a rolled-back transaction.

Denied safely with `seller-order-not-found`:

- accept
- reject
- processing
- packed
- handed_to_carrier
- verify_delivery_code

No production mutation was committed.

## Buyer Ownership Isolation

Buyer B profile `1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9` was tested against order `ADS-20260726-95E3DA09` inside a rolled-back transaction.

Denied safely with `order-not-found`:

- reveal delivery code
- cancel order

No production mutation was committed.

## Admin Order Detail

Added migration `20260726125558_adishop_phase_1_4g_admin_order_detail.sql`.

Added RPC:

- `adishop_admin_get_order(p_profile_id uuid, p_order_id uuid)`

Security:

- permission gate: `order.support.read`
- grants: `service_role` only, plus owner `postgres`
- excludes delivery-code plaintext, salt and hash

Added Edge Function:

- `adishop-admin-order-detail`
- deployed ACTIVE version `2`
- `verify_jwt=false`

Added native Android support:

- Admin Orders card opens native detail
- detail shows order state, payment, address summary, groups/items, inventory, timeline, delivery confirmation status, support notes and audit
- no WebView
- `:app:compileDebugKotlin` passed

Live Super Admin validation:

- `adishop-admin-orders` initially returned `orders`, while Android expected `adminOrders`; Admin Orders rendered empty even though the RPC returned 13 rows.
- Patched `supabase/functions/_shared/adishop.ts` to return `adminOrders`.
- Redeployed `adishop-admin-orders` ACTIVE version `2`, `verify_jwt=false`.
- Device package `com.adischat.app` opened AdisGebeya Admin -> Orders.
- Orders list displayed `ADS-20260726-95E3DA09`, `ADS-20260725-AFD12AFE`, `ADS-20260724-69AAD80A`, and other controlled orders.
- Opened native Order Detail for `ADS-20260726-95E3DA09`.
- Detail displayed delivered state, `hulu store`, payment due on delivery, ETB 20,000 total, timeline through delivery confirmed, delivery confirmation revealed/verified flags, attempt count `1`, and inventory `9 available, 0 reserved`.
- UI dump did not expose plaintext delivery code, delivery-code hash, salt, service-role data, or token material.

## Known Blockers

- Live seller account-switch cache validation is still required.
- Live buyer account-switch cache validation is still required.
- A fresh Hulu Store pending order is required for rejection, cancellation and inventory restoration live validation.
- Buyer Protection issue reporting tables/functions were not found in the live schema during audit, so issue reporting is still not validated.
- Full live permission-matrix validation is still required for Support Agent and non-admin seller account states.
- `:app:testDebugUnitTest` passed.
- `:app:assembleDebug` passed.
- `:app:assembleRelease` did not complete within the command timeout and the lingering Gradle/Java process was stopped; release build is not validated.
- Focused secret scan reported references to environment variable names, migration grant text, validation docs and helper scripts; no literal secret value was printed into this report.
- Scoped commits are not complete yet.

## Additional Security Checks

- Missing unauthenticated request to `adishop-admin-order-detail` returned 401 with a friendly sign-in message.
- Phase 1.4G migration history rows exist for:
  - `20260726114945_adishop_phase_1_4g_secure_delivery_codes`
  - `20260726122350_adishop_phase_1_4g_delivery_code_salt_fix`
  - `20260726123207_adishop_phase_1_4g_repeatable_delivery_code`
  - `20260726125558_adishop_phase_1_4g_admin_order_detail`
- Sensitive RPC grants verified remotely:
  - `adishop_admin_get_order`: anon false, authenticated false, service_role true
  - `adishop_reveal_delivery_code`: anon false, authenticated false, service_role true
  - `adishop_verify_delivery_code`: anon false, authenticated false, service_role true
- RLS is enabled on:
  - `shop_orders`
  - `shop_order_groups`
  - `shop_order_items`
  - `shop_order_status_history`
  - `shop_notifications`
- Live schema scan found no issue-reporting, dispute, return, or claim tables. Buyer Protection issue reporting remains blocked.

## Live Seller Rejection Validation

Controlled pending Hulu Store COD order:

- Order: `ADS-20260726-F8F86F38`
- Order id: `5acb57ab-c40d-40f2-a4a6-6b821c982907`
- Order group id: `d934e6d9-2167-48fd-b9a8-ab3e19cbe0aa`
- Buyer profile: `6951c7b9-6b65-4adc-a8c2-11ce5f40f7ee`
- Seller profile: `98118bab-7f68-468d-9b28-75bf9ccfe12e`
- Store: `hulu store`
- Product: `mobile`
- Quantity: `1`
- Before rejection: order `confirmed`, group fulfillment `awaiting_seller_confirmation`, inventory `8 available, 0 reserved`, history count `1`, notification count `1`, seller audit count `0`.

Live seller UI evidence:

- Seller account displayed `hulu store` and opened Seller Center -> Orders.
- Order `ADS-20260726-F8F86F38` was visible with `Awaiting seller confirmation`, `Payment due on delivery`, and buttons `Accept` and `Reject`.
- Seller tapped `Reject` on the connected device.

Server result:

- Order status became `cancelled`.
- Order fulfillment status became `rejected`.
- Group status became `rejected`.
- Group fulfillment status became `rejected`.
- Rejection reason stored as `Seller rejected order`.
- `inventory_released_at` set once.
- Inventory restored from `8 available, 0 reserved` to `9 available, 0 reserved`.
- History events: `order_placed`, `seller_rejected`.
- Seller rejection event count: `1`.
- Buyer notifications: `order_placed`, `seller_rejected`.
- Seller rejection notification count: `1`.
- Audit rows for the group: `seller_order_group.reject` count `1`.

Duplicate and invalid-transition evidence:

- Duplicate Reject probe was denied with `invalid-transition`.
- Counts remained stable after duplicate Reject: history `2`, seller rejection events `1`, notifications `2`, seller rejection notifications `1`, audit `1`, reject audit `1`.
- Rejected terminal transition probes for `accept`, `processing`, `packed`, `ready_for_pickup`, and `handed_to_carrier` were all denied with `invalid-transition`.

Remaining rejection gap:

- The current seller UI performs rejection with a default reason instead of prompting for an explicit reason code. The server stored a reason, but the stricter Phase 1.4G.1 requirement for explicit reason-code UX is not fully satisfied.

## Live Buyer Rejected Order Validation

Buyer A device validation for `ADS-20260726-F8F86F38`:

- Buyer A opened AdisGebeya -> Account -> My Orders.
- Order card displayed:
  - `ADS-20260726-F8F86F38`
  - `Cancelled`
  - `Rejected`
  - `Payment due on delivery`
  - `ETB 20,000`
- `Track` remained available.
- `Delivery code` action was visible but disabled.

Server result:

- Order status: `cancelled`
- Order fulfillment status: `rejected`
- Group fulfillment status: `rejected`
- Buyer notifications for the order:
  - `order_placed`
  - `seller_rejected`
- Delivery-code record count: `1`
- Revealed delivery-code count: `0`
- Verified delivery-code count: `0`

Buyer-facing rejection gap:

- The buyer sees `Cancelled` and `Rejected`, but the UI does not show the seller rejection explanation text directly on the order card.

## Buyer B Isolation Validation

Buyer B device validation:

- Buyer B opened AdisGebeya -> Account -> My Orders.
- Buyer B saw their own order `ADS-20260725-AFD12AFE`.
- Buyer B did not see Buyer A order `ADS-20260726-F8F86F38`.
- No stale Buyer A rejected order card, timeline, delivery state, or notification was visible in the order list.

Server isolation checks:

- Buyer B order rows for `ADS-20260726-F8F86F38`: `0`
- Buyer B notifications for `ADS-20260726-F8F86F38`: `0`
- Direct Buyer B probe against Buyer A delivery-code reveal: denied with `order-not-found`
- Direct Buyer B probe against Buyer A cancellation: denied with `order-not-found`

Post-probe mutation check for `ADS-20260726-F8F86F38`:

- History count remained `2`.
- Notification count remained `2`.
- Audit count remained `1`.
- Cancellation count remained `0`.

## Seller B Isolation Validation

Seller B device validation:

- Seller B account displayed store `Abel mobile`.
- Seller B dashboard showed `0` orders and `0` new orders.
- Seller B did not see Hulu Store order `ADS-20260726-F8F86F38`.
- Seller B did not see Hulu customer information, product item `mobile`, delivery address, phone number, or fulfillment actions for the Hulu order.

Server isolation checks:

- Seller B profile: `1dcac349-9ab1-42dd-a9f6-7a11c97c3ea9`
- Seller B store: `Abel mobile`
- Direct Seller B probes against Hulu order group `d934e6d9-2167-48fd-b9a8-ab3e19cbe0aa` were all denied with `seller-order-not-found`:
  - `accept`
  - `reject`
  - `processing`
  - `packed`
  - `ready_for_pickup`
  - `handed_to_carrier`
  - `verify_delivery_code`

Post-probe mutation check for `ADS-20260726-F8F86F38`:

- History count remained `2`.
- Notification count remained `2`.
- Audit count remained `1`.
- Delivery attempt count remained `0`.

No secrets, tokens, service-role keys, passwords or plaintext delivery codes are stored in this evidence file.
