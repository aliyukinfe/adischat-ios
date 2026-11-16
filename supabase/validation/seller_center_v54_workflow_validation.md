# Seller Center V5.4 workflow validation

## Scope and preflight

- Production project: `mcewjfnkrtiiolzibsuh`. No separate AdisGebeya staging project is available through the connected Supabase account.
- V5.3 selected-store order isolation remains deployed. No category metadata or selling-unit SQL is part of V5.4.
- The live refund table has two open requests (`submitted`, `awaiting_seller`); the exchange table has none. No existing request row is rewritten by the proposed migration.
- The schema has no `resolved` request status. Refund and exchange terminal statuses are `rejected`, `cancelled`, `completed`, and `closed`. Other allowed statuses remain eligible for a seller response.
- Applied migration: `20261027184820_adisgebeya_v54_seller_request_terminal_guard.sql`. It replaces only the service-role-only selected-store wrapper, preserving row locking, ownership checks, and the established response RPC. The two existing response Edge Functions add a safe HTTP 409 mapping. Android hides the response action for terminal requests.

## Rollback-only results

- **PASS, delivery code:** A synthetic zero-value, no-item, no-payment order and hashed code were created inside one transaction. Selected-store owner verification set the group to `delivered`, marked the code verified, and wrote one history event and one notification. Wrong-store and foreign-seller calls were denied. An incorrect code only incremented the designed failed-attempt counter; it did not change order state, inventory, history, or notifications. Duplicate verification was denied. The transaction rolled back; no `V54QA-` order persisted. The code model has no separate expiry timestamp, so code-expiration behavior is not claimed.
- **PASS, refund and exchange response:** Synthetic zero-value requests in two stores accepted authorized open responses, incremented versions, and wrote one event, audit entry, and notification each. Wrong-store, foreign-seller, and stale-version calls were denied. Duplicate idempotency keys did not duplicate events. No payment or inventory changed. The transaction rolled back.
- **FAIL in deployed V5.3, confirmed defect:** A `closed` refund request accepted a new seller response. The test transaction rolled back with zero durable QA orders.
- **PASS, proposed V5.4 preflight:** The exact proposed wrapper body was installed under `pg_temp` in a rollback-only transaction. Four terminal statuses for each of refund and exchange were denied. Full request rows (including `updated_at`), history, audit, and notification counts remained identical; payments and inventory were unchanged. Open refund and exchange requests still accepted authorized responses. No production function was replaced during this preflight.
- Notification insertion is transactional. Database triggers on the relevant tables do not call an external push service; Edge push dispatch is separate. Rolled-back QA notifications could not appear in the seller/buyer inbox.

## Approved V5.4 rollout and post-deployment checks

- The user conditionally approved this exact request-state fix after preflight. Migration `20261027184820` was applied; the local filename matches the remote history entry. No category SQL ran.
- The existing `adishop-seller-respond-refund-request` and `adishop-seller-respond-exchange-request` functions were deployed individually. Both are ACTIVE at version 4 with gateway `verify_jwt=false` and internal Shop-session checks. The project remains at 100 Edge Functions.
- Remote SQL confirms the terminal guard is present and the scoped wrapper grants remain `anon=false`, `authenticated=false`, `service_role=true`.
- The saved `seller_center_v54_request_guard_rollback.sql` test passed against the deployed wrapper. Terminal statuses for both kinds returned `seller-request-closed`; wrong-store and foreign-seller calls returned `seller-request-not-found`. Denied requests retained full row contents, including timestamps, and had no new events, audits, notifications, payment, or inventory changes. Authorized open requests succeeded. The test transaction rolled back.
- Cleanup query: zero durable `V54QA-` orders, zero `v54-` refund requests, and zero `v54-` exchange requests.
- Missing and malformed bearer credentials returned HTTP 401 from each deployed response function. An authenticated HTTP 409 for a terminal request was not exercised because no genuine terminal QA request exists outside rollback.
- Regression: eight focused Node tests and 140 Android unit tests passed; `compileDebugKotlin` and `assembleDebug` passed. Debug APK 1.0.121 installed on Samsung RZCT7061S7E; no AdisChat FATAL EXCEPTION or ANR was found in sampled logcat. The phone was signed in as Akab during the first read-only check, so Aliyu Seller Center navigation remains unverified on this build.

## Deployment and recovery

1. Apply only the reviewed V5.4 migration after the conditional preflight and regression checks pass. Verify the function definition, service-role grant, and anon/authenticated denial remotely.
2. Deploy only the existing `adishop-seller-respond-refund-request` and `adishop-seller-respond-exchange-request` functions. Keep gateway `verify_jwt=false` and internal Shop-session validation. Confirm both are ACTIVE.
3. Re-run rollback-only open/terminal, wrong-store, foreign-seller, and no-partial-write tests against the deployed wrapper. Do not modify genuine requests.
4. If the migration causes an unexpected regression, restore the previous V5.3 wrapper definition using a reviewed compensating migration. If an Edge deployment fails, redeploy its previous version first. Do not delete request/history/notification rows or rewrite existing migration history as rollback.

## Remaining live gate

Store B has one `seller_order_received` QA marker without an `orderGroupId`, product, or request destination. A rollback-only notification cannot be read by Android. No genuine Store B deep link, Back behavior, or account-switch/revocation sequence can be verified without a safe persistent QA workflow or a dedicated staging environment. Do not invent a destination or send a fake customer notification.
