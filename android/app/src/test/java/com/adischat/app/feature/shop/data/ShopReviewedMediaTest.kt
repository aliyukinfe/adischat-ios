package com.adischat.app.feature.shop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.http.GET

class ShopReviewedMediaTest {
    private val imageId = "30000000-0000-4000-8000-000000000001"
    private val storeId = "10000000-0000-4000-8000-000000000001"

    @Test fun proxyUrlUsesOnlyEntityKindAndId() {
        assertEquals(
            "https://example.supabase.co/functions/v1/adishop-product-reviews?action=reviewed-media&kind=image&id=$imageId",
            ShopReviewedMedia.url("image", imageId, "https://example.supabase.co/"),
        )
        assertNull(ShopReviewedMedia.url("image", "../private", "https://example.supabase.co"))
        assertNull(ShopReviewedMedia.url("other", imageId, "https://example.supabase.co"))
        assertFalse(ShopReviewedMedia.enabled)
    }

    @Test fun mappedProductRetainsIdentityAndUsesReviewedImageUrls() {
        val original = ShopProductDto(id = "product", title = "Laptop", images = listOf(
            ShopProductImageDto(id = imageId, imageUrl = "https://old-public-url.invalid/photo.png"),
        ), store = ShopProductStoreDto(id = storeId, name = "Controlled store"))
        val mapped = ShopReviewedMedia.product(original)
        assertEquals(original.id, mapped.id)
        assertEquals(original.title, mapped.title)
        assertEquals(ShopReviewedMedia.url("image", imageId), mapped.images?.single()?.imageUrl)
        assertEquals(ShopReviewedMedia.url("store-logo", storeId), mapped.store?.logoUrl)
    }

    @Test fun reviewedListMethodsDoNotRequestRawMediaColumns() {
        for (methodName in listOf("reviewedProducts", "reviewedSearchProducts", "reviewedSearchCatalogPage", "reviewedStores")) {
            val route = ShopSupabaseApi::class.java.declaredMethods.first { it.name == methodName }
                .getAnnotation(GET::class.java).value
            assertFalse(route.contains("image_url"))
            assertFalse(route.contains("logo_url"))
            assertFalse(route.contains("cover_url"))
        }
    }

    @Test fun cartAndOrderMediaNeverReuseOldPublicUrls() {
        val cart = ShopCartDto(items = listOf(ShopCartItemDto(product = ShopProductDto(
            images = listOf(ShopProductImageDto(id = imageId, imageUrl = "https://old-public/photo")),
        ))))
        assertEquals(ShopReviewedMedia.url("image", imageId),
            ShopReviewedMedia.cart(cart).items.single().product?.images?.single()?.imageUrl)
        val order = ShopOrderDto(groups = listOf(ShopOrderGroupDto(items = listOf(
            ShopOrderItemDto(productId = imageId, imageSnapshot = "https://old-public/order-photo"),
            ShopOrderItemDto(imageSnapshot = "https://old-public/unlinked"),
        ))))
        val mapped = ShopReviewedMedia.order(order).groups!!.single().items!!
        assertEquals(ShopReviewedMedia.url("product-cover", imageId), mapped[0].imageSnapshot)
        assertNull(mapped[1].imageSnapshot)
    }
}
