package com.adischat.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Red notification badge for unread chat counts on sidebar icons.
 */
@Composable
private fun SidebarUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B30))
            .border(1.2.dp, Color(0xFF0B0D12), CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Expanded compact sidebar for the Home dashboard:
 * Displays the 5 approved services:
 * 1. Home (active on dashboard)
 * 2. Chat (messages, groups & channels)
 * 3. AdisGebeya (marketplace)
 * 4. Restaurants (dine-in & QR)
 * 5. Promotions (vouchers & deals)
 *
 * Width is tuned to 104dp to eliminate clipped text and give the promotional feed
 * maximum horizontal breathing space on mobile displays.
 */
@Composable
fun SuperAppExpandedSidebar(
    selectedService: SuperAppService?,
    onSelectService: (SuperAppService) -> Unit,
    totalUnreadChat: Int = 0,
    onGoHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF10141D)
    val borderColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E2532)
    val textColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)

    // On the Home dashboard, default to HOME being selected
    val activeService = selectedService ?: SuperAppService.HOME

    Surface(
        modifier = modifier
            .width(104.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = bgColor,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGoHome),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = SuperAppService.HOME.accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            SuperAppService.dashboardServices.filter { it != SuperAppService.HOME }.forEach { service ->
                val isSelected = activeService == service
                val itemBg = when {
                    isSelected && isLight -> Color(0xFFE2E8F0)
                    isSelected && !isLight -> Color(0xFF1E2532)
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelectService(service) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = service.title,
                        color = if (isSelected) service.accentColor else textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = if (service == SuperAppService.CHAT && totalUnreadChat > 0) 20.dp else 0.dp),
                    )
                    if (service == SuperAppService.CHAT && totalUnreadChat > 0) {
                        SidebarUnreadBadge(
                            count = totalUnreadChat,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact icon rail shown inside any active service (Chat, AdisGebeya, Restaurants, Promotions):
 * - Collapses to a narrow 52dp rail (strictly 48–56dp) on the left edge.
 * - Top button: Small Home icon returning to the unified dashboard.
 * - Service icons for instant 1-tap switching without returning to Home.
 * - Persistent visible rail maximizing space for the selected service.
 */
@Composable
fun SuperAppCompactSidebar(
    selectedService: SuperAppService?,
    onSelectService: (SuperAppService) -> Unit,
    onExpandHome: () -> Unit,
    totalUnreadChat: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isLight = (selectedService == SuperAppService.ADISGEBEYA || selectedService == SuperAppService.RESTAURANTS) ||
        (MaterialTheme.colorScheme.background.luminance() > 0.5f)
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0F1218)
    val borderColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E2532)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val homeColor = Color(0xFF10B981)

    Surface(
        modifier = modifier
            .width(52.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        color = bgColor,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLight) Color(0xFFE2E8F0) else Color(0xFF191F2B))
                        .clickable(onClick = onExpandHome),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Return to Dashboard",
                        tint = homeColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(borderColor),
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuperAppService.compactRailServices.forEach { service ->
                    val isSelected = selectedService == service
                    val iconTint = if (isSelected) service.accentColor else mutedColor
                    val bgBoxColor = if (isSelected) {
                        service.accentColor.copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgBoxColor)
                            .clickable { onSelectService(service) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isSelected) service.selectedIcon else service.unselectedIcon,
                            contentDescription = service.title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                        if (service == SuperAppService.CHAT && totalUnreadChat > 0) {
                            SidebarUnreadBadge(
                                count = totalUnreadChat,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp),
                            )
                        }
                    }
                }
            }

        }
    }
}
