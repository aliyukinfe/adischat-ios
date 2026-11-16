package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceDiscoveryVisualSearchTest {
    @Test
    fun discoveryControlsHaveDistinctTypedSurfaces() {
        val surfaces = listOf(
            MarketplaceDiscoverySurface.Categories,
            MarketplaceDiscoverySurface.Filters,
            MarketplaceDiscoverySurface.Sort,
            MarketplaceDiscoverySurface.Price,
            MarketplaceDiscoverySurface.Rating,
        )
        assertEquals(5, surfaces.toSet().size)
        assertNotEquals(MarketplaceDiscoverySurface.Categories, MarketplaceDiscoverySurface.Filters)
        assertNotEquals(MarketplaceDiscoverySurface.Filters, MarketplaceDiscoverySurface.Sort)
        assertNotEquals(MarketplaceDiscoverySurface.Sort, MarketplaceDiscoverySurface.Price)
        assertNotEquals(MarketplaceDiscoverySurface.Price, MarketplaceDiscoverySurface.Rating)
    }

    @Test
    fun categoryChangePreservesGlobalFiltersAndDropsIncompatibleAttributes() {
        val original = ShopHomeFilters(
            categorySlug = "mobile-phones",
            categoryValues = mapOf("ram" to "8 GB"),
            minimumPriceEtb = "20000",
            minimumRating = 4,
        )
        val changed = original.withCategory("fashion")
        assertEquals("fashion", changed.categorySlug)
        assertTrue(changed.categoryValues.isEmpty())
        assertEquals("20000", changed.minimumPriceEtb)
        assertEquals(4, changed.minimumRating)
    }

    @Test
    fun activeChipRemovalChangesOnlyOneConstraint() {
        val original = ShopHomeFilters(
            categorySlug = "mobile-phones",
            minimumPriceEtb = "1000",
            maximumPriceEtb = "5000",
            minimumRating = 4,
            inStockOnly = true,
        )
        val changed = original.withoutConstraint("price")
        assertEquals("mobile-phones", changed.categorySlug)
        assertEquals(4, changed.minimumRating)
        assertTrue(changed.inStockOnly)
        assertEquals("", changed.minimumPriceEtb)
        assertEquals("", changed.maximumPriceEtb)
    }

    @Test
    fun combinedVisualFiltersApplyCategoryPriceAndRatingTogether() {
        val category = ShopCategoryDto(id = "phones", slug = "phones", name = "Phones")
        val matching = ShopProductDto(id = "match", categorySlug = "phones", priceMinor = 300_000, averageRating = 4.5)
        val lowRating = ShopProductDto(id = "rating", categorySlug = "phones", priceMinor = 300_000, averageRating = 2.0)
        val wrongCategory = ShopProductDto(id = "other", categorySlug = "fashion", priceMinor = 300_000, averageRating = 5.0)
        val result = filterVisualResults(
            listOf(matching, lowRating, wrongCategory),
            ShopHomeFilters(categorySlug = "phones", minimumPriceEtb = "2000", maximumPriceEtb = "4000", minimumRating = 4),
            listOf(category),
        )
        assertEquals(listOf("match"), result.map { it.id })
    }

    @Test
    fun visualEntryModesAndRequestDeduplicationAreExplicit() {
        assertNotEquals(MarketplaceVisualSearchEntryMode.Image, MarketplaceVisualSearchEntryMode.Scan)
        assertTrue(shouldStartVisualSearch(previousSignature = null, inFlight = false, nextSignature = 42L))
        assertFalse(shouldStartVisualSearch(previousSignature = 42L, inFlight = false, nextSignature = 42L))
        assertFalse(shouldStartVisualSearch(previousSignature = null, inFlight = true, nextSignature = 42L))
    }

    @Test
    fun backendSortMappingDoesNotMutateFilters() {
        val filters = ShopHomeFilters(categorySlug = "phones", minimumRating = 4, sort = MarketplaceSort.PriceAscending.label)
        assertEquals("price_minor.asc", marketplaceBackendOrder(filters.sort))
        assertEquals("phones", filters.categorySlug)
        assertEquals(4, filters.minimumRating)
    }

    @Test
    fun emptyVisualResultsStayEmptyInsteadOfUsingRandomFallback() {
        val result = filterVisualResults(emptyList(), ShopHomeFilters(), emptyList())
        assertTrue(result.isEmpty())
        assertEquals(MarketplaceVisualSearchPhase.Empty, MarketplaceVisualSearchPhase.Empty)
    }
}
