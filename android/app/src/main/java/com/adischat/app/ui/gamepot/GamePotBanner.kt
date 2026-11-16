package com.adischat.app.ui.gamepot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.data.model.ChannelGame
import kotlinx.coroutines.flow.collectLatest
import com.adischat.app.ui.localization.tr

/**
 * Compact banner shown inside a channel's ConversationView when there is an active
 * (or drawing) Game Pot. Tapping it opens the GameRoomScreen overlay.
 *
 * @param channelId      The conversation/channel ID to poll for an active game.
 * @param myUid      The current user's UID (used for winner highlight).
 * @param isAdmin      Whether the current user is the channel admin.
 */
@Composable
fun GamePotBannerHost(
    channelId: String,
    myUid:     String,
    isAdmin:   Boolean,
    modifier:  androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    showLiveBanner:    Boolean = true,
    showPendingBanner: Boolean = true,
    showCreateEntry:   Boolean = true,
    onLiveGameVisibleChange: (Boolean) -> Unit = {},
    vm:      GamePotViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Start watching this channel
    LaunchedEffect(channelId) {
        vm.watchChannel(channelId)
    }

    var showRoom   by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // Listen to ViewModel events - NavigateToRoom opens the room immediately
    // after game creation, without waiting for the next poll cycle.
    LaunchedEffect(Unit) {
        vm.events.collectLatest { event ->
        when (event) {
        is GamePotEvent.NavigateToRoom -> {
        showCreate = false
        showRoom   = true
        }
        else -> Unit
        }
        }
    }

    val game = state.game
    val hasLiveGame    = game != null && game.isLive
    val hasPendingGame = game != null && game.isPending

    LaunchedEffect(hasLiveGame, showLiveBanner) {
        onLiveGameVisibleChange(hasLiveGame && showLiveBanner)
    }

    // Active game banner (live / drawing)
    AnimatedVisibility(
        visible  = showLiveBanner && hasLiveGame,
        enter    = expandVertically() + fadeIn(),
        exit     = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        if (game != null) {
        GamePotBanner(
        game      = game,
        msRemaining   = state.msRemaining,
        myTicketCount = state.myTickets.size,
        onClick       = { showRoom = true },
        )
        }
    }

    // Pending game banner - admin taps to resume the activation dashboard
    AnimatedVisibility(
        visible  = showPendingBanner && hasPendingGame && !hasLiveGame,
        enter    = expandVertically() + fadeIn(),
        exit     = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        PendingGameBanner(
        title    = game?.title ?: "",
        onClick  = { showRoom = true },
        modifier = modifier,
        )
    }

    // Admin "Start Game Pot" button - only when there is truly no game at all
    if (showCreateEntry && isAdmin && !hasLiveGame && !hasPendingGame) {
        AdminCreateGameButton(
        modifier = modifier,
        onClick  = { showCreate = true },
        )
    }

    // Full-screen game room overlay - open as soon as showRoom is true;
    // game is guaranteed to be in state because NavigateToRoom is emitted
    // AFTER the ViewModel sets game = snapshot.game.
    if (showRoom) {
        androidx.compose.ui.window.Dialog(
        onDismissRequest = { showRoom = false },
        properties       = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        ),
        ) {
        GameRoomScreen(
        gameId    = game?.id ?: "",
        channelId = channelId,
        myUid     = myUid,
        isAdmin   = isAdmin,
        onBack    = { showRoom = false },
        vm      = vm,
        )
        }
    }

    // Full-screen create game overlay (admin only)
    if (showCreate) {
        androidx.compose.ui.window.Dialog(
        onDismissRequest = { showCreate = false },
        properties       = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        ),
        ) {
        CreateGameScreen(
        channelId = channelId,
        onBack    = { showCreate = false },
        onCreated = {
        showCreate = false
        // NavigateToRoom event from ViewModel will set showRoom = true
        // once game state is confirmed in the ViewModel
        },
        vm      = vm,
        )
        }
    }
}

@Composable
private fun GamePotBanner(
    game:      ChannelGame,
    msRemaining:   Long?,
    myTicketCount: Int,
    onClick:       () -> Unit,
) {
    Box(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(
        Brush.horizontalGradient(
        listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFDB2777))
        )
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
        modifier      = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        // Left: emoji + title + pot size
        Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        Text(tr("-"), fontSize = 28.sp)
        Column {
        Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        // Live badge
        Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFEF4444).copy(alpha = 0.25f),
        ) {
        Text(
        "- LIVE",
        color      = Color(0xFFFCA5A5),
        fontSize   = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        }
        Text(
        game.title,
        color      = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize   = 13.sp,
        maxLines   = 1,
        )
        }
        Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        Text(
        "${"%.0f".format(game.jackpotAmount)} ETB",
        color      = Color(0xFFFBBF24),
        fontWeight = FontWeight.ExtraBold,
        fontSize   = 15.sp,
        )
        if (myTicketCount > 0) {
        Text(
        "- My tickets: $myTicketCount",
        color    = Color(0xFF6EE7B7),
        fontSize = 11.sp,
        )
        }
        }
        }
        }

        // Right: countdown + enter button
        Column(horizontalAlignment = Alignment.End) {
        if (msRemaining != null) {
        val mins = (msRemaining / 60_000)
        val secs = (msRemaining % 60_000) / 1_000
        Text(
        "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}",
        color      = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp,
        )
        }
        Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.18f),
        ) {
        Text(
        "Enter -",
        color      = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 12.sp,
        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        }
        }
        }
    }
}

// - Pending game banner (not yet activated) -

@Composable
private fun PendingGameBanner(
    title:    String,
    onClick:  () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Box(
        modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(
        Brush.horizontalGradient(
        listOf(Color(0xFF1E3A5F), Color(0xFF2563EB))
        )
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
        modifier      = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        Text(tr("-"), fontSize = 22.sp)
        Column {
        Text(
        "Game Ready - Not Activated",
        color      = Color(0xFF93C5FD),
        fontSize   = 11.sp,
        fontWeight = FontWeight.Medium,
        )
        Text(
        title.ifBlank { "Game Pot" },
        color      = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize   = 13.sp,
        maxLines   = 1,
        )
        }
        }
        Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.15f),
        ) {
        Text(
        "Activate -",
        color      = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 12.sp,
        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        }
        }
    }
}

// - Admin create-game entry point -

@Composable
private fun AdminCreateGameButton(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onClick:  () -> Unit,
) {
    Box(
        modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        border   = androidx.compose.foundation.BorderStroke(
        1.dp,
        Color(0xFF7C3AED).copy(alpha = 0.5f),
        ),
        ) {
        Text(
        "-  Start a Game Pot",
        color      = Color(0xFF7C3AED),
        fontWeight = FontWeight.SemiBold,
        fontSize   = 13.sp,
        )
        }
    }
}
