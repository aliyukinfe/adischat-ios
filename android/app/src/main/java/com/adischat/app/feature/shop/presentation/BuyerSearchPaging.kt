package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto

internal const val BUYER_SEARCH_PAGE_SIZE = 24

internal enum class BuyerSearchSort(val label: String, val apiOrder: String) {
    Newest("Newest", "created_at.desc,id.desc"),
    PriceLow("Price low", "price_minor.asc,id.asc"),
    PriceHigh("Price high", "price_minor.desc,id.desc"),
    TopRated("Top rated", "rating_average.desc.nullslast,id.asc"),
}

internal data class BuyerSearchKey(
    val term: String,
    val categorySlug: String?,
    val sort: BuyerSearchSort,
    val inStockOnly: Boolean,
)

internal data class BuyerSearchState(
    val key: BuyerSearchKey? = null,
    val products: List<ShopProductDto> = emptyList(),
    val nextOffset: Int = 0,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

internal fun normalizedBuyerSearchTerm(raw: String): String = raw.trim()
    .take(80)
    .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
    .replace(Regex("\\s+"), " ").trim()

internal fun appendBuyerSearchPage(state: BuyerSearchState, page: List<ShopProductDto>): BuyerSearchState =
    state.copy(
        products = (state.products + page).distinctBy { it.id },
        nextOffset = state.nextOffset + page.size,
        hasMore = page.size == BUYER_SEARCH_PAGE_SIZE,
        loading = false,
        loaded = true,
        error = null,
    )

internal fun buyerSearchCountLabel(count: Int): String =
    "Showing $count ${if (count == 1) "product" else "products"}"

internal fun isCurrentBuyerSearchResponse(
    requestKey: BuyerSearchKey,
    currentKey: BuyerSearchKey?,
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean = requestKey == currentKey && requestGeneration == currentGeneration

internal fun canLoadNextBuyerSearchPage(state: BuyerSearchState): Boolean =
    state.key != null && state.loaded && state.hasMore && !state.loading && state.error == null

internal fun canRetryBuyerSearch(state: BuyerSearchState): Boolean =
    state.key != null && !state.loading && state.error != null
