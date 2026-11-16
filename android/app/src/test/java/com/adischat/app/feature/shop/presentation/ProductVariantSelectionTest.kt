package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductOptionDto
import com.adischat.app.feature.shop.data.ShopProductSpecificationDto
import com.adischat.app.feature.shop.data.ShopOrderItemDto
import com.adischat.app.feature.shop.data.ShopOrderVariantSnapshotDto
import com.adischat.app.feature.shop.data.ShopPublicVariantDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductVariantSelectionTest {
    private val variants = listOf(
        ShopPublicVariantDto("black-256", options = mapOf("COLOR" to "Black", "STORAGE" to "256GB"), availableQuantity = 5),
        ShopPublicVariantDto("black-512", options = mapOf("COLOR" to "Black", "STORAGE" to "512GB"), availableQuantity = 0),
        ShopPublicVariantDto("white-512", options = mapOf("COLOR" to "White", "STORAGE" to "512GB"), availableQuantity = 4),
    )

    @Test fun exactSelectionResolvesOneVariant() {
        assertEquals("black-256", resolveExactVariant(variants, listOf("COLOR", "STORAGE"), mapOf("COLOR" to "Black", "STORAGE" to "256GB"))?.id)
    }

    @Test fun exactSelectionUsesVariantPriceAndStock() {
        val priced = variants.mapIndexed { index, variant ->
            variant.copy(priceMinor = if (index == 0) 2_800_000 else 3_200_000)
        }
        val selected = resolveExactVariant(priced, listOf("COLOR", "STORAGE"),
            mapOf("COLOR" to "White", "STORAGE" to "512GB"))
        assertEquals(3_200_000, selected?.priceMinor)
        assertEquals(4, selected?.availableQuantity)
    }

    @Test fun eliteBookPurchaseSheetSwitchesBothDirectionsWithoutReopening() {
        val eliteBook = listOf(
            ShopPublicVariantDto(id = "9b85fb4f-ff1d-430b-9da4-1c35f2fb39e9", options = mapOf("COLOR" to "Silver", "RAM" to "8GB", "STORAGE" to "256GB"), priceMinor = 5_600_000, availableQuantity = 3),
            ShopPublicVariantDto(id = "3021c88c-3302-4d6f-9a29-70b0bad2df81", options = mapOf("COLOR" to "Silver", "RAM" to "16GB", "STORAGE" to "512GB"), priceMinor = 6_300_000, availableQuantity = 3),
        )
        val codes = listOf("COLOR", "RAM", "STORAGE")
        val selected = mutableMapOf("COLOR" to "Silver", "RAM" to "8GB", "STORAGE" to "256GB")
        assertTrue(isPurchaseOptionValueAvailable(eliteBook, "RAM", "16GB"))
        selected["RAM"] = "16GB"
        clearInvalidDependentSelections(eliteBook, "RAM", selected)
        assertEquals(mapOf("RAM" to "16GB", "COLOR" to "Silver"), selected)
        assertNull(resolveExactVariant(eliteBook, codes, selected))
        selected["STORAGE"] = "512GB"
        clearInvalidDependentSelections(eliteBook, "STORAGE", selected)
        assertEquals("3021c88c-3302-4d6f-9a29-70b0bad2df81", resolveExactVariant(eliteBook, codes, selected)?.id)
        assertEquals(6_300_000, resolveExactVariant(eliteBook, codes, selected)?.priceMinor)
        assertEquals(3, resolveExactVariant(eliteBook, codes, selected)?.availableQuantity)

        selected["RAM"] = "8GB"
        clearInvalidDependentSelections(eliteBook, "RAM", selected)
        assertNull(resolveExactVariant(eliteBook, codes, selected))
        selected["STORAGE"] = "256GB"
        clearInvalidDependentSelections(eliteBook, "STORAGE", selected)
        assertEquals("9b85fb4f-ff1d-430b-9da4-1c35f2fb39e9", resolveExactVariant(eliteBook, codes, selected)?.id)
        assertEquals(5_600_000, resolveExactVariant(eliteBook, codes, selected)?.priceMinor)
        assertEquals(3, resolveExactVariant(eliteBook, codes, selected)?.availableQuantity)
    }

    @Test fun eliteBookPublicSnapshotResolvesBothThreeAxisConfigurations() {
        val listingVariants = listOf(
            ShopPublicVariantDto(
                id = "9b85fb4f-ff1d-430b-9da4-1c35f2fb39e9",
                options = mapOf("COLOR" to "Silver", "RAM" to "8GB", "STORAGE" to "256GB"),
                priceMinor = 5_600_000,
                availableQuantity = 3,
            ),
            ShopPublicVariantDto(
                id = "3021c88c-3302-4d6f-9a29-70b0bad2df81",
                options = mapOf("COLOR" to "Silver", "RAM" to "16GB", "STORAGE" to "512GB"),
                priceMinor = 6_300_000,
                availableQuantity = 3,
            ),
        )
        val codes = listOf("COLOR", "RAM", "STORAGE")
        val base = resolveExactVariant(listingVariants, codes, mapOf("COLOR" to "Silver", "RAM" to "8GB", "STORAGE" to "256GB"))
        val upgraded = resolveExactVariant(listingVariants, codes, mapOf("COLOR" to "Silver", "RAM" to "16GB", "STORAGE" to "512GB"))
        assertEquals("9b85fb4f-ff1d-430b-9da4-1c35f2fb39e9", base?.id)
        assertEquals(5_600_000, base?.priceMinor)
        assertEquals(3, base?.availableQuantity)
        assertEquals("3021c88c-3302-4d6f-9a29-70b0bad2df81", upgraded?.id)
        assertEquals(6_300_000, upgraded?.priceMinor)
        assertEquals(3, upgraded?.availableQuantity)
        assertNull(resolveExactVariant(listingVariants, codes, mapOf("COLOR" to "Silver", "RAM" to "16GB")))
    }

    @Test fun incompleteSelectionDoesNotResolve() {
        assertNull(resolveExactVariant(variants, listOf("COLOR", "STORAGE"), mapOf("COLOR" to "Black")))
    }

    @Test fun impossibleOrOutOfStockCombinationIsDisabled() {
        assertFalse(isOptionValueAvailable(variants, "STORAGE", "512GB", mapOf("COLOR" to "Black")))
        assertTrue(isOptionValueAvailable(variants, "STORAGE", "512GB", mapOf("COLOR" to "White")))
    }

    @Test fun multipleBuyerConfigurationsOpenPurchaseSheet() {
        val product = ShopProductDto(
            options = listOf(ShopProductOptionDto(code = "COLOR")),
            variants = listOf(
                ShopPublicVariantDto(id = "black", options = mapOf("COLOR" to "Black"), availableQuantity = 2),
                ShopPublicVariantDto(id = "white", options = mapOf("COLOR" to "White"), availableQuantity = 1),
            ),
        )
        assertTrue(buyerSelectableConfigurationRequired(product))
        assertNull(directPurchaseVariant(product))
    }

    @Test fun oneSellableConfigurationPurchasesDirectly() {
        val only = ShopPublicVariantDto(id = "only", options = mapOf("COLOR" to "Black"), availableQuantity = 2)
        val product = ShopProductDto(options = listOf(ShopProductOptionDto(code = "COLOR")), variants = listOf(only))
        assertFalse(buyerSelectableConfigurationRequired(product))
        assertEquals("only", directPurchaseVariant(product)?.id)
    }

    @Test fun multipleVariantsWithoutOptionMetadataRequireExactSelection() {
        val product = ShopProductDto(variants = listOf(
            ShopPublicVariantDto(id = "laptop-256", title = "256 GB", priceMinor = 2800000, availableQuantity = 2),
            ShopPublicVariantDto(id = "laptop-512", title = "512 GB", priceMinor = 3500000, availableQuantity = 1),
        ))
        assertTrue(buyerSelectableConfigurationRequired(product))
        assertNull(directPurchaseVariant(product))
    }

    @Test fun multipleConfigurationsStillOpenSheetWhenOnlyOneIsInStock() {
        val product = ShopProductDto(
            options = listOf(ShopProductOptionDto(code = "COLOR")),
            variants = listOf(
                ShopPublicVariantDto(id = "black", options = mapOf("COLOR" to "Black"), availableQuantity = 2),
                ShopPublicVariantDto(id = "white", options = mapOf("COLOR" to "White"), availableQuantity = 0),
            ),
        )
        assertTrue(buyerSelectableConfigurationRequired(product))
        assertNull(directPurchaseVariant(product))
    }

    @Test fun changingAnOptionClearsImpossibleDependentChoice() {
        val candidates = listOf(
            ShopPublicVariantDto(id = "black-s", options = mapOf("COLOR" to "Black", "SIZE" to "S"), availableQuantity = 1),
            ShopPublicVariantDto(id = "white-m", options = mapOf("COLOR" to "White", "SIZE" to "M"), availableQuantity = 1),
        )
        val selected = mutableMapOf("COLOR" to "White", "SIZE" to "S")
        clearInvalidDependentSelections(candidates, "COLOR", selected)
        assertFalse(selected.containsKey("SIZE"))
    }

    @Test fun categoryFilterValuesComeOnlyFromRealSellableConfigurations() {
        val product = ShopProductDto(
            id = "phone",
            variants = listOf(
                ShopPublicVariantDto(id = "black", options = mapOf("COLOR" to "Black"), availableQuantity = 2),
                ShopPublicVariantDto(id = "white", options = mapOf("COLOR" to "White"), availableQuantity = 0),
            ),
            specifications = listOf(ShopProductSpecificationDto(code = "BRAND", valueText = "Adis")),
        )

        assertEquals(listOf("Black"), buyerFilterValues("COLOR", listOf(product), mapOf(product.id to product)))
        assertEquals(listOf("Adis"), buyerFilterValues("BRAND", listOf(product), mapOf(product.id to product)))
    }

    @Test fun buyerFilterMatchesExactAvailableVariantOrSpecification() {
        val product = ShopProductDto(
            variants = listOf(
                ShopPublicVariantDto(id = "black-m", options = mapOf("COLOR" to "Black", "SIZE" to "M"), availableQuantity = 1),
                ShopPublicVariantDto(id = "white-l", options = mapOf("COLOR" to "White", "SIZE" to "L"), availableQuantity = 0),
            ),
            specifications = listOf(ShopProductSpecificationDto(code = "MATERIAL", valueText = "Cotton")),
        )

        assertTrue(product.matchesBuyerFilter("COLOR", "Black"))
        assertFalse(product.matchesBuyerFilter("COLOR", "White"))
        assertTrue(product.matchesBuyerFilter("MATERIAL", "Cotton"))
    }

    @Test fun historicalOrderLabelUsesImmutableSnapshot() {
        val item = ShopOrderItemDto(
            titleSnapshot = "Phone",
            variantSnapshot = ShopOrderVariantSnapshotDto(
                title = "Black / 512GB",
                options = linkedMapOf("COLOR" to "Black", "STORAGE" to "512GB"),
            ),
        )

        assertEquals("Black • 512GB", item.variantDisplayLabel())
    }
}
