package com.adischat.app.feature.shop.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.adischat.app.ui.home.AdaptiveFlowAround
import com.adischat.app.ui.home.LocalSidebarBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarketplaceSearchScreen(
    searchState: BuyerSearchState,
    categories: List<ShopCategoryDto>,
    recentlyViewed: List<ShopProductDto>,
    savedIds: Set<String>,
    loading: Boolean,
    onBack: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onCategory: (ShopCategoryDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
    onSearch: (BuyerSearchKey) -> Unit,
    onClearSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var categorySlug by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(BuyerSearchSort.Newest) }
    var inStockOnly by rememberSaveable { mutableStateOf(false) }
    val term = normalizedBuyerSearchTerm(query)
    val key = BuyerSearchKey(term, categorySlug, sort, inStockOnly)
    val active = searchState.key == key
    val results = if (active) searchState.products else emptyList()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var previousKey by remember { mutableStateOf(key) }

    val isImeVisible = WindowInsets.isImeVisible

    BackHandler {
        if (isImeVisible) {
            focusManager.clearFocus()
        } else if (query.isNotEmpty()) {
            query = ""
            onClearSearch()
        } else {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(key) {
        if (previousKey != key) {
            previousKey = key
        }
        if (term.length < 2) onClearSearch()
        else {
            delay(350)
            onSearch(key)
        }
    }
    LaunchedEffect(key, active, searchState.loading, searchState.hasMore, searchState.error, searchState.nextOffset) {
        if (term.length >= 2 && active && searchState.loaded && searchState.hasMore && !searchState.loading && searchState.error == null) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                if (last >= listState.layoutInfo.totalItemsCount - 3) onLoadMore()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AdisGebeyaColors.Background),
    ) {
        val sidebar = LocalSidebarBounds.current
        val startPad = if (sidebar != null) 60.dp else 8.dp
        Row(
            Modifier.fillMaxWidth().padding(start = startPad, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AdisGebeyaColors.PrimaryText)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text("Search products or brands", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AdisGebeyaColors.SecondaryText) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; onClearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AdisGebeyaColors.SecondaryText)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AdisGebeyaColors.SecondarySurface,
                    unfocusedContainerColor = AdisGebeyaColors.SecondarySurface,
                    focusedBorderColor = AdisGebeyaColors.Primary,
                    unfocusedBorderColor = AdisGebeyaColors.Divider,
                    focusedTextColor = AdisGebeyaColors.PrimaryText,
                    unfocusedTextColor = AdisGebeyaColors.PrimaryText,
                    cursorColor = AdisGebeyaColors.Primary,
                ),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        if (query.isBlank()) {
            if (loading && categories.isEmpty()) item { AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) { SkeletonCard() } }
            if (categories.isNotEmpty()) {
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                        SearchSection("Browse categories") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(categories, key = { it.id }) { category ->
                                    SearchChip(category.name, false) { onCategory(category) }
                                }
                            }
                        }
                    }
                }
            }
            if (recentlyViewed.isNotEmpty()) {
                item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Recently viewed", color = AdisGebeyaColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            ProductGridSection(null, recentlyViewed, onProduct, onToggleFavorite, savedIds, maxItems = 6)
                        }
                    }
                }
            }
            if (categories.isEmpty() && recentlyViewed.isEmpty() && !loading) {
                item { AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) { PremiumEmptyState("Start searching", "Find products, stores and categories in AdisGebeya.", Icons.Default.Search, Modifier.padding(horizontal = 16.dp)) } }
            }
        } else if (term.length < 2) {
            item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    PremiumEmptyState(
                        "Keep typing",
                        "Enter at least two letters or numbers to search products.",
                        Icons.Default.Search,
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else {
            item {
                AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (!active || searchState.loading && !searchState.loaded) "Searching..." else buyerSearchCountLabel(results.size),
                            color = AdisGebeyaColors.PrimaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BuyerSearchSort.entries) { option -> SearchChip(option.label, sort == option) { sort = option } }
                            item { SearchChip("In stock", inStockOnly) { inStockOnly = !inStockOnly } }
                        }
                        if (categories.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { SearchChip("All", categorySlug == null) { categorySlug = null } }
                                items(categories, key = { it.id }) { category ->
                                    SearchChip(category.name, categorySlug == category.slug) { categorySlug = category.slug }
                                }
                            }
                        }
                    }
                }
            }
            when {
                !active || searchState.loading && !searchState.loaded -> items(2) { AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) { SkeletonCard() } }
                searchState.error != null && results.isEmpty() -> item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                        SearchRetry(searchState.error, onRetry)
                    }
                }
                results.isEmpty() -> item {
                    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                        PremiumEmptyState("No products found", "Try another search or remove a filter.", Icons.Default.Search, Modifier.padding(horizontal = 16.dp))
                    }
                }
                else -> {
                    items(results.chunked(2), key = { "search-${it.first().id}" }) { row ->
                        AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) {
                            SearchProductRow(row, savedIds, onProduct, onToggleFavorite)
                        }
                    }
                    if (searchState.loading) item { AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) { SkeletonCard() } }
                    searchState.error?.let { error -> item { AdaptiveFlowAround(besideStart = 60.dp, belowStart = 12.dp) { SearchRetry(error, onRetry) } } }
                }
            }
        }
    }
}
}

@Composable
private fun SearchProductRow(
    row: List<ShopProductDto>,
    savedIds: Set<String>,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val width = (maxWidth - 8.dp) / 2
        val height = homeProductCardHeight(width, LocalDensity.current.fontScale)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { product ->
                Box(Modifier.weight(1f)) {
                    HomeV2ProductCard(product, product.id in savedIds, height, onProduct, onToggleFavorite)
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchRetry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = AdisGebeyaColors.Error, fontSize = 13.sp)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SearchSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = AdisGebeyaColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
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
        shape = RoundedCornerShape(10.dp),
    )
}
