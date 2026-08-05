package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProtectedState


@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TrendingScreen(
    state: ShopHomeState,
    protectedState: ShopProtectedState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onCategory: (ShopCategoryDto) -> Unit,
    onAddToCart: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val savedIds = protectedState.favorites.map { it.productId }.toSet()
    val productsWithSales = state.products.filter { (it.soldCount ?: 0) > 0 }
    val productsWithRatings = state.products.filter { (it.reviewCount ?: 0) > 0 }
    val trendingProducts = state.products.sortedWith(
        compareByDescending<ShopProductDto> { it.soldCount ?: 0 }
            .thenByDescending { it.reviewCount ?: 0 }
            .thenByDescending { it.averageRating ?: 0.0 },
    )
    LazyColumn(
        Modifier.fillMaxSize().background(ShopBg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageTitle("Trending", "Popular products based on real marketplace activity") }
        when {
            state.loading -> items(4) { SkeletonCard() }
            state.error -> item { ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh) }
            else -> {
                if (trendingProducts.isEmpty()) {
                    item { EmptyCard("Trending products will appear when the marketplace has activity.") }
                } else {
                    item { ProductGridSection("Trending now", trendingProducts, onProduct, onToggleFavorite, savedIds, maxItems = 6) }
                }
                if (productsWithSales.isNotEmpty()) {
                    item {
                        ProductRail("Best sellers", productsWithSales.sortedByDescending { it.soldCount ?: 0 }, onProduct, onToggleFavorite, savedIds)
                    }
                }
                item { CategoryRail(state.categories, onCategory) }
                if (productsWithRatings.isNotEmpty()) {
                    item {
                        ProductRail("Top rated", productsWithRatings.sortedByDescending { it.averageRating ?: 0.0 }, onProduct, onToggleFavorite, savedIds)
                    }
                }
                if (state.banners.isNotEmpty()) item { PromoCarousel(state.banners) }
            }
        }
    }
}
