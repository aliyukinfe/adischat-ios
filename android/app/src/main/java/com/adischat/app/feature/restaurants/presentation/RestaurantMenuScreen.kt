package com.adischat.app.feature.restaurants.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Resolves the active sidebar category from the current food list scroll state without
 * jumping to the final short category when the list is at the very top or non-scrollable.
 */
internal fun resolveVisibleActiveCategoryId(
    sectionMap: MenuSectionIndexMap,
    firstMeaningfulIndex: Int,
    lastVisibleIndex: Int,
    canScrollForward: Boolean,
    canScrollBackward: Boolean,
): String? {
    if (sectionMap.isEmpty) return null
    if (!canScrollForward && canScrollBackward && lastVisibleIndex >= 0) {
        sectionMap.categoryIdForIndex(lastVisibleIndex)?.let { return it }
    }
    return sectionMap.categoryIdForIndex(firstMeaningfulIndex.coerceAtLeast(0))
        ?: sectionMap.categories.firstOrNull()?.id
}

/**
 * Super-app light-mode restaurant menu screen combining an optional horizontally scrolling
 * voucher/promotion strip, compact left category rail with spotlight filters, and dense
 * interactive dish cards with + Add -> stepper animations.
 */
@Composable
fun RestaurantMenuScreen(
    state: RestaurantMenuUiState,
    onBranchSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMenuItemClick: (MenuItemUiModel) -> Unit,
    onRetryClick: () -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    cartQuantityByItemId: Map<String, Int> = emptyMap(),
    showStandaloneChrome: Boolean = true,
    bottomContentPadding: Dp = 24.dp,
    onQuickAddClick: (MenuItemUiModel) -> Unit = onMenuItemClick,
    onQuickDecrementClick: (MenuItemUiModel) -> Unit = { _ -> },
    searchQuery: String = "",
    activeSpotlight: MenuSpotlightFilter = MenuSpotlightFilter.NONE,
    onSpotlightSelected: (MenuSpotlightFilter) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onPromotionClick: (RestaurantPromotionUiModel) -> Unit = {},
) {
    if (!showStandaloneChrome) {
        RestaurantMenuBody(
            state = state,
            onBranchSelected = onBranchSelected,
            onCategorySelected = onCategorySelected,
            onMenuItemClick = onMenuItemClick,
            onQuickAddClick = onQuickAddClick,
            onQuickDecrementClick = onQuickDecrementClick,
            onRetryClick = onRetryClick,
            cartQuantityByItemId = cartQuantityByItemId,
            showBrowseBanner = false,
            showBranchSwitcher = false,
            bottomContentPadding = bottomContentPadding,
            searchQuery = searchQuery,
            activeSpotlight = activeSpotlight,
            onSpotlightSelected = onSpotlightSelected,
            onClearFilters = onClearFilters,
            onPromotionClick = onPromotionClick,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    RestaurantsLightTheme {
        val extColors = RestaurantsLightTheme.colors
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = extColors.mainBackground,
            topBar = {
                RestaurantsTopBar(
                    title = state.restaurantName.ifBlank { "Menu" },
                    subtitle = state.selectedBranch?.name ?: state.restaurantBrandTagline,
                    onBackClick = onBackClick,
                    trailingContent = {
                        RestaurantTagPill(
                            label = if (state.isBrowseOnly) "Browse Only" else "Table Active",
                            containerColor = if (state.isBrowseOnly) {
                                extColors.qaNoticeBackground
                            } else {
                                extColors.brandGreenSoft
                            },
                            contentColor = if (state.isBrowseOnly) {
                                extColors.qaNoticeText
                            } else {
                                extColors.brandGreenDeep
                            },
                        )
                    },
                )
            },
        ) { innerPadding ->
            RestaurantMenuBody(
                state = state,
                onBranchSelected = onBranchSelected,
                onCategorySelected = onCategorySelected,
                onMenuItemClick = onMenuItemClick,
                onQuickAddClick = onQuickAddClick,
                onQuickDecrementClick = onQuickDecrementClick,
                onRetryClick = onRetryClick,
                cartQuantityByItemId = cartQuantityByItemId,
                showBrowseBanner = state.isBrowseOnly,
                showBranchSwitcher = state.branches.size > 1,
                bottomContentPadding = bottomContentPadding,
                searchQuery = searchQuery,
                activeSpotlight = activeSpotlight,
                onSpotlightSelected = onSpotlightSelected,
                onClearFilters = onClearFilters,
                onPromotionClick = onPromotionClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun RestaurantMenuBody(
    state: RestaurantMenuUiState,
    onBranchSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onMenuItemClick: (MenuItemUiModel) -> Unit,
    onQuickAddClick: (MenuItemUiModel) -> Unit,
    onQuickDecrementClick: (MenuItemUiModel) -> Unit,
    onRetryClick: () -> Unit,
    cartQuantityByItemId: Map<String, Int>,
    showBrowseBanner: Boolean,
    showBranchSwitcher: Boolean,
    bottomContentPadding: Dp,
    searchQuery: String,
    activeSpotlight: MenuSpotlightFilter,
    onSpotlightSelected: (MenuSpotlightFilter) -> Unit,
    onClearFilters: () -> Unit,
    onPromotionClick: (RestaurantPromotionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors

    when (val load = state.loadState) {
        is RestaurantsLoadState.Disabled -> {
            Box(
                modifier = modifier.background(extColors.mainBackground),
                contentAlignment = Alignment.TopCenter,
            ) {
                RestaurantsDisabledNoticeCard(
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            }
        }

        is RestaurantsLoadState.Loading -> {
            Box(
                modifier = modifier.background(extColors.mainBackground),
                contentAlignment = Alignment.TopCenter,
            ) {
                RestaurantsLoadingSkeletonList(
                    cardCount = 3,
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            }
        }

        is RestaurantsLoadState.Error -> {
            Box(
                modifier = modifier.background(extColors.mainBackground),
                contentAlignment = Alignment.TopCenter,
            ) {
                RestaurantsStateMessageCard(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Unable to load menu",
                    message = load.message,
                    actionLabel = "Retry",
                    onActionClick = onRetryClick,
                    isError = true,
                    modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                )
            }
        }

        is RestaurantsLoadState.Empty,
        is RestaurantsLoadState.Content, -> {
            val fullSectionMap = remember(state.categories, state.items) {
                MenuSectionIndexMap.build(
                    categories = state.categories,
                    items = state.items,
                )
            }
            val isFiltered = searchQuery.isNotBlank() || activeSpotlight != MenuSpotlightFilter.NONE
            val filteredItems = remember(state.items, searchQuery, activeSpotlight) {
                if (isFiltered) {
                    RestaurantsUiReducers.filterMenuItemsForDisplay(
                        items = state.items,
                        searchQuery = searchQuery,
                        spotlightFilter = activeSpotlight,
                    )
                } else {
                    state.items
                }
            }
            val displaySectionMap = remember(state.categories, filteredItems, fullSectionMap, isFiltered) {
                if (!isFiltered) fullSectionMap
                else MenuSectionIndexMap.build(
                    categories = state.categories,
                    items = filteredItems,
                )
            }
            val availableSpotlights = remember(state.items) {
                RestaurantsUiReducers.availableSpotlights(state.items)
            }
            val categoryById = remember(state.categories) {
                state.categories.associateBy { it.id }
            }

            Column(
                modifier = modifier.background(extColors.mainBackground),
            ) {
                if (state.promotions.isNotEmpty()) {
                    VoucherPromotionStrip(
                        promotions = state.promotions,
                        onPromotionClick = onPromotionClick,
                    )
                }

                if (showBrowseBanner || (showBranchSwitcher && state.branches.size > 1)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showBrowseBanner) {
                            DineInNoticeBanner(
                                headline = "Browse-Only Dine-In Menu",
                                supportingText = "Inspect dishes, branch prices, and options. Scan a table QR when seated to place an order.",
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                            )
                        }
                        if (showBranchSwitcher && state.branches.size > 1) {
                            BranchSwitcherStrip(
                                branches = state.branches,
                                selectedBranchId = state.selectedBranch?.id,
                                onBranchSelected = onBranchSelected,
                            )
                        }
                    }
                    HorizontalDivider(color = extColors.divider)
                }

                if (load is RestaurantsLoadState.Empty || fullSectionMap.isEmpty) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.Restaurant,
                            title = "No menu dishes available",
                            message = "Switch branches or check back later to see available items.",
                            modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                        )
                    }
                } else {
                    ContinuousCategoryMenuSplitView(
                        sidebarCategories = fullSectionMap.categories,
                        sectionMap = displaySectionMap,
                        categoryById = categoryById,
                        availableSpotlights = availableSpotlights,
                        activeSpotlight = activeSpotlight,
                        onSpotlightSelected = onSpotlightSelected,
                        isFiltered = isFiltered,
                        searchQuery = searchQuery,
                        filteredCount = filteredItems.size,
                        onClearFilters = onClearFilters,
                        selectedCategoryOverride = state.selectedCategoryId,
                        selectedBranchName = state.selectedBranch?.name,
                        cartQuantityByItemId = cartQuantityByItemId,
                        bottomContentPadding = bottomContentPadding,
                        onCategorySelected = onCategorySelected,
                        onMenuItemClick = onMenuItemClick,
                        onQuickAddClick = onQuickAddClick,
                        onQuickDecrementClick = onQuickDecrementClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoucherPromotionStrip(
    promotions: List<RestaurantPromotionUiModel>,
    onPromotionClick: (RestaurantPromotionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val hasDemoPromo = promotions.any { it.isDemoFixture }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = extColors.mainBackground,
        border = BorderStroke(1.dp, extColors.cardBorder),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .background(extColors.voucherStripLabelBg)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Voucher &\nPromotion",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = extColors.primaryText,
                )
                if (hasDemoPromo) {
                    Text(
                        text = "QA Demo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = extColors.qaNoticeText,
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 7.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(promotions, key = { it.id }) { promo ->
                    VoucherCouponCard(
                        promo = promo,
                        onClick = { onPromotionClick(promo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoucherCouponCard(
    promo: RestaurantPromotionUiModel,
    onClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    when (promo.tone) {
        PromotionTone.CORAL_RED -> {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(extColors.voucherRedStart, extColors.voucherRedEnd),
                            ),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFD166),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ConfirmationNumber,
                                contentDescription = null,
                                tint = extColors.voucherRedStart,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = promo.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                        )
                        if (!promo.subtitle.isNullOrBlank()) {
                            Text(
                                text = promo.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        PromotionTone.GOLD,
        PromotionTone.CREAM, -> {
            val bg = if (promo.tone == PromotionTone.GOLD) extColors.voucherGoldSurface else extColors.voucherCreamSurface
            val border = if (promo.tone == PromotionTone.GOLD) extColors.voucherGoldBorder else extColors.voucherCreamBorder
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(10.dp),
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = promo.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = extColors.voucherGoldText,
                        maxLines = 1,
                    )
                    if (!promo.subtitle.isNullOrBlank()) {
                        Text(
                            text = promo.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                            ),
                            color = extColors.voucherGoldText.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinuousCategoryMenuSplitView(
    sidebarCategories: List<MenuCategoryUiModel>,
    sectionMap: MenuSectionIndexMap,
    categoryById: Map<String, MenuCategoryUiModel>,
    availableSpotlights: Set<MenuSpotlightFilter>,
    activeSpotlight: MenuSpotlightFilter,
    onSpotlightSelected: (MenuSpotlightFilter) -> Unit,
    isFiltered: Boolean,
    searchQuery: String,
    filteredCount: Int,
    onClearFilters: () -> Unit,
    selectedCategoryOverride: String?,
    selectedBranchName: String?,
    cartQuantityByItemId: Map<String, Int>,
    bottomContentPadding: Dp,
    onCategorySelected: (String?) -> Unit,
    onMenuItemClick: (MenuItemUiModel) -> Unit,
    onQuickAddClick: (MenuItemUiModel) -> Unit,
    onQuickDecrementClick: (MenuItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    val foodListState = rememberLazyListState()
    val sidebarListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var activeCategoryId by remember(sidebarCategories) {
        val initial = selectedCategoryOverride?.takeIf { id ->
            sidebarCategories.any { it.id == id }
        } ?: sidebarCategories.firstOrNull()?.id
        mutableStateOf(initial)
    }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var programmaticScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(foodListState, sectionMap, activeSpotlight, isFiltered) {
        if (isFiltered) return@LaunchedEffect
        var lastProcessedPosition: Pair<Int, Int>? = null
        snapshotFlow {
            val layoutInfo = foodListState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            val firstMeaningful = visible.firstOrNull { (it.offset + it.size) > 16 }?.index
                ?: foodListState.firstVisibleItemIndex
            val lastVisible = visible.lastOrNull()?.index ?: -1
            ScrollObservation(
                firstIdx = foodListState.firstVisibleItemIndex,
                firstOffset = foodListState.firstVisibleItemScrollOffset,
                firstMeaningfulIndex = firstMeaningful,
                lastVisibleIndex = lastVisible,
                canScrollForward = foodListState.canScrollForward,
                canScrollBackward = foodListState.canScrollBackward,
                isProgrammatic = isProgrammaticScroll,
            )
        }
            .distinctUntilChanged()
            .collect { obs ->
                if (obs.isProgrammatic) return@collect
                val currentPosition = obs.firstIdx to obs.firstOffset
                if (lastProcessedPosition == null) {
                    lastProcessedPosition = currentPosition
                    return@collect
                }
                if (currentPosition == lastProcessedPosition) return@collect
                lastProcessedPosition = currentPosition

                val nextCategory = resolveVisibleActiveCategoryId(
                    sectionMap = sectionMap,
                    firstMeaningfulIndex = obs.firstMeaningfulIndex,
                    lastVisibleIndex = obs.lastVisibleIndex,
                    canScrollForward = obs.canScrollForward,
                    canScrollBackward = obs.canScrollBackward,
                )
                if (nextCategory != null && nextCategory != activeCategoryId) {
                    activeCategoryId = nextCategory
                }
            }
    }

    val onSidebarCategoryTap: (String) -> Unit = { categoryId ->
        onSpotlightSelected(MenuSpotlightFilter.NONE)
        activeCategoryId = categoryId
        onCategorySelected(categoryId)
        val targetIndex = sectionMap.startIndexForCategory(categoryId)
        if (targetIndex != null) {
            programmaticScrollJob?.cancel()
            programmaticScrollJob = coroutineScope.launch {
                isProgrammaticScroll = true
                try {
                    foodListState.animateScrollToItem(targetIndex)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.background(extColors.feedBackground),
    ) {
        val sidebarWidth = resolveCategorySidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            RestaurantCategorySidebar(
                categories = sidebarCategories,
                activeCategoryId = activeCategoryId,
                onCategoryClick = onSidebarCategoryTap,
                width = sidebarWidth,
                bottomContentPadding = bottomContentPadding,
                listState = sidebarListState,
                availableSpotlights = availableSpotlights,
                activeSpotlight = activeSpotlight,
                onSpotlightSelected = onSpotlightSelected,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(extColors.feedBackground),
            ) {
                if (isFiltered) {
                    MenuFilterBanner(
                        searchQuery = searchQuery,
                        activeSpotlight = activeSpotlight,
                        resultCount = filteredCount,
                        onClearFilters = onClearFilters,
                    )
                }

                if (sectionMap.isEmpty) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.Restaurant,
                            title = "No matching dishes",
                            message = "Try another keyword or clear your active menu filter.",
                            actionLabel = "Show All Menu",
                            onActionClick = onClearFilters,
                        )
                    }
                } else {
                    LazyColumn(
                        state = foodListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = 6.dp,
                            bottom = bottomContentPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(
                            items = sectionMap.entries,
                            key = { it.key },
                            contentType = { entry ->
                                when (entry) {
                                    is MenuSectionEntry.CategoryHeader -> "category_header"
                                    is MenuSectionEntry.FoodItem -> "food_item"
                                }
                            },
                        ) { entry ->
                            when (entry) {
                                is MenuSectionEntry.CategoryHeader -> {
                                    MenuSectionHeading(
                                        title = entry.category.name,
                                        itemCount = entry.itemCount,
                                    )
                                }

                                is MenuSectionEntry.FoodItem -> {
                                    val category = categoryById[entry.item.categoryId]
                                    val fallbackIconKey = resolveCategoryFallbackIconKey(
                                        iconKey = category?.iconKey,
                                        categoryName = category?.name.orEmpty(),
                                    )
                                    MenuDishCard(
                                        item = entry.item,
                                        fallbackIconKey = fallbackIconKey,
                                        branchName = selectedBranchName,
                                        inCartQuantity = cartQuantityByItemId[entry.item.id] ?: 0,
                                        onCardClick = { onMenuItemClick(entry.item) },
                                        onQuickAddClick = { onQuickAddClick(entry.item) },
                                        onQuickDecrementClick = { onQuickDecrementClick(entry.item) },
                                        brandColorHex = state.brandColorHex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuFilterBanner(
    searchQuery: String,
    activeSpotlight: MenuSpotlightFilter,
    resultCount: Int,
    onClearFilters: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val filterParts = buildList {
        if (activeSpotlight != MenuSpotlightFilter.NONE) add(activeSpotlight.label)
        if (searchQuery.isNotBlank()) add("\"${searchQuery.trim()}\"")
    }
    Surface(
        color = extColors.brandGreenSoft,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${filterParts.joinToString(" • ")} ($resultCount)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = extColors.brandGreenDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClearFilters),
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = BorderStroke(1.dp, extColors.brandGreenBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear filter",
                        tint = extColors.brandGreenDeep,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.brandGreenDeep,
                    )
                }
            }
        }
    }
}

private data class ScrollObservation(
    val firstIdx: Int,
    val firstOffset: Int,
    val firstMeaningfulIndex: Int,
    val lastVisibleIndex: Int,
    val canScrollForward: Boolean,
    val canScrollBackward: Boolean,
    val isProgrammatic: Boolean,
)

@Composable
private fun MenuSectionHeading(
    title: String,
    itemCount: Int,
) {
    val extColors = RestaurantsLightTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 1.dp, start = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = extColors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$itemCount",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = extColors.secondaryText,
        )
    }
}

@Composable
private fun BranchSwitcherStrip(
    branches: List<RestaurantBranchUiModel>,
    selectedBranchId: String?,
    onBranchSelected: (String) -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = extColors.secondaryText,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Menu for branch",
                style = MaterialTheme.typography.labelMedium,
                color = extColors.secondaryText,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(branches, key = { it.id }) { branch ->
                val selected = branch.id == selectedBranchId
                FilterChip(
                    selected = selected,
                    onClick = { onBranchSelected(branch.id) },
                    label = {
                        Text(
                            text = branch.name,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    shape = RoundedCornerShape(RestaurantsUiDefaults.PillRadius),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = extColors.brandGreenSoft,
                        selectedLabelColor = extColors.brandGreenDeep,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MenuDishCard(
    item: MenuItemUiModel,
    fallbackIconKey: String,
    branchName: String?,
    inCartQuantity: Int = 0,
    onCardClick: () -> Unit,
    onQuickAddClick: () -> Unit,
    onQuickDecrementClick: () -> Unit,
    brandColorHex: String? = null,
) {
    val extColors = RestaurantsLightTheme.colors
    val availabilityAlpha = if (item.isAvailableAtBranch) 1f else 0.68f
    val brandColor = RestaurantsLightPalette.parseHexColor(brandColorHex, extColors.brandGreen)
    val brandTextColor = RestaurantsLightPalette.getContrastTextColor(brandColor)

    val borderStroke = if (inCartQuantity > 0) {
        BorderStroke(1.3.dp, brandColor.copy(alpha = 0.75f))
    } else {
        BorderStroke(1.dp, Color(0xFFE2E8F0))
    }
    val placeholderTint = when (fallbackIconKey) {
        "juice", "cafe" -> extColors.brandGreen
        "shake", "burger", "pizza" -> Color(0xFFEA580C)
        else -> extColors.brandGreenDeep
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(availabilityAlpha)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = borderStroke,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                    ),
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                        ),
                        color = Color(0xFF64748B),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val hasMetaInfo = !item.isAvailableAtBranch || item.isFasting || item.isVeg ||
                    item.prepTimeMins != null || item.dietaryTags.isNotEmpty() || item.hasRequiredOptions()

                if (hasMetaInfo) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        if (!item.isAvailableAtBranch) {
                            DenseCreamTagPill(
                                label = branchName?.let { "Sold out • $it" } ?: "Sold out",
                                containerColor = extColors.errorSoft,
                                contentColor = extColors.error,
                            )
                        } else {
                            if (item.isFasting) {
                                DenseCreamTagPill(
                                    label = "Fasting",
                                    containerColor = Color(0xFFE8F5E9),
                                    contentColor = Color(0xFF1B5E20),
                                )
                            }
                            if (item.isVeg) {
                                DenseCreamTagPill(
                                    label = "Veg",
                                    containerColor = Color(0xFFECFDF5),
                                    contentColor = Color(0xFF047857),
                                )
                            }
                            if (item.prepTimeMins != null) {
                                DenseCreamTagPill(
                                    label = "${item.prepTimeMins} min",
                                    containerColor = Color(0xFFF1F5F9),
                                    contentColor = Color(0xFF475569),
                                )
                            }
                            item.dietaryTags.take(1).forEach { tag ->
                                DenseCreamTagPill(
                                    label = tag,
                                    containerColor = extColors.tagCreamSurface,
                                    contentColor = extColors.tagCreamText,
                                )
                            }
                            if (item.hasRequiredOptions()) {
                                DenseCreamTagPill(
                                    label = "Options",
                                    containerColor = extColors.mutedSurface,
                                    contentColor = extColors.secondaryText,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = RestaurantsUiReducers.formatEtbMinor(item.effectivePriceMinor),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color(0xFF0F172A),
                    )
                    if (item.priceOverrideMinor != null && item.priceOverrideMinor != item.basePriceMinor) {
                        Text(
                            text = "Branch price",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = extColors.secondaryText,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(90.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 90.dp, height = 82.dp)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    RestaurantArtworkImage(
                        imageUrl = item.imageUrls.firstOrNull(),
                        contentDescription = item.name,
                        fallbackIcon = categoryFallbackIconForKey(fallbackIconKey),
                        aspectRatio = null,
                        cornerRadius = 10.dp,
                        placeholderTint = placeholderTint,
                        modifier = Modifier.fillMaxSize(),
                    )
                    val ribbon = item.promoRibbonText?.takeIf { it.isNotBlank() }
                    if (ribbon != null && item.isAvailableAtBranch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(extColors.promoRibbonStart, extColors.promoRibbonEnd),
                                    ),
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFFFE082),
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = ribbon,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = when {
                        !item.isAvailableAtBranch -> -1
                        inCartQuantity > 0 -> inCartQuantity
                        else -> 0
                    },
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160))) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)))
                    },
                    label = "dish_action_state",
                ) { actionState ->
                    when {
                        actionState < 0 -> {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = extColors.mutedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Sold out",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = extColors.secondaryText,
                                    )
                                }
                            }
                        }

                        actionState > 0 -> {
                            DishQuantityStepperPill(
                                quantity = actionState,
                                onDecrementClick = onQuickDecrementClick,
                                onIncrementClick = onQuickAddClick,
                                brandColor = brandColor,
                            )
                        }

                        else -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onQuickAddClick),
                                shape = RoundedCornerShape(50),
                                color = brandColor,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add ${item.name}",
                                        tint = brandTextColor,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = brandTextColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DenseCreamTagPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DishQuantityStepperPill(
    quantity: Int,
    onDecrementClick: () -> Unit,
    onIncrementClick: () -> Unit,
    brandColor: Color = RestaurantsLightTheme.colors.brandGreen,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        shape = RoundedCornerShape(50),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDecrementClick),
                shape = CircleShape,
                color = brandColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease quantity",
                        tint = RestaurantsLightPalette.getContrastTextColor(brandColor),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            )

            Surface(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onIncrementClick),
                shape = CircleShape,
                color = brandColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase quantity",
                        tint = RestaurantsLightPalette.getContrastTextColor(brandColor),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantMenuScreenPreview() {
    var branchId by remember { mutableStateOf<String?>("prev-br-bole") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    val state = RestaurantsPreviewFixtures.menuState(
        restaurantId = "prev-rest-habesha",
        branchId = branchId,
    ).copy(selectedCategoryId = categoryId)

    RestaurantMenuScreen(
        state = state,
        onBranchSelected = { branchId = it },
        onCategorySelected = { categoryId = it },
        onMenuItemClick = {},
        onBackClick = {},
    )
}
