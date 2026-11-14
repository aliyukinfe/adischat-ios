package com.adischat.app.feature.restaurants.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.util.Locale

internal fun resolveCategorySidebarWidth(availableWidth: Dp): Dp = when {
    availableWidth < 360.dp -> 80.dp
    availableWidth >= 600.dp -> 124.dp
    else -> (availableWidth * 0.23f).coerceIn(82.dp, 108.dp)
}

internal fun resolveCategoryFallbackIconKey(
    iconKey: String?,
    categoryName: String,
): String {
    val raw = listOfNotNull(iconKey, categoryName)
        .joinToString(" ")
        .lowercase(Locale.US)
    return when {
        raw.contains("coffee") || raw.contains("hot") || raw.contains("buna") ||
            raw.contains("espresso") || raw.contains("tea") -> "cafe"
        raw.contains("juice") || raw.contains("smoothie") || raw.contains("mocktail") -> "juice"
        raw.contains("shake") || raw.contains("ice") || raw.contains("gelato") ||
            raw.contains("dessert") -> "shake"
        raw.contains("cold") || raw.contains("drink") || raw.contains("beverage") ||
            raw.contains("soda") || raw.contains("honey") || raw.contains("water") -> "drink"
        raw.contains("burger") || raw.contains("grill") -> "burger"
        raw.contains("pizza") -> "pizza"
        raw.contains("sandwich") || raw.contains("wrap") || raw.contains("bakery") ||
            raw.contains("pastry") || raw.contains("croissant") || raw.contains("brunch") ||
            raw.contains("breakfast") -> "bakery"
        raw.contains("tibs") || raw.contains("stew") || raw.contains("wot") ||
            raw.contains("platter") || raw.contains("fasting") || raw.contains("meal") ||
            raw.contains("dish") || raw.contains("traditional") -> "meal"
        else -> "default"
    }
}

internal fun categoryFallbackIconForKey(key: String): ImageVector = when (key) {
    "cafe" -> Icons.Rounded.LocalCafe
    "juice" -> Icons.Rounded.LocalBar
    "shake" -> Icons.Rounded.Icecream
    "drink" -> Icons.Rounded.LocalDrink
    "burger" -> Icons.Rounded.LunchDining
    "pizza" -> Icons.Rounded.LocalPizza
    "bakery" -> Icons.Rounded.BakeryDining
    "meal" -> Icons.Rounded.DinnerDining
    else -> Icons.Rounded.RestaurantMenu
}

/**
 * High-density super-app left category rail inspired by Meituan / Ele.me with AdisChat
 * green active styling, circular icons, optional SALE/HOT metadata badges, and optional
 * Deals / Chef's Pick / Hot Sellers quick-filter spotlights.
 */
@Composable
fun RestaurantCategorySidebar(
    categories: List<MenuCategoryUiModel>,
    activeCategoryId: String?,
    onCategoryClick: (String) -> Unit,
    width: Dp,
    bottomContentPadding: Dp = 24.dp,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    availableSpotlights: Set<MenuSpotlightFilter> = emptySet(),
    activeSpotlight: MenuSpotlightFilter = MenuSpotlightFilter.NONE,
    onSpotlightSelected: ((MenuSpotlightFilter) -> Unit)? = null,
) {
    val extColors = RestaurantsLightTheme.colors

    LaunchedEffect(activeCategoryId, activeSpotlight, categories) {
        if (activeSpotlight != MenuSpotlightFilter.NONE) return@LaunchedEffect
        val targetId = activeCategoryId ?: return@LaunchedEffect
        val targetIndex = categories.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull {
            it.key == "sidebar_$targetId"
        }
        val needsReveal = visibleItem == null ||
            visibleItem.offset < layoutInfo.viewportStartOffset ||
            (visibleItem.offset + visibleItem.size) > layoutInfo.viewportEndOffset
        if (needsReveal) {
            val spotlightCount = if (onSpotlightSelected != null && availableSpotlights.isNotEmpty()) {
                availableSpotlights.size + 1
            } else {
                0
            }
            listState.animateScrollToItem(targetIndex + spotlightCount)
        }
    }

    Row(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(extColors.sidebarBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onSpotlightSelected != null && availableSpotlights.isNotEmpty()) {
                item(key = "spotlight_all_header") {
                    SidebarSpotlightHeaderTile(
                        isActive = activeSpotlight == MenuSpotlightFilter.DEALS,
                        onClick = {
                            onSpotlightSelected(
                                if (activeSpotlight == MenuSpotlightFilter.DEALS) {
                                    MenuSpotlightFilter.NONE
                                } else {
                                    MenuSpotlightFilter.DEALS
                                },
                            )
                        },
                    )
                }
                if (availableSpotlights.contains(MenuSpotlightFilter.CHEFS_PICK)) {
                    item(key = "spotlight_chefs_pick") {
                        SidebarSpotlightTile(
                            title = "Chef's Pick",
                            icon = Icons.Rounded.LocalFireDepartment,
                            iconTint = Color(0xFFFF6B1A),
                            isSelected = activeSpotlight == MenuSpotlightFilter.CHEFS_PICK,
                            onClick = {
                                onSpotlightSelected(
                                    if (activeSpotlight == MenuSpotlightFilter.CHEFS_PICK) {
                                        MenuSpotlightFilter.NONE
                                    } else {
                                        MenuSpotlightFilter.CHEFS_PICK
                                    },
                                )
                            },
                        )
                    }
                }
                if (availableSpotlights.contains(MenuSpotlightFilter.HOT_SELLERS)) {
                    item(key = "spotlight_hot_sellers") {
                        SidebarSpotlightTile(
                            title = "Hot Sellers",
                            icon = Icons.Rounded.AutoAwesome,
                            iconTint = extColors.sidebarBadgeHot,
                            isSelected = activeSpotlight == MenuSpotlightFilter.HOT_SELLERS,
                            showHotFlame = true,
                            onClick = {
                                onSpotlightSelected(
                                    if (activeSpotlight == MenuSpotlightFilter.HOT_SELLERS) {
                                        MenuSpotlightFilter.NONE
                                    } else {
                                        MenuSpotlightFilter.HOT_SELLERS
                                    },
                                )
                            },
                        )
                    }
                }
            }

            itemsIndexed(
                items = categories,
                key = { _, category -> "sidebar_${category.id}" },
            ) { _, category ->
                val isSelected = activeSpotlight == MenuSpotlightFilter.NONE && category.id == activeCategoryId
                SidebarCategoryTile(
                    category = category,
                    isSelected = isSelected,
                    onClick = {
                        onSpotlightSelected?.invoke(MenuSpotlightFilter.NONE)
                        onCategoryClick(category.id)
                    },
                )
            }
        }
        VerticalDivider(
            thickness = 1.dp,
            color = extColors.divider,
        )
    }
}

@Composable
private fun SidebarSpotlightHeaderTile(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (isActive) extColors.trustGoldSurface else Color.Transparent,
        animationSpec = tween(180),
        label = "deals_bg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalOffer,
            contentDescription = null,
            tint = if (isActive) extColors.trustGoldText else extColors.primaryText,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "Deals",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (isActive) extColors.trustGoldText else extColors.primaryText,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarSpotlightTile(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    showHotFlame: Boolean = false,
) {
    val extColors = RestaurantsLightTheme.colors
    val containerStart by animateColorAsState(
        targetValue = if (isSelected) extColors.trustGoldSurface else Color.Transparent,
        animationSpec = tween(180),
        label = "spot_start",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerStart)
            .clickable(onClick = onClick)
            .padding(end = 5.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(if (isSelected) iconTint else Color.Transparent),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = if (isSelected) iconTint else Color.White,
                shadowElevation = 0.5.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else iconTint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) extColors.trustGoldText else extColors.primaryText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showHotFlame) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun SidebarCategoryTile(
    category: MenuCategoryUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val extColors = RestaurantsLightTheme.colors
    val iconKey = resolveCategoryFallbackIconKey(category.iconKey, category.name)
    val fallbackVector = categoryFallbackIconForKey(iconKey)

    val gradientStart by animateColorAsState(
        targetValue = if (isSelected) extColors.sidebarSelectedMintStart else Color.Transparent,
        animationSpec = tween(180),
        label = "cat_start",
    )
    val gradientEnd by animateColorAsState(
        targetValue = if (isSelected) extColors.sidebarSelectedMintEnd else Color.Transparent,
        animationSpec = tween(180),
        label = "cat_end",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) extColors.brandGreenDeep else extColors.secondaryText,
        animationSpec = tween(180),
        label = "cat_label",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(gradientStart, gradientEnd),
                ),
            )
            .clickable(onClick = onClick)
            .padding(end = 5.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(if (isSelected) extColors.brandGreen else Color.Transparent),
        )

        Spacer(modifier = Modifier.width(4.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.padding(top = if (!category.badgeText.isNullOrBlank()) 4.dp else 0.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isSelected) extColors.brandGreen else Color.White,
                    shadowElevation = if (isSelected) 2.dp else 0.5.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .let {
                                if (isSelected) {
                                    it.background(
                                        Brush.linearGradient(
                                            listOf(extColors.brandGreen, Color(0xFF22C55E)),
                                        ),
                                    )
                                } else {
                                    it
                                }
                            },
                    ) {
                        if (!category.imageUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = category.imageUrl,
                                contentDescription = category.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                error = {
                                    Icon(
                                        imageVector = fallbackVector,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else extColors.secondaryText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        } else {
                            Icon(
                                imageVector = fallbackVector,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else extColors.secondaryText.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                val badge = category.badgeText?.takeIf { it.isNotBlank() }
                if (badge != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-5).dp),
                        shape = RoundedCornerShape(50),
                        color = extColors.sidebarBadgeHot,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.5.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 12.5.sp,
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
