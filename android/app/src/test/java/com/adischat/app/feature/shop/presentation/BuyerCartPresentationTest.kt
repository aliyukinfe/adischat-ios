package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCartItemDto
import com.adischat.app.feature.shop.data.ShopCartDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProductStoreDto
import com.adischat.app.feature.shop.data.ShopPublicVariantDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BuyerCartPresentationTest {
    @Test fun groupsByStableStoreIdEvenWhenNamesMatch() {
        val items = listOf(
            item("item-a", "store-a", "Hulu Store"),
            item("item-b", "store-b", "Hulu Store"),
            item("item-c", "store-a", "Renamed Store"),
        )
        val groups = cartStoreGroups(items)
        assertEquals(listOf("store-a", "store-b"), groups.keys.toList())
        assertEquals(listOf("item-a", "item-c"), groups.getValue("store-a").map { it.id })
        assertEquals(listOf("item-b"), groups.getValue("store-b").map { it.id })
    }

    @Test fun unknownStoresDoNotMergeAcrossProducts() {
        val groups = cartStoreGroups(listOf(
            ShopCartItemDto(id = "a", productId = "product-a"),
            ShopCartItemDto(id = "b", productId = "product-b"),
        ))
        assertEquals(2, groups.size)
    }

    @Test fun pendingCartMutationBlocksDuplicateAndConflictingActions() {
        assertTrue(canStartCartMutation(emptySet(), "item-a"))
        assertFalse(canStartCartMutation(emptySet(), ""))
        assertFalse(canStartCartMutation(setOf("item-a"), "item-a"))
        assertFalse(canStartCartMutation(setOf(CART_MUTATION_BUSY_ID), "item-b"))
        assertFalse(canStartCartMutation(setOf(CART_MUTATION_BUSY_ID)))
    }

    @Test fun loadingEmptyAndErrorRemainDistinct() {
        assertEquals(BuyerCartContentState.Loading, buyerCartContentState(null, true, null))
        assertEquals(BuyerCartContentState.Error, buyerCartContentState(null, false, "Offline"))
        assertEquals(BuyerCartContentState.Empty, buyerCartContentState(ShopCartDto(), false, null))
        assertEquals(BuyerCartContentState.Items, buyerCartContentState(ShopCartDto(items = listOf(item("a", "store-a", "Hulu"))), true, "Old error"))
    }

    @Test fun knownVariantStockBlocksOnlyExcessIncreases() {
        val cartItem = ShopCartItemDto(id = "cart-item", productId = "product", variantId = "variant", quantity = 1)
        val detail = ShopProductDto(id = "product", variants = listOf(ShopPublicVariantDto(id = "variant", availableQuantity = 1)))
        assertEquals("Only 1 item available", cartQuantityLimitMessage(cartItem, 2, detail))
        assertEquals(null, cartQuantityLimitMessage(cartItem, 1, detail))
        assertEquals(null, cartQuantityLimitMessage(cartItem, 0, detail))
        assertEquals(null, cartQuantityLimitMessage(cartItem, 2, detail.copy(id = "other")))
        assertEquals(null, cartQuantityLimitMessage(cartItem, 2, detail.copy(variants = listOf(ShopPublicVariantDto(id = "variant", availableQuantity = 3)))))
    }

    @Test fun knownNonVariantStockUsesAuthoritativeDetailOnly() {
        val cartItem = ShopCartItemDto(id = "cart-item", productId = "product", quantity = 2)
        assertEquals("Only 2 items available", cartQuantityLimitMessage(cartItem, 3, ShopProductDto(id = "product", aggregateAvailableQuantity = 2)))
        assertEquals(null, cartQuantityLimitMessage(cartItem, 3, ShopProductDto(id = "product")))
    }

    @Test fun onlyExplicitInventoryConflictGetsStockMessage() {
        fun httpError(status: Int, code: String): HttpException {
            val body = """{"error":{"code":"$code","message":"Rejected"}}""".toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(status, body))
        }
        assertEquals("Not enough stock for that quantity. Your cart was unchanged.", cartQuantityFailureMessage(httpError(409, "insufficient_inventory")))
        assertEquals("Couldn't update your cart. Try again.", cartQuantityFailureMessage(httpError(409, "minimum_order_quantity")))
        assertEquals("Your session expired. Please sign in again.", cartQuantityFailureMessage(httpError(401, "unauthorized")))
        assertEquals("Network unavailable", cartQuantityFailureMessage(IllegalStateException("Network unavailable")))
    }

    @Test fun checkoutAvailabilityUsesOnlyKnownStock() {
        val item = ShopCartItemDto(id = "cart-item", productId = "product", quantity = 2)
        assertEquals(null, cartAvailabilityMessage(item, null))
        assertEquals("Out of stock", cartAvailabilityMessage(item, 0))
        assertEquals("Only 1 item available", cartAvailabilityMessage(item, 1))
        assertEquals(null, cartAvailabilityMessage(item, 2))
        assertEquals(null, cartAvailabilityMessage(item, 3))
    }

    @Test fun cartProductNavigationUsesExactIdEvenWhenJoinIsMissingOrStale() {
        val missing = ShopCartItemDto(id = "item", productId = "laptop")
        val stale = missing.copy(product = ShopProductDto(id = "other", title = "Wrong product"))
        val matching = missing.copy(product = ShopProductDto(id = "laptop", title = "Laptop"))
        assertEquals("laptop", cartProductForDetails(missing).id)
        assertEquals("laptop", cartProductForDetails(stale).id)
        assertEquals("Laptop", cartProductForDetails(matching).title)
    }

    @Test fun checkoutBlocksOnlyConfirmedUnavailableQuantities() {
        val cart = ShopCartDto(items = listOf(ShopCartItemDto(id = "item", productId = "laptop", quantity = 2)))
        assertFalse(cartHasConfirmedUnavailableQuantity(cart, emptyMap()))
        assertTrue(cartHasConfirmedUnavailableQuantity(cart, mapOf("item" to 0)))
        assertTrue(cartHasConfirmedUnavailableQuantity(cart, mapOf("item" to 1)))
        assertFalse(cartHasConfirmedUnavailableQuantity(cart, mapOf("item" to 2)))
    }

    private fun item(id: String, storeId: String, storeName: String) = ShopCartItemDto(
        id = id,
        productId = "product-$id",
        product = ShopProductDto(store = ShopProductStoreDto(id = storeId, name = storeName)),
    )
}
