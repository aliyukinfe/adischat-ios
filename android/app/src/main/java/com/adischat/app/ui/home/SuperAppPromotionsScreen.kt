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

/**
 * Dedicated Promotions screen in the Super App:
 * Displays active vouchers, promotional campaigns, and service discounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAppPromotionsScreen(
    onBack: () -> Unit,
    onSelectService: (SuperAppService) -> Unit,
    sidebarBounds: Rect? = null,
    modifier: Modifier = Modifier,
    promotions: List<SuperAppPromotionItem> = SuperAppDemoPromotions.items,
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

    val filtered = remember(selectedCategory, promotions) {
        if (selectedCategory == "All") promotions
        else promotions.filter { it.categoryTag.equals(selectedCategory, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(start = 52.dp),
                title = {
                    Column {
                        Text(
                            text = "Promotions & Vouchers",
                            color = textColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Demo concepts, not live offers",
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
            // QA Notice banner
            item {
                FlowAroundRow(sidebarBounds, 70.dp, besideStart = 62.dp) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7).copy(alpha = if (isLight) 0.9f else 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "QA",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "QA DEMO PROMOTIONS • Simulated promotional offers for layout verification.",
                            color = if (isLight) Color(0xFF92400E) else Color(0xFFFCD34D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                }
            }

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
