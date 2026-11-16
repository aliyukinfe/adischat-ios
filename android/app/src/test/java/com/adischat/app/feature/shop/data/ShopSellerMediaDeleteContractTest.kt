package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSellerMediaDeleteContractTest {
    @Test
    fun deleteRequestCarriesSelectedStore() {
        val json = Gson().toJson(
            ShopSellerProductMediaDeleteRequest(
                storeId = "controlled-store",
                productId = "controlled-product",
                imageId = "controlled-image",
            ),
        )

        assertTrue(json.contains("\"storeId\":\"controlled-store\""))
        assertTrue(json.contains("\"productId\":\"controlled-product\""))
        assertTrue(json.contains("\"imageId\":\"controlled-image\""))
    }
}
