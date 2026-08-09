# Phase 1.4I Final Build

Date: 2026-08-09

## Debug

- Focused contract compile/test: PASS in 5m 9s.
- Full `testDebugUnitTest`: PASS and executed (not NO-SOURCE).
- `assembleDebug`: PASS in 1m 50s with warm compilation outputs.
- Installed with `adb install -r` on `RZCT7061S7E`: PASS.
- Installed version: 1.0.121 (121).
- Launch process alive; fatal crash/ANR count in launch log window: 0.

## Release

- `compileReleaseKotlin`: PASS.
- `lintVitalRelease`: PASS.
- `minifyReleaseWithR8`: PASS.
- `assembleRelease`: PASS.
- Final post-fix release pipeline duration: 11m 36s.
- Fresh artifact: `android/app/build/outputs/apk/release/app-release.apk`.
- Artifact size: 72,737,297 bytes.
- APK Signature Scheme v2 verification: PASS.
- Scoped JWT, Supabase secret and private-key literal scan: zero findings.
- Version remains 1.0.121 (121).

No version bump and no publication occurred.
