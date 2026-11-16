# Seller Center V5 store-scope audit

Status: approved V5 SQL applied as remote migration `20261026201025`;
seven existing product Edge Functions deployed and the core A/B device checks passed.
The wider Seller Center gate remains blocked by the verification gaps below.

## Root cause and affected operations

The product RPC helper `adishop_seller_owned_store(uuid)` selects the first
approved store. The existing product list, detail, create, update, variant
catalog save, and submit RPCs all use that helper. The Android business-suite
store selection was not propagated to product requests or product UI state.

| Operation | Prior behavior | Prepared correction |
| --- | --- | --- |
| Product list | First approved store, regardless of selected store | Explicit store-scoped RPC; server echoes scope; Android rejects mismatches and stale responses. |
| Product detail | First-store-only RPC | Explicit store-scoped RPC; ownership checked in the helper. |
| Product create | First approved store, no store ID | Selected store ID required for multi-store users; new RPC checks approved ownership before insert. |
| Draft save/archive | First-store-only update RPC | Explicit store-scoped wrapper; resource store must match selected store. |
| Variant/SKU/specification save | First-store-only catalog RPC, including nested category core | Store-scoped wrapper; transaction-local scope persists through nested validation. |
| Submit for review | First-store-only submit RPC | Store-scoped wrapper; existing submission validation and status history retained. |
| Media upload/delete/reorder | Product/owner checked in Edge; delete/reorder response used first-store get RPC | Existing owner check kept; response re-read through store-scoped get RPC. Android refuses stale-store results. |
| Inventory | Business endpoint and update RPC accept store ID and validate ownership | Existing server logic retained; clear old-store UI on switch. |
| Seller notifications | Store ID already authenticated and scoped | Existing server logic retained; clear old-store inbox and unread count on switch. |
| Seller orders/fulfillment | Server lists all authorized stores; detail and transition verify group ownership | Android tab filters to selected store; server ownership checks retained. No customer-order mutation for V5 tests. |
| Seller messages | Server lists all authorized stores and verifies conversation participants | Android tab filters to selected store and clears old conversation on switch. |
| Seller requests | Existing endpoint returns requests for authorized seller stores, with order-group IDs but no direct store field in DTO | Android now displays only requests whose order group belongs to the selected store; unknown groups fail closed. Endpoint still returns all stores authorized to the seller. |

The migration uses a transaction-local `adishop.selected_store_id` only inside
new service-role-only wrappers. The helper independently validates that the
selected store is active, approved, and owned or managed by the authenticated
profile. Legacy calls without a store ID continue for a uniquely authorized
seller; ambiguous multi-store calls fail instead of silently choosing a store.
No client-supplied store ID can override a product's existing `store_id`.

## Deployment and rollback

1. Explicit approval was received for this V5 SQL and scoped Edge deployment.
2. Recheck the live definitions and grants of the seven product RPCs, store
   memberships, and migration history. Abort on drift. Take a schema backup.
3. Apply only `20261026201025_adisgebeya_explicit_seller_store_scope.sql`.
   Verify wrappers are executable by `service_role` but not `anon` or
   `authenticated`. Verify missing/foreign/ambiguous stores are rejected.
4. Deploy only the existing product-related Edge Functions sharing
   `_shared/adishop.ts`; do not create a new function. Confirm the list response
   echoes `sellerProductStoreId` and all mutations route through the wrappers.
5. Install the 1.0.121 debug Android build and run Store A/B, foreign-owner,
   invalid-session, account-switch, and media/variant regression tests.
6. If step 4 fails, restore the prior Edge deployment before reverting SQL.
   If SQL must be rolled back, remove new wrappers and restore the prior helper
   only after the old Edge version is active. Never revert while the new Edge
   routes still call the wrappers. No product or order rows should be deleted.

The Android client refuses product lists without a matching server scope echo.
It must not be installed for active sellers before the Edge deployment. Build
success alone is not proof of production readiness.

## Live validation still required

### Completed on 2026-10-26

- Remote migration history contains `20261026201025`. All six new wrappers
  grant `service_role` and deny `anon` and `authenticated`.
- Seven existing product functions are ACTIVE with `verify_jwt=false` and
  internal Shop-session authentication: products/detail/create/submit v12,
  update v16, delete-media v13, reorder-media v11. No function was added.
- The Store A list RPC returned 17 products, all assigned to Store A; the
  Store B list returned zero. Android showed Store A products and an honest
  empty Store B list after switching. A second switch restored Store A only.
- One unpublished QA draft was created through Android under each store.
  Database ownership matched the selected store. Both were archived through
  Seller Center immediately after this check; neither was submitted.
- Authenticated Abel device probes denied Aliyu's Store A list, detail,
  create, edit/archive, inventory update, business context, and notifications.
  Missing and invalid Shop tokens returned 401. An in-memory bearer replay
  after controlled session revocation returned 401.
- Android `compileDebugKotlin`, `testDebugUnitTest` (135/135), `assembleDebug`,
  and `assembleDebugAndroidTest` passed. The debug app and test APK were
  installed on Samsung RZCT7061S7E. The focused device tests passed with
  Aliyu and Abel. Backend contract tests passed 3/3.
- Abel -> Aliyu -> Abel account switching was exercised without a cross-owner
  product result. No real customer order, payment, or inventory row was
  changed. No QA product was published.
- After a further selected-store Requests filter change, the debug build was
  installed again on the reconnected Samsung. Seller Center opened normally;
  recent logcat contained no app FATAL EXCEPTION or ANR.
- With Aliyu, Store B's existing seller notification was visible only when
  Store B was selected. Switching to Store A showed Store A's own notification
  and not Store B's. The Store B record has no order destination, so it was
  not used as evidence of a working order deep link.

### Not yet verified

- Expired Shop-session denial was not exercised with a controlled expired
  bearer; missing, invalid, and revoked cases were exercised.
- Product draft save, variant/image round-trip, and media actions under both
  store selections were not rerun in this V5 pass.
- Direct foreign order-detail and fulfillment denial and selected-store seller
  notification/message deep links were not established end to end. Requests
  filtering has a unit test but was not exercised with live multi-store
  request records (neither controlled store has refund/exchange requests).
  Its endpoint still returns all authorized stores.

## V5.1 follow-up, 2026-10-27

- PASS: An Aliyu QA Shop session was expired in `shop_sessions` while its bearer
  remained only in device test memory. Product list/create/edit, inventory,
  seller notifications, business data, and seller orders all returned 401.
- PASS: Two unpublished QA drafts, one per approved Aliyu store, accepted a
  controlled PNG upload. Public image retrieval, variant-image association,
  and detail reload succeeded in both stores. Cross-store product detail and
  update requests were denied. A Store A image could not be associated with
  Store B's variant, and the denied request left Store B `updated_at` unchanged.
- The first cross-image probe exposed a 500 response and a potential partial
  base-product update before variant validation. The existing
  `adishop-seller-update-product` function alone was patched to prevalidate
  selected-store/product/image ownership before the base write and to map the
  database mismatch to a safe 404. Its remote version is 17, ACTIVE, with
  `verify_jwt=false` and internal Shop-session validation. No new function or
  category migration was deployed; the project still has 100 functions.
- Cleanup checked remotely: all six V5.1 QA drafts from diagnostic/retest
  runs are archived; no active QA image rows or QA Storage objects remain.
- PASS: A read-only authenticated Abel probe excluded Aliyu's controlled
  Store A validation order from the seller list and returned 404 for its
  direct order-group detail. No fulfillment mutation was sent.
- NOT VERIFIED: Live fulfillment-transition authorization and invalid-state
  rejection require an isolated QA order; the existing controlled order was
  not mutated. Store B has no order group.
- NOT VERIFIED: Store B's sole notification has only `storeId` and QA markers,
  with no genuine destination. No deep link was invented or tapped.
- PASS: Empty selected-store Requests behavior and cross-store filtering have
  Android unit coverage. NOT VERIFIED live with nonempty requests: both
  controlled stores currently have zero refund and exchange requests.
- Regression: six focused Node contract tests, 136 Android unit tests,
  `compileDebugKotlin`, `assembleDebug`, and the targeted Samsung device tests
  passed. Samsung app remains debug 1.0.121; only the test APK was reinstalled.
  Recent logcat contained no AdisChat FATAL EXCEPTION or ANR.

**Gate:** Confirmed product isolation passes. Complete multi-store security
isolation and Seller Center stabilization remain blocked by the unverified
order/fulfillment, Store B destination, and separate category/workflow gates.

## V5.2 follow-up, 2026-10-27

- PASS: Targeted Samsung test `unpublishedMediaAndVariantsStayInTheirStore`
  passed (1 test, 24.931 seconds) with Aliyu's two approved stores. The
  deployed update-product v17 returned HTTP 404 for a Store A image used in
  a Store B variant. Store B title, price, inventory, variants, images, and
  `updated_at` were unchanged. Legitimate same-store upload, image retrieval,
  variant association, and reload succeeded in both stores. Read-only cleanup
  checks found zero active QA image rows and zero QA Storage objects; QA drafts
  were archived. No listing was submitted or published.
- PASS in rollback-only synthetic, zero-value, no-item/no-payment database
  fixtures: Aliyu accepted a Store A pending group and advanced it to
  processing. Abel's foreign-owner acceptance was denied with
  `seller-order-not-found`; `packed` from awaiting confirmation was denied
  with `invalid-transition`. Denied attempts left the group/order JSON,
  history count, and notification count unchanged. The transactions were
  rolled back and no V5.2 fixture order persisted. These checks do not prove
  the Android-to-Edge fulfillment path end to end.
- FAIL selected-store context: with Store A selected in a rollback-only test
  context, Aliyu could accept a synthetic Store B group because he also owns
  Store B. The order transition API/RPC authorizes store ownership but does
  not receive or enforce the selected store ID. This is not a foreign-owner
  bypass, but it fails the requirement that the wrong selected context be
  rejected. The successful test mutation was rolled back.
- NOT VERIFIED: Store B's existing seller notification has no genuine
  destination ID. There is no safe Store B QA order group to tap into. Android
  destination, Back, and account-switch behavior cannot be claimed from this
  record. A genuine Store B QA notification with an authorized destination is
  needed; no destination was invented.
- Regression: six focused Node tests, 136 Android unit tests,
  `compileDebugKotlin`, `assembleDebug`, and `assembleDebugAndroidTest` passed.
  Only the test APK was reinstalled because no main Android code changed.
  The production Edge Function count remains 100. No category SQL, new
  function, deployment, release build, version bump, real customer-order
  mutation, or product publication occurred.

**V5.2 gate:** Product image pre-write authorization and owner-based order
checks pass within the stated scopes. Complete multi-store isolation remains
blocked by selected-store fulfillment context and the unverified Store B
notification deep link. Seller Center stabilization remains independently
blocked by category configuration and other workflow gates.

## V5.3 proposed fulfillment store scope, 2026-10-27

- Root cause: the seller order Edge handlers accepted only an order-group ID;
  the existing transition and delivery RPCs authorized seller ownership but
  did not receive the store selected in Seller Center. Multi-store owners
  could therefore act on a different owned store's group.
- Local fix: list and detail reads, six status transitions, delivery-code
  verification, seller request reads, and refund/exchange responses resolve an
  explicitly selected, authorized store. A missing store remains compatible
  only when the seller has one authorized store. Mismatched groups/requests
  return a safe 404; ambiguous multi-store requests return 400.
- Proposed migration
  `20261027134112_adisgebeya_seller_order_store_scope.sql` adds three
  service-role-only wrappers. Each locks the group/request row, checks actual
  versus selected store and seller authority, then calls the existing atomic
  transition, delivery, or response RPC in the same transaction. The state
  machines and stored order schema are unchanged. No production SQL was run.
- Android passes `storeId`, verifies the order-list response's scope echo,
  clears orders and requests on store change, and ignores late responses for
  a prior store. The compiled debug APK is not installed yet: it requires
  the proposed backend contract, and installing it early would break seller
  order actions.
- Notification review: the real `seller_order_received` trigger includes
  `orderGroupId`; the notification-scoping trigger derives `store_id` from
  that group. The existing Store B QA marker lacks a destination and is not
  proof of an Android deep link. No notification migration is proposed.

### Approval and rollout required

1. Review the three wrappers and their grants. Apply only the V5.3 migration
   after explicit approval; verify service-role execution and anon/
   authenticated denial. Do not apply category metadata SQL.
2. Deploy only these existing functions after the migration: list/get seller
   order group; accept, reject, processing, packed, ready-for-pickup, hand-to-
   carrier; verify delivery code; seller requests; respond refund/exchange
   request. Keep gateway `verify_jwt=false` with internal Shop-session checks.
3. Install the matched debug Android build, then run controlled rollback-only
   or isolated QA orders across Store A, Store B, and a foreign seller. A
   coordinated rollout is necessary: older multi-store clients without a
   selected store will receive 400 until upgraded.
4. On Edge failure, restore the previous versions of these functions before
   reverting the wrapper migration. Do not remove wrappers while deployed
   handlers still call them. No order, history, inventory, payment, or
   notification rows should be deleted as a rollback mechanism.

**V5.3 gate:** Local source and build checks do not establish a production
security pass. Production approval, live rollback-only scope tests across
all actions, and a genuine Store B notification deep link remain required.

## V5.3 approved rollout and verification, 2026-10-27

- The user explicitly approved only the scoped V5.3 SQL and 12 affected
  existing Edge Function deployments. The migration was applied to project
  `mcewjfnkrtiiolzibsuh` as remote version `20261027134112`. The three
  scoped wrappers are executable by `service_role` only; `anon` and
  `authenticated` have no execute grant. No category metadata SQL ran.
- The 12 seller-order/request functions were deployed individually and
  verified ACTIVE with `verify_jwt=false` and internal Shop-session checks.
  The project still has 100 Edge Functions. Missing and malformed bearer
  credentials on the order-list endpoint returned 401.
- PASS: A rollback-only, zero-value, no-item/no-payment fixture covering both
  controlled stores verified Aliyu's authorized Store A accept, processing,
  packed, ready-for-pickup, handed-to-carrier, and Store B accept. A separate
  rollback-only fixture verified authorized seller rejection. Every
  wrong-selected-store attempt across those six actions was denied with
  `seller-order-not-found`; Abel's foreign-owner accept, missing store,
  invalid store, and packed-before-accept were denied. Wrong-store delivery
  verification was denied before inspecting the code. Denied actions did
  not alter group status or history count; the delivery/rejection denial
  fixture also left notification count unchanged. Transaction rollback left
  zero durable V5.3 QA orders. No real order, payment, or inventory changed.
- PASS: Android `compileDebugKotlin`, `testDebugUnitTest` (138 tests, zero
  failures), and `assembleDebug` completed; the matched 1.0.121 debug APK
  installed on Samsung RZCT7061S7E. Seller Center and Orders opened without
  a crash. The instrumentation APK builds after updating the expired-session
  test to pass the now-required store ID. The non-mutating Samsung
  `selectedStoreScopesSellerOrdersBeforeMutation` test passed (1 test,
  26.509 seconds), including Store A/B list scope and wrong/missing/invalid
  store denial at the deployed Edge API. Six focused Node contract tests
  passed. Recent logcat contained no AdisChat FATAL EXCEPTION or ANR.
- NOT VERIFIED: Store B has one seller-order notification but it contains no
  real order-group, product, or request destination. A genuine Store B
  notification deep link and its Back/account-switch behavior cannot be
  tested from that marker. No destination was invented or notification sent.
- NOT VERIFIED: The scoped delivery-code wrapper's positive path and the
  refund/exchange response wrappers' positive paths have no isolated QA
  code/request fixture. Their selected-store preconditions and grants were
  reviewed, and wrong-store delivery verification was denied, but no real
  customer delivery or request was modified to make these tests pass.
- The unrelated remote migration-history drift remains; the scoped migration
  was applied directly after review rather than using `db push`. No migration
  history was rewritten. Older multi-store clients that omit `storeId` now
  receive 400 for affected seller operations and require the updated Android
  client. The separately proposed category metadata remains unapplied.

**Current gate:** Production selected-store fulfillment authorization and
rollback-only transitions pass. Full multi-store isolation remains blocked
by the missing genuine Store B notification deep-link verification and the
unverified positive delivery/request workflows. Seller Center stabilization is
separately blocked by category configuration and workflow gates.
