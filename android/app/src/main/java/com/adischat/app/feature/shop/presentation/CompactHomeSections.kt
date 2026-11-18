package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto

internal data class CompactHomeSections(
    val recommended: List<ShopProductDto>,
    val newArrivals: List<ShopProductDto>,
    val deals: List<ShopProductDto>,
)

internal fun recommendedBrowseProducts(products: List<ShopProductDto>): List<ShopProductDto> {
    val unique = products.distinctBy { it.id }
    val dealIds = genuineDealProducts(unique).mapTo(mutableSetOf()) { it.id }
    return unique.filterNot { it.id in dealIds }
}

internal fun homePreviousPriceMinor(product: ShopProductDto): Int? {
    // Return compareAtPriceMinor when it represents a genuine discount over current priceMinor
    return product.compareAtPriceMinor?.takeIf { it > product.priceMinor }
}

internal fun verifiedDiscountPercent(product: ShopProductDto): Int? =
    homePreviousPriceMinor(product)?.let { previous ->
        ((previous - product.priceMinor).toLong() * 100 / previous).toInt().takeIf { it in 1..99 }
    }

internal fun genuineDealProducts(products: List<ShopProductDto>): List<ShopProductDto> =
    products.distinctBy { it.id }.filter { homePreviousPriceMinor(it) != null }

internal fun compactHomeSections(products: List<ShopProductDto>): CompactHomeSections {
    val unique = products.distinctBy { it.id }
    val deals = genuineDealProducts(unique).take(4)
    val remaining = recommendedBrowseProducts(unique)
    return CompactHomeSections(
        recommended = remaining.take(8),
        newArrivals = remaining.drop(8).take(4),
        deals = deals,
    )
}
