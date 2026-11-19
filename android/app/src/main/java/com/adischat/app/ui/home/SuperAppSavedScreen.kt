package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.adischat.app.R
import com.adischat.app.feature.shop.presentation.ShopViewModel

/**
 * Saved item model representing saved products and items across services.
 */
data class SavedItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String, // "Products", "Restaurants", "Deals"
    val priceOrRating: String,
    val imageUrl: String,
    val targetService: SuperAppService,
    val badge: String? = null,
)

/**
 * SuperAppSavedScreen — Approved bottom tab destination for "Saved".
 * Displays saved products, favorite restaurants, and vouchers.
 * Tapping the AdisChat logo returns to the unified Home promotional feed.
 */
@Composable
fun SuperAppSavedScreen(
    onGoHome: () -> Unit,
    onNavigateService: (SuperAppService) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val cardBg = if (isLight) Color.White else Color(0xFF12151C)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val mintAccent = Color(0xFF34D399)

    val shopVm: ShopViewModel = viewModel()
    val protectedState by shopVm.protectedState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        shopVm.loadFavorites()
    }

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Products", "Restaurants", "Deals") }

    val savedItems = remember(protectedState.favorites) {
        protectedState.favorites.mapNotNull { fav ->
            val p = fav.product ?: return@mapNotNull null
            SavedItem(
                id = fav.id.ifBlank { fav.productId },
                title = p.title,
                subtitle = p.store?.name ?: p.brand ?: "AdisGebeya",
                category = "Products",
                priceOrRating = if (p.priceMinor > 0) "ETB ${p.priceMinor / 100}" else "Free",
                imageUrl = p.images?.firstOrNull()?.imageUrl ?: "",
                targetService = SuperAppService.ADISGEBEYA,
                badge = if ((p.discountPercent ?: 0) > 0) "${p.discountPercent}% OFF" else "SAVED",
            )
        }
    }
    val filteredItems = remember(selectedCategory, savedItems) {
        if (selectedCategory == "All") savedItems
        else savedItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding(),
    ) {
        // 1. Top Bar with Clickable AdisChat Logo returning to Home
        Surface(
            color = if (isLight) Color.White else Color(0xFF12151C),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(onClick = onGoHome),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Return to Home",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Saved Items",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                            ),
                        )
                        Text(
                            text = "Saved for later",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = mutedColor,
                            ),
                        )
                    }
                }

                // Bookmark Count Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(mintAccent.copy(alpha = 0.15f))
                        .border(1.dp, mintAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${savedItems.size} Saved",
                        color = mintAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 2. Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                val chipBg = if (isSelected) mintAccent else cardBg
                val chipText = if (isSelected) Color.Black else textColor
                val chipBorder = if (isSelected) mintAccent else cardBorder

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cat,
                        color = chipText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // 4. Saved Items List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(mintAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = mintAccent,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Text(
                                text = "No saved items yet",
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Items and products you save while browsing will appear here.",
                                color = mutedColor,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onNavigateService(SuperAppService.ADISGEBEYA) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = mintAccent,
                                    contentColor = Color.Black,
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = "Explore AdisGebeya",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2532)),
                            ) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                if (item.badge != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(item.targetService.accentColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = item.badge,
                                            color = item.targetService.accentColor,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                Text(
                                    text = item.title,
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    color = mutedColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.priceOrRating,
                                    color = mintAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // Action button routing to the appropriate service
                            Button(
                                onClick = { onNavigateService(item.targetService) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = item.targetService.accentColor,
                                    contentColor = Color.Black,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(
                                    text = "Open",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
