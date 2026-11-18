package com.adischat.app

/**
 * Global feature toggles.
 *
 * Wallet / payments are temporarily hidden across the app. All the wallet code,
 * screens and backend remain in place - only the user-facing entry points are gated
 * behind [WALLET_ENABLED]. Flip this back to `true` to resume the wallet experience.
 */
object FeatureFlags {
    const val FEATURE_WALLET_ENABLED = false
    const val WALLET_ENABLED = FEATURE_WALLET_ENABLED
    const val VIDEO_CALLS_ENABLED = false

    // AdisChat Restaurants — QA & Offline Preview gating only.
    // Release and Debug builds keep all Restaurants flags strictly disabled.
    val RESTAURANTS_DINE_IN_ENABLED: Boolean
        get() = BuildConfig.QA_BUILD || BuildConfig.OFFLINE_QA_PREVIEW
    val RESTAURANT_CENTER_ENABLED: Boolean
        get() = BuildConfig.QA_BUILD || BuildConfig.OFFLINE_QA_PREVIEW
    val RESTAURANT_ADMIN_ENABLED: Boolean
        get() = BuildConfig.QA_BUILD || BuildConfig.OFFLINE_QA_PREVIEW
    val RESTAURANTS_QA_PREVIEW_ENABLED: Boolean
        get() = BuildConfig.QA_BUILD || BuildConfig.OFFLINE_QA_PREVIEW

    // Phase H1 — Super App Home & Adaptive Sidebar
    // Production release and QA builds both run unified Super App navigation.
    var SUPER_APP_HOME_ENABLED: Boolean = true
}
