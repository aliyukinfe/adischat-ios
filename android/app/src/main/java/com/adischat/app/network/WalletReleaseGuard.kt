package com.adischat.app.network

internal object WalletReleaseGuard {
    fun blocks(method: String, segments: List<String>): Boolean {
        if (segments.firstOrNull() != "api") return false
        return when (segments.getOrNull(1)) {
            "wallet" -> true
            "admin" -> segments.getOrNull(2) == "wallets"
            "merchant" -> segments.getOrNull(2) == "pay-code"
            "games" -> method != "GET"
            else -> false
        }
    }
}
