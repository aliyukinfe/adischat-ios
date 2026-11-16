package com.adischat.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketplacePushRoutingTest {
    @Test fun buyerOrderOpensBuyerNotifications() {
        assertEquals(MarketplacePushTarget.BUYER_NOTIFICATIONS,
            marketplacePushTarget("adischat_shop", "buyer", "account-a", "account-a", null, "seller_accepted", null))
    }

    @Test fun buyerMessageOpensBuyerConversation() {
        assertEquals(MarketplacePushTarget.BUYER_MESSAGE,
            marketplacePushTarget("adischat_shop", "buyer", "account-a", "account-a", null, "shop_message", "conversation-a"))
    }

    @Test fun sellerEventOpensSellerNotifications() {
        assertEquals(MarketplacePushTarget.SELLER_NOTIFICATIONS,
            marketplacePushTarget("adischat_shop", "seller", "account-a", "account-a", "store-a", "seller_order_received", null))
    }

    @Test fun accountSwitchAndUnscopedPushAreIgnored() {
        assertEquals(MarketplacePushTarget.IGNORE,
            marketplacePushTarget("adischat_shop", "seller", "account-a", "account-b", "store-a", "seller_order_received", null))
        assertEquals(MarketplacePushTarget.IGNORE,
            marketplacePushTarget("adischat_shop", null, "account-a", "account-a", "store-a", "seller_order_received", null))
        assertEquals(MarketplacePushTarget.IGNORE,
            marketplacePushTarget("adischat_shop", "seller", "account-a", "account-a", null, "shop_message", null))
    }
}
