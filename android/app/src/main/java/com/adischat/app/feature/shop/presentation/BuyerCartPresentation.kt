package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopCartItemDto
import com.adischat.app.feature.shop.data.ShopCartDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopStateReducers
import com.google.gson.JsonParser
import retrofit2.HttpException

internal const val CART_MUTATION_BUSY_ID = "cart-mutation"
internal const val CART_CLEAR_BUSY_ID = "cart-clear"

internal enum class BuyerCartContentState { Loading, Error, Empty, Items }

internal fun buyerCartContentState(cart: ShopCartDto?, loading: Boolean, error: String?): BuyerCartContentState = when {
    cart?.items?.isNotEmpty() == true -> BuyerCartContentState.Items
    !error.isNullOrBlank() -> BuyerCartContentState.Error
    loading && cart == null -> BuyerCartContentState.Loading
    else -> BuyerCartContentState.Empty
}

internal fun cartStoreGroups(items: List<ShopCartItemDto>): Map<String, List<ShopCartItemDto>> =
    items.groupBy { item ->
        item.product?.store?.id?.takeIf(String::isNotBlank)
            ?: "unknown:${item.productId.ifBlank { item.id }}"
    }

internal fun canStartCartMutation(busyIds: Set<String>, itemId: String? = null): Boolean =
    CART_MUTATION_BUSY_ID !in busyIds && (itemId == null || itemId.isNotBlank() && itemId !in busyIds)

internal fun cartQuantityLimit(item: ShopCartItemDto, detail: ShopProductDto?): Int? {
    if (detail == null || detail.id != item.productId) return null
    return if (item.variantId != null) {
        detail.variants.firstOrNull { it.id == item.variantId && it.status == "active" }?.availableQuantity
    } else if (!detail.hasVariants && detail.variants.isEmpty()) {
        detail.aggregateAvailableQuantity ?: detail.aggregateAvailable
    } else null
}

internal fun cartQuantityLimitMessage(item: ShopCartItemDto, requested: Int, detail: ShopProductDto?): String? {
    if (requested <= item.quantity) return null
    val available = cartQuantityLimit(item, detail) ?: return null
    return if (requested > available) "Only $available ${if (available == 1) "item" else "items"} available" else null
}

internal fun cartAvailabilityMessage(item: ShopCartItemDto, available: Int?): String? = when {
    available == null -> null
    available <= 0 -> "Out of stock"
    item.quantity > available -> "Only $available ${if (available == 1) "item" else "items"} available"
    else -> null
}

internal fun cartProductForDetails(item: ShopCartItemDto): ShopProductDto =
    item.product?.takeIf { it.id == item.productId } ?: ShopProductDto(id = item.productId)

internal fun cartHasConfirmedUnavailableQuantity(cart: ShopCartDto?, availableByItemId: Map<String, Int>): Boolean =
    cart?.items?.any { cartAvailabilityMessage(it, availableByItemId[it.id]) != null } == true

internal fun cartQuantityFailureMessage(error: Throwable): String {
    if (error is HttpException && error.code() == 409) {
        val code = runCatching {
            JsonParser.parseString(error.response()?.errorBody()?.string().orEmpty())
                .asJsonObject.getAsJsonObject("error")?.get("code")?.asString
        }.getOrNull()
        if (code == "insufficient_inventory") return "Not enough stock for that quantity. Your cart was unchanged."
    }
    return ShopStateReducers.safeErrorMessage(error.message, "Couldn't update your cart. Try again.")
}
