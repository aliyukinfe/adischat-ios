package com.adischat.app.notifications

internal enum class MarketplacePushTarget {
    IGNORE,
    BUYER_MESSAGE,
    BUYER_NOTIFICATIONS,
    SELLER_NOTIFICATIONS,
}

internal fun marketplacePushTarget(
    area: String?,
    context: String?,
    receiverUid: String?,
    activeUid: String?,
    storeId: String?,
    type: String?,
    conversationId: String?,
): MarketplacePushTarget {
    if (area != "adischat_shop" || activeUid.isNullOrBlank() || receiverUid != activeUid) {
        return MarketplacePushTarget.IGNORE
    }
    return when (context) {
        "buyer" -> if (type == "shop_message" && !conversationId.isNullOrBlank()) {
            MarketplacePushTarget.BUYER_MESSAGE
        } else {
            MarketplacePushTarget.BUYER_NOTIFICATIONS
        }
        "seller" -> if (!storeId.isNullOrBlank()) MarketplacePushTarget.SELLER_NOTIFICATIONS else MarketplacePushTarget.IGNORE
        else -> MarketplacePushTarget.IGNORE
    }
}
