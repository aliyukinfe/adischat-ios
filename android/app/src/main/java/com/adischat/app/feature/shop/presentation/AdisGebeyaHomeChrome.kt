package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import com.adischat.app.ui.theme.AdisGebeyaHomeV2Colors

internal const val MARKETPLACE_LANGUAGE_ENGLISH = "en"
internal const val MARKETPLACE_LANGUAGE_AMHARIC = "am"

internal fun normalizedMarketplaceLanguage(value: String?): String =
    if (value?.lowercase() == MARKETPLACE_LANGUAGE_AMHARIC) MARKETPLACE_LANGUAGE_AMHARIC else MARKETPLACE_LANGUAGE_ENGLISH

internal fun marketplaceLanguageFlag(value: String?): String =
    if (normalizedMarketplaceLanguage(value) == MARKETPLACE_LANGUAGE_AMHARIC) {
        "\uD83C\uDDEA\uD83C\uDDF9"
    } else {
        "\uD83C\uDDFA\uD83C\uDDF8"
    }

internal fun marketplaceCategorySearch(
    categories: List<ShopCategoryDto>,
    query: String,
    parentId: String?,
): List<ShopCategoryDto> = if (query.isBlank()) {
    categories.filter { it.parentId == parentId }
} else {
    categories.filter { category ->
        category.name.contains(query.trim(), ignoreCase = true) ||
            category.slug.contains(query.trim(), ignoreCase = true)
    }
}

internal data class HomeDiscoveryControl(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdisGebeyaHomeTopBar(
    language: String,
    notificationUnreadCount: Int,
    activeFilterCount: Int,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
    onImageSearch: () -> Unit,
    onScanner: () -> Unit,
    onLanguage: (String) -> Unit,
    onDiscovery: (MarketplaceDiscoverySurface) -> Unit,
) {
    var showLanguages by remember { mutableStateOf(false) }
    Surface(
        color = AdisGebeyaHomeV2Colors.Surface,
        border = BorderStroke(1.dp, AdisGebeyaHomeV2Colors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.adisgebeya_loader_buyer),
                        contentDescription = "AdisGebeya",
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.4f, scaleY = 1.4f),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Adis",
                        color = AdisGebeyaHomeV2Colors.PurpleDark,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Gebeya",
                        color = AdisGebeyaHomeV2Colors.Gold,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                HomeHeaderIcon(
                    icon = Icons.Default.Notifications,
                    description = "Marketplace notifications",
                    badge = notificationUnreadCount,
                    onClick = onNotifications,
                )
                Spacer(Modifier.width(2.dp))
                Surface(
                    modifier = Modifier.size(48.dp).clickable { showLanguages = true },
                    shape = CircleShape,
                    color = AdisGebeyaHomeV2Colors.Surface,
                    border = BorderStroke(1.dp, AdisGebeyaHomeV2Colors.Border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            marketplaceLanguageFlag(language),
                            fontSize = 23.sp,
                            modifier = Modifier.semantics { contentDescription = "Choose marketplace language" },
                        )
                    }
                }
            }
            MarketplaceHomeSearchBar(onSearch, onImageSearch, onScanner)
        }
    }

    if (showLanguages) {
        ModalBottomSheet(
            onDismissRequest = { showLanguages = false },
            containerColor = AdisGebeyaColors.Surface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Language", color = AdisGebeyaColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                LanguageRow("\uD83C\uDDFA\uD83C\uDDF8", "English", normalizedMarketplaceLanguage(language) == MARKETPLACE_LANGUAGE_ENGLISH) {
                    onLanguage(MARKETPLACE_LANGUAGE_ENGLISH)
                    showLanguages = false
                }
                LanguageRow("\uD83C\uDDEA\uD83C\uDDF9", "\u12A0\u121B\u122D\u129B", normalizedMarketplaceLanguage(language) == MARKETPLACE_LANGUAGE_AMHARIC) {
                    onLanguage(MARKETPLACE_LANGUAGE_AMHARIC)
                    showLanguages = false
                }
                Text(
                    "The preference is saved on this device. Translated text appears only where translations are currently available.",
                    color = AdisGebeyaColors.SecondaryText,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun MarketplaceHomeSearchBar(
    onSearch: () -> Unit,
    onImageSearch: () -> Unit,
    onScanner: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AdisGebeyaHomeV2Colors.Surface)
            .border(1.dp, AdisGebeyaHomeV2Colors.Border, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(48.dp).clickable(onClick = onSearch).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = AdisGebeyaHomeV2Colors.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Search products, stores and categories",
                color = AdisGebeyaHomeV2Colors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SearchAction(Icons.Default.CameraAlt, "Search by image", onImageSearch)
        SearchAction(Icons.Default.QrCodeScanner, "Scan product", onScanner)
    }
}

@Composable
private fun SearchAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, tint = AdisGebeyaHomeV2Colors.Purple, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun MarketplaceDiscoveryBar(activeFilterCount: Int, onDiscovery: (MarketplaceDiscoverySurface) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem(0) }
    val controls = listOf(
        HomeDiscoveryControl("Categories", Icons.Default.Category),
        HomeDiscoveryControl("Filter", Icons.Default.FilterList, activeFilterCount > 0),
        HomeDiscoveryControl("Sort", Icons.Default.Sort),
        HomeDiscoveryControl("Price", Icons.Default.Tune),
        HomeDiscoveryControl("Rating", Icons.Default.StarOutline),
    )
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(controls, key = { it.label }) { control ->
            Box(
                modifier = Modifier.height(48.dp).clickable {
                    onDiscovery(
                        when (control.label) {
                            "Categories" -> MarketplaceDiscoverySurface.Categories
                            "Filter" -> MarketplaceDiscoverySurface.Filters
                            "Sort" -> MarketplaceDiscoverySurface.Sort
                            "Price" -> MarketplaceDiscoverySurface.Price
                            "Rating" -> MarketplaceDiscoverySurface.Rating
                            else -> MarketplaceDiscoverySurface.None
                        },
                    )
                }.semantics { contentDescription = control.label },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (control.active) AdisGebeyaColors.PrimaryLight else AdisGebeyaColors.Surface,
                    border = BorderStroke(1.dp, if (control.active) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
                ) {
                    Row(
                        Modifier.height(34.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(control.icon, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(16.dp))
                        Text(control.label, color = AdisGebeyaColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (control.label == "Filter" && activeFilterCount > 0) {
                            Text(activeFilterCount.toString(), color = AdisGebeyaColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderIcon(icon: ImageVector, description: String, badge: Int, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = description, tint = AdisGebeyaHomeV2Colors.PurpleDark, modifier = Modifier.size(22.dp))
        }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(AdisGebeyaColors.Error),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (badge > 9) "9+" else badge.toString(), color = AdisGebeyaColors.OnColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LanguageRow(flag: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AdisGebeyaColors.PrimaryLight else AdisGebeyaColors.Surface,
        border = BorderStroke(1.dp, if (selected) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selected) Text("Selected", color = AdisGebeyaColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageSearchAvailabilitySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AdisGebeyaColors.Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Search with image", color = AdisGebeyaColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Image search is coming soon. No image will be uploaded until secure product-image matching is available.",
                color = AdisGebeyaColors.SecondaryText,
                fontSize = 13.sp,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
            ) {
                Text("Done", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun MarketplaceCategoryFilter(
    categories: List<ShopCategoryDto>,
    selectedSlug: String?,
    onSelect: (ShopCategoryDto?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    val visible = remember(categories, query, parentId) { marketplaceCategorySearch(categories, query, parentId) }
    val currentParent = categories.firstOrNull { it.id == parentId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Category", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSelect(null); parentId = null; query = "" }) {
                Text("All", color = AdisGebeyaColors.Primary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search categories...") },
            shape = RoundedCornerShape(16.dp),
            colors = shopOutlinedTextFieldColors(),
        )
        if (currentParent != null && query.isBlank()) {
            TextButton(onClick = { parentId = currentParent.parentId }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(currentParent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        visible.take(30).forEach { category ->
            val hasChildren = categories.any { it.parentId == category.id }
            val selected = category.slug == selectedSlug
            Surface(
                color = if (selected) AdisGebeyaColors.PrimaryLight else AdisGebeyaColors.Surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (selected) AdisGebeyaColors.Primary else AdisGebeyaColors.Border),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hasChildren && query.isBlank()) parentId = category.id else onSelect(category)
                        }
                        .padding(start = 13.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(category.name, color = AdisGebeyaColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (query.isNotBlank()) {
                            categories.firstOrNull { it.id == category.parentId }?.let { parent ->
                                Text(parent.name, color = AdisGebeyaColors.SecondaryText, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    if (hasChildren && query.isBlank()) {
                        TextButton(onClick = { onSelect(category) }) { Text("Select", fontSize = 11.sp) }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open ${category.name}", tint = AdisGebeyaColors.SecondaryText)
                    } else if (selected) {
                        Text("Selected", color = AdisGebeyaColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            Text("No matching categories.", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp)
        }
    }
}
