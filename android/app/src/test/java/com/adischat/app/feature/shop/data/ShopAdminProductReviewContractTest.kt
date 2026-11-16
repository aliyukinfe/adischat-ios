package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopAdminProductReviewContractTest {
    @Test
    fun reviewRequestCarriesSubmissionTimestamp() {
        val json = Gson().toJson(
            ShopAdminReviewProductRequest(
                productId = "controlled-product",
                action = "approve",
                requestId = "controlled-review",
                expectedSubmittedAt = "2026-10-28T12:00:00Z",
            ),
        )

        assertTrue(json.contains("\"expectedSubmittedAt\":\"2026-10-28T12:00:00Z\""))
    }

    @Test
    fun adminProductDetailsRetainServerSubmissionTimestamp() {
        val product = Gson().fromJson(
            """{"id":"controlled-product","submitted_at":"2026-10-28T12:00:00Z"}""",
            ShopSellerProductDto::class.java,
        )

        assertEquals("2026-10-28T12:00:00Z", product.submittedAt)
    }
}
