package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Premium Promotional Feed for the Super App Home screen.
 * Displays curated hero promotions, deals, and service highlights with responsive layouts.
 */
@Composable
fun SuperAppPromotionalFeed(
    userName: String,
    onSelectService: (SuperAppService) -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    promotions: List<SuperAppPromotionItem> = SuperAppDemoPromotions.items,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val cardBg = if (isLight) Color.White else Color(0xFF12151C)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val mintAccent = Color(0xFF34D399)

    var selectedCategory by remember { mutableStateOf("All") }
    // Clean categories reflecting the approved 5 services without duplicate Channels/Seller Center
    val categories = remember { listOf("All", "Restaurants", "AdisGebeya", "Deals") }

    val filteredPromotions = remember(selectedCategory, promotions) {
        if (selectedCategory == "All") promotions
        else promotions.filter {
            it.categoryTag.equals(selectedCategory, ignoreCase = true) ||
                (selectedCategory == "Deals" && (it.originalPriceLabel != null || it.categoryTag == "Promotions"))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. User Header & Welcome
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)) {
                Text(
                    text = "AdisChat Super App",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = mintAccent,
                    ),
                )
                Text(
                    text = "Welcome, ${userName.ifBlank { "Akab" }} \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 2. QA Notice Banner
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF064E3B).copy(alpha = if (isLight) 0.12f else 0.25f),
                border = BorderStroke(1.dp, mintAccent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "QA Info",
                        tint = mintAccent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "QA DEMO FEED • Previewing unified promotional feed. Zero charges.",
                        color = if (isLight) Color(0xFF047857) else mintAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // 3. Search Bar
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSearch),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = mutedColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Search deals, dishes, or stores...",
                        color = mutedColor,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // 4. Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 1.dp),
            ) {
                items(categories) { cat ->
                    val isCatSelected = selectedCategory == cat
                    val chipBg = if (isCatSelected) mintAccent else cardBg
                    val chipText = if (isCatSelected) Color.Black else textColor
                    val chipBorder = if (isCatSelected) mintAccent else cardBorder

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(18.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cat,
                            color = chipText,
                            fontSize = 10.5.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 5. Featured Hero Card
        if (filteredPromotions.isNotEmpty()) {
            val hero = filteredPromotions.first()
            item {
                FeaturedHeroPromoCard(
                    item = hero,
                    onCtaClick = { onSelectService(hero.targetService) },
                    isLight = isLight,
                )
            }
        }

        // 6. Section Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Special Offers & Highlights",
                    color = textColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${filteredPromotions.size} available",
                    color = mutedColor,
                    fontSize = 10.sp,
                )
            }
        }

        // 7. Promotional Feed Cards
        items(filteredPromotions.drop(1), key = { it.id }) { promo ->
            SuperAppPromoCard(
                item = promo,
                onCtaClick = { onSelectService(promo.targetService) },
                isLight = isLight,
            )
        }
    }
}

/**
 * Top featured hero promotion card with gradient overlay and responsive typography.
 */
@Composable
private fun FeaturedHeroPromoCard(
    item: SuperAppPromotionItem,
    onCtaClick: () -> Unit,
    isLight: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, item.accentColor.copy(alpha = 0.35f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCtaClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.90f),
                            ),
                        ),
                    ),
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Tag Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(item.accentColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = item.highlightBadge,
                            color = Color.Black,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = item.categoryTag,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Bottom Titles & Action
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Button(
                            onClick = onCtaClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = item.accentColor,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text(
                                text = item.ctaLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        if (item.promoPriceLabel != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.promoPriceLabel,
                                    color = Color(0xFF34D399),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (item.originalPriceLabel != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = item.originalPriceLabel,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        textDecoration = TextDecoration.LineThrough,
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

/**
 * Standard promotional feed card with compact layout to prevent text clipping.
 */
@Composable
private fun SuperAppPromoCard(
    item: SuperAppPromotionItem,
    onCtaClick: () -> Unit,
    isLight: Boolean,
) {
    val cardBg = if (isLight) Color.White else Color(0xFF12151C)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCtaClick),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left Image Thumbnail
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(bottomEnd = 6.dp))
                        .background(item.accentColor)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.highlightBadge,
                        color = Color.Black,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Right Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    color = mutedColor,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.promoPriceLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.promoPriceLabel,
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (item.originalPriceLabel != null) {
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = item.originalPriceLabel,
                                    color = mutedColor,
                                    fontSize = 9.sp,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = item.validityNote,
                            color = mutedColor,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.clickable(onClick = onCtaClick),
                    ) {
                        Text(
                            text = item.ctaLabel,
                            color = item.accentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = item.ctaLabel,
                            tint = item.accentColor,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
    }
}
