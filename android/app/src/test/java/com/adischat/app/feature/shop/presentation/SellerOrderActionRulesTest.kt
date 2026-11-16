package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerOrderActionRulesTest {
    @Test fun rejectionRemainsAvailableAfterAcceptanceButNotShipment() {
        assertTrue(sellerRejectionActionAvailable("awaiting_seller_confirmation"))
        assertTrue(sellerRejectionActionAvailable("created"))
        assertTrue(sellerRejectionActionAvailable("accepted"))
        listOf("processing", "packed", "handed_to_carrier", "shipped", "out_for_delivery", "delivered", "cancelled", "rejected", "expired")
            .forEach { assertFalse(sellerRejectionActionAvailable(it)) }
    }
}
