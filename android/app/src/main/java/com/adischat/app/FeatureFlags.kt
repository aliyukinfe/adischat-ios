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
}
