package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adischat.app.R

/**
 * Saved item model representing local bookmarked items across services.
 * Purely local fixtures for QA preview — zero backend mutations.
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

private val defaultSavedItems = listOf(
    SavedItem(
        id = "saved-1",
        title = "Habesha Cultural Restaurant",
        subtitle = "Authentic Injera, Kitfo & Cultural Ambience • Bole",
        category = "Restaurants",
        priceOrRating = "4.8 ★ (180+)",
        imageUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=600&q=80",
        targetService = SuperAppService.RESTAURANTS,
        badge = "FAVORITE DINE-IN",
    ),
    SavedItem(
        id = "saved-2",
        title = "Addis Organic Wild Honey (500g)",
        subtitle = "100% Raw Forest Honey • Green Addis Market",
        category = "Products",
        priceOrRating = "ETB 450",
        imageUrl = "https://images.unsplash.com/photo-1587049352846-4a222e784d38?auto=format&fit=crop&w=600&q=80",
        targetService = SuperAppService.ADISGEBEYA,
        badge = "WISHLIST",
    ),
    SavedItem(
        id = "saved-3",
        title = "Welcome Voucher 15% OFF",
        subtitle = "Save up to ETB 350 on first order • Code ADIS15",
        category = "Deals",
        priceOrRating = "15% DISCOUNT",
        imageUrl = "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=600&q=80",
        targetService = SuperAppService.PROMOTIONS,
        badge = "ACTIVE CODE",
    ),
    SavedItem(
        id = "saved-4",
        title = "Traditional Handwoven Netela",
        subtitle = "Pure Ethiopian Cotton & Hand-Embroidered Tibeb",
        category = "Products",
        priceOrRating = "ETB 850",
        imageUrl = "https://images.unsplash.com/photo-1523381210434-271e8be1f52b?auto=format&fit=crop&w=600&q=80",
        targetService = SuperAppService.ADISGEBEYA,
        badge = "WISHLIST",
    ),
    SavedItem(
        id = "saved-5",
        title = "Addis Flame Grill & Steakhouse",
        subtitle = "Prime cuts, burgers & sizzling ribs • Kazanchis",
        category = "Restaurants",
        priceOrRating = "4.7 ★ (95+)",
        imageUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=600&q=80",
        targetService = SuperAppService.RESTAURANTS,
        badge = "RECOMMENDED",
    ),
)

/**
 * SuperAppSavedScreen — Approved bottom tab destination for "Saved".
 * Displays local bookmarked products, favorite restaurants, and vouchers.
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

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Products", "Restaurants", "Deals") }

    val savedItems = remember { mutableStateListOf(*defaultSavedItems.toTypedArray()) }
    val filteredItems = remember(selectedCategory, savedItems.size) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = "Return to Home",
                            modifier = Modifier.size(24.dp),
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
                            text = "Tap logo to return Home",
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

        // 2. QA Notice Banner
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = Color(0xFFFEF3C7).copy(alpha = if (isLight) 0.9f else 0.15f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "QA PREVIEW • Local bookmarks only. Zero backend mutations or charges.",
                    color = if (isLight) Color(0xFF92400E) else Color(0xFFFCD34D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 3. Category Filter Chips
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No saved items in this category.",
                            color = mutedColor,
                            fontSize = 13.sp,
                        )
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
