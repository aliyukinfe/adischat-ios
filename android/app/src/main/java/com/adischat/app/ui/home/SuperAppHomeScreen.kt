package com.adischat.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Super App Home screen layout featuring:
 * - Left: Expanded modern sidebar containing Chat, AdisGebeya, Restaurants, Channels, Seller Center, Promotions
 * - Right: Curated Super App promotional feed with deals, hero banners, and quick shortcuts.
 *
 * (Unified promotional feed is the default opening screen; tapping the AdisChat logo returns to Home).
 */
@Composable
fun SuperAppHomeScreen(
    userUid: String?,
    selectedService: SuperAppService?,
    onSelectService: (SuperAppService) -> Unit,
    onOpenSearchResult: (HomeSearchHit) -> Unit,
    totalUnreadChat: Int = 0,
    onGoHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bg = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0B0D12)
    var sidebarBounds by remember { mutableStateOf<Rect?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val feedListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val closeSearch = {
        query = ""
        searchOpen = false
        focusManager.clearFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
    ) {
        CompositionLocalProvider(LocalSidebarBounds provides sidebarBounds) {
            if (searchOpen) {
                HomeUniversalSearchResults(
                    userUid = userUid,
                    query = query,
                    sidebarBounds = sidebarBounds,
                    onOpenResult = onOpenSearchResult,
                    modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                )
            } else {
                RealHomeFeed(
                    onSelectService = onSelectService,
                    onOpenSearchResult = onOpenSearchResult,
                    sidebarBounds = sidebarBounds,
                    listState = feedListState,
                    modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                )
            }
        }
        SuperAppExpandedSidebar(
            selectedService = selectedService,
            onSelectService = onSelectService,
            totalUnreadChat = totalUnreadChat,
            onGoHome = {
                closeSearch()
                onGoHome()
            },
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val next = Rect(position.x, position.y, position.x + coordinates.size.width, position.y + coordinates.size.height)
                if (sidebarBounds != next) sidebarBounds = next
            },
        )
        Surface(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(start = 114.dp, end = 12.dp, top = 4.dp)
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (isLight) Color.White else Color(0xFF171C24),
            shadowElevation = 2.dp,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(80); searchOpen = true },
                singleLine = true,
                textStyle = TextStyle(color = if (isLight) Color(0xFF0F172A) else Color.White, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxSize().onFocusChanged { if (it.isFocused) searchOpen = true },
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search", color = Color(0xFF64748B), fontSize = 14.sp)
                            inner()
                        }
                        if (searchOpen) {
                            IconButton(onClick = closeSearch) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        }
                    }
                },
            )
        }
    }
}
