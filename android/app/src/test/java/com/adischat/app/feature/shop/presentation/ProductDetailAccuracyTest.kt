package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductImageDto
import com.adischat.app.feature.shop.data.ShopProductStoreDto
import com.adischat.app.feature.shop.data.ShopBenefitFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDetailAccuracyTest {
    @Test fun freshDetailReplacesStaleListFieldsWithoutChangingPurchaseData() {
        val image = ShopProductImageDto(id = "image-a", imageUrl = "https://example.invalid/laptop.jpg")
        val list = ShopProductDto(id = "a", title = "Old", priceMinor = 1000, stockStatus = "in_stock", images = listOf(image), store = ShopProductStoreDto(id = "store-a", name = "Hulu"))
        val detail = ShopProductDto(id = "a", title = "Fresh", priceMinor = 1200, stockStatus = "out_of_stock", hasVariants = true)
        assertSame(list, productForDetailRoute(list, emptyMap()))
        val shown = productForDetailRoute(list, mapOf("a" to productDetailWithCatalogFallback(detail, list)))
        assertEquals(1200, shown.priceMinor)
        assertEquals("out_of_stock", shown.stockStatus)
        assertTrue(shown.hasVariants)
        assertEquals(listOf(image), shown.images)
        assertEquals("store-a", shown.store?.id)
    }

    @Test fun explicitFreshImageListWinsOverCatalogFallback() {
        val old = ShopProductImageDto(id = "old")
        val fresh = ShopProductImageDto(id = "fresh")
        val catalog = ShopProductDto(id = "a", images = listOf(old), priceMinor = 1000)
        val detail = ShopProductDto(id = "a", images = listOf(fresh), priceMinor = 1500)
        assertEquals(listOf(fresh), productDetailWithCatalogFallback(detail, catalog).images)
        assertEquals(emptyList<ShopProductImageDto>(), productDetailWithCatalogFallback(detail.copy(images = emptyList()), catalog).images)
    }

    @Test fun rapidSelectionDoesNotApplyThePreviousProductResponse() {
        val first = ShopProductDto(id = "a")
        val second = ShopProductDto(id = "b")
        assertFalse(isCurrentDetailResponse(1, 2))
        assertTrue(isCurrentDetailResponse(2, 2))
        assertSame(second, productForDetailRoute(second, mapOf("a" to first)))
    }

    @Test fun unverifiedComparisonAndDiscountNeverBecomePromotionClaims() {
        val product = ShopProductDto(id = "a", priceMinor = 1000, compareAtPriceMinor = 1500, discountPercent = 33)
        assertNull(homePreviousPriceMinor(product))
        assertNull(verifiedDiscountPercent(product))
        assertTrue(genuineDealProducts(listOf(product)).isEmpty())
    }

    @Test fun missingConditionAndDisabledBenefitsRemainUnclaimed() {
        val product = ShopProductDto(id = "a")
        assertTrue(product.specifications.isEmpty())
        assertTrue(product.verifiedBenefits.isEmpty())
        assertFalse(ShopBenefitFlags.buyerBadgesEnabled)
        assertEquals("Approved marketplace store", storeApprovalDescription("approved"))
        assertNull(storeApprovalDescription("pending"))
    }
}
