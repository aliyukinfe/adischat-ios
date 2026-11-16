package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopCategoryAttributeDto
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.ui.theme.AdisGebeyaColors

internal sealed interface MarketplaceDiscoverySurface {
    data object None : MarketplaceDiscoverySurface
    data object Categories : MarketplaceDiscoverySurface
    data object Filters : MarketplaceDiscoverySurface
    data object Sort : MarketplaceDiscoverySurface
    data object Price : MarketplaceDiscoverySurface
    data object Rating : MarketplaceDiscoverySurface
}

internal enum class MarketplaceSort(val label: String, val backendOrder: String) {
    Recommended("Recommended", "created_at.desc"),
    Newest("Newest", "created_at.desc"),
    PriceAscending("Price: Low to High", "price_minor.asc"),
    PriceDescending("Price: High to Low", "price_minor.desc"),
    RatingDescending("Rating: High to Low", "rating_average.desc.nullslast"),
}

internal fun marketplaceBackendOrder(label: String): String =
    MarketplaceSort.entries.firstOrNull { it.label == label }?.backendOrder ?: MarketplaceSort.Newest.backendOrder

internal data class MarketplaceDiscoveryChip(val key: String, val label: String)

internal fun marketplaceDiscoveryChips(
    filters: ShopHomeFilters,
    categories: List<ShopCategoryDto>,
): List<MarketplaceDiscoveryChip> = buildList {
    filters.categorySlug?.let { slug ->
        add(MarketplaceDiscoveryChip("category", categories.firstOrNull { it.slug == slug }?.name ?: slug))
    }
    filters.minimumPriceEtb.toIntOrNull()?.let { minimum ->
        val maximum = filters.maximumPriceEtb.toIntOrNull()
        add(MarketplaceDiscoveryChip("price", if (maximum != null) "ETB $minimum-$maximum" else "From ETB $minimum"))
    } ?: filters.maximumPriceEtb.toIntOrNull()?.let { maximum ->
        add(MarketplaceDiscoveryChip("price", "Up to ETB $maximum"))
    }
    if (filters.minimumRating > 0) add(MarketplaceDiscoveryChip("rating", "${filters.minimumRating} stars & above"))
    if (filters.inStockOnly) add(MarketplaceDiscoveryChip("stock", "In stock"))
    if (filters.discountedOnly) add(MarketplaceDiscoveryChip("discount", "Discounted"))
    filters.categoryValues.forEach { (code, value) -> add(MarketplaceDiscoveryChip("attribute:$code", value)) }
    if (filters.sort != MarketplaceSort.Newest.label) add(MarketplaceDiscoveryChip("sort", filters.sort))
}

internal fun ShopHomeFilters.withoutConstraint(key: String): ShopHomeFilters = when {
    key == "category" -> copy(categorySlug = null, categoryValues = emptyMap())
    key == "price" -> copy(minimumPriceEtb = "", maximumPriceEtb = "")
    key == "rating" -> copy(minimumRating = 0)
    key == "stock" -> copy(inStockOnly = false)
    key == "discount" -> copy(discountedOnly = false)
    key == "sort" -> copy(sort = MarketplaceSort.Newest.label)
    key.startsWith("attribute:") -> copy(categoryValues = categoryValues - key.substringAfter(':'))
    else -> this
}

internal fun ShopHomeFilters.withCategory(categorySlug: String?): ShopHomeFilters =
    copy(categorySlug = categorySlug, categoryValues = emptyMap())

@Composable
internal fun MarketplaceActiveFilterChips(
    filters: ShopHomeFilters,
    categories: List<ShopCategoryDto>,
    onChange: (ShopHomeFilters) -> Unit,
) {
    val chips = marketplaceDiscoveryChips(filters, categories)
    if (chips.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        items(chips, key = { it.key }) { chip ->
            Surface(
                modifier = Modifier.clickable { onChange(filters.withoutConstraint(chip.key)) },
                color = AdisGebeyaColors.PrimaryLight,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, AdisGebeyaColors.Primary.copy(alpha = .28f)),
            ) {
                Row(
                    Modifier.padding(start = 11.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(chip.label, color = AdisGebeyaColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.Close, contentDescription = "Remove ${chip.label}", tint = AdisGebeyaColors.Primary, modifier = Modifier.height(15.dp))
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.clickable { onChange(ShopHomeFilters()) },
                color = Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            ) {
                Text("Clear all", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MarketplaceDiscoverySurfaceHost(
    surface: MarketplaceDiscoverySurface,
    filters: ShopHomeFilters,
    categories: List<ShopCategoryDto>,
    attributes: List<ShopCategoryAttributeDto>,
    products: List<ShopProductDto>,
    productDetails: Map<String, ShopProductDto>,
    loadingAttributes: Boolean,
    onDismiss: () -> Unit,
    onApply: (ShopHomeFilters) -> Unit,
) {
    when (surface) {
        MarketplaceDiscoverySurface.None -> Unit
        MarketplaceDiscoverySurface.Categories -> CategoryBrowserSheet(filters, categories, onDismiss, onApply)
        MarketplaceDiscoverySurface.Filters -> AdvancedFiltersSheet(filters, attributes, products, productDetails, loadingAttributes, onDismiss, onApply)
        MarketplaceDiscoverySurface.Sort -> SortSheet(filters, onDismiss, onApply)
        MarketplaceDiscoverySurface.Price -> PriceSheet(filters, onDismiss, onApply)
        MarketplaceDiscoverySurface.Rating -> RatingSheet(filters, onDismiss, onApply)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CategoryBrowserSheet(
    filters: ShopHomeFilters,
    categories: List<ShopCategoryDto>,
    onDismiss: () -> Unit,
    onApply: (ShopHomeFilters) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeading("Categories", "Browse the marketplace category hierarchy")
            MarketplaceCategoryFilter(categories, filters.categorySlug) { category ->
                onApply(filters.withCategory(category?.slug))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AdvancedFiltersSheet(
    filters: ShopHomeFilters,
    attributes: List<ShopCategoryAttributeDto>,
    products: List<ShopProductDto>,
    productDetails: Map<String, ShopProductDto>,
    loadingAttributes: Boolean,
    onDismiss: () -> Unit,
    onApply: (ShopHomeFilters) -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeading("Advanced filters", "Narrow products using supported catalog information")
            Text("Availability", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("In stock", draft.inStockOnly) { draft = draft.copy(inStockOnly = !draft.inStockOnly) }
                FilterPill("Discounted", draft.discountedOnly) { draft = draft.copy(discountedOnly = !draft.discountedOnly) }
            }
            when {
                filters.categorySlug == null -> Text("Choose a category to see product-specific filters.", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
                loadingAttributes -> Text("Loading category filters...", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
                else -> attributes.filter { it.filterable }.forEach { attribute ->
                    val values = buyerFilterValues(attribute.code, products, productDetails)
                    if (values.isNotEmpty()) {
                        Text(attribute.displayName.ifBlank { attribute.name }, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(values, key = { it }) { value ->
                                FilterPill(value, draft.categoryValues[attribute.code] == value) {
                                    val selected = draft.categoryValues.toMutableMap()
                                    if (selected[attribute.code] == value) selected.remove(attribute.code) else selected[attribute.code] = value
                                    draft = draft.copy(categoryValues = selected)
                                }
                            }
                        }
                    }
                }
            }
            SheetActions(
                resetLabel = "Reset filters",
                onReset = { onApply(filters.copy(categoryValues = emptyMap(), inStockOnly = false, discountedOnly = false)) },
                applyLabel = "Show results",
                onApply = { onApply(draft) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SortSheet(filters: ShopHomeFilters, onDismiss: () -> Unit, onApply: (ShopHomeFilters) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SheetHeading("Sort by", "Choose one ordering")
            MarketplaceSort.entries.forEach { option ->
                SelectionRow(option.label, filters.sort == option.label) { onApply(filters.copy(sort = option.label)) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PriceSheet(filters: ShopHomeFilters, onDismiss: () -> Unit, onApply: (ShopHomeFilters) -> Unit) {
    var minimum by remember(filters.minimumPriceEtb) { mutableStateOf(filters.minimumPriceEtb) }
    var maximum by remember(filters.maximumPriceEtb) { mutableStateOf(filters.maximumPriceEtb) }
    val valid = maximum.toLongOrNull() == null || minimum.toLongOrNull() == null || maximum.toLong() >= minimum.toLong()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetHeading("Price range", "Prices are in Ethiopian birr")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(minimum, { minimum = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Minimum") }, prefix = { Text("ETB ") }, singleLine = true)
                OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Maximum") }, prefix = { Text("ETB ") }, singleLine = true)
            }
            if (!valid) Text("Maximum price must be greater than or equal to minimum price.", color = AdisGebeyaColors.Error, fontSize = 12.sp)
            SheetActions(
                onReset = { onApply(filters.copy(minimumPriceEtb = "", maximumPriceEtb = "")) },
                applyLabel = "Apply",
                applyEnabled = valid,
                onApply = { onApply(filters.copy(minimumPriceEtb = minimum, maximumPriceEtb = maximum)) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RatingSheet(filters: ShopHomeFilters, onDismiss: () -> Unit, onApply: (ShopHomeFilters) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SheetHeading("Customer rating", "Based only on real review aggregates")
            listOf(0 to "Any rating", 4 to "4 stars & above", 3 to "3 stars & above", 2 to "2 stars & above", 1 to "1 star & above").forEach { option ->
                SelectionRow(option.second, filters.minimumRating == option.first, showStar = option.first > 0) {
                    onApply(filters.copy(minimumRating = option.first))
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = AdisGebeyaColors.PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
    }
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, showStar: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (selected) AdisGebeyaColors.Primary else AdisGebeyaColors.SecondaryText)
        Text(label, color = AdisGebeyaColors.PrimaryText, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (showStar) Icon(Icons.Default.Star, contentDescription = null, tint = AdisGebeyaColors.AccentOrange)
    }
}

@Composable
private fun SheetActions(
    resetLabel: String = "Reset",
    onReset: () -> Unit,
    applyLabel: String,
    applyEnabled: Boolean = true,
    onApply: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(50.dp)) { Text(resetLabel, color = AdisGebeyaColors.PrimaryText) }
        Button(
            onClick = onApply,
            enabled = applyEnabled,
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
        ) { Text(applyLabel, fontWeight = FontWeight.SemiBold) }
    }
}
