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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
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
 * Activity Item for Personal customer timeline (orders, vouchers, sessions, history).
 * Strictly local QA preview fixtures with zero backend mutations.
 */
data class ActivityPersonalItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val category: String, // "Restaurant Orders", "AdisGebeya Orders", "Vouchers", "History"
    val icon: ImageVector,
    val iconTint: Color,
    val targetService: SuperAppService,
    val ctaLabel: String,
)

private val defaultPersonalActivities = listOf(
    ActivityPersonalItem(
        id = "act-1",
        title = "Table Session Active",
        description = "Table 4 check-in recorded at Habesha Cultural Restaurant. Menu viewing ready.",
        timestamp = "10 min ago",
        category = "Restaurant Orders",
        icon = Icons.Default.Restaurant,
        iconTint = Color(0xFFFB923C), // Warm orange
        targetService = SuperAppService.RESTAURANTS,
        ctaLabel = "View Dine-in",
    ),
    ActivityPersonalItem(
        id = "act-2",
        title = "Order Dispatched",
        description = "AdisGebeya order #AG-88421 with Addis Organic Honey is on its way to Bole.",
        timestamp = "2 hours ago",
        category = "AdisGebeya Orders",
        icon = Icons.Default.LocalShipping,
        iconTint = Color(0xFF34D399), // Mint green
        targetService = SuperAppService.ADISGEBEYA,
        ctaLabel = "Track Order",
    ),
    ActivityPersonalItem(
        id = "act-3",
        title = "Promotional Voucher Ready",
        description = "Welcome voucher 15% OFF (ADIS15) is claimed and ready in your voucher wallet.",
        timestamp = "Yesterday",
        category = "Vouchers",
        icon = Icons.Default.LocalOffer,
        iconTint = Color(0xFFF87171), // Coral red
        targetService = SuperAppService.PROMOTIONS,
        ctaLabel = "Use Voucher",
    ),
    ActivityPersonalItem(
        id = "act-4",
        title = "Dine-In Order Completed",
        description = "Bun & Patty Craft Burgers: 2x Truffle Smash + Loaded Fries. Receipt saved.",
        timestamp = "2 days ago",
        category = "History",
        icon = Icons.Default.CheckCircle,
        iconTint = Color(0xFF60A5FA), // Blue
        targetService = SuperAppService.RESTAURANTS,
        ctaLabel = "View Restaurant",
    ),
    ActivityPersonalItem(
        id = "act-5",
        title = "Marketplace Delivery Completed",
        description = "AdisGebeya order #AG-87910 with wireless earbuds delivered successfully.",
        timestamp = "3 days ago",
        category = "History",
        icon = Icons.Default.CheckCircle,
        iconTint = Color(0xFF34D399), // Mint green
        targetService = SuperAppService.ADISGEBEYA,
        ctaLabel = "View Purchases",
    ),
)

/**
 * SuperAppActivityScreen — Approved destination for the "Activity" bottom tab.
 * Organized into:
 * - Personal: Restaurant orders, AdisGebeya purchases, history, and vouchers.
 * - Business: Restaurant Center and AdisGebeya Seller Center (for authorized users).
 */
@Composable
fun SuperAppActivityScreen(
    onGoHome: () -> Unit,
    onNavigateService: (SuperAppService) -> Unit,
    onOpenRestaurantCenter: () -> Unit = {},
    onOpenSellerCenter: () -> Unit = {},
    isRestaurantCenterAuthorized: Boolean = false,
    isSellerAuthorized: Boolean = false,
    sellerStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    val cardBg = if (isLight) Color.White else Color(0xFF12151C)
    val cardBorder = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1F2530)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val mintAccent = Color(0xFF34D399)
    val orangeAccent = Color(0xFFFB923C)
    val goldAccent = Color(0xFFFBBF24)

    // Primary Activity section toggle: Personal vs Business
    var activeSection by remember { mutableStateOf("Personal") }

    // Sub-filters for Personal section
    var selectedPersonalFilter by remember { mutableStateOf("All") }
    val personalFilters = remember { listOf("All", "Restaurant Orders", "AdisGebeya Orders", "Vouchers", "History") }

    val filteredPersonalActivities = remember(selectedPersonalFilter) {
        if (selectedPersonalFilter == "All") defaultPersonalActivities
        else defaultPersonalActivities.filter { it.category.equals(selectedPersonalFilter, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding(),
    ) {
        // 1. Top Bar with Clickable AdisChat Logo returning to Home dashboard
        Surface(
            color = if (isLight) Color.White else Color(0xFF12151C),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                text = "Activity Center",
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

                    // Count Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(mintAccent.copy(alpha = 0.15f))
                            .border(1.dp, mintAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (activeSection == "Personal") "${defaultPersonalActivities.size} Updates" else "2 Centers",
                            color = mintAccent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Segmented Toggle: Personal | Business
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Personal Tab
                    val isPersonal = activeSection == "Personal"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPersonal) (if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E2532)) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isPersonal) mintAccent.copy(alpha = 0.5f) else cardBorder,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { activeSection = "Personal" }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isPersonal) mintAccent else mutedColor,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Personal",
                                fontSize = 12.sp,
                                fontWeight = if (isPersonal) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isPersonal) textColor else mutedColor,
                            )
                        }
                    }

                    // Business Tab
                    val isBusiness = activeSection == "Business"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBusiness) (if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E2532)) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isBusiness) orangeAccent.copy(alpha = 0.5f) else cardBorder,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { activeSection = "Business" }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = if (isBusiness) orangeAccent else mutedColor,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Business",
                                fontSize = 12.sp,
                                fontWeight = if (isBusiness) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isBusiness) textColor else mutedColor,
                            )
                        }
                    }
                }
            }
        }

        // 2. Section Content
        if (activeSection == "Personal") {
            // --- PERSONAL SECTION ---

            // QA Notice Banner
            Surface(
                color = Color(0xFF064E3B).copy(alpha = if (isLight) 0.10f else 0.20f),
                border = BorderStroke(1.dp, mintAccent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = mintAccent,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "PERSONAL ACTIVITY • Orders, dining sessions, and vouchers preview.",
                        color = if (isLight) Color(0xFF047857) else mintAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Sub-category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(personalFilters) { cat ->
                    val isSelected = selectedPersonalFilter == cat
                    val chipBg = if (isSelected) mintAccent else cardBg
                    val chipText = if (isSelected) Color.Black else textColor
                    val chipBorder = if (isSelected) mintAccent else cardBorder

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
                            .clickable { selectedPersonalFilter = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cat,
                            color = chipText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            // Personal Activities List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredPersonalActivities, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(item.iconTint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.title,
                                        color = textColor,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = item.timestamp,
                                        color = mutedColor,
                                        fontSize = 9.5.sp,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = item.description,
                                    color = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNavigateService(item.targetService) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = item.ctaLabel,
                                        color = item.iconTint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = item.iconTint,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- BUSINESS SECTION ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header notice
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF78350F).copy(alpha = if (isLight) 0.10f else 0.20f),
                        border = BorderStroke(1.dp, orangeAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = orangeAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "BUSINESS MANAGEMENT • Merchant operations & store portals.",
                                color = if (isLight) Color(0xFFB45309) else orangeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Card 1: Restaurant Center
                item {
                    val orangeBorder = if (isRestaurantCenterAuthorized) orangeAccent.copy(alpha = 0.4f) else cardBorder
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, orangeBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(orangeAccent.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restaurant,
                                            contentDescription = null,
                                            tint = orangeAccent,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Restaurant Center",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (isRestaurantCenterAuthorized) "Dining Operations & KDS" else "Partner Dining Portal",
                                            color = mutedColor,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isRestaurantCenterAuthorized) orangeAccent.copy(alpha = 0.15f)
                                            else mutedColor.copy(alpha = 0.12f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (isRestaurantCenterAuthorized) "AUTHORIZED" else "PARTNER ROLE REQUIRED",
                                        color = if (isRestaurantCenterAuthorized) orangeAccent else mutedColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isRestaurantCenterAuthorized) {
                                    "Manage restaurant branches, dining menus, live orders, waiter calls, and kitchen display system (KDS)."
                                } else {
                                    "Access requires an active verified Restaurant Owner or Manager role. If your establishment is an AdisChat dining partner, ask your restaurant administrator to invite your account."
                                },
                                color = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                            Spacer(Modifier.height(10.dp))

                            if (isRestaurantCenterAuthorized) {
                                Button(
                                    onClick = onOpenRestaurantCenter,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = orangeAccent,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "Open Restaurant Center",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E2532),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Role verification enforced by backend workspace memberships.",
                                        color = mutedColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 2: AdisGebeya Seller Center
                item {
                    val isPending = sellerStatus in setOf("submitted", "under_review")
                    val goldBorder = if (isSellerAuthorized) goldAccent.copy(alpha = 0.4f) else cardBorder
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, goldBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(goldAccent.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = goldAccent,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "AdisGebeya Seller Center",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (isSellerAuthorized) "Marketplace Merchant Portal" else "Merchant Onboarding",
                                            color = mutedColor,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }

                                val (badgeText, badgeColor) = when {
                                    isSellerAuthorized -> "ACTIVE SELLER" to goldAccent
                                    isPending -> "UNDER REVIEW" to Color(0xFF60A5FA)
                                    else -> "APPLICATION OPEN" to mintAccent
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when {
                                    isSellerAuthorized ->
                                        "Merchant dashboard for marketplace vendors. Monitor store status, product catalog, sales, and payouts."
                                    isPending ->
                                        "Your merchant application has been submitted and is currently being reviewed by the AdisGebeya onboarding team."
                                    else ->
                                        "Sell products on AdisGebeya to local customers. Complete a verified seller application with integrated delivery and payments."
                                },
                                color = if (isLight) Color(0xFF334155) else Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = onOpenSellerCenter,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSellerAuthorized) goldAccent else mintAccent,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = when {
                                            isSellerAuthorized -> "Open Seller Center"
                                            isPending -> "Check Application Status"
                                            else -> "Apply for Seller Account"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.Black,
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
}
