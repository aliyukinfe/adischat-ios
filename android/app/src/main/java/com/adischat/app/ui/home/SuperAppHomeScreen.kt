package com.adischat.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Super App Home screen layout featuring:
 * - Left: Expanded modern sidebar containing Chat, AdisGebeya, Restaurants, Channels, Seller Center, Promotions
 * - Right: Curated Super App promotional feed with deals, hero banners, and quick shortcuts.
 *
 * (Unified promotional feed is the default opening screen; tapping the AdisChat logo returns to Home).
 */
@Composable
fun SuperAppHomeScreen(
    userName: String,
    selectedService: SuperAppService?,
    onSelectService: (SuperAppService) -> Unit,
    totalUnreadChat: Int = 0,
    onOpenSearch: () -> Unit = {},
    onGoHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bg = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    var sidebarBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
    ) {
        SuperAppPromotionalFeed(
            userName = userName,
            onSelectService = onSelectService,
            onOpenSearch = onOpenSearch,
            sidebarBounds = sidebarBounds,
            modifier = Modifier.fillMaxSize(),
        )
        SuperAppExpandedSidebar(
            selectedService = selectedService,
            onSelectService = onSelectService,
            totalUnreadChat = totalUnreadChat,
            onGoHome = onGoHome,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val next = Rect(position.x, position.y, position.x + coordinates.size.width, position.y + coordinates.size.height)
                if (sidebarBounds != next) sidebarBounds = next
            },
        )
    }
}
