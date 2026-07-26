# AdisGebeya Phase 1.4G.4 Seller Buyer Protection Visibility

Date: 2026-07-26
Branch: adisgebeya/phase-1.4d.1-stabilization
Baseline HEAD: ace602fd06c781be762a706288ce6dfd2776597b
Device: RZCT7061S7E
Installed Android version: 1.0.121 (versionCode 121)

## Scope

This validation resolved the remaining Phase 1.4G.4 seller-side Buyer Protection issue visibility blocker and rechecked the Supabase migration CLI path.

No version bump, Play release, production data deletion, or Phase 1.4H work was performed.

## Root Cause

Seller order group APIs enriched items, buyer address, payment, history, and delivery-code state, but did not include seller-visible `shop_order_issues` data. Android also lacked a seller issue field on `ShopOrderGroupDto`, so the seller UI had no section to render Buyer Protection issues.

An initial server query attempted to select a non-existent `reason_label` column from `shop_order_issues`. The corrected implementation maps reason labels server-side from `reason_code`.

## Files Changed

- `supabase/functions/_shared/adishop.ts`
- `android/app/src/main/java/com/adischat/app/feature/shop/data/ShopModels.kt`
- `android/app/src/main/java/com/adischat/app/feature/shop/presentation/ShopScreen.kt`
- `android/app/src/test/java/com/adischat/app/feature/shop/data/ShopStateReducersTest.kt`

## Backend Change

`enrichSellerOrderGroups` now loads seller-visible order issues for the seller-owned order group and attaches sanitized issue objects to seller order group responses.

Seller-visible issue payload intentionally excludes:

- issue id
- order id
- order group id
- admin/private notes
- delivery code
- delivery code hash
- internal metadata

The response includes only:

- reason code
- reason label
- buyer description
- issue status
- buyer-visible resolution
- timestamps
- public issue events

## Android Change

Seller order group DTOs now decode `issues`.

Seller order details now render a `Buyer Protection` section when the group has issues. The section shows status, reason, buyer description, submitted timestamp, and latest public update only.

## Automated Tests Added

- `sellerOrderGroupMapsSellerVisibleIssuesOnly`
- `sellerOrderGroupNoIssueStateDefaultsToEmptyList`

The mapping test confirms Android can decode the seller-visible issue payload without requiring private identifiers.

## Deployment Verification

Redeployed only the changed seller order read functions:

- `adishop-list-seller-order-groups`: ACTIVE, version 5, verify_jwt=false
- `adishop-get-seller-order-group`: ACTIVE, version 4, verify_jwt=false

No migrations were required for this fix.

## Live Seller Device Verification

After reinstalling the debug APK and reopening the seller order details on the connected device, the seller page showed:

- `Buyer Protection`
- `Under review`
- `Item not as described`
- buyer-visible issue description
- submitted timestamp
- public issue update

No delivery code, delivery-code hash, raw issue UUID, raw order UUID, raw group UUID, admin private note, raw backend error, or raw issue enum was visible in the Buyer Protection section.

## Seller B Isolation

No live Seller B device session/token was available during this run. The server-side store ownership gate was verified remotely: Seller B is not an owner/operator of the controlled target store, so the seller store-id filter cannot return that store's order group or issue.

This is strong server-side evidence, but not a full live Seller B token probe.

## RPC Grant Verification

Sensitive marketplace RPCs remained service-role-only:

- `adishop_admin_get_order`
- `adishop_admin_update_order_issue`
- `adishop_buyer_order_issues`
- `adishop_reveal_delivery_code`
- `adishop_transition_seller_order_group`
- `adishop_verify_delivery_code`

Observed grants:

- anon: false
- authenticated: false
- service_role: true

## Migration CLI Verification

Supabase CLI version:

- installed: 2.109.1
- npm latest: 2.109.1

Project:

- `mcewjfnkrtiiolzibsuh`
- name: adisgebeya
- status: ACTIVE_HEALTHY
- linked: true

Direct linked migration command still times out:

- `npx supabase migration list --linked --password <redacted> --debug`
- result: `LegacyDbConnectError`, PostgreSQL connection timeout

Network diagnostics:

- direct host resolves only to IPv6 in this environment
- direct PostgreSQL 5432 test fails
- pooler host resolves to IPv4
- pooler 6543 and 5432 tests succeed

Official Supabase CLI migration list via supported pooler `--db-url` succeeds without printing the password:

- `npx supabase migration list --db-url <redacted pooler url>`
- result: `{ "message": "Migrations listed" }`

Phase 1.4G migration versions were aligned locally and remotely in the successful pooler-backed output:

- `20260726114945`
- `20260726122350`
- `20260726123207`
- `20260726125558`
- `20260726141304`
- `20260726181606`

Because the prompt explicitly required `supabase migration list --linked` to succeed, the exact linked-path item remains blocked even though the official CLI produced migration alignment through the pooler.

## Build Verification

First full gate attempt failed because the Gradle JVM crashed:

- JVM: Temurin OpenJDK 17.0.19
- error: `EXCEPTION_ACCESS_VIOLATION`
- problematic thread: GC thread

The same gate was retried with a fresh single-use daemon and passed:

- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:compileReleaseKotlin`
- `:app:lintVitalRelease`
- `:app:minifyReleaseWithR8`
- `:app:assembleRelease`

Final retry result: `BUILD SUCCESSFUL`.

## Gate Status

Completed:

- Seller Buyer Protection issue is visible in seller order details.
- Seller-visible issue payload is sanitized.
- Changed functions deployed and active.
- Android tests/build passed.
- Version remained 1.0.121.

Remaining blockers:

- Exact `supabase migration list --linked` still times out from this Windows environment.
- Seller B denial was verified by server-side ownership evidence, not by a live Seller B token probe.

Final gate should remain blocked until those exact live/linked requirements are satisfied.
