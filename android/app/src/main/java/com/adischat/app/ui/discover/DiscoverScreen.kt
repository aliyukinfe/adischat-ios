package com.adischat.app.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adischat.app.ui.explore.ExploreNearbyScreen

/**
 * DiscoverScreen adapter forwarding to the verified GPS-powered ExploreNearbyScreen.
 */
@Composable
fun DiscoverScreen(
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExploreNearbyScreen(
        onGoHome = {},
        onNavigateService = {},
        onOpenUrl = onOpenUrl,
        modifier = modifier,
    )
}


