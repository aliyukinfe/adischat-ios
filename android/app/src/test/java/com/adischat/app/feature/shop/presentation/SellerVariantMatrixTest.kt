package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopSellerOptionValueInput
import com.adischat.app.feature.shop.data.ShopSellerProductOptionInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerVariantMatrixTest {

    @Test
    fun unselected_new_combinations_are_not_persisted() {
        assertFalse(shouldPersistSellerVariant(existingId = null, active = false))
        assertTrue(shouldPersistSellerVariant(existingId = null, active = true))
        assertTrue(shouldPersistSellerVariant(existingId = "variant-id", active = false))
    }
    @Test fun colorAndSizeGenerateOnlyCandidateMatrix() {
        val options = listOf(
            ShopSellerProductOptionInput("COLOR", "Color", 1, listOf(ShopSellerOptionValueInput("BLACK", "Black", "Black"), ShopSellerOptionValueInput("WHITE", "White", "White"))),
            ShopSellerProductOptionInput("SIZE", "Size", 2, listOf(ShopSellerOptionValueInput("S", "S", "S"), ShopSellerOptionValueInput("M", "M", "M"))),
        )
        val combinations = generateVariantCombinations(options)
        assertEquals(4, combinations.size)
        assertEquals(setOf("COLOR", "SIZE"), combinations.first().keys)
    }

    @Test fun noOptionsCreatesOneDefaultSellableVariant() {
        assertEquals(listOf(emptyMap<String, String>()), generateVariantCombinations(emptyList()))
    }

    @Test fun colorSwatchesSurviveSellerCatalogEdits() {
        assertEquals("#123456", sellerOptionColorHex("COLOR", "Blue", mapOf("BLUE" to "#123456")))
        assertEquals("#E53935", sellerOptionColorHex("COLOR", "Red"))
        assertEquals(null, sellerOptionColorHex("SIZE", "Red"))
    }
}
