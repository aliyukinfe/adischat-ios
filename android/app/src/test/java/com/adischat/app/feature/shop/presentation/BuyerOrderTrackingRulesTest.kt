package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopOrderDto
import com.adischat.app.feature.shop.data.ShopOrderGroupDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyerOrderTrackingRulesTest {
    private val placed = "2026-11-07T10:00:00Z"
    private val pendingGroup = ShopOrderGroupDto(fulfillmentStatus = "awaiting_seller_confirmation", sellerResponseStatus = "pending")

    private fun order(now: String) = ShopOrderDto(
        createdAt = placed,
        serverNow = now,
        fulfillmentStatus = "awaiting_seller_confirmation",
        groups = listOf(pendingGroup),
    )

    @Test fun buyerCancellationUsesServerTimeAndExpiresAtThirtyMinutes() {
        assertTrue(buyerCancellationEligible(order("2026-11-07T10:29:59Z")))
        assertFalse(buyerCancellationEligible(order("2026-11-07T10:30:00Z")))
        assertFalse(buyerCancellationEligible(order("2026-11-07T09:59:59Z")))
        assertFalse(buyerCancellationEligible(order("2026-11-07T10:05:00Z").copy(serverNow = null)))
    }

    @Test fun sellerAcceptanceOrAnyNonPendingGroupDisablesCancellation() {
        val pending = order("2026-11-07T10:05:00Z")
        assertFalse(buyerCancellationEligible(pending.copy(groups = listOf(pendingGroup.copy(
            fulfillmentStatus = "accepted", sellerResponseStatus = "accepted", sellerRespondedAt = "2026-11-07T10:04:00Z",
        )))))
        assertFalse(buyerCancellationEligible(pending.copy(groups = listOf(pendingGroup, pendingGroup.copy(fulfillmentStatus = "accepted")))))
        assertFalse(buyerCancellationEligible(pending.copy(fulfillmentStatus = "accepted")))
        assertFalse(buyerCancellationEligible(pending.copy(status = "cancelled")))
    }

    @Test fun activeDeliveryCodeStopsAfterConfirmationOrCancellation() {
        val shipped = ShopOrderDto(status = "shipped", fulfillmentStatus = "out_for_delivery")
        assertTrue(buyerDeliveryCodeAvailable(shipped))
        assertFalse(buyerDeliveryCodeAvailable(shipped.copy(status = "delivered", fulfillmentStatus = "delivered")))
        assertFalse(buyerDeliveryCodeAvailable(shipped.copy(status = "cancelled")))
        assertTrue(buyerDeliveryConfirmed(shipped.copy(status = "delivered", fulfillmentStatus = "delivered")))
        assertTrue(buyerTrackingMessage(shipped.copy(status = "delivered", fulfillmentStatus = "delivered")).contains("can no longer be used"))
    }

    @Test fun expiredOrderDoesNotClaimRefundCompleted() {
        val expired = order("2026-11-08T10:00:00Z").copy(status = "cancelled", fulfillmentStatus = "expired")
        assertTrue(buyerTrackingMessage(expired).contains("pending provider confirmation"))
        assertFalse(BuyerCancellationFlag.enabled)
    }

    @Test fun rejectionAndCancellationHaveDistinctMessages() {
        val pending = order("2026-11-07T10:05:00Z")
        assertTrue(buyerTrackingMessage(pending.copy(status = "rejected")).contains("seller rejected"))
        assertTrue(buyerTrackingMessage(pending.copy(status = "cancelled", groups = listOf(pendingGroup.copy(sellerResponseStatus = "rejected")))).contains("rejected an item"))
        assertTrue(buyerTrackingMessage(pending.copy(status = "cancelled")).contains("cancelled"))
    }
}
