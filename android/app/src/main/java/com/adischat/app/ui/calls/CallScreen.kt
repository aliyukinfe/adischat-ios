package com.adischat.app.ui.calls

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CallMode { Voice, Video }

/** Call lifecycle state machine. */
enum class CallPhase {
    Calling, Ringing, IncomingRinging, Connecting, Connected, Reconnecting,
    Ended, Missed, Rejected, Failed;

    val isRinging get() = this == Calling || this == Ringing || this == IncomingRinging
    val isOngoing get() = this == Connecting || this == Connected || this == Reconnecting
    val isTerminal get() = this == Ended || this == Missed || this == Rejected || this == Failed
}

data class CallUiState(
    val callId: String = "",
    val mode: CallMode = CallMode.Voice,
    val phase: CallPhase = CallPhase.Calling,
    val peerUid: String = "",
    val peerName: String = "",
    val peerPhotoUrl: String = "",
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraEnabled: Boolean = false,
    val usingFrontCamera: Boolean = true,
    val loading: Boolean = false,
    val error: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val connectedAt: Long = 0L,
    val endReason: String = "",
    val connectionQuality: String = "",
    val latencyMs: Int = 0,
    val audioLevel: Float = 0f,
    // Group calls (1:1 leaves these at defaults - no participant UI is shown).
    val isGroup: Boolean = false,
    val participantCount: Int = 0,
    val participantAvatars: List<String> = emptyList(),
)

// - AdisChat call palette -
private val Ink      = Color(0xFF05070D)
private val Green     = Color(0xFF22C55E)
private val Emerald   = Color(0xFF34D399)
private val Teal      = Color(0xFF0EA5A4)
private val Gold      = Color(0xFFF5C451)   // Ethiopian-inspired warm accent
private val RedOrb    = Color(0xFFFF3B47)
private val TextHi    = Color(0xFFF4F8FF)
private val TextLo    = Color(0xFF93A0B4)
private val Glass     = Color(0x14FFFFFF)
private val GlassBrd  = Color(0x22FFFFFF)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    state: CallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleCamera: () -> Unit,
    onDismissEnded: () -> Unit,
    remoteVideo: (@Composable () -> Unit)? = null,
    localVideo: (@Composable () -> Unit)? = null,
) {
    val permissions = if (state.mode == CallMode.Video)
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    else listOf(Manifest.permission.RECORD_AUDIO)
    val permissionState = rememberMultiplePermissionsState(permissions)
    val hasPermissions = permissionState.permissions.all { it.status.isGranted }
    LaunchedEffect(state.mode, hasPermissions) {
        Log.d("CallScreen", "call permission result mode=${state.mode} granted=$hasPermissions details=${permissionState.permissions.joinToString { "${it.permission}:${it.status}" }}")
        if (!hasPermissions) permissionState.launchMultiplePermissionRequest()
    }

    // Ringtone / ringback, driven by phase. Calling is silent until the receiver
    // device acknowledges that an incoming call UI is visible.
    val context = LocalContext.current
    val sounds = remember { CallSoundManager(context) }
    LaunchedEffect(state.phase) {
        when (state.phase) {
        CallPhase.Ringing -> sounds.outgoingRinging()
        CallPhase.IncomingRinging -> sounds.incomingRinging()
        else -> sounds.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { sounds.stop() } }

    val showVideo = state.mode == CallMode.Video &&
        (state.phase == CallPhase.Connected || state.phase == CallPhase.Reconnecting)

    AnimatedVisibility(visible = true, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Ink)) {

        // - Background: full-screen video OR signature aurora -
        if (showVideo && remoteVideo != null) {
        Box(Modifier.fillMaxSize()) { remoteVideo() }
        Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(0f to Color(0x66000000), 0.35f to Color.Transparent, 1f to Color(0xCC000000))
        ))
        } else {
        CallAuroraBackground(active = state.phase.isRinging, level = state.audioLevel)
        }

        Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Spacer(Modifier.height(14.dp))
        CallHeader(state, hasPermissions)

        if (!showVideo) {
        Spacer(Modifier.weight(1f))
        SignatureAvatarStage(state)
        Spacer(Modifier.weight(1f))
        } else {
        Spacer(Modifier.weight(1f))
        if (state.cameraEnabled && localVideo != null) {
        Box(
        Modifier.align(Alignment.End).size(112.dp, 156.dp)
        .clip(RoundedCornerShape(20.dp)).border(1.dp, GlassBrd, RoundedCornerShape(20.dp)),
        ) { localVideo() }
        }
        }

        if (state.error.isNotBlank()) {
        Text(
        state.error, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp))
        .background(Gold.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 9.dp),
        )
        }

        if (state.phase == CallPhase.Connected) {
        ConnectionInsights(state)
        Spacer(Modifier.height(14.dp))
        }

        CallDock(state, hasPermissions, onAccept, onReject, onEnd, onDismissEnded,
        onToggleMute, onToggleSpeaker, onSwitchCamera, onToggleCamera)
        Spacer(Modifier.height(10.dp))
        }
        }
    }
}

// - Header: name - type - status - duration - chips -
@Composable
private fun CallHeader(state: CallUiState, hasPermissions: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
        state.peerName.ifBlank { "Unknown caller" },
        color = TextHi, fontSize = 27.sp, fontWeight = FontWeight.Black,
        maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
        (if (state.isGroup) "Group " else "") + (if (state.mode == CallMode.Video) "Video Call" else "Voice Call"),
        color = TextLo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        )

        // Subtle participant summary (group calls only) - small supporting info,
        // never competing with the name or the call duration.
        if (state.isGroup && state.participantCount > 0) {
        Spacer(Modifier.height(8.dp))
        if (state.participantAvatars.isNotEmpty()) {
        OverlappingAvatars(state.participantAvatars, extra = (state.participantCount - state.participantAvatars.size).coerceAtLeast(0))
        Spacer(Modifier.height(6.dp))
        }
        androidx.compose.animation.Crossfade(targetState = state.participantCount, label = "participants") { c ->
        Text(
        "$c participant${if (c == 1) "" else "s"}",
        color = TextLo, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
        )
        }
        }
        Spacer(Modifier.height(10.dp))

        if (state.phase == CallPhase.Connected) {
        DurationTimer(state.connectedAt.takeIf { it > 0L } ?: state.startedAt)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(Icons.Default.GraphicEq, "HD Audio", Emerald)
        StatusChip(Icons.Default.Lock, "Encrypted", Green)
        QualityChip(state.connectionQuality.ifBlank { "Excellent" })
        }
        } else {
        Text(
        statusText(state, hasPermissions),
        color = if (state.phase.isTerminal) TextLo else Emerald,
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        }
    }
}

@Composable
private fun DurationTimer(base: Long) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(base) {
        while (true) { elapsed = ((System.currentTimeMillis() - base).coerceAtLeast(0L) / 1000L); delay(250) }
    }
    val tick by remember { derivedStateOf { elapsed } }
    val pulse by animateFloatAsState(if (tick % 2L == 0L) 1f else 0.985f, tween(400), label = "tick")
    Text(
        "%02d:%02d".format(elapsed / 60, elapsed % 60),
        color = TextHi, fontSize = 40.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
    )
}

/** Small overlapping participant avatars with a "+N" indicator (group calls). */
@Composable
private fun OverlappingAvatars(urls: List<String>, extra: Int) {
    val shown = urls.take(4)
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { u ->
        Box(
        Modifier.size(28.dp).clip(CircleShape).border(2.dp, Ink, CircleShape).background(Glass),
        contentAlignment = Alignment.Center,
        ) {
        if (u.isNotBlank()) {
        AsyncImage(u, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
        Icon(Icons.Default.Person, null, tint = TextLo, modifier = Modifier.size(15.dp))
        }
        }
        }
        if (extra > 0) {
        Box(
        Modifier.size(28.dp).clip(CircleShape).border(2.dp, Ink, CircleShape).background(Color(0xFF1A2230)),
        contentAlignment = Alignment.Center,
        ) { Text("+$extra", color = TextHi, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatusChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Glass).border(1.dp, GlassBrd, RoundedCornerShape(999.dp))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Text(label, color = TextHi, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QualityChip(quality: String) {
    val color = when (quality) { "Poor" -> RedOrb; "Good" -> Gold; else -> Green }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Glass).border(1.dp, GlassBrd, RoundedCornerShape(999.dp))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (0..2).forEach { i ->
        val active = when (quality) { "Poor" -> i == 0; "Good" -> i <= 1; else -> true }
        Box(Modifier.size(width = 3.dp, height = (5 + i * 3).dp).clip(RoundedCornerShape(2.dp))
        .background(if (active) color else color.copy(alpha = 0.25f)))
        }
        Text(quality, color = TextHi, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// - Signature dynamic avatar stage -
@Composable
private fun SignatureAvatarStage(state: CallUiState) {
    val infinite = rememberInfiniteTransition(label = "stage")
    // Breathing / floating
    val breathe by infinite.animateFloat(0.97f, 1.03f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val float by infinite.animateFloat(-6f, 6f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    // Expanding energy waves
    val wave by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    // Audio-reactive glow
    val targetGlow = if (state.phase == CallPhase.Connected) 0.35f + state.audioLevel.coerceIn(0f, 1f) * 0.9f else 0.5f
    val glow by animateFloatAsState(targetGlow, spring(stiffness = Spring.StiffnessLow), label = "glow")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        // Circular sound energy waves - the AdisChat identity
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationY = float }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseR = size.minDimension * 0.18f
        val maxR = size.minDimension * 0.5f
        val ringCount = if (state.phase.isRinging) 4 else 3
        for (i in 0 until ringCount) {
        val p = ((wave + i.toFloat() / ringCount) % 1f)
        val radius = baseR + (maxR - baseR) * p
        val alpha = (1f - p) * (if (state.phase.isRinging) 0.5f else 0.28f + glow * 0.25f)
        drawCircle(
        brush = Brush.radialGradient(
        listOf(Color.Transparent, Green.copy(alpha = alpha)),
        center = center, radius = radius,
        ),
        radius = radius, center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
        )
        }
        // Audio-reactive radial glow halo
        val haloR = baseR * (1.5f + glow)
        drawCircle(
        brush = Brush.radialGradient(
        listOf(Emerald.copy(alpha = 0.30f * glow), Color.Transparent),
        center = center, radius = haloR,
        ),
        radius = haloR, center = center,
        )
        }

        // Floating avatar with breathing + gradient ring
        Box(
        Modifier.size(140.dp).graphicsLayer { translationY = float; scaleX = breathe; scaleY = breathe }
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Green, Teal)))
        .padding(3.dp),
        contentAlignment = Alignment.Center,
        ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF0B1220)), contentAlignment = Alignment.Center) {
        if (state.peerPhotoUrl.isNotBlank()) {
        key(state.peerUid, state.peerPhotoUrl) {
        AsyncImage(state.peerPhotoUrl, "Caller", modifier = Modifier.fillMaxSize().clip(CircleShape),
        contentScale = ContentScale.Crop)
        }
        } else {
        Text(state.peerName.take(1).ifBlank { "U" }.uppercase(),
        color = Emerald, fontSize = 52.sp, fontWeight = FontWeight.Black)
        }
        }
        }

        if (state.loading || state.phase == CallPhase.Connecting) {
        CircularProgressIndicator(
        color = Emerald, strokeWidth = 2.dp,
        modifier = Modifier.size(168.dp).graphicsLayer { translationY = float },
        )
        }
    }
}

// - Connection insights (expandable) -
@Composable
private fun ConnectionInsights(state: CallUiState) {
    var expanded by remember { mutableStateOf(false) }
    val quality = state.connectionQuality.ifBlank { "Excellent" }
    val latency = if (state.latencyMs > 0) "${state.latencyMs} ms" else "Measuring-"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass)
        .border(1.dp, GlassBrd, RoundedCornerShape(22.dp)).clickable { expanded = !expanded }
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(
        when (quality) { "Poor" -> RedOrb; "Good" -> Gold; else -> Green }))
        Spacer(Modifier.width(10.dp))
        Text("Connection $quality", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(latency, color = TextLo, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
        tint = TextLo, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(expanded) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InsightRow("Network", quality, when (quality) { "Poor" -> RedOrb; "Good" -> Gold; else -> Green })
        InsightRow("Latency", latency, Emerald)
        InsightRow("Audio", "HD - Opus", Emerald)
        InsightRow("Encryption", "Active - DTLS-SRTP", Green)
        }
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextLo, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// - Bottom dock -
@Composable
private fun CallDock(
    state: CallUiState,
    hasPermissions: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onDismissEnded: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleCamera: () -> Unit,
) {
    when {
        state.phase == CallPhase.IncomingRinging ->
        IncomingCallControls(onAccept = onAccept, onReject = onReject)

        state.phase.isTerminal -> {
        Text(state.endReason.ifBlank { "Call ended" }, color = TextLo, fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        EndOrb(label = "Close", color = Color(0xFF1A2030), glow = false, onClick = onDismissEnded)
        }

        else -> {
        // Only the essential, modern controls: Mute - Speaker - (Camera - Flip) - End.
        Row(
        Modifier.clip(RoundedCornerShape(28.dp)).background(Glass).border(1.dp, GlassBrd, RoundedCornerShape(28.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        GlassControl(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", state.muted, onToggleMute)
        GlassControl(Icons.Default.VolumeUp, "Speaker", state.speaker, onToggleSpeaker)
        if (state.mode == CallMode.Video) {
        GlassControl(if (state.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera", state.cameraEnabled, onToggleCamera)
        GlassControl(Icons.Default.Cameraswitch, "Flip", false, onSwitchCamera)
        }
        }
        Spacer(Modifier.height(18.dp))
        EndOrb(label = if (state.phase.isRinging) "Cancel" else "End", color = RedOrb, glow = true, onClick = onEnd)
        }
    }
}

@Composable
private fun GlassControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(), label = "press")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
        Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(20.dp))
        .background(if (active) Green else Color(0x1FFFFFFF))
        .border(1.dp, if (active) Color.Transparent else GlassBrd, RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        ) {
        Icon(icon, label, tint = if (active) Ink else TextHi, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextLo, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EndOrb(label: String, color: Color, glow: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "orb")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (glow) Modifier.androidShadow(color) else Modifier)
        .clip(CircleShape)
        .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.78f))))
        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        .clickable(interactionSource = interaction, indication = null) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
        },
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.CallEnd, label, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(7.dp))
        Text(label, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Modifier.androidShadow(color: Color): Modifier =
    this.then(Modifier.graphicsLayer { shadowElevation = 24f; shape = CircleShape; clip = false; ambientShadowColor = color; spotShadowColor = color })

// - Incoming: two clean, large, centered buttons (Decline / Answer) -
@Composable
private fun IncomingCallControls(onAccept: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnswerOrb(Icons.Default.CallEnd, RedOrb, "Decline", onReject)
        AnswerOrb(Icons.Default.Call, Green, "Answer", onAccept)
    }
}

@Composable
private fun AnswerOrb(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, label: String, onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ans")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .androidShadow(color)
        .clip(CircleShape)
        .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.80f))))
        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
        .clickable(interactionSource = interaction, indication = null) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
        },
        contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(9.dp))
        Text(label, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// - "More" smart-features panel (architecture surface) -
// - Animated aurora background + particles -
@Composable
private fun CallAuroraBackground(active: Boolean, level: Float) {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val t by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "auroraT")
    val drift by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "drift")

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF070A12), Color(0xFF090D18), Color(0xFF05070D))))
        val w = size.width; val h = size.height
        val a = sin(t * 2 * PI).toFloat()
        val b = cos(t * 2 * PI).toFloat()
        fun blob(cx: Float, cy: Float, r: Float, color: Color) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r, center = Offset(cx, cy))
        }
        blob(w * 0.28f + a * 60f, h * 0.30f + b * 40f, w * 0.62f, Green.copy(alpha = 0.16f + level * 0.06f))
        blob(w * 0.74f - a * 50f, h * 0.62f - b * 30f, w * 0.58f, Teal.copy(alpha = 0.14f))
        blob(w * 0.55f + b * 40f, h * 0.16f, w * 0.42f, Gold.copy(alpha = 0.05f))
        blob(w * 0.20f, h * 0.85f + a * 30f, w * 0.40f, Emerald.copy(alpha = 0.08f))

        // Subtle drifting particles
        val n = 26
        for (i in 0 until n) {
        val seed = (i * 1327 % 1000) / 1000f
        val seed2 = (i * 7919 % 1000) / 1000f
        val px = (seed * w + sin((drift + seed) * 2 * PI).toFloat() * 22f)
        val py = (1f - ((drift + seed2) % 1f)) * h
        val pr = 1.2f + seed2 * 2.2f
        drawCircle(Color.White.copy(alpha = 0.06f + seed * 0.06f), radius = pr, center = Offset(px % w, py))
        }
    }
    // Soft glass vignette
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x66000000))))
}

// - Status text for non-connected phases -
@Composable
private fun statusText(state: CallUiState, hasPermissions: Boolean): String {
    if (!hasPermissions) return if (state.mode == CallMode.Video) "Allow camera & microphone" else "Allow microphone"
    return when (state.phase) {
        CallPhase.Calling -> "Calling..."
        CallPhase.Ringing -> "Ringing..."
        CallPhase.IncomingRinging -> if (state.mode == CallMode.Video) "Incoming video call" else "Incoming voice call"
        CallPhase.Connecting     -> "Connecting - secure channel"
        CallPhase.Reconnecting   -> "Reconnecting..."
        CallPhase.Connected      -> "Connected"
        CallPhase.Ended -> state.endReason.ifBlank { "Call ended" }
        CallPhase.Missed -> "Missed call"
        CallPhase.Rejected -> "Call declined"
        CallPhase.Failed -> state.endReason.ifBlank { "Connection failed" }
    }
}

// - Ringtone / ringback (unchanged behavior) -
private class CallSoundManager(private val context: Context) {
    private var ringbackPlayer: android.media.MediaPlayer? = null
    private var ringtonePlayer: android.media.MediaPlayer? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun outgoingRinging() {
        stop()
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        // Custom AdisChat ringback melody (res/raw/call_ringback.mp3), looped
        // while the other side is ringing.
        runCatching {
        ringbackPlayer = android.media.MediaPlayer.create(context, com.adischat.app.R.raw.call_ringback)?.apply {
        isLooping = true
        setAudioAttributes(
        android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build(),
        )
        start()
        }
        }
    }

    fun incomingRinging() {
        stop()
        val mode = audio?.ringerMode
        // Custom AdisChat incoming ringtone (res/raw/call_ringtone.mp3), looped.
        // Only plays sound when the ringer is in normal mode; vibrates otherwise.
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
        runCatching {
        ringtonePlayer = android.media.MediaPlayer.create(context, com.adischat.app.R.raw.call_ringtone)?.apply {
        isLooping = true
        setAudioAttributes(
        android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build(),
        )
        start()
        }
        }
        }
        if (mode != AudioManager.RINGER_MODE_SILENT) {
        val pattern = longArrayOf(0, 700, 900)
        runCatching {
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }
        }
    }

    fun stop() {
        runCatching { ringbackPlayer?.stop(); ringbackPlayer?.release() }; ringbackPlayer = null
        runCatching { ringtonePlayer?.stop(); ringtonePlayer?.release() }; ringtonePlayer = null
        runCatching { vibrator?.cancel() }
    }
}

private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
