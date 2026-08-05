# Debug and release build results

Debug:

- `assembleDebug`: PASS, final post-fix run 121.1 seconds
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Size: 101,314,775 bytes

Release gate:

- `compileReleaseKotlin`: PASS
- `lintVitalRelease`: PASS
- `minifyReleaseWithR8`: PASS
- `assembleRelease`: PASS
- Final combined duration: 764.7 seconds
- APK: `android/app/build/outputs/apk/release/app-release.apk`
- Size: 72,671,761 bytes
- APK Signature Scheme v2 verification: PASS
