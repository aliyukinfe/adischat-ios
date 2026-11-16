package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopConversationDto
import com.adischat.app.feature.shop.data.ShopProductContextDto
import com.adischat.app.feature.shop.data.ShopProductContextOptionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ShopProductContextTest {
    @Test
    fun oldConversationRemainsCompatibleWithoutProductContext() {
        assertNull(ShopConversationDto(id = "conversation-1").productContext)
    }

    @Test
    fun productContextSupportsGenericOptionsWithoutHardCodedVariantTypes() {
        val context = ShopProductContextDto(
            productId = "product-1",
            title = "Phone",
            selectedOptions = listOf(
                ShopProductContextOptionDto("Color", "Black"),
                ShopProductContextOptionDto("Storage", "512GB"),
            ),
            availability = "in_stock",
            canOpen = true,
        )

        assertEquals(listOf("Color", "Storage"), context.selectedOptions.map { it.name })
        assertEquals(listOf("Black", "512GB"), context.selectedOptions.map { it.value })
    }

    @Test
    fun unavailableContextCannotOpenProduct() {
        val context = ShopProductContextDto(productId = "product-1")

        assertEquals("unavailable", context.availability)
        assertFalse(context.canOpen)
    }

    @Test
    fun contextPriceUsesServerCurrencyAndMinorUnits() {
        assertEquals("ETB 1,800", formatProductContextPrice(180_000, "etb"))
    }

    @Test
    fun contextPriceUsesServerSellingUnitMetadata() {
        assertEquals("ETB 550 / kg", formatProductContextPrice(55_000, "ETB", "KILOGRAM"))
        assertEquals("ETB 320 / m", formatProductContextPrice(32_000, "ETB", "METER"))
        assertEquals("ETB 25,000 / piece", formatProductContextPrice(2_500_000, "ETB", "PIECE"))
    }

    @Test
    fun productSnapshotCarriesSafeStoreAndVariantContext() {
        val context = ShopProductContextDto(
            productId = "product-1",
            title = "Mobile",
            storeId = "store-1",
            storeName = "Hulu Store",
            variantId = "variant-1",
            variantTitle = "Black / 256 GB / 8 GB",
            sellingUnitCode = "PIECE",
            productStatus = "active",
            canOpen = true,
        )

        assertEquals("Hulu Store", context.storeName)
        assertEquals("variant-1", context.variantId)
        assertEquals("PIECE", context.sellingUnitCode)
    }
}
