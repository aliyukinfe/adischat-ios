package com.adischat.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Material 3 bottom sheet for inspecting dish photos, descriptions, option groups,
 * required/optional constraints, and live quantity total previews.
 * Strictly browse-only in Phase R3D (no Add to Cart mutation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMenuItemSheet(
    state: MenuItemSheetState,
    onToggleOption: (groupId: String, optionId: String) -> Unit,
    onIncrementQuantity: () -> Unit,
    onDecrementQuantity: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        RestaurantMenuItemSheetContent(
            state = state,
            onToggleOption = onToggleOption,
            onIncrementQuantity = onIncrementQuantity,
            onDecrementQuantity = onDecrementQuantity,
        )
    }
}

@Composable
fun RestaurantMenuItemSheetContent(
    state: MenuItemSheetState,
    onToggleOption: (groupId: String, optionId: String) -> Unit,
    onIncrementQuantity: () -> Unit,
    onDecrementQuantity: () -> Unit,
    modifier: Modifier = Modifier,
    showBrowseOnlyNotice: Boolean = state.isBrowseOnly,
) {
    val item = state.item
    val sortedGroups = remember(item.optionGroups) {
        item.optionGroups.sortedBy { it.sortOrder }
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .verticalScroll(scrollState)
                .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.imageUrls.size <= 1) {
                RestaurantArtworkImage(
                    imageUrl = item.imageUrls.firstOrNull(),
                    contentDescription = item.name,
                    fallbackLabel = item.name,
                    aspectRatio = 16f / 9f,
                    cornerRadius = RestaurantsUiDefaults.CardRadius,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(item.imageUrls, key = { it }) { url ->
                        RestaurantArtworkImage(
                            imageUrl = url,
                            contentDescription = item.name,
                            aspectRatio = 16f / 9f,
                            cornerRadius = RestaurantsUiDefaults.InnerRadius,
                            modifier = Modifier.width(260.dp),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestaurantTagPill(
                    label = if (item.isAvailableAtBranch) "Available for Dine-In" else "Unavailable at Branch",
                    containerColor = if (item.isAvailableAtBranch) {
                        RestaurantsUiDefaults.DineInGreenSoft
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    },
                    contentColor = if (item.isAvailableAtBranch) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                item.dietaryTags.take(2).forEach { tag ->
                    RestaurantTagPill(
                        label = tag,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(item.effectivePriceMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            sortedGroups.forEach { group ->
                val selectedIds = state.selectedOptionsByGroup[group.id].orEmpty()
                OptionGroupCard(
                    group = group,
                    selectedOptionIds = selectedIds,
                    onToggleOption = { optionId -> onToggleOption(group.id, optionId) },
                )
            }

            QuantityAndPricePreviewCard(
                quantity = state.quantityPreview,
                unitTotalMinor = state.previewUnitTotalMinor(),
                lineTotalMinor = state.previewLineTotalMinor(),
                missingRequiredGroups = state.missingRequiredGroups(),
                onIncrementQuantity = onIncrementQuantity,
                onDecrementQuantity = onDecrementQuantity,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        if (showBrowseOnlyNotice) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            DineInNoticeBanner(
                headline = "Browse-Only Preview",
                supportingText = "Add to Cart is disabled during browse mode. Scan a table QR at the restaurant to begin an order.",
                icon = Icons.Rounded.Info,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun OptionGroupCard(
    group: MenuOptionGroupUiModel,
    selectedOptionIds: Set<String>,
    onToggleOption: (String) -> Unit,
) {
    val isSingleChoice = group.effectiveMaxSelect() == 1
    val extColors = RestaurantsLightTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = extColors.sidebarBackground,
        border = BorderStroke(1.dp, extColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.primaryText,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                RestaurantTagPill(
                    label = group.constraintLabel(),
                    containerColor = if (group.effectiveMinSelect() > 0) {
                        extColors.qaNoticeBackground
                    } else {
                        extColors.mutedSurface
                    },
                    contentColor = if (group.effectiveMinSelect() > 0) {
                        extColors.qaNoticeText
                    } else {
                        extColors.secondaryText
                    },
                )
            }

            group.options.forEach { option ->
                val isSelected = selectedOptionIds.contains(option.id)
                val icon = when {
                    isSingleChoice && isSelected -> Icons.Rounded.CheckCircle
                    isSingleChoice -> Icons.Rounded.RadioButtonUnchecked
                    isSelected -> Icons.Rounded.CheckBox
                    else -> Icons.Rounded.CheckBoxOutlineBlank
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (option.isAvailable) 1f else 0.5f)
                        .clickable(enabled = option.isAvailable) { onToggleOption(option.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        extColors.brandGreenSoft
                    } else {
                        extColors.cardSurface
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            extColors.brandGreen
                        } else {
                            extColors.cardBorder
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (option.isAvailable) option.name else "${option.name} (Unavailable)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = RestaurantsUiReducers.formatPriceDeltaMinor(option.priceDeltaMinor),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (option.priceDeltaMinor > 0) {
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

@Composable
private fun QuantityAndPricePreviewCard(
    quantity: Int,
    unitTotalMinor: Long,
    lineTotalMinor: Long,
    missingRequiredGroups: List<MenuOptionGroupUiModel>,
    onIncrementQuantity: () -> Unit,
    onDecrementQuantity: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Quantity Preview",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${RestaurantsUiReducers.formatEtbMinor(unitTotalMinor)} each with options",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrementQuantity,
                        enabled = quantity > 1,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease quantity preview",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FilledTonalIconButton(
                        onClick = onIncrementQuantity,
                        enabled = quantity < MenuItemSheetState.MAX_PREVIEW_QUANTITY,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase quantity preview",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Estimated Dine-In Preview",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = RestaurantsUiReducers.formatEtbMinor(lineTotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (missingRequiredGroups.isNotEmpty()) {
                Text(
                    text = "Select required: ${missingRequiredGroups.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RestaurantsUiDefaults.WarmAmberAccent,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RestaurantMenuItemSheetContentPreview() {
    val sampleItem = RestaurantsPreviewFixtures.menuItemsFor(
        restaurantId = "prev-rest-habesha",
        branchId = "prev-br-bole",
    ).first()
    var sheetState by remember { mutableStateOf(MenuItemSheetState.initialFor(sampleItem)) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        RestaurantMenuItemSheetContent(
            state = sheetState,
            onToggleOption = { groupId, optionId ->
                sheetState = sheetState.toggleOption(groupId, optionId)
            },
            onIncrementQuantity = { sheetState = sheetState.incrementQuantity() },
            onDecrementQuantity = { sheetState = sheetState.decrementQuantity() },
        )
    }
}
