package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.ui.home.AdaptiveFlowAround
import com.adischat.app.ui.theme.AdisGebeyaColors

private enum class TrendingTab(val label: String) {
    ForYou("For you"),
    Popular("Popular"),
    New("New"),
    Deals("Deals"),
}

@Composable
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
    var tab by remember { mutableStateOf(TrendingTab.Popular) }
    var category by remember { mutableStateOf<String?>(null) }
    val savedIds = protectedState.favorites.map { it.productId }.toSet()
    val visibleProducts = remember(state.products, tab, category) {
        val ranked = when (tab) {
            TrendingTab.ForYou -> state.products
            TrendingTab.Popular -> state.products
                .filter { (it.soldCount ?: 0) > 0 || (it.reviewCount ?: 0) > 0 }
                .sortedWith(compareByDescending<ShopProductDto> { it.soldCount ?: 0 }.thenByDescending { it.reviewCount ?: 0 }.thenByDescending { it.averageRating ?: 0.0 })
            TrendingTab.New -> state.products
            TrendingTab.Deals -> genuineDealProducts(state.products)
        }
        category?.let { slug -> ranked.filter { it.categorySlug == slug } } ?: ranked
    }

    LazyColumn(
        Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Trending", color = AdisGebeyaColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TrendingTab.entries.forEach { option ->
                            Text(
                                option.label,
                                color = if (tab == option) AdisGebeyaColors.Primary else AdisGebeyaColors.SecondaryText,
                                fontSize = 13.sp,
                                fontWeight = if (tab == option) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clickable { tab = option }.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (state.categories.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { TrendingChip("All", category == null) { category = null } }
                            items(state.categories, key = { it.id }) { item ->
                                TrendingChip(item.name, category == item.slug) { category = item.slug }
                            }
                        }
                    }
                }
            }
        }
        when {
            state.loading -> items(4) {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    SkeletonCard()
                }
            }
            state.error -> item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh)
                }
            }
            visibleProducts.isEmpty() -> item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    PremiumEmptyState(
                        title = if (tab == TrendingTab.Deals) "No active deals" else "No products here yet",
                        message = "Try another tab or category.",
                        icon = androidx.compose.material.icons.Icons.Default.TrendingUp,
                    )
                }
            }
            else -> item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    PremiumHomeProductGrid(
                        title = "",
                        products = visibleProducts,
                        savedIds = savedIds,
                        onProduct = onProduct,
                        onToggleFavorite = onToggleFavorite,
                        onSeeAll = {},
                        showSeeAll = false,
                        maxItems = Int.MAX_VALUE,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AdisGebeyaColors.SecondarySurface,
            labelColor = AdisGebeyaColors.SecondaryText,
            selectedContainerColor = AdisGebeyaColors.Primary,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AdisGebeyaColors.Divider,
            selectedBorderColor = AdisGebeyaColors.Primary,
        ),
    )
}
