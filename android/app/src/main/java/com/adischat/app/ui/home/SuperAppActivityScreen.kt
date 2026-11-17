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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R

/**
 * Activity feed item representing recent transactions, order status, or community alerts.
 * Strictly local fixtures for QA preview — zero backend mutations.
 */
data class ActivityItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val category: String, // "Orders", "Restaurants", "Community", "System"
    val icon: ImageVector,
    val iconTint: Color,
    val targetService: SuperAppService,
    val ctaLabel: String,
)

private val defaultActivityItems = listOf(
    ActivityItem(
        id = "act-1",
        title = "Table Session Active",
        description = "Table 4 check-in recorded at Habesha Cultural Restaurant. Menu viewing ready.",
        timestamp = "10 min ago",
        category = "Restaurants",
        icon = Icons.Default.Restaurant,
        iconTint = Color(0xFFFB923C),
        targetService = SuperAppService.RESTAURANTS,
        ctaLabel = "View Dine-in",
    ),
    ActivityItem(
        id = "act-2",
        title = "Order Dispatched",
        description = "AdisGebeya order #AG-88421 with Addis Organic Honey is on its way to Bole.",
        timestamp = "2 hours ago",
        category = "Orders",
        icon = Icons.Default.LocalShipping,
        iconTint = Color(0xFF34D399),
        targetService = SuperAppService.ADISGEBEYA,
        ctaLabel = "Track Order",
    ),
    ActivityItem(
        id = "act-3",
        title = "Promotional Voucher Ready",
        description = "Welcome voucher 15% OFF (ADIS15) is claimed and ready in your voucher wallet.",
        timestamp = "Yesterday",
        category = "Orders",
        icon = Icons.Default.LocalOffer,
        iconTint = Color(0xFFF87171),
        targetService = SuperAppService.PROMOTIONS,
        ctaLabel = "Use Voucher",
    ),
    ActivityItem(
        id = "act-4",
        title = "Addis Tech Hub Broadcast",
        description = "New community announcement posted: Ethiopian Tech Startups Demo Day 2026.",
        timestamp = "2 days ago",
        category = "Community",
        icon = Icons.Default.Campaign,
        iconTint = Color(0xFFC084FC),
        targetService = SuperAppService.CHANNELS,
        ctaLabel = "Open Channel",
    ),
    ActivityItem(
        id = "act-5",
        title = "Device Session Verified",
        description = "Connected via Samsung Galaxy M33 5G. QA security policies active.",
        timestamp = "3 days ago",
        category = "System",
        icon = Icons.Default.Security,
        iconTint = Color(0xFF60A5FA),
        targetService = SuperAppService.CHAT,
        ctaLabel = "View Session",
    ),
)

/**
 * SuperAppActivityScreen — Approved bottom tab destination for "Activity".
 * Displays recent order updates, restaurant sessions, promotions, and announcements.
 * Tapping the AdisChat logo returns to the unified Home promotional feed.
 */
@Composable
fun SuperAppActivityScreen(
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
    val categories = remember { listOf("All", "Orders", "Restaurants", "Community", "System") }

    val filteredItems = remember(selectedCategory) {
        if (selectedCategory == "All") defaultActivityItems
        else defaultActivityItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
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
                            text = "Activity",
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

                // Recent Count Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(mintAccent.copy(alpha = 0.15f))
                        .border(1.dp, mintAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${defaultActivityItems.size} Updates",
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
                    text = "QA PREVIEW • Activity preview feed. No real orders or transactions.",
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

        // 4. Activity Items List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filteredItems, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Icon Box
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(item.iconTint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.iconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Text Body
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.title,
                                    color = textColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = item.timestamp,
                                    color = mutedColor,
                                    fontSize = 10.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                color = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                            Spacer(Modifier.height(8.dp))

                            // Action CTA
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onNavigateService(item.targetService) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = item.ctaLabel,
                                    color = item.iconTint,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
