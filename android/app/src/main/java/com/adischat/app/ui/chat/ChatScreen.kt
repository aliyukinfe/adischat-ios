package com.adischat.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.view.ViewGroup
import com.adischat.app.FeatureFlags
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.aspectRatio
import com.adischat.app.ui.emoji.EmojiCategory
import com.adischat.app.ui.emoji.EmojiData
import com.adischat.app.ui.emoji.EmojiStore
import com.adischat.app.ui.emoji.applySkinTone
import com.adischat.app.ui.emoji.supportsSkinTone
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.data.model.AdisLinks
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.UserSummary
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.CallRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.data.preferences.WalletTransaction
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.gamepot.GamePotBannerHost
import android.widget.Toast
import androidx.compose.ui.graphics.SolidColor
import com.adischat.app.ui.channel.ChannelFeedScreen
import com.adischat.app.ui.calls.CallMode
import com.adischat.app.ui.calls.CallPhase
import com.adischat.app.ui.calls.CallScreen
import com.adischat.app.ui.calls.CallUiState
import com.adischat.app.ui.common.AdisVerifiedBadge
import com.adischat.app.ui.common.LinkifiedText
import com.adischat.app.ui.theme.*
import com.adischat.app.calls.WebRtcCallService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import com.adischat.app.ui.localization.tr

// All surfaces adapt via MaterialTheme.colorScheme; only brand constants live here.

private val BrandGreen      = Color(0xFF7CFF8A)   // premium neon green
private val BrandGreenBright  = Color(0xFF9DFFAA)   // soft neon highlight
private val BubbleGradStart   = Color(0xFF25D96F)   // sent gradient start
private val BubbleGradEnd     = Color(0xFF7CFF8A)   // sent gradient end
private val DeleteRed      = Color(0xFFEF4444)
private val ProfileBg      = Color(0xFF070A12)
private val ProfileCardBg     = Color(0xFF101722)
private val ProfilePurple     = Color(0xFF7C4DFF)
private val ProfileText       = Color(0xFFF8FAFC)
private val ProfileTextMuted  = Color(0xFF9AA4B2)

// Thread canvas (chat background)
private val CanvasLight = Color(0xFFF0F2F5)
private val CanvasDark  = Color(0xFF00100A)

// Received bubble
private val BubbleRecvDark  = Color(0xFF1B2228)
private val BubbleRecvLight = Color(0xFFFFFFFF)
private val BubbleRecvBorderDark  = Color(0x0FFFFFFF)  // rgba(255,255,255,0.06)
private val BubbleRecvBorderLight = Color(0x1A000000)


private val isInDarkTheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

private val brandAccent: Color
    @Composable get() = if (isInDarkTheme) BrandGreen else BrandGreen

// - - - Premium dark palette (2026 redesign of the Chats / Groups / Channels screen) - - -
private val AdisBg      = Color(0xFF0A0A0A)   // app background
private val AdisSurface = Color(0xFF121212)   // raised surfaces (dock, sheets)
private val AdisCard    = Color(0xFF171717)   // list cards
private val AdisBorder  = Color.White.copy(alpha = 0.06f)   // rgba(255,255,255,0.06)
private val AdisAccent  = Color(0xFF22C55E)   // primary accent / unread
private val AdisTextPri = Color(0xFFFFFFFF)
private val AdisTextSec = Color(0xFFA1A1AA)

/** Compact follower/member count, e.g. 1.2k, 3.4M. */
private fun formatCompactCount(n: Int): String = when {
    n >= 1_000_000 -> (n / 100_000 / 10.0).let { if (it % 1.0 == 0.0) "${it.toInt()}M" else "${it}M" }
    n >= 1_000     -> (n / 100 / 10.0).let { if (it % 1.0 == 0.0) "${it.toInt()}k" else "${it}k" }
    else -> n.toString()
}

/** Cleaned-up last-message preview with an optional leading media indicator. */
private data class ConvPreview(val icon: androidx.compose.ui.graphics.vector.ImageVector?, val text: String)

private fun conversationPreview(lastMessage: String): ConvPreview {
    val m = strippedReplyText(lastMessage).trim()
    return when {
        m.isBlank() -> ConvPreview(null, "Say hello")
        m.startsWith("Photo:", true) -> ConvPreview(Icons.Default.PhotoCamera, m.substringAfter(":").trim().ifBlank { "Photo" })
        m.startsWith("Video:", true) -> ConvPreview(Icons.Default.Videocam, m.substringAfter(":").trim().ifBlank { "Video" })
        m.startsWith("Voice:", true) -> ConvPreview(Icons.Default.Mic, m.substringAfter(":").trim().ifBlank { "Voice message" })
        m.startsWith("Attachment:", true) -> ConvPreview(Icons.Default.InsertDriveFile, m.substringAfter(":").trim().ifBlank { "File" })
        m.contains("maps", true) && m.contains("http", true) -> ConvPreview(Icons.Default.LocationOn, "Location")
        else -> ConvPreview(null, m)
    }
}

private val threadCanvas: Color
    @Composable get() = if (isInDarkTheme) CanvasDark else CanvasLight

private val bubbleReceivedColor: Color
    @Composable get() = if (isInDarkTheme) BubbleRecvDark else BubbleRecvLight

private val bubbleReceivedBorder: Color
    @Composable get() = if (isInDarkTheme) BubbleRecvBorderDark else BubbleRecvBorderLight

private enum class MessageTab { Chats, Channels, Groups }

// ENTRY POINT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    pendingAdisLink: AdisLinkTarget? = null,
    onAdisLinkConsumed: () -> Unit = {},
    pendingNotificationConversationId: String? = null,
    onNotificationConversationConsumed: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val vm      : ChatViewModel = viewModel()
    val uiState      by vm.uiState.collectAsStateWithLifecycle()
    val convs      by vm.conversations.collectAsStateWithLifecycle()
    val messages       by vm.messages.collectAsStateWithLifecycle()
    val sending      by vm.sending.collectAsStateWithLifecycle()
    val error      by vm.error.collectAsStateWithLifecycle()
    val query      by vm.searchQuery.collectAsStateWithLifecycle()
    val results      by vm.searchResults.collectAsStateWithLifecycle()
    val communityQuery by vm.communityQuery.collectAsStateWithLifecycle()
    val channels       by vm.channels.collectAsStateWithLifecycle()
    val groups      by vm.groups.collectAsStateWithLifecycle()
    val me      by vm.me.collectAsStateWithLifecycle()
    var refreshing     by remember { mutableStateOf(false) }
    val scope      = rememberCoroutineScope()
    val openMessageUrl: (String) -> Unit = { url ->
        AdisLinks.parse(url)?.let(vm::openAdisLink) ?: onOpenUrl(url)
    }

    BackHandler(enabled = uiState is ChatUiState.Thread || uiState is ChatUiState.NewChat) {
        vm.navigateBack()
    }

    error?.let { msg ->
        LaunchedEffect(msg) { delay(3_500); vm.clearError() }
    }

    LaunchedEffect(pendingAdisLink?.slug, pendingAdisLink?.type) {
        pendingAdisLink?.let {
        vm.openAdisLink(it)
        onAdisLinkConsumed()
        }
    }

    LaunchedEffect(pendingNotificationConversationId) {
        val id = pendingNotificationConversationId.orEmpty()
        if (id.isNotBlank()) {
        vm.openConversationByNotification(id)
        onNotificationConversationConsumed()
        }
    }

    // Let the FCM service know which conversation is on screen so it can suppress
    // a push for the chat the user is actively reading.
    val activeThreadId = (uiState as? ChatUiState.Thread)?.conversationId
    DisposableEffect(activeThreadId) {
        com.adischat.app.notifications.ActiveChat.conversationId = activeThreadId
        onDispose { com.adischat.app.notifications.ActiveChat.conversationId = null }
    }

    // Telegram-like read state: only mark messages read for the thread that is
    // actually open on screen. This lets sender status become seen after the
    // receiver opens the conversation, instead of when a push merely arrives.
    LaunchedEffect(activeThreadId, messages.lastOrNull()?.id) {
        if (activeThreadId != null) vm.markCurrentRead()
    }

    when (val s = uiState) {
        is ChatUiState.Loading -> LoadingScreen()
        is ChatUiState.Error -> ErrorScreen(s.message, vm::retryLoad)
        is ChatUiState.ConversationsList ->
        ConversationsListScreen(
        conversations      = convs,
        channels      = channels,
        groups      = groups,
        communityQuery      = communityQuery,
        userResults      = results,
        errorMsg      = error,
        isRefreshing      = refreshing,
        onRefresh      = {
        refreshing = true; vm.retryLoad()
        scope.launch { delay(650); refreshing = false }
        },
        onOpen      = vm::openConversation,
        onNewChat      = vm::navigateToNewChat,
        onChatQueryChange      = vm::onSearchQueryChanged,
        onSelectUser      = vm::startConversation,
        onCommunityQueryChange = vm::onCommunityQueryChanged,
        onOpenCommunity      = vm::openCommunity,
        onJoinCommunity      = vm::joinCommunity,
        onCreateCommunity      = vm::createCommunity,
        onDismissError      = vm::clearError,
        myUid      = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
        )
        is ChatUiState.NewChat ->
        NewChatScreen(
        query      = query,
        results       = results,
        onQueryChange = vm::onSearchQueryChanged,
        onSelectUser  = vm::startConversation,
        onBack      = vm::navigateBack,
        )
        is ChatUiState.Thread -> {
        val isDirectThread = !s.isSupport && !s.isCommunity && s.otherUserId.isNotBlank()
        val peerPresence by vm.peerPresence.collectAsStateWithLifecycle()
        LaunchedEffect(s.conversationId, isDirectThread, s.otherUserId) {
        vm.observePeerPresence(if (isDirectThread) s.otherUserId else "")
        }
        if (s.isCommunity && s.communityType == "channel") {
        ChannelFeedScreen(
        channelId      = s.conversationId,
        channelName      = s.otherUserName.ifBlank { "Channel" },
        channelPhoto      = s.otherUserPhotoUrl,
        channelDescription    = s.communityDescription,
        subscriberCount       = s.communityMemberCount,
        subscribed      = s.communityJoined,
        isAdmin      = s.communityOwnerUid == me?.uid || me?.role == "admin",
        myUid      = me?.uid ?: "",
        onBack      = vm::navigateBack,
        onJoinCommunity       = vm::joinCurrentCommunity,
        onLeaveCommunity      = vm::leaveCurrentCommunity,
        onUpdateCommunity     = vm::updateCurrentCommunity,
        onUploadCommunityPhoto= vm::uploadCommunityPhoto,
        onDeleteCommunity     = vm::deleteCurrentCommunity,
        onOpenUrl      = openMessageUrl,
        )
        } else {
        ConversationView(
        title      = s.otherUserName.ifBlank { "Chat" },
        subtitle      = when {
        s.isSupport   -> "We typically reply within minutes"
        s.isCommunity -> "Group chat"
        else -> presenceSubtitle(peerPresence, me?.lastSeenVisibility == "private")
        },
        peerOnline      = peerPresence?.online == true,
        photoUrl      = s.otherUserPhotoUrl,
        peerUid      = s.otherUserId,
        threadAccent       = if (s.isSupport) Amber else accentFor(s.conversationId),
        isSupportHeader    = s.isSupport,
        isCommunity      = s.isCommunity,
        threadId      = s.conversationId,
        channelId      = if (s.isCommunity) s.conversationId else "",
        communityOwnerUid  = s.communityOwnerUid,
        profileKind      = s.communityType,
        profileDescription = s.communityDescription,
        profileMemberCount = s.communityMemberCount,
        communityJoined = s.communityJoined,
        profileVerificationLevel = s.otherVerificationLevel,
        profileVerified = s.otherVerified,
        unreadCount      = s.unreadCount,
        messages      = messages,
        currentUid      = me?.uid ?: "",
        canManageCommunity = s.isCommunity && (s.communityOwnerUid == me?.uid || me?.role == "admin"),
        canSend      = !s.isCommunity || (s.communityJoined && s.communityType == "group") || s.communityOwnerUid == me?.uid || me?.role == "admin",
        sending      = sending,
        onBack      = vm::navigateBack,
        onSend      = { text, reply -> vm.sendMessage(text, reply) },
        onUpdateCommunity  = vm::updateCurrentCommunity,
        onUploadCommunityPhoto = vm::uploadCommunityPhoto,
        onDeleteCommunity  = vm::deleteCurrentCommunity,
        onJoinCommunity    = vm::joinCurrentCommunity,
        onLeaveCommunity   = vm::leaveCurrentCommunity,
        onReact      = vm::reactToMessage,
        onDelete      = vm::deleteMessage,
        onEditMessage      = vm::editMessage,
        onDeleteForMe      = vm::deleteMessageForMe,
        onDeleteForEveryone= vm::deleteMessageForEveryone,
        onPinMessage       = vm::pinMessage,
        onStarMessage      = vm::starMessage,
        onTranslateMessage = vm::translateMessage,
        onReportMessage    = { message -> vm.reportThread(message) },
        onThreadAction     = { action ->
        when (action) {
        "mute", "wallpaper", "disappearing" -> vm.updateThreadSetting(action)
        "clear" -> vm.clearCurrentThread()
        "export" -> vm.exportCurrentThread()
        "report" -> vm.reportThread()
        "block" -> vm.blockCurrentUser()
        else -> vm.clearError()
        }
        },
        onModerateMember   = vm::moderateGroupMember,
        onSendImage      = { uri, caption -> vm.sendImage(uri, caption) },
        onSendVideo      = { uri, caption -> vm.sendVideo(uri, caption) },
        onSendVoice      = vm::sendVoice,
        onRetryMessage     = vm::retryMessage,
        onMessagesShown    = vm::markCurrentRead,
        onOpenUrl      = openMessageUrl,
        )
        }
        }
    }
}

// LOADING & ERROR

@Composable
private fun LoadingScreen() {
    Box(
        modifier      = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment  = Alignment.Center,
    ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        CircularProgressIndicator(
        color       = MaterialTheme.colorScheme.primary,
        strokeWidth = 2.5.dp,
        modifier    = Modifier.size(38.dp),
        )
        Text(
        text  = "Connecting...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier      = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier      = Modifier.padding(36.dp),
        ) {
        Icon(
        imageVector      = Icons.Default.CloudOff,
        contentDescription = null,
        tint      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier      = Modifier.size(52.dp),
        )
        Text(
        text      = message,
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        )
        Button(
        onClick = onRetry,
        shape   = RoundedCornerShape(50.dp),
        colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
        Text(tr("Retry"), fontWeight = FontWeight.SemiBold)
        }
        }
    }
}

// CONVERSATIONS LIST

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsListScreen(
    conversations      : List<Conversation>,
    channels      : List<CommunityRoom>,
    groups      : List<CommunityRoom>,
    communityQuery      : String,
    userResults      : List<UserSummary>,
    errorMsg      : String?,
    isRefreshing      : Boolean,
    onRefresh      : () -> Unit,
    onOpen      : (Conversation) -> Unit,
    onNewChat      : () -> Unit,
    onChatQueryChange     : (String) -> Unit,
    onSelectUser      : (UserSummary) -> Unit,
    onCommunityQueryChange: (String) -> Unit,
    onOpenCommunity       : (CommunityRoom) -> Unit,
    onJoinCommunity       : (CommunityRoom) -> Unit,
    onCreateCommunity     : (String, String, String) -> Unit,
    onDismissError      : () -> Unit,
    myUid      : String = "",
) {
    var activeTab       by remember { mutableStateOf(MessageTab.Chats) }
    var searchQuery     by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var pinnedIdsCsv    by rememberSaveable { mutableStateOf("") }
    var hiddenIdsCsv    by rememberSaveable { mutableStateOf("") }
    var deleteTarget    by remember { mutableStateOf<Conversation?>(null) }
    val accent      = if (isInDarkTheme) AdisAccent else brandAccent

    // Payment notifications surface inside the Chats list (no separate bell).
    var payments by remember { mutableStateOf<List<WalletTransaction>>(emptyList()) }
    var showPaymentsChannel by remember { mutableStateOf(false) }
    // Tracks the newest transaction the user has already seen; unread badge clears once opened.
    var seenPaymentTs by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(isRefreshing) {
        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED) {
        runCatching { WalletRepository().getTransactions() }
        .getOrNull()?.getOrNull()
        ?.sortedByDescending { it.timestamp }
        ?.let { payments = it }
        } else {
        payments = emptyList()
        showPaymentsChannel = false
        }
    }

    if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED && showPaymentsChannel) {
        PaymentsChannelScreen(
        transactions = payments,
        onBack       = { showPaymentsChannel = false },
        )
    }

    val pinnedIds = remember(pinnedIdsCsv) { pinnedIdsCsv.csvToIdSet() }
    val hiddenIds = remember(hiddenIdsCsv) { hiddenIdsCsv.csvToIdSet() }
    fun savePinned(ids: Set<String>) { pinnedIdsCsv = ids.joinToString(",") }
    fun saveHidden(ids: Set<String>) { hiddenIdsCsv = ids.joinToString(",") }

    val visibleConversations = remember(conversations, searchQuery, hiddenIdsCsv) {
        val q = searchQuery.trim()
        val active = conversations.filter { it.id !in hiddenIds }
        if (q.isBlank()) active
        else active.filter { c ->
        c.otherUserName.contains(q, ignoreCase = true) ||
        c.lastMessage.contains(q, ignoreCase = true)
        }
    }
    val pinnedConvs = visibleConversations.filter { it.id in pinnedIds }
    val recentConvs = visibleConversations.filter { it.id !in pinnedIds }

    // People search: surface users not already in a conversation so you can start a new chat
    val peopleResults = remember(userResults, conversations, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
        val existing = conversations.map { it.otherUserId }.toSet()
        userResults.filter { it.uid !in existing }
        }
    }

    val activeSearch = if (activeTab == MessageTab.Chats) searchQuery else communityQuery
    // Global unread badges (Chats sum is server-backed; group/channel computed locally).
    val chatUnread    by com.adischat.app.notifications.UnreadCenter.chatUnread.collectAsStateWithLifecycle()
    val groupUnread   by com.adischat.app.notifications.UnreadCenter.groupUnread.collectAsStateWithLifecycle()
    val channelUnread by com.adischat.app.notifications.UnreadCenter.channelUnread.collectAsStateWithLifecycle()
    val groupMap      by com.adischat.app.notifications.UnreadCenter.groupPerRoom.collectAsStateWithLifecycle()
    val channelMap    by com.adischat.app.notifications.UnreadCenter.channelPerRoom.collectAsStateWithLifecycle()

    val rawTabRooms  = if (activeTab == MessageTab.Channels) channels else groups
    val tabRooms     = remember(rawTabRooms, communityQuery, groupMap, channelMap, activeTab) {
        val withCounts = rawTabRooms.map { room ->
        val c = (if (activeTab == MessageTab.Channels) channelMap else groupMap)[room.id] ?: 0
        if (c != room.unreadCount) room.copy(unreadCount = c) else room
        }
        if (communityQuery.isBlank()) withCounts.filter { it.joined } else withCounts
    }
    val listCount    = when (activeTab) {
        MessageTab.Chats    -> visibleConversations.size
        MessageTab.Channels -> tabRooms.size
        MessageTab.Groups   -> tabRooms.size
    }
    val listTitle = when (activeTab) {
        MessageTab.Chats    -> "Chats"
        MessageTab.Channels -> "Channels"
        MessageTab.Groups   -> "Groups"
    }

    if (showCreateSheet) {
        CreateCommunitySheet(
        type      = if (activeTab == MessageTab.Groups) "group" else "channel",
        onDismiss = { showCreateSheet = false },
        onCreate  = { type, name, desc ->
        showCreateSheet = false
        onCreateCommunity(type, name, desc)
        },
        )
    }
    deleteTarget?.let { conv ->
        AlertDialog(
        onDismissRequest = { deleteTarget = null },
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = DeleteRed) },
        title = { Text(tr("Delete chat?")) },
        text = { Text("This removes ${conv.otherUserName.ifBlank { "this contact" }} from your chat list on this phone.") },
        confirmButton = {
        TextButton(
        onClick = {
        saveHidden(hiddenIds + conv.id)
        savePinned(pinnedIds - conv.id)
        deleteTarget = null
        },
        ) { Text(tr("Delete"), color = DeleteRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text(tr("Cancel")) }
        },
        )
    }

    val communityMode = activeTab != MessageTab.Chats
    val screenBg = if (isInDarkTheme) AdisBg else MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = screenBg,
        snackbarHost = {
        errorMsg?.let {
        Snackbar(
        action = { TextButton(onClick = onDismissError) { Text(tr("OK"), color = accent) } },
        ) { Text(it) }
        }
        },
    ) { innerPadding ->
        PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        modifier     = Modifier.fillMaxSize().padding(innerPadding),
        ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

        ListScreenHeader(
        title = listTitle,
        count = listCount,
        accent = accent,
        activeTab = activeTab,
        onBack = { activeTab = MessageTab.Chats },
        onAction = { if (activeTab == MessageTab.Chats) onNewChat() else showCreateSheet = true },
        )

        ChatSearchBar(
        value      = activeSearch,
        accent      = accent,
        onValueChange = {
        if (activeTab == MessageTab.Chats) {
        searchQuery = it
        onChatQueryChange(it)   // also search people to start new chats
        } else onCommunityQueryChange(it)
        },
        placeholder   = when (activeTab) {
        MessageTab.Chats    -> "Search messages and people..."
        MessageTab.Channels -> "Search channels..."
        MessageTab.Groups   -> "Search groups..."
        },
        )

        Spacer(Modifier.height(2.dp))

        when {
        activeTab != MessageTab.Chats ->
        CommunityListPanel(
        rooms    = tabRooms,
        type     = if (activeTab == MessageTab.Channels) "channel" else "group",
        query    = communityQuery,
        onOpen   = onOpenCommunity,
        onJoin   = onJoinCommunity,
        onCreate = { showCreateSheet = true },
        )
        visibleConversations.isEmpty() && peopleResults.isEmpty() && searchQuery.isNotBlank() ->
        EmptyChatState(isSearching = true, accent = accent)
        else ->
        LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp),
        ) {
        // Payments channel entry (all transaction notifications live here)
        // Hidden while the wallet feature is paused.
        if (com.adischat.app.FeatureFlags.WALLET_ENABLED && searchQuery.isBlank()) {
        item(key = "payments-channel") {
        PaymentsChannelRow(
        latest = payments.firstOrNull(),
        unread = payments.count { it.timestamp > seenPaymentTs },
        accent = accent,
        onClick = {
        seenPaymentTs = payments.maxOfOrNull { it.timestamp } ?: seenPaymentTs
        showPaymentsChannel = true
        },
        )
        InsetDivider()
        }
        }
        // Pinned section
        if (pinnedConvs.isNotEmpty()) {
        item {
        SectionLabel(
        text  = "Pinned",
        icon  = Icons.Default.PushPin,
        accent = accent,
        )
        }
        items(pinnedConvs, key = { "pin-${it.id}" }) { conv ->
        ConversationRow(
        conv     = conv,
        isPinned = true,
        accent   = accent,
        onClick  = { onOpen(conv) },
        onTogglePin = { savePinned(pinnedIds - conv.id) },
        onDelete = { deleteTarget = conv },
        )
        }
        }
        // Recent section
        if (recentConvs.isNotEmpty()) {
        item {
        SectionLabel(
        text  = "Recent",
        icon  = Icons.Default.ChatBubbleOutline,
        accent = accent,
        )
        }
        items(recentConvs, key = { "conv-${it.id}" }) { conv ->
        ConversationRow(
        conv     = conv,
        isPinned = false,
        accent   = accent,
        onClick  = { onOpen(conv) },
        onTogglePin = { savePinned(pinnedIds + conv.id) },
        onDelete = { deleteTarget = conv },
        )
        }
        }
        // People section (start a new chat from search)
        if (peopleResults.isNotEmpty()) {
        item {
        SectionLabel(
        text   = "People",
        icon   = Icons.Default.PersonSearch,
        accent = accent,
        )
        }
        items(peopleResults, key = { "user-${it.uid}" }) { user ->
        UserSearchRow(user, accent = accent) { onSelectUser(user) }
        }
        }
        }
        }
        }

        TabDock(
        activeTab   = activeTab,
        onTabChange = { activeTab = it },
        accent      = accent,
        chatBadge    = chatUnread,
        channelBadge = channelUnread,
        groupBadge   = groupUnread,
        modifier    = Modifier.align(Alignment.BottomCenter),
        )
        }
    }
}


@Composable
private fun ListScreenHeader(
    title: String,
    count: Int,
    accent: Color,
    activeTab: MessageTab,
    onBack: () -> Unit,
    onAction: () -> Unit,
) {
    val actionIcon = when (activeTab) {
        MessageTab.Chats -> Icons.Default.Edit
        MessageTab.Channels -> Icons.Default.Add
        MessageTab.Groups -> Icons.Default.GroupAdd
    }
    val actionLabel = when (activeTab) {
        MessageTab.Chats -> "New chat"
        MessageTab.Channels -> "Create channel"
        MessageTab.Groups -> "Create group"
    }
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
        Text(
        text = title,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onBackground,
        letterSpacing = (-0.5).sp,
        )
        if (count > 0) {
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(
        text = "$count active",
        fontSize = 13.sp,
        color = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        )
        }
        }
        }
        Box(
        modifier = Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (isInDarkTheme) AdisCard else Color(0xFF111111))
        .border(1.dp, if (isInDarkTheme) AdisBorder else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
        .clickable(onClick = onAction),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector = actionIcon,
        contentDescription = actionLabel,
        tint = accent,
        modifier = Modifier.size(20.dp),
        )
        }
    }
}
@Composable
private fun ChatSearchBar(value: String, onValueChange: (String) -> Unit, placeholder: String, accent: Color = brandAccent) {
    Surface(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .height(44.dp)
        .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(18.dp),
        color = if (isInDarkTheme) AdisCard else Color.White,
        border = BorderStroke(1.dp, if (isInDarkTheme) AdisBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
    ) {
        Row(
        modifier = Modifier.padding(start = 15.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = Color(0xFF8D95A3),
        modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        ),
        modifier = Modifier.weight(1f),
        cursorBrush = SolidColor(accent),
        decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) {
        Text(
        text = placeholder,
        fontSize = 14.sp,
        color = Color(0xFF8D95A3),
        maxLines = 1,
        )
        }
        inner()
        }
        },
        )
        if (value.isNotBlank()) {
        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
        Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Clear",
        tint = Color(0xFF8D95A3),
        modifier = Modifier.size(15.dp),
        )
        }
        }
        }
    }
}
@Composable
private fun SectionLabel(
    text  : String,
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
) {
    Row(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(
        text      = text.uppercase(),
        fontSize = 12.sp,
        fontWeight    = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 82.dp, end = 20.dp),
        thickness = 0.5.dp,
        color     = if (isInDarkTheme) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.08f),
    )
}

@Composable
private fun EmptyChatState(isSearching: Boolean, accent: Color) {
    Box(
        modifier      = Modifier.fillMaxSize().padding(bottom = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier      = Modifier.padding(32.dp),
        ) {
        Box(
        modifier      = Modifier
        .size(78.dp)
        .clip(CircleShape)
        .background(accent.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector      = if (isSearching) Icons.Default.Search else Icons.Default.Forum,
        contentDescription = null,
        tint      = accent,
        modifier      = Modifier.size(36.dp),
        )
        }
        Spacer(Modifier.height(4.dp))
        Text(
        text       = if (isSearching) "No results found" else "No conversations yet",
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
        text      = if (isSearching) "Try a different name or message"
        else "Tap the compose button to start chatting",
        fontSize = 17.sp,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        )
        }
    }
}


/**
 * Full-screen "Forward to" picker that looks exactly like the Chats list (same cards,
 * search, RECENT header and bottom Chats/Channels/Groups dock) - - used for both forward
 * and share so the user lands on a familiar screen instead of a separate sheet.
 */
@Composable
fun ForwardScreen(
    messageText: String,
    onDismiss: () -> Unit,
    sourceType: String = "text",
    mediaUrl: String = "",
    mediaMimeType: String = "",
    durationMs: Long = 0L,
) {
    val ctx      = LocalContext.current
    val scope    = rememberCoroutineScope()
    val chatRepo = remember { ChatRepository() }
    val authRepo = remember { AuthRepository() }

    var me       by remember { mutableStateOf<AppUser?>(null) }
    var directs  by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var chans    by remember { mutableStateOf<List<CommunityRoom>>(emptyList()) }
    var grps     by remember { mutableStateOf<List<CommunityRoom>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }
    var sending  by remember { mutableStateOf(false) }
    var query    by remember { mutableStateOf("") }
    var tab      by remember { mutableStateOf(MessageTab.Chats) }
    val selected = remember { mutableStateListOf<String>() }

    val accent    = if (isInDarkTheme) AdisAccent else brandAccent
    val bg      = if (isInDarkTheme) AdisBg else MaterialTheme.colorScheme.background
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onBackground

    // Collect each list CONTINUOUSLY (same source the Chats list uses) instead of a
    // one-shot .first()+timeout - - a slow/cold first poll on mobile data was timing out
    // and falling back to empty, which showed "No chats yet" even when chats existed.
    LaunchedEffect(Unit) {
        me = runCatching { authRepo.getCurrentAppUser() }.getOrNull()
    }
    LaunchedEffect(Unit) {
        runCatching {
        chatRepo.observeAllConversations().collect { directs = it; loading = false }
        }.onFailure { loading = false }
    }
    LaunchedEffect(Unit) {
        runCatching { chatRepo.observeCommunities("channel", "").collect { chans = it } }
    }
    LaunchedEffect(Unit) {
        runCatching { chatRepo.observeCommunities("group", "").collect { grps = it } }
    }
    // Safety: never spin forever if the conversations flow never emits.
    LaunchedEffect(Unit) { delay(7000); loading = false }

    fun forward() {
        if (selected.isEmpty() || sending) return
        sending = true
        scope.launch {
        val u = me
        selected.toList().forEach { id ->
        val isCommunity = chans.any { it.id == id } || grps.any { it.id == id }
        chatRepo.forwardMessage(
        targetId = id, isCommunity = isCommunity,
        senderName = u?.name ?: "", senderPhotoUrl = u?.photoUrl ?: "",
        text = messageText, type = sourceType, mediaUrl = mediaUrl,
        mediaMimeType = mediaMimeType, durationMs = durationMs,
        )
        }
        sending = false
        Toast.makeText(ctx, "Forwarded", Toast.LENGTH_SHORT).show()
        onDismiss()
        }
    }

    val q = query.trim()
    val fDirects = directs.sortedByDescending { it.lastMessageAt }
        .filter { q.isBlank() || it.otherUserName.contains(q, true) || it.lastMessage.contains(q, true) }
    val fChans = chans.filter { q.isBlank() || it.name.contains(q, true) }
    val fGrps  = grps.filter { q.isBlank() || it.name.contains(q, true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
        // Header (mirrors the Chats list title block)
        Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onDismiss) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = titleColor)
        }
        Column(Modifier.weight(1f)) {
        Text("Forward to", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = titleColor, letterSpacing = (-0.5).sp)
        Text(
        if (selected.isEmpty()) "Choose chats, groups, or channels" else "${selected.size} selected",
        fontSize = 13.sp,
        color = if (selected.isEmpty()) secondary else accent,
        fontWeight = FontWeight.Medium,
        )
        }
        }
        ChatSearchBar(
        value = query, accent = accent, onValueChange = { query = it },
        placeholder = when (tab) {
        MessageTab.Chats    -> "Search messages and people..."
        MessageTab.Channels -> "Search channels..."
        MessageTab.Groups   -> "Search groups..."
        },
        )
        Spacer(Modifier.height(2.dp))

        if (loading) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        } else {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 180.dp)) {
        when (tab) {
        MessageTab.Chats -> {
        item { SectionLabel("Recent", Icons.Default.ChatBubbleOutline, accent) }
        if (fDirects.isEmpty()) item { ForwardEmpty(if (q.isNotBlank()) "No matches found" else "No chats yet", secondary) }
        else items(fDirects, key = { "c_${it.id}" }) { conv ->
        ForwardConvRow(conv, selected.contains(conv.id), accent) {
        if (selected.contains(conv.id)) selected.remove(conv.id) else selected.add(conv.id)
        }
        }
        }
        MessageTab.Channels -> {
        if (fChans.isEmpty()) item { ForwardEmpty(if (q.isNotBlank()) "No matches found" else "No channels yet", secondary) }
        else items(fChans, key = { "ch_${it.id}" }) { room ->
        ForwardCommRow(room, selected.contains(room.id), accent) {
        if (selected.contains(room.id)) selected.remove(room.id) else selected.add(room.id)
        }
        }
        }
        MessageTab.Groups -> {
        if (fGrps.isEmpty()) item { ForwardEmpty(if (q.isNotBlank()) "No matches found" else "No groups yet", secondary) }
        else items(fGrps, key = { "g_${it.id}" }) { room ->
        ForwardCommRow(room, selected.contains(room.id), accent) {
        if (selected.contains(room.id)) selected.remove(room.id) else selected.add(room.id)
        }
        }
        }
        }
        }
        }
        }

        // Floating Forward bar (appears above the dock once something is selected)
        androidx.compose.animation.AnimatedVisibility(
        visible  = selected.isNotEmpty(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 92.dp),
        enter    = fadeIn() + slideInVertically { it / 2 },
        exit     = fadeOut() + slideOutVertically { it / 2 },
        ) {
        Row(
        Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(if (isInDarkTheme) AdisCard else MaterialTheme.colorScheme.surface)
        .border(1.dp, AdisBorder, RoundedCornerShape(20.dp))
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Text("${selected.size} selected", color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = { selected.clear() }, enabled = !sending) {
        Text("Clear", color = secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
        onClick = { forward() }, enabled = !sending,
        modifier = Modifier.height(44.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        ) {
        if (sending) {
        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Forward", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        }
        }
        }

        TabDock(activeTab = tab, onTabChange = { tab = it }, accent = accent, modifier = Modifier.align(Alignment.BottomCenter))
        }
        }
    }
}

@Composable
private fun ForwardEmpty(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun ForwardConvRow(conv: Conversation, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    val preview   = remember(conv.lastMessage) { conversationPreview(conv.lastMessage) }
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = if (isInDarkTheme) { if (checked) accent.copy(alpha = 0.14f) else AdisCard }
        else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(20.dp)).background(cardBg)
        .then(if (checked) Modifier.border(1.5.dp, accent, RoundedCornerShape(20.dp)) else Modifier)
        .clickable(onClick = onToggle)
        .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(conv.otherUserPhotoUrl, initialsOf(conv.otherUserName), 54.dp, accentFor(conv.id))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
        Text(conv.otherUserName.ifBlank { "Unknown" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        color = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        if (preview.icon != null) {
        Icon(preview.icon, null, tint = secondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        }
        Text(preview.text, fontSize = 13.5.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        }
        Spacer(Modifier.width(8.dp))
        ForwardCheck(checked, accent)
    }
}

@Composable
private fun ForwardCommRow(room: CommunityRoom, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (room.type == "channel") "followers" else "members"
    val sub = buildString {
        if (room.memberCount > 0) append("${formatCompactCount(room.memberCount)} $label")
        val p = strippedReplyText(room.lastPostPreview.ifBlank { room.lastMessage })
        if (p.isNotBlank()) { if (isNotEmpty()) append("  -  "); append(p) }
    }.ifBlank { if (room.type == "channel") "Channel" else "Group" }
    val cardBg = if (isInDarkTheme) { if (checked) accent.copy(alpha = 0.14f) else AdisCard }
        else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(20.dp)).background(cardBg)
        .then(if (checked) Modifier.border(1.5.dp, accent, RoundedCornerShape(20.dp)) else Modifier)
        .clickable(onClick = onToggle)
        .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(room.photoUrl, initialsOf(room.name), 54.dp, accentFor(room.id))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
        Text(room.name.ifBlank { if (room.type == "channel") "Channel" else "Group" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        color = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text(sub, fontSize = 13.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        ForwardCheck(checked, accent)
    }
}

@Composable
private fun ForwardCheck(checked: Boolean, accent: Color) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
        .background(if (checked) accent else Color.Transparent)
        .border(1.5.dp, if (checked) accent else Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv    : Conversation,
    isPinned: Boolean,
    accent  : Color,
    onClick : () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val unread = conv.unreadCount > 0
    var menuOpen by remember { mutableStateOf(false) }
    val preview = remember(conv.lastMessage) { conversationPreview(conv.lastMessage) }
    val cardBg = if (isInDarkTheme) {
        if (unread) accent.copy(alpha = 0.10f) else AdisCard
    } else {
        if (unread) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    }
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(cardBg)
        .then(if (unread) Modifier.border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp)) else Modifier)
        .combinedClickable(
        onClick = onClick,
        onLongClick = { menuOpen = true },
        )
        .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(
        photoUrl = conv.otherUserPhotoUrl,
        initials = initialsOf(conv.otherUserName),
        size = 54.dp,
        accent = accentFor(conv.id),
        )

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Text(
        text = conv.otherUserName.ifBlank { "Unknown" },
        fontSize = 16.sp,
        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
        color = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
        )
        if (conv.otherUserVerificationLevel >= 3 || conv.otherUserVerified) {
        Spacer(Modifier.width(5.dp))
        UserVerifiedBadge(Modifier.size(15.dp))
        }
        }
        Spacer(Modifier.width(8.dp))
        Text(
        text = chatTime(conv.lastMessageAt),
        fontSize = 12.sp,
        color = if (unread) accent else secondary,
        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
        )
        }
        Spacer(Modifier.height(5.dp))
        Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        if (preview.icon != null) {
        Icon(
        imageVector = preview.icon,
        contentDescription = null,
        tint = if (unread) accent else secondary,
        modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        }
        Text(
        text = preview.text,
        fontSize = 13.5.sp,
        color = if (unread) (if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onSurface) else secondary,
        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when {
        unread -> UnreadBadge(count = conv.unreadCount, accent = accent)
        isPinned -> Icon(
        imageVector = Icons.Default.PushPin,
        contentDescription = null,
        tint = secondary.copy(alpha = 0.72f),
        modifier = Modifier.size(14.dp),
        )
        }
        }
        }
    }
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        DropdownMenuItem(
        text = { Text(if (isPinned) "Unpin chat" else "Pin chat") },
        leadingIcon = { Icon(Icons.Default.PushPin, null, tint = accent) },
        onClick = {
        menuOpen = false
        onTogglePin()
        },
        )
        DropdownMenuItem(
        text = { Text(tr("Delete chat"), color = DeleteRed) },
        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = DeleteRed) },
        onClick = {
        menuOpen = false
        onDelete()
        },
        )
    }
}

@Composable
private fun PaymentNotificationRow(tx: WalletTransaction) {
    val incoming = tx.amount >= 0
    val tint     = if (incoming) Color(0xFF18C964) else Color(0xFFFF6B6B)
    val sign     = if (incoming) "+" else "-"
    val title    = tx.title.ifBlank { if (incoming) "Payment received" else "Payment sent" }
    val sub      = when {
        tx.counterpartyName.isNotBlank() -> tx.counterpartyName
        tx.note.isNotBlank() -> tx.note
        else -> tx.status.ifBlank { "Wallet activity" }
    }

    Row(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        Modifier.size(54.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector      = if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
        contentDescription = null,
        tint      = tint,
        modifier      = Modifier.size(24.dp),
        )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text       = title,
        fontSize   = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis,
        modifier   = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
        text       = chatTime(tx.timestamp),
        fontSize   = 15.sp,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text     = sub,
        fontSize = 16.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
        text       = "$sign${tx.currency} ${"%,.2f".format(kotlin.math.abs(tx.amount))}",
        fontSize   = 16.sp,
        fontWeight = FontWeight.Bold,
        color      = tint,
        )
        }
        }
    }
}


@Composable
private fun PaymentsChannelRow(
    latest: WalletTransaction?,
    unread: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 7.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(if (isInDarkTheme) Color.White.copy(alpha = 0.035f) else Color.White)
        .border(1.dp, if (isInDarkTheme) Color.White.copy(alpha = 0.055f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.AccountBalanceWallet, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        tr("Payments"),
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
        )
        latest?.let {
        Text(chatTime(it.timestamp), fontSize = 11.sp, color = Color(0xFF7F8795), fontWeight = FontWeight.Medium)
        }
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text = latest?.let { paymentSummary(it) } ?: tr("Transaction notifications appear here"),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
        )
        if (unread > 0) {
        Spacer(Modifier.width(8.dp))
        UnreadBadge(count = unread, accent = accent)
        }
        }
        }
    }
}
private fun paymentSummary(tx: WalletTransaction): String {
    val sign = if (tx.amount >= 0) "+" else "-"
    val who  = tx.counterpartyName.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
    return "$sign${tx.currency} ${"%,.2f".format(kotlin.math.abs(tx.amount))}$who"
}

@Composable
private fun PaymentsChannelScreen(
    transactions: List<WalletTransaction>,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
        tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF18C964).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF18C964), modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
        Text(tr("Payments"), fontSize = 19.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface)
        Text(tr("Transaction notifications"), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        InsetDivider()
        if (transactions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(tr("No payment notifications yet"),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        }
        } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(transactions, key = { "ptx-${it.id}" }) { tx ->
        PaymentNotificationRow(tx)
        InsetDivider()
        }
        }
        }
        }
        }
    }
}


@Composable
private fun CommunityListPanel(
    rooms   : List<CommunityRoom>,
    type    : String,
    query   : String,
    onOpen  : (CommunityRoom) -> Unit,
    onJoin  : (CommunityRoom) -> Unit,
    onCreate: () -> Unit,
) {
    val accent = brandAccent

    if (rooms.isEmpty()) {
        Box(
        modifier      = Modifier.fillMaxSize().padding(bottom = 96.dp),
        contentAlignment = Alignment.Center,
        ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier      = Modifier.padding(32.dp),
        ) {
        Box(
        modifier      = Modifier.size(72.dp).clip(CircleShape).background(accent.copy(0.10f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector      = if (type == "channel") Icons.Default.Campaign else Icons.Default.Groups,
        contentDescription = null,
        tint      = accent,
        modifier      = Modifier.size(32.dp),
        )
        }
        Text(
        text       = if (query.isBlank()) "No ${type}s yet" else "No ${type}s found",
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
        text      = if (query.isBlank()) "Create the first $type for AdisChat" else "Try another name",
        fontSize = 17.sp,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        )
        Button(
        onClick = onCreate,
        shape   = RoundedCornerShape(50.dp),
        colors  = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Create ${type.replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Bold)
        }
        }
        }
        return
    }

    val sorted = remember(rooms) {
        rooms.sortedByDescending { it.lastPostAt.takeIf { time -> time > 0L } ?: it.lastMessageAt }
    }
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 112.dp),
    ) {
        items(sorted, key = { it.id }) { room ->
        PremiumCommunityRow(room = room, onOpen = { onOpen(room) }, onJoin = { onJoin(room) }, accent = accent)
        }
    }
}

@Composable
private fun PremiumCommunityRow(
    room  : CommunityRoom,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    accent: Color,
) {
    val activityAt = room.lastPostAt.takeIf { it > 0L } ?: room.lastMessageAt
    val preview = channelPreview(room)
    val unread = room.unreadCount > 0
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant
    val countLabel = if (room.type == "channel") "followers" else "members"
    val metaText = buildString {
        if (room.memberCount > 0) append("${formatCompactCount(room.memberCount)} $countLabel")
        if (preview.isNotBlank() && !preview.equals("No posts yet", true)) {
        if (isNotEmpty()) append("  -  ")
        append(preview)
        } else if (isEmpty()) append(preview)
    }

    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(
        if (isInDarkTheme) { if (unread) accent.copy(alpha = 0.10f) else AdisCard }
        else MaterialTheme.colorScheme.surface,
        )
        .clickable { onOpen() }
        .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
        ) {
        ChatAvatar(room.photoUrl, initialsOf(room.name), 48.dp, accent)
        }
        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text = room.name,
        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
        )
        if (room.verified) {
        Spacer(Modifier.width(5.dp))
        ChannelVerifiedBadge(Modifier.size(18.dp))
        }
        }
        Spacer(Modifier.height(4.dp))
        Text(
        text = metaText.ifBlank { preview },
        fontSize = 13.sp,
        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
        color = if (unread && isInDarkTheme) AdisTextPri else secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        }
        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        Text(
        text = channelActivityTime(activityAt),
        fontSize = 13.sp,
        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
        color = if (unread) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        when {
        !room.joined -> ChannelNotificationStatus(muted = true, label = "Join", accent = accent, onClick = onJoin)
        room.unreadCount > 0 -> UnreadPostBadge(count = room.unreadCount, accent = accent)
        else -> ChannelNotificationStatus(muted = false, label = "On", accent = accent)
        }
        }
    }
}

@Composable
private fun ChannelNotificationStatus(
    muted: Boolean,
    label: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
        .clip(CircleShape)
        .background(if (muted) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.12f))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
        imageVector = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
        contentDescription = label,
        tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else accent,
        modifier = Modifier.size(13.dp),
        )
        Text(
        text = label,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChannelVerifiedBadge(modifier: Modifier = Modifier) {
    UserVerifiedBadge(modifier = modifier, contentDescription = "Verified channel")
}



@Composable
private fun UserVerifiedBadge(
    modifier: Modifier = Modifier,
    contentDescription: String = "Verified user",
) {
    AdisVerifiedBadge(modifier = modifier, contentDescription = contentDescription)
}
@Composable
private fun UnreadPostBadge(count: Int, accent: Color) {
    Box(
        modifier = Modifier
        .sizeIn(minWidth = 24.dp, minHeight = 24.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(NotifRed)
        .padding(horizontal = if (count > 9) 7.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
        text = if (count > 99) "99+" else count.toString(),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        )
    }
}

@Composable
private fun JoinMiniButton(accent: Color, onJoin: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Text(
        "Join",
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
        .clickable(onClick = onJoin)
        .padding(horizontal = 13.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommunitySheet(
    type     : String,
    onDismiss: () -> Unit,
    onCreate : (String, String, String) -> Unit,
) {
    var name      by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val accent      = brandAccent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        Text(
        text       = "Create ${type.replaceFirstChar { it.uppercase() }}",
        fontSize = 25.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
        value      = name,
        onValueChange = { name = it },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(tr("Name")) },
        singleLine    = true,
        shape      = RoundedCornerShape(16.dp),
        colors      = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = accent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        )
        OutlinedTextField(
        value      = description,
        onValueChange = { description = it },
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(tr("Description (optional)")) },
        minLines      = 2,
        shape      = RoundedCornerShape(16.dp),
        colors      = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = accent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        )
        Button(
        enabled  = name.trim().length >= 2,
        onClick  = { onCreate(type, name, description) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(18.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) {
        Text(tr("Create and Join"), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        }
    }
}


@Composable
private fun TabDock(
    activeTab  : MessageTab,
    onTabChange: (MessageTab) -> Unit,
    accent     : Color,
    chatBadge    : Int = 0,
    channelBadge : Int = 0,
    groupBadge   : Int = 0,
    modifier   : Modifier = Modifier,
) {
    Box(
        modifier = modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 0.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
        modifier = Modifier
        .fillMaxWidth()
        .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.40f), spotColor = Color.Black.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(26.dp),
        color = AdisSurface,
        border = BorderStroke(1.dp, AdisBorder),
        tonalElevation = 0.dp,
        ) {
        Row(
        modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        ) {
        listOf(
        Triple(MessageTab.Chats, Icons.Default.ChatBubbleOutline, "Chats"),
        Triple(MessageTab.Channels, Icons.Default.Campaign, "Channels"),
        Triple(MessageTab.Groups, Icons.Default.Groups, "Groups"),
        ).forEach { (tab, icon, label) ->
        val active = activeTab == tab
        val badge = when (tab) {
        MessageTab.Chats    -> chatBadge
        MessageTab.Channels -> channelBadge
        MessageTab.Groups   -> groupBadge
        }
        Column(
        modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .clip(RoundedCornerShape(20.dp))
        .clickable { onTabChange(tab) }
        .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        ) {
        Box(Modifier.height(7.dp), contentAlignment = Alignment.Center) {
        if (active) Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        }
        Box(contentAlignment = Alignment.Center) {
        Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (active) accent else AdisTextSec.copy(alpha = 0.7f),
        modifier = Modifier.size(23.dp),
        )
        if (badge > 0) {
        NotificationPill(
        count = badge,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = 13.dp, y = (-7).dp),
        )
        }
        }
        Spacer(Modifier.height(3.dp))
        Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) accent else AdisTextSec.copy(alpha = 0.7f),
        )
        }
        }
        }
        }
    }
}
// NEW CHAT SCREEN

@Composable
private fun NewChatScreen(
    query      : String,
    results      : List<UserSummary>,
    onQueryChange: (String) -> Unit,
    onSelectUser : (UserSummary) -> Unit,
    onBack       : () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accent   = brandAccent

    Column(
        Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding(),
    ) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onBack) {
        Icon(
        imageVector      = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint      = MaterialTheme.colorScheme.onSurface,
        )
        }
        Text(
        text       = "New Message",
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        color      = MaterialTheme.colorScheme.onSurface,
        )
        }
        }

        // Search field
        OutlinedTextField(
        value      = query,
        onValueChange   = onQueryChange,
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
        shape      = RoundedCornerShape(24.dp),
        placeholder     = { Text(tr("Search by phone, username or name...")) },
        leadingIcon     = { Icon(Icons.Default.Search, null) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors      = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = accent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.35f),
        ),
        )

        when {
        query.length < 2 ->
        Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Icon(
        imageVector      = Icons.Default.PersonSearch,
        contentDescription = null,
        tint      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
        modifier      = Modifier.size(56.dp),
        )
        Text(
        text      = "Enter a phone number, username,\nor name to find someone",
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        }
        results.isEmpty() ->
        Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
        text     = "No users found for \"$query\"",
        fontSize = 17.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        else ->
        LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        items(results, key = { it.uid }) { user ->
        UserSearchRow(user, accent = accent) {
        keyboard?.hide()
        onSelectUser(user)
        }
        }
        }
        }
    }
}

@Composable
private fun UserSearchRow(user: UserSummary, accent: Color, onClick: () -> Unit) {
    Row(
        modifier      = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(user.photoUrl, initialsOf(user.name.ifBlank { user.username }), 48.dp, accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text       = user.name.ifBlank { user.username.ifBlank { "Unknown" } },
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color      = MaterialTheme.colorScheme.onSurface,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis,
        modifier   = Modifier.weight(1f, fill = false),
        )
        if (user.verificationLevel >= 3 || user.verified) {
        Spacer(Modifier.width(5.dp))
        UserVerifiedBadge(Modifier.size(18.dp))
        }
        }
        val sub = listOfNotNull(
        user.username.takeIf { it.isNotBlank() }?.let { "@$it" },
        user.phone.takeIf { it.isNotBlank() },
        ).joinToString(" - ")
        if (sub.isNotBlank()) {
        Text(sub, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        Icon(
        imageVector      = Icons.Default.ChevronRight,
        contentDescription = null,
        tint      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.38f),
        modifier      = Modifier.size(18.dp),
        )
    }
}

// CONVERSATION THREAD

@Composable
private fun ConversationView(
    title      : String,
    subtitle      : String,
    peerOnline      : Boolean = false,
    photoUrl      : String,
    peerUid      : String = "",
    threadAccent       : Color,
    isSupportHeader    : Boolean,
    isCommunity      : Boolean,
    threadId      : String,      // stable conversation id for saved scroll state
    channelId      : String,      // empty for direct messages
    communityOwnerUid  : String,      // empty for direct messages
    profileKind      : String,
    profileDescription : String,
    profileMemberCount : Int,
    communityJoined    : Boolean = false,
    profileVerificationLevel: Int = 0,
    profileVerified    : Boolean = false,
    unreadCount      : Int = 0,
    messages      : List<Message>,
    currentUid      : String,
    canManageCommunity : Boolean,
    canSend      : Boolean,
    sending      : Boolean,
    onBack      : () -> Unit,
    onSend      : (String, Message?) -> Unit,
    onUpdateCommunity      : (String, String, String) -> Unit,
    onUploadCommunityPhoto : (android.net.Uri, (String?) -> Unit) -> Unit,
    onDeleteCommunity  : () -> Unit,
    onJoinCommunity    : () -> Unit,
    onLeaveCommunity   : () -> Unit,
    onReact      : (Message, String) -> Unit,
    onDelete      : (Message) -> Unit,
    onEditMessage      : (Message, String) -> Unit,
    onDeleteForMe      : (Message) -> Unit,
    onDeleteForEveryone: (Message) -> Unit,
    onPinMessage       : (Message, Boolean) -> Unit,
    onStarMessage      : (Message, Boolean) -> Unit,
    onTranslateMessage : (Message) -> Unit,
    onReportMessage    : (Message) -> Unit,
    onThreadAction     : (String) -> Unit,
    onModerateMember   : (String, String) -> Unit,
    onSendImage      : (android.net.Uri, String) -> Unit,
    onSendVideo      : (android.net.Uri, String) -> Unit,
    onSendVoice      : (File, Long) -> Unit,
    onRetryMessage     : (Message) -> Unit,
    onMessagesShown    : () -> Unit,
    onOpenUrl      : (String) -> Unit,
) {
    val listState     = remember(threadId) { androidx.compose.foundation.lazy.LazyListState() }
    val scope      = rememberCoroutineScope()
    val context       = LocalContext.current
    val clipboard     = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptic      = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showProfile   by remember { mutableStateOf(false) }
    var forwardText   by remember { mutableStateOf<String?>(null) }
    var forwardMedia  by remember { mutableStateOf<Message?>(null) }  // source message for media forwards
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var memberSheet   by remember { mutableStateOf<Message?>(null) }
    var replyTo       by remember { mutableStateOf<Message?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var deleteConfirmMessage by remember { mutableStateOf<Message?>(null) }
    var deleteConfirmEveryone by remember { mutableStateOf(false) }
    var searchOpen    by remember { mutableStateOf(false) }
    var chatSearch    by remember { mutableStateOf("") }
    var pinnedMessage by remember { mutableStateOf<Message?>(null) }
    var starredIdsCsv by rememberSaveable { mutableStateOf("") }
    var hiddenIdsCsv  by rememberSaveable { mutableStateOf("") }
    var selectedIdsCsv by rememberSaveable { mutableStateOf("") }
    val canvas      = threadCanvas
    val otherTyping   = false
    val density       = LocalDensity.current
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    var bottomOverlayPadding by remember { mutableStateOf(0.dp) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val bottomContentPaddingRaw = with(density) { bottomBarHeightPx.toDp() } + bottomOverlayPadding + 18.dp
    val bottomContentPadding by animateDpAsState(
        targetValue = bottomContentPaddingRaw,
        animationSpec = tween(durationMillis = 180),
        label = "chatBottomPadding",
    )
    // Tracks soft-keyboard open/close transitions so we keep the latest message in
    // view ONLY when the user was already at the bottom (see keyboard effect below).
    var keyboardWasOpen by remember(threadId) { mutableStateOf(false) }
    var wasAtBottomBeforeKeyboard by remember(threadId) { mutableStateOf(false) }
    var restoredScroll by remember(threadId) { mutableStateOf(false) }
    var previousLastMessageId by remember(threadId) { mutableStateOf("") }
    var previousMessageCount by remember(threadId) { mutableStateOf(0) }
    var newMessagesCount by remember(threadId) { mutableStateOf(0) }
    val starredIds = remember(starredIdsCsv) { starredIdsCsv.csvToIdSet() }
    val hiddenIds = remember(hiddenIdsCsv) { hiddenIdsCsv.csvToIdSet() }
    val selectedIds = remember(selectedIdsCsv) { selectedIdsCsv.csvToIdSet() }
    val selectionMode = selectedIds.isNotEmpty()
    val visibleMessages = remember(messages, hiddenIds, chatSearch) {
        val base = messages.filter { it.id !in hiddenIds }
        val q = chatSearch.trim()
        if (q.isBlank()) base else base.filter {
        strippedReplyText(it.text).contains(q, ignoreCase = true) ||
        it.senderName.contains(q, ignoreCase = true) ||
        chatDate(it.timestamp).contains(q, ignoreCase = true)
        }
    }
    val unreadDividerMessageId = remember(visibleMessages, unreadCount, currentUid) {
        if (unreadCount <= 0 || visibleMessages.isEmpty()) ""
        else visibleMessages
        .takeLast(unreadCount.coerceAtMost(visibleMessages.size))
        .firstOrNull { it.senderId != currentUid }
        ?.id
        ?: visibleMessages.getOrNull((visibleMessages.size - unreadCount).coerceAtLeast(0))?.id.orEmpty()
    }

    fun setStarred(ids: Set<String>) { starredIdsCsv = ids.joinToString(",") }
    fun setHidden(ids: Set<String>) { hiddenIdsCsv = ids.joinToString(",") }
    fun setSelected(ids: Set<String>) { selectedIdsCsv = ids.joinToString(",") }
    fun toggleSelected(message: Message) {
        setSelected(if (message.id in selectedIds) selectedIds - message.id else selectedIds + message.id)
    }
    fun messageText(message: Message): String = forwardTextFor(message)
    fun showPlaceholder(label: String) {
        Toast.makeText(context, "$label is coming soon", Toast.LENGTH_SHORT).show()
    }
    fun startOneToOneCall(mode: CallMode) {
        if (isCommunity || peerUid.isBlank()) {
        Toast.makeText(context, "1-to-1 calls are available in direct chats first", Toast.LENGTH_SHORT).show()
        return
        }
        if (mode == CallMode.Video && !FeatureFlags.VIDEO_CALLS_ENABLED) {
        Toast.makeText(context, "Video calls are paused for this stability release", Toast.LENGTH_SHORT).show()
        return
        }
        // The real P2P call engine drives signaling + WebRTC media; the global
        // CallHost renders the call UI (so it also covers incoming calls).
        com.adischat.app.calls.CallEngine.init(context.applicationContext)
        com.adischat.app.calls.CallEngine.startOutgoing(peerUid, title, photoUrl, mode)
    }
    fun renderedIndexForMessage(messageId: String): Int {
        var index = if (!isCommunity) 1 else 0
        var lastDateLabel = ""
        visibleMessages.forEach { message ->
        val label = chatDate(message.timestamp)
        if (label != lastDateLabel) {
        index += 1
        lastDateLabel = label
        }
        if (message.id == unreadDividerMessageId) index += 1
        if (message.id == messageId) return index
        index += 1
        }
        return (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
    }

    fun jumpToMessage(messageId: String) {
        if (messageId.isBlank() || visibleMessages.none { it.id == messageId }) {
        Toast.makeText(context, "Original message isn't loaded", Toast.LENGTH_SHORT).show()
        return
        }
        scope.launch {
        runCatching { listState.animateScrollToItem(renderedIndexForMessage(messageId)) }
        highlightedMessageId = messageId
        delay(1600)
        if (highlightedMessageId == messageId) highlightedMessageId = null
        }
    }

    fun isNearBottom(): Boolean {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total == 0) return true
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        return lastVisible >= total - 3
    }

    fun scrollPrefs() =
        context.getSharedPreferences("adis_chat_scroll_positions", android.content.Context.MODE_PRIVATE)

    fun savedMessageIdForThread(): String =
        scrollPrefs().getString("${threadId}_messageId", "").orEmpty()

    fun saveConversationScrollPosition() {
        val messageIds = visibleMessages.mapTo(mutableSetOf()) { it.id }
        val firstVisibleMessage = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { (it.key as? String) in messageIds }
        ?: return
        val firstVisibleMessageId = firstVisibleMessage.key as String
        scrollPrefs()
        .edit()
        .putString("${threadId}_messageId", firstVisibleMessageId)
        // Store the actual message row offset, not the LazyColumn's first item offset.
        // Date dividers/header rows can be above the message and otherwise make the
        // restored position drift into the middle of the conversation.
        .putInt("${threadId}_offset", firstVisibleMessage.offset - listState.layoutInfo.viewportStartOffset)
        .putLong("${threadId}_timestamp", System.currentTimeMillis())
        .apply()
    }

    suspend fun restoreConversationScrollPosition() {
        if (visibleMessages.isEmpty()) return
        val prefs = scrollPrefs()
        val savedMessageId = prefs.getString("${threadId}_messageId", "").orEmpty()
        val savedOffset = prefs.getInt("${threadId}_offset", 0)
        val targetMessageId = when {
        savedMessageId.isNotBlank() && visibleMessages.any { it.id == savedMessageId } -> savedMessageId
        savedMessageId.isBlank() && unreadDividerMessageId.isNotBlank() -> unreadDividerMessageId
        else -> ""
        }
        if (targetMessageId.isNotBlank()) {
        listState.scrollToItem(renderedIndexForMessage(targetMessageId), -savedOffset)
        }
    }

    suspend fun scrollToLatestMessage() {
        val lastRenderedIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        listState.animateScrollToItem(lastRenderedIndex)
        newMessagesCount = 0
        onMessagesShown()
    }

    fun handleIncomingMessageWithoutJump(oldCount: Int, newCount: Int) {
        val added = (newCount - oldCount).coerceAtLeast(0)
        if (added == 0) return
        if (isNearBottom()) {
        scope.launch { scrollToLatestMessage() }
        } else {
        newMessagesCount += added
        }
    }

    val showScrollFab by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > 0 && !isNearBottom() }
    }

    LaunchedEffect(threadId, visibleMessages.lastOrNull()?.id, visibleMessages.firstOrNull()?.id, unreadDividerMessageId) {
        if (!restoredScroll && visibleMessages.isNotEmpty()) {
        val savedMessageId = savedMessageIdForThread()
        if (savedMessageId.isNotBlank() && visibleMessages.none { it.id == savedMessageId }) {
        // The previous reading anchor is not in this batch yet. Do not jump to
        // unread/latest and do not start saving a wrong replacement; wait for
        // the server/cache to provide the anchored message.
        return@LaunchedEffect
        }
        delay(80)
        restoreConversationScrollPosition()
        previousLastMessageId = visibleMessages.lastOrNull()?.id.orEmpty()
        previousMessageCount = visibleMessages.size
        restoredScroll = true
        }
    }
    // Persist the reading position continuously - - but only AFTER the initial restore
    // has run. Otherwise the very first (top-of-list) layout frame would overwrite the
    // saved position before restoreConversationScrollPosition() can read it, which made
    // restored chats land in the wrong place.
    LaunchedEffect(listState, visibleMessages, restoredScroll) {
        if (!restoredScroll) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .collect { saveConversationScrollPosition() }
    }
    LaunchedEffect(visibleMessages.lastOrNull()?.id, visibleMessages.size, restoredScroll) {
        if (restoredScroll) {
        val newLastId = visibleMessages.lastOrNull()?.id.orEmpty()
        if (previousLastMessageId.isNotBlank() && newLastId != previousLastMessageId) {
        handleIncomingMessageWithoutJump(previousMessageCount, visibleMessages.size)
        }
        previousLastMessageId = newLastId
        previousMessageCount = visibleMessages.size
        }
    }
    // Telegram-like keyboard behavior. We react ONLY to the soft keyboard (the IME
    // inset) - - never to the input bar measuring itself or the list settling on open.
    // Reacting to those layout changes was what made a freshly-opened chat jump to the
    // newest message. When the keyboard opens we keep the latest message in view only if
    // the user was already at the bottom; an older reading position is left untouched.
    LaunchedEffect(imeBottomPx, restoredScroll) {
        val keyboardOpen = imeBottomPx > 0
        if (keyboardOpen && !keyboardWasOpen) {
        // Snapshot the reading position at the instant the keyboard starts opening,
        // before the IME inset has pushed the message list around.
        wasAtBottomBeforeKeyboard =
        restoredScroll && visibleMessages.isNotEmpty() && isNearBottom()
        }
        if (restoredScroll && keyboardOpen && wasAtBottomBeforeKeyboard) {
        delay(120)
        scrollToLatestMessage()
        }
        keyboardWasOpen = keyboardOpen
    }
    // Telegram-like read receipts: opening the conversation counts as seeing it, so
    // report it as read once its messages are on screen - - even if the user doesn't
    // scroll all the way down. This is what lets the SENDER's bubble flip to "Seen".
    LaunchedEffect(threadId, restoredScroll, visibleMessages.isNotEmpty()) {
        if (restoredScroll && visibleMessages.isNotEmpty()) {
        onMessagesShown()
        }
    }
    // Keep marking as read as the user reaches the latest messages (e.g. new ones
    // that arrive while the chat is open and the user is already at the bottom).
    LaunchedEffect(listState) {
        snapshotFlow { isNearBottom() }
        .collect { atBottom ->
        if (atBottom) {
        newMessagesCount = 0
        onMessagesShown()
        }
        }
    }

    Box(
        Modifier
        .fillMaxSize()
        .imePadding()
        .background(canvas),
    ) {
        ChatThreadBackground(isDark = isInDarkTheme, modifier = Modifier.matchParentSize())

        // Subtle ambient gradient so the header blends in
        Box(
        Modifier
        .fillMaxWidth()
        .height(140.dp)
        .background(
        Brush.verticalGradient(
        listOf(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
        Color.Transparent,
        ),
        ),
        )
        .align(Alignment.TopCenter),
        )

        // Empty state
        if (visibleMessages.isEmpty()) {
        Box(
        Modifier
        .fillMaxSize()
        .padding(top = 88.dp, bottom = bottomContentPadding),
        Alignment.Center,
        ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Box(
        Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(threadAccent.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.ChatBubbleOutline, null,
        tint     = threadAccent.copy(0.70f),
        modifier = Modifier.size(28.dp),
        )
        }
        Text(
        text       = if (chatSearch.isBlank()) "Start the conversation" else "No messages found",
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface.copy(0.65f),
        )
        Text(
        text     = if (chatSearch.isBlank()) "Say hello!" else "Try a different search",
        fontSize = 16.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        }
        } else {
        LazyColumn(
        state      = listState,
        modifier      = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(
        start  = 12.dp,
        end    = 12.dp,
        top    = 88.dp,
        bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        // End-to-end encryption notice (direct messages only)
        if (!isCommunity) {
        item(key = "enc-badge") { EncryptionBadge() }
        }
        // Dynamic date-group separators
        var lastDateLabel = ""
        visibleMessages.forEachIndexed { idx, msg ->
        val label = chatDate(msg.timestamp)
        if (label != lastDateLabel) {
        lastDateLabel = label
        item(key = "date-$label-$idx") { DateSeparator(label) }
        }
        val stableMessageKey = listMessageKey(msg, idx)
        if (msg.id == unreadDividerMessageId) {
        item(key = "unread-$stableMessageKey") { UnreadMessagesDivider() }
        }
        val prev = visibleMessages.getOrNull(idx - 1)
        val next = visibleMessages.getOrNull(idx + 1)
        val samePrev = prev?.senderId == msg.senderId && prev?.senderRole != "system"
        val sameNext = next?.senderId == msg.senderId && next?.senderRole != "system"
        item(key = stableMessageKey) {
        // Groups never show delivery/read ticks on any message type.
        androidx.compose.runtime.CompositionLocalProvider(LocalShowDeliveryStatus provides !isCommunity) {
        if (msg.type == "call") {
        // Telegram-style in-timeline call record
        CallSystemMessage(
        message     = msg,
        isMine      = msg.senderId == currentUid,
        onCallAgain = { mode -> startOneToOneCall(mode) },
        )
        } else {
        MessageBubble(
        message     = msg,
        currentUid  = currentUid,
        isCommunity = isCommunity,
        useDirectSentBubble = !isCommunity,
        sameSenderBefore = samePrev,
        sameSenderAfter = sameNext,
        canModerate = canManageCommunity && profileKind == "group" && msg.senderId.isNotBlank() && msg.senderId != currentUid,
        selected    = msg.id in selectedIds,
        highlighted = msg.id == highlightedMessageId,
        starred     = msg.id in starredIds,
        pinned      = pinnedMessage?.id == msg.id,
        selectionMode = selectionMode,
        onReplyJump = { jumpToMessage(it) },
        onReact     = { emoji -> onReact(msg, emoji) },
        onDelete    = { onDelete(msg) },
        onModerate  = { action -> onModerateMember(msg.senderId, action) },
        onForward   = { forwardText = msg.text; forwardMedia = msg },
        onOpenUrl   = onOpenUrl,
        onSelect    = { toggleSelected(msg) },
        onRetry     = { onRetryMessage(msg) },
        onOpenActions = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        actionMessage = msg
        },
        onOpenSender = { if (msg.senderId != currentUid) memberSheet = msg },
        )
        }
        }
        }
        }
        if (otherTyping) {
        item(key = "typing") { TypingIndicatorRow(threadAccent) }
        }
        }
        }

        ThreadTopBar(
        title      = title,
        subtitle      = subtitle,
        photoUrl      = photoUrl,
        accent      = threadAccent,
        isSupportHeader = isSupportHeader,
        isTyping      = otherTyping,
        isOnline      = peerOnline,
        verified      = !isCommunity && (profileVerificationLevel >= 3 || profileVerified),
        showCallActions = !isCommunity,
        isGroup      = isCommunity && profileKind == "group",
        isGroupAdmin    = canManageCommunity && profileKind == "group",
        onBack      = onBack,
        onProfileClick  = { showProfile = true },
        onSearchClick   = { searchOpen = true },
        onMenuAction    = { action ->
        when (action) {
        "call" -> startOneToOneCall(CallMode.Voice)
        "video" -> startOneToOneCall(CallMode.Video)
        "permissions", "members" ->
        Toast.makeText(context, "Group ${if (action == "members") "member management" else "permissions"} coming soon", Toast.LENGTH_SHORT).show()
        "pin_chat" -> Toast.makeText(context, "Pin chat coming soon", Toast.LENGTH_SHORT).show()
        "lock_chat" -> Toast.makeText(context, "Lock chat coming soon", Toast.LENGTH_SHORT).show()
        "customize" -> onThreadAction("wallpaper")   // reuse the existing wallpaper/customize flow
        else -> onThreadAction(action)
        }
        },
        modifier      = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )

        AnimatedVisibility(
        visible = searchOpen,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp, start = 12.dp, end = 12.dp),
        enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -it / 3 },
        exit = fadeOut(tween(100)),
        ) {
        ChatSearchOverlay(
        value = chatSearch,
        resultCount = visibleMessages.size.takeIf { chatSearch.isNotBlank() },
        accent = threadAccent,
        onValueChange = { chatSearch = it },
        onClose = { searchOpen = false; chatSearch = "" },
        )
        }

        pinnedMessage?.let { pinned ->
        PinnedMessageBanner(
        message = pinned,
        accent = threadAccent,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (searchOpen) 124.dp else 68.dp, start = 12.dp, end = 12.dp),
        onClose = { pinnedMessage = null },
        )
        }

        AnimatedVisibility(
        visible = selectionMode,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -it },
        exit = fadeOut(tween(120)),
        ) {
        MessageSelectionBar(
        count = selectedIds.size,
        accent = threadAccent,
        onClose = { setSelected(emptySet()) },
        onForward = {
        val selectedText = messages.filter { it.id in selectedIds }.joinToString("\n\n") { messageText(it) }
        if (selectedText.isNotBlank()) { forwardText = selectedText; forwardMedia = null }
        setSelected(emptySet())
        },
        onCopy = {
        val selectedText = messages.filter { it.id in selectedIds }.joinToString("\n\n") { messageText(it) }
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(selectedText))
        setSelected(emptySet())
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
        onStar = {
        setStarred(starredIds + selectedIds)
        setSelected(emptySet())
        },
        onDelete = {
        setHidden(hiddenIds + selectedIds)
        setSelected(emptySet())
        },
        )
        }

        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED && isCommunity && profileKind == "channel" && channelId.isNotBlank()) {
        GamePotBannerHost(
        channelId = channelId,
        myUid     = currentUid,
        isAdmin   = communityOwnerUid == currentUid,
        modifier  = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 62.dp),   // sits just below the top bar
        )
        }

        AnimatedVisibility(
        visible  = showScrollFab,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = bottomContentPadding + 2.dp),
        enter    = fadeIn() + slideInVertically { it / 2 },
        exit     = fadeOut(),
        ) {
        SmallFloatingActionButton(
        onClick      = {
        if (visibleMessages.isNotEmpty()) {
        scope.launch { scrollToLatestMessage() }
        }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor   = threadAccent,
        shape      = CircleShape,
        ) {
        Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Default.KeyboardArrowDown, "Scroll to latest", modifier = Modifier.size(20.dp))
        if (newMessagesCount > 0) {
        Box(
        modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = 8.dp, y = (-8).dp)
        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
        .clip(CircleShape)
        .background(threadAccent)
        .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
        ) {
        Text(
        text = if (newMessagesCount > 99) "99+" else newMessagesCount.toString(),
        color = Color.Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        )
        }
        }
        }
        }
        }

        if (canSend) {
        Box(
        modifier = Modifier
        .align(Alignment.BottomCenter)
        .onSizeChanged { bottomBarHeightPx = it.height },
        ) {
        MediaInputBar(
        sending     = sending,
        accent      = threadAccent,
        peerName    = title,
        peerUid     = peerUid,
        replyPreview = replyTo?.let {
        val who = when {
        it.senderId == currentUid     -> "You"
        it.senderName.isNotBlank()    -> it.senderName
        else -> title
        }
        "$who - ${replySnippetFor(it)}"
        },
        onCancelReply = { replyTo = null },
        editingText = editingMessage?.text,
        onCancelEdit = { editingMessage = null },
        onSubmitEdit = { newText ->
        val target = editingMessage
        editingMessage = null
        if (target != null && newText.isNotBlank()) onEditMessage(target, newText)
        },
        onSendText  = { text ->
        val reply = replyTo
        replyTo = null
        onSend(text, reply)
        },
        onSendImage = onSendImage,
        onSendVideo = onSendVideo,
        onSendVoice = onSendVoice,
        onBottomOverlayPaddingChange = { bottomOverlayPadding = it },
        )
        }
        } else {
        LaunchedEffect(Unit) { bottomOverlayPadding = 0.dp }
        Box(
        modifier = Modifier
        .align(Alignment.BottomCenter)
        .onSizeChanged { bottomBarHeightPx = it.height },
        ) {
        if (isCommunity && !communityJoined) {
        CommunityMembershipBar(
        kind = profileKind,
        accent = threadAccent,
        onJoin = onJoinCommunity,
        )
        } else {
        ReadOnlyBar()
        }
        }
        }
    }

    // Full-screen profile page (replaces the old bottom sheet)
    if (showProfile) {
        FullProfileScreen(
        title       = title,
        subtitle    = subtitle,
        photoUrl    = photoUrl,
        kind      = profileKind,
        linkId      = channelId.ifBlank { peerUid },
        description = profileDescription,
        memberCount = profileMemberCount,
        verificationLevel = profileVerificationLevel,
        verified = profileVerified,
        sharedMessages = messages,
        accent      = threadAccent,
        showCallActions = !isCommunity && peerUid.isNotBlank(),
        communityJoined = communityJoined,
        canManageCommunity     = canManageCommunity,
        onUpdateCommunity      = onUpdateCommunity,
        onUploadCommunityPhoto = onUploadCommunityPhoto,
        onJoinCommunity      = onJoinCommunity,
        onLeaveCommunity       = onLeaveCommunity,
        onDeleteCommunity      = {
        onDeleteCommunity()
        showProfile = false
        },
        onDirectMessage = { showProfile = false },
        onMute = {
        showProfile = false
        onThreadAction("mute")
        },
        onSearch = {
        showProfile = false
        searchOpen = true
        },
        onBlock = {
        showProfile = false
        onThreadAction("block")
        },
        onReport = {
        showProfile = false
        onThreadAction("report")
        },
        onClearChat = {
        showProfile = false
        onThreadAction("clear")
        },
        onCall = { startOneToOneCall(CallMode.Voice) },
        onVideoCall = { startOneToOneCall(CallMode.Video) },
        onOpenUrl = onOpenUrl,
        onDismiss   = { showProfile = false },
        )
    }

    forwardText?.let { text ->
        ForwardScreen(
        messageText   = text,
        onDismiss     = { forwardText = null; forwardMedia = null },
        sourceType    = forwardMedia?.type ?: "text",
        mediaUrl      = forwardMedia?.mediaUrl ?: "",
        mediaMimeType = forwardMedia?.mediaMimeType ?: "",
        durationMs    = forwardMedia?.durationMs ?: 0L,
        )
    }

    memberSheet?.let { msg ->
        GroupMemberSheet(
        name  = msg.senderName.ifBlank { "Member" },
        photo = msg.senderPhotoUrl,
        uid   = msg.senderId,
        verified = msg.senderVerificationLevel >= 3 || msg.senderVerified,
        onDismiss = { memberSheet = null },
        )
    }

    actionMessage?.let { msg ->
        MessageActionsSheet(
        message = msg,
        isMine = msg.senderId == currentUid,
        isStarred = msg.id in starredIds,
        isPinned = pinnedMessage?.id == msg.id,
        canDeleteForEveryone = msg.senderId == currentUid || (isCommunity && canManageCommunity),
        onDismiss = { actionMessage = null },
        onReply = { replyTo = msg; actionMessage = null },
        onForward = { forwardText = forwardTextFor(msg); forwardMedia = msg; actionMessage = null },
        onCopy = {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(copyTextFor(msg)))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        actionMessage = null
        },
        onCopyImage = {
        scope.launch {
        val copied = copyImageMessageToClipboard(context, msg)
        Toast.makeText(
        context,
        if (copied) "Image copied" else "Copy image is not supported on this device. Use Save image instead.",
        Toast.LENGTH_SHORT
        ).show()
        actionMessage = null
        }
        },
        onCopyCaption = {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(strippedReplyText(msg.text).trim()))
        Toast.makeText(context, "Caption copied", Toast.LENGTH_SHORT).show()
        actionMessage = null
        },
        onEdit = {
        // Enter edit mode: load the message into the composer (the save happens
        // when the user taps send). Previously this re-sent the SAME text, so
        // nothing ever changed - - that was the edit bug.
        editingMessage = msg
        replyTo = null
        actionMessage = null
        },
        onDeleteForMe = {
        deleteConfirmMessage = msg
        deleteConfirmEveryone = false
        actionMessage = null
        },
        onDeleteForEveryone = {
        deleteConfirmMessage = msg
        deleteConfirmEveryone = true
        actionMessage = null
        },
        onPin = {
        onPinMessage(msg, pinnedMessage?.id != msg.id)
        pinnedMessage = if (pinnedMessage?.id == msg.id) null else msg
        actionMessage = null
        },
        onStar = {
        onStarMessage(msg, msg.id !in starredIds)
        setStarred(if (msg.id in starredIds) starredIds - msg.id else starredIds + msg.id)
        actionMessage = null
        },
        onTranslate = {
        onTranslateMessage(msg)
        actionMessage = null
        },
        onReport = {
        onReportMessage(msg)
        actionMessage = null
        },
        onSelect = {
        toggleSelected(msg)
        actionMessage = null
        },
        onSave = {
        downloadMessageMedia(context, msg)
        actionMessage = null
        },
        )
    }

    deleteConfirmMessage?.let { target ->
        AlertDialog(
        onDismissRequest = { deleteConfirmMessage = null },
        title = { Text(if (deleteConfirmEveryone) "Delete for everyone?" else "Delete for me?") },
        text = { Text(if (deleteConfirmEveryone) "This will replace the message with a deleted placeholder for everyone." else "This removes the message only from your chat view.") },
        confirmButton = {
        TextButton(onClick = {
        if (deleteConfirmEveryone) {
        onDeleteForEveryone(target)
        } else {
        onDeleteForMe(target)
        setHidden(hiddenIds + target.id)
        }
        deleteConfirmMessage = null
        }) { Text("Delete", color = DeleteRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
        TextButton(onClick = { deleteConfirmMessage = null }) { Text("Cancel") }
        },
        )
    }
}

/** Save an image/video/voice message to the device's Downloads folder. */
private fun downloadMessageMedia(context: android.content.Context, message: Message) {
    val url = message.mediaUrl
    if (url.isBlank()) {
        Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = android.net.Uri.parse(url)
        val ext = when (message.type) {
        "image" -> "jpg"; "video" -> "mp4"; "voice" -> "m4a"; else -> ""
        }
        val base = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "adischat_${System.currentTimeMillis()}"
        val name = if (ext.isNotBlank() && !base.contains('.')) "$base.$ext" else base
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val request = android.app.DownloadManager.Request(uri)
        .setTitle(name)
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        dm.enqueue(request)
        Toast.makeText(context, "Saving to Downloads... -", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Couldn't save file", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun copyImageMessageToClipboard(context: android.content.Context, message: Message): Boolean {
    val url = message.mediaUrl.trim()
    if (message.type != "image" || url.isBlank()) return false

    return runCatching {
        val appContext = context.applicationContext
        val file = withContext(Dispatchers.IO) {
        val token = FirebaseAuth.getInstance().currentUser
        ?.getIdToken(false)
        ?.await()
        ?.token
        .orEmpty()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        val mime = connection.contentType?.substringBefore(";")?.lowercase(Locale.ROOT).orEmpty()
        val extension = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
        }
        val dir = File(appContext.cacheDir, "clipboard_images").apply { mkdirs() }
        val outFile = File(dir, "adischat_image_${System.currentTimeMillis()}.$extension")
        connection.inputStream.use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        outFile
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        file
        )
        val clip = ClipData.newUri(context.contentResolver, "AdisChat image", uri)
        val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        true
    }.getOrDefault(false)
}


/** Modern call action: a soft, accent-tinted circular button (messenger style) instead
 *  of a bare floating glyph. */
@Composable
private fun HeaderCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(accent.copy(alpha = 0.14f))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThreadTopBar(
    title      : String,
    subtitle       : String,
    photoUrl       : String,
    accent      : Color,
    isSupportHeader: Boolean,
    isTyping       : Boolean,
    isOnline       : Boolean = false,
    verified       : Boolean = false,
    showCallActions: Boolean,
    isGroup      : Boolean = false,
    isGroupAdmin   : Boolean = false,
    onBack      : () -> Unit,
    onProfileClick : () -> Unit,
    onSearchClick  : () -> Unit,
    onMenuAction    : (String) -> Unit,
    modifier       : Modifier = Modifier,
) {
    val isDark   = isInDarkTheme
    val headerBg = if (isDark) Color(0xE6091210) else Color(0xF5FFFFFF)
    val iconTint = if (isDark) Color.White else Color(0xFF111827)
    val subTint  = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280)

    Box(
        modifier = modifier
        .fillMaxWidth()
        .background(headerBg)
        .border(BorderStroke(0.5.dp, if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000)),
        shape = RoundedCornerShape(0.dp)),
    ) {
        Row(
        modifier      = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .height(64.dp).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        // Back button
        IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = iconTint)
        }

        // Avatar
        Box(
        modifier = Modifier
        .size(42.dp)
        .clickable(onClick = onProfileClick),
        ) {
        if (isSupportHeader) {
        Box(
        modifier      = Modifier.fillMaxSize().clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.SupportAgent, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        } else {
        ChatAvatar(photoUrl, initialsOf(title), 42.dp, accent)
        }
        // Online indicator - - only when the peer is actually online (or for support).
        if (isOnline || isSupportHeader) {
        Box(
        Modifier
        .align(Alignment.BottomEnd)
        .size(11.dp)
        .clip(CircleShape)
        .background(Color(0xFF18C964))
        .border(1.5.dp, headerBg, CircleShape),
        )
        }
        }

        Spacer(Modifier.width(10.dp))

        // Name & status
        Column(
        modifier = Modifier
        .weight(1f)
        .clickable(onClick = onProfileClick),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text       = title,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color      = iconTint,
        maxLines   = 1,
        overflow   = TextOverflow.Ellipsis,
        modifier   = Modifier.weight(1f, fill = false),
        )
        if (verified) {
        Spacer(Modifier.width(5.dp))
        UserVerifiedBadge(Modifier.size(18.dp))
        }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isTyping) {
        TypingDots(dotColor = Color(0xFF18C964), size = 4.dp)
        Spacer(Modifier.width(3.dp))
        Text(tr("typing..."), fontSize = 13.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
        } else {
        Text(
        text = if (isSupportHeader) "AdisChat Support" else subtitle.ifBlank { "last seen unavailable" },
        fontSize = 13.sp,
        color = if (isOnline) Color(0xFF34D399) else subTint,
        fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        }
        }
        }

        if (showCallActions) {
        HeaderCallButton(Icons.Rounded.Call, "Voice call", accent) { onMenuAction("call") }
        if (FeatureFlags.VIDEO_CALLS_ENABLED) {
        HeaderCallButton(Icons.Rounded.Videocam, "Video call", accent) { onMenuAction("video") }
        }
        }
        IconButton(onClick = onSearchClick) {
        Icon(Icons.Default.Search, "Search", tint = subTint, modifier = Modifier.size(21.dp))
        }
        // Overflow menu
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Default.MoreVert, "More", tint = subTint, modifier = Modifier.size(22.dp))
        }
        if (menuOpen) {
        ModernChatOverflowMenu(
        accent = accent,
        isGroup = isGroup,
        isGroupAdmin = isGroupAdmin,
        onDismiss = { menuOpen = false },
        onViewProfile = { menuOpen = false; onProfileClick() },
        onMute = { menuOpen = false; onMenuAction("mute") },
        onSearch = { menuOpen = false; onSearchClick() },
        onWallpaper = { menuOpen = false; onMenuAction("wallpaper") },
        onMedia = { menuOpen = false; onMenuAction("media") },
        onDisappearing = { menuOpen = false; onMenuAction("disappearing") },
        onExport = { menuOpen = false; onMenuAction("export") },
        onClear = { menuOpen = false; onMenuAction("clear") },
        onReport = { menuOpen = false; onMenuAction("report") },
        onBlock = { menuOpen = false; onMenuAction("block") },
        onPermissions = { menuOpen = false; onMenuAction("permissions") },
        onManageMembers = { menuOpen = false; onMenuAction("members") },
        onPinChat = { menuOpen = false; onMenuAction("pin_chat") },
        onLockChat = { menuOpen = false; onMenuAction("lock_chat") },
        onCustomize = { menuOpen = false; onMenuAction("customize") },
        )
        }
        }
    }
}

private data class ChatOverflowMenuItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun ModernChatOverflowMenu(
    accent: Color,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onMute: () -> Unit,
    onSearch: () -> Unit,
    onWallpaper: () -> Unit,
    onMedia: () -> Unit,
    onDisappearing: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    isGroup: Boolean = false,
    isGroupAdmin: Boolean = false,
    onPermissions: () -> Unit = {},
    onManageMembers: () -> Unit = {},
    onPinChat: () -> Unit = {},
    onLockChat: () -> Unit = {},
    onCustomize: () -> Unit = {},
) {
    // Groups - - - a focused group menu. Direct chats - - - the modern 2026 set.
    val items = if (isGroup) {
        buildList {
        add(ChatOverflowMenuItem(tr("Group info"), Icons.Default.Info, accent, onViewProfile))
        add(ChatOverflowMenuItem(tr("Search in group"), Icons.Default.Search, Color(0xFF60A5FA), onSearch))
        add(ChatOverflowMenuItem(tr("Shared media"), Icons.Default.PermMedia, Color(0xFF22D3EE), onMedia))
        add(ChatOverflowMenuItem(tr("Mute notifications"), Icons.Default.NotificationsOff, Color(0xFF7CFF8A), onMute))
        if (isGroupAdmin) {
        add(ChatOverflowMenuItem(tr("Group permissions"), Icons.Default.AdminPanelSettings, Color(0xFFA78BFA), onPermissions))
        add(ChatOverflowMenuItem(tr("Manage members"), Icons.Default.Group, Color(0xFFFACC15), onManageMembers))
        }
        }
    } else listOf(
        ChatOverflowMenuItem(tr("Mute notifications"), Icons.Default.NotificationsOff, Color(0xFF7CFF8A), onMute),
        ChatOverflowMenuItem(tr("Pin chat"), Icons.Default.PushPin, Color(0xFF60A5FA), onPinChat),
        ChatOverflowMenuItem(tr("Shared media"), Icons.Default.PermMedia, Color(0xFF22D3EE), onMedia),
        ChatOverflowMenuItem(tr("Disappearing messages"), Icons.Default.Timer, Color(0xFFFACC15), onDisappearing),
        ChatOverflowMenuItem(tr("Lock chat"), Icons.Default.Lock, Color(0xFF34D399), onLockChat),
        ChatOverflowMenuItem(tr("Customize chat"), Icons.Default.Palette, Color(0xFFA78BFA), onCustomize),
        ChatOverflowMenuItem(tr("Block"), Icons.Default.Block, DeleteRed, onBlock),
        ChatOverflowMenuItem(tr("Report"), Icons.Default.Report, Amber, onReport),
    )
    ChatMenuBottomSheet(items = items, onDismiss = onDismiss)
}

/** Compact glassmorphism bottom sheet (- - screen height) for chat actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMenuBottomSheet(items: List<ChatOverflowMenuItem>, onDismiss: () -> Unit) {
    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isInDarkTheme) Color(0xFF0E1512) else Color(0xFFFBFBFD),
        contentColor = if (isInDarkTheme) Color.White else Color(0xFF111827),
        dragHandle = {
        Box(
        Modifier.padding(top = 10.dp).size(width = 40.dp, height = 4.dp)
        .clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.22f)),
        )
        },
    ) {
        Column(
        Modifier.fillMaxWidth().heightIn(max = maxH).navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        items.forEach { item -> ChatMenuCardRow(item) }
        Spacer(Modifier.height(4.dp))
        }
    }
}

/** Glass card row - - 24dp icon, consistent spacing, modern typography. */
@Composable
private fun ChatMenuCardRow(item: ChatOverflowMenuItem) {
    Row(
        modifier = Modifier
        .fillMaxWidth().height(58.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(if (isInDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f))
        .border(1.dp, if (isInDarkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
        .clickable(onClick = item.onClick)
        .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
        .background(item.tint.copy(alpha = if (isInDarkTheme) 0.16f else 0.12f)),
        contentAlignment = Alignment.Center,
        ) { Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(
        text = item.label,
        color = when (item.tint) { DeleteRed -> DeleteRed; Amber -> Amber; else -> MaterialTheme.colorScheme.onSurface },
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatOverflowMenuRow(item: ChatOverflowMenuItem) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 66.dp)
        .clickable(onClick = item.onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        modifier = Modifier
        .size(46.dp)
        .clip(RoundedCornerShape(15.dp))
        .background(item.tint.copy(alpha = if (isInDarkTheme) 0.16f else 0.12f))
        .border(1.dp, item.tint.copy(alpha = 0.24f), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
        text = item.label,
        color = when (item.tint) {
        DeleteRed -> DeleteRed
        Amber -> Amber
        else -> MaterialTheme.colorScheme.onSurface
        },
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun ChatSearchOverlay(
    value: String,
    resultCount: Int?,
    accent: Color,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Search, null, tint = accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        singleLine = true,
        cursorBrush = SolidColor(accent),
        modifier = Modifier.weight(1f),
        decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) Text("Search inside chat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        inner()
        }
        },
        )
        if (resultCount != null) Text("$resultCount", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
        Icon(Icons.Default.Close, "Close search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        }
    }
}

@Composable
private fun PinnedMessageBanner(message: Message, accent: Color, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PushPin, null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
        Text("Pinned message", color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(forwardTextFor(message), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.Close, "Unpin", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        }
    }
}

@Composable
private fun MessageSelectionBar(
    count: Int,
    accent: Color,
    onClose: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 18.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close selection") }
        Text("$count selected", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onStar) { Icon(Icons.Default.Star, "Star", tint = Amber) }
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
        IconButton(onClick = onForward) { CurvedForwardIcon(tint = accent, modifier = Modifier.size(21.dp)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = DeleteRed) }
        }
    }
}

private val ActionSheetSurface = Color(0xFF141414)
private val ActionSheetText    = Color(0xFFECECEC)
private val ActionSheetMuted   = Color(0xFF8A8A8A)
private val ActionSheetHair    = Color.White.copy(alpha = 0.06f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    message: Message,
    isMine: Boolean,
    isStarred: Boolean,
    isPinned: Boolean,
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onCopyImage: () -> Unit,
    onCopyCaption: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onTranslate: () -> Unit,
    onReport: () -> Unit,
    onSelect: () -> Unit,
    onSave: () -> Unit = {},
) {
    val isImage = message.type == "image" && message.mediaUrl.isNotBlank()
    val isMedia = message.type in setOf("image", "video", "voice", "file", "document") && message.mediaUrl.isNotBlank()
    val copyPayload = copyTextFor(message)
    val hasText = strippedReplyText(message.text).isNotBlank()
    val canCopy = !isImage && copyPayload.isNotBlank()
    val canEdit = isMine && message.type !in setOf("deleted", "call") && (hasText || isMedia)

    // Native spring open/close, swipe-to-dismiss and tap-outside come for free from
    // ModalBottomSheet; the rounded 32dp top + dark surface make it feel 2026-premium.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = ActionSheetSurface,
        shape      = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
        Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
        ) {
        // - - - Preview header (sender - - time - - preview - - thumbnail) - - no raw IDs - - -
        MessageSheetPreview(message, isMine)
        Spacer(Modifier.height(14.dp))

        // - - - Quick actions: Reply - - Edit - - Forward - - Copy - - -
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.Reply, "Reply", onReply)
        QuickActionButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.Reply, "Forward", onForward, mirrorIcon = true)
        if (canEdit && !isImage) QuickActionButton(Modifier.weight(1f), Icons.Default.Edit, "Edit", onEdit)
        if (isImage) QuickActionButton(Modifier.weight(1f), Icons.Default.ContentCopy, "Copy image", onCopyImage)
        if (canCopy) QuickActionButton(Modifier.weight(1f), Icons.Default.ContentCopy, "Copy", onCopy)
        }

        SheetSectionDivider()

        // - - - Secondary actions - - -
        if (isImage && hasText) SheetListItem(Icons.Default.ContentCopy, "Copy caption", onClick = onCopyCaption)
        if (isImage) {
        SheetListItem(Icons.Default.Download, "Save image", onClick = onSave)
        } else if (isMedia) {
        SheetListItem(Icons.Default.Download, "Save to device", onClick = onSave)
        }
        if (hasText) SheetListItem(Icons.Default.Translate, "Translate", onClick = onTranslate)
        SheetListItem(Icons.Default.PushPin, if (isPinned) "Unpin message" else "Pin message", onClick = onPin)
        SheetListItem(Icons.Default.Star, if (isStarred) "Unsave message" else "Save message", onClick = onStar)
        SheetListItem(Icons.Default.Checklist, "Select message", onClick = onSelect)

        SheetSectionDivider()

        // - - - Destructive (red accents only here) - - -
        SheetListItem(Icons.Default.Report, "Report message", tint = Amber, onClick = onReport)
        SheetListItem(Icons.Default.DeleteOutline, "Delete for me", tint = DeleteRed, onClick = onDeleteForMe)
        if (canDeleteForEveryone) SheetListItem(Icons.Default.DeleteForever, "Delete for everyone", tint = DeleteRed, onClick = onDeleteForEveryone)
        }
    }
}

@Composable
private fun MessageSheetPreview(message: Message, isMine: Boolean) {
    val sender  = if (isMine) "You" else message.senderName.ifBlank { "Message" }
    val time    = "${chatDate(message.timestamp)} ${messageTime(message.timestamp)}"
    val preview = forwardTextFor(message)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
        message.type == "image" && message.mediaUrl.isNotBlank() ->
        AsyncImage(message.mediaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
        message.type == "video" && message.mediaUrl.isNotBlank() ->
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        message.type == "voice" ->
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(AdisAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Mic, null, tint = AdisAccent, modifier = Modifier.size(22.dp))
        }
        else ->
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(AdisAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.ChatBubbleOutline, null, tint = AdisAccent, modifier = Modifier.size(20.dp))
        }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(sender, color = ActionSheetText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time, color = ActionSheetMuted, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(preview, color = Color(0xFFB8B8B8), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    mirrorIcon: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, label = "qaScale")
    Column(
        modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White.copy(alpha = 0.06f))
        .clickable(interactionSource = interaction, indication = null) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        onClick()
        }
        .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
        icon, label, tint = ActionSheetText,
        modifier = Modifier.size(22.dp).then(if (mirrorIcon) Modifier.graphicsLayer(scaleX = -1f) else Modifier),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFCFCFCF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SheetListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = ActionSheetText,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "liScale")
    Row(
        Modifier
        .fillMaxWidth()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp))
        .clickable(interactionSource = interaction, indication = null) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        onClick()
        }
        .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SheetSectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 7.dp, horizontal = 4.dp), thickness = 1.dp, color = ActionSheetHair)
}

@Composable
private fun MessageActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ForwardMessageActionRow(label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurvedForwardIcon(tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
private fun ReadOnlyBar(modifier: Modifier = Modifier) {
    Surface(
        modifier       = modifier.fillMaxWidth().navigationBarsPadding(),
        color      = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Row(
        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(
        Icons.Default.Lock, null,
        tint     = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
        text     = "Only the channel admin can post",
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 17.sp,
        )
        }
    }
}

@Composable
private fun CommunityMembershipBar(
    kind: String,
    accent: Color,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isChannel = kind == "channel"
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Icon(
        if (isChannel) Icons.Default.Campaign else Icons.Default.Groups,
        null,
        tint = accent,
        modifier = Modifier.size(22.dp),
        )
        Text(
        text = if (isChannel) "Follow this channel to get updates" else "Join this group to send messages",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
        modifier = Modifier.weight(1f),
        maxLines = 2,
        )
        Button(
        onClick = onJoin,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (isChannel) "Follow" else "Join", fontWeight = FontWeight.Bold)
        }
        }
    }
}

// INPUT BAR

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MediaInputBar(
    sending    : Boolean,
    accent     : Color,
    peerName   : String,
    peerUid    : String = "",
    replyPreview: String? = null,
    onCancelReply: () -> Unit = {},
    editingText: String? = null,
    onCancelEdit: () -> Unit = {},
    onSubmitEdit: (String) -> Unit = {},
    onSendText : (String) -> Unit,
    onSendImage: (android.net.Uri, String) -> Unit,
    onSendVideo: (android.net.Uri, String) -> Unit,
    onSendVoice: (File, Long) -> Unit,
    onBottomOverlayPaddingChange: (Dp) -> Unit = {},
    modifier   : Modifier = Modifier,
) {
    val context      = LocalContext.current
    val scope      = rememberCoroutineScope()
    var draft      by remember { mutableStateOf("") }
    var isRecording     by remember { mutableStateOf(false) }
    var recSeconds      by remember { mutableIntStateOf(0) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showEmojiBar    by remember { mutableStateOf(false) }
    var showTransfer    by remember { mutableStateOf(false) }
    var showLocationConfirm by remember { mutableStateOf(false) }
    val voiceRecorder   = remember { VoiceRecorderState(context) }
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // When the keyboard becomes visible, auto-hide the attachment/emoji panels so
    // they never stack on top of each other (Telegram/WhatsApp behavior).
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) { showAttachSheet = false; showEmojiBar = false }
    }

    // Edit mode: when a message is being edited, load its text into the composer and
    // open the keyboard. Sending then SAVES the edit instead of posting a new message.
    val editing = editingText != null
    LaunchedEffect(editingText) {
        if (editingText != null) {
        draft = editingText
        keyboardController?.show()
        }
    }

    // Media chosen but not yet sent - - - show the multi-select preview sheet first.
    // Each item is (uri, "image"|"video"). No raw device filename ever appears.
    var pendingMedia by remember { mutableStateOf<List<PendingMedia>>(emptyList()) }

    // Multi-select image/video pickers (append to the current selection).
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) pendingMedia = pendingMedia + uris.map { PendingMedia(it, "image") }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) pendingMedia = pendingMedia + uris.map { PendingMedia(it, "video") }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
        val f = File.createTempFile("cam_", ".jpg", context.cacheDir)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        pendingMedia = pendingMedia + PendingMedia(android.net.Uri.fromFile(f), "image")
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
        // Media picked via the Document option - - - route to the real media path
        // (so it shows as a Photo/Video, never IMG_xxxx.jpg). Real documents
        // keep their meaningful name with a clean paperclip label.
        val mime = context.contentResolver.getType(it) ?: ""
        when {
        mime.startsWith("image/") -> pendingMedia = pendingMedia + PendingMedia(it, "image")
        mime.startsWith("video/") -> pendingMedia = pendingMedia + PendingMedia(it, "video")
        else -> onSendText("- - ${queryFileName(context, it)}")
        }
        }
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
        val (name, phone) = readContact(context, it)
        if (name.isNotBlank() || phone.isNotBlank()) {
        onSendText("Contact: ${name.ifBlank { "Contact" }}" + if (phone.isNotBlank()) "\nPhone: $phone" else "")
        }
        }
    }
    val cameraPermission   = rememberPermissionState(Manifest.permission.CAMERA)
    val contactPermission  = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(isRecording) {
        if (isRecording) {
        recSeconds = 0
        while (true) { delay(1000); recSeconds++ }
        }
    }

    LaunchedEffect(showAttachSheet) {
        if (!showAttachSheet) onBottomOverlayPaddingChange(0.dp)
    }

    DisposableEffect(Unit) {
        onDispose { onBottomOverlayPaddingChange(0.dp) }
    }

    if (showTransfer) {
        TransferDialog(
        peerName   = peerName,
        peerUid    = peerUid,
        accent     = accent,
        onDismiss  = { showTransfer = false },
        onSendText = onSendText,
        )
    }

    // Media preview + caption before sending (Telegram-style, no device filenames)
    if (pendingMedia.isNotEmpty()) {
        MediaPreviewSheet(
        items    = pendingMedia,
        accent   = accent,
        onRemove = { i -> pendingMedia = pendingMedia.toMutableList().also { if (i in it.indices) it.removeAt(i) } },
        onReplace = { i, uri -> pendingMedia = pendingMedia.toMutableList().also { if (i in it.indices) it[i] = PendingMedia(uri, "image") } },
        onAddMore = { imagePicker.launch("image/*") },
        onCancel = { pendingMedia = emptyList() },
        onSend   = { caption ->
        // Send the whole selection as an album; caption goes on the first item.
        pendingMedia.forEachIndexed { i, m ->
        val cap = if (i == 0) caption else ""
        if (m.type == "image") onSendImage(m.uri, cap) else onSendVideo(m.uri, cap)
        }
        pendingMedia = emptyList()
        },
        )
    }

    if (showLocationConfirm) {
        LocationConfirmDialog(
        accent    = accent,
        onDismiss = { showLocationConfirm = false },
        onConfirm = { lat, lng ->
        onSendText("My location\nhttps://maps.google.com/?q=$lat,$lng")
        showLocationConfirm = false
        },
        )
    }

    val surfaceBg = Color(0xE60A1210)

    Surface(
        modifier      = modifier.fillMaxWidth().navigationBarsPadding(),
        color      = surfaceBg,
        tonalElevation  = 0.dp,
        shadowElevation = 14.dp,
    ) {
        if (isRecording) {
        val pulseTransition = rememberInfiniteTransition(label = "recPulse")
        val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.30f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label      = "recAlpha",
        )
        Row(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        // Cancel
        Box(
        modifier      = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.errorContainer)
        .clickable { voiceRecorder.cancel(); isRecording = false },
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.Close, "Cancel",
        tint     = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(18.dp),
        )
        }
        Spacer(Modifier.width(10.dp))
        // Pulsing red dot
        Box(
        Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(DeleteRed.copy(alpha = pulseAlpha)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
        Text(
        text       = formatDuration(recSeconds * 1000L),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurface,
        )
        Text(
        text     = "Slide left to cancel",
        fontSize = 14.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        // Send voice
        Box(
        modifier      = Modifier
        .size(46.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(BubbleGradStart, BubbleGradEnd)))
        .clickable {
        voiceRecorder.stop()?.let { (f, ms) -> onSendVoice(f, ms) }
        isRecording = false
        },
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.AutoMirrored.Filled.Send, "Send voice",
        tint     = Color.White,
        modifier = Modifier.size(20.dp),
        )
        }
        }
        } else {
        Column(Modifier.fillMaxWidth()) {
        if (editing && editingText != null) {
        Row(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(accent.copy(alpha = 0.12f))
        .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Edit, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
        Text("Editing message", color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(editingText, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { draft = ""; onCancelEdit() }, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.Close, "Cancel edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        }
        }
        replyPreview?.let { preview ->
        Row(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(accent.copy(alpha = 0.12f))
        .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
        Text("Reply", color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(preview, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancelReply, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.Close, "Cancel reply", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        }
        }
        // Inline attachment panel - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - pushes the conversation up like the keyboard.
        AnimatedVisibility(
        visible = showAttachSheet,
        enter   = fadeIn(tween(140)) + expandVertically(tween(180)),
        exit    = fadeOut(tween(100)) + shrinkVertically(tween(160)),
        ) {
        AttachmentGridPanel(
        onCamera  = {
        showAttachSheet = false
        if (
        cameraPermission.status.isGranted &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
        cameraLauncher.launch(null)
        }
        else cameraPermission.launchPermissionRequest()
        },
        onGallery = { imagePicker.launch("image/*"); showAttachSheet = false },
        onVideo   = { videoPicker.launch("video/*"); showAttachSheet = false },
        onVoice   = {
        showAttachSheet = false
        android.widget.Toast.makeText(context, "Hold the mic button to record a voice note", android.widget.Toast.LENGTH_SHORT).show()
        },
        onFile     = { filePicker.launch("*/*"); showAttachSheet = false },
        onContact  = {
        showAttachSheet = false
        if (contactPermission.status.isGranted) contactPicker.launch(null)
        else contactPermission.launchPermissionRequest()
        },
        onLocation = {
        showAttachSheet = false
        if (locationPermission.status.isGranted) showLocationConfirm = true
        else locationPermission.launchPermissionRequest()
        },
        onWallet   = {
        showAttachSheet = false
        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED) showTransfer = true
        },
        onPoll = {
        showAttachSheet = false
        Toast.makeText(context, "Polls are coming soon", Toast.LENGTH_SHORT).show()
        },
        onHabesha = {
        showAttachSheet = false
        showEmojiBar = true
        },
        )
        }
        AnimatedVisibility(
        visible = showEmojiBar,
        enter   = fadeIn(tween(140)) + expandVertically(tween(160)),
        exit    = fadeOut(tween(100)),
        ) {
        EmojiBar(onPick = { emoji ->
        if (emoji.startsWith(":hab_")) {
        draft = emoji
        showEmojiBar = false
        } else {
        draft += emoji
        }
        })
        }
        Row(
        modifier      = Modifier
        .fillMaxWidth()
        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        ) {
        // Attach button
        Box(
        modifier      = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(Color(0xFF101820))
        .clickable {
        showEmojiBar = false
        keyboardController?.hide()
        showAttachSheet = !showAttachSheet
        },
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.AttachFile, "Attach",
        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
        )
        }

        Spacer(Modifier.width(8.dp))

        // Text input field
        Surface(
        modifier       = Modifier.weight(1f),
        shape      = RoundedCornerShape(26.dp),
        color      = Color(0xFF101820),
        tonalElevation = 0.dp,
        ) {
        val selectedHabeshaEmoji = remember(draft) {
        resolveHabeshaEmoji(draft)
        }
        Row(
        modifier      = Modifier.padding(
        start  = 16.dp,
        end    = 4.dp,
        top    = 10.dp,
        bottom = 10.dp,
        ),
        verticalAlignment = Alignment.Bottom,
        ) {
        BasicTextField(
        value      = draft,
        onValueChange = { draft = it },
        textStyle     = TextStyle(
        color      = if (selectedHabeshaEmoji == null) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        ),
        modifier      = Modifier
        .weight(1f)
        .onFocusChanged { if (it.isFocused) { showEmojiBar = false; showAttachSheet = false } },
        maxLines      = 6,
        decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
        if (selectedHabeshaEmoji != null) {
        Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        AsyncImage(
        model = selectedHabeshaEmoji.imageUrl,
        contentDescription = selectedHabeshaEmoji.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(42.dp),
        )
        Text(
        text = selectedHabeshaEmoji.label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        }
        if (draft.isBlank()) {
        Text(
        text     = "Message...",
        fontSize = 18.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        inner()
        }
        },
        )
        // Emoji bar toggle inside the field
        IconButton(
        onClick  = {
        showAttachSheet = false
        if (!showEmojiBar) keyboardController?.hide()
        showEmojiBar = !showEmojiBar
        },
        modifier = Modifier.size(32.dp),
        ) {
        Icon(
        Icons.Default.EmojiEmotions, "Emoji",
        tint     = if (showEmojiBar) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
        )
        }
        }
        }

        Spacer(Modifier.width(8.dp))

        // Send button (when text present) or mic (hold-to-record)
        AnimatedVisibility(
        visible = draft.isNotBlank(),
        enter   = scaleIn(animationSpec = tween(140)) + fadeIn(tween(140)),
        exit    = scaleOut(animationSpec = tween(100)) + fadeOut(tween(100)),
        ) {
        Box(
        modifier      = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(
        if (!sending)
        Brush.linearGradient(listOf(BubbleGradStart, BubbleGradEnd))
        else
        Brush.linearGradient(
        listOf(
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.outline,
        ),
        ),
        )
        .clickable(enabled = !sending) {
        val text = draft.trim()
        draft = ""
        if (editing) onSubmitEdit(text) else onSendText(text)
        },
        contentAlignment = Alignment.Center,
        ) {
        if (sending) {
        CircularProgressIndicator(
        color       = Color.White,
        strokeWidth = 2.dp,
        modifier    = Modifier.size(20.dp),
        )
        } else {
        Icon(
        Icons.AutoMirrored.Filled.Send, "Send",
        tint     = Color.White,
        modifier = Modifier.size(19.dp),
        )
        }
        }
        }
        if (draft.isBlank()) {
        // Tap = camera, long-press = record voice (release to send).
        Box(
        modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(BubbleGradStart, BubbleGradEnd)))
        .pointerInput(audioPermission.status.isGranted) {
        detectTapGestures(
        onTap = {
        Toast.makeText(context, "Hold to record a voice note", Toast.LENGTH_SHORT).show()
        },
        onLongPress = {
        if (audioPermission.status.isGranted) {
        if (voiceRecorder.start()) {
        isRecording = true
        } else {
        android.widget.Toast.makeText(context, "Microphone permission is required", android.widget.Toast.LENGTH_SHORT).show()
        }
        } else audioPermission.launchPermissionRequest()
        },
        onPress = {
        tryAwaitRelease()
        if (isRecording) {
        isRecording = false
        voiceRecorder.stop()?.let { (f, ms) ->
        scope.launch { onSendVoice(f, ms) }
        }
        }
        },
        )
        },
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.Mic, "Tap for camera, hold to record",
        tint     = Color.White,
        modifier = Modifier.size(20.dp),
        )
        }
        }
        }
        } // end normal-state Column
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiBar(onPick: (String) -> Unit) {
    val context = LocalContext.current
    val haptic  = androidx.compose.ui.platform.LocalHapticFeedback.current
    val accent  = if (isInDarkTheme) AdisAccent else brandAccent
    val store   = remember { EmojiStore(context) }

    var category  by remember { mutableStateOf(EmojiCategory.Smileys) }
    var query     by remember { mutableStateOf("") }
    var recents   by remember { mutableStateOf(store.recents()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var tone      by remember { mutableStateOf(store.skinTone()) }
    var toneFor   by remember { mutableStateOf<String?>(null) }   // long-pressed toneable base

    val surface   = if (isInDarkTheme) Color(0xFF141414) else MaterialTheme.colorScheme.surface
    val secondary = if (isInDarkTheme) AdisTextSec else MaterialTheme.colorScheme.onSurfaceVariant

    fun choose(emoji: String) {
        store.addRecent(emoji); recents = store.recents()
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        onPick(emoji)
    }

    val searching = query.isNotBlank()
    val unicodeEmojis: List<String> = when {
        searching -> EmojiData.search(query)
        category == EmojiCategory.Recent     -> recents
        category == EmojiCategory.Favorites  -> favorites
        category == EmojiCategory.Habesha    -> emptyList()
        else -> EmojiData.categoryEmojis(category)
    }
    val emojiGridItems = remember(unicodeEmojis) { EmojiData.gridItems(unicodeEmojis) }
    val showHabesha = !searching && category == EmojiCategory.Habesha

    Column(Modifier.fillMaxWidth().height(320.dp).background(surface)) {
        // \u2500\u2500 Search \u2500\u2500
        Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp).height(42.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(if (isInDarkTheme) AdisCard else MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Search, null, tint = secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
        value = query, onValueChange = { query = it },
        modifier = Modifier.weight(1f), singleLine = true,
        textStyle = TextStyle(color = if (isInDarkTheme) AdisTextPri else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
        cursorBrush = SolidColor(accent),
        decorationBox = { inner -> if (query.isBlank()) Text("Search emojis\u2026", color = secondary, fontSize = 15.sp); inner() },
        )
        if (query.isNotBlank()) Icon(Icons.Default.Close, "Clear", tint = secondary,
        modifier = Modifier.size(18.dp).clickable { query = "" })
        }

        // \u2500\u2500 Skin-tone strip (appears on long-press of a toneable emoji) \u2500\u2500
        toneFor?.let { base ->
        Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        (0..5).forEach { t ->
        Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
        .background(if (t == tone) accent.copy(alpha = 0.18f) else Color.Transparent)
        .clickable {
        tone = t; store.setSkinTone(t); toneFor = null
        choose(applySkinTone(base, t))
        },
        contentAlignment = Alignment.Center,
        ) { Text(applySkinTone(base, t), fontSize = 22.sp) }
        }
        }
        }

        // \u2500\u2500 Emoji grid (virtualized) \u2500\u2500
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (showHabesha) {
        LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
        gridItems(HabeshaExactPack, key = { it.code }) { e ->
        Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onPick(e.token) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        AsyncImage(e.imageUrl, e.label, contentScale = ContentScale.Fit,
        modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
        Spacer(Modifier.height(2.dp))
        Text(e.label, color = secondary, fontSize = 9.sp, maxLines = 1)
        }
        }
        }
        } else if (unicodeEmojis.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
        when {
        searching -> "No emojis found"
        category == EmojiCategory.Recent -> "No recent emojis yet"
        category == EmojiCategory.Favorites -> "Long-press an emoji to favorite it"
        else -> ""
        },
        color = secondary, fontSize = 14.sp,
        )
        }
        } else {
        LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
        gridItems(emojiGridItems, key = { it.key }) { item ->
        val e = item.emoji
        val shown = applySkinTone(e, tone)
        Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
        .combinedClickable(
        onClick = { choose(shown) },
        onLongClick = {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (supportsSkinTone(e)) toneFor = e
        else { store.toggleFavorite(e); favorites = store.favorites() }
        },
        ),
        contentAlignment = Alignment.Center,
        ) { Text(shown, fontSize = 26.sp) }
        }
        }
        }
        }

        // \u2500\u2500 Horizontal category bar (Telegram-style) \u2500\u2500
        HorizontalDivider(color = if (isInDarkTheme) AdisBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        items(EmojiCategory.entries.toList()) { cat ->
        val active = !searching && cat == category
        Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
        .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent)
        .clickable { category = cat; query = ""; toneFor = null },
        contentAlignment = Alignment.Center,
        ) { Text(cat.icon, fontSize = 20.sp) }
        }
        }
    }
}

// `glyph` is a Unicode placeholder; set `drawableRes` once custom artwork is
// added to res/drawable and swap the Text() for an Image() in EmojiBar.
private data class HabeshaEmoji(
    val label: String,
    val code: String,
    val fallback: String,
    val drawableName: String? = null,
    val lottieAsset: String? = null,
) {
    constructor(
        label: String,
        glyph: String,
        drawableName: String? = null,
        lottieAsset: String? = null,
    ) : this(label, drawableName ?: habeshaCodeFor(label), glyph, drawableName, lottieAsset)

    val token: String get() = ":$code:"
    val imageUrl: String get() = "$HabeshaEmojiBaseUrl/$code.png?v=20260602"
}

private val HabeshaEmojiBaseUrl = com.adischat.app.BuildConfig.ADISCHAT_MEDIA_BASE_URL.trimEnd('/') + "/emoji/habesha"

private fun habeshaCodeFor(label: String): String = when (label.lowercase(Locale.US)) {
    "taxi" -> "hab_addis_taxi"
    "bajaj" -> "hab_addis_bajaj"
    "doro wat" -> "hab_food_doro_wat"
    "ethiopia heart" -> "hab_expr_flag_heart"
    "smile" -> "hab_face_smile"
    "laugh" -> "hab_face_laugh"
    "love" -> "hab_face_love"
    "thinking" -> "hab_face_thinking"
    "wink" -> "hab_face_wink"
    "cry" -> "hab_face_cry"
    "angry" -> "hab_face_angry"
    "cool" -> "hab_face_cool"
    "blessing" -> "hab_face_blessing"
    "respect" -> "hab_face_respect"
    "ayzoh" -> "hab_expr_ayzoh"
    "gobez" -> "hab_expr_gobez"
    "eshi" -> "hab_expr_eshi"
    else -> "hab_face_${label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')}"
}

private val HabeshaFacePack = listOf(
    HabeshaEmoji("Smile", "hab_face_smile", "\uD83D\uDE42"),
    HabeshaEmoji("Laugh", "hab_face_laugh", "\uD83D\uDE02"),
    HabeshaEmoji("Love", "hab_face_love", "\uD83D\uDE0D"),
    HabeshaEmoji("Thinking", "hab_face_thinking", "\uD83E\uDD14"),
    HabeshaEmoji("Wink", "hab_face_wink", "\uD83D\uDE09"),
    HabeshaEmoji("Cry", "hab_face_cry", "\uD83D\uDE22"),
    HabeshaEmoji("Angry", "hab_face_angry", "\uD83D\uDE20"),
    HabeshaEmoji("Cool", "hab_face_cool", "\uD83D\uDE0E"),
    HabeshaEmoji("Blessing", "hab_face_blessing", "\uD83D\uDE4F"),
    HabeshaEmoji("Respect", "hab_face_respect", "\uD83E\uDEE1"),
    HabeshaEmoji("Ayzoh", "hab_expr_ayzoh", "\uD83E\uDD17"),
    HabeshaEmoji("Gobez", "hab_expr_gobez", "\uD83D\uDCAA"),
    HabeshaEmoji("Eshi", "hab_expr_eshi", "\uD83D\uDC4D")
)

private val HabeshaExactPack = listOf(
    HabeshaEmoji("Taxi",      "hab_addis_taxi",      "Taxi"),
    HabeshaEmoji("Bajaj",      "hab_addis_bajaj",     "Bajaj"),
    HabeshaEmoji("Doro Wat",       "hab_food_doro_wat",   "Doro"),
    HabeshaEmoji("Ethiopia Heart", "hab_expr_flag_heart", "Heart"),
    HabeshaEmoji("Love",      "hab_face_love",       "Love"),
    HabeshaEmoji("Cool",      "hab_face_cool",       "Cool"),
)


private val AllHabeshaEmojis: List<HabeshaEmoji>
    get() = (HabeshaExactPack + HabeshaFacePack + HabeshaPack).distinctBy { it.code }

private fun resolveHabeshaEmoji(text: String): HabeshaEmoji? {
    val raw = text.trim()
    if (raw.isBlank()) return null
    val normalized = raw.removePrefix(":").removeSuffix(":").lowercase(Locale.US)
    return AllHabeshaEmojis.firstOrNull { e ->
        raw == e.token ||
        normalized == e.code.lowercase(Locale.US) ||
        normalized == e.label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_') ||
        raw.substringAfterLast('/').substringBefore('?').equals("${e.code}.png", ignoreCase = true)
    }
}
private val HabeshaPack = listOf(
    HabeshaEmoji("Taxi", "\uD83D\uDE95", drawableName = "hab_addis_taxi"),
    HabeshaEmoji("Bajaj", "\uD83D\uDEF5", drawableName = "hab_bajaj"),
    HabeshaEmoji("Doro Wat", "\uD83C\uDF72", drawableName = "hab_food_doro_wat"),
    HabeshaEmoji("Ethiopia Heart", "\u2764\uFE0F", drawableName = "hab_ethiopia_heart"),
    HabeshaEmoji("Love", "\uD83D\uDE0D", drawableName = "hab_face_love"),
    HabeshaEmoji("Cool", "\uD83D\uDE0E", drawableName = "hab_face_cool"),
    HabeshaEmoji("Smile", "\uD83D\uDE42", drawableName = "hab_face_smile"),
    HabeshaEmoji("Laugh", "\uD83D\uDE02", drawableName = "hab_face_laugh"),
    HabeshaEmoji("Thinking", "\uD83E\uDD14", drawableName = "hab_face_thinking"),
    HabeshaEmoji("Wink", "\uD83D\uDE09", drawableName = "hab_face_wink"),
    HabeshaEmoji("Blessing", "\uD83D\uDE4F", drawableName = "hab_face_blessing"),
    HabeshaEmoji("Gobez", "\uD83D\uDCAA", drawableName = "hab_expr_gobez"),
    HabeshaEmoji("Eshi", "\uD83D\uDC4D", drawableName = "hab_expr_eshi")
)

// Inline attachment grid - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - rendered inside the input bar so it pushes the
// conversation up (like the keyboard/emoji panel), never overlaying messages.
@Composable
private fun AttachmentGridPanel(
    onCamera  : () -> Unit,
    onGallery : () -> Unit,
    onVideo   : () -> Unit,
    onVoice   : () -> Unit,
    onFile    : () -> Unit,
    onContact : () -> Unit,
    onLocation: () -> Unit,
    onWallet  : () -> Unit,
    onPoll    : () -> Unit,
    onHabesha : () -> Unit,
) {
    data class AttachAction(
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val onClick: () -> Unit,
    )

    val context = LocalContext.current
    val isDark = isInDarkTheme
    val iconCircle = if (isDark) Color.White.copy(alpha = 0.075f) else Color(0xFFEFF4F1)
    val labelColor = if (isDark) Color(0xFFDDE7E2) else Color(0xFF24302A)
    val accent = Color(0xFF18C964)

    // Voice Note (mic beside the box) and Poll removed - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - kept compact, single row.
    val actions = listOfNotNull(
        AttachAction("Camera", Icons.Default.PhotoCamera, onCamera),
        AttachAction("Photos", Icons.Default.Image, onGallery),
        AttachAction("Video", Icons.Default.Videocam, onVideo),
        AttachAction("Document", Icons.Default.InsertDriveFile, onFile),
        AttachAction("Location", Icons.Default.LocationOn, onLocation),
        AttachAction("Contact", Icons.Default.Person, onContact),
        AttachAction("Poll", Icons.Default.Poll, onPoll),
        // Payment hidden while the wallet feature is paused.
        if (com.adischat.app.FeatureFlags.WALLET_ENABLED) AttachAction("Payment", Icons.Default.AccountBalanceWallet, onWallet) else null,
        AttachAction("Voice", Icons.Default.Mic, onVoice),
        AttachAction("Habesha", Icons.Default.EmojiEmotions, onHabesha),
    )

    Row(
        modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        actions.forEach { action ->
        ModernAttachmentAction(
        label = action.label,
        icon = action.icon,
        circleColor = iconCircle,
        labelColor = labelColor,
        accent = accent,
        onClick = action.onClick,
        modifier = Modifier.width(64.dp),
        )
        }
    }
}

@Composable
private fun ModernAttachmentAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    circleColor: Color,
    labelColor: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
        modifier = Modifier
        .size(50.dp)
        .clip(CircleShape)
        .background(circleColor)
        .border(1.dp, accent.copy(alpha = 0.13f), CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accent,
        modifier = Modifier.size(24.dp),
        )
        }
        Text(
        text = label,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        color = labelColor.copy(alpha = 0.90f),
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.heightIn(min = 28.dp),
        )
    }
}

// WALLET TRANSFER

/**
 * Send money to the chat peer straight from the AdisPay wallet.
 * Uses the existing [WalletRepository.send] backend (recipient + amount + PIN);
 * posts a confirmation message into the thread on success.
 */
@Composable
private fun TransferDialog(
    peerName  : String,
    peerUid   : String = "",
    accent    : Color,
    onDismiss : () -> Unit,
    onSendText: (String) -> Unit,
) {
    val scope      = rememberCoroutineScope()
    val walletRepo = remember { WalletRepository() }
    val chatRepo: ChatRepository = remember { ChatRepository() }
    val isDark     = isInDarkTheme

    val surface   = if (isDark) Color(0xFF13171A) else Color.White
    val fieldBg   = if (isDark) Color(0xFF1B2024) else Color(0xFFF3F5F7)
    val textMain  = if (isDark) Color.White else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF8B949E) else Color(0xFF6B7280)
    val purple    = Color(0xFF8B5CF6)

    var step      by remember { mutableStateOf(0) }      // 0 = details, 1 = PIN
    var amount    by remember { mutableStateOf("") }
    var pin       by remember { mutableStateOf("") }
    var note      by remember { mutableStateOf("") }
    var sending   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    // Resolve the recipient's @username from the chat peer.
    var resolving by remember { mutableStateOf(true) }
    var username  by remember { mutableStateOf("") }
    LaunchedEffect(peerUid, peerName) {
        resolving = true
        username = runCatching {
        val results: List<UserSummary> = chatRepo.searchUsers(peerName.trim())
        val match = results.firstOrNull { u: UserSummary -> u.uid == peerUid } ?: results.firstOrNull()
        match?.username ?: ""
        }.getOrDefault("")
        resolving = false
    }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val hasUsername = username.isNotBlank()
    val canContinue = !resolving && hasUsername && amountValue > 0.0
    val canSend     = !sending && pin.length >= 4

    Dialog(onDismissRequest = { if (!sending) onDismiss() }) {
        Surface(shape = RoundedCornerShape(26.dp), color = surface) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {

        // Header
        Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
        .background(purple.copy(alpha = 0.16f)).align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.AccountBalanceWallet, null, tint = purple, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(tr("Send money"), color = textMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))

        // Recipient card
        Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fieldBg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
        Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        ) { Text(peerName.firstOrNull()?.uppercase() ?: "?", color = accent, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(peerName, color = textMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
        resolving   -> Text(tr("Checking username- - - - - - - - - - - - - - - - - - - - - - - - - - -"), color = textMuted, fontSize = 13.sp)
        hasUsername -> Text("@$username", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        else -> Text(tr("No username - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - can't receive money"), color = Color(0xFFEF4444), fontSize = 13.sp)
        }
        }
        if (resolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
        else if (hasUsername) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (step == 0) {
        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - Step 1: amount + note - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        TransferField(
        value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
        placeholder = "0.00", label = "Amount (ETB)", fieldBg = fieldBg, textMain = textMain, textMuted = textMuted,
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, big = true,
        )
        Spacer(Modifier.height(12.dp))
        TransferField(
        value = note, onValueChange = { note = it },
        placeholder = "Note (optional)", label = "Note", fieldBg = fieldBg, textMain = textMain, textMuted = textMuted,
        )
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFEF4444), fontSize = 14.sp) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = textMuted) }
        Spacer(Modifier.width(8.dp))
        Button(
        onClick = { error = null; step = 1 },
        enabled = canContinue,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) { Text(tr("Continue"), fontWeight = FontWeight.Bold) }
        }
        } else {
        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - Step 2: PIN - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        val prettyAmt = if (amountValue % 1.0 == 0.0) amountValue.toLong().toString() else amount
        Text(tr("You're sending"), color = textMuted, fontSize = 13.sp,
        modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("ETB $prettyAmt", color = textMain, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text(tr("Enter your wallet PIN to confirm"), color = textMuted, fontSize = 14.sp,
        modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
        value = pin,
        onValueChange = { new -> if (!sending) pin = new.filter { it.isDigit() }.take(6) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !sending,
        textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 24.sp, letterSpacing = 12.sp, color = textMain),
        placeholder = { Text(tr("0000"), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        color = textMuted, fontSize = 24.sp, letterSpacing = 12.sp) },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent),
        )
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFEF4444), fontSize = 14.sp,
        modifier = Modifier.align(Alignment.CenterHorizontally)) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (!sending) { step = 0; error = null } }) { Text(tr("Back"), color = textMuted) }
        Spacer(Modifier.weight(1f))
        Button(
        onClick = {
        sending = true; error = null
        scope.launch {
        walletRepo.send(username, amountValue, pin)
        .onSuccess { state ->
        val cur = state.currency.ifBlank { "ETB " }
        val pretty = if (amountValue % 1.0 == 0.0) amountValue.toLong().toString() else amount
        val noteSuffix = if (note.isNotBlank()) " - ${note.trim()}" else ""
        onSendText("Sent $cur$pretty to @$username$noteSuffix")
        sending = false; onDismiss()
        }
        .onFailure {
        error = CloudflareClient.friendlyErrorMessage(it, "Transfer failed")
        sending = false
        }
        }
        },
        enabled = canSend,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) {
        if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(tr("Send money"), fontWeight = FontWeight.Bold)
        }
        }
        }
        }
        }
    }
}

@Composable
private fun TransferField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    fieldBg: Color,
    textMain: Color,
    textMuted: Color,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    big: Boolean = false,
) {
    Column {
        Text(label, color = textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fieldBg)
        .padding(horizontal = 16.dp, vertical = if (big) 14.dp else 13.dp),
        contentAlignment = Alignment.CenterStart,
        ) {
        if (value.isEmpty()) Text(placeholder, color = textMuted, fontSize = if (big) 24.sp else 16.sp)
        BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = TextStyle(color = textMain, fontSize = if (big) 24.sp else 16.sp,
        fontWeight = if (big) FontWeight.Bold else FontWeight.Normal),
        cursorBrush = SolidColor(textMain),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}

// MESSAGE BUBBLES

// - - - Telegram-style in-conversation call record - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// Outgoing calls align RIGHT (like sent messages); incoming align LEFT.
@Composable
private fun CallSystemMessage(
    message: Message,
    isMine: Boolean,      // true - - - I'm the caller - - - OUTGOING
    onCallAgain: (CallMode) -> Unit,
) {
    val isVideo  = message.callType == "video"
    val mode     = if (isVideo) CallMode.Video else CallMode.Voice
    val status   = message.callStatus
    val outgoing = isMine
    val typeWord = if (isVideo) "Video" else "Voice"
    val time     = messageTime(message.timestamp)
    val dirWord  = if (outgoing) "Outgoing" else "Incoming"
    val red      = Color(0xFFEF4444)
    val accent   = if (isInDarkTheme) AdisAccent else brandAccent

    // (icon, tint, title, subtitle, actionLabel, isError)
    data class Spec(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val tint: Color, val title: String, val sub: String, val action: String?, val error: Boolean,
    )
    val baseIcon = if (isVideo) Icons.Default.Videocam else Icons.Default.Call
    val spec = when (status) {
        "completed" -> Spec(baseIcon, accent, "$dirWord $typeWord Call",
        "${callDurationLabel(message.callDuration)} - $time",
        if (outgoing) "Call again" else "Call back", false)
        "missed" -> if (outgoing)
        Spec(Icons.Default.CallMissedOutgoing, accent, "No Answer", "Outgoing - $time", "Call again", false)
        else
        Spec(Icons.Default.CallMissed, red, "Missed $typeWord Call", "Incoming - $time", "Call back", true)
        "cancelled" -> if (outgoing)
        Spec(Icons.Default.PhoneDisabled, AdisTextSec, "Call Cancelled", "Cancelled by you - $time", "Call again", false)
        else
        Spec(Icons.Default.CallMissed, red, "Missed $typeWord Call", "Cancelled by contact - $time", "Call back", true)
        "rejected" -> if (outgoing)
        Spec(Icons.Default.Block, accent, "Call Rejected", "Outgoing - $time", "Call again", false)
        else
        Spec(Icons.Default.Block, AdisTextSec, "Call Rejected", "You declined - $time", "Call back", false)
        "failed" -> Spec(Icons.Default.ErrorOutline, red, "$typeWord Call Failed", "$dirWord - $time", "Call again", true)
        else -> Spec(baseIcon, accent, "$dirWord $typeWord Call", time, null, false)
    }

    val bubbleBg = when {
        spec.error -> red.copy(alpha = 0.12f)
        outgoing -> accent.copy(alpha = if (isInDarkTheme) 0.16f else 0.14f)
        isInDarkTheme -> Color(0xFF141A24)
        else -> Color(0xFFEFF3F8)
    }
    val border     = if (isInDarkTheme) AdisBorder else Color.Black.copy(alpha = 0.05f)
    val titleColor = if (spec.error) red else if (isInDarkTheme) AdisTextPri else Color(0xFF111827)
    val subColor   = if (isInDarkTheme) AdisTextSec else Color(0xFF6B7280)

    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 12.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Row(
        Modifier.widthIn(max = 268.dp)
        .clip(RoundedCornerShape(18.dp)).background(bubbleBg)
        .border(1.dp, border, RoundedCornerShape(18.dp))
        .clickable { onCallAgain(mode) }
        .padding(start = 9.dp, end = if (spec.action != null) 8.dp else 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(spec.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(spec.icon, null, tint = spec.tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f, fill = false)) {
        Text(spec.title, color = titleColor, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(1.dp))
        Text(spec.sub, color = subColor, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (spec.action != null) {
        Spacer(Modifier.width(10.dp))
        Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(spec.tint.copy(alpha = 0.18f))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text(spec.action, color = spec.tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        }
    }
}

private fun callDurationLabel(secs: Int): String =
    if (secs >= 3600) "%dh %02dm".format(secs / 3600, (secs % 3600) / 60)
    else if (secs >= 60) "%dm %02ds".format(secs / 60, secs % 60)
    else "${secs}s"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message     : Message,
    currentUid  : String,
    isCommunity : Boolean,
    useDirectSentBubble: Boolean,
    sameSenderBefore: Boolean = false,
    sameSenderAfter : Boolean = false,
    canModerate : Boolean,
    selected    : Boolean,
    starred     : Boolean,
    pinned      : Boolean,
    selectionMode: Boolean,
    highlighted : Boolean = false,
    onReact     : (String) -> Unit,
    onDelete    : () -> Unit,
    onModerate  : (String) -> Unit,
    onForward   : () -> Unit,
    onOpenUrl   : (String) -> Unit,
    onSelect    : () -> Unit,
    onRetry     : () -> Unit,
    onOpenActions: () -> Unit,
    onOpenSender: () -> Unit = {},
    onReplyJump : (String) -> Unit = {},
) {
    // System / event message
    if (message.senderRole == "system") {
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Text(
        text     = message.text,
        fontSize = 15.sp,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(Color(0xFF101820))
        .padding(horizontal = 14.dp, vertical = 5.dp),
        )
        }
        return
    }

    val isMine      = message.senderId == currentUid
    val useBlackSentBubble = isMine && useDirectSentBubble
    // Media bubbles (image/video/voice) open a preview on single tap, so their long press
    // opens the action menu; everything else is text-like (tap = actions, long press = select).
    val isMediaMessage    = message.type in setOf("image", "video", "voice")
    var showReactions     by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title      = { Text(tr("Delete message")) },
        text      = { Text(tr("This will remove the message from this chat.")) },
        confirmButton    = {
        TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text(tr("Delete")) }
        },
        dismissButton    = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text(tr("Cancel")) }
        },
        )
    }

    // Capture theme values in composable scope before lambdas
    val recvColor  = bubbleReceivedColor
    val recvBorder = bubbleReceivedBorder
    val isHabeshaEmojiMessage = remember(message.text) {
        resolveHabeshaEmoji(message.text) != null
    }
    val isStandaloneContent = message.type == "image" ||
        message.type == "video" ||
        message.type == "voice" ||
        isHabeshaEmojiMessage ||
        isStandaloneEmojiText(message.text)

    AnimatedVisibility(
        visible = true,
        enter   = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 4 } + expandVertically(),
    ) {
        Column(
        modifier      = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        // Telegram-style full-row highlight for a selected (or reply-jumped) message.
        .background(
        when {
        selected    -> BrandGreen.copy(alpha = 0.18f)
        highlighted  -> BrandGreen.copy(alpha = 0.12f)
        else -> Color.Transparent
        },
        )
        .padding(vertical = if (selected || highlighted) 4.dp else 0.dp, horizontal = if (selected || highlighted) 4.dp else 0.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
        AnimatedVisibility(
        visible = showReactions,
        enter   = scaleIn(initialScale = 0.75f, animationSpec = tween(180)) +
        fadeIn(animationSpec = tween(180)),
        exit    = scaleOut(targetScale = 0.75f, animationSpec = tween(130)) +
        fadeOut(animationSpec = tween(130)),
        ) {
        ReactionActionRail(
        isMine      = isMine,
        isCommunity = isCommunity,
        canModerate = canModerate,
        onReact     = { emoji -> showReactions = false; onReact(emoji) },
        onDelete    = { showReactions = false; showDeleteConfirm = true },
        onForward   = { showReactions = false; onForward() },
        onSuspend   = { showReactions = false; onModerate("suspend") },
        onBlock     = { showReactions = false; onModerate("block") },
        )
        }
        val bubbleShape = RoundedCornerShape(
        topStart    = if (!isMine && sameSenderBefore) 8.dp else 22.dp,
        topEnd      = if (isMine && sameSenderBefore) 8.dp else 22.dp,
        bottomStart = if (!isMine && sameSenderAfter) 8.dp else 22.dp,
        bottomEnd   = if (isMine && sameSenderAfter) 8.dp else 22.dp,
        )

        Row(
        // Wider bubbles (was 0.72) so messages wrap less and read cleaner - -
        // still sided/not full-width for a modern messenger look.
        modifier      = Modifier.fillMaxWidth(0.86f),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Top,
        ) {
        // Group chats show the sender's avatar beside received messages on the
        // FIRST message of a consecutive run (then compact bubbles below), so
        // groups read clearly differently from a 1:1 DM. Tap - - - open sender.
        // Direct chats / channels are unaffected.
        if (isCommunity && !isMine) {
        if (!sameSenderBefore) {
        Box(Modifier.clickable(onClick = onOpenSender)) {
        ChatAvatar(message.senderPhotoUrl, initialsOf(message.senderName.ifBlank { "?" }), 32.dp, accentFor(message.senderId))
        }
        } else {
        Spacer(Modifier.width(32.dp))
        }
        Spacer(Modifier.width(8.dp))
        }
        Column(
        modifier = (if (isStandaloneContent) {
        Modifier
        } else {
        Modifier
        .shadow(
        elevation    = 2.dp,
        shape      = bubbleShape,
        ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor    = Color.Black.copy(alpha = 0.07f),
        )
        .clip(bubbleShape)
        .background(
        if (useBlackSentBubble)
        Brush.linearGradient(listOf(Color(0xFF101010), Color(0xFF050505)))
        else if (isMine)
        // Group sent bubble: premium dark green-tinted glass
        // (was flat neon green) - - white text reads cleanly on it.
        Brush.linearGradient(listOf(Color(0xFF182A1F), Color(0xFF0E1A13)))
        else
        Brush.linearGradient(listOf(recvColor, recvColor)),
        )
        .border(
        width  = if (isMine && !useBlackSentBubble) 1.dp else 0.5.dp,
        color  = if (useBlackSentBubble) Color.White.copy(alpha = 0.08f)
        else if (isMine) AdisAccent.copy(alpha = 0.45f)   // soft green accent glow
        else recvBorder,
        shape  = bubbleShape,
        )
        })
        .combinedClickable(
        // Telegram/WhatsApp gestures. The whole bubble is touchable (leaf
        // content - - text, images, video, voice - - routes its own long press
        // here too, so long press works ANYWHERE, not just the meta line).
        //  - - selection mode - - - tap toggles the message
        //  - - text bubble    - - - tap opens actions, long press selects
        //  - - media bubble   - - - long press opens actions (tap = preview, on leaf)
        onClick     = {
        when {
        selectionMode -> onSelect()
        !isMediaMessage -> onOpenActions()
        else -> {}
        }
        },
        onLongClick = {
        showReactions = false
        when {
        selectionMode  -> onSelect()
        isMediaMessage -> onOpenActions()
        else -> onSelect()
        }
        },
        )
        .animateContentSize()
        .padding(
        horizontal = if (isStandaloneContent) 2.dp else 15.dp,
        vertical = if (isStandaloneContent) 2.dp else 9.dp,
        ),
        ) {
        // Sender name - - on the FIRST message of a run (groups) or always
        // for the support admin. Tap - - - open the sender.
        if (!isMine) {
        val senderLabel = when {
        message.senderRole == "admin" -> "AdisChat Support"
        isCommunity && message.senderName.isNotBlank() && !sameSenderBefore -> message.senderName
        else -> null
        }
        if (senderLabel != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        text       = senderLabel,
        color      = if (message.senderRole == "admin") Primary
        else accentFor(message.senderId),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenSender),
        )
        if (message.senderVerificationLevel >= 3 || message.senderVerified) {
        Spacer(Modifier.width(5.dp))
        UserVerifiedBadge(Modifier.size(15.dp))
        }
        }
        Spacer(Modifier.height(3.dp))
        }
        }
        // Telegram/WhatsApp-style "Forwarded" label.
        if (message.forwarded && message.type != "deleted") {
        Text(
        text       = "Forwarded",
        color      = if (isMine) Color.White.copy(0.72f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize   = 12.sp,
        fontStyle  = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        }
        // Leaf gesture routing so long press works ANYWHERE on the bubble.
        val onLeafLongPress: () -> Unit = { showReactions = false; onOpenActions() }
        val onTextTap: () -> Unit = { if (selectionMode) onSelect() else onOpenActions() }
        val onTextLongPress: () -> Unit = { showReactions = false; onSelect() }
        when (message.type) {
        "deleted" -> Text(
        text      = "Message deleted",
        color     = if (isMine) Color.White.copy(0.60f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 17.sp,
        fontStyle = FontStyle.Italic,
        )
        "image"   -> ImageBubbleContent(message, isMine, selectionMode, onSelect, onLeafLongPress)
        "video"   -> VideoBubbleContent(message, isMine, selectionMode, onSelect, onLeafLongPress)
        "voice"   -> VoiceBubbleContent(message, isMine, selectionMode, onSelect, onLeafLongPress)
        else      -> TextBubbleContent(message, isMine, onOpenUrl, onTextTap, onTextLongPress, selectionMode, onSelect, onReplyJump)
        }
        ReactionSummary(message.reactions)
        OptimisticMessageStatus(message = message, isMine = isMine, onRetry = onRetry)
        if (starred || pinned) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (starred) Icon(Icons.Default.Star, "Starred", tint = if (isMine) Color.White.copy(alpha = 0.80f) else Amber, modifier = Modifier.size(13.dp))
        if (pinned) Icon(Icons.Default.PushPin, "Pinned", tint = if (isMine) Color.White.copy(alpha = 0.80f) else BrandGreen, modifier = Modifier.size(13.dp))
        }
        }
        }
        }
        }
    }
}

@Composable
private fun CurvedForwardIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = "Forward",
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Reply,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(scaleX = -1f),
    )
}

@Composable
private fun ReactionSummary(reactions: Map<String, String>) {
    val safeReactions = reactions.values
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length <= 16 }
        .groupingBy { it }
        .eachCount()
    if (safeReactions.isEmpty()) return
    Spacer(Modifier.height(5.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        safeReactions.forEach { (emoji, count) ->
        Row(
        modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(Color(0xFF18C964).copy(alpha = 0.12f))
        .border(1.dp, Color(0xFF18C964).copy(alpha = 0.28f), RoundedCornerShape(999.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        Text(emoji, fontSize = 17.sp)
        if (count > 1) Text(
        text     = count.toString(),
        fontSize = 14.sp,
        color    = Color(0xFFA0A0A8),
        fontWeight = FontWeight.Medium,
        )
        }
        }
    }
}

private fun listMessageKey(message: Message, index: Int): String {
    val id = message.id.ifBlank { "local-${message.senderId}-${message.timestamp}-${message.text.hashCode()}" }
    // Incoming realtime + optimistic sync can briefly expose duplicate ids. Compose
    // LazyColumn keys must be unique, or opening the conversation can crash.
    return "msg-$id-$index"
}

@Composable
private fun OptimisticMessageStatus(message: Message, isMine: Boolean, onRetry: () -> Unit) {
    // Delivery state (sending / sent / delivered / seen) is only ever shown on YOUR
    // own outgoing messages - - the recipient must never see ticks on messages they
    // received. (Telegram/WhatsApp behavior.)
    if (!isMine) return
    val status = message.localStatus
    if (status.isBlank()) return
    // In groups, never show delivered/read state (keep upload progress + failed).
    if (!LocalShowDeliveryStatus.current && status in setOf("sent", "delivered", "seen")) return
    Spacer(Modifier.height(5.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
        "sending", "uploading" -> {
        CircularProgressIndicator(
        modifier = Modifier.size(12.dp),
        strokeWidth = 1.5.dp,
        color = if (isMine) Color.White.copy(alpha = 0.78f) else BrandGreen,
        )
        Spacer(Modifier.width(5.dp))
        Text(
        text = if (status == "uploading") "Uploading ${message.localProgress.coerceIn(0, 100)}%" else "Sending",
        color = if (isMine) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        )
        }
        "sent" -> {
        Icon(Icons.Default.Done, null, tint = if (isMine) Color.White.copy(alpha = 0.74f) else BrandGreen, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Sent", color = if (isMine) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        "delivered" -> {
        Icon(Icons.Default.Done, null, tint = BrandGreen, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Delivered", color = if (isMine) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        "seen" -> {
        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Seen", color = if (isMine) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        "failed" -> {
        Text(
        text = "Failed",
        color = DeleteRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
        onClick = onRetry,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
        ) {
        Text("Retry", color = BrandGreen, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        }
        }
    }
}

@Composable
private fun ColumnScope.TextBubbleContent(
    m: Message,
    isMine: Boolean,
    onOpenUrl: (String) -> Unit,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onReplyJump: (String) -> Unit = {},
) {
    // Structured reply: show a tappable quoted chip, then render only the body below.
    val reply = remember(m.text, m.replyToMessageId, m.replyPreview, m.replySenderName) { replyRefFor(m) }
    if (reply != null) {
        QuotedReplyChip(reply, isMine, onClick = { onReplyJump(reply.targetId) })
        Spacer(Modifier.height(5.dp))
        val emojiOnly = remember(reply.body) { isStandaloneEmojiText(reply.body) }
        LinkifiedText(
        text       = reply.body,
        color      = if (isMine) Color.White else Color(0xFFFFFFFF),
        linkColor  = if (isMine) Color(0xFFCFE4FF) else Color(0xFF7FB8FF),
        fontSize   = if (emojiOnly) 44.sp else 16.sp,
        lineHeight = if (emojiOnly) 48.sp else 22.sp,
        onOpenUrl  = onOpenUrl,
        onTap      = onTap,
        onLongPress = onLongPress,
        )
        Spacer(Modifier.height(4.dp))
        MessageMeta(messageTime(m.timestamp), isMine, message = m, modifier = Modifier.align(Alignment.End))
        return
    }

    val habeshaEmoji = remember(m.text) { resolveHabeshaEmoji(m.text) }
    when {
        habeshaEmoji != null -> {
        AsyncImage(
        model = habeshaEmoji.imageUrl,
        contentDescription = habeshaEmoji.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(92.dp),
        )
        Spacer(Modifier.height(2.dp))
        MessageMeta(messageTime(m.timestamp), isMine, message = m, modifier = Modifier.align(Alignment.End))
        }
        com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED && isPaymentMessage(m.text) -> PaymentMessageCard(m, isMine, messageTime(m.timestamp), Modifier.align(Alignment.End))
        isLocationMessage(m.text) -> LocationMessageCard(m, isMine, messageTime(m.timestamp), Modifier.align(Alignment.End), selectionMode, onToggleSelect, onLongPress)
        else -> {
        val emojiOnly = remember(m.text) { isStandaloneEmojiText(m.text) }
        LinkifiedText(
        text       = m.text,
        color      = if (isMine) Color.White else Color(0xFFFFFFFF),
        // Soft blue link tint that reads cleanly on the dark bubble - - no white
        // highlight box, no underline (handled in LinkifiedText).
        linkColor  = if (isMine) Color(0xFFCFE4FF) else Color(0xFF7FB8FF),
        fontSize = if (emojiOnly) 44.sp else 16.sp,
        lineHeight = if (emojiOnly) 48.sp else 22.sp,
        onOpenUrl = onOpenUrl,
        onTap = onTap,
        onLongPress = onLongPress,
        )
        Spacer(Modifier.height(4.dp))
        MessageMeta(messageTime(m.timestamp), isMine, message = m, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun PaymentMessageCard(message: Message, isMine: Boolean, time: String, metaModifier: Modifier = Modifier) {
    val parsed = remember(message.text) { parsePaymentText(message.text) }
    Column(
        modifier = Modifier
        .widthIn(min = 230.dp, max = 300.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF0B2A1B), Color(0xFF123B2A))))
        .border(1.dp, Color(0xFF18C964).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF18C964).copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF34D399), modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Money Sent"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(tr("Completed"), color = Color(0xFF9EF2BC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        }
        Spacer(Modifier.height(10.dp))
        Text(parsed.amount, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("To ${parsed.recipient}", color = Color(0xFFA8B3AD), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (parsed.note.isNotBlank()) Text(parsed.note, color = Color(0xFFDCE7E1), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("View Receipt"), color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        MessageMeta(time, isMine, message = message, modifier = metaModifier)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocationMessageCard(
    message: Message,
    isMine: Boolean,
    time: String,
    metaModifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val context = LocalContext.current
    val url = remember(message.text) { Regex("https?://\\S+").find(message.text)?.value ?: "" }
    Column(
        modifier = Modifier
        .widthIn(min = 220.dp, max = 300.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xFF101820))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
        .combinedClickable(
        onClick = {
        when {
        selectionMode -> onToggleSelect()
        url.isNotBlank() -> runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
        }
        },
        onLongClick = onLongPress,
        )
        .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF0B2A1B), Color(0xFF1B2228)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF34D399), modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(tr("My location"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(if (url.isNotBlank()) "Open in Maps" else "Location shared", color = Color(0xFFA8B3AD), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        MessageMeta(time, isMine, message = message, modifier = metaModifier)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ColumnScope.ImageBubbleContent(
    m: Message,
    isMine: Boolean,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var fullscreen by remember { mutableStateOf(false) }
    Box(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp)),
    ) {
        AsyncImage(
        model      = m.mediaUrl,
        contentDescription = "Image",
        contentScale       = ContentScale.Crop,
        modifier      = Modifier
        .fillMaxWidth()
        .aspectRatio(4f / 3f)
        .clip(RoundedCornerShape(12.dp))
        .combinedClickable(
        onClick     = { if (selectionMode) onToggleSelect() else fullscreen = true },
        onLongClick = onLongPress,
        ),
        )
        // Gradient + timestamp overlay
        Box(
        Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .background(
        Brush.verticalGradient(
        colorStops = arrayOf(
        0f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.52f),
        ),
        ),
        )
        .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
        MessageMeta(
        time      = messageTime(m.timestamp),
        isMine    = isMine,      // tick only on my own images
        forceLight = true,      // but keep white text on the dark overlay
        message   = m,
        modifier  = Modifier.align(Alignment.CenterEnd),
        )
        }
    }
    if (m.text.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
        m.text,
        color    = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
        fontSize = 17.sp,
        )
    }
    if (fullscreen) ImageFullscreenDialog(m.mediaUrl) { fullscreen = false }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ColumnScope.VideoBubbleContent(
    m: Message,
    isMine: Boolean,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var showPlayer by remember { mutableStateOf(false) }
    Box(
        modifier      = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.Black.copy(alpha = 0.80f))
        .combinedClickable(
        onClick     = { if (selectionMode) onToggleSelect() else showPlayer = true },
        onLongClick = onLongPress,
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Play button circle
        Box(
        Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.PlayArrow, "Play video",
        tint     = Color.White,
        modifier = Modifier.size(34.dp),
        )
        }
        // Timestamp overlay
        Box(
        Modifier
        .align(Alignment.BottomEnd)
        .padding(8.dp),
        ) {
        MessageMeta(messageTime(m.timestamp), isMine, message = m, forceLight = true)
        }
    }
    if (m.text.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(m.text, color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
    }
    if (showPlayer) VideoPlayerDialog(m.mediaUrl) { showPlayer = false }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ColumnScope.VoiceBubbleContent(
    m: Message,
    isMine: Boolean,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress  by remember { mutableFloatStateOf(0f) }
    val player    = remember { MediaPlayer() }
    var prepared  by remember { mutableStateOf(false) }

    LaunchedEffect(m.mediaUrl) {
        try {
        player.setDataSource(m.mediaUrl)
        player.prepareAsync()
        player.setOnPreparedListener { prepared = true }
        player.setOnCompletionListener { isPlaying = false; progress = 0f; player.seekTo(0) }
        } catch (_: Exception) {}
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying && prepared) {
        progress = player.currentPosition.toFloat() / player.duration.coerceAtLeast(1)
        delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val iconTint   = if (isMine) Color.White else MaterialTheme.colorScheme.primary
    val trackColor = if (isMine) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier      = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(trackColor)
        .combinedClickable(
        // Long press anywhere on the voice row opens actions; tap (while selecting)
        // toggles. The play/pause button below keeps its own tap to start playback.
        onClick     = { if (selectionMode) onToggleSelect() },
        onLongClick = onLongPress,
        )
        .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play / pause button
        Box(
        modifier      = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(iconTint.copy(alpha = 0.15f))
        .clickable {
        if (!prepared) return@clickable
        if (isPlaying) { player.pause(); isPlaying = false }
        else { player.start(); isPlaying = true }
        },
        contentAlignment = Alignment.Center,
        ) {
        if (!prepared) {
        CircularProgressIndicator(
        color       = iconTint,
        strokeWidth = 2.dp,
        modifier    = Modifier.size(18.dp),
        )
        } else {
        Icon(
        imageVector      = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = "Play",
        tint      = iconTint,
        modifier      = Modifier.size(22.dp),
        )
        }
        }
        Spacer(Modifier.width(10.dp))
        // Waveform
        VoiceWaveform(progress = progress, activeColor = iconTint)
        Spacer(Modifier.width(10.dp))
        // Duration
        Column(horizontalAlignment = Alignment.End) {
        Text(
        text       = formatDuration(
        if (isPlaying && progress > 0f) (progress * m.durationMs).toLong() else m.durationMs,
        ),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color      = if (isMine) Color.White.copy(0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MessageMeta(messageTime(m.timestamp), isMine, message = m)
        }
    }
}

// SHARED COMPOSABLES

// Replaces the old ModalBottomSheet; shows a proper hero layout with zoom support.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullProfileScreen(
    title      : String,
    subtitle   : String,
    photoUrl   : String,
    kind       : String,
    linkId     : String = "",
    description: String,
    memberCount: Int,
    verificationLevel: Int = 0,
    verified: Boolean = false,
    sharedMessages: List<Message> = emptyList(),
    accent     : Color,
    showCallActions       : Boolean = true,
    communityJoined       : Boolean = false,
    canManageCommunity    : Boolean = false,
    onUpdateCommunity     : (String, String, String) -> Unit = { _, _, _ -> },
    onUploadCommunityPhoto: (android.net.Uri, (String?) -> Unit) -> Unit = { _, _ -> },
    onJoinCommunity       : () -> Unit = {},
    onLeaveCommunity      : () -> Unit = {},
    onDeleteCommunity     : () -> Unit = {},
    onDirectMessage       : () -> Unit = {},
    onMute      : () -> Unit = {},
    onSearch      : () -> Unit = {},
    onBlock      : () -> Unit = {},
    onReport      : () -> Unit = {},
    onClearChat      : () -> Unit = {},
    onCall      : () -> Unit = {},
    onVideoCall      : () -> Unit = {},
    onOpenUrl      : (String) -> Unit = {},
    onDismiss  : () -> Unit,
) {
    var showPhotoZoom by remember { mutableStateOf(false) }
    var showEdit      by remember { mutableStateOf(false) }
    var showDelete    by remember { mutableStateOf(false) }
    var showForward   by remember { mutableStateOf(false) }
    var sharedCategory by remember { mutableStateOf<SharedContentCategory?>(null) }
    var previewImageUrl by remember { mutableStateOf("") }
    var subscribed    by remember(kind, linkId, communityJoined) { mutableStateOf(communityJoined) }
    val ctx      = LocalContext.current
    val sharedSummary = remember(sharedMessages) { SharedContentSummary.from(sharedMessages) }

    // Resolve the contact's @username so "Share contact" can share me.adischat.com/<username>.
    val profileRepo = remember { ChatRepository() }
    var peerUsername by remember { mutableStateOf("") }
    LaunchedEffect(linkId, title, kind) {
        if (kind != "channel" && kind != "group" && linkId.isNotBlank()) {
        peerUsername = runCatching {
        val results = profileRepo.searchUsers(title.trim())
        (results.firstOrNull { it.uid == linkId } ?: results.firstOrNull())?.username ?: ""
        }.getOrDefault("")
        }
    }
    fun shareProfileLink() {
        val link = when (kind) {
        "channel" -> AdisLinks.channel(linkId)
        "group"   -> AdisLinks.group(linkId)
        else      -> if (peerUsername.isNotBlank()) AdisLinks.user(peerUsername) else AdisLinks.user(linkId)
        }
        runCatching {
        ctx.startActivity(
        android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "Connect with $title on AdisChat: $link")
        },
        "Share",
        ),
        )
        }
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows  = false,
        ),
    ) {
        AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 8 },
        ) {
        Box(
        Modifier
        .fillMaxSize()
        .background(ProfileBg),
        ) {
        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 34.dp),
        ) {
        item {
        ProfileHeroHeader(
        title = title,
        subtitle = subtitle,
        photoUrl = photoUrl,
        kind = kind,
        memberCount = memberCount,
        verificationLevel = verificationLevel,
        verified = verified,
        onPhotoClick = { if (photoUrl.isNotBlank()) showPhotoZoom = true },
        )
        }

        item {
        Column(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        if (description.isNotBlank()) {
        Text(
        text = description,
        color = ProfileTextMuted,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
        .padding(16.dp),
        )
        }

        if (kind == "channel") {
        ProfilePrimaryAction(
        icon = if (subscribed) Icons.Default.Check else Icons.Default.PersonAdd,
        label = if (subscribed) "Unfollow" else "Follow",
        modifier = Modifier.fillMaxWidth(),
        onClick = {
        if (subscribed) {
        onLeaveCommunity()
        subscribed = false
        } else {
        onJoinCommunity()
        subscribed = true
        }
        },
        )
        } else if (kind == "group") {
        ProfilePrimaryAction(
        icon = if (subscribed) Icons.Default.ExitToApp else Icons.Default.GroupAdd,
        label = if (subscribed) "Leave group" else "Join group",
        modifier = Modifier.fillMaxWidth(),
        onClick = {
        if (subscribed) {
        onLeaveCommunity()
        subscribed = false
        } else {
        onJoinCommunity()
        subscribed = true
        }
        },
        )
        }

        ProfileSectionTitle("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileQuickAction(Icons.Default.NotificationsOff, "Mute", ProfilePurple, Modifier.weight(1f), onMute)
        ProfileQuickAction(Icons.Default.Search, "Search", Color(0xFF5CC8FF), Modifier.weight(1f), onSearch)
        ProfileQuickAction(Icons.Default.Share, "Share", Color(0xFF2EE59D), Modifier.weight(1f)) { showForward = true }
        }

        ProfileSectionCard("Shared content") {
        ProfileInfoRow(Icons.Default.PhotoLibrary, "Media", sharedSummary.media.size.toString(), ProfilePurple) {
        sharedCategory = SharedContentCategory.Media
        }
        ProfileInfoRow(Icons.Default.InsertDriveFile, "Files", sharedSummary.files.size.toString(), Color(0xFF5CC8FF)) {
        sharedCategory = SharedContentCategory.Files
        }
        ProfileInfoRow(Icons.Default.Link, "Links", sharedSummary.links.size.toString(), Color(0xFF2EE59D)) {
        sharedCategory = SharedContentCategory.Links
        }
        ProfileInfoRow(Icons.Default.GraphicEq, "Voice messages", sharedSummary.voices.size.toString(), Color(0xFFFFB020), showDivider = false) {
        sharedCategory = SharedContentCategory.Voice
        }
        }

        ProfileSectionCard("Chat controls") {
        ProfileInfoRow(Icons.Default.Lock, "Lock chat with PIN/Biometric", "Available", Color(0xFF2EE59D), showDivider = false)
        }

        ProfileSectionCard(if (kind == "channel" || kind == "group") "Community" else "Contact") {
        ProfileInfoRow(Icons.Default.ContactPage, if (kind == "channel" || kind == "group") "Share invite" else "Share contact", "Share", ProfilePurple, showDivider = false) {
        shareProfileLink()
        }
        }

        if (canManageCommunity && (kind == "channel" || kind == "group")) {
        ProfileSectionCard("Management") {
        ProfileInfoRow(Icons.Default.Edit, "Edit name, info and photo", "Edit", ProfilePurple, onClick = { showEdit = true })
        ProfileInfoRow(Icons.Default.Delete, "Delete ${if (kind == "channel") "channel" else "group"}", "Delete", DeleteRed, showDivider = false, onClick = { showDelete = true })
        }
        }

        ProfileSectionCard("Safety") {
        ProfileDangerRow(Icons.Default.Block, "Block user", onBlock)
        ProfileDangerRow(Icons.Default.Report, "Report user", onReport)
        ProfileDangerRow(Icons.Default.DeleteOutline, "Delete conversation", onClearChat, showDivider = false)
        }
        }
        }
        }

        Surface(
        modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(start = 12.dp, top = 10.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
        IconButton(onClick = onDismiss) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        }
        }
        }
    }

    // In-app forward (share / invite the channel)
    if (showForward) {
        // Direct contact - - - me.adischat.com/<username>; community - - - channel/group invite.
        val inviteLink = when (kind) {
        "group"   -> AdisLinks.group(linkId)
        "channel" -> AdisLinks.channel(linkId)
        else      -> if (peerUsername.isNotBlank()) AdisLinks.user(peerUsername) else AdisLinks.user(linkId)
        }
        ForwardScreen(
        messageText = "Check out $title on AdisChat: $inviteLink",
        onDismiss   = { showForward = false },
        )
    }

    // Fullscreen zoomable photo viewer
    if (showPhotoZoom && photoUrl.isNotBlank()) {
        ZoomableImageDialog(url = photoUrl, onDismiss = { showPhotoZoom = false })
    }

    if (previewImageUrl.isNotBlank()) {
        ImageFullscreenDialog(url = previewImageUrl) { previewImageUrl = "" }
    }

    sharedCategory?.let { category ->
        val initialTab = when (category) {
        SharedContentCategory.Media -> SharedTab.Media
        SharedContentCategory.Files -> SharedTab.Files
        SharedContentCategory.Links -> SharedTab.Links
        SharedContentCategory.Voice -> SharedTab.Voice
        }
        SharedContentGallery(
        summary    = sharedSummary,
        initialTab = initialTab,
        onDismiss  = { sharedCategory = null },
        onOpenUrl  = { url -> sharedCategory = null; onOpenUrl(url) },
        onPreviewImage = { url -> previewImageUrl = url },
        )
    }

    if (showEdit) {
        EditCommunityDialog(
        kind = kind,
        initialName = title,
        initialDescription = description,
        initialPhotoUrl = photoUrl,
        accent = accent,
        onUploadPhoto = onUploadCommunityPhoto,
        onDismiss = { showEdit = false },
        onSave = { name, info, newPhotoUrl ->
        onUpdateCommunity(name, info, newPhotoUrl)
        showEdit = false
        },
        )
    }

    if (showDelete) {
        AlertDialog(
        onDismissRequest = { showDelete = false },
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DeleteRed) },
        title = { Text("Delete ${if (kind == "channel") "channel" else "group"}?") },
        text = { Text("This removes the ${if (kind == "channel") "channel" else "group"} and its chat history for everyone.") },
        confirmButton = {
        Button(
        onClick = {
        showDelete = false
        onDeleteCommunity()
        },
        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
        ) { Text(tr("Delete")) }
        },
        dismissButton = {
        TextButton(onClick = { showDelete = false }) { Text(tr("Cancel")) }
        },
        )
    }
}

private enum class SharedContentCategory(val title: String) {
    Media("Media"),
    Files("Files"),
    Links("Links"),
    Voice("Voice messages"),
}

private data class SharedLinkItem(
    val url: String,
    val senderName: String,
    val timestamp: Long,
)

private data class SharedContentSummary(
    val media: List<Message>,
    val images: List<Message>,
    val videos: List<Message>,
    val files: List<Message>,
    val links: List<SharedLinkItem>,
    val voices: List<Message>,
) {
    companion object {
        fun from(messages: List<Message>): SharedContentSummary {
        val visible = messages.filterNot { it.deleted }
        val media = visible.filter { it.mediaUrl.isNotBlank() && it.type in setOf("image", "video") }
        .sortedByDescending { it.timestamp }
        return SharedContentSummary(
        media = media,
        images = media.filter { it.type == "image" },
        videos = media.filter { it.type == "video" },
        files = visible.filter {
        it.mediaUrl.isNotBlank() &&
        (it.type in setOf("document", "file") || it.mediaMimeType.startsWith("application/"))
        }.sortedByDescending { it.timestamp },
        links = visible.flatMap { message ->
        extractUrls(message.text).map { url ->
        SharedLinkItem(url = url, senderName = message.senderName, timestamp = message.timestamp)
        }
        }.sortedByDescending { it.timestamp },
        voices = visible.filter { it.mediaUrl.isNotBlank() && it.type == "voice" }
        .sortedByDescending { it.timestamp },
        )
        }
    }
}

// - - - Premium shared-content gallery (Telegram-style swipeable tabs) - - - - - - - - - - - - - - -
private enum class SharedTab(val title: String) {
    Media("Media"), Files("Files"), Links("Links"), Voice("Voice")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedContentGallery(
    summary: SharedContentSummary,
    initialTab: SharedTab,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    val tabs = SharedTab.values().toList()
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(initialTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    fun countOf(t: SharedTab) = when (t) {
        SharedTab.Media -> summary.media.size
        SharedTab.Files -> summary.files.size
        SharedTab.Voice -> summary.voices.size
        SharedTab.Links -> summary.links.size
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(ProfileBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ProfileText) }
        Text("Shared Content", color = ProfileText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", tint = ProfileText)
        }
        }
        if (searchOpen) {
        Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(14.dp)).background(ProfileCardBg)
        .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
        ) {
        if (query.isBlank()) Text("Search by sender, name or link...", color = ProfileTextMuted, fontSize = 14.sp)
        BasicTextField(
        value = query, onValueChange = { query = it }, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = ProfileText, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(ProfilePurple),
        modifier = Modifier.fillMaxWidth(),
        )
        }
        }
        // Sticky tab bar
        Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        tabs.forEachIndexed { i, t ->
        val active = pagerState.currentPage == i
        val n = countOf(t)
        Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) ProfilePurple else ProfileCardBg)
        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
        .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
        Text(if (n > 0) "${t.title} - $n" else t.title,
        color = if (active) Color.White else ProfileTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
        val q = query.trim()
        fun List<Message>.f() = if (q.isBlank()) this
        else filter { it.senderName.contains(q, true) || sharedFileTitle(it).contains(q, true) }
        when (tabs[page]) {
        SharedTab.Media  -> SharedMediaGrid(summary.media.f(), onPreviewImage, onOpenUrl)
        SharedTab.Files  -> SharedColumnPage(summary.files.f().isNotEmpty()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(summary.files.f(), key = { it.id }) { SharedFileRow(it, onOpenUrl) }
        }
        }
        SharedTab.Voice  -> SharedColumnPage(summary.voices.f().isNotEmpty()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(summary.voices.f(), key = { it.id }) { SharedVoiceCard(it, onOpenUrl) }
        }
        }
        SharedTab.Links  -> {
        val links = if (q.isBlank()) summary.links else summary.links.filter { it.url.contains(q, true) || it.senderName.contains(q, true) }
        SharedColumnPage(links.isNotEmpty()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(links, key = { "${it.url}-${it.timestamp}" }) { SharedLinkRow(it, onOpenUrl) }
        }
        }
        }
        }
        }
        }
        }
    }
}

@Composable
private fun SharedColumnPage(hasItems: Boolean, content: @Composable () -> Unit) {
    if (hasItems) content() else EmptyShared("Nothing shared yet")
}

@Composable
private fun EmptyShared(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint = ProfileTextMuted, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = ProfileTextMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SharedMediaGrid(items: List<Message>, onPreviewImage: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    if (items.isEmpty()) { EmptyShared("Nothing shared yet"); return }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gridItems(items, key = { it.id }) { m ->
        val isVideo = m.type == "video"
        Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(ProfileCardBg)
        .clickable { if (isVideo) onOpenUrl(m.mediaUrl) else onPreviewImage(m.mediaUrl) },
        ) {
        AsyncImage(model = m.mediaUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (isVideo) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        if (m.durationMs > 0) {
        Box(
        Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp),
        ) { Text(formatDuration(m.durationMs), color = Color.White, fontSize = 10.sp) }
        }
        }
        }
        }
    }
}

@Composable
private fun SharedVoiceCard(m: Message, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
        .clickable { onOpenUrl(m.mediaUrl) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFFB020).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFFB020), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        MiniWaveform()
        Spacer(Modifier.height(6.dp))
        Text(
        "${formatDuration(m.durationMs)} - ${m.senderName.ifBlank { "AdisChat" }} - ${chatDate(m.timestamp)}",
        color = ProfileTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        }
    }
}

@Composable
private fun MiniWaveform() {
    val heights = listOf(8, 16, 11, 22, 14, 26, 12, 19, 9, 24, 13, 20, 10, 17, 7)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        heights.forEach { h ->
        Box(Modifier.size(width = 2.5.dp, height = h.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFB020).copy(alpha = 0.6f)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedContentSheet(
    category: SharedContentCategory,
    summary: SharedContentSummary,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    val itemsCount = when (category) {
        SharedContentCategory.Media -> summary.media.size
        SharedContentCategory.Files -> summary.files.size
        SharedContentCategory.Links -> summary.links.size
        SharedContentCategory.Voice -> summary.voices.size
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProfileBg,
        contentColor = ProfileText,
        dragHandle = {
        Box(
        Modifier
        .padding(top = 10.dp)
        .size(width = 42.dp, height = 4.dp)
        .clip(RoundedCornerShape(100.dp))
        .background(Color.White.copy(alpha = 0.22f)),
        )
        },
    ) {
        Column(
        modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text(category.title, color = ProfileText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text("$itemsCount items", color = ProfileTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onDismiss) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = ProfileText)
        }
        }
        Spacer(Modifier.height(12.dp))
        if (itemsCount == 0) {
        Box(
        modifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(ProfileCardBg),
        contentAlignment = Alignment.Center,
        ) {
        Text("Nothing shared yet", color = ProfileTextMuted, fontWeight = FontWeight.SemiBold)
        }
        } else {
        LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
        ) {
        when (category) {
        SharedContentCategory.Media -> items(summary.media, key = { it.id }) { message ->
        SharedMediaRow(message, onPreviewImage, onOpenUrl)
        }
        SharedContentCategory.Files -> items(summary.files, key = { it.id }) { message ->
        SharedFileRow(message, onOpenUrl)
        }
        SharedContentCategory.Links -> items(summary.links, key = { "${it.url}-${it.timestamp}" }) { link ->
        SharedLinkRow(link, onOpenUrl)
        }
        SharedContentCategory.Voice -> items(summary.voices, key = { it.id }) { message ->
        SharedVoiceRow(message, onOpenUrl)
        }
        }
        }
        }
        }
    }
}

@Composable
private fun SharedMediaRow(message: Message, onPreviewImage: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    SharedContentRow(
        icon = if (message.type == "video") Icons.Default.PlayArrow else Icons.Default.Image,
        iconTint = if (message.type == "video") Color(0xFFFFB020) else ProfilePurple,
        title = if (message.type == "video") "Video" else "Photo",
        subtitle = "${message.senderName.ifBlank { "AdisChat" }} - ${chatDate(message.timestamp)}",
        imageUrl = if (message.type == "image") message.mediaUrl else "",
        onClick = {
        if (message.type == "image") onPreviewImage(message.mediaUrl) else onOpenUrl(message.mediaUrl)
        },
    )
}

@Composable
private fun SharedFileRow(message: Message, onOpenUrl: (String) -> Unit) {
    SharedContentRow(
        icon = Icons.Default.InsertDriveFile,
        iconTint = Color(0xFF5CC8FF),
        title = sharedFileTitle(message),
        subtitle = "${message.mediaMimeType.ifBlank { "File" }} - ${chatDate(message.timestamp)}",
        onClick = { onOpenUrl(message.mediaUrl) },
    )
}

@Composable
private fun SharedLinkRow(link: SharedLinkItem, onOpenUrl: (String) -> Unit) {
    SharedContentRow(
        icon = Icons.Default.Link,
        iconTint = Color(0xFF2EE59D),
        title = link.url.removePrefix("https://").removePrefix("http://"),
        subtitle = "${link.senderName.ifBlank { "AdisChat" }} - ${chatDate(link.timestamp)}",
        onClick = { onOpenUrl(link.url) },
    )
}

@Composable
private fun SharedVoiceRow(message: Message, onOpenUrl: (String) -> Unit) {
    SharedContentRow(
        icon = Icons.Default.GraphicEq,
        iconTint = Color(0xFFFFB020),
        title = "Voice message",
        subtitle = "${formatDuration(message.durationMs)} - ${chatDate(message.timestamp)}",
        onClick = { onOpenUrl(message.mediaUrl) },
    )
}

@Composable
private fun SharedContentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    imageUrl: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
        modifier = Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(iconTint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        ) {
        if (imageUrl.isNotBlank()) {
        AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        )
        } else {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(25.dp))
        }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
        Text(title, color = ProfileText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = ProfileTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = ProfileTextMuted.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
    }
}

private fun extractUrls(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val regex = Regex("""https?://[^\s<>()]+|(?:me\.adischat\.com|adis\.me)/[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]+""")
    return regex.findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']') }
        .map { if (it.startsWith("http")) it else "https://$it" }
        .distinct()
        .toList()
}

private fun sharedFileTitle(message: Message): String {
    val fromUrl = message.mediaUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
    return fromUrl ?: when {
        message.mediaMimeType.contains("pdf", ignoreCase = true) -> "PDF document"
        message.mediaMimeType.contains("image", ignoreCase = true) -> "Image file"
        else -> "Shared file"
    }
}

@Composable
private fun ProfileHeroHeader(
    title: String,
    subtitle: String,
    photoUrl: String,
    kind: String,
    memberCount: Int,
    verificationLevel: Int,
    verified: Boolean,
    onPhotoClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(318.dp)) {
        if (photoUrl.isNotBlank()) {
        AsyncImage(
        model = photoUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clickable(onClick = onPhotoClick),
        )
        } else {
        Box(
        Modifier
        .fillMaxSize()
        .background(Brush.linearGradient(listOf(ProfilePurple.copy(alpha = 0.45f), Color(0xFF111827)))),
        contentAlignment = Alignment.Center,
        ) {
        Text(initialsOf(title), color = Color.White.copy(alpha = 0.22f), fontSize = 92.sp, fontWeight = FontWeight.ExtraBold)
        }
        }
        Box(
        Modifier
        .fillMaxSize()
        .background(
        Brush.verticalGradient(
        listOf(
        Color.Black.copy(alpha = 0.24f),
        Color.Black.copy(alpha = 0.34f),
        ProfileBg,
        ),
        ),
        ),
        )
        Column(
        modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(
        modifier = Modifier
        .size(104.dp)
        .clip(CircleShape)
        .background(ProfileCardBg)
        .border(4.dp, ProfileBg, CircleShape)
        .clickable(onClick = onPhotoClick),
        contentAlignment = Alignment.Center,
        ) {
        if (photoUrl.isNotBlank()) {
        AsyncImage(
        model = photoUrl,
        contentDescription = "Profile photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
        } else {
        Text(initialsOf(title), color = ProfilePurple, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
        title,
        color = ProfileText,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
        )
        if ((verificationLevel >= 3 || verified) && kind !in listOf("channel", "group")) {
        Spacer(Modifier.width(7.dp))
        UserVerifiedBadge(Modifier.size(22.dp))
        }
        }
        Spacer(Modifier.height(6.dp))
        if (kind == "channel" || kind == "group") {
        Text(
        text = if (kind == "channel") "Channel - $memberCount members" else "Group - $memberCount members",
        color = ProfileTextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        } else {
        Text(
        text = "@${title.cleanHandle()}",
        color = ProfileTextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        Text(
        text = subtitle.ifBlank { "last seen recently" },
        color = ProfileTextMuted,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        }
        Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = ProfileTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 2.dp, start = 2.dp),
    )
}

@Composable
private fun ProfilePrimaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "profilePrimaryPress")
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp).graphicsLayer(scaleX = scale, scaleY = scale),
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfilePurple, contentColor = Color.White),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ProfileIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "profileIconPress")
    Box(
        modifier = Modifier
        .size(54.dp)
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clip(RoundedCornerShape(20.dp))
        .background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = ProfilePurple, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ProfileQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "profileQuickPress")
    Column(
        modifier = modifier
        .height(92.dp)
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clip(RoundedCornerShape(22.dp))
        .background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = ProfileText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProfileSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileSectionTitle(title)
        Column(
        modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(ProfileCardBg)
        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
        .padding(vertical = 6.dp),
        ) {
        content()
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    tint: Color,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
        modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(15.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(title, color = ProfileText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = ProfileTextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, null, tint = ProfileTextMuted.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        }
        if (showDivider) HorizontalDivider(color = Color.White.copy(alpha = 0.055f), modifier = Modifier.padding(start = 68.dp, end = 14.dp))
    }
}

@Composable
private fun ProfileDangerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    ProfileInfoRow(icon = icon, title = title, value = "", tint = DeleteRed, showDivider = showDivider, onClick = onClick)
}

private fun String.cleanHandle(): String =
    trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "adischat" }


@Composable
private fun EditCommunityDialog(
    kind: String,
    initialName: String,
    initialDescription: String,
    initialPhotoUrl: String,
    accent: Color,
    onUploadPhoto: (android.net.Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name      by remember(initialName)      { mutableStateOf(initialName) }
    var info      by remember(initialDescription) { mutableStateOf(initialDescription) }
    var photo      by remember(initialPhotoUrl)    { mutableStateOf(initialPhotoUrl) }
    var isUploading  by remember { mutableStateOf(false) }
    var uploadError  by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploading = true
        uploadError = null
        onUploadPhoto(uri) { newUrl ->
        isUploading = false
        if (newUrl != null) {
        photo = newUrl
        } else {
        uploadError = "Upload failed. Try again."
        }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Edit ${if (kind == "channel") "Channel" else "Group"}") },
        text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
        ) {
        Box(
        modifier = Modifier
        .size(88.dp)
        .clip(CircleShape)
        .background(accent.copy(alpha = 0.12f))
        .border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
        .clickable(enabled = !isUploading) { photoPicker.launch("image/*") },
        contentAlignment = Alignment.Center,
        ) {
        if (photo.isNotBlank()) {
        AsyncImage(
        model      = photo,
        contentDescription = "Photo",
        contentScale       = ContentScale.Crop,
        modifier      = Modifier.fillMaxSize().clip(CircleShape),
        )
        } else {
        Icon(
        imageVector      = Icons.Default.CameraAlt,
        contentDescription = "Pick photo",
        tint      = accent,
        modifier      = Modifier.size(32.dp),
        )
        }

        // Upload spinner overlay
        if (isUploading) {
        Box(
        modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        CircularProgressIndicator(
        modifier    = Modifier.size(28.dp),
        color       = Color.White,
        strokeWidth = 2.5.dp,
        )
        }
        }

        // Camera badge (bottom-right)
        if (!isUploading) {
        Box(
        modifier = Modifier
        .align(Alignment.BottomEnd)
        .size(26.dp)
        .background(accent, CircleShape)
        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        imageVector      = Icons.Default.CameraAlt,
        contentDescription = null,
        tint      = Color.White,
        modifier      = Modifier.size(14.dp),
        )
        }
        }
        }
        }

        // Upload error
        uploadError?.let { err ->
        Text(
        err,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
        )
        }

        OutlinedTextField(
        value      = name,
        onValueChange = { name = it },
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Name")) },
        singleLine    = true,
        shape      = RoundedCornerShape(14.dp),
        colors      = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent),
        )

        OutlinedTextField(
        value      = info,
        onValueChange = { info = it },
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Info")) },
        minLines      = 3,
        shape      = RoundedCornerShape(14.dp),
        colors      = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent),
        )
        }
        },
        confirmButton = {
        Button(
        enabled = name.trim().length >= 2 && !isUploading,
        onClick = { onSave(name.trim(), info.trim(), photo) },
        colors  = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) { Text(tr("Save")) }
        },
        dismissButton = {
        TextButton(onClick = onDismiss, enabled = !isUploading) { Text(tr("Cancel")) }
        },
    )
}

@Composable
private fun ZoomableImageDialog(url: String, onDismiss: () -> Unit) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + offsetChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows  = false,
        ),
    ) {
        Box(
        Modifier
        .fillMaxSize()
        .background(Color.Black),
        contentAlignment = Alignment.Center,
        ) {
        AsyncImage(
        model      = url,
        contentDescription = "Profile photo",
        contentScale       = ContentScale.Fit,
        modifier      = Modifier
        .fillMaxWidth()
        .graphicsLayer {
        scaleX       = scale
        scaleY       = scale
        translationX = offset.x
        translationY = offset.y
        }
        .transformable(state = transformState),
        )

        // Close button
        Surface(
        modifier      = Modifier
        .align(Alignment.TopEnd)
        .statusBarsPadding()
        .padding(10.dp),
        shape      = CircleShape,
        color      = Color.Black.copy(alpha = 0.55f),
        ) {
        IconButton(onClick = onDismiss) {
        Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
        }

        // Zoom level indicator
        if (scale > 1.05f) {
        Surface(
        modifier      = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 24.dp),
        shape      = RoundedCornerShape(999.dp),
        color      = Color.Black.copy(alpha = 0.50f),
        ) {
        Text(
        text     = "Pinch to zoom - Double-tap to reset",
        fontSize = 15.sp,
        color    = Color.White.copy(alpha = 0.80f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        }
        }
        }
    }
}

@Composable
private fun ProfileActionBtn(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    modifier: Modifier,
    accent  : Color,
    onClick : (() -> Unit)? = null,
) {
    Column(
        modifier      = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF101820))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
        text       = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChatThreadBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    val base = if (isDark) Color(0xFF04150F) else Color(0xFFEAF7EF)
    androidx.compose.foundation.Canvas(modifier = modifier.background(base)) {
        val w = size.width
        val h = size.height
        drawRect(
        brush = Brush.verticalGradient(
        listOf(
        Color(0xFF061A12),
        Color(0xFF04150F),
        Color(0xFF020806),
        ),
        ),
        )
        drawCircle(
        brush = Brush.radialGradient(
        listOf(BrandGreen.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(w * 0.85f, h * 0.10f),
        radius = w * 0.72f,
        ),
        radius = w * 0.72f,
        center = Offset(w * 0.85f, h * 0.10f),
        )
    }
}

@Composable
private fun EncryptionBadge() {
    val isDark = isInDarkTheme
    val tint   = if (isDark) Color(0xFF606068) else Color(0xFF9CA3AF)
    Row(
        modifier      = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Lock, null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(
        text     = "End-to-end encrypted",
        fontSize = 15.sp,
        color    = tint,
        )
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFB8C1BC),
        modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xAA101820))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun UnreadMessagesDivider() {
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(
        modifier = Modifier.weight(1f),
        thickness = 0.7.dp,
        color = BrandGreen.copy(alpha = 0.38f),
        )
        Text(
        text = "Unread messages",
        color = BrandGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xAA101820))
        .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        HorizontalDivider(
        modifier = Modifier.weight(1f),
        thickness = 0.7.dp,
        color = BrandGreen.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun TypingIndicatorRow(accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        val isDark = isInDarkTheme
        Box(
        modifier = Modifier
        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
        .background(if (isDark) Color(0xFF1A1A1A) else Color.White)
        .border(1.dp, if (isDark) Color(0x0FFFFFFF) else Color(0x1A000000),
        RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
        .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
        TypingDots(dotColor = Color(0xFF606068), size = 7.dp)
        }
    }
}

@Composable
private fun TypingDots(dotColor: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "typingDots")
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 6.28f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Restart),
        label      = "typingPhase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(size)) {
        repeat(3) { i ->
        val alpha = 0.30f + 0.70f * ((sin(phase + i) + 1f) / 2f)
        Box(
        Modifier
        .size(size)
        .clip(CircleShape)
        .background(dotColor.copy(alpha = alpha)),
        )
        }
    }
}

@Composable
private fun ReactionActionRail(
    isMine     : Boolean,
    isCommunity: Boolean,
    canModerate: Boolean,
    onReact    : (String) -> Unit,
    onDelete   : () -> Unit,
    onForward  : () -> Unit,
    onSuspend  : () -> Unit,
    onBlock    : () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Emoji row
        Surface(
        shape      = RoundedCornerShape(999.dp),
        color      = MaterialTheme.colorScheme.surface,
        tonalElevation  = 12.dp,
        shadowElevation = 8.dp,
        modifier      = Modifier.border(
        0.5.dp,
        MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
        RoundedCornerShape(999.dp),
        ),
        ) {
        Row(
        modifier      = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        ) {
        listOf("\uD83D\uDE0A", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83C\uDF89", "\uD83D\uDE80", "\uD83D\uDCA1", "\u2728", "\uD83D\uDCAA", "\uD83D\uDE4F", "\uD83D\uDE2E").forEach { emoji ->
        Box(
        modifier      = Modifier
        .clip(CircleShape)
        .clickable { onReact(emoji) }
        .padding(horizontal = 7.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
        ) {
        Text(
        text     = emoji,
        fontSize = 29.sp,
        color    = Color.Unspecified,
        )
        }
        }
        Spacer(Modifier.width(4.dp))
        Box(
        modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(BrandGreen.copy(alpha = 0.12f))
        .clickable(onClick = onForward),
        contentAlignment = Alignment.Center,
        ) {
        CurvedForwardIcon(tint = BrandGreen, modifier = Modifier.size(18.dp))
        }
        if (isMine) {
        Spacer(Modifier.width(4.dp))
        Box(
        modifier      = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(DeleteRed.copy(alpha = 0.10f))
        .clickable(onClick = onDelete),
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        Icons.Default.Delete, "Delete",
        tint     = DeleteRed,
        modifier = Modifier.size(17.dp),
        )
        }
        }
        if (canModerate) {
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onSuspend) {
        Text(tr("Suspend"), color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBlock) {
        Text(tr("Block"), color = DeleteRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        }
        }
        }
    }
    Spacer(Modifier.height(6.dp))
}

// Delivery/read ticks are a 1:1 concept only. The group message call site sets
// this to false so NO message type (text/media/voice/call) shows ticks in groups.
private val LocalShowDeliveryStatus = androidx.compose.runtime.compositionLocalOf { true }

/** Lightweight sender card shown when a group member's avatar/name is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupMemberSheet(name: String, photo: String, uid: String, verified: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isInDarkTheme) Color(0xFF0E1512) else Color(0xFFFBFBFD),
        contentColor = if (isInDarkTheme) Color.White else Color(0xFF111827),
    ) {
        Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        ChatAvatar(photo, initialsOf(name), 88.dp, accentFor(uid))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (verified) { Spacer(Modifier.width(6.dp)); UserVerifiedBadge(Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
        tr("Group member"),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(18.dp))
        Text(
        tr("Full member profile and direct message are coming soon."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MessageMeta(
    time: String,
    isMine: Boolean,
    message: Message? = null,
    modifier: Modifier = Modifier,
    // For meta drawn over a dark media overlay we want light text even on
    // RECEIVED media - - but delivery ticks must STILL only appear on my own
    // messages, so the tick stays gated on the real `isMine`.
    forceLight: Boolean = false,
) {
    val lightText = isMine || forceLight
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (message?.edited == true) {
        Text(
        text = "edited",
        fontSize = 11.sp,
        fontStyle = FontStyle.Italic,
        color = if (lightText) Color.White.copy(0.62f) else Color(0xFF9AA5A0),
        )
        }
        Text(
        text = time,
        fontSize = 11.sp,
        color = if (lightText) Color.White.copy(0.72f) else Color(0xFFA8B3AD),
        )
        // Ticks only on my own 1:1 messages - - never in groups.
        if (isMine && LocalShowDeliveryStatus.current) {
        val status = message?.realDeliveryStatus().orEmpty()
        when (status) {
        "seen" -> Icon(Icons.Default.DoneAll, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
        "delivered", "sent" -> Icon(Icons.Default.Done, null, tint = Color(0xFF9EF2BC), modifier = Modifier.size(13.dp))
        }
        }
    }
}

private fun Message.realDeliveryStatus(): String {
    val explicit = deliveryStatus.trim().lowercase()
    return when {
        localStatus in setOf("sending", "uploading", "failed") -> ""
        localStatus in setOf("sent", "delivered", "seen") -> localStatus
        readAt > 0L || explicit in setOf("seen", "read") -> "seen"
        deliveredAt > 0L || explicit == "delivered" -> "delivered"
        id.isNotBlank() && !id.startsWith("local-") -> "sent"
        else -> ""
    }
}

@Composable
private fun VoiceWaveform(progress: Float, activeColor: Color) {
    Row(
        modifier      = Modifier.width(96.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(22) { i ->
        val h      = (7 + ((i * 5) % 14)).dp
        val active = i / 22f <= progress.coerceIn(0f, 1f)
        Box(
        Modifier
        .width(3.dp)
        .height(h)
        .clip(RoundedCornerShape(99.dp))
        .background(activeColor.copy(alpha = if (active) 0.90f else 0.22f)),
        )
        }
    }
}

@Composable
private fun ChatAvatar(photoUrl: String, initials: String, size: Dp, accent: Color) {
    Box(
        modifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f))))
        .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNotBlank()) {
        AsyncImage(
        model = photoUrl,
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
        )
        } else {
        Text(
        text = initials,
        color = Color.White,
        fontWeight = FontWeight.ExtraBold,
        fontSize = (size.value / 2.8f).sp,
        )
        }
    }
}
@Composable
private fun UnreadBadge(count: Int, accent: Color) {
    Box(
        modifier      = Modifier
        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(NotifRed)
        .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
        text       = if (count > 99) "99+" else count.toString(),
        color      = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        )
    }
}

/** Unified modern red notification pill used on section tabs (and reusable elsewhere). */
private val NotifRed = Color(0xFFFF3B30)

@Composable
private fun NotificationPill(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(NotifRed)
        .border(1.5.dp, AdisSurface, RoundedCornerShape(999.dp))
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

@Composable
private fun ImageFullscreenDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Pinch-zoom (1x- -), double-tap zoom, drag-to-pan when zoomed, tap or
        // swipe-down to close. Works for sent and received images; full quality.
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var dragDown by remember { mutableStateOf(0f) }

        Box(
        modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = (0.96f - (dragDown / 1200f)).coerceIn(0.4f, 0.96f)))
        .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        scale = newScale
        if (newScale > 1f) {
        offset += pan
        dragDown = 0f
        } else {
        offset = Offset.Zero
        // Not zoomed - - - a downward drag dismisses the viewer.
        dragDown = (dragDown + pan.y).coerceAtLeast(0f)
        if (dragDown > 240f) onDismiss()
        }
        }
        }
        .pointerInput(Unit) {
        detectTapGestures(
        onTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else onDismiss() },
        onDoubleTap = {
        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
        },
        )
        },
        contentAlignment = Alignment.Center,
        ) {
        AsyncImage(
        model      = url,
        contentDescription = "Full image",
        contentScale       = ContentScale.Fit,
        modifier      = Modifier
        .fillMaxSize()
        .graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = offset.x
        translationY = offset.y + (if (scale <= 1f) dragDown else 0f)
        },
        )
        IconButton(onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
        Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
        }
    }
}

@Composable
private fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    val ctx    = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().also {
        it.setMediaItem(MediaItem.fromUri(url))
        it.prepare()
        it.playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
        Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.Black),
        ) {
        AndroidView(
        factory  = { c ->
        PlayerView(c).apply {
        this.player = player
        layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        )
        }
        },
        modifier = Modifier.fillMaxSize(),
        )
        IconButton(onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
        Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
        }
    }
}


/** Stable accent color derived from a conversation ID. */
private fun accentFor(id: String): Color {
    val palette = listOf(
        Color(0xFF7C4DFF), Color(0xFF00BCD4), Color(0xFFFF5722),
        Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63),
        Color(0xFF2196F3), Color(0xFF009688),
    )
    return palette[(id.hashCode() and 0x7FFFFFFF) % palette.size]
}

/** First letter(s) of each word, up to 2 characters. */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

/** Clock time shown under a single message bubble - - ALWAYS the time of day (e.g.
 *  "14:32"), never a date. The day a message was sent is already shown by the date
 *  separators between message groups, so a message from yesterday must still show its
 *  send time here, not "Mon"/"Jan 5". */
private fun messageTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
}

/** Short time label for a chat row (e.g. "3:45 PM", "Mon", "Jan 5"). */
private fun chatTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = epochMs }
    return when {
        now.get(java.util.Calendar.DATE)  == then.get(java.util.Calendar.DATE) &&
        now.get(java.util.Calendar.MONTH) == then.get(java.util.Calendar.MONTH) &&
        now.get(java.util.Calendar.YEAR)  == then.get(java.util.Calendar.YEAR) ->
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
        now.get(java.util.Calendar.WEEK_OF_YEAR) == then.get(java.util.Calendar.WEEK_OF_YEAR) &&
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
        java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
        else ->
        java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
    }
}

private fun channelActivityTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diffMs < minute -> "Now"
        diffMs < hour -> "${diffMs / minute}m"
        diffMs < day -> "${diffMs / hour}h"
        diffMs < 2 * day -> "Yesterday"
        else -> chatTime(epochMs)
    }
}

// - - - Structured replies - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// A reply is encoded inside the message text so it round-trips through ANY backend
// (direct, support, group, channel) with no schema change. Layout:
//   RPLY{targetId}{sender}{snippet}{body}
// Everything before  is metadata; the visible body follows it.
private const val REPLY_PREFIX = "RPLY"
private const val REPLY_BODY_SEP = ''

private data class ReplyRef(val targetId: String, val sender: String, val snippet: String, val body: String)

/** Build the wire text for a reply message. */
private fun encodeReply(targetId: String, sender: String, snippet: String, body: String): String {
    fun clean(s: String) = s.replace('', ' ').replace('', ' ')
        .replace('\n', ' ').replace('\r', ' ').trim().take(160)
    return REPLY_PREFIX + clean(targetId) + "" + clean(sender) + "" + clean(snippet) + REPLY_BODY_SEP + body
}

/** Parse a reply-encoded message back into its parts, or null if it isn't one. */
private fun decodeReply(text: String): ReplyRef? {
    if (!text.startsWith(REPLY_PREFIX)) return null
    val sep = text.indexOf(REPLY_BODY_SEP)
    if (sep < 0) return null
    val head = text.substring(REPLY_PREFIX.length, sep).split('')
    if (head.size < 3) return null
    return ReplyRef(head[0], head[1], head[2], text.substring(sep + 1))
}

private fun replyRefFor(message: Message): ReplyRef? {
    val encoded = decodeReply(message.text)
    if (encoded != null) return encoded
    if (message.replyToMessageId.isBlank()) return null
    val sender = message.replySenderName.ifBlank { "Reply" }
    val snippet = message.replyPreview.ifBlank { "Original message" }
    return ReplyRef(message.replyToMessageId, sender, snippet, strippedReplyText(message.text))
}

/** Visible text with any reply encoding removed - - for previews, copy, search, forward. */
private fun strippedReplyText(text: String): String = decodeReply(text)?.body ?: text

/** Short quoted-preview of the message being replied to (used in the reply chip). */
private fun replySnippetFor(message: Message): String = when (message.type) {
    "image" -> "Photo"
    "video" -> "Video"
    "voice" -> "Voice message"
    else    -> strippedReplyText(message.text).trim().ifBlank { "Message" }
}

/** Modern quoted-reply chip rendered above the body of a reply message. */
@Composable
private fun QuotedReplyChip(ref: ReplyRef, isMine: Boolean, onClick: () -> Unit) {
    val barColor     = if (isMine) Color(0xFF9EF2BC) else BrandGreen
    val nameColor    = if (isMine) Color(0xFFB9F5CE) else BrandGreen
    val snippetColor = if (isMine) Color.White.copy(alpha = 0.80f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val bg      = if (isMine) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
    Row(
        Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(bg)
        .clickable(onClick = onClick)
        .height(IntrinsicSize.Min)
        .widthIn(max = 260.dp)
        .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(barColor))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(vertical = 5.dp)) {
        Text(ref.sender.ifBlank { "Reply" }, color = nameColor, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ref.snippet, color = snippetColor, fontSize = 13.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun copyTextFor(message: Message): String {
    if (message.type == "deleted" || message.deleted) return ""
    val body = strippedReplyText(message.text).trim()
    if (body.isNotBlank()) return body
    return when (message.type) {
        "image" -> ""
        "video" -> "[Video]"
        "voice" -> "[Voice message]"
        "file", "document" -> "[File]"
        else -> ""
    }
}

private fun forwardTextFor(message: Message): String {
    val body = strippedReplyText(message.text).trim()
    val content = when (message.type) {
        "image" -> body.ifBlank { "[Photo]" }
        "video" -> body.ifBlank { "[Video]" }
        "voice" -> body.ifBlank { "[Voice message]" }
        "deleted" -> "Message deleted"
        else -> body
    }
    return content.ifBlank { "Forwarded message" }
}

private fun channelPreview(room: CommunityRoom): String {
    val source = strippedReplyText(room.lastPostPreview
        .ifBlank { room.lastMessage })
        .ifBlank { if (room.lastPostAt > 0L || room.lastMessageAt > 0L) "New post available" else "" }
        .ifBlank { "No posts yet" }
    val words = source.replace(Regex("\\s+"), " ").trim().split(" ").filter { it.isNotBlank() }
    if (words.size <= 8) return source
    return words.take(8).joinToString(" ") + "..."
}

private fun String.csvToIdSet(): Set<String> =
    split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

private fun isStandaloneEmojiText(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank() || trimmed.startsWith(":hab_")) return false
    if (trimmed.any { it.isLetterOrDigit() }) return false
    val count = trimmed.codePointCount(0, trimmed.length)
    return count in 1..6
}

private fun channelCategory(room: CommunityRoom): Pair<String, String> {
    val text = "${room.name} ${room.description}".lowercase(Locale.getDefault())
    return when {
        listOf("job", "career", "vacancy", "work").any { it in text } -> "Job" to "Jobs"
        listOf("game", "gaming", "pot", "play").any { it in text } -> "Game" to "Gaming"
        listOf("market", "shop", "sale", "deal", "store").any { it in text } -> "Shop" to "Marketplace"
        listOf("music", "song", "artist").any { it in text } -> "Music" to "Music"
        listOf("sport", "football", "soccer").any { it in text } -> "Sport" to "Sports"
        listOf("news", "media", "daily").any { it in text } -> "News" to "News"
        else -> "Info" to "Announcement"
    }
}

private data class ParsedPayment(val amount: String, val recipient: String, val note: String)

private fun isPaymentMessage(text: String): Boolean =
    text.contains("Sent", ignoreCase = true) && text.contains(" to ", ignoreCase = true)

private fun isLocationMessage(text: String): Boolean =
    text.contains("maps.google", ignoreCase = true) || text.contains("My location", ignoreCase = true)

private fun parsePaymentText(text: String): ParsedPayment {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    val sentPart = clean.substringAfter("Sent", clean).trim()
    val amount = sentPart.substringBefore(" to ", sentPart).trim().ifBlank { "ETB" }
    val afterTo = sentPart.substringAfter(" to ", "").trim()
    val recipient = afterTo.substringBefore(" - ").substringBefore(" -- ").substringBefore(" ? ").trim().ifBlank { "AdisChat user" }
    val note = when {
        afterTo.contains(" - ") -> afterTo.substringAfter(" - ").trim()
        afterTo.contains(" -- ") -> afterTo.substringAfter(" -- ").trim()
        afterTo.contains(" ? ") -> afterTo.substringAfter(" ? ").trim()
        else -> ""
    }
    val prettyAmount = amount.replace(Regex("^(ETB)(\\d)"), "ETB $2")
    return ParsedPayment(prettyAmount, recipient, note)
}

/** Date header label inside a message thread (e.g. "Today", "Yesterday", "Mon Jan 5"). */
private fun chatDate(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = epochMs }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff  = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear -> java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
    }
}

/**
 * Professional presence subtitle for a direct chat header.
 *   online       - - "online"
 *   < 1 min ago       - - "last seen just now"
 *   < 60 min ago      - - - "last seen 5 min ago"
 *   earlier today     - - - "last seen today at 10:00"
 *   yesterday       - - "last seen yesterday at 21:34"
 *   older / same year - - - "last seen Mar 4 at 10:00"
 *   different year    - - - "last seen Mar 4, 2025"
 *   unknown       - - "last seen recently"
 */
private fun presenceSubtitle(presence: PeerPresence?, hideExactLastSeen: Boolean = false): String {
    if (presence == null) return "last seen recently"
    if (presence.online) return "online"
    if (hideExactLastSeen) return "last seen recently"
    val seenMs = normalizePresenceTimestampMs(presence.lastSeen)
    if (seenMs <= 0L) return "last seen recently"

    val nowMs   = System.currentTimeMillis()
    val diffMin = ((nowMs - seenMs).coerceAtLeast(0L)) / 60_000L
    if (diffMin < 1)  return "last seen now"
    if (diffMin < 60) return "last seen $diffMin min ago"

    val now  = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = seenMs }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val todayStart = (now.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val thenStart = (then.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val dayDiff  = ((todayStart.timeInMillis - thenStart.timeInMillis) / 86_400_000L).toInt()
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(seenMs))
    return when {
        sameYear && dayDiff == 0 -> "last seen at $time"
        sameYear && dayDiff == 1 -> "last seen yesterday at $time"
        sameYear -> "last seen ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(seenMs))} at $time"
        else     -> "last seen ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(seenMs))}"
    }
}

private fun normalizePresenceTimestampMs(value: Long): Long = when {
    value <= 0L -> 0L
    value < 10_000_000_000L -> value * 1000L
    else -> value
}

/** Format a voice note duration: "0:04", "1:23". */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}


// - - - Media caption / preview sheet (clean labels, no device filenames) - - - - - - - - - - - -
private data class PendingMedia(val uri: android.net.Uri, val type: String)  // type: "image" | "video"

/**
 * Reusable multi-select media preview for direct AND group chats. Fixed header
 * + caption + Send footer (never cropped), capped/scrollable preview area, safe
 * for small screens, the keyboard, and the Android navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPreviewSheet(
    items: List<PendingMedia>,
    accent: Color,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
    onCancel: () -> Unit,
    onSend: (caption: String) -> Unit,
    onReplace: (Int, android.net.Uri) -> Unit = { _, _ -> },
) {
    if (items.isEmpty()) return
    var caption by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(0) }
    val idx = current.coerceIn(0, items.lastIndex)
    val item = items[idx]
    var editing by remember { mutableStateOf<Int?>(null) }

    // Full-screen editor overlays the sheet; on Done it replaces this item's uri.
    editing?.let { ei ->
        if (ei in items.indices && items[ei].type == "image") {
        Dialog(onDismissRequest = { editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImageEditorScreen(
        uri = items[ei].uri,
        onCancel = { editing = null },
        onDone = { newUri -> onReplace(ei, newUri); editing = null },
        )
        }
        return
        } else editing = null
    }
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    // Preview capped so header + thumbnails + caption + Send always fit.
    val previewMax = (screenH * 0.42f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // A real ModalBottomSheet anchors above the system navigation bar and lifts
    // for the keyboard, so the caption + Send footer is never cropped (a custom
    // Dialog doesn't reliably receive the nav-bar inset - - that was the bug).
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0F14),
        contentColor = Color.White,
        dragHandle = {
        Box(
        Modifier.padding(top = 10.dp).size(width = 40.dp, height = 4.dp)
        .clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.25f)),
        )
        },
    ) {
        Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        ) {
        // - - - Header - - -
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        if (items.size > 1) "${items.size} selected" else "Send ${if (item.type == "video") "Video" else "Photo"}",
        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF1A2230)).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(12.dp))

        // - - - Capped preview (contain - - never full-screen) - - -
        Box(
        Modifier.fillMaxWidth().heightIn(min = 140.dp, max = previewMax)
        .clip(RoundedCornerShape(18.dp)).background(Color(0xFF101722)),
        contentAlignment = Alignment.Center,
        ) {
        if (item.type == "image") {
        AsyncImage(model = item.uri, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // Edit (crop/text/brush/stickers) - - opens the full-screen editor.
        Row(
        Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(999.dp))
        .background(Color.Black.copy(alpha = 0.55f)).clickable { editing = idx }
        .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Edit, "Edit", tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("Edit", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Videocam, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(8.dp))
        Text("Video ready to send", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        }
        }

        // - - - Thumbnail strip (multi-select) - - -
        if (items.size > 1) {
        Spacer(Modifier.height(10.dp))
        Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        items.forEachIndexed { i, m ->
        Box(Modifier.size(60.dp)) {
        Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0xFF101722))
        .border(2.dp, if (i == idx) accent else Color.Transparent, RoundedCornerShape(12.dp))
        .clickable { current = i },
        ) {
        AsyncImage(model = m.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (m.type == "video") {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
        }
        Box(
        Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clip(CircleShape)
        .background(Color.Black.copy(alpha = 0.6f)).clickable { onRemove(i) },
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
        }
        Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF141A24))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).clickable(onClick = onAddMore),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "Add more", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        }
        Spacer(Modifier.height(12.dp))

        // - - - Footer: caption + Send (always visible) - - -
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF141A24)).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
        ) {
        if (caption.isBlank()) Text("Add a caption...", color = Color(0xFF7F8A94), fontSize = 15.sp)
        BasicTextField(
        value = caption, onValueChange = { caption = it },
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
        maxLines = 4, modifier = Modifier.fillMaxWidth(),
        )
        }
        Spacer(Modifier.width(10.dp))
        Box(
        Modifier.size(52.dp).clip(CircleShape).background(accent).clickable { onSend(caption.trim()) },
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color(0xFF05070D), modifier = Modifier.size(22.dp))
        }
        }
        }
    }
}

/** Resolve a human-readable file name for a content uri. */
private fun queryFileName(context: android.content.Context, uri: android.net.Uri): String {
    var name = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: ""
        }
    } catch (_: Exception) {}
    if (name.isBlank()) name = uri.lastPathSegment ?: "file"
    return name
}

/** Read a contact's display name and primary phone number from a contact uri. */
private fun readContact(context: android.content.Context, uri: android.net.Uri): Pair<String, String> {
    var name = ""
    var phone = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
        val nameIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
        if (nameIdx >= 0) name = c.getString(nameIdx) ?: ""
        val idIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
        val hasPhoneIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
        if (idIdx >= 0 && hasPhoneIdx >= 0 && c.getInt(hasPhoneIdx) > 0) {
        val contactId = c.getString(idIdx)
        context.contentResolver.query(
        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null,
        "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
        )?.use { p ->
        val pIdx = p.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (pIdx >= 0 && p.moveToFirst()) phone = p.getString(pIdx) ?: ""
        }
        }
        }
        }
    } catch (_: Exception) {}
    return name to phone
}

/**
 * Resolve the device location for sharing: a recent cached fix if available,
 * otherwise actively request a single fresh fix (GPS - - - network) with a timeout.
 * Returns null only if no provider is enabled or it times out.
 */
@android.annotation.SuppressLint("MissingPermission")
private suspend fun awaitCurrentLocation(context: android.content.Context, timeoutMs: Long): android.location.Location? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null

    // 1. Best recent cached fix (< 2 minutes old).
    var best: android.location.Location? = null
    for (p in runCatching { lm.getProviders(true) }.getOrDefault(emptyList())) {
        val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (best == null || loc.accuracy < best!!.accuracy) best = loc
    }
    if (best != null && System.currentTimeMillis() - best!!.time < 120_000L) return best

    // 2. Actively request a single fresh fix.
    val provider = when {
        runCatching { lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
        android.location.LocationManager.GPS_PROVIDER
        runCatching { lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
        android.location.LocationManager.NETWORK_PROVIDER
        else -> return best
    }
    val fresh = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
        val listener = object : android.location.LocationListener {
        override fun onLocationChanged(loc: android.location.Location) {
        runCatching { lm.removeUpdates(this) }
        if (cont.isActive) cont.resumeWith(Result.success(loc))
        }
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("Required on API < 30") @Suppress("DEPRECATION")
        override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
        }
        runCatching {
        lm.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
        }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }
    }
    return fresh ?: best
}

/** Get the location and send it as a Google Maps link via [onSendText]. */
@android.annotation.SuppressLint("MissingPermission")
// Confirmation dialog: loads the current location, shows it on a preview map +
// resolved address, and only sends after the user confirms it's correct.
@Composable
private fun LocationConfirmDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val isDark  = isInDarkTheme
    val surface   = if (isDark) Color(0xFF13171A) else Color.White
    val previewBg = if (isDark) Color(0xFF0E1B14) else Color(0xFFE8F5EC)
    val textMain  = if (isDark) Color.White else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF8B949E) else Color(0xFF6B7280)

    var loading by remember { mutableStateOf(true) }
    var lat     by remember { mutableStateOf<Double?>(null) }
    var lng     by remember { mutableStateOf<Double?>(null) }
    var address by remember { mutableStateOf("") }
    var failed  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Try a recent cached fix first, then actively request a fresh one
        // (the old code only read getLastKnownLocation - - - "No location fix yet").
        val result = awaitCurrentLocation(context, timeoutMs = 15_000L)
        if (result == null) {
        failed = true; loading = false
        } else {
        lat = result.latitude; lng = result.longitude
        address = withContext(Dispatchers.IO) {
        runCatching {
        @Suppress("DEPRECATION")
        android.location.Geocoder(context).getFromLocation(result.latitude, result.longitude, 1)
        ?.firstOrNull()
        ?.let { a ->
        listOfNotNull(a.thoroughfare, a.subLocality, a.locality, a.adminArea, a.countryName)
        .distinct().joinToString(", ")
        } ?: ""
        }.getOrDefault("")
        }
        loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(tr("Share location"), color = textMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(tr("Confirm this is your correct location before sending."),
        color = textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Preview card
        Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(previewBg),
        contentAlignment = Alignment.Center,
        ) {
        when {
        loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(tr("Locating you..."), color = textMuted, fontSize = 13.sp)
        }
        failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LocationOff, null, tint = textMuted, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(tr("No location fix yet"), color = textMuted, fontSize = 13.sp)
        }
        else -> Icon(Icons.Default.LocationOn, null, tint = accent, modifier = Modifier.size(46.dp))
        }
        }

        if (!loading && !failed) {
        Spacer(Modifier.height(12.dp))
        if (address.isNotBlank()) {
        Text(address, color = textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        }
        Text(tr("%.5f, %.5f").format(lat ?: 0.0, lng ?: 0.0), color = textMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = textMuted) }
        Spacer(Modifier.weight(1f))
        Button(
        onClick = { val la = lat; val lo = lng; if (la != null && lo != null) onConfirm(la, lo) },
        enabled = !loading && !failed,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) {
        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(tr("Send location"), fontWeight = FontWeight.Bold)
        }
        }
        }
        }
    }
}

private fun shareLocation(context: android.content.Context, onSendText: (String) -> Unit) {
    try {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (lm == null) {
        android.widget.Toast.makeText(context, "Location unavailable", android.widget.Toast.LENGTH_SHORT).show()
        return
        }
        var best: android.location.Location? = null
        for (provider in lm.getProviders(true)) {
        val loc = lm.getLastKnownLocation(provider) ?: continue
        if (best == null || loc.accuracy < best!!.accuracy) best = loc
        }
        if (best == null) {
        android.widget.Toast.makeText(context, "No location fix yet. Try again.", android.widget.Toast.LENGTH_SHORT).show()
        return
        }
        val lat = best!!.latitude
        val lng = best!!.longitude
        onSendText("My location\nhttps://maps.google.com/?q=$lat,$lng")
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Couldn't get location", android.widget.Toast.LENGTH_SHORT).show()
    }
}


class VoiceRecorderState(private val context: android.content.Context) {

    private var recorder: android.media.MediaRecorder? = null
    private var outputFile: java.io.File? = null

    var isRecording by androidx.compose.runtime.mutableStateOf(false)
        private set

    var startTimeMs = 0L
        private set

    fun start(): Boolean {
        if (isRecording) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        return false
        }
        val file = java.io.File.createTempFile("voice_", ".m4a", context.cacheDir)
        outputFile  = file
        startTimeMs = System.currentTimeMillis()
        return try {
        recorder = android.media.MediaRecorder().apply {
        setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
        setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
        setOutputFile(file.absolutePath)
        prepare()
        start()
        }
        isRecording = true
        true
        } catch (_: SecurityException) {
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
        false
        } catch (_: Exception) {
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
        false
        }
    }

    fun stop(): Pair<java.io.File, Long>? {
        val durationMs = System.currentTimeMillis() - startTimeMs
        return try {
        recorder?.apply { stop(); release() }
        recorder    = null
        isRecording = false
        val f = outputFile ?: return null
        f to durationMs
        } catch (e: Exception) {
        recorder    = null
        isRecording = false
        null
        }
    }

    fun cancel() {
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder    = null
        isRecording = false
        outputFile?.delete()
        outputFile  = null
    }
}
