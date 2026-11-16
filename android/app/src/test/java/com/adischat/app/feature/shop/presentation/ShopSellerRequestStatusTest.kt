package com.adischat.app.feature.shop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSellerRequestStatusTest {
    @Test
    fun activeRequestsAllowSellerResponse() {
        listOf("submitted", "under_review", "awaiting_buyer", "awaiting_seller", "approved", "partially_approved", "awaiting_return", "replacement_processing", "replacement_shipped")
            .forEach { assertTrue(it, sellerRequestCanRespond(it)) }
    }

    @Test
    fun terminalRequestsHideSellerResponse() {
        listOf("rejected", "cancelled", "completed", "closed")
            .forEach { assertFalse(it, sellerRequestCanRespond(it)) }
    }
}
