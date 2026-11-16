package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopBannerDto
import com.adischat.app.feature.shop.data.ShopProductDto
import androidx.compose.material.icons.filled.Category
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AdisGebeyaHomePresentationTest {
    @Test
    fun productCardGrowsForLargerFontsWithoutChangingGridWidth() {
        val width = 160.dp
        val regular = homeProductCardHeight(width, 1f)
        val large = homeProductCardHeight(width, 1.6f)
        assertTrue(large > regular)
        assertEquals(regular, homeProductCardHeight(width, 0.9f))
    }

    @Test
    fun languageStateAlwaysMapsToExactlyOneSupportedFlag() {
        assertEquals(MARKETPLACE_LANGUAGE_ENGLISH, normalizedMarketplaceLanguage(null))
        assertEquals(MARKETPLACE_LANGUAGE_ENGLISH, normalizedMarketplaceLanguage("unsupported"))
        assertEquals(MARKETPLACE_LANGUAGE_AMHARIC, normalizedMarketplaceLanguage("am"))
        assertFalse(marketplaceLanguageFlag("en").contains(marketplaceLanguageFlag("am")))
    }

    @Test
    fun categorySearchUsesHierarchyWhenBlankAndAllLevelsWhenSearching() {
        val root = ShopCategoryDto(id = "root", name = "Crafts and Sewing", slug = "crafts")
        val child = ShopCategoryDto(id = "child", parentId = "root", name = "Garment Accessories", slug = "garment-accessories")
        val leaf = ShopCategoryDto(id = "leaf", parentId = "child", name = "Rhinestones", slug = "rhinestones")
        val categories = listOf(root, child, leaf)

        assertEquals(listOf(root), marketplaceCategorySearch(categories, "", null))
        assertEquals(listOf(child), marketplaceCategorySearch(categories, "", "root"))
        assertEquals(listOf(leaf), marketplaceCategorySearch(categories, "rhine", null))
        assertEquals(setOf("crafts", "garment-accessories", "rhinestones"), marketplaceCategoryAndDescendantSlugs(categories, "crafts"))
    }

    @Test
    fun filterLabelsExposeOnlyActiveRealSelections() {
        val categories = listOf(ShopCategoryDto(id = "leaf", name = "Rhinestones", slug = "rhinestones"))
        val labels = ShopHomeFilters(
            categorySlug = "rhinestones",
            inStockOnly = true,
            minimumPriceEtb = "100",
            maximumPriceEtb = "900",
            minimumRating = 4,
            sort = "Best rated",
        ).activeLabels(categories)

        assertTrue("Rhinestones" in labels)
        assertTrue("In stock" in labels)
        assertTrue("From ETB 100" in labels)
        assertTrue("Up to ETB 900" in labels)
        assertTrue("4 stars & above" in labels)
        assertTrue("Best rated" in labels)
    }

    @Test
    fun productionBannerNeverExposesDemoWording() {
        assertFalse(productionBannerTitle("Fresh deals from demo sellers").contains("demo", ignoreCase = true))
        assertFalse(productionBannerSubtitle("Browse approved development catalog products.").contains("development", ignoreCase = true))
        assertEquals("Seasonal arrivals", productionBannerTitle("Seasonal arrivals"))
    }

    @Test
    fun homeHeroUsesOnlyActiveRealBannersWithinTheirSchedule() {
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val valid = ShopBannerDto(id = "valid", title = "Seasonal arrivals", imageUrl = "https://example.test/banner.png", isActive = true,
            startAt = "2026-10-29T00:00:00Z", endAt = "2026-10-31T00:00:00Z")
        val seed = valid.copy(id = "seed", isSeed = true)
        val expired = valid.copy(id = "expired", endAt = "2026-10-30T11:00:00Z")
        val noImage = valid.copy(id = "no-image", imageUrl = null)
        val inactive = valid.copy(id = "inactive", isActive = false)

        assertEquals(listOf(valid), eligibleHomeBanners(listOf(valid, seed, expired, noImage, inactive), now))
    }

    @Test
    fun categoryLabelsAreDisplayOnlyAndKeepTheRealCategoryIdentity() {
        val category = ShopCategoryDto(id = "real-id", name = "Electronics & Technology", slug = "electronics")
        assertEquals("Electronics", homeCategoryDisplayLabel(category))
        assertEquals("Electronics & Technology", category.name)
        assertEquals("real-id", category.id)
        assertEquals("Handmade Supplies", homeCategoryDisplayLabel(category.copy(name = "Handmade Supplies", slug = "handmade")))
    }

    @Test
    fun productTitleCasingDoesNotChangeSellerData() {
        val product = ShopProductDto(title = "samsung galaxy A16 5G 128Gb", brand = "Samsung")
        assertEquals("Samsung Galaxy A16 5G 128Gb", homeProductDisplayTitle(product))
        assertEquals("samsung galaxy A16 5G 128Gb", product.title)
        assertEquals("Samsung Galaxy A16 5G 128Gb", homeProductDisplayTitle(product.copy(brand = "samsung")))
    }

    @Test
    fun homeBrowseUsesRealProductsWithoutAnEmptySearchQuery() {
        val newest = ShopProductDto(id = "new", createdAt = "2026-11-04T10:00:00Z")
        val older = ShopProductDto(id = "old", createdAt = "2026-11-03T10:00:00Z")
        val deal = ShopProductDto(id = "deal", createdAt = "2026-11-02T10:00:00Z", priceMinor = 900,
            compareAtPriceMinor = 1000, discountPercent = 10)
        val products = listOf(older, deal, newest)

        assertEquals(listOf(newest, older), homeBrowseProducts(ShopHomeBrowseSection.NewArrivals, products).take(2))
        assertTrue(homeBrowseProducts(ShopHomeBrowseSection.Recommended, products).isNotEmpty())
        assertTrue(homeBrowseProducts(ShopHomeBrowseSection.Deals, products).isEmpty())
    }

    @Test
    fun allLiveRootCategorySlugsHaveNonGenericIcons() {
        val rootSlugs = listOf("electronics", "fashion", "home", "beauty", "groceries", "books", "sports",
            "jewelry-watches", "toys", "crafts-sewing-accessories", "fabric-textile-leather",
            "shoes-bags-accessories", "automotive-motorcycle", "tools-hardware", "industrial-manufacturing",
            "packaging-printing", "office-school-stationery", "health-wellness", "pet-supplies",
            "agriculture-farming", "construction-building-materials", "appliances", "gifts-party-events")
        val icons = rootSlugs.map { categoryIcon(ShopCategoryDto(slug = it)) }
        assertEquals(rootSlugs.size, icons.distinct().size)
        icons.forEach { assertNotEquals(androidx.compose.material.icons.Icons.Default.Category, it) }

        val root = ShopCategoryDto(id = "root", slug = "automotive-motorcycle")
        val child = ShopCategoryDto(id = "child", parentId = "root", slug = "car-parts")
        assertEquals(categoryIcon(root), categoryIcon(child, listOf(root, child)))
    }
}
