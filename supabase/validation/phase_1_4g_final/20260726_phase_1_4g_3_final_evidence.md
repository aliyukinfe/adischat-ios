# AdisGebeya Phase 1.4G.3 Final Evidence

## Baseline

- Branch: `adisgebeya/phase-1.4d.1-stabilization`
- Starting HEAD: `8a74f58122da5770c64f4eff7f3038644c8f5127`
- Android version remained `1.0.121`, versionCode `121`.
- Google Play publishing was not performed.

## ADB And Install

- Initial root cause: `adb` was not on PATH.
- Active ADB: `C:\Users\adema\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- ADB version: `37.0.0-14910828`
- Connected device: `RZCT7061S7E`, model `SM_M336BU`
- Debug APK installed in place with `adb install -r`.
- App launched and stayed running.

## Device Validation

- Buyer order detail for `ADS-20260726-95E3DA09` showed delivered timeline, Buyer Protection, and issue status `Under review`.
- Buyer UI did not show private Admin note, plaintext delivery code, delivery hash, raw UUID, or raw backend error.
- Seller delivered order detail opened without crash and did not expose plaintext delivery code or hash.
- Seller rejection UI on pending order `ADS-20260726-EC3BB016` required explicit reason selection before submit.
- Cancel closed rejection dialog without mutation.
- Rejection with `Duplicate order` moved the order out of the New queue.
- Admin order detail for `ADS-20260726-95E3DA09` showed Buyer Protection issue, Admin status event, audit area, delivered state, and delivery confirmation booleans only.
- Account switch Admin -> Buyer returned to buyer-only order list with no seller/admin controls visible.

## Migration History

- Official `supabase migration list --linked` and `supabase migration repair` timed out through the direct Postgres path.
- Supabase SQL query endpoint remained healthy.
- Remote schema was verified before metadata repair.
- Metadata-only repair inserted missing history records for:
  - `20260726141304_adishop_phase_1_4g_2_buyer_protection_rejections`
  - `20260726181606_adishop_phase_1_4g_2_admin_issue_support`
- All Phase 1.4G migration versions are now present in `supabase_migrations.schema_migrations`.

## Backend Security And Invariants

- Missing-session probes returned `401` for buyer issue creation, buyer issue options, admin issue update, and seller reject order group.
- `shop_order_issues` and `shop_order_issue_events` have RLS enabled.
- Sensitive RPCs deny anon/authenticated execution and allow service role execution only.
- Delivered order `ADS-20260726-95E3DA09`:
  - order/group/delivery statuses are `delivered`
  - completed timestamp present
  - delivery code revealed and verified
  - plaintext code is null
  - code hash is stored
  - successful delivery attempts: `1`
  - verify audit count: `1`
- Issue `d64f146a-99ee-45a4-86a1-178eb3e29d90`:
  - issue count: `1`
  - status: `under_review`
  - Admin issue event count: `1`
  - Admin audit count: `1`
- Rejected order `ADS-20260726-EC3BB016`:
  - order status: `cancelled`
  - group status: `rejected`
  - reason code: `duplicate_order`
  - buyer-visible reason: `Duplicate order`
  - seller rejection history count: `1`
  - seller rejection audit count: `1`
  - buyer notification count: `1`

## Build Regression

- `:app:compileDebugKotlin`: PASS
- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug`: PASS
- `:app:compileReleaseKotlin`: PASS
- `:app:lintVitalRelease`: PASS
- `:app:minifyReleaseWithR8`: PASS
- `:app:assembleRelease`: PASS
- Previous JVM `EXCEPTION_ACCESS_VIOLATION` did not reproduce in this final run.

## Evidence Files

- `20260726_fulfillment_final_security_validation.md`
- `20260726_phase_1_4g_3_final_evidence.md`
- Raw screenshots and UI XML dumps were used transiently for validation, then removed because they can contain buyer contact details.

## Remaining Limitations

- Official Supabase direct Postgres migration-list/repair CLI still times out from this machine, although remote migration history is query-verified after metadata repair.
- Seller-side Buyer Protection issue visibility was not visible in the seller delivered-order screen captured during this pass.
