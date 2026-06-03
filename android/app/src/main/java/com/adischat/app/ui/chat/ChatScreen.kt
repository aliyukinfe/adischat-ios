package com.adischat.app.ui.chat

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.UserSummary
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.data.preferences.WalletTransaction
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.gamepot.GamePotBannerHost
import android.widget.Toast
import androidx.compose.ui.graphics.SolidColor
import com.adischat.app.ui.channel.ChannelFeedScreen
import com.adischat.app.ui.channel.ForwardSheet
import com.adischat.app.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

// ─── Design tokens ─────────────────────────────────────────────────────────────
// All surfaces adapt via MaterialTheme.colorScheme; only brand constants live here.

// ── HTML design tokens (matching chat.html) ───────────────────────────────────
private val BrandGreen        = Color(0xFF18C964)   // --accent-green
private val BrandGreenBright  = Color(0xFF34D399)   // --accent-emerald
private val BubbleGradStart   = Color(0xFF15B85A)   // sent gradient start
private val BubbleGradEnd     = Color(0xFF18C964)   // sent gradient end (135°)
private val DeleteRed         = Color(0xFFEF4444)

// Thread canvas (chat background)
private val CanvasLight = Color(0xFFF0F2F5)
private val CanvasDark  = Color(0xFF0A0A0A)         // --bg-primary

// Received bubble
private val BubbleRecvDark  = Color(0xFF1A1A1A)     // --bubble-received
private val BubbleRecvLight = Color(0xFFFFFFFF)
private val BubbleRecvBorderDark  = Color(0x0FFFFFFF)  // rgba(255,255,255,0.06)
private val BubbleRecvBorderLight = Color(0x1A000000)

// ─── Composable colour helpers ─────────────────────────────────────────────────

private val isInDarkTheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

private val brandAccent: Color
    @Composable get() = if (isInDarkTheme) BrandGreen else BrandGreen

private val threadCanvas: Color
    @Composable get() = if (isInDarkTheme) CanvasDark else CanvasLight

private val bubbleReceivedColor: Color
    @Composable get() = if (isInDarkTheme) BubbleRecvDark else BubbleRecvLight

private val bubbleReceivedBorder: Color
    @Composable get() = if (isInDarkTheme) BubbleRecvBorderDark else BubbleRecvBorderLight

// ─── Tab enum ─────────────────────────────────────────────────────────────────
private enum class MessageTab { Chats, Channels, Groups }

// ══════════════════════════════════════════════════════════════════════════════
// ENTRY POINT
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val vm             : ChatViewModel = viewModel()
    val uiState        by vm.uiState.collectAsStateWithLifecycle()
    val convs          by vm.conversations.collectAsStateWithLifecycle()
    val messages       by vm.messages.collectAsStateWithLifecycle()
    val sending        by vm.sending.collectAsStateWithLifecycle()
    val error          by vm.error.collectAsStateWithLifecycle()
    val query          by vm.searchQuery.collectAsStateWithLifecycle()
    val results        by vm.searchResults.collectAsStateWithLifecycle()
    val communityQuery by vm.communityQuery.collectAsStateWithLifecycle()
    val channels       by vm.channels.collectAsStateWithLifecycle()
    val groups         by vm.groups.collectAsStateWithLifecycle()
    val me             by vm.me.collectAsStateWithLifecycle()
    var refreshing     by remember { mutableStateOf(false) }
    val scope          = rememberCoroutineScope()

    BackHandler(enabled = uiState is ChatUiState.Thread || uiState is ChatUiState.NewChat) {
        vm.navigateBack()
    }

    error?.let { msg ->
        LaunchedEffect(msg) { delay(3_500); vm.clearError() }
    }

    when (val s = uiState) {
        is ChatUiState.Loading           -> LoadingScreen()
        is ChatUiState.Error             -> ErrorScreen(s.message, vm::retryLoad)
        is ChatUiState.ConversationsList ->
            ConversationsListScreen(
                conversations          = convs,
                channels               = channels,
                groups                 = groups,
                communityQuery         = communityQuery,
                userResults            = results,
                errorMsg               = error,
                isRefreshing           = refreshing,
                onRefresh              = {
                    refreshing = true; vm.retryLoad()
                    scope.launch { delay(650); refreshing = false }
                },
                onOpen                 = vm::openConversation,
                onNewChat              = vm::navigateToNewChat,
                onChatQueryChange      = vm::onSearchQueryChanged,
                onSelectUser           = vm::startConversation,
                onCommunityQueryChange = vm::onCommunityQueryChanged,
                onOpenCommunity        = vm::openCommunity,
                onJoinCommunity        = vm::joinCommunity,
                onCreateCommunity      = vm::createCommunity,
                onDismissError         = vm::clearError,
            )
        is ChatUiState.NewChat ->
            NewChatScreen(
                query         = query,
                results       = results,
                onQueryChange = vm::onSearchQueryChanged,
                onSelectUser  = vm::startConversation,
                onBack        = vm::navigateBack,
            )
        is ChatUiState.Thread ->
            if (s.isCommunity && s.communityType == "channel") {
                ChannelFeedScreen(
                    channelId             = s.conversationId,
                    channelName           = s.otherUserName.ifBlank { "Channel" },
                    channelPhoto          = s.otherUserPhotoUrl,
                    channelDescription    = s.communityDescription,
                    subscriberCount       = s.communityMemberCount,
                    isAdmin               = s.communityOwnerUid == me?.uid || me?.role == "admin",
                    myUid                 = me?.uid ?: "",
                    onBack                = vm::navigateBack,
                    onUpdateCommunity     = vm::updateCurrentCommunity,
                    onUploadCommunityPhoto= vm::uploadCommunityPhoto,
                    onDeleteCommunity     = vm::deleteCurrentCommunity,
                )
            } else {
                ConversationView(
                title              = s.otherUserName.ifBlank { "Chat" },
                subtitle           = when {
                    s.isSupport   -> "We typically reply within minutes"
                    s.isCommunity -> "Group chat"
                    else          -> "Direct message"
                },
                photoUrl           = s.otherUserPhotoUrl,
                threadAccent       = if (s.isSupport) Amber else accentFor(s.conversationId),
                isSupportHeader    = s.isSupport,
                isCommunity        = s.isCommunity,
                channelId          = if (s.isCommunity) s.conversationId else "",
                communityOwnerUid  = s.communityOwnerUid,
                profileKind        = s.communityType,
                profileDescription = s.communityDescription,
                profileMemberCount = s.communityMemberCount,
                messages           = messages,
                currentUid         = me?.uid ?: "",
                canManageCommunity = s.isCommunity && (s.communityOwnerUid == me?.uid || me?.role == "admin"),
                canSend            = !s.isCommunity || s.communityType == "group" || s.communityOwnerUid == me?.uid || me?.role == "admin",
                sending            = sending,
                onBack             = vm::navigateBack,
                onSend             = vm::sendMessage,
                onUpdateCommunity  = vm::updateCurrentCommunity,
                onUploadCommunityPhoto = vm::uploadCommunityPhoto,
                onDeleteCommunity  = vm::deleteCurrentCommunity,
                onReact            = vm::reactToMessage,
                onDelete           = vm::deleteMessage,
                onModerateMember   = vm::moderateGroupMember,
                onSendImage        = vm::sendImage,
                onSendVideo        = vm::sendVideo,
                onSendVoice        = vm::sendVoice,
                onMessagesShown    = vm::markCurrentRead,
            )
            }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LOADING & ERROR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingScreen() {
    Box(
        modifier          = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                text  = "Connecting…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier            = Modifier.padding(36.dp),
        ) {
            Icon(
                imageVector      = Icons.Default.CloudOff,
                contentDescription = null,
                tint             = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier         = Modifier.size(52.dp),
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
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CONVERSATIONS LIST
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsListScreen(
    conversations         : List<Conversation>,
    channels              : List<CommunityRoom>,
    groups                : List<CommunityRoom>,
    communityQuery        : String,
    userResults           : List<UserSummary>,
    errorMsg              : String?,
    isRefreshing          : Boolean,
    onRefresh             : () -> Unit,
    onOpen                : (Conversation) -> Unit,
    onNewChat             : () -> Unit,
    onChatQueryChange     : (String) -> Unit,
    onSelectUser          : (UserSummary) -> Unit,
    onCommunityQueryChange: (String) -> Unit,
    onOpenCommunity       : (CommunityRoom) -> Unit,
    onJoinCommunity       : (CommunityRoom) -> Unit,
    onCreateCommunity     : (String, String, String) -> Unit,
    onDismissError        : () -> Unit,
) {
    var activeTab       by remember { mutableStateOf(MessageTab.Chats) }
    var searchQuery     by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var pinnedIdsCsv    by rememberSaveable { mutableStateOf("") }
    var hiddenIdsCsv    by rememberSaveable { mutableStateOf("") }
    var deleteTarget    by remember { mutableStateOf<Conversation?>(null) }
    val accent          = brandAccent

    // Payment notifications surface inside the Chats list (no separate bell).
    var payments by remember { mutableStateOf<List<WalletTransaction>>(emptyList()) }
    var showPaymentsChannel by remember { mutableStateOf(false) }
    // Tracks the newest transaction the user has already seen; unread badge clears once opened.
    var seenPaymentTs by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(isRefreshing) {
        runCatching { WalletRepository().getTransactions() }
            .getOrNull()?.getOrNull()
            ?.sortedByDescending { it.timestamp }
            ?.let { payments = it }
    }

    // Payments channel — opens a dedicated screen with all payment notifications.
    if (showPaymentsChannel) {
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
    val tabRooms     = if (activeTab == MessageTab.Channels) channels else groups
    val listCount    = when (activeTab) {
        MessageTab.Chats    -> visibleConversations.size
        MessageTab.Channels -> channels.size
        MessageTab.Groups   -> groups.size
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
            title = { Text("Delete chat?") },
            text = { Text("This removes ${conv.otherUserName.ifBlank { "this contact" }} from your chat list on this phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveHidden(hiddenIds + conv.id)
                        savePinned(pinnedIds - conv.id)
                        deleteTarget = null
                    },
                ) { Text("Delete", color = DeleteRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    val communityMode = activeTab != MessageTab.Chats
    val screenBg = Color(0xFF02070D)

    Scaffold(
        containerColor = screenBg,
        // Floating action button — new chat (Chats tab) or create community
        snackbarHost = {
            errorMsg?.let {
                Snackbar(
                    action = { TextButton(onClick = onDismissError) { Text("OK", color = accent) } },
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

                // ── Header ──────────────────────────────────────────────
                ListScreenHeader(
                    title = listTitle,
                    count = listCount,
                    accent = accent,
                    activeTab = activeTab,
                    onBack = { activeTab = MessageTab.Chats },
                    onAction = { if (activeTab == MessageTab.Chats) onNewChat() else showCreateSheet = true },
                )

                // ── Search bar ──────────────────────────────────────────
                ChatSearchBar(
                    value         = activeSearch,
                    accent        = accent,
                    onValueChange = {
                        if (activeTab == MessageTab.Chats) {
                            searchQuery = it
                            onChatQueryChange(it)   // also search people to start new chats
                        } else onCommunityQueryChange(it)
                    },
                    placeholder   = when (activeTab) {
                        MessageTab.Chats    -> "Search messages and people…"
                        MessageTab.Channels -> "Search channels…"
                        MessageTab.Groups   -> "Search groups…"
                    },
                )

                Spacer(Modifier.height(2.dp))

                // ── Content ─────────────────────────────────────────────
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
                            contentPadding = PaddingValues(top = 10.dp, bottom = 112.dp),
                        ) {
                            // Payments channel entry (all transaction notifications live here)
                            if (searchQuery.isBlank()) {
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
                                    InsetDivider()
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
                                    InsetDivider()
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

            // ── Bottom tab dock (Chats / Channels / Groups) ─────────────
            TabDock(
                activeTab   = activeTab,
                onTabChange = { activeTab = it },
                accent      = accent,
                modifier    = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ── List screen sub-components ────────────────────────────────────────────────

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
        MessageTab.Channels -> Icons.Default.Campaign
        MessageTab.Groups -> Icons.Default.GroupAdd
    }
    val actionLabel = when (activeTab) {
        MessageTab.Chats -> "New chat"
        MessageTab.Channels -> "Create channel"
        MessageTab.Groups -> "Create group"
    }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text          = title,
                fontSize = 34.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = Color.White,
                letterSpacing = 0.sp,
            )
            if (count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(15.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text       = "$count active",
                        fontSize = 18.sp,
                        color      = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Box(
            modifier         = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF1D2335), Color(0xFF101625)))
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = actionIcon,
                contentDescription = actionLabel,
                tint               = Color(0xFFE7ECFF),
                modifier           = Modifier.size(27.dp),
            )
            if (activeTab != MessageTab.Chats) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatSearchBar(value: String, onValueChange: (String) -> Unit, placeholder: String, accent: Color = brandAccent) {
    val glass = if (isInDarkTheme) Color(0xFF151B2A).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.78f)
    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp)
            .height(48.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = accent.copy(alpha = 0.10f)),
        shape          = RoundedCornerShape(26.dp),
        color          = glass,
        border         = BorderStroke(1.dp, Color.White.copy(alpha = if (isInDarkTheme) 0.08f else 0.18f)),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier          = Modifier.padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = null,
                tint               = if (isInDarkTheme) Color(0xFFD3DAF0) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(23.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                singleLine    = true,
                textStyle     = TextStyle(
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                ),
                modifier      = Modifier.weight(1f),
                cursorBrush   = SolidColor(accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text     = placeholder,
                                fontSize = 16.sp,
                                color    = if (isInDarkTheme) Color(0xFFB8BED3) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(
                    onClick  = { onValueChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(15.dp),
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
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text          = text.uppercase(),
            fontSize = 14.sp,
            fontWeight    = FontWeight.Bold,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 100.dp, end = 42.dp),
        thickness = 0.5.dp,
        color     = Color.White.copy(alpha = 0.065f),
    )
}

@Composable
private fun EmptyChatState(isSearching: Boolean, accent: Color) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(bottom = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = if (isSearching) Icons.Default.Search else Icons.Default.Forum,
                    contentDescription = null,
                    tint               = accent,
                    modifier           = Modifier.size(36.dp),
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
                            else "Tap the ✎ button to start chatting",
                fontSize = 17.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Conversation row ───────────────────────────────────────────────────────────

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

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(
                if (unread) Color(0xFF0A151F).copy(alpha = 0.72f)
                else Color.Transparent,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            )
            .padding(start = 22.dp, end = 22.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — no always-on green dot; presence data is not available
        ChatAvatar(
            photoUrl = conv.otherUserPhotoUrl,
            initials = initialsOf(conv.otherUserName),
            size     = 52.dp,
            accent   = accentFor(conv.id),
        )

        Spacer(Modifier.width(13.dp))

        // Name + preview + meta
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = conv.otherUserName.ifBlank { "Unknown" },
                    fontSize = 16.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = chatTime(conv.lastMessageAt),
                    fontSize = 13.sp,
                    color      = if (unread) accent else Color(0xFFAEB4C7),
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = conv.lastMessage.ifBlank { "Say hello 👋" },
                    fontSize = 13.sp,
                    color      = if (unread) Color(0xFFE7ECFF) else Color(0xFFB9C0D4),
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                when {
                    unread   -> UnreadBadge(count = conv.unreadCount, accent = accent)
                    isPinned -> Icon(
                        imageVector        = Icons.Default.PushPin,
                        contentDescription = null,
                        tint               = Color(0xFFAEB4C7).copy(alpha = 0.55f),
                        modifier           = Modifier.size(13.dp),
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
            text = { Text("Delete chat", color = DeleteRed) },
            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = DeleteRed) },
            onClick = {
                menuOpen = false
                onDelete()
            },
        )
    }
}

// ── Payment notification row ───────────────────────────────────────────────────

@Composable
private fun PaymentNotificationRow(tx: WalletTransaction) {
    val incoming = tx.amount >= 0
    val tint     = if (incoming) Color(0xFF18C964) else Color(0xFFFF6B6B)
    val sign     = if (incoming) "+" else "−"
    val title    = tx.title.ifBlank { if (incoming) "Payment received" else "Payment sent" }
    val sub      = when {
        tx.counterpartyName.isNotBlank() -> tx.counterpartyName
        tx.note.isNotBlank()             -> tx.note
        else                             -> tx.status.ifBlank { "Wallet activity" }
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint               = tint,
                modifier           = Modifier.size(24.dp),
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

// ── Payments channel row + screen ──────────────────────────────────────────────

@Composable
private fun PaymentsChannelRow(
    latest: WalletTransaction?,
    unread: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    val brand = Color(0xFF18C964)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(brand.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = brand, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Payments",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                )
                latest?.let {
                    Text(chatTime(it.timestamp), fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = latest?.let { paymentSummary(it) } ?: "Transaction notifications appear here",
                    fontSize = 16.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    val sign = if (tx.amount >= 0) "+" else "−"
    val who  = tx.counterpartyName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
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
                        Text("Payments", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Transaction notifications", fontSize = 13.sp,
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
                            Text("No payment notifications yet",
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

// ── Community panels ───────────────────────────────────────────────────────────

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
            modifier         = Modifier.fillMaxSize().padding(bottom = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(32.dp),
            ) {
                Box(
                    modifier         = Modifier.size(72.dp).clip(CircleShape).background(accent.copy(0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = if (type == "channel") Icons.Default.Campaign else Icons.Default.Groups,
                        contentDescription = null,
                        tint               = accent,
                        modifier           = Modifier.size(32.dp),
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

    // Latest activity first (Telegram-style) — joined channels with recent posts bubble up.
    val sorted = remember(rooms) {
        rooms.sortedByDescending { it.lastPostAt.takeIf { time -> time > 0L } ?: it.lastMessageAt }
    }
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 112.dp),
    ) {
        items(sorted, key = { it.id }) { room ->
            PremiumCommunityRow(room = room, onOpen = { onOpen(room) }, onJoin = { onJoin(room) }, accent = accent)
            HorizontalDivider(
                modifier = Modifier.padding(start = 100.dp, end = 42.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.065f),
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (room.joined) onOpen() else onJoin() }
            .padding(start = 22.dp, end = 22.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChatAvatar(room.photoUrl, initialsOf(room.name), 52.dp, accent)
        }
        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.name,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White,
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
                text = preview,
                fontSize = 13.sp,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    unread -> Color(0xFFE7ECFF)
                    preview.equals("No posts yet", ignoreCase = true) -> Color(0xFF8F96AA)
                    else -> Color(0xFFB9C0D4)
                },
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
                color = if (unread) accent else Color(0xFFAEB4C7),
                maxLines = 1,
            )
            Spacer(Modifier.height(7.dp))
            when {
                !room.joined -> JoinMiniButton(accent = accent, onJoin = onJoin)
                room.unreadCount > 0 -> UnreadPostBadge(count = room.unreadCount, accent = accent)
                else -> Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChannelVerifiedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = "Verified channel",
        tint = Color(0xFF35A7FF),
        modifier = modifier
            .size(19.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color(0x4435A7FF),
                spotColor = Color(0x7735A7FF),
            ),
    )
}

// ── Create community sheet ─────────────────────────────────────────────────────

@Composable
private fun UnreadPostBadge(count: Int, accent: Color) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
            .shadow(10.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.55f))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF45E27A), Color(0xFF15A653))))
            .padding(horizontal = if (count > 9) 8.dp else 0.dp),
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
    var name        by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val accent      = brandAccent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier            = Modifier
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
                value         = name,
                onValueChange = { name = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Name") },
                singleLine    = true,
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            OutlinedTextField(
                value         = description,
                onValueChange = { description = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Description (optional)") },
                minLines      = 2,
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
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
                Text("Create and Join", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Tab dock (Chats / Channels / Groups) ──────────────────────────────────────

@Composable
private fun TabDock(
    activeTab  : MessageTab,
    onTabChange: (MessageTab) -> Unit,
    accent     : Color,
    modifier   : Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .offset(y = 20.dp)
            .padding(start = 18.dp, end = 18.dp, bottom = 0.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.16f)),
            shape = RoundedCornerShape(24.dp),
            color = if (isInDarkTheme) Color(0xB3070D16) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isInDarkTheme) 0.08f else 0.15f)),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(
                    Triple(MessageTab.Chats,    Icons.Default.ChatBubbleOutline, "Chats"),
                    Triple(MessageTab.Channels, Icons.Default.Campaign,          "Channels"),
                    Triple(MessageTab.Groups,   Icons.Default.Groups,     "Groups"),
                ).forEach { (tab, icon, label) ->
                    val active = activeTab == tab
                    Column(
                        modifier            = Modifier
                            .weight(1f)
                            .clickable { onTabChange(tab) }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                            if (active) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Icon(
                            imageVector        = icon,
                            contentDescription = label,
                            tint               = if (active) accent else Color(0xFFB4B9CC),
                            modifier           = Modifier.size(if (active) 25.dp else 24.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text       = label,
                            fontSize   = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color      = if (active) accent else Color(0xFFB4B9CC),
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NEW CHAT SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NewChatScreen(
    query        : String,
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
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onSurface,
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
            value           = query,
            onValueChange   = onQueryChange,
            modifier        = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape           = RoundedCornerShape(24.dp),
            placeholder     = { Text("Search by phone, username or name…") },
            leadingIcon     = { Icon(Icons.Default.Search, null) },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors          = OutlinedTextFieldDefaults.colors(
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
                            imageVector        = Icons.Default.PersonSearch,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f),
                            modifier           = Modifier.size(56.dp),
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
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(user.photoUrl, initialsOf(user.name.ifBlank { user.username }), 46.dp, accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = user.name.ifBlank { user.username.ifBlank { "Unknown" } },
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                user.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                user.phone.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            imageVector        = Icons.Default.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.38f),
            modifier           = Modifier.size(18.dp),
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CONVERSATION THREAD
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConversationView(
    title              : String,
    subtitle           : String,
    photoUrl           : String,
    threadAccent       : Color,
    isSupportHeader    : Boolean,
    isCommunity        : Boolean,
    channelId          : String,            // empty for direct messages
    communityOwnerUid  : String,            // empty for direct messages
    profileKind        : String,
    profileDescription : String,
    profileMemberCount : Int,
    messages           : List<Message>,
    currentUid         : String,
    canManageCommunity : Boolean,
    canSend            : Boolean,
    sending            : Boolean,
    onBack             : () -> Unit,
    onSend             : (String) -> Unit,
    onUpdateCommunity      : (String, String, String) -> Unit,
    onUploadCommunityPhoto : (android.net.Uri, (String?) -> Unit) -> Unit,
    onDeleteCommunity  : () -> Unit,
    onReact            : (Message, String) -> Unit,
    onDelete           : (Message) -> Unit,
    onModerateMember   : (String, String) -> Unit,
    onSendImage        : (android.net.Uri) -> Unit,
    onSendVideo        : (android.net.Uri) -> Unit,
    onSendVoice        : (File, Long) -> Unit,
    onMessagesShown    : () -> Unit,
) {
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()
    var showProfile   by remember { mutableStateOf(false) }
    val canvas        = threadCanvas
    val otherTyping   = false

    val showScrollFab by remember {
        derivedStateOf { messages.size > 6 && listState.firstVisibleItemIndex < messages.lastIndex - 4 }
    }

    // Auto-scroll to latest when new messages arrive — does NOT mark read by itself
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    // Mark as read ONLY when the last message is actually visible on screen
    LaunchedEffect(listState) {
        snapshotFlow {
            val info        = listState.layoutInfo
            val total       = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 1
        }.collect { atBottom -> if (atBottom) onMessagesShown() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .background(canvas),
    ) {
        // Ambient chat background — radial brand glows + faint dot grid (matches .chat-bg)
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
        if (messages.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 88.dp, bottom = 96.dp),
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
                        text       = "Start the conversation",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(0.65f),
                    )
                    Text(
                        text     = "Say hello! 👋",
                        fontSize = 16.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(
                    start  = 12.dp,
                    end    = 12.dp,
                    top    = 88.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // End-to-end encryption notice (direct messages only)
                if (!isCommunity) {
                    item(key = "enc-badge") { EncryptionBadge() }
                }
                // Dynamic date-group separators
                var lastDateLabel = ""
                messages.forEachIndexed { idx, msg ->
                    val label = chatDate(msg.timestamp)
                    if (label != lastDateLabel) {
                        lastDateLabel = label
                        item(key = "date-$label-$idx") { DateSeparator(label) }
                    }
                    item(key = msg.id) {
                        MessageBubble(
                            message     = msg,
                            currentUid  = currentUid,
                            isCommunity = isCommunity,
                            canModerate = canManageCommunity && profileKind == "group" && msg.senderId.isNotBlank() && msg.senderId != currentUid,
                            onReact     = { emoji -> onReact(msg, emoji) },
                            onDelete    = { onDelete(msg) },
                            onModerate  = { action -> onModerateMember(msg.senderId, action) },
                        )
                    }
                }
                if (otherTyping) {
                    item(key = "typing") { TypingIndicatorRow(threadAccent) }
                }
            }
        }

        // ── Fixed top header bar ─────────────────────────────────────
        ThreadTopBar(
            title           = title,
            subtitle        = subtitle,
            photoUrl        = photoUrl,
            accent          = threadAccent,
            isSupportHeader = isSupportHeader,
            isTyping        = otherTyping,
            showCallActions = !isCommunity,
            onBack          = onBack,
            onProfileClick  = { showProfile = true },
            modifier        = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )

        // ── Game Pot banner (channels only) ───────────────────────────
        if (isCommunity && profileKind == "channel" && channelId.isNotBlank()) {
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

        // ── Scroll-to-latest FAB ─────────────────────────────────────
        AnimatedVisibility(
            visible  = showScrollFab,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 98.dp),
            enter    = fadeIn() + slideInVertically { it / 2 },
            exit     = fadeOut(),
        ) {
            SmallFloatingActionButton(
                onClick        = {
                    if (messages.isNotEmpty())
                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor   = threadAccent,
                shape          = CircleShape,
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Scroll to latest", modifier = Modifier.size(20.dp))
            }
        }

        // ── Input ────────────────────────────────────────────────────
        if (canSend) {
            MediaInputBar(
                sending     = sending,
                accent      = threadAccent,
                peerName    = title,
                onSendText  = onSend,
                onSendImage = onSendImage,
                onSendVideo = onSendVideo,
                onSendVoice = onSendVoice,
                modifier    = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            ReadOnlyBar(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    // Full-screen profile page (replaces the old bottom sheet)
    if (showProfile) {
        FullProfileScreen(
            title       = title,
            subtitle    = subtitle,
            photoUrl    = photoUrl,
            kind        = profileKind,
            description = profileDescription,
            memberCount = profileMemberCount,
            accent      = threadAccent,
            canManageCommunity     = canManageCommunity,
            onUpdateCommunity      = onUpdateCommunity,
            onUploadCommunityPhoto = onUploadCommunityPhoto,
            onDeleteCommunity      = {
                onDeleteCommunity()
                showProfile = false
            },
            onDismiss   = { showProfile = false },
        )
    }
}

// ── Thread top bar ─────────────────────────────────────────────────────────────

@Composable
private fun ThreadTopBar(
    title          : String,
    subtitle       : String,
    photoUrl       : String,
    accent         : Color,
    isSupportHeader: Boolean,
    isTyping       : Boolean,
    showCallActions: Boolean,
    onBack         : () -> Unit,
    onProfileClick : () -> Unit,
    modifier       : Modifier = Modifier,
) {
    val isDark   = isInDarkTheme
    val headerBg = if (isDark) Color(0xD90A0A0A) else Color(0xF5FFFFFF)
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
            modifier          = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
                        modifier         = Modifier.fillMaxSize().clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.SupportAgent, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                } else {
                    ChatAvatar(photoUrl, initialsOf(title), 42.dp, accent)
                }
                // Online dot — pulsing green
            }

            Spacer(Modifier.width(10.dp))

            // Name & status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onProfileClick),
            ) {
                Text(
                    text       = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color      = iconTint,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isTyping) {
                        TypingDots(dotColor = Color(0xFF18C964), size = 4.dp)
                        Spacer(Modifier.width(3.dp))
                        Text("typing…", fontSize = 15.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (showCallActions) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Call, "Call", tint = subTint, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Videocam, "Video call", tint = subTint, modifier = Modifier.size(22.dp))
                }
            }
            // Overflow menu
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = subTint, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded         = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text        = { Text("View profile") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        onClick     = { menuOpen = false; onProfileClick() },
                    )
                    DropdownMenuItem(
                        text        = { Text("Mute notifications") },
                        leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                        onClick     = { menuOpen = false },
                    )
                    DropdownMenuItem(
                        text        = { Text("Search in chat") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        onClick     = { menuOpen = false },
                    )
                    DropdownMenuItem(
                        text        = { Text("Block", color = DeleteRed) },
                        leadingIcon = { Icon(Icons.Default.Block, null, tint = DeleteRed) },
                        onClick     = { menuOpen = false },
                    )
                }
            }
        }
    }
}

// ── Read-only channel bar ─────────────────────────────────────────────────────

@Composable
private fun ReadOnlyBar(modifier: Modifier = Modifier) {
    Surface(
        modifier       = modifier.fillMaxWidth().navigationBarsPadding(),
        color          = MaterialTheme.colorScheme.surface,
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

// ══════════════════════════════════════════════════════════════════════════════
// INPUT BAR
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MediaInputBar(
    sending    : Boolean,
    accent     : Color,
    peerName   : String,
    onSendText : (String) -> Unit,
    onSendImage: (android.net.Uri) -> Unit,
    onSendVideo: (android.net.Uri) -> Unit,
    onSendVoice: (File, Long) -> Unit,
    modifier   : Modifier = Modifier,
) {
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    var draft           by remember { mutableStateOf("") }
    var isRecording     by remember { mutableStateOf(false) }
    var recSeconds      by remember { mutableIntStateOf(0) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showEmojiBar    by remember { mutableStateOf(false) }
    var showTransfer    by remember { mutableStateOf(false) }
    val voiceRecorder   = remember { VoiceRecorderState(context) }
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSendImage(it) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSendVideo(it) }
    }
    // Camera capture → JPEG temp file → send as image
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val f = File.createTempFile("cam_", ".jpg", context.cacheDir)
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            onSendImage(android.net.Uri.fromFile(f))
        }
    }
    // Any file → send filename as a text message (no file message type in backend yet)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSendText("📎 ${queryFileName(context, it)}") }
    }
    // Contact picker → send name + phone as a text message
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            val (name, phone) = readContact(context, it)
            if (name.isNotBlank() || phone.isNotBlank()) {
                onSendText("👤 ${name.ifBlank { "Contact" }}" + if (phone.isNotBlank()) "\n📞 $phone" else "")
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

    if (showAttachSheet) {
        AttachmentSheet(
            onDismiss = { showAttachSheet = false },
            onCamera  = {
                showAttachSheet = false
                if (cameraPermission.status.isGranted) cameraLauncher.launch(null)
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
                if (locationPermission.status.isGranted) shareLocation(context, onSendText)
                else locationPermission.launchPermissionRequest()
            },
            onWallet   = {
                showAttachSheet = false
                showTransfer = true
            },
        )
    }

    if (showTransfer) {
        TransferDialog(
            peerName   = peerName,
            accent     = accent,
            onDismiss  = { showTransfer = false },
            onSendText = onSendText,
        )
    }

    val surfaceBg = MaterialTheme.colorScheme.surface

    Surface(
        modifier        = modifier.fillMaxWidth().navigationBarsPadding(),
        color           = surfaceBg,
        tonalElevation  = 6.dp,
        shadowElevation = 8.dp,
    ) {
        if (isRecording) {
            // ── Recording state ──────────────────────────────────────
            val pulseTransition = rememberInfiniteTransition(label = "recPulse")
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = 0.30f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label         = "recAlpha",
            )
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cancel
                Box(
                    modifier         = Modifier
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
                        text     = "← Slide to cancel",
                        fontSize = 14.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Send voice
                Box(
                    modifier         = Modifier
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
          // ── Normal state ─────────────────────────────────────────
          Column(Modifier.fillMaxWidth()) {
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
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Attach button
                Box(
                    modifier         = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showAttachSheet = true },
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
                    shape          = RoundedCornerShape(26.dp),
                    color          = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    val selectedHabeshaEmoji = remember(draft) {
                        HabeshaExactPack.firstOrNull { it.token == draft.trim() }
                    }
                    Row(
                        modifier          = Modifier.padding(
                            start  = 16.dp,
                            end    = 4.dp,
                            top    = 10.dp,
                            bottom = 10.dp,
                        ),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        BasicTextField(
                            value         = draft,
                            onValueChange = { draft = it },
                            textStyle     = TextStyle(
                                color      = if (selectedHabeshaEmoji == null) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                            ),
                            modifier      = Modifier.weight(1f),
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
                                            text     = "Message…",
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
                            onClick  = { showEmojiBar = !showEmojiBar },
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
                        modifier         = Modifier
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
                                onSendText(text)
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
                    // Hold-to-record mic
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(BubbleGradStart, BubbleGradEnd)))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    if (audioPermission.status.isGranted) {
                                        voiceRecorder.start(); isRecording = true
                                        var totalX = 0f
                                        while (true) {
                                            val evt = awaitPointerEvent(PointerEventPass.Main)
                                            val chg = evt.changes.firstOrNull() ?: break
                                            totalX += (chg.position - chg.previousPosition).x
                                            if (!chg.pressed) break
                                            chg.consume()
                                        }
                                        isRecording = false
                                        if (totalX < -120f) voiceRecorder.cancel()
                                        else voiceRecorder.stop()?.let { (f, ms) ->
                                            scope.launch { onSendVoice(f, ms) }
                                        }
                                    } else audioPermission.launchPermissionRequest()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Mic, "Hold to record",
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

// ── Emoji quick bar (matches mockup .emoji-bar) ────────────────────────────────

@Composable
private fun EmojiBar(onPick: (String) -> Unit) {
    val accent = brandAccent
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // ── Habesha Pack (AdisChat exclusive) ─────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🇪🇹", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("HABESHA PACK", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(8.dp))
        val context = LocalContext.current

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(HabeshaExactPack, key = { it.code }) { e ->
                // Resolve custom art by name; 0 means the file isn't in res/drawable yet → use glyph.
                val resId = remember(e.drawableName) {
                    e.drawableName?.let {
                        context.resources.getIdentifier(it, "drawable", context.packageName)
                    } ?: 0
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(e.token) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = e.imageUrl,
                        contentDescription = e.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(e.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        Spacer(Modifier.height(8.dp))

        // ── Standard quick emojis ─────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("😊", "❤️", "🔥", "👍", "😂", "🎉", "🚀", "💡", "✨", "💪", "🙏", "😮").forEach { emoji ->
                Box(
                    modifier         = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(emoji) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 25.sp)
                }
            }
        }
    }
}

// ── Habesha emoji pack (AdisChat exclusive) ───────────────────────────────────
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

private const val HabeshaEmojiBaseUrl = "https://adischat-api.adischat.workers.dev/media/emoji/habesha"

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
    HabeshaEmoji("Smile",    "hab_face_smile",    "🙂"),
    HabeshaEmoji("Laugh",    "hab_face_laugh",    "😂"),
    HabeshaEmoji("Love",     "hab_face_love",     "😍"),
    HabeshaEmoji("Thinking", "hab_face_thinking", "🤔"),
    HabeshaEmoji("Wink",     "hab_face_wink",     "😉"),
    HabeshaEmoji("Cry",      "hab_face_cry",      "😢"),
    HabeshaEmoji("Angry",    "hab_face_angry",    "😠"),
    HabeshaEmoji("Cool",     "hab_face_cool",     "😎"),
    HabeshaEmoji("Blessing", "hab_face_blessing", "🙏"),
    HabeshaEmoji("Respect",  "hab_face_respect",  "🫡"),
    HabeshaEmoji("Ayzoh",    "hab_expr_ayzoh",    "🤗"),
    HabeshaEmoji("Gobez",    "hab_expr_gobez",    "💪"),
    HabeshaEmoji("Eshi",     "hab_expr_eshi",     "👍"),
)

private val HabeshaExactPack = listOf(
    HabeshaEmoji("Taxi",           "hab_addis_taxi",      "Taxi"),
    HabeshaEmoji("Bajaj",          "hab_addis_bajaj",     "Bajaj"),
    HabeshaEmoji("Doro Wat",       "hab_food_doro_wat",   "Doro"),
    HabeshaEmoji("Ethiopia Heart", "hab_expr_flag_heart", "Heart"),
    HabeshaEmoji("Love",           "hab_face_love",       "Love"),
    HabeshaEmoji("Cool",           "hab_face_cool",       "Cool"),
)

private val HabeshaPack = listOf(
    HabeshaEmoji("Smile",    "😀"),
    HabeshaEmoji("Laugh",    "😂"),
    HabeshaEmoji("Love",     "😍"),
    HabeshaEmoji("Thinking", "🤔"),
    HabeshaEmoji("Ayzoh",    "🤗"),
    HabeshaEmoji("Gobez",    "💪"),
    HabeshaEmoji("Eshi",     "👍", drawableName = "hab_expr_eshi"),
    HabeshaEmoji("Injera",   "🫓"),
    HabeshaEmoji("Doro Wat", "🍲", drawableName = "hab_food_doro_wat"),
    HabeshaEmoji("Jebena",   "☕"),
    HabeshaEmoji("Kemis",    "👗"),
    HabeshaEmoji("Taxi",     "🚕", drawableName = "hab_addis_taxi"),
    HabeshaEmoji("Eskista",  "💃", drawableName = "hab_culture_eskista"),
    HabeshaEmoji("Adis Lion","🦁"),
)

// ── Attachment grid bottom sheet (matches mockup .attachment-grid) ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss : () -> Unit,
    onCamera  : () -> Unit,
    onGallery : () -> Unit,
    onVideo   : () -> Unit,
    onVoice   : () -> Unit,
    onFile    : () -> Unit,
    onContact : () -> Unit,
    onLocation: () -> Unit,
    onWallet  : () -> Unit,
) {
    data class AttachAction(
        val label   : String,
        val icon    : androidx.compose.ui.graphics.vector.ImageVector,
        val gradient: List<Color>,
        val onClick : () -> Unit,
    )

    val actions = listOf(
        AttachAction("Camera",   Icons.Default.PhotoCamera,    listOf(Color(0xFF667EEA), Color(0xFF764BA2)), onCamera),
        AttachAction("Gallery",  Icons.Default.Image,          listOf(Color(0xFFF093FB), Color(0xFFF5576C)), onGallery),
        AttachAction("Video",    Icons.Default.Videocam,       listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)), onVideo),
        AttachAction("Voice",    Icons.Default.Mic,            listOf(Color(0xFF43E97B), Color(0xFF38F9D7)), onVoice),
        AttachAction("File",     Icons.Default.InsertDriveFile, listOf(Color(0xFFFA709A), Color(0xFFFEE140)), onFile),
        AttachAction("Contact",  Icons.Default.Person,         listOf(Color(0xFF30CFD0), Color(0xFF330867)), onContact),
        AttachAction("Location", Icons.Default.LocationOn,     listOf(Color(0xFFFF9A9E), Color(0xFFF6416C)), onLocation),
        AttachAction("Transfer", Icons.Default.AccountBalanceWallet, listOf(Color(0xFF15B85A), Color(0xFF18C964)), onWallet),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text       = "Share",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 16.dp),
            )
            // 4 per row (8 items → 2 rows)
            actions.chunked(4).forEach { rowItems ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    rowItems.forEach { action ->
                        Column(
                            modifier            = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = action.onClick)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(action.gradient)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    action.icon, action.label,
                                    tint     = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Text(
                                text     = action.label,
                                fontSize = 14.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WALLET TRANSFER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Send money to the chat peer straight from the AdisPay wallet.
 * Uses the existing [WalletRepository.send] backend (recipient + amount + PIN);
 * posts a confirmation message into the thread on success.
 */
@Composable
private fun TransferDialog(
    peerName  : String,
    accent    : Color,
    onDismiss : () -> Unit,
    onSendText: (String) -> Unit,
) {
    val scope     = rememberCoroutineScope()
    val walletRepo = remember { WalletRepository() }

    var recipient by remember { mutableStateOf(peerName) }
    var amount    by remember { mutableStateOf("") }
    var pin       by remember { mutableStateOf("") }
    var note      by remember { mutableStateOf("") }
    var sending   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val canSend = !sending && recipient.isNotBlank() && amountValue > 0.0 && pin.length >= 4

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon  = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = accent) },
        title = { Text("Send money", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient (username or phone)") },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> amount = new.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount") },
                    singleLine = true,
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { new -> pin = new.filter { it.isDigit() }.take(6) },
                    label = { Text("Wallet PIN") },
                    singleLine = true,
                    enabled = !sending,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSend,
                onClick = {
                    sending = true
                    error   = null
                    scope.launch {
                        walletRepo.send(recipient.trim(), amountValue, pin)
                            .onSuccess { state ->
                                val cur = state.currency.ifBlank { "" }
                                val pretty = if (amountValue % 1.0 == 0.0) amountValue.toLong().toString()
                                             else amountValue.toString()
                                val noteSuffix = if (note.isNotBlank()) " — ${note.trim()}" else ""
                                onSendText("💸 Sent $cur$pretty to ${recipient.trim()}$noteSuffix")
                                sending = false
                                onDismiss()
                            }
                            .onFailure {
                                error   = CloudflareClient.friendlyErrorMessage(it, "Transfer failed")
                                sending = false
                            }
                    }
                },
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                } else {
                    Text("Send", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// MESSAGE BUBBLES
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message     : Message,
    currentUid  : String,
    isCommunity : Boolean,
    canModerate : Boolean,
    onReact     : (String) -> Unit,
    onDelete    : () -> Unit,
    onModerate  : (String) -> Unit,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
        return
    }

    val isMine            = message.senderId == currentUid
    var showReactions     by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text("Delete message") },
            text             = { Text("This will remove the message from this chat.") },
            confirmButton    = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete") }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Capture theme values in composable scope before lambdas
    val recvColor  = bubbleReceivedColor
    val recvBorder = bubbleReceivedBorder
    val isHabeshaEmojiMessage = remember(message.text) {
        HabeshaExactPack.any { it.token == message.text.trim() }
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
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            // Reaction action rail — animated pop-in on long-press
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
                    onSuspend   = { showReactions = false; onModerate("suspend") },
                    onBlock     = { showReactions = false; onModerate("block") },
                )
            }

            // Bubble shape: matches HTML chat.html corner radii
            // received: 4dp top-start tail; sent: 4dp top-end tail
            val bubbleShape = RoundedCornerShape(
                topStart    = if (isMine) 22.dp else 4.dp,
                topEnd      = if (isMine) 4.dp  else 22.dp,
                bottomStart = 22.dp,
                bottomEnd   = 22.dp,
            )

            Row(
                modifier              = Modifier.fillMaxWidth(0.82f),
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            ) {
                Column(
                    modifier = (if (isStandaloneContent) {
                        Modifier
                    } else {
                        Modifier
                            .shadow(
                                elevation    = 2.dp,
                                shape        = bubbleShape,
                                ambientColor = Color.Black.copy(alpha = 0.05f),
                                spotColor    = Color.Black.copy(alpha = 0.07f),
                            )
                            .clip(bubbleShape)
                            .background(
                                if (isMine)
                                    Brush.linearGradient(listOf(BubbleGradStart, BubbleGradEnd))
                                else
                                    Brush.linearGradient(listOf(recvColor, recvColor)),
                            )
                            .border(
                                width  = 0.5.dp,
                                color  = if (isMine) Color.Transparent else recvBorder,
                                shape  = bubbleShape,
                            )
                    })
                        .combinedClickable(
                            onClick     = {},
                            onLongClick = { showReactions = !showReactions },
                        )
                        .animateContentSize()
                        .padding(
                            horizontal = if (isStandaloneContent) 2.dp else 14.dp,
                            vertical = if (isStandaloneContent) 2.dp else 10.dp,
                        ),
                ) {
                    // Sender name (community threads or support admin)
                    if (!isMine) {
                        val senderLabel = when {
                            message.senderRole == "admin" -> "AdisChat Support"
                            isCommunity && message.senderName.isNotBlank() -> message.senderName
                            else -> null
                        }
                        if (senderLabel != null) {
                            Text(
                                text       = senderLabel,
                                color      = if (message.senderRole == "admin") Primary
                                             else accentFor(message.senderId),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                    when (message.type) {
                        "deleted" -> Text(
                            text      = "Message deleted",
                            color     = if (isMine) Color.White.copy(0.60f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 17.sp,
                            fontStyle = FontStyle.Italic,
                        )
                        "image"   -> ImageBubbleContent(message, isMine)
                        "video"   -> VideoBubbleContent(message, isMine)
                        "voice"   -> VoiceBubbleContent(message, isMine)
                        else      -> TextBubbleContent(message, isMine)
                    }
                    ReactionSummary(message.reactions)
                }
            }
        }
    }
}

@Composable
private fun ReactionSummary(reactions: Map<String, String>) {
    if (reactions.isEmpty()) return
    Spacer(Modifier.height(5.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
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

@Composable
private fun ColumnScope.TextBubbleContent(m: Message, isMine: Boolean) {
    val habeshaEmoji = remember(m.text) { HabeshaExactPack.firstOrNull { it.token == m.text.trim() } }
    if (habeshaEmoji != null) {
        AsyncImage(
            model = habeshaEmoji.imageUrl,
            contentDescription = habeshaEmoji.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
    } else {
        val emojiOnly = remember(m.text) { isStandaloneEmojiText(m.text) }
        Text(
            text       = m.text,
            color      = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = if (emojiOnly) 44.sp else 18.sp,
            lineHeight = if (emojiOnly) 48.sp else 24.sp,
        )
    }
    Spacer(Modifier.height(4.dp))
    MessageMeta(chatTime(m.timestamp), isMine, modifier = Modifier.align(Alignment.End))
}

@Composable
private fun ColumnScope.ImageBubbleContent(m: Message, isMine: Boolean) {
    var fullscreen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        AsyncImage(
            model              = m.mediaUrl,
            contentDescription = "Image",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { fullscreen = true },
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
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            MessageMeta(
                time     = chatTime(m.timestamp),
                isMine   = true,          // white text on dark overlay
                modifier = Modifier.align(Alignment.CenterEnd),
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
private fun ColumnScope.VideoBubbleContent(m: Message, isMine: Boolean) {
    var showPlayer by remember { mutableStateOf(false) }
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.80f))
            .clickable { showPlayer = true },
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
            MessageMeta(chatTime(m.timestamp), isMine = true)
        }
    }
    if (m.text.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(m.text, color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
    }
    if (showPlayer) VideoPlayerDialog(m.mediaUrl) { showPlayer = false }
}

@Composable
private fun ColumnScope.VoiceBubbleContent(m: Message, isMine: Boolean) {
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
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(trackColor)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play / pause button
        Box(
            modifier         = Modifier
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
                    imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint               = iconTint,
                    modifier           = Modifier.size(22.dp),
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
            MessageMeta(chatTime(m.timestamp), isMine)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SHARED COMPOSABLES
// ══════════════════════════════════════════════════════════════════════════════

// ── Full-screen profile page ───────────────────────────────────────────────────
// Replaces the old ModalBottomSheet; shows a proper hero layout with zoom support.

@Composable
private fun FullProfileScreen(
    title      : String,
    subtitle   : String,
    photoUrl   : String,
    kind       : String,
    description: String,
    memberCount: Int,
    accent     : Color,
    canManageCommunity    : Boolean = false,
    onUpdateCommunity     : (String, String, String) -> Unit = { _, _, _ -> },
    onUploadCommunityPhoto: (android.net.Uri, (String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteCommunity     : () -> Unit = {},
    onDismiss  : () -> Unit,
) {
    var showPhotoZoom by remember { mutableStateOf(false) }
    var showEdit      by remember { mutableStateOf(false) }
    var showDelete    by remember { mutableStateOf(false) }
    var showForward   by remember { mutableStateOf(false) }
    var subscribed    by remember { mutableStateOf(true) }
    val ctx           = LocalContext.current

    BackHandler(onBack = onDismiss)

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
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ── Scrollable content ──────────────────────────────────────
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 56.dp),
            ) {
                // Hero banner / avatar
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    ) {
                        if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model              = photoUrl,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clickable { showPhotoZoom = true },
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(accent.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text       = initialsOf(title),
                                    fontSize = 83.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = accent,
                                )
                            }
                        }
                        // Gradient fade into background colour
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.45f to Color.Transparent,
                                            1.00f to MaterialTheme.colorScheme.background,
                                        ),
                                    ),
                                ),
                        )
                        // Zoom hint button (only when there's a photo)
                        if (photoUrl.isNotBlank()) {
                            Surface(
                                modifier         = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp),
                                shape            = CircleShape,
                                color            = Color.Black.copy(alpha = 0.46f),
                            ) {
                                IconButton(onClick = { showPhotoZoom = true }) {
                                    Icon(
                                        Icons.Default.ZoomIn, "View full photo",
                                        tint     = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Name + badge
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text       = title,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = MaterialTheme.colorScheme.onBackground,
                            textAlign  = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text       = when (kind) {
                                    "channel" -> "Channel · $memberCount members"
                                    "group"   -> "Group · $memberCount members"
                                    else      -> subtitle.ifBlank { "AdisChat User" }
                                },
                                fontSize = 16.sp,
                                color      = accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical   = 6.dp,
                                ),
                            )
                        }
                    }
                }

                // Description / bio
                if (description.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text      = description,
                            fontSize = 17.sp,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                        )
                    }
                }

                // Channel actions: Subscribe / Share / Invite / QR
                if (kind == "channel") {
                    item {
                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick  = { subscribed = !subscribed },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else accent,
                                contentColor   = if (subscribed) MaterialTheme.colorScheme.onSurface else Color.White,
                            ),
                        ) {
                            Icon(if (subscribed) Icons.Default.Check else Icons.Default.PersonAdd,
                                null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (subscribed) "Subscribed" else "Subscribe", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProfileActionBtn(Icons.Default.Share, "Share", Modifier.weight(1f), accent,
                                onClick = { showForward = true })
                            ProfileActionBtn(Icons.Default.PersonAddAlt, "Invite", Modifier.weight(1f), accent,
                                onClick = { showForward = true })
                            ProfileActionBtn(Icons.Default.QrCode, "QR", Modifier.weight(1f), accent,
                                onClick = { Toast.makeText(ctx, "QR code coming soon", Toast.LENGTH_SHORT).show() })
                        }
                    }
                }

                // Action buttons
                item {
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileActionBtn(
                            Icons.Default.Notifications, "Mute",
                            Modifier.weight(1f), accent,
                        )
                        ProfileActionBtn(
                            Icons.Default.Search, "Search",
                            Modifier.weight(1f), accent,
                        )
                        ProfileActionBtn(
                            Icons.Default.Block, "Block",
                            Modifier.weight(1f), DeleteRed,
                        )
                    }
                }

                if (canManageCommunity && (kind == "channel" || kind == "group")) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { showEdit = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit name, info and photo", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showDelete = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, DeleteRed.copy(alpha = 0.50f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeleteRed),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete ${if (kind == "channel") "channel" else "group"}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Stats row (communities only)
                if (kind == "channel" || kind == "group") {
                    item {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(
                            modifier  = Modifier.padding(horizontal = 24.dp),
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text       = memberCount.toString(),
                                    fontSize = 29.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text     = "Members",
                                    fontSize = 15.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Floating back button (always on top) ────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Surface(
                    shape           = CircleShape,
                    color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    tonalElevation  = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    // In-app forward (share / invite the channel)
    if (showForward) {
        ForwardSheet(
            messageText = "Check out the $title channel on AdisChat",
            onDismiss   = { showForward = false },
        )
    }

    // Fullscreen zoomable photo viewer
    if (showPhotoZoom && photoUrl.isNotBlank()) {
        ZoomableImageDialog(url = photoUrl, onDismiss = { showPhotoZoom = false })
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
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Pinch-to-zoom image dialog ─────────────────────────────────────────────────

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
    var name         by remember(initialName)        { mutableStateOf(initialName) }
    var info         by remember(initialDescription) { mutableStateOf(initialDescription) }
    var photo        by remember(initialPhotoUrl)    { mutableStateOf(initialPhotoUrl) }
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

                // ── Photo picker ─────────────────────────────────────────────
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
                                model              = photo,
                                contentDescription = "Photo",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(
                                imageVector        = Icons.Default.CameraAlt,
                                contentDescription = "Pick photo",
                                tint               = accent,
                                modifier           = Modifier.size(32.dp),
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
                                    imageVector        = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint               = Color.White,
                                    modifier           = Modifier.size(14.dp),
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

                // ── Name ─────────────────────────────────────────────────────
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("Name") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent),
                )

                // ── Info / description ────────────────────────────────────────
                OutlinedTextField(
                    value         = info,
                    onValueChange = { info = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("Info") },
                    minLines      = 3,
                    shape         = RoundedCornerShape(14.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.trim().length >= 2 && !isUploading,
                onClick = { onSave(name.trim(), info.trim(), photo) },
                colors  = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
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
                model              = url,
                contentDescription = "Profile photo",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
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
                modifier         = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                shape            = CircleShape,
                color            = Color.Black.copy(alpha = 0.55f),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            // Zoom level indicator
            if (scale > 1.05f) {
                Surface(
                    modifier         = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    shape            = RoundedCornerShape(999.dp),
                    color            = Color.Black.copy(alpha = 0.50f),
                ) {
                    Text(
                        text     = "Pinch to zoom · Double-tap to reset",
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
        modifier            = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
    // Base canvas colour
    val base = if (isDark) Color(0xFF06130D) else Color(0xFFEAF7EF)
    val dot  = if (isDark) Color(0xFF9EF2BC).copy(alpha = 0.035f) else Color(0xFF0F7A3A).copy(alpha = 0.055f)
    androidx.compose.foundation.Canvas(modifier = modifier.background(base)) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF0B2617).copy(alpha = if (isDark) 0.92f else 0.24f),
                    Color(0xFF062015).copy(alpha = if (isDark) 0.74f else 0.12f),
                    Color.Transparent,
                ),
            ),
        )
        // Soft brand-green radial glows.
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(BrandGreen.copy(alpha = if (isDark) 0.18f else 0.16f), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.42f),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * 0.18f, h * 0.42f),
        )
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(BrandGreenBright.copy(alpha = if (isDark) 0.10f else 0.12f), Color.Transparent),
                center = Offset(w * 0.85f, h * 0.16f),
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.85f, h * 0.16f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF123C29).copy(alpha = if (isDark) 0.42f else 0.16f), Color.Transparent),
                center = Offset(w * 0.72f, h * 0.78f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.72f, h * 0.78f),
        )
        // Faint dot grid (50dp spacing)
        val step = 50.dp.toPx()
        val r    = 1.dp.toPx()
        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                drawCircle(color = dot, radius = r, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun EncryptionBadge() {
    val isDark = isInDarkTheme
    val tint   = if (isDark) Color(0xFF606068) else Color(0xFF9CA3AF)
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
    val isDark = isInDarkTheme
    Box(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color      = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280),
            modifier   = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFE5E7EB))
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
        label         = "typingPhase",
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
    onSuspend  : () -> Unit,
    onBlock    : () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Emoji row
        Surface(
            shape           = RoundedCornerShape(999.dp),
            color           = MaterialTheme.colorScheme.surface,
            tonalElevation  = 12.dp,
            shadowElevation = 8.dp,
            modifier        = Modifier.border(
                0.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                RoundedCornerShape(999.dp),
            ),
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                listOf("❤️", "🔥", "👍", "😂", "🙏", "😮").forEach { emoji ->
                    Box(
                        modifier         = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = isCommunity) { onReact(emoji) }
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text     = emoji,
                            fontSize = 29.sp,
                            color    = if (isCommunity) Color.Unspecified
                                       else LocalContentColor.current.copy(alpha = 0.40f),
                        )
                    }
                }
                if (isMine) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier         = Modifier
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
                        Text("Suspend", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onBlock) {
                        Text("Block", color = DeleteRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Hint label for direct-message threads
        if (!isCommunity) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text     = "Reactions available in channels & groups",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MessageMeta(time: String, isMine: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text     = time,
            fontSize = 14.sp,
            color    = if (isMine) Color.White.copy(0.75f) else Color(0xFF606068),
        )
        if (isMine) {
            // Double-tick, green = read (matches HTML .check-marks.read → color: #58e59a)
            Icon(
                Icons.Default.Done, null,
                tint     = Color.White.copy(0.72f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun VoiceWaveform(progress: Float, activeColor: Color) {
    Row(
        modifier              = Modifier.width(96.dp),
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
        modifier         = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.13f))
            .border(1.5.dp, accent.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model              = photoUrl,
                contentDescription = "Avatar",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text       = initials,
                color      = accent,
                fontWeight = FontWeight.Bold,
                fontSize   = (size.value / 2.4f).sp,
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, accent: Color) {
    Box(
        modifier         = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(accent)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = if (count > 99) "99+" else count.toString(),
            color      = Color.White,
            fontSize = 14.sp,
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
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model              = url,
                contentDescription = "Full image",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxWidth().padding(16.dp),
            )
            IconButton(onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
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

// ── Helpers ───────────────────────────────────────────────────────────────────

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

/** Short time label for a chat row (e.g. "3:45 PM", "Mon", "Jan 5"). */
private fun chatTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = epochMs }
    return when {
        now.get(java.util.Calendar.DATE)  == then.get(java.util.Calendar.DATE) &&
        now.get(java.util.Calendar.MONTH) == then.get(java.util.Calendar.MONTH) &&
        now.get(java.util.Calendar.YEAR)  == then.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
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

private fun channelPreview(room: CommunityRoom): String {
    val source = room.lastPostPreview
        .ifBlank { room.lastMessage }
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
        listOf("job", "career", "vacancy", "work").any { it in text } -> "💼" to "Jobs"
        listOf("game", "gaming", "pot", "play").any { it in text } -> "🎮" to "Gaming"
        listOf("market", "shop", "sale", "deal", "store").any { it in text } -> "🛒" to "Marketplace"
        listOf("music", "song", "artist").any { it in text } -> "🎵" to "Music"
        listOf("sport", "football", "soccer").any { it in text } -> "⚽" to "Sports"
        listOf("news", "media", "daily").any { it in text } -> "📰" to "News"
        else -> "📢" to "Announcement"
    }
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

/** Format a voice note duration: "0:04", "1:23". */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

// ── Attachment helpers ────────────────────────────────────────────────────────

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

/** Get the last known location and send it as a Google Maps link via [onSendText]. */
@android.annotation.SuppressLint("MissingPermission")
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
            android.widget.Toast.makeText(context, "No location fix yet — try again", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val lat = best!!.latitude
        val lng = best!!.longitude
        onSendText("📍 My location\nhttps://maps.google.com/?q=$lat,$lng")
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Couldn't get location", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ── Voice recorder state ──────────────────────────────────────────────────────

class VoiceRecorderState(private val context: android.content.Context) {

    private var recorder: android.media.MediaRecorder? = null
    private var outputFile: java.io.File? = null

    var isRecording by androidx.compose.runtime.mutableStateOf(false)
        private set

    var startTimeMs = 0L
        private set

    fun start() {
        if (isRecording) return
        val file = java.io.File.createTempFile("voice_", ".m4a", context.cacheDir)
        outputFile  = file
        startTimeMs = System.currentTimeMillis()
        recorder = android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        isRecording = true
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
