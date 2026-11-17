package com.adischat.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R

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
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B30))
            .border(1.5.dp, Color(0xFF0B0D12), CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Text chip badge (e.g. "HOT", "NEW", "DEALS") for service items.
 */
@Composable
private fun SidebarTextBadge(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.2f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Expanded sidebar for Home state:
 * Shows vertical list of the 6 services (Chat, AdisGebeya, Restaurants, Channels, Seller Center, Promotions)
 * with labels, subtitle descriptions, and unread/status badges.
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
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

    Surface(
        modifier = modifier
            .width(130.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = bgColor,
        tonalElevation = 1.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 7.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Header with AdisChat logo (clickable to return to Home)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onGoHome)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "AdisChat Home",
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "SERVICES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    ),
                    color = mutedColor,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34D399)),
                )
            }

            Spacer(Modifier.height(2.dp))

            SuperAppService.values().forEach { service ->
                val isSelected = selectedService == service
                val itemBg = when {
                    isSelected && isLight -> Color(0xFFE2E8F0)
                    isSelected && !isLight -> Color(0xFF1E2532)
                    else -> Color.Transparent
                }
                val iconTint = if (isSelected) service.accentColor else mutedColor

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(itemBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelectService(service)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) service.accentColor.copy(alpha = 0.22f)
                                else if (isLight) Color.White else Color(0xFF161B26)
                            ),
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
                                    .offset(x = 4.dp, y = (-4).dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = service.title,
                                color = if (isSelected) service.accentColor else textColor,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!service.badgeLabel.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            SidebarTextBadge(
                                label = service.badgeLabel,
                                accentColor = service.accentColor,
                            )
                        } else {
                            Text(
                                text = service.subtitle,
                                color = mutedColor,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact icon rail shown when a service is active:
 * Collapses to a slim ~54dp vertical column on the left edge with icon-only buttons
 * and an active pill indicator. Allows rapid switching between services and expanding back to Home.
 */
@Composable
fun SuperAppCompactSidebar(
    selectedService: SuperAppService?,
    onSelectService: (SuperAppService) -> Unit,
    onExpandHome: () -> Unit,
    totalUnreadChat: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bgColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0F1218)
    val borderColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E2532)
    val mutedColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

    Surface(
        modifier = modifier
            .width(54.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = bgColor,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Home Expand shortcut button (AdisChat Logo)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLight) Color(0xFFE2E8F0) else Color(0xFF191F2B))
                    .clickable(onClick = onExpandHome),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "AdisChat Logo - Return to Home",
                    modifier = Modifier.size(24.dp),
                )
            }

            // Services vertical stack
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuperAppService.values().forEach { service ->
                    val isSelected = selectedService == service
                    val iconTint = if (isSelected) service.accentColor else mutedColor
                    val bgBoxColor = if (isSelected) {
                        service.accentColor.copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgBoxColor)
                            .clickable { onSelectService(service) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isSelected) service.selectedIcon else service.unselectedIcon,
                            contentDescription = service.title,
                            tint = iconTint,
                            modifier = Modifier.size(21.dp),
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

            // Bottom collapse indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(mutedColor.copy(alpha = 0.5f)),
            )
        }
    }
}
