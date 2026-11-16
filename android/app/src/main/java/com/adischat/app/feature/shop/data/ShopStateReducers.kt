package com.adischat.app.feature.shop.data

object ShopStateReducers {
    fun sellerProductsForStore(products: List<ShopSellerProductDto>, storeId: String?): List<ShopSellerProductDto> =
        if (storeId.isNullOrBlank()) emptyList() else products.filter { it.storeId == storeId }

    fun sellerProductResponseMatchesStore(
        requestedStoreId: String,
        responseStoreId: String?,
        products: List<ShopSellerProductDto>,
    ): Boolean = requestedStoreId.isNotBlank() && responseStoreId == requestedStoreId &&
        products.all { it.storeId == requestedStoreId }

    fun sellerRequestsForStore(
        requests: ShopSellerRequestsDto?,
        groups: List<ShopOrderGroupDto>,
        storeId: String?,
    ): ShopSellerRequestsDto {
        if (storeId.isNullOrBlank()) return ShopSellerRequestsDto()
        val groupIds = groups.filter { it.storeId == storeId }.mapTo(mutableSetOf()) { it.id }
        return ShopSellerRequestsDto(
            refundRequests = requests?.refundRequests.orEmpty().filter { it.orderGroupId in groupIds },
            exchangeRequests = requests?.exchangeRequests.orEmpty().filter { it.orderGroupId in groupIds },
        )
    }

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
        if ("http 401" in lower || "401 client error" in lower || "unauthorized" in lower) {
            return "Your session expired. Please sign in again."
        }
        val blocked = listOf("jwt", "token", "uuid", "policy", "postgres", "supabase", "stack", "service" + "_" + "role")
        val rawHttpFailure = Regex("""\bhttp\s+[45]\d\d\b""").containsMatchIn(lower)
        return if (rawHttpFailure || blocked.any { it in lower }) fallback else message
    }

    fun stateForActiveIdentity(state: ShopProtectedState, activeUid: String?): ShopProtectedState {
        val normalizedUid = activeUid?.trim().orEmpty()
        return if (state.ownerUid == normalizedUid) state else ShopProtectedState(ownerUid = normalizedUid)
    }

    fun clearAdminWorkspace(state: ShopProtectedState): ShopProtectedState = state.copy(
        adminSession = null,
        adminSelectedTab = "Sellers",
        allAdminProductsQuery = ShopAdminProductQuery(),
        adminDashboard = null,
        adminSellerApplications = emptyList(),
        adminProducts = emptyList(),
        allAdminProducts = emptyList(),
        allAdminProductsLoading = false,
        allAdminProductsError = null,
        allAdminProductsOffset = 0,
        allAdminProductsHasNext = false,
        selectedAdminProduct = null,
        publicContentQueue = null,
        publicContentOffset = 0,
        publicContentLoading = false,
        publicContentReviewing = false,
        publicContentError = null,
        publicContentPreview = null,
        adminOrders = emptyList(),
        selectedAdminOrder = null,
        adminAuditLog = emptyList(),
        adminUsers = emptyList(),
        adminStores = emptyList(),
        selectedAdminStore = null,
        adminRefundRequests = emptyList(),
        adminExchangeRequests = emptyList(),
        adminReviews = emptyList(),
    )

    fun notificationMarkedRead(
        state: ShopProtectedState,
        notificationId: String,
        serverUnreadCount: Int,
        readAt: String,
    ): ShopProtectedState = state.copy(
        notificationUnreadCount = serverUnreadCount.coerceAtLeast(0),
        notifications = state.notifications.map { notification ->
            if (notification.id == notificationId && notification.readAt.isNullOrBlank()) {
                notification.copy(readAt = readAt)
            } else {
                notification
            }
        },
    )

    fun allNotificationsMarkedRead(
        state: ShopProtectedState,
        serverUnreadCount: Int,
        readAt: String,
    ): ShopProtectedState = state.copy(
        notificationUnreadCount = serverUnreadCount.coerceAtLeast(0),
        notifications = state.notifications.map { notification ->
            if (notification.readAt.isNullOrBlank()) notification.copy(readAt = readAt) else notification
        },
    )

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
