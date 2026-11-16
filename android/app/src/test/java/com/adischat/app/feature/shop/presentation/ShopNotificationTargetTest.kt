package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopNotificationDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopNotificationTargetTest {
    @Test
    fun `known order notification maps to typed order destination`() {
        val target = resolveShopNotificationTarget(
            ShopNotificationDto(type = "order_confirmed", data = mapOf("orderId" to "order-1")),
        )
        assertEquals(ShopNotificationTarget.BuyerOrder(orderId = "order-1"), target)
    }

    @Test
    fun `seller response reminder maps by active audience`() {
        val notification = ShopNotificationDto(
            id = "n-reminder",
            type = "seller_response_reminder",
            data = mapOf("orderId" to "order-1", "orderGroupId" to "group-1"),
        )

        assertEquals(ShopNotificationTarget.BuyerOrder(orderGroupId = "group-1"), resolveShopNotificationTarget(notification.copy(data = mapOf("orderGroupId" to "group-1"))))
        assertEquals(ShopNotificationTarget.SellerOrder("group-1"), resolveShopNotificationTarget(notification, sellerView = true))
    }

    @Test
    fun `seller and message destinations use only known identifiers`() {
        assertEquals(
            ShopNotificationTarget.SellerOrder("group-1"),
            resolveShopNotificationTarget(ShopNotificationDto(type = "seller_order_received", data = mapOf("orderGroupId" to "group-1"))),
        )
        assertEquals(
            ShopNotificationTarget.Conversation("conversation-1"),
            resolveShopNotificationTarget(ShopNotificationDto(type = "shop_message", data = mapOf("conversationId" to "conversation-1"))),
        )
    }

    @Test
    fun `unknown or incomplete payload falls back to center`() {
        assertEquals(
            ShopNotificationTarget.Center,
            resolveShopNotificationTarget(ShopNotificationDto(type = "https://malicious.example/route", data = mapOf("route" to "admin"))),
        )
        assertEquals(
            ShopNotificationTarget.Center,
            resolveShopNotificationTarget(ShopNotificationDto(type = "order_confirmed")),
        )
    }

    @Test
    fun `production delivery event opens tracking`() {
        val target = resolveShopNotificationTarget(
            ShopNotificationDto(type = "delivery_confirmed", data = mapOf("orderId" to "order-7")),
        )
        assertEquals(ShopNotificationTarget.BuyerTracking("order-7"), target)
    }

    @Test
    fun `production seller lifecycle event opens buyer order`() {
        val target = resolveShopNotificationTarget(
            ShopNotificationDto(type = "seller_accepted", data = mapOf("orderId" to "order-8")),
        )
        assertEquals(ShopNotificationTarget.BuyerOrder(orderId = "order-8"), target)
    }

    @Test
    fun `production refund update opens buyer protection`() {
        val target = resolveShopNotificationTarget(
            ShopNotificationDto(type = "refund_request_update", data = mapOf("requestId" to "request-2")),
        )
        assertEquals(ShopNotificationTarget.BuyerProtection("request-2"), target)
    }

    @Test
    fun `shared refund and issue events open seller requests only in seller context`() {
        listOf("refund_request_update", "buyer_issue_status_changed", "refund_request_received").forEach { type ->
            val notification = ShopNotificationDto(type = type, data = mapOf("requestId" to "request-2"))
            assertEquals(ShopNotificationTarget.SellerRequests, resolveShopNotificationTarget(notification, sellerView = true))
        }
        val buyerUpdate = ShopNotificationDto(type = "refund_request_update", data = mapOf("requestId" to "request-2"))
        assertEquals(ShopNotificationTarget.BuyerProtection("request-2"), resolveShopNotificationTarget(buyerUpdate))
    }

    @Test
    fun `seller moderation and store events stay in seller center`() {
        assertEquals(ShopNotificationTarget.SellerProducts,
            resolveShopNotificationTarget(ShopNotificationDto(type = "product_moderation_approve"), sellerView = true))
        assertEquals(ShopNotificationTarget.SellerMore,
            resolveShopNotificationTarget(ShopNotificationDto(type = "store_review_received"), sellerView = true))
    }

    @Test
    fun `production notification matrix resolves every supported lifecycle family`() {
        listOf("seller_processing", "seller_packed", "handed_to_carrier", "delivery_confirmed").forEach { type ->
            assertEquals(
                ShopNotificationTarget.BuyerTracking("order-9"),
                resolveShopNotificationTarget(ShopNotificationDto(type = type, data = mapOf("orderId" to "order-9"))),
            )
        }
        listOf("order_placed", "seller_accepted", "seller_rejected", "order_cancelled", "order_expired").forEach { type ->
            assertEquals(
                ShopNotificationTarget.BuyerOrder(orderId = "order-10"),
                resolveShopNotificationTarget(ShopNotificationDto(type = type, data = mapOf("orderId" to "order-10"))),
            )
        }
        listOf("refund_request_update", "exchange_request_updated").forEach { type ->
            assertEquals(
                ShopNotificationTarget.BuyerProtection("request-3"),
                resolveShopNotificationTarget(ShopNotificationDto(type = type, data = mapOf("requestId" to "request-3"))),
            )
        }
        assertEquals(
            ShopNotificationTarget.Conversation("conversation-2"),
            resolveShopNotificationTarget(ShopNotificationDto(type = "shop_message", data = mapOf("conversationId" to "conversation-2"))),
        )
    }
}
