package com.adischat.app.ui.gamepot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.data.model.ChannelGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRoomScreen(
    gameId:    String,
    channelId: String,
    myUid:     String,
    isAdmin:   Boolean,
    onBack:    () -> Unit,
    vm:        GamePotViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val game = state.game
    val snackbarHost = remember { SnackbarHostState() }

    // Start polling this specific game
    LaunchedEffect(gameId) {
        vm.watchGame(gameId)
    }

    // Collect one-shot events → snackbar / navigation
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is GamePotEvent.ShowError   -> snackbarHost.showSnackbar(event.message)
                is GamePotEvent.ShowSuccess -> snackbarHost.showSnackbar(event.message)
                is GamePotEvent.TicketsBought ->
                    snackbarHost.showSnackbar("Tickets purchased! Good luck 🍀")
                is GamePotEvent.GameCancelled -> {
                    snackbarHost.showSnackbar("Game cancelled. Refunds issued.")
                    onBack()
                }
                else -> Unit
            }
        }
    }

    // Winner overlay
    if (state.showWinnerOverlay && state.winner != null) {
        WinnerOverlay(
            winner    = state.winner!!,
            myUid     = myUid,
            onDismiss = { vm.dismissWinnerOverlay() },
        )
    }

    if (state.isDrawing) {
        DrawingWinnerOverlay()
    }

    Scaffold(
        containerColor = Color(0xFF05070D),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 112.dp),
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            game?.title ?: "Game Room",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                        )
                        if (game != null) {
                            val subtitle = when {
                                game.isPending   -> "⏳ Pending activation"
                                game.isActive    -> "${game.remainingTickets} tickets left"
                                game.isDrawing   -> "Drawing winner…"
                                game.isCompleted -> "Game ended"
                                else             -> ""
                            }
                            Text(
                                subtitle,
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF05070D),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        }
    ) { padding ->

        if (game == null) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎰", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No active game",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@Scaffold
        }

        // Animate between Pending dashboard and Live room
        AnimatedContent(
            targetState = game.isPending,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 4 })
                    .togetherWith(fadeOut() + slideOutVertically { -it / 4 })
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "game-state-transition",
        ) { isPending ->
            if (isPending) {
                PendingGameDashboard(
                    game      = game,
                    isAdmin   = isAdmin,
                    isLoading = state.isLoading,
                    onActivate = { vm.activateGame(game.id) },
                    onCancel   = { vm.cancelGame(game.id) },
                )
            } else {
                LiveGameRoom(
                    game           = game,
                    state          = state,
                    gameId         = gameId,
                    isAdmin        = isAdmin,
                    onDraw         = { vm.drawWinner(gameId) },
                    onCancel       = { vm.cancelGame(gameId) },
                    onToggleTicket = { vm.toggleTicket(it) },
                    onBuy          = { pin -> vm.buySelectedTickets(gameId, pin) },
                    onClearSel     = { vm.clearSelection() },
                )
            }
        }
    }
}

// ── Pending Game Dashboard ────────────────────────────────────────────────────

@Composable
private fun PendingGameDashboard(
    game:      ChannelGame,
    isAdmin:   Boolean,
    isLoading: Boolean,
    onActivate: () -> Unit,
    onCancel:   () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel game?") },
            text  = { Text("This will discard the game. No tickets have been sold yet.") },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text("Yes, cancel", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep it") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070D), Color(0xFF0B1020), Color(0xFF05070D)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        // Hero banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFDB2777))
                    ),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🎰", fontSize = 52.sp)
                Text(
                    game.title,
                    color      = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 22.sp,
                    textAlign  = TextAlign.Center,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFBBF24).copy(alpha = 0.2f),
                ) {
                    Text(
                        "🏆 Pot: ${"%.0f".format(game.jackpotAmount)} ETB",
                        color      = Color(0xFFFBBF24),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 18.sp,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Game details card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Game Setup",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                PendingDetailRow("Total tickets",       "${game.totalTickets}")
                PendingDetailRow("Price per ticket",    "${"%.0f".format(game.pricePerTicket)} ETB")
                PendingDetailRow("Admin commission",    "${game.adminCommissionPct.toInt()}%")
                PendingDetailRow("Winner payout",       "${"%.0f".format(game.jackpotAmount)} ETB")
                if (game.maxTicketsPerUser != null && game.maxTicketsPerUser > 0) {
                    PendingDetailRow("Max per user", "${game.maxTicketsPerUser}")
                }
                val durMin = (game.durationSeconds / 60).toInt()
                PendingDetailRow("Duration",            if (durMin >= 60) "${durMin / 60}h ${durMin % 60}m" else "${durMin}m")
            }
        }

        // Status info
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFBBF24).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚡", fontSize = 20.sp)
                Text(
                    "Game is ready. Tap 'Activate' to open ticket sales to participants.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Activate button (admin only)
        if (isAdmin) {
            Button(
                onClick  = onActivate,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED),
                    contentColor   = Color.White,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "▶  Activate Game",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = { showCancelDialog = true },
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(14.dp),
                border   = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                ),
            ) {
                Text(
                    "✕  Cancel Game",
                    color      = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PendingDetailRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Live Game Room ─────────────────────────────────────────────────────────────

@Composable
private fun LiveGameRoom(
    game:           ChannelGame,
    state:          GamePotUiState,
    gameId:         String,
    isAdmin:        Boolean,
    onDraw:         () -> Unit,
    onCancel:       () -> Unit,
    onToggleTicket: (Int) -> Unit,
    onBuy:          (String) -> Unit,
    onClearSel:     () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070D), Color(0xFF090D16), Color(0xFF05070D)),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed header: jackpot + controls
            Column(
                modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JackpotHeader(game = game, msRemaining = state.msRemaining)

                if (state.myTickets.isNotEmpty()) {
                    MyTicketsRow(state.myTickets)
                }

                if (isAdmin && game.isLive) {
                    AdminActionRow(
                        game      = game,
                        isDrawing = state.isDrawing,
                        onDraw    = onDraw,
                        onCancel  = onCancel,
                    )
                }

                TicketLegend()
                RecentBuyersRow(participants = state.participants)
            }

            HorizontalDivider()

            if (state.tickets.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Adaptive(minSize = 52.dp),
                    contentPadding        = PaddingValues(
                        start  = 12.dp,
                        top    = 12.dp,
                        end    = 12.dp,
                        bottom = if (state.selectedTickets.isNotEmpty()) 136.dp else 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(
                        items = state.tickets,
                        key   = { it.ticketNumber },
                    ) { ticket ->
                        TicketCell(
                            ticket     = ticket,
                            isSelected = ticket.ticketNumber in state.selectedTickets,
                            onClick    = {
                                if (game.isActive && ticket.status == "available") {
                                    onToggleTicket(ticket.ticketNumber)
                                }
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = state.selectedTickets.isNotEmpty() && game.isActive,
            enter    = slideInVertically(initialOffsetY = { it }),
            exit     = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
        ) {
            BuyPanel(
                selectedCount = state.selectedTickets.size,
                priceEach     = game.pricePerTicket,
                isBuying      = state.isBuying,
                onBuy         = onBuy,
                onClear       = onClearSel,
            )
        }
    }
}

// ── My tickets banner ─────────────────────────────────────────────────────────

@Composable
private fun MyTicketsRow(myTickets: List<Int>) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = Color(0xFF10B981).copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "My Tickets 🎟",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color(0xFF059669),
                )
                Text(
                    myTickets.sorted().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${myTickets.size}",
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 22.sp,
                color      = Color(0xFF059669),
            )
        }
    }
}

// ── Ticket color legend ───────────────────────────────────────────────────────

@Composable
private fun TicketLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        LegendItem(color = Color(0xFF1F2937), label = "Available")
        LegendItem(color = Color(0xFF374151), label = "Sold")
        LegendItem(color = Color(0xFF10B981), label = "Mine")
        LegendItem(color = Color(0xFF7C3AED), label = "Selected")
        LegendItem(color = Color(0xFFFBBF24), label = "Winner")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
