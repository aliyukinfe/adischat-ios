package com.adischat.app.feature.restaurants.presentation

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern, mobile-first customer restaurant discovery screen featuring:
 * - Soft off-white background (#F5F6F8)
 * - Pure-white layered cards with subtle borders (#E2E8F0)
 * - 9 swipeable cuisine category chips with distinct icons & states
 * - 16:9 high-res cover photos with floating brand logo avatars
 * - Verified badges, genuine star ratings, and location summaries
 * - Brand-accented "View Menu" action button with contrast colors
 * - QA Demo Preview banner when running in demo/offline preview mode.
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
    onRegisterRestaurantClick: (() -> Unit)? = null,
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
                onScanQrClick = onScanQrClick,
                onRegisterRestaurantClick = onRegisterRestaurantClick,
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
    onScanQrClick: (() -> Unit)? = null,
    onRegisterRestaurantClick: (() -> Unit)? = null,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.isDemoPreview) {
                        DemoPreviewAlertBanner(
                            modifier = Modifier.padding(
                                horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding,
                                vertical = 4.dp,
                            ),
                        )
                    }
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
            is RestaurantsLoadState.Content -> {
                val filtered = state.filteredRestaurants
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                        end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                        top = 10.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(columns) }) {
                        com.adischat.app.ui.home.AdaptiveFlowAround(besideStart = 60.dp, belowStart = 0.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (state.isDemoPreview) {
                                    DemoPreviewAlertBanner()
                                }
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
                    }

                    if (load is RestaurantsLoadState.Empty || filtered.isEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            val hasActiveFilter = state.searchQuery.isNotBlank() ||
                                state.selectedCuisineFilter != RestaurantsDiscoverUiState.ALL_CUISINES
                            if (hasActiveFilter) {
                                RestaurantsStateMessageCard(
                                    icon = Icons.Rounded.Restaurant,
                                    title = "No matching restaurants",
                                    message = "Try clearing your search or switching cuisine filters.",
                                    actionLabel = "Clear Filters",
                                    onActionClick = {
                                        onSearchQueryChange("")
                                        onCuisineFilterSelected(RestaurantsDiscoverUiState.ALL_CUISINES)
                                    },
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = extColors.cardBackground,
                                    border = BorderStroke(1.dp, extColors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(extColors.brandGreenSoft),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RestaurantMenu,
                                                contentDescription = null,
                                                tint = extColors.brandGreenDeep,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                        Text(
                                            text = "Welcome to AdisChat Restaurants",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = extColors.primaryText,
                                        )
                                        Text(
                                            text = "Explore partner dining menus, order straight from your table, or partner with AdisChat to list your restaurant.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extColors.secondaryText,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            if (onScanQrClick != null) {
                                                Button(
                                                    onClick = onScanQrClick,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp),
                                                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = extColors.brandGreen,
                                                        contentColor = Color.White,
                                                    ),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.QrCodeScanner,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Scan Table QR",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                            }
                                            if (onRegisterRestaurantClick != null) {
                                                OutlinedButton(
                                                    onClick = onRegisterRestaurantClick,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp),
                                                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                                                    border = BorderStroke(1.dp, extColors.brandGreen),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = extColors.brandGreenDeep,
                                                    ),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Storefront,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Register Restaurant",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = filtered,
                            key = { it.id },
                        ) { restaurant ->
                            com.adischat.app.ui.home.AdaptiveFlowAround(besideStart = 60.dp, belowStart = 0.dp) {
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
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 6.dp),
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

private fun resolveCuisineCategoryIcon(categoryName: String): ImageVector {
    return when (categoryName.trim().lowercase()) {
        "all", "all restaurants" -> Icons.Rounded.Restaurant
        "traditional", "ethiopian" -> Icons.Rounded.SoupKitchen
        "burgers", "burger" -> Icons.Rounded.LunchDining
        "chicken" -> Icons.Rounded.Egg
        "café", "cafe", "coffee" -> Icons.Rounded.LocalCafe
        "pizza" -> Icons.Rounded.LocalPizza
        "hotels", "hotel" -> Icons.Rounded.Hotel
        "fine dining" -> Icons.Rounded.DinnerDining
        "fast food" -> Icons.Rounded.Fastfood
        else -> Icons.Rounded.Restaurant
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
    val extColors = RestaurantsLightTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    text = "Search restaurant name, cuisine, or area...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = extColors.brandGreen,
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedTextColor = Color(0xFF0F172A),
                unfocusedTextColor = Color(0xFF0F172A),
            ),
        )

        if (cuisineFilters.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cuisineFilters, key = { it }) { filter ->
                    val selected = filter.equals(selectedCuisine, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onCuisineFilterSelected(filter) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color(0xFFE8F5E9) else Color.White,
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 1.dp,
                            color = if (selected) Color(0xFF4CAF50) else Color(0xFFE2E8F0),
                        ),
                        shadowElevation = if (selected) 0.5.dp else 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = resolveCuisineCategoryIcon(filter),
                                contentDescription = null,
                                tint = if (selected) Color(0xFF1B5E20) else Color(0xFF64748B),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                ),
                                color = if (selected) Color(0xFF1B5E20) else Color(0xFF334155),
                            )
                        }
                    }
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
    val brandColor = RestaurantsLightPalette.parseHexColor(
        hex = restaurant.brandColorHex,
        fallback = extColors.brandGreen,
    )
    val brandButtonTextColor = RestaurantsLightPalette.getContrastTextColor(brandColor)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
    ) {
        Column {
            // 1. Cover artwork box with top badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
            ) {
                RestaurantArtworkImage(
                    imageUrl = restaurant.coverUrl,
                    contentDescription = "${restaurant.brandName} cover",
                    fallbackLabel = restaurant.cuisineTag,
                    aspectRatio = null,
                    cornerRadius = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                )

                // Top Start: Cuisine tag pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = resolveCuisineCategoryIcon(restaurant.cuisineTag),
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = restaurant.cuisineTag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color(0xFF0F172A),
                        )
                    }
                }

                // Top End: Dine-In Open pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(0.8.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF22C55E), CircleShape),
                        )
                        Text(
                            text = restaurant.isOpenNowLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(0xFF15803D),
                        )
                    }
                }
            }

            // 2. Overlapping Brand Logo Avatar + Trust Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .offset(y = (-24).dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RestaurantLogoAvatar(
                    logoUrl = restaurant.logoUrl,
                    brandName = restaurant.brandName,
                    size = 52.dp,
                    brandColor = brandColor,
                    borderWidth = 3.dp,
                    borderColor = Color.White,
                    cornerRadius = 14.dp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (restaurant.isVerified) {
                        RestaurantVerifiedBadge(showLabel = true)
                    }
                    if (restaurant.rating != null) {
                        RestaurantRatingPill(
                            rating = restaurant.rating,
                            reviewCount = restaurant.reviewCount,
                        )
                    }
                }
            }

            // 3. Main Restaurant Details (adjusted for avatar offset)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .offset(y = (-14).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = restaurant.brandName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "${restaurant.primaryBranchName} • ${restaurant.city}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (restaurant.description.isNotBlank()) {
                    Text(
                        text = restaurant.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = Color(0xFF475569),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (restaurant.featuredDishes.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        restaurant.featuredDishes.take(3).forEach { dish ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.6.dp, Color(0xFFE2E8F0)),
                            ) {
                                Text(
                                    text = dish,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color(0xFFF1F5F9),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                // 4. Bottom Action Row: Pricing + View Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        val priceLabel = restaurant.startingPriceMinor?.let {
                            "From ${RestaurantsUiReducers.formatEtbMinor(it)}"
                        } ?: (restaurant.priceRangeLabel ?: "Dine-in menu")
                        Text(
                            text = priceLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(0xFF0F172A),
                        )
                        if (restaurant.branchCount > 1) {
                            Text(
                                text = "${restaurant.branchCount} dine-in branches",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandColor,
                            contentColor = brandButtonTextColor,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "View Menu",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                ),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
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
