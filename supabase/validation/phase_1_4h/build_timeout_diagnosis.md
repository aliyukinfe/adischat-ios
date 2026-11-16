# Phase 1.4H.3 Build Timeout Diagnosis

Date: 2026-07-29

## Starting Point

- Branch: `adisgebeya/phase-1.4d.1-stabilization`
- HEAD: `b922f01`
- Android version was not changed.
- Google Play publish was not attempted.

## Environment

- Gradle wrapper: 9.4.1
- Android Gradle Plugin: 9.2.1
- Kotlin Android plugin declared: 2.0.21
- Compose Kotlin plugin declared: 2.0.21
- Gradle configured JVM: `C:\Users\adema\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot`
- Shell default Java/Javac observed: JDK 25.0.3
- Physical memory: about 7.88 GB total, about 2.41 GB free during diagnosis
- Disk free on C: about 27.5 GB

## Timeout Reproduction

Reproduced with:

```text
gradlew.bat :app:compileDebugKotlin --stacktrace --info --console=plain --no-daemon
```

Observed:

- Build reaches `:app:compileDebugKotlin`.
- Kotlin source listing completes.
- Kotlin daemon is started with JDK 17 and `-Xmx3072m`.
- The build then stalls until the external timeout kills the command.
- Java/Kotlin compiler process remains CPU-active with a very large working set.

## Evidence

Saved diagnostic logs:

- `compile_debug_kotlin_timeout_info_20260728_234131.log`
- `compile_debug_kotlin_jdk17_launcher_20260729_000132.log`
- `compile_debug_kotlin_jdk25_diagnostic_20260729_001339.log`

Kotlin daemon JVM crash evidence:

- New `hs_err_pid*.log` files were observed under `C:\Users\adema\AppData\Local\kotlin\daemon`.
- The newest inspected crash showed `EXCEPTION_ACCESS_VIOLATION (0xc0000005)` in `jvm.dll`.
- The crash occurred in Temurin JDK 17.0.19 while running `org.jetbrains.kotlin.daemon.KotlinCompileDaemon`.
- No secrets or private account data were captured.

## Minimal Fix Attempted

- Added explicit return type to the local seller-application request builder.
- Added explicit type for seller-application progress labels.
- Split the new seller application Compose block into `ShopSellerApplicationComponents.kt`.
- Left behavior intact and did not remove Phase 1.4H functionality.

Result:

- `:app:compileDebugKotlin` still timed out.

## Current Root Cause Assessment

The blocker is not a normal Kotlin syntax error. The build stalls inside Kotlin compilation and the Kotlin daemon has produced JVM crash logs. The strongest evidence points to JVM/Kotlin daemon instability under memory pressure on this machine, likely aggravated by the very large Compose source set.

## Gate Status

Debug compile did not pass, so the following were not run:

- debug unit tests
- assembleDebug
- release Kotlin compile
- R8
- assembleRelease
- APK install
- live buyer/seller/applicant/admin validation
- scoped commits

