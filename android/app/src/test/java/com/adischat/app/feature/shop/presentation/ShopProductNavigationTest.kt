package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShopProductNavigationTest {
    @Test fun repeatedProductVisitGetsFreshStateWithoutChangingBackDestination() {
        val first = ShopRoute.Product(ShopProductDto(id = "samsung"), visitId = 1)
        val similar = ShopRoute.Product(ShopProductDto(id = "similar"), visitId = 2)
        val reopened = ShopRoute.Product(ShopProductDto(id = "samsung"), visitId = 3)

        assertNotEquals(first.stateKey(), similar.stateKey())
        assertNotEquals(first.stateKey(), reopened.stateKey())
        assertEquals("product:samsung:1", first.stateKey())
    }
}
