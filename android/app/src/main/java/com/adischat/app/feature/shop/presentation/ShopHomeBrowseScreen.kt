package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopHomeState
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.ui.theme.AdisGebeyaHomeV2Colors
import java.time.OffsetDateTime

enum class ShopHomeBrowseSection(val title: String) {
    Recommended("Browse products"),
    NewArrivals("New Arrivals"),
    Deals("Deals"),
}

internal fun homeBrowseProducts(section: ShopHomeBrowseSection, products: List<ShopProductDto>): List<ShopProductDto> =
    when (section) {
        ShopHomeBrowseSection.Recommended -> recommendedBrowseProducts(products)
        ShopHomeBrowseSection.NewArrivals -> products.distinctBy { it.id }.sortedByDescending { product ->
            product.createdAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        }
        ShopHomeBrowseSection.Deals -> genuineDealProducts(products)
    }

@Composable
internal fun ShopHomeBrowseScreen(
    section: ShopHomeBrowseSection,
    state: ShopHomeState,
    savedIds: Set<String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val products = homeBrowseProducts(section, state.products)
    val sidebar = com.adischat.app.ui.home.LocalSidebarBounds.current
    val startPad = if (sidebar != null) 60.dp else 12.dp
    BoxWithConstraints(Modifier.fillMaxSize().background(AdisGebeyaHomeV2Colors.Background)) {
        val columns = if (maxWidth >= 600.dp) 3 else 2
        val cardWidth = (maxWidth - 24.dp - 8.dp * (columns - 1)) / columns
        val cardHeight = homeProductCardHeight(cardWidth, LocalDensity.current.fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = startPad, end = 12.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Text(section.title, Modifier.weight(1f), color = AdisGebeyaHomeV2Colors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }
            when {
                state.loading -> items(4) { SkeletonCard() }
                state.error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(maintenance = state.maintenance, onRefresh = onRefresh)
                }
                products.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No products available right now.", Modifier.padding(16.dp), color = AdisGebeyaHomeV2Colors.Muted)
                }
                else -> items(products, key = { it.id }) { product ->
                    HomeV2ProductCard(product, product.id in savedIds, cardHeight, onProduct, onToggleFavorite)
                }
            }
        }
    }
}
