package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Isolated customer restaurant discovery screen (Phase R3D offline UI foundation).
 * Not wired into production navigation while feature flags remain false.
 */
@Composable
fun RestaurantsDiscoverScreen(
    state: RestaurantsDiscoverUiState,
    onSearchQueryChange: (String) -> Unit,
    onCuisineFilterSelected: (String) -> Unit,
    onRestaurantClick: (RestaurantCardUiModel) -> Unit,
    onRetryClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onScanQrClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    RestaurantsLightTheme {
        val extColors = RestaurantsLightTheme.colors
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = extColors.mainBackground,
            topBar = {
                RestaurantsTopBar(
                    title = "Restaurants",
                    subtitle = "Dine-in menus & table QR",
                    onBackClick = onBackClick,
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RestaurantTagPill(
                                label = "Dine-In",
                                containerColor = extColors.brandGreenSoft,
                                contentColor = extColors.brandGreenDeep,
                            )
                            if (onScanQrClick != null) {
                                IconButton(onClick = onScanQrClick) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = "Scan Table QR",
                                        tint = extColors.brandGreenDeep,
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            RestaurantsDiscoverScaffoldBody(
                state = state,
                onSearchQueryChange = onSearchQueryChange,
                onCuisineFilterSelected = onCuisineFilterSelected,
                onRestaurantClick = onRestaurantClick,
                onRetryClick = onRetryClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun RestaurantsDiscoverScaffoldBody(
    state: RestaurantsDiscoverUiState,
    onSearchQueryChange: (String) -> Unit,
    onCuisineFilterSelected: (String) -> Unit,
    onRestaurantClick: (RestaurantCardUiModel) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
            val columns = if (maxWidth >= 680.dp) 2 else 1

            when (val load = state.loadState) {
                is RestaurantsLoadState.Disabled -> {
                    RestaurantsDisabledNoticeCard(
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                is RestaurantsLoadState.Loading -> {
                    Column(
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    ) {
                        DiscoverSearchAndFilters(
                            searchQuery = state.searchQuery,
                            selectedCuisine = state.selectedCuisineFilter,
                            cuisineFilters = state.availableCuisineFilters,
                            onSearchQueryChange = onSearchQueryChange,
                            onCuisineFilterSelected = onCuisineFilterSelected,
                        )
                        RestaurantsLoadingSkeletonList(cardCount = 3)
                    }
                }

                is RestaurantsLoadState.Error -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Unable to load restaurants",
                        message = load.message,
                        actionLabel = "Try Again",
                        onActionClick = onRetryClick,
                        isError = true,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                is RestaurantsLoadState.Empty,
                is RestaurantsLoadState.Content, -> {
                    val filtered = state.filteredRestaurants
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                            top = 10.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(columns) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DineInNoticeBanner(
                                    headline = "Table Dine-In Ordering",
                                    supportingText = "Explore partner kitchens and menus here. Scan a table QR inside the restaurant to start ordering.",
                                    icon = Icons.Rounded.QrCodeScanner,
                                )
                                DiscoverSearchHeader(
                                    searchQuery = state.searchQuery,
                                    selectedCuisine = state.selectedCuisineFilter,
                                    cuisineFilters = state.availableCuisineFilters,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onCuisineFilterSelected = onCuisineFilterSelected,
                                )
                            }
                        }

                        if (load is RestaurantsLoadState.Empty || filtered.isEmpty()) {
                            item(span = { GridItemSpan(columns) }) {
                                val hasActiveFilter = state.searchQuery.isNotBlank() ||
                                    state.selectedCuisineFilter != RestaurantsDiscoverUiState.ALL_CUISINES
                                RestaurantsStateMessageCard(
                                    icon = Icons.Rounded.Restaurant,
                                    title = if (hasActiveFilter) "No matching restaurants" else "No restaurants listed yet",
                                    message = if (hasActiveFilter) {
                                        "Try clearing your search or switching cuisine filters."
                                    } else {
                                        "Approved dine-in partner restaurants will appear here."
                                    },
                                    actionLabel = if (hasActiveFilter) "Clear Filters" else null,
                                    onActionClick = if (hasActiveFilter) {
                                        {
                                            onSearchQueryChange("")
                                            onCuisineFilterSelected(RestaurantsDiscoverUiState.ALL_CUISINES)
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        } else {
                            items(
                                items = filtered,
                                key = { it.id },
                            ) { restaurant ->
                                RestaurantDiscoverCard(
                                    restaurant = restaurant,
                                    onClick = { onRestaurantClick(restaurant) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun DiscoverSearchAndFilters(
    searchQuery: String,
    selectedCuisine: String,
    cuisineFilters: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onCuisineFilterSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiscoverSearchHeader(
            searchQuery = searchQuery,
            selectedCuisine = selectedCuisine,
            cuisineFilters = cuisineFilters,
            onSearchQueryChange = onSearchQueryChange,
            onCuisineFilterSelected = onCuisineFilterSelected,
        )
    }
}

@Composable
private fun DiscoverSearchHeader(
    searchQuery: String,
    selectedCuisine: String,
    cuisineFilters: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onCuisineFilterSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
            placeholder = {
                Text(
                    text = "Search restaurant name, cuisine, or area...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RestaurantsLightTheme.colors.mainBackground,
                unfocusedContainerColor = RestaurantsLightTheme.colors.sidebarBackground,
                focusedBorderColor = RestaurantsLightTheme.colors.brandGreen,
                unfocusedBorderColor = RestaurantsLightTheme.colors.cardBorder,
            ),
        )

        if (cuisineFilters.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cuisineFilters, key = { it }) { filter ->
                    val selected = filter.equals(selectedCuisine, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { onCuisineFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = RoundedCornerShape(RestaurantsUiDefaults.PillRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RestaurantsLightTheme.colors.brandGreenSoft,
                            selectedLabelColor = RestaurantsLightTheme.colors.brandGreenDeep,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestaurantDiscoverCard(
    restaurant: RestaurantCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extColors = RestaurantsLightTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = extColors.cardSurface,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
            ) {
                RestaurantArtworkImage(
                    imageUrl = restaurant.coverUrl,
                    contentDescription = "${restaurant.brandName} cover",
                    fallbackLabel = restaurant.cuisineTag,
                    aspectRatio = 16f / 9f,
                    cornerRadius = RestaurantsUiDefaults.InnerRadius,
                    modifier = Modifier.fillMaxWidth(),
                )
                RestaurantTagPill(
                    label = restaurant.isOpenNowLabel,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RestaurantLogoAvatar(
                    logoUrl = restaurant.logoUrl,
                    brandName = restaurant.brandName,
                    size = 44.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = restaurant.brandName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        RestaurantTagPill(
                            label = restaurant.cuisineTag,
                            containerColor = RestaurantsLightTheme.colors.sidebarBackground,
                            contentColor = RestaurantsLightTheme.colors.secondaryText,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${restaurant.primaryBranchName} • ${restaurant.city}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = restaurant.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val branchSummary = if (restaurant.branchCount > 1) {
                            "${restaurant.branchCount} dine-in locations"
                        } else {
                            restaurant.primaryAddress
                        }
                        Text(
                            text = branchSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        restaurant.startingPriceMinor?.let { minPrice ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "From ${RestaurantsUiReducers.formatEtbMinor(minPrice)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantsDiscoverScreenPreview() {
    var query by rememberSaveable { mutableStateOf("") }
    var cuisine by rememberSaveable { mutableStateOf(RestaurantsDiscoverUiState.ALL_CUISINES) }
    val state = RestaurantsPreviewFixtures.discoverState().copy(
        searchQuery = query,
        selectedCuisineFilter = cuisine,
    )
    RestaurantsDiscoverScreen(
        state = state,
        onSearchQueryChange = { query = it },
        onCuisineFilterSelected = { cuisine = it },
        onRestaurantClick = {},
    )
}
