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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

/**
 * Dedicated Promotions screen in the Super App:
 * Displays active vouchers, promotional campaigns, and service discounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAppPromotionsScreen(
    onBack: () -> Unit,
    onSelectService: (SuperAppService) -> Unit,
    sidebarBounds: Rect? = LocalSidebarBounds.current,
    modifier: Modifier = Modifier,
    promotions: List<SuperAppPromotionItem> = emptyList(),
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val cardBg = if (isLight) Color.White else Color(0xFF12151C)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val coralAccent = Color(0xFFF87171)

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All", "Restaurants", "AdisGebeya", "Chat", "Promotions") }

    var livePromotions by remember { mutableStateOf(promotions) }

    LaunchedEffect(promotions) {
        if (promotions.isNotEmpty()) {
            livePromotions = promotions
        } else {
            try {
                val coordinator = RealHomeFeedCoordinator()
                val feedState = coordinator.loadFeed()
                val realCards = feedState.items.filter { it.isPromoted }.mapNotNull { card ->
                    when (card) {
                        is RealFeedCard.ProductCard -> {
                            val prod = card.product
                            val priceStr = "ETB ${String.format(Locale.US, "%.2f", prod.priceMinor / 100.0)}"
                            val originalStr = prod.compareAtPriceMinor?.let {
                                "ETB ${String.format(Locale.US, "%.2f", it / 100.0)}"
                            }
                            val badge = card.discountPercent?.let { "-$it%" } ?: "OFFER"
                            val primaryImg = card.customBannerUrl
                                ?: prod.images?.firstOrNull { it.isPrimary == true }?.imageUrl
                                ?: prod.images?.firstOrNull()?.imageUrl.orEmpty()
                            SuperAppPromotionItem(
                                id = card.id,
                                title = card.customTitle ?: prod.title,
                                subtitle = card.customSubtitle ?: (card.storeName ?: "AdisGebeya Verified Seller"),
                                highlightBadge = badge,
                                categoryTag = "AdisGebeya",
                                imageUrl = primaryImg,
                                targetService = SuperAppService.ADISGEBEYA,
                                ctaLabel = "View Item",
                                accentColor = Color(0xFF34D399),
                                originalPriceLabel = originalStr,
                                promoPriceLabel = priceStr,
                                validityNote = "Verified active promotion",
                            )
                        }
                        is RealFeedCard.RestaurantCard -> {
                            val rest = card.restaurant
                            SuperAppPromotionItem(
                                id = card.id,
                                title = card.customTitle ?: rest.name,
                                subtitle = card.customSubtitle ?: (rest.cuisineCategory ?: "Dine-in menu"),
                                highlightBadge = "FEATURED",
                                categoryTag = "Restaurants",
                                imageUrl = card.customBannerUrl ?: rest.logoPath.orEmpty(),
                                targetService = SuperAppService.RESTAURANTS,
                                ctaLabel = "View Menu",
                                accentColor = Color(0xFFFB923C),
                                validityNote = "Verified partner highlight",
                            )
                        }
                        is RealFeedCard.ChannelCard -> {
                            val room = card.room
                            SuperAppPromotionItem(
                                id = card.id,
                                title = room.name,
                                subtitle = card.snippet ?: "Join community channel",
                                highlightBadge = "OFFICIAL",
                                categoryTag = "Chat",
                                imageUrl = room.photoUrl,
                                targetService = SuperAppService.CHAT,
                                ctaLabel = "Join Channel",
                                accentColor = Color(0xFF60A5FA),
                                validityNote = "Verified community channel",
                            )
                        }
                    }
                }
                livePromotions = realCards
            } catch (_: Exception) {
                livePromotions = emptyList()
            }
        }
    }

    val activeList = if (promotions.isNotEmpty()) promotions else livePromotions
    val filtered = remember(selectedCategory, activeList) {
        if (selectedCategory == "All") activeList
        else activeList.filter { it.categoryTag.equals(selectedCategory, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(start = if (sidebarBounds != null) 60.dp else 0.dp),
                title = {
                    Column {
                        Text(
                            text = "Promotions & Vouchers",
                            color = textColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Verified offers & partner discounts",
                            color = mutedColor,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Categories
            item {
                FlowAroundRow(sidebarBounds, 48.dp, besideStart = 62.dp) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories) { cat ->
                        val isCatSelected = selectedCategory == cat
                        val chipBg = if (isCatSelected) coralAccent else cardBg
                        val chipText = if (isCatSelected) Color.White else textColor
                        val chipBorder = if (isCatSelected) coralAccent else cardBorder

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(20.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cat,
                                color = chipText,
                                fontSize = 11.5.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    FlowAroundRow(sidebarBounds, 182.dp, besideStart = 62.dp) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(176.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = "Promotions",
                                    tint = coralAccent,
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No Active Promotions",
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Check back soon for seasonal offers, discounts, and exclusive vouchers from AdisGebeya and local partners.",
                                    color = mutedColor,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                // Items list
                items(filtered, key = { it.id }) { item ->
                FlowAroundRow(sidebarBounds, 182.dp, besideStart = 62.dp) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clickable { onSelectService(item.targetService) },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            ) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = item.categoryTag,
                                        color = mutedColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    text = item.title,
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.subtitle,
                                    color = mutedColor,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (item.originalPriceLabel != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = item.originalPriceLabel,
                                            color = mutedColor,
                                            fontSize = 10.5.sp,
                                            textDecoration = TextDecoration.LineThrough,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = item.validityNote,
                                    color = mutedColor,
                                    fontSize = 9.5.sp,
                                )
                            }

                            Button(
                                onClick = { onSelectService(item.targetService) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = item.accentColor,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text(
                                    text = item.ctaLabel,
                                    fontSize = 11.sp,
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
    }
}
