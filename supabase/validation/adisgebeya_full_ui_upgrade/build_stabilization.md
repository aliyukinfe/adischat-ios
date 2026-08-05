# Build stabilization

- Branch: `adisgebeya/phase-1.4d.1-stabilization`
- Starting HEAD: `b922f016d0ceb6fb232cd5f3ded3c2b95ce84ee6`
- Android version preserved: `1.0.121` (`versionCode 121`)
- Gradle heap: `-Xmx3072m`; no SerialGC or in-process compiler override.
- Root cause: `ShopScreen.kt` mixed buyer, cart, store, seller order, seller product, and seller dashboard UI in one compilation unit.
- Result: normal debug and release Kotlin compilation completes without daemon or JVM failure.
