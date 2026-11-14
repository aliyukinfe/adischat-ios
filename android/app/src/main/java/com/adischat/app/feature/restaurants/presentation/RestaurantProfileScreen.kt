package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Isolated restaurant profile view displaying cover branding, description, branches,
 * opening hours, Browse Menu CTA, and Scan Table QR CTA (Phase R3D offline foundation).
 */
@Composable
fun RestaurantProfileScreen(
    state: RestaurantProfileUiState,
    onBranchSelected: (String) -> Unit,
    onBrowseMenuClick: (restaurantId: String, branchId: String?) -> Unit,
    onScanTableQrClick: (restaurantId: String, branchId: String?) -> Unit,
    onRetryClick: () -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RestaurantsTopBar(
                title = state.restaurant?.brandName ?: "Restaurant Profile",
                subtitle = state.restaurant?.cuisineTag ?: "Dine-in Partner",
                onBackClick = onBackClick,
                trailingContent = {
                    RestaurantTagPill(label = "Pay at Restaurant")
                },
            )
        },
        bottomBar = {
            val restaurant = state.restaurant
            if (state.loadState is RestaurantsLoadState.Content && restaurant != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onBrowseMenuClick(restaurant.id, state.selectedBranch?.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Browse Menu",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Button(
                            onClick = { onScanTableQrClick(restaurant.id, state.selectedBranch?.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan Table QR",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val load = state.loadState) {
                is RestaurantsLoadState.Disabled -> {
                    RestaurantsDisabledNoticeCard(
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                is RestaurantsLoadState.Loading -> {
                    RestaurantsLoadingSkeletonList(
                        cardCount = 2,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                is RestaurantsLoadState.Error -> {
                    RestaurantsStateMessageCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Unable to load profile",
                        message = load.message,
                        actionLabel = "Retry",
                        onActionClick = onRetryClick,
                        isError = true,
                        modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                    )
                }

                is RestaurantsLoadState.Empty,
                is RestaurantsLoadState.Content, -> {
                    val restaurant = state.restaurant
                    if (restaurant == null) {
                        RestaurantsStateMessageCard(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "Restaurant unavailable",
                            message = "This restaurant profile could not be found.",
                            modifier = Modifier.widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = RestaurantsUiDefaults.MaxReadableContentWidth),
                            contentPadding = PaddingValues(
                                start = RestaurantsUiDefaults.ScreenHorizontalPadding,
                                end = RestaurantsUiDefaults.ScreenHorizontalPadding,
                                top = 12.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item {
                                RestaurantProfileHeroCard(restaurant = restaurant)
                            }

                            item {
                                DineInNoticeBanner(
                                    headline = "Dine-In Ordering Only • Pay at Restaurant",
                                    supportingText = "Seated guests scan the table QR to activate an approved dining session and track kitchen status live.",
                                    icon = Icons.Rounded.QrCodeScanner,
                                )
                            }

                            item {
                                Text(
                                    text = "Locations & Hours (${state.branches.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }

                            if (state.branches.isEmpty()) {
                                item {
                                    RestaurantsStateMessageCard(
                                        icon = Icons.Rounded.LocationOn,
                                        title = "No branches published",
                                        message = "Branch details and opening hours are not available yet.",
                                    )
                                }
                            } else {
                                items(
                                    items = state.branches,
                                    key = { it.id },
                                ) { branch ->
                                    BranchDetailCard(
                                        branch = branch,
                                        isSelected = branch.id == state.selectedBranch?.id,
                                        onClick = { onBranchSelected(branch.id) },
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
private fun RestaurantProfileHeroCard(
    restaurant: RestaurantCardUiModel,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            RestaurantArtworkImage(
                imageUrl = restaurant.coverUrl,
                contentDescription = "${restaurant.brandName} banner",
                fallbackLabel = restaurant.cuisineTag,
                aspectRatio = 16f / 9f,
                cornerRadius = RestaurantsUiDefaults.InnerRadius,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestaurantLogoAvatar(
                    logoUrl = restaurant.logoUrl,
                    brandName = restaurant.brandName,
                    size = 50.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = restaurant.brandName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RestaurantTagPill(
                            label = restaurant.cuisineTag,
                            containerColor = RestaurantsUiDefaults.WarmAmberSoft,
                            contentColor = RestaurantsUiDefaults.WarmAmberAccent,
                        )
                        RestaurantTagPill(label = restaurant.isOpenNowLabel)
                    }
                }
            }

            if (restaurant.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = restaurant.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BranchDetailCard(
    branch: RestaurantBranchUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = branch.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${branch.address} • ${branch.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RestaurantTagPill(
                    label = if (branch.isDineInActive) "Dine-in Active" else "Paused",
                    containerColor = if (branch.isDineInActive) {
                        RestaurantsUiDefaults.DineInGreenSoft
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (branch.isDineInActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (branch.openingHours.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Opening Hours",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    branch.openingHours.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = row.dayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.isHighlighted) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = row.hoursLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (row.isHighlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
private fun RestaurantProfileScreenPreview() {
    RestaurantProfileScreen(
        state = RestaurantsPreviewFixtures.profileState(),
        onBranchSelected = {},
        onBrowseMenuClick = { _, _ -> },
        onScanTableQrClick = { _, _ -> },
        onBackClick = {},
    )
}
