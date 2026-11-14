package com.adischat.app.feature.restaurants.presentation

import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.data.RestaurantsEnvironment
import java.util.Locale
import kotlin.math.max

/**
 * Unified UI load state for Restaurants customer discovery, profile, and menu views.
 */
sealed interface RestaurantsLoadState {
    data object Loading : RestaurantsLoadState
    data object Content : RestaurantsLoadState
    data object Empty : RestaurantsLoadState
    data class Error(val message: String) : RestaurantsLoadState
    data object Disabled : RestaurantsLoadState
}

data class OpeningHourRowUiModel(
    val dayLabel: String,
    val hoursLabel: String,
    val isHighlighted: Boolean = false,
)

data class RestaurantBranchUiModel(
    val id: String,
    val restaurantId: String,
    val code: String,
    val name: String,
    val address: String,
    val city: String,
    val openingHours: List<OpeningHourRowUiModel> = emptyList(),
    val requireStaffSessionActivation: Boolean = true,
    val requireSessionCode: Boolean = false,
    val isDineInActive: Boolean = true,
)

data class RestaurantCardUiModel(
    val id: String,
    val slug: String,
    val brandName: String,
    val cuisineTag: String,
    val city: String,
    val primaryBranchName: String,
    val primaryAddress: String,
    val description: String,
    val logoUrl: String? = null,
    val coverUrl: String? = null,
    val isOpenNowLabel: String = "Dine-in Open",
    val branchCount: Int = 1,
    val isLiveOrderingEnabled: Boolean = false,
    val startingPriceMinor: Long? = null,
    val highlightBadge: String? = null,
    val isHighlightBadgeDemo: Boolean = false,
    val ratingLabel: String? = null,
)

enum class PromotionTone {
    GOLD,
    CORAL_RED,
    CREAM,
}

data class RestaurantPromotionUiModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tone: PromotionTone = PromotionTone.GOLD,
    val isDemoFixture: Boolean = false,
)

enum class MenuSpotlightFilter(val label: String, val tagKey: String) {
    NONE("All", ""),
    DEALS("Deals", "deals"),
    CHEFS_PICK("Chef's Pick", "chefs_pick"),
    HOT_SELLERS("Hot Sellers", "hot_sellers"),
}

data class MenuCategoryUiModel(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val imageUrl: String? = null,
    val iconKey: String? = null,
    val badgeText: String? = null,
)

data class MenuOptionUiModel(
    val id: String,
    val groupId: String,
    val name: String,
    val priceDeltaMinor: Long = 0L,
    val isAvailable: Boolean = true,
)

data class MenuOptionGroupUiModel(
    val id: String,
    val name: String,
    val isRequired: Boolean = false,
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val sortOrder: Int = 0,
    val options: List<MenuOptionUiModel> = emptyList(),
) {
    fun effectiveMinSelect(): Int =
        max(minSelect.coerceAtLeast(0), if (isRequired) 1 else 0)

    fun effectiveMaxSelect(): Int {
        val floor = effectiveMinSelect().coerceAtLeast(1)
        return max(maxSelect, floor)
    }

    fun constraintLabel(): String {
        val min = effectiveMinSelect()
        val max = effectiveMaxSelect()
        return when {
            min >= 1 && max == 1 -> "Required • Choose 1"
            min >= 1 -> "Required • Choose $min–$max"
            max == 1 -> "Optional • Up to 1"
            else -> "Optional • Up to $max"
        }
    }
}

data class MenuItemUiModel(
    val id: String,
    val restaurantId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val basePriceMinor: Long,
    val priceOverrideMinor: Long? = null,
    val isAvailableAtBranch: Boolean = true,
    val imageUrls: List<String> = emptyList(),
    val optionGroups: List<MenuOptionGroupUiModel> = emptyList(),
    val dietaryTags: List<String> = emptyList(),
    val promoRibbonText: String? = null,
    val spotlightTags: List<String> = emptyList(),
) {
    val effectivePriceMinor: Long
        get() = priceOverrideMinor ?: basePriceMinor

    fun hasRequiredOptions(): Boolean =
        optionGroups.any { it.effectiveMinSelect() > 0 }

    fun optionsPreviewSummary(): String {
        if (optionGroups.isEmpty()) return "Standard preparation • No custom options"
        val requiredNames = optionGroups.filter { it.effectiveMinSelect() > 0 }.map { it.name }
        return if (requiredNames.isNotEmpty()) {
            "${optionGroups.size} option groups • Required: ${requiredNames.joinToString(", ")}"
        } else {
            "${optionGroups.size} optional customization group${if (optionGroups.size == 1) "" else "s"}"
        }
    }
}

data class RestaurantsDiscoverUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Content,
    val searchQuery: String = "",
    val selectedCuisineFilter: String = ALL_CUISINES,
    val availableCuisineFilters: List<String> = listOf(ALL_CUISINES),
    val allRestaurants: List<RestaurantCardUiModel> = emptyList(),
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
) {
    val filteredRestaurants: List<RestaurantCardUiModel>
        get() = RestaurantsUiReducers.filterRestaurants(
            restaurants = allRestaurants,
            query = searchQuery,
            cuisineFilter = selectedCuisineFilter,
        )

    companion object {
        const val ALL_CUISINES = "All"
    }
}

data class RestaurantProfileUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Content,
    val restaurant: RestaurantCardUiModel? = null,
    val branches: List<RestaurantBranchUiModel> = emptyList(),
    val selectedBranchId: String? = null,
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
) {
    val selectedBranch: RestaurantBranchUiModel?
        get() = RestaurantsUiReducers.resolveSelectedBranch(branches, selectedBranchId)
}

data class RestaurantMenuUiState(
    val loadState: RestaurantsLoadState = RestaurantsLoadState.Content,
    val restaurantName: String = "",
    val restaurantBrandTagline: String = "",
    val branches: List<RestaurantBranchUiModel> = emptyList(),
    val selectedBranchId: String? = null,
    val categories: List<MenuCategoryUiModel> = emptyList(),
    val selectedCategoryId: String? = null,
    val items: List<MenuItemUiModel> = emptyList(),
    val promotions: List<RestaurantPromotionUiModel> = emptyList(),
    val isBrowseOnly: Boolean = true,
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
) {
    val selectedBranch: RestaurantBranchUiModel?
        get() = RestaurantsUiReducers.resolveSelectedBranch(branches, selectedBranchId)

    val visibleItems: List<MenuItemUiModel>
        get() = RestaurantsUiReducers.filterMenuItems(items, selectedCategoryId)
}

/**
 * Interactive preview state for [RestaurantMenuItemSheet].
 * Supports required/optional option-group constraints and quantity preview
 * without exposing any Add-to-Cart mutation in Phase R3D.
 */
data class MenuItemSheetState(
    val item: MenuItemUiModel,
    val selectedOptionsByGroup: Map<String, Set<String>> = emptyMap(),
    val quantityPreview: Int = 1,
    val isBrowseOnly: Boolean = true,
) {
    fun toggleOption(groupId: String, optionId: String): MenuItemSheetState {
        val group = item.optionGroups.firstOrNull { it.id == groupId } ?: return this
        val option = group.options.firstOrNull { it.id == optionId } ?: return this
        if (!option.isAvailable) return this

        val current = selectedOptionsByGroup[groupId].orEmpty()
        val maxSelect = group.effectiveMaxSelect()
        val minSelect = group.effectiveMinSelect()

        val updatedSet = if (maxSelect == 1) {
            if (current.contains(optionId) && minSelect == 0) {
                emptySet()
            } else {
                setOf(optionId)
            }
        } else {
            if (current.contains(optionId)) {
                if (current.size > minSelect) {
                    current - optionId
                } else {
                    current
                }
            } else if (current.size < maxSelect) {
                current + optionId
            } else {
                current
            }
        }

        return copy(selectedOptionsByGroup = selectedOptionsByGroup + (groupId to updatedSet))
    }

    fun incrementQuantity(): MenuItemSheetState =
        copy(quantityPreview = (quantityPreview + 1).coerceAtMost(MAX_PREVIEW_QUANTITY))

    fun decrementQuantity(): MenuItemSheetState =
        copy(quantityPreview = (quantityPreview - 1).coerceAtLeast(1))

    fun missingRequiredGroups(): List<MenuOptionGroupUiModel> =
        item.optionGroups.filter { group ->
            val selectedCount = selectedOptionsByGroup[group.id]?.size ?: 0
            selectedCount < group.effectiveMinSelect()
        }

    fun isSelectionValid(): Boolean =
        item.isAvailableAtBranch && item.optionGroups.all { group ->
            val selectedIds = selectedOptionsByGroup[group.id].orEmpty()
            val selectedCount = selectedIds.size
            val withinBounds = selectedCount >= group.effectiveMinSelect() &&
                selectedCount <= group.effectiveMaxSelect()
            val allAvailable = selectedIds.all { id ->
                group.options.any { it.id == id && it.isAvailable }
            }
            withinBounds && allAvailable
        }

    fun optionsDeltaMinor(): Long =
        item.optionGroups.sumOf { group ->
            val selectedIds = selectedOptionsByGroup[group.id].orEmpty()
            group.options
                .filter { selectedIds.contains(it.id) }
                .sumOf { it.priceDeltaMinor }
        }

    fun previewUnitTotalMinor(): Long =
        (item.effectivePriceMinor + optionsDeltaMinor()).coerceAtLeast(0L)

    fun previewLineTotalMinor(): Long =
        previewUnitTotalMinor() * quantityPreview.coerceAtLeast(1)

    companion object {
        const val MAX_PREVIEW_QUANTITY = 20

        fun initialFor(item: MenuItemUiModel): MenuItemSheetState {
            val defaults = item.optionGroups.associate { group ->
                val min = group.effectiveMinSelect()
                val available = group.options.filter { it.isAvailable }
                val initialIds = if (min > 0) {
                    available.take(min).map { it.id }.toSet()
                } else {
                    emptySet()
                }
                group.id to initialIds
            }
            return MenuItemSheetState(
                item = item,
                selectedOptionsByGroup = defaults,
                quantityPreview = 1,
                isBrowseOnly = true,
            )
        }
    }
}

object RestaurantsUiReducers {
    fun filterRestaurants(
        restaurants: List<RestaurantCardUiModel>,
        query: String,
        cuisineFilter: String,
    ): List<RestaurantCardUiModel> {
        val cleanQuery = query.trim().lowercase(Locale.US)
        val cleanFilter = cuisineFilter.trim()
        return restaurants.filter { restaurant ->
            val matchesCuisine = cleanFilter.isEmpty() ||
                cleanFilter.equals(RestaurantsDiscoverUiState.ALL_CUISINES, ignoreCase = true) ||
                restaurant.cuisineTag.equals(cleanFilter, ignoreCase = true)

            val matchesQuery = cleanQuery.isEmpty() ||
                restaurant.brandName.lowercase(Locale.US).contains(cleanQuery) ||
                restaurant.cuisineTag.lowercase(Locale.US).contains(cleanQuery) ||
                restaurant.city.lowercase(Locale.US).contains(cleanQuery) ||
                restaurant.primaryAddress.lowercase(Locale.US).contains(cleanQuery) ||
                restaurant.description.lowercase(Locale.US).contains(cleanQuery)

            matchesCuisine && matchesQuery
        }
    }

    fun resolveSelectedBranch(
        branches: List<RestaurantBranchUiModel>,
        selectedBranchId: String?,
    ): RestaurantBranchUiModel? {
        if (branches.isEmpty()) return null
        return branches.firstOrNull { it.id == selectedBranchId }
            ?: branches.firstOrNull { it.isDineInActive }
            ?: branches.first()
    }

    fun filterMenuItems(
        items: List<MenuItemUiModel>,
        selectedCategoryId: String?,
    ): List<MenuItemUiModel> {
        if (selectedCategoryId.isNullOrBlank()) return items
        return items.filter { it.categoryId == selectedCategoryId }
    }

    fun availableSpotlights(items: List<MenuItemUiModel>): Set<MenuSpotlightFilter> = buildSet {
        if (items.any { it.spotlightTags.contains(MenuSpotlightFilter.DEALS.tagKey) || !it.promoRibbonText.isNullOrBlank() }) {
            add(MenuSpotlightFilter.DEALS)
        }
        if (items.any { it.spotlightTags.contains(MenuSpotlightFilter.CHEFS_PICK.tagKey) }) {
            add(MenuSpotlightFilter.CHEFS_PICK)
        }
        if (items.any { it.spotlightTags.contains(MenuSpotlightFilter.HOT_SELLERS.tagKey) }) {
            add(MenuSpotlightFilter.HOT_SELLERS)
        }
    }

    fun filterMenuItemsForDisplay(
        items: List<MenuItemUiModel>,
        searchQuery: String = "",
        spotlightFilter: MenuSpotlightFilter = MenuSpotlightFilter.NONE,
    ): List<MenuItemUiModel> {
        val cleanQuery = searchQuery.trim().lowercase(Locale.US)
        return items.filter { item ->
            val matchesSpotlight = when (spotlightFilter) {
                MenuSpotlightFilter.NONE -> true
                MenuSpotlightFilter.DEALS -> item.spotlightTags.contains(spotlightFilter.tagKey) || !item.promoRibbonText.isNullOrBlank()
                MenuSpotlightFilter.CHEFS_PICK,
                MenuSpotlightFilter.HOT_SELLERS, -> item.spotlightTags.contains(spotlightFilter.tagKey)
            }
            val matchesQuery = cleanQuery.isEmpty() ||
                item.name.lowercase(Locale.US).contains(cleanQuery) ||
                item.description.lowercase(Locale.US).contains(cleanQuery) ||
                item.dietaryTags.any { it.lowercase(Locale.US).contains(cleanQuery) } ||
                (item.promoRibbonText?.lowercase(Locale.US)?.contains(cleanQuery) == true)
            matchesSpotlight && matchesQuery
        }
    }

    /**
     * Builds the discover UI state explicitly separating offline preview fixtures from
     * live results. Never substitutes fake restaurants when a live fetch fails.
     */
    fun buildDiscoverStateFromLiveResult(
        environment: RestaurantsEnvironment,
        liveResult: Result<List<RestaurantCardUiModel>>?,
    ): RestaurantsDiscoverUiState {
        if (!environment.isDineInEnabled) {
            return RestaurantsDiscoverUiState(
                loadState = RestaurantsLoadState.Disabled,
                allRestaurants = emptyList(),
                isDineInEnabled = false,
            )
        }
        if (liveResult == null) {
            return RestaurantsDiscoverUiState(
                loadState = RestaurantsLoadState.Loading,
                allRestaurants = emptyList(),
                isDineInEnabled = true,
            )
        }
        return liveResult.fold(
            onSuccess = { rows ->
                val cuisines = listOf(RestaurantsDiscoverUiState.ALL_CUISINES) +
                    rows.map { it.cuisineTag }.filter { it.isNotBlank() }.distinct()
                RestaurantsDiscoverUiState(
                    loadState = if (rows.isEmpty()) RestaurantsLoadState.Empty else RestaurantsLoadState.Content,
                    availableCuisineFilters = cuisines,
                    allRestaurants = rows,
                    isDineInEnabled = true,
                )
            },
            onFailure = { error ->
                RestaurantsDiscoverUiState(
                    loadState = RestaurantsLoadState.Error(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: "Unable to load restaurants right now.",
                    ),
                    allRestaurants = emptyList(),
                    isDineInEnabled = true,
                )
            },
        )
    }

    fun formatEtbMinor(amountMinor: Long): String {
        val absMinor = kotlin.math.abs(amountMinor)
        val whole = absMinor / 100
        val cents = absMinor % 100
        val sign = if (amountMinor < 0) "-" else ""
        return if (cents == 0L) {
            "${sign}ETB $whole"
        } else {
            String.format(Locale.US, "%sETB %d.%02d", sign, whole, cents)
        }
    }

    fun formatPriceDeltaMinor(deltaMinor: Long): String {
        if (deltaMinor == 0L) return "Included"
        val prefix = if (deltaMinor > 0) "+" else "-"
        return "$prefix${formatEtbMinor(kotlin.math.abs(deltaMinor))}"
    }
}

sealed interface MenuSectionEntry {
    val key: String
    val categoryId: String

    data class CategoryHeader(
        val category: MenuCategoryUiModel,
        val itemCount: Int,
    ) : MenuSectionEntry {
        override val key: String = "cat_${category.id}"
        override val categoryId: String = category.id
    }

    data class FoodItem(
        val item: MenuItemUiModel,
        override val categoryId: String,
    ) : MenuSectionEntry {
        override val key: String = "item_${item.id}"
    }
}

/**
 * Pure flattened index map for continuous category-grouped menu lists and
 * bidirectional left-sidebar synchronization. Skips empty categories safely
 * while preserving backend sort order for both categories and dishes.
 */
data class MenuSectionIndexMap(
    val categories: List<MenuCategoryUiModel> = emptyList(),
    val entries: List<MenuSectionEntry> = emptyList(),
    val categoryStartIndices: Map<String, Int> = emptyMap(),
    val itemStartIndices: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = entries.isEmpty()

    fun categoryIdForIndex(index: Int): String? =
        entries.getOrNull(index)?.categoryId

    fun startIndexForCategory(categoryId: String): Int? =
        categoryStartIndices[categoryId]

    fun indexForItem(itemId: String): Int? =
        itemStartIndices[itemId]

    companion object {
        fun build(
            categories: List<MenuCategoryUiModel>,
            items: List<MenuItemUiModel>,
        ): MenuSectionIndexMap {
            if (items.isEmpty()) return MenuSectionIndexMap()

            val sortedCategories = categories.sortedBy { it.sortOrder }
            val itemsByCategory = items.groupBy { it.categoryId }

            val visibleCategories = mutableListOf<MenuCategoryUiModel>()
            val entries = mutableListOf<MenuSectionEntry>()
            val categoryStartIndices = LinkedHashMap<String, Int>()
            val itemStartIndices = LinkedHashMap<String, Int>()

            for (category in sortedCategories) {
                val categoryItems = itemsByCategory[category.id].orEmpty()
                if (categoryItems.isEmpty()) continue
                visibleCategories += category
                categoryStartIndices[category.id] = entries.size
                entries += MenuSectionEntry.CategoryHeader(
                    category = category,
                    itemCount = categoryItems.size,
                )
                for (dish in categoryItems) {
                    itemStartIndices[dish.id] = entries.size
                    entries += MenuSectionEntry.FoodItem(
                        item = dish,
                        categoryId = category.id,
                    )
                }
            }

            val mappedIds = visibleCategories.mapTo(HashSet()) { it.id }
            val uncategorized = items.filter { !mappedIds.contains(it.categoryId) }
            if (uncategorized.isNotEmpty()) {
                val fallbackCategory = MenuCategoryUiModel(
                    id = "cat-uncategorized",
                    name = "More Dishes",
                    sortOrder = Int.MAX_VALUE,
                )
                visibleCategories += fallbackCategory
                categoryStartIndices[fallbackCategory.id] = entries.size
                entries += MenuSectionEntry.CategoryHeader(
                    category = fallbackCategory,
                    itemCount = uncategorized.size,
                )
                for (dish in uncategorized) {
                    itemStartIndices[dish.id] = entries.size
                    entries += MenuSectionEntry.FoodItem(
                        item = dish,
                        categoryId = fallbackCategory.id,
                    )
                }
            }

            return MenuSectionIndexMap(
                categories = visibleCategories,
                entries = entries,
                categoryStartIndices = categoryStartIndices,
                itemStartIndices = itemStartIndices,
            )
        }
    }
}
