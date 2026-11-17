package com.adischat.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 1. Expanded Left Sidebar with service labels and badges
            SuperAppExpandedSidebar(
                selectedService = selectedService,
                onSelectService = onSelectService,
                totalUnreadChat = totalUnreadChat,
                onGoHome = onGoHome,
                modifier = Modifier.fillMaxHeight(),
            )

            // 2. Right Pane: Super App Promotional Feed
            SuperAppPromotionalFeed(
                userName = userName,
                onSelectService = onSelectService,
                onOpenSearch = onOpenSearch,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}
