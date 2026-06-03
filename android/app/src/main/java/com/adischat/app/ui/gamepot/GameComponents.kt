package com.adischat.app.ui.gamepot

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adischat.app.data.model.ChannelGame
import com.adischat.app.data.model.GameParticipant
import com.adischat.app.data.model.GameTicket
import com.adischat.app.data.model.GameWinner

// ── Jackpot header card ───────────────────────────────────────────────────────

@Composable
fun JackpotHeader(
    game:        ChannelGame,
    msRemaining: Long?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF111827),
                        Color(0xFF4C1D95),
                        Color(0xFF7C3AED),
                        Color(0xFF0F172A),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Status badge
            GameStatusBadge(game.status)

            Spacer(Modifier.height(8.dp))

            Text(
                text       = game.title,
                color      = Color.White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign  = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            // Jackpot amount — pulses when game is live
            AnimatedJackpotAmount(amount = game.jackpotAmount, live = game.isLive)

            Spacer(Modifier.height(14.dp))

            // Sold progress bar
            TicketProgressBar(soldPct = game.soldPct, sold = game.ticketsSold, total = game.totalTickets)

            Spacer(Modifier.height(10.dp))

            // Countdown
            if (game.isLive && msRemaining != null) {
                CountdownTimer(msRemaining = msRemaining)
            }
        }
    }
}

@Composable
private fun GameStatusBadge(status: String) {
    val (label, color) = when (status) {
        "active"    -> "🔴 LIVE"     to Color(0xFFEF4444)
        "pending"   -> "⏳ PENDING"  to Color(0xFFF59E0B)
        "drawing"   -> "🎰 DRAWING"  to Color(0xFF8B5CF6)
        "completed" -> "✅ COMPLETE" to Color(0xFF10B981)
        "cancelled" -> "❌ CANCELLED" to Color(0xFF6B7280)
        else        -> status        to Color.Gray
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.25f),
    ) {
        Text(
            text       = label,
            color      = color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AnimatedJackpotAmount(amount: Double, live: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (live) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "jackpot_scale",
    )
    Text(
        text       = "${"%.2f".format(amount)} ETB",
        color      = Color(0xFFFBBF24),
        fontSize   = 38.sp,
        fontWeight = FontWeight.Black,
        modifier   = Modifier.scale(scale),
    )
}

@Composable
private fun TicketProgressBar(soldPct: Float, sold: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress           = { soldPct },
            modifier           = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color              = Color(0xFFFBBF24),
            trackColor         = Color.White.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "$sold / $total tickets sold",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun CountdownTimer(msRemaining: Long) {
    val hours   = (msRemaining / 3_600_000)
    val minutes = (msRemaining % 3_600_000) / 60_000
    val seconds = (msRemaining % 60_000) / 1_000

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hours > 0) {
            TimerSegment(value = hours.toString().padStart(2, '0'),   label = "HR")
            Text(":", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        TimerSegment(value = minutes.toString().padStart(2, '0'),     label = "MIN")
        Text(":", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TimerSegment(value = seconds.toString().padStart(2, '0'),     label = "SEC")
    }
}

@Composable
private fun TimerSegment(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.3f),
        ) {
            Text(
                text       = value,
                color      = Color.White,
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
    }
}

// ── Ticket grid cell ──────────────────────────────────────────────────────────

@Composable
fun TicketCell(
    ticket:    GameTicket,
    isSelected: Boolean,
    onClick:   () -> Unit,
) {
    val bgColor = when {
        ticket.status == "won"       -> Color(0xFFFBBF24)
        ticket.isMine                -> Color(0xFF10B981)
        ticket.status == "sold"      -> Color(0xFF374151)
        isSelected                   -> Color(0xFF7C3AED)
        else                         -> Color(0xFF1F2937)
    }
    val borderColor = when {
        isSelected       -> Color(0xFF7C3AED)
        ticket.status == "won"  -> Color(0xFFF59E0B)
        ticket.isMine    -> Color(0xFF059669)
        else             -> Color.Transparent
    }
    val textColor = when {
        ticket.status == "won" -> Color.Black
        else                   -> Color.White
    }
    val clickable = ticket.status == "available"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected || ticket.status == "won" || ticket.isMine) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text       = ticket.ticketNumber.toString(),
            color      = textColor,
            fontSize   = 12.sp,
            fontWeight = if (isSelected || ticket.isMine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── Recent buyers row ─────────────────────────────────────────────────────────

@Composable
fun RecentBuyersRow(participants: List<GameParticipant>) {
    if (participants.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Recent Buyers",
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(participants) { p ->
                ParticipantChip(participant = p)
            }
        }
    }
}

@Composable
private fun ParticipantChip(participant: GameParticipant) {
    Surface(
        shape  = RoundedCornerShape(50),
        color  = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Box(
                modifier         = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = participant.name.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column {
                Text(
                    text     = participant.name,
                    style    = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "${participant.ticketsCount} ticket${if (participant.ticketsCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Winner announcement overlay ───────────────────────────────────────────────

@Composable
fun DrawingWinnerOverlay() {
    val spin by rememberInfiniteTransition(label = "drawing_winner_spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drawing_winner_spin_value",
    )

    Dialog(
        onDismissRequest = {},
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.82f),
                shape    = RoundedCornerShape(24.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFBBF24),
                                        Color(0xFF7C3AED),
                                        Color(0xFF10B981),
                                        Color(0xFFFBBF24),
                                    )
                                )
                            )
                            .rotate(spin),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.size(68.dp),
                            shape = CircleShape,
                            color = Color(0xFF111827),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("?", color = Color(0xFFFBBF24), fontSize = 36.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Text(
                        "Drawing winner...",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Selecting the lucky ticket number",
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun WinnerOverlay(
    winner:    GameWinner,
    myUid:     String,
    onDismiss: () -> Unit,
) {
    val isMe = winner.winnerUid == myUid

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(24.dp)),
                colors   = CardDefaults.cardColors(
                    containerColor = Color(0xFF1F2937),
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Trophy / confetti header
                    Text("🏆", fontSize = 64.sp)

                    Text(
                        text       = if (isMe) "You Won!" else "${winner.winnerName} Won!",
                        color      = Color(0xFFFBBF24),
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Black,
                        textAlign  = TextAlign.Center,
                    )

                    // Winning ticket
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                Text("Winner Ticket No.", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                "#${winner.ticketNumber}",
                                color      = Color(0xFFFBBF24),
                                fontSize   = 44.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Payout breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Jackpot", color = Color.White.copy(alpha = 0.7f))
                        Text("${"%.2f".format(winner.jackpotAmount)} ETB", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Admin fee", color = Color.White.copy(alpha = 0.7f))
                        Text("- ${"%.2f".format(winner.adminCommission)} ETB", color = Color(0xFFEF4444))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Winner receives", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${"%.2f".format(winner.payoutAmount)} ETB", color = Color(0xFF10B981), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (isMe) "Collect Winnings 🎉" else "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Admin action row ──────────────────────────────────────────────────────────

@Composable
fun AdminActionRow(
    game:      ChannelGame,
    isDrawing: Boolean,
    onDraw:    () -> Unit,
    onCancel:  () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Draw winner button — visible when active
        if (game.isActive) {
            Button(
                onClick  = onDraw,
                enabled  = !isDrawing && game.ticketsSold > 0,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            ) {
                if (isDrawing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("🎰 Draw Winner", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Cancel button — pending or active
        if (game.isPending || game.isActive) {
            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text("Cancel Game", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Buy panel (shown at bottom when tickets are selected) ─────────────────────

@Composable
fun BuyPanel(
    selectedCount: Int,
    priceEach:     Double,
    isBuying:      Boolean,
    onBuy:         (pin: String) -> Unit,
    onClear:       () -> Unit,
) {
    val total = selectedCount * priceEach
    var showPinDialog by remember { mutableStateOf(false) }

    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color          = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "$selectedCount ticket${if (selectedCount != 1) "s" else ""} selected",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Total: ${"%.2f".format(total)} ETB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                Button(
                    onClick  = { showPinDialog = true },
                    enabled  = !isBuying && selectedCount > 0,
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    if (isBuying) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Buy Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        WalletPinDialog(
            title        = "Confirm Purchase",
            subtitle     = "Enter your wallet PIN to buy ${selectedCount} ticket${if (selectedCount != 1) "s" else ""} for ${"%.2f".format(total)} ETB",
            onDismiss    = { showPinDialog = false },
            onConfirm    = { pin ->
                showPinDialog = false
                onBuy(pin)
            },
        )
    }
}

// ── Wallet PIN dialog ─────────────────────────────────────────────────────────

@Composable
fun WalletPinDialog(
    title:     String,
    subtitle:  String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit,
) {
    var pin     by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val blink by rememberInfiniteTransition(label = "pinCursor").animateFloat(
        initialValue = 0.2f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pinCursorAlpha",
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Auto-submit when all 6 digits entered
    LaunchedEffect(pin) {
        if (pin.length == 6) onConfirm(pin)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape  = RoundedCornerShape(24.dp),
            color  = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Lock icon
                Text("🔐", fontSize = 36.sp)

                // Title
                Text(
                    title,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )

                // Subtitle
                Text(
                    subtitle,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Invisible text field that captures input
                BasicTextField(
                    value         = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush   = SolidColor(Color.Transparent),
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
                    decorationBox = {
                        // 6 PIN boxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(6) { index ->
                                val filled = index < pin.length
                                val active = focused && index == pin.length.coerceAtMost(5)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (filled || active) 0.14f else 0.06f
                                            )
                                        )
                                        .border(
                                            width = if (active) 2.dp else if (filled) 1.6.dp else 1.dp,
                                            color = when {
                                                active -> MaterialTheme.colorScheme.primary
                                                filled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                else   -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (filled) {
                                        Text(
                                            "•",
                                            color      = MaterialTheme.colorScheme.primary,
                                            fontSize   = 28.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    } else if (active) {
                                        Box(
                                            Modifier
                                                .width(2.dp)
                                                .height(22.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = blink)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                Text(
                    "Enter your 6-digit wallet PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
