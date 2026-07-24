package com.adischat.app.feature.shop.data

object ShopStateReducers {
    fun cartItemCount(cart: ShopCartDto?): Int = cart?.items.orEmpty().sumOf { it.quantity.coerceAtLeast(0) }

    fun canUseCachedSession(session: ShopSessionDto?, nowMs: Long = System.currentTimeMillis()): Boolean =
        session != null && session.accessToken.isNotBlank() && session.expiresAt > nowMs + 60_000

    fun canUseCachedIdentity(
        session: ShopSessionDto?,
        profile: ShopProfileDto?,
        activeAdisChatUid: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val activeUid = activeAdisChatUid?.trim().orEmpty()
        val profileUid = profile?.adisChatUserId?.trim().orEmpty()
        return activeUid.isNotBlank() &&
            profileUid.isNotBlank() &&
            activeUid == profileUid &&
            canUseCachedSession(session, nowMs)
    }

    fun safeErrorMessage(raw: String?, fallback: String): String {
        val message = raw?.trim().orEmpty()
        if (message.isBlank()) return fallback
        val lower = message.lowercase()
        val blocked = listOf("jwt", "token", "uuid", "policy", "postgres", "supabase", "stack", "service" + "_" + "role")
        return if (blocked.any { it in lower }) fallback else message
    }

    fun sellerRoute(status: String?): String = when (status) {
        "approved" -> "dashboard"
        "submitted", "under_review", "more_information_required", "rejected", "suspended" -> "status"
        else -> "apply"
    }

    fun rootTabForRoute(routeName: String): String = when (routeName) {
        "cart" -> "cart"
        "trending" -> "trending"
        "home", "category", "product" -> "home"
        else -> "account"
    }

    fun accountSubpageForRoute(routeName: String): String? = when (routeName) {
        "orders" -> "orders"
        "saved" -> "saved"
        "recently_viewed" -> "recently_viewed"
        "addresses" -> "addresses"
        "reviews" -> "reviews"
        "seller_apply", "seller_status", "seller_dashboard" -> "seller"
        "buyer_protection" -> "buyer_protection"
        else -> null
    }
}
