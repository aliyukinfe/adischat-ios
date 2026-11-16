package com.adischat.app.feature.shop.data

import com.adischat.app.BuildConfig

internal object ShopReviewedMedia {
    // Keep production clients on their current catalog until private Storage and cutover are verified.
    const val enabled = false
    private val uuid = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")

    fun url(kind: String, id: String?, baseUrl: String = BuildConfig.ADISHOP_SUPABASE_URL): String? {
        if (kind !in setOf("image", "product-cover", "store-logo", "store-cover") || id == null || !uuid.matches(id)) return null
        val base = baseUrl.trimEnd('/')
        if (!base.startsWith("https://")) return null
        return "$base/functions/v1/adishop-product-reviews?action=reviewed-media&kind=$kind&id=$id"
    }

    fun product(product: ShopProductDto): ShopProductDto = product.copy(
        images = product.images?.map { it.copy(imageUrl = url("image", it.id)) },
        store = product.store?.let { store ->
            store.copy(logoUrl = url("store-logo", store.id), coverUrl = url("store-cover", store.id))
        },
    )

    fun store(store: ShopStoreDto): ShopStoreDto = store.copy(
        logoUrl = url("store-logo", store.id), coverUrl = url("store-cover", store.id),
    )

    fun cart(cart: ShopCartDto): ShopCartDto = cart.copy(items = cart.items.map { item ->
        item.copy(product = item.product?.let(::product))
    })

    fun favorite(favorite: ShopFavoriteDto): ShopFavoriteDto = favorite.copy(
        product = favorite.product?.let(::product),
    )

    fun recentlyViewed(item: ShopRecentlyViewedDto): ShopRecentlyViewedDto = item.copy(
        product = item.product?.let(::product),
    )

    fun order(order: ShopOrderDto): ShopOrderDto = order.copy(groups = order.groups?.map { group ->
        group.copy(items = group.items?.map { item ->
            item.copy(imageSnapshot = url("product-cover", item.productId))
        })
    })
}
