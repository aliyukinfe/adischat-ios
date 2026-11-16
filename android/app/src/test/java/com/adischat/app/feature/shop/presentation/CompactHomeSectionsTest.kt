package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactHomeSectionsTest {
    @Test fun productsAppearInOnlyOneHomeSection() {
        val products = (1..16).map { index ->
            ShopProductDto(
                id = index.toString(),
                discountPercent = if (index == 2 || index == 5) 20 else null,
                compareAtPriceMinor = if (index == 2 || index == 5) 2000 else null,
                priceMinor = 1000,
            )
        }
        val sections = compactHomeSections(products + products.first())
        val ids = (sections.recommended + sections.newArrivals + sections.deals).map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(sections.deals.isEmpty())
        assertEquals(8, sections.recommended.size)
        assertEquals(4, sections.newArrivals.size)
    }

    @Test fun unverifiedComparisonFieldsDoNotCreateDeals() {
        val sections = compactHomeSections(listOf(
            ShopProductDto(id = "stale", priceMinor = 1000, compareAtPriceMinor = 1000, discountPercent = 20),
            ShopProductDto(id = "valid", priceMinor = 1000, compareAtPriceMinor = 1200, discountPercent = 16),
        ))
        assertTrue(sections.deals.isEmpty())
        assertEquals(listOf("stale", "valid"), sections.recommended.map { it.id })
        assertTrue(sections.newArrivals.isEmpty())
    }

    @Test fun seeAllContainsPreviewProductsAndMoreCatalogItems() {
        val products = (1..20).map { index ->
            ShopProductDto(id = index.toString(), priceMinor = 1000,
                compareAtPriceMinor = if (index == 2 || index == 5) 1200 else null,
                discountPercent = if (index == 2 || index == 5) 16 else null)
        }
        val preview = compactHomeSections(products)
        val recommended = homeBrowseProducts(ShopHomeBrowseSection.Recommended, products)
        val arrivals = homeBrowseProducts(ShopHomeBrowseSection.NewArrivals, products)
        assertEquals(20, recommended.size)
        assertTrue(recommended.containsAll(preview.recommended))
        assertTrue(arrivals.containsAll(preview.newArrivals))
        assertEquals(20, arrivals.size)
    }

    @Test fun unverifiedComparisonFieldsNeverShowPreviousPrice() {
        val valid = ShopProductDto(id = "valid", priceMinor = 1000, compareAtPriceMinor = 1200, discountPercent = 16)
        assertEquals(null, homePreviousPriceMinor(valid))
        assertEquals(null, homePreviousPriceMinor(valid.copy(discountPercent = null)))
        assertEquals(null, homePreviousPriceMinor(valid.copy(compareAtPriceMinor = 1000)))
        assertEquals(null, homePreviousPriceMinor(valid.copy(priceMinor = 0)))
    }
}
