package com.adischat.app.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing active left sidebar bounds (expanded or compact rail)
 * to descendant content across all Super App destinations.
 */
val LocalSidebarBounds = compositionLocalOf<Rect?> { null }

/**
 * Checks whether a component with vertical bounds [rowTop]..[rowBottom] overlaps
 * the sidebar's vertical bounds.
 * If [sidebar] is null, returns false so content occupies full width.
 */
fun overlapsSidebar(rowTop: Float, rowBottom: Float, sidebar: Rect?): Boolean =
    sidebar != null && (rowTop < sidebar.bottom && rowBottom > sidebar.top)

/**
 * Responsive flow-around wrapper that indents content when alongside the sidebar
 * and expands into full available width once scrolled underneath the sidebar.
 *
 * @param sidebarBounds Explicit sidebar bounds or automatically reads [LocalSidebarBounds.current]
 * @param besideStart Start padding when beside the sidebar (e.g. 114.dp for expanded, 60.dp for compact)
 * @param belowStart Start padding when below the sidebar or when no sidebar is present (default 12.dp)
 * @param content Slot receiving [isBeside] flag allowing items to adapt from compact to wide visual cards
 */
@Composable
fun AdaptiveFlowAround(
    modifier: Modifier = Modifier,
    sidebarBounds: Rect? = LocalSidebarBounds.current,
    besideStart: Dp? = null,
    belowStart: Dp = 12.dp,
    content: @Composable (isBeside: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val effectiveBesideStart = besideStart ?: run {
        if (sidebarBounds != null) {
            val widthDp = with(density) { sidebarBounds.width.toDp() }
            if (widthDp > 70.dp) 114.dp else 60.dp
        } else {
            60.dp
        }
    }
    var besideSidebar by remember { mutableStateOf(sidebarBounds != null) }
    val startPadding by animateDpAsState(
        targetValue = if (besideSidebar) effectiveBesideStart else belowStart,
        animationSpec = tween(160),
        label = "flowAroundSidebar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val next = overlapsSidebar(top, top + coordinates.size.height, sidebarBounds)
                if (besideSidebar != next) besideSidebar = next
            }
            .padding(start = startPadding, end = belowStart),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(besideSidebar)
    }
}

/**
 * Fixed-height backward-compatible FlowAroundRow used in existing feed and promotions screens.
 */
@Composable
fun FlowAroundRow(
    sidebarBounds: Rect?,
    height: Dp,
    besideStart: Dp = 114.dp,
    belowStart: Dp = 12.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var besideSidebar by remember { mutableStateOf(sidebarBounds != null) }
    val startPadding by animateDpAsState(
        targetValue = if (besideSidebar) besideStart else belowStart,
        animationSpec = tween(160),
        label = "flowAroundSidebar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val next = overlapsSidebar(top, top + coordinates.size.height, sidebarBounds)
                if (besideSidebar != next) besideSidebar = next
            }
            .padding(start = startPadding, end = belowStart),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}
