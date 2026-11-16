package com.adischat.app.ui.channel

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.adischat.app.data.model.AppUser
import com.adischat.app.ui.chat.ForwardScreen
import com.adischat.app.data.model.ChannelPost
import com.adischat.app.data.model.AdisLinks
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.common.AdisVerifiedBadge
import com.adischat.app.ui.common.LinkifiedText
import com.adischat.app.ui.gamepot.GamePotBannerHost
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import com.adischat.app.ui.localization.tr

// - Premium design tokens (mirrors channel.html) -
private val ChCanvas    = Color(0xFF020604)
private val ChDeep      = Color(0xFF07130D)
private val ChSurface   = Color(0xFF0D1710)
private val ChTertiary  = Color(0xFF122118)
private val ChGreen     = Color(0xFF18C964)
private val ChEmerald   = Color(0xFF34D399)
private val ChText      = Color(0xFFF7FFF9)
private val ChTextSec   = Color(0xFFB4C0B8)
private val ChTextMuted = Color(0xFF7F8A84)
private val ChBorder    = Color(0x1FFFFFFF)
private val ChGlass     = Color(0xE60B1510)
private val ChGlow      = Color(0x2618C964)
private val ChRed       = Color(0xFFE5484D)
private val ChBlue      = Color(0xFF1DA1F2)   // verified blue badge

// - Screen entry point -

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelFeedScreen(
    channelId:      String,
    channelName:      String,
    channelPhoto:      String  = "",
    channelDescription:    String  = "",
    subscriberCount:       Int     = 0,
    subscribed:      Boolean = false,
    isAdmin:      Boolean = false,
    myUid:      String  = "",
    onBack:      () -> Unit = {},
    onJoinCommunity:       () -> Unit = {},
    onLeaveCommunity:      () -> Unit = {},
    onUpdateCommunity:     (name: String, desc: String, photoUrl: String) -> Unit = { _, _, _ -> },
    onUploadCommunityPhoto:(Uri, (String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteCommunity:     () -> Unit = {},
    onOpenUrl:      (String) -> Unit = {},
    vm: ChannelViewModel = viewModel(key = channelId),
) {
    LaunchedEffect(channelId) { vm.init(channelId) }

    val state      = vm.state.collectAsStateWithLifecycle().value
    val listState    = rememberLazyListState()
    val snackbarHost  = remember { SnackbarHostState() }
    val context      = LocalContext.current
    val screenScope  = rememberCoroutineScope()

    // Blue badge shows only when the channel's verification is approved.
    var channelVerificationStatus by remember(channelId) { mutableStateOf("none") }
    val channelVerified = channelVerificationStatus == "approved"
    LaunchedEffect(channelId) {
        com.adischat.app.data.repository.VerificationRepository()
        .getChannelVerification(channelId)
        .getOrNull()?.let { channelVerificationStatus = it.status }
    }

    // Snackbar for errors / success
    LaunchedEffect(state.error, state.actionError, state.successMessage) {
        val msg = state.successMessage ?: state.actionError ?: state.error
        if (msg != null) {
        snackbarHost.showSnackbar(msg)
        vm.dismissError()
        vm.dismissSuccessMessage()
        }
    }

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        totalItems > 0 && lastVisible >= totalItems - 3 && state.hasMore && !state.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    // Glass header appears after scrolling past the cover
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 }
    }
    val headerBg by animateColorAsState(if (scrolled) ChGlass else Color.Transparent, label = "headerBg")

    var editingPost      by remember { mutableStateOf<ChannelPost?>(null) }
    var showOverflow     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBadgeRequest by remember { mutableStateOf(false) }
    var showTip      by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }
    var searchActive     by remember { mutableStateOf(false) }
    var query      by remember { mutableStateOf("") }
    var forwardText      by remember { mutableStateOf<String?>(null) }
    var liveGamePinned   by remember { mutableStateOf(false) }
    var isSubscribed by remember(channelId, subscribed) { mutableStateOf(subscribed) }

    val pinnedPost   = state.posts.firstOrNull { it.pinned }
    val orderedPosts = remember(state.posts) {
        state.posts.sortedWith(
        compareByDescending<ChannelPost> { it.pinned }
        .thenByDescending { it.createdAt }
        )
    }
    val feedPosts    = if (query.isBlank()) orderedPosts
        else orderedPosts.filter {
        it.body.contains(query, true) || it.title.contains(query, true)
        }

    var restoredScroll by remember(channelId) { mutableStateOf(false) }

    fun saveChannelScrollPosition() {
        if (query.isNotBlank() || listState.layoutInfo.totalItemsCount == 0) return
        context.getSharedPreferences("adis_channel_scroll_positions", android.content.Context.MODE_PRIVATE)
        .edit()
        .putInt("$channelId:index", listState.firstVisibleItemIndex)
        .putInt("$channelId:offset", listState.firstVisibleItemScrollOffset)
        .putLong("$channelId:timestamp", System.currentTimeMillis())
        .apply()
    }

    suspend fun restoreChannelScrollPosition() {
        val prefs = context.getSharedPreferences("adis_channel_scroll_positions", android.content.Context.MODE_PRIVATE)
        val index = prefs.getInt("$channelId:index", -1)
        val offset = prefs.getInt("$channelId:offset", 0)
        if (index >= 0) {
        listState.scrollToItem(index.coerceAtMost(listState.layoutInfo.totalItemsCount - 1), offset)
        }
    }

    LaunchedEffect(channelId, feedPosts.size, query.isBlank()) {
        // Telegram-like channel behavior: opening the feed restores where the user
        // was reading instead of forcing a jump to the newest post.
        if (!restoredScroll && query.isBlank() && feedPosts.isNotEmpty()) {
        restoreChannelScrollPosition()
        restoredScroll = true
        }
    }

    // Only start persisting the reading position AFTER the restore has run, otherwise
    // the initial top-of-feed frame overwrites the saved index before restore reads it.
    LaunchedEffect(listState, channelId, query.isBlank(), restoredScroll) {
        if (query.isBlank() && restoredScroll) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .collect { saveChannelScrollPosition() }
        }
    }

    Scaffold(
        containerColor = ChCanvas,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        bottomBar = {
        if (isAdmin) {
        ChannelComposerBar(vm = vm)
        }
        },
    ) { padding ->
        Box(
        Modifier
        .fillMaxSize()
        .padding(padding)
        .background(ChCanvas),
        ) {
        if (state.isLoading && state.posts.isEmpty()) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = ChGreen)
        } else {
        LazyColumn(
        state      = listState,
        contentPadding = PaddingValues(bottom = 96.dp),
        ) {
        // - Cover + channel header -
        item(key = "cover") {
        ChannelCoverHeader(
        name      = channelName,
        photo       = channelPhoto,
        description = channelDescription,
        subscribers = subscriberCount,
        verified    = channelVerified,
        subscribed  = isSubscribed,
        onToggleSubscribe = {
        if (isSubscribed) {
        onLeaveCommunity()
        isSubscribed = false
        } else {
        onJoinCommunity()
        isSubscribed = true
        }
        },
        onShare     = {
        forwardText = buildString {
        appendLine(channelName)
        if (channelDescription.isNotBlank()) appendLine(channelDescription)
        append(AdisLinks.channel(channelId))
        }.trim()
        },
        onSearch    = { searchActive = true },
        onOpenInfo  = { showInfo = true },
        )
        }

        // - Channel game banner (real data) -
        // - Pinned banner -
        // - Wallet / tip section (real wallet backend) -
        // - Load-older spinner (older posts load at the top) -
        // - Empty state -
        if (feedPosts.isEmpty()) {
        item(key = "empty") {
        Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Icon(Icons.Default.Campaign, null, modifier = Modifier.size(52.dp), tint = ChTextMuted)
        Text(
        if (query.isNotBlank()) "No posts match \"$query\"" else "No posts yet",
        color = ChText, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        )
        if (query.isBlank()) {
        Text(
        if (isAdmin) "Use the bar below to publish your first post" else "Check back later for updates",
        color = ChTextSec, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
        }
        }
        }
        }

        // - Post feed (newest first, older posts paginate at bottom) -
        items(feedPosts, key = { it.id }) { post ->
        ChannelPostCard(
        post      = post,
        channelName  = channelName,
        channelPhoto = channelPhoto,
        isAdmin      = isAdmin,
        myUid      = myUid,
        onReact   = { emoji -> vm.react(post.id, emoji) },
        onView    = { vm.recordView(post.id) },
        onPin     = { vm.togglePin(post.id) },
        onEdit    = { editingPost = post },
        onDelete  = { vm.deletePost(post.id) },
        onRetry   = { vm.retryPost(post) },
        onOpenUrl = onOpenUrl,
        onShare   = {
        forwardText = buildString {
        if (post.title.isNotBlank()) appendLine(post.title)
        append(post.body.ifBlank { post.title })
        appendLine()
        append(AdisLinks.channel(channelId))
        }.trim().ifBlank { "Shared a post from $channelName" }
        },
        )
        }

        if (state.isLoadingMore) {
        item(key = "loadmore") {
        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = ChGreen)
        }
        }
        }

        }
        }

        // - Sticky glass top bar overlay -
        Row(
        modifier = Modifier
        .fillMaxWidth()
        .background(headerBg)
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        GlassIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "Back",
        onClick = { if (searchActive) { searchActive = false; query = "" } else onBack() },
        )

        if (searchActive) {
        // Search field - filters posts in this channel
        Box(
        modifier = Modifier
        .padding(start = 10.dp)
        .weight(1f)
        .clip(RoundedCornerShape(20.dp))
        .background(ChTertiary)
        .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
        ) {
        if (query.isBlank()) {
        Text(tr("Search posts-"), color = ChTextMuted, fontSize = 15.sp)
        }
        BasicTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        textStyle = TextStyle(color = ChText, fontSize = 15.sp),
        cursorBrush = SolidColor(ChGreen),
        modifier = Modifier.fillMaxWidth(),
        )
        }
        Spacer(Modifier.width(6.dp))
        GlassIconButton(icon = Icons.Default.Close, desc = "Close search",
        onClick = { searchActive = false; query = "" })
        } else {
        // Channel identity - tap to open channel info
        Row(
        modifier = Modifier
        .padding(start = 10.dp)
        .weight(1f)
        .clip(RoundedCornerShape(12.dp))
        .clickable { showInfo = true }
        .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        ChannelAvatar(photo = channelPhoto, name = channelName, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(channelName, color = ChText, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (channelVerified) {
        Spacer(Modifier.width(4.dp))
        AdisVerifiedBadge(Modifier.size(16.dp), contentDescription = "Verified")
        }
        }
        Text("${formatCount(subscriberCount)} subscribers",
        color = ChTextSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        }

        GlassIconButton(icon = Icons.Default.Search, desc = "Search",
        onClick = { searchActive = true })
        Spacer(Modifier.width(6.dp))
        }
        if (isAdmin) {
        Box {
        GlassIconButton(icon = Icons.Default.MoreVert, desc = "Channel options",
        onClick = { showOverflow = true })
        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
        DropdownMenuItem(
        text = { Text(tr("Edit channel")) },
        leadingIcon = { Icon(Icons.Default.Edit, null) },
        onClick = { showOverflow = false; showEditDialog = true },
        )
        when (channelVerificationStatus) {
        "approved" -> DropdownMenuItem(
        text = { Text(tr("Verified channel")) },
        leadingIcon = { Icon(Icons.Default.Verified, null, tint = ChBlue) },
        enabled = false,
        onClick = {},
        )
        "pending" -> DropdownMenuItem(
        text = { Text(tr("Blue badge pending")) },
        leadingIcon = { Icon(Icons.Default.Schedule, null, tint = ChTextSec) },
        enabled = false,
        onClick = {},
        )
        else -> DropdownMenuItem(
        text = { Text(if (channelVerificationStatus == "rejected") "Request blue badge again" else "Request blue badge") },
        leadingIcon = { Icon(Icons.Default.Verified, null, tint = ChBlue) },
        onClick = { showOverflow = false; showBadgeRequest = true },
        )
        }
        DropdownMenuItem(
        text = { Text(tr("Delete channel"), color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        onClick = { showOverflow = false; showDeleteDialog = true },
        )
        }
        }
        }
        }

        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED) {
        GamePotBannerHost(
        channelId = channelId,
        myUid     = myUid,
        isAdmin   = isAdmin,
        showPendingBanner = false,
        showCreateEntry   = false,
        onLiveGameVisibleChange = { liveGamePinned = it },
        modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 64.dp, start = 10.dp, end = 10.dp),
        )
        }

        pinnedPost?.let { pin ->
        FloatingPinnedBanner(
        text = pin.body.ifBlank { pin.title }.ifBlank { "Pinned post" },
        onClick = {
        query = ""
        val postIndex = orderedPosts.indexOfFirst { it.id == pin.id }
        if (postIndex >= 0) {
        screenScope.launch {
        listState.animateScrollToItem(1 + postIndex)
        }
        }
        },
        modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = if (liveGamePinned) 138.dp else 66.dp, start = 14.dp, end = 14.dp),
        )
        }

        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED) {
        ChannelGiftButton(
        onClick = { showTip = true },
        modifier = Modifier
        .align(Alignment.BottomEnd)
        .navigationBarsPadding()
        .padding(end = 18.dp, bottom = if (isAdmin) 86.dp else 22.dp),
        )
        }
        }
    }

    // - Edit post composer -
    editingPost?.let { post ->
        ChannelPostComposer(
        vm      = vm,
        existingPost = post,
        onDismiss    = { editingPost = null },
        )
    }

    // - Tip dialog -
    if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED && showTip) {
        ChannelGiftDialog(
        channelId = channelId,
        channelName = channelName,
        onDismiss   = { showTip = false },
        )
    }

    // - Request blue badge (admin) -
    if (showBadgeRequest) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
        onDismissRequest = { showBadgeRequest = false },
        icon  = { Icon(Icons.Default.Verified, null, tint = ChBlue) },
        title = { Text(tr("Request blue badge")) },
        text  = {
        Column {
        Text(tr("Submit this channel for verification. An admin will review and approve the blue badge."))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
        value = note, onValueChange = { note = it },
        label = { Text(tr("Why should this channel be verified? (optional)")) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
        )
        }
        },
        confirmButton = {
        Button(onClick = {
        showBadgeRequest = false
        val reason = note.trim()
        screenScope.launch {
        val result = com.adischat.app.data.repository.VerificationRepository()
        .requestChannelVerification(channelId, reason, channelName)
        result.getOrNull()?.let { channelVerificationStatus = it.status }
        val msg = if (result.isSuccess)
        "Verification request sent to admin for review"
        else
        CloudflareClient.friendlyErrorMessage(result.exceptionOrNull(), "Request failed. Please try again.")
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        }) { Text(tr("Send request")) }
        },
        dismissButton = { TextButton(onClick = { showBadgeRequest = false }) { Text(tr("Cancel")) } },
        )
    }

    // - Channel info sheet -
    if (showInfo) {
        ChannelInfoSheet(
        channelId   = channelId,
        name      = channelName,
        photo       = channelPhoto,
        description = channelDescription,
        subscribers = subscriberCount,
        verified    = channelVerified,
        postCount   = state.posts.size,
        canManage   = isAdmin,
        myUid       = myUid,
        onDismiss   = { showInfo = false },
        )
    }

    // - Forward a post - same chat-style "Forward to" recent-chats screen -
    forwardText?.let { text ->
        ForwardScreen(
        messageText = text,
        onDismiss   = { forwardText = null },
        )
    }

    // - Edit channel dialog -
    if (showEditDialog) {
        ChannelEditDialog(
        initialName      = channelName,
        initialDescription = channelDescription,
        initialPhotoUrl    = channelPhoto,
        onUploadPhoto      = onUploadCommunityPhoto,
        onDismiss      = { showEditDialog = false },
        onSave      = { name, desc, photo ->
        onUpdateCommunity(name, desc, photo)
        showEditDialog = false
        },
        )
    }

    // - Delete channel confirmation -
    if (showDeleteDialog) {
        AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        icon    = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title   = { Text(tr("Delete channel?")) },
        text    = { Text(tr("This removes the channel and all its posts for everyone.")) },
        confirmButton = {
        Button(
        onClick = { showDeleteDialog = false; onDeleteCommunity() },
        colors  = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor   = MaterialTheme.colorScheme.onError,
        ),
        ) { Text(tr("Delete")) }
        },
        dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text(tr("Cancel")) }
        },
        )
    }
}

// - Cover + header block -

@Composable
private fun ChannelCoverHeader(
    name: String,
    photo: String,
    description: String,
    subscribers: Int,
    verified: Boolean = false,
    category: String = "Public Channel",
    subscribed: Boolean = true,
    onToggleSubscribe: () -> Unit = {},
    onShare: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenInfo: () -> Unit = {},
) {
    // A branded gradient banner (NOT the avatar again) removes the old duplicate-photo
    // look and gives every channel a premium, consistent cover.
    val seed   = name.sumOf { it.code }
    val tone   = Color(0xFF0E7A3D).copy(alpha = 1f)
    val cover  = Brush.linearGradient(
        listOf(
        Color(0xFF15B85A).copy(alpha = 0.95f),
        ChGreen.copy(alpha = 0.55f),
        ChDeep,
        )
    )
    Column(Modifier.fillMaxWidth().background(ChDeep)) {
        // - Cover banner with soft radial glow + bottom fade into the canvas -
        Box(Modifier.fillMaxWidth().height(176.dp)) {
        Box(Modifier.fillMaxSize().background(cover))
        // Glass glow blob - subtle premium depth
        Box(
        Modifier
        .align(Alignment.TopEnd)
        .padding(top = 18.dp, end = 24.dp)
        .size(150.dp)
        .clip(CircleShape)
        .background(Brush.radialGradient(listOf(ChEmerald.copy(alpha = 0.45f), Color.Transparent)))
        )
        Box(
        Modifier.fillMaxSize().background(
        Brush.verticalGradient(
        0f to Color(0x33000000),
        0.55f to Color.Transparent,
        1f to ChDeep,
        )
        )
        )
        }

        // - Identity card overlapping the banner -
        Column(
        Modifier.fillMaxWidth().offset(y = (-40).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        // Glass avatar ring
        Box(
        Modifier
        .size(92.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(ChGreen, ChEmerald)))
        .padding(3.dp),
        contentAlignment = Alignment.Center,
        ) {
        Box(
        Modifier.fillMaxSize().clip(CircleShape).border(3.dp, ChDeep, CircleShape).background(ChTertiary),
        contentAlignment = Alignment.Center,
        ) { ChannelAvatar(photo = photo, name = name, size = 84.dp) }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = ChText, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        if (verified) {
        Spacer(Modifier.width(6.dp))
        ChannelBlueVerifiedBadge(Modifier.size(20.dp))
        }
        }

        // Category + subscriber meta row
        Spacer(Modifier.height(6.dp))
        Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(ChGlow)
        .border(1.dp, ChGreen.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text(category, color = ChGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.People, null, tint = ChTextSec, modifier = Modifier.size(15.dp))
        Text("${formatCount(subscribers)} subscribers", color = ChTextSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        }

        if (description.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
        description,
        color = ChTextSec,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 30.dp),
        )
        }

        // - Action row: Join/Following - Share - Search -
        Spacer(Modifier.height(16.dp))
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        // Primary subscribe/following pill
        Row(
        Modifier.weight(1f).height(46.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (subscribed) ChTertiary else ChGreen)
        .border(1.dp, if (subscribed) ChBorder else Color.Transparent, RoundedCornerShape(14.dp))
        .clickable(onClick = onToggleSubscribe),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(
        if (subscribed) Icons.Default.Check else Icons.Default.Add,
        null, tint = if (subscribed) ChText else ChDeep, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
        if (subscribed) "Unfollow" else tr("Join"),
        color = if (subscribed) ChText else ChDeep, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
        }
        HeaderActionButton(Icons.Default.Share, "Share", onShare)
        HeaderActionButton(Icons.Default.Search, "Search", onSearch)
        HeaderActionButton(Icons.Default.Info, "Channel info", onOpenInfo)
        }
        }
    }
}

@Composable
private fun ChannelBlueVerifiedBadge(modifier: Modifier = Modifier) {
    AdisVerifiedBadge(modifier = modifier, contentDescription = "Verified")
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
        .background(ChTertiary).border(1.dp, ChBorder, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = ChText, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ChannelAvatar(photo: String, name: String, size: androidx.compose.ui.unit.Dp) {
    if (photo.isNotBlank()) {
        AsyncImage(
        model = photo,
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
        )
    } else {
        Box(
        Modifier.size(size).clip(CircleShape).background(ChGreen.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        ) {
        Text(name.firstOrNull()?.uppercase() ?: "C", color = ChGreen, fontWeight = FontWeight.Bold,
        fontSize = (size.value / 2.4f).sp)
        }
    }
}


@Composable
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(ChGlass)
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = ChText, modifier = Modifier.size(20.dp)) }
}

// - Action buttons -

// - Pinned banner -

@Composable
private fun PinnedBanner(text: String) {
    Row(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ChGlass)
        .border(1.dp, Color(0x3318C964), RoundedCornerShape(12.dp))
        .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.PushPin, null, tint = ChGreen, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("PINNED"), color = ChGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(text, color = ChTextSec, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// - Wallet / tip section -

@Composable
private fun FloatingPinnedBanner(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = ChGlass,
        border = BorderStroke(1.dp, Color(0x3318C964)),
        shadowElevation = 10.dp,
    ) {
        Row(
        Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
        Icon(Icons.Default.PushPin, null, tint = ChGreen, modifier = Modifier.size(17.dp))
        Text(tr("PINNED"), color = ChGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = ChText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChannelGiftButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
        .size(58.dp)
        .shadow(18.dp, CircleShape, ambientColor = ChGreen.copy(alpha = 0.22f), spotColor = ChGreen.copy(alpha = 0.68f))
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(ChGreen, ChEmerald)))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.CardGiftcard, "Send gift", tint = ChDeep, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun WalletTipSection(onTip: () -> Unit) {
    Column(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(ChSurface)
        .border(1.dp, ChBorder, RoundedCornerShape(16.dp))
        .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.linearGradient(listOf(ChGreen, ChEmerald))),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.AccountBalanceWallet, null, tint = ChDeep, modifier = Modifier.size(18.dp)) }
        Column {
        Text(tr("AdisPay"), color = ChText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("Support this channel with a tip"), color = ChTextMuted, fontSize = 15.sp)
        }
        }
        Spacer(Modifier.height(12.dp))
        Box(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(ChGreen)
        .clickable(onClick = onTip)
        .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.AttachMoney, null, tint = ChDeep, modifier = Modifier.size(16.dp))
        Text(tr("Send Tip"), color = ChDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}

// - Tip dialog (reuses real wallet send) -

@Composable
private fun ChannelGiftDialog(channelId: String, channelName: String, onDismiss: () -> Unit) {
    val scope      = rememberCoroutineScope()
    val context    = LocalContext.current

    var coins     by remember { mutableStateOf("10") }
    var emoji     by remember { mutableStateOf("-") }
    var pin       by remember { mutableStateOf("") }
    var sending   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    val coinValue = coins.toIntOrNull() ?: 0
    val etbValue = coinValue / 10.0
    val canSend = !sending && coinValue >= 10 && pin.length >= 4

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon  = { Icon(Icons.Default.CardGiftcard, null, tint = ChGreen) },
        title = { Text(tr("Send a gift"), fontWeight = FontWeight.Bold) },
        text  = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$channelName receives gifts as coins and emojis, not direct money.", color = ChTextSec, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("-", "-", "-", "-").forEach { item ->
        Surface(
        shape = CircleShape,
        color = if (emoji == item) ChGreen.copy(alpha = 0.22f) else ChSurface,
        border = BorderStroke(1.dp, if (emoji == item) ChGreen else ChBorder),
        modifier = Modifier.size(44.dp).clickable { emoji = item },
        ) {
        Box(contentAlignment = Alignment.Center) { Text(item, fontSize = 22.sp) }
        }
        }
        }
        OutlinedTextField(
        value = coins,
        onValueChange = { new -> coins = new.filter { it.isDigit() }.take(6) },
        label = { Text(tr("Coins")) }, singleLine = true, enabled = !sending,
        supportingText = { Text("Minimum 10 coins. 10 coins = 1 ETB. This gift is worth ETB ${"%.2f".format(etbValue)}.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
        value = pin,
        onValueChange = { new -> pin = new.filter { it.isDigit() }.take(6) },
        label = { Text(tr("Wallet PIN")) }, singleLine = true, enabled = !sending,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
        }
        },
        confirmButton = {
        TextButton(
        enabled = canSend,
        onClick = {
        sending = true; error = null
        scope.launch {
        runCatching {
        com.adischat.app.network.CloudflareClient.walletApi.sendChannelGift(
        channelId,
        com.adischat.app.network.ChannelGiftBody(emoji = emoji, coins = coinValue, pin = pin),
        )
        }.onSuccess {
        Toast.makeText(context, "$emoji $coinValue coins sent to $channelName", Toast.LENGTH_SHORT).show()
        sending = false
        onDismiss()
        }.onFailure {
        error = CloudflareClient.friendlyErrorMessage(it, "Gift failed")
        sending = false
        }
        }
        },
        ) {
        if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ChGreen)
        else Text(tr("Send Gift"), color = ChGreen, fontWeight = FontWeight.Bold)
        }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

// - Post card -

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelPostCard(
    post:      ChannelPost,
    channelName:  String,
    channelPhoto: String,
    isAdmin:      Boolean,
    myUid:      String,
    onReact:  (String) -> Unit,
    onView:   () -> Unit,
    onPin:    () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit,
    onRetry:  () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare:  () -> Unit,
) {
    var showMenu      by remember { mutableStateOf(false) }
    var showContext   by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    val clipboard     = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx      = LocalContext.current
    val haptics       = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Optimistic reactions - update instantly, sync when the server pushes new data.
    var reactions by remember(post.id) { mutableStateOf(post.reactions) }
    LaunchedEffect(post.reactions) { reactions = post.reactions }
    val myReaction = reactions[myUid]
    val counts = reactions.values.groupingBy { it }.eachCount()
    fun react(emoji: String) {
        reactions = reactions.toMutableMap().apply {
        if (this[myUid] == emoji) remove(myUid) else this[myUid] = emoji
        }
        onReact(emoji)
    }

    LaunchedEffect(post.id) { onView() }

    Column(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 7.dp)
        .shadow(
        elevation = 8.dp,
        shape = RoundedCornerShape(24.dp),
        ambientColor = Color.Black.copy(alpha = 0.08f),
        spotColor = Color.Black.copy(alpha = 0.10f),
        )
        .clip(RoundedCornerShape(24.dp))
        .background(ChSurface)
        .border(1.dp, ChBorder, RoundedCornerShape(24.dp))
        .combinedClickable(onClick = {}, onLongClick = { showContext = true }),
    ) {
        // - Long-press actions: modern bottom sheet (Telegram/WhatsApp style) -
        if (showContext) {
        ModalBottomSheet(
        onDismissRequest = { showContext = false },
        containerColor   = ChSurface,
        shape      = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        Text(
        post.title.ifBlank { post.body.ifBlank { post.linkUrl.ifBlank { "Post" } } },
        color = ChText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
        )
        Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .clickable { showContext = false; onShare() }
        .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        CurvedForwardIcon(tint = ChText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(tr("Forward"), color = ChText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        ChannelSheetRow(Icons.Default.ContentCopy, tr("Copy"), ChText) {
        showContext = false
        val text = buildString {
        if (post.title.isNotBlank()) appendLine(post.title)
        append(post.body.ifBlank { post.linkUrl })
        }.trim()
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }
        if (isAdmin) {
        ChannelSheetRow(Icons.Default.PushPin, if (post.pinned) tr("Unpin") else tr("Pin to top"), ChText) { showContext = false; onPin() }
        ChannelSheetRow(Icons.Default.Edit, tr("Edit"), ChText) { showContext = false; onEdit() }
        ChannelSheetRow(Icons.Default.DeleteOutline, tr("Delete"), Color(0xFFFF5A5A)) { showContext = false; onDelete() }
        }
        }
        }
        }
        // - Header -
        Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        ChannelAvatar(photo = channelPhoto, name = channelName, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(channelName.ifBlank { "Channel" }, color = ChText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isAdmin) {
        Spacer(Modifier.width(8.dp))
        Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(ChGlow).padding(horizontal = 7.dp, vertical = 2.dp),
        ) { Text(tr("ADMIN"), color = ChGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp) }
        }
        }
        Spacer(Modifier.height(2.dp))
        Text(
        when (post.localStatus) {
        "publishing" -> "Publishing..."
        "published" -> "Published"
        "failed" -> "Publish failed"
        else -> formatPostTime(post.createdAt)
        },
        color = when (post.localStatus) {
        "publishing", "published" -> ChGreen
        "failed" -> MaterialTheme.colorScheme.error
        else -> ChTextMuted
        },
        fontSize = 13.sp,
        fontWeight = if (post.localStatus.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = 0.sp,
        )
        }
        if (post.pinned) {
        Icon(Icons.Default.PushPin, "Pinned", tint = ChGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        }
        if (isAdmin) {
        Box {
        IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Default.MoreVert, "More", tint = ChTextMuted)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
        text = { Text(if (post.pinned) "Unpin" else "Pin to top") },
        onClick = { showMenu = false; onPin() },
        leadingIcon = { Icon(Icons.Default.PushPin, null) },
        )
        DropdownMenuItem(
        text = { Text(tr("Edit")) },
        onClick = { showMenu = false; onEdit() },
        leadingIcon = { Icon(Icons.Default.Edit, null) },
        )
        DropdownMenuItem(
        text = { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) },
        onClick = { showMenu = false; onDelete() },
        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        )
        }
        }
        }
        }

        // - Title -
        if (post.title.isNotBlank()) {
        Text(post.title, color = ChText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        lineHeight = 26.sp, letterSpacing = 0.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
        }

        // - Body (comfortable 1.5 line height) -
        if (post.body.isNotBlank()) {
        LinkifiedText(
        text = post.body,
        color = ChText.copy(alpha = 0.92f),
        linkColor = ChGreen,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        onOpenUrl = onOpenUrl,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (post.title.isBlank()) 14.dp else 6.dp, bottom = 4.dp),
        )
        }

        // - Media (image / video) -
        if (post.mediaUrl.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        if (post.isVideo) {
        Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(210.dp)
        .clip(RoundedCornerShape(16.dp)).background(ChDeep),
        contentAlignment = Alignment.Center,
        ) {
        AsyncImage(
        model = post.mediaUrl, contentDescription = "Video",
        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        Box(
        Modifier.size(60.dp).clip(CircleShape).background(Color(0x40000000))
        .border(1.5.dp, Color(0x55FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        } else {
        AsyncImage(
        model = post.mediaUrl, contentDescription = "Post image",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
        .clip(RoundedCornerShape(16.dp)).heightIn(max = 360.dp),
        contentScale = ContentScale.Crop,
        )
        }
        }

        // - Link preview -
        if (post.linkUrl.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 14.dp)) {
        PremiumLinkPreview(url = post.linkUrl, title = post.linkTitle, image = post.linkImage, onOpenUrl = onOpenUrl)
        }
        }

        Spacer(Modifier.height(12.dp))
        if (post.localStatus.isNotBlank()) {
        ChannelPostLocalStatus(post = post, onRetry = onRetry)
        Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(color = ChBorder, modifier = Modifier.padding(horizontal = 16.dp))

        // - All existing reactions (every emoji + count) -
        if (counts.isNotEmpty()) {
        FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        val ranked = counts.entries.sortedByDescending { it.value }
        ranked.take(6).forEach { (emoji, count) ->
        val active = myReaction == emoji
        Row(
        Modifier
        .clip(CircleShape)
        .background(if (active) ChGlow else ChTertiary)
        .border(1.dp, if (active) ChGreen else ChBorder, CircleShape)
        .clickable { react(emoji) }
        .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        Text(emoji, fontSize = 17.sp)
        Text(formatCount(count), color = ChTextSec, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        }
        if (ranked.size > 6) {
        Box(
        Modifier.clip(CircleShape).background(ChTertiary).border(1.dp, ChBorder, CircleShape)
        .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("+${ranked.size - 6}", color = ChTextSec, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
        }
        }

        // - Telegram-style quick reactions (tap to react, tap again to remove,
        //    long-press for the full emoji picker) -
        LazyRow(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        items(ChannelReactionSet) { emoji ->
        val active = myReaction == emoji
        val scale by animateFloatAsState(
        targetValue = if (active) 1.18f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "reactScale",
        )
        Box(
        Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(CircleShape)
        .background(if (active) ChGlow else Color.Transparent)
        .border(1.dp, if (active) ChGreen.copy(alpha = 0.45f) else Color.Transparent, CircleShape)
        .combinedClickable(
        onClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); react(emoji) },
        onLongClick = { showReactionPicker = true },
        )
        .padding(horizontal = 6.dp, vertical = 4.dp),
        ) { Text(emoji, fontSize = 23.sp) }
        }
        item {
        Box(
        Modifier.clip(CircleShape).background(ChTertiary)
        .clickable { showReactionPicker = true }.padding(8.dp),
        ) { Icon(Icons.Default.Add, "More reactions", tint = ChTextMuted, modifier = Modifier.size(18.dp)) }
        }
        }

        if (showReactionPicker) {
        ReactionPickerSheet(
        current = myReaction,
        onPick  = { emoji -> showReactionPicker = false; react(emoji) },
        onDismiss = { showReactionPicker = false },
        )
        }

        // - Footer: views + share -
        Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        // Modern view counter - only shown once it's meaningful
        if (post.views > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Default.Visibility, null, tint = ChTextMuted, modifier = Modifier.size(15.dp))
        Text("${formatCount(post.views)} views", color = ChTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        }
        Spacer(Modifier.weight(1f))
        Text(formatPostTime(post.createdAt), color = ChTextMuted, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Row(
        Modifier
        .clip(CircleShape)
        .background(ChTertiary)
        .clickable(onClick = onShare)
        .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
        CurvedForwardIcon(tint = ChTextMuted, modifier = Modifier.size(16.dp))
        Text(tr("Forward"), color = ChTextMuted, fontSize = 14.sp)
        }
        }
    }
}

// - Premium link preview -

@Composable
private fun ChannelPostLocalStatus(post: ChannelPost, onRetry: () -> Unit) {
    val isFailed = post.localStatus == "failed"
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.10f) else ChGlow)
        .border(
        1.dp,
        if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.24f) else ChGreen.copy(alpha = 0.24f),
        RoundedCornerShape(14.dp),
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFailed) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        } else {
        CircularProgressIndicator(color = ChGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
        Text(
        if (isFailed) "Upload failed" else "Publishing...",
        color = if (isFailed) MaterialTheme.colorScheme.error else ChGreen,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        )
        Text(
        if (isFailed) post.localError.ifBlank { "Tap retry to publish again" } else "Syncing ${post.localProgress.coerceIn(0, 100)}%",
        color = ChTextMuted,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        )
        }
        if (isFailed) {
        TextButton(onClick = onRetry) {
        Text("Retry", color = ChGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}

@Composable
private fun PremiumLinkPreview(url: String, title: String, image: String, onOpenUrl: (String) -> Unit = {}) {
    Row(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(ChTertiary)
        .border(1.dp, ChBorder, RoundedCornerShape(12.dp))
        .clickable { onOpenUrl(url) },
    ) {
        if (image.isNotBlank()) {
        AsyncImage(
        model = image, contentDescription = null,
        modifier = Modifier.size(92.dp), contentScale = ContentScale.Crop,
        )
        } else {
        Box(Modifier.size(92.dp).background(ChGlow), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Link, null, tint = ChGreen, modifier = Modifier.size(26.dp))
        }
        }
        Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.Center) {
        val site = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
        if (site.isNotBlank()) Text(site, color = ChTextMuted, fontSize = 14.sp)
        if (title.isNotBlank()) {
        Text(title, color = ChText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(url, color = ChGreen, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// - In-app forward sheet (chats / channels / groups only) -

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    messageText: String,
    onDismiss: () -> Unit,
    // Source media so an image / video / voice / file is forwarded as real media
    // (pointing at its already-uploaded URL) instead of as a "[Photo]" text placeholder.
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
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        // Load every list in PARALLEL (this was sequential, so the picker could spin for
        // up to ~24s before showing). Each fetch is guarded by a timeout so the sheet
        // never hangs on a flow that doesn't emit.
        val meD      = async { runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { authRepo.getCurrentAppUser() } }.getOrNull() }
        val directsD = async { runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeAllConversations().first() } }.getOrNull() ?: emptyList() }
        val chansD   = async { runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeCommunities("channel", "").first() } }.getOrNull() ?: emptyList() }
        val grpsD    = async { runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeCommunities("group", "").first() } }.getOrNull() ?: emptyList() }
        me      = meD.await()
        directs = directsD.await()
        chans   = chansD.await()
        grps    = grpsD.await()
        loading = false
    }

    fun forward() {
        if (selected.isEmpty() || sending) return
        sending = true
        scope.launch {
        val u = me
        selected.toList().forEach { id ->
        val isCommunity = chans.any { it.id == id } || grps.any { it.id == id }
        chatRepo.forwardMessage(
        targetId       = id,
        isCommunity    = isCommunity,
        senderName     = u?.name ?: "",
        senderPhotoUrl = u?.photoUrl ?: "",
        text      = messageText,
        type      = sourceType,
        mediaUrl       = mediaUrl,
        mediaMimeType  = mediaMimeType,
        durationMs     = durationMs,
        )
        }
        sending = false
        Toast.makeText(ctx, "Message forwarded", Toast.LENGTH_SHORT).show()
        onDismiss()
        }
    }

    var tab by remember { mutableStateOf(ForwardTab.Recent) }
    val q = query.trim()
    fun ForwardTarget.matchesQuery(): Boolean =
        q.isBlank() || name.contains(q, ignoreCase = true) || subtitle.contains(q, ignoreCase = true)

    val chatTargets = remember(directs, q) {
        directs.sortedByDescending { it.lastMessageAt }
        .map {
        ForwardTarget(
        it.id, it.otherUserName.ifBlank { "Chat" }, it.otherUserPhotoUrl,
        it.lastMessage.ifBlank { "Tap to forward here" }, "chat", it.lastMessageAt, it.unreadCount,
        )
        }
        .filter { it.matchesQuery() }
    }
    val groupTargets = remember(grps, q) {
        grps.map {
        ForwardTarget(
        it.id, it.name.ifBlank { "Group" }, it.photoUrl,
        it.lastMessage.ifBlank { it.lastPostPreview }.ifBlank { "Group" }, "group",
        it.lastPostAt.takeIf { t -> t > 0L } ?: it.lastMessageAt, it.unreadCount,
        )
        }.filter { it.matchesQuery() }
    }
    val channelTargets = remember(chans, q) {
        chans.map {
        ForwardTarget(
        it.id, it.name.ifBlank { "Channel" }, it.photoUrl,
        it.lastPostPreview.ifBlank { it.lastMessage }.ifBlank { "Channel" }, "channel",
        it.lastPostAt.takeIf { t -> t > 0L } ?: it.lastMessageAt, it.unreadCount,
        )
        }.filter { it.matchesQuery() }
    }
    val visibleTargets = when (tab) {
        ForwardTab.Recent   -> (chatTargets + groupTargets + channelTargets).sortedByDescending { it.activityAt }
        ForwardTab.Chats    -> chatTargets
        ForwardTab.Groups   -> groupTargets
        ForwardTab.Channels -> channelTargets
    }

    // Full-screen "Forward to" page (Telegram/WhatsApp style) - NOT a bottom sheet.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = ChDeep) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {

        // - Top bar -
        Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconButton(onClick = onDismiss) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ChText)
        }
        Column(Modifier.weight(1f)) {
        Text(tr("Forward to"), color = ChText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(tr("Choose chats, groups, or channels"), color = ChTextMuted, fontSize = 13.sp)
        }
        }

        // - Forwarded-message preview chip -
        Box(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(ChTertiary)
        .border(1.dp, ChBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
        Text(
        messageText.ifBlank {
        when (sourceType) {
        "image" -> "- Photo"
        "video" -> "- Video"
        "voice" -> "- Voice message"
        else    -> "Forwarded message"
        }
        },
        color = ChTextSec, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        }
        Spacer(Modifier.height(10.dp))

        // - Search -
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(46.dp)
        .clip(RoundedCornerShape(999.dp)).background(ChTertiary)
        .border(1.dp, ChBorder.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(Icons.Default.Search, null, tint = ChTextMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
        value = query, onValueChange = { query = it },
        modifier = Modifier.weight(1f), singleLine = true,
        textStyle = TextStyle(color = ChText, fontSize = 15.sp),
        cursorBrush = SolidColor(ChGreen),
        decorationBox = { inner ->
        if (query.isBlank()) Text("Search chats, groups, channels", color = ChTextMuted, fontSize = 15.sp)
        inner()
        },
        )
        if (query.isNotBlank()) IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.Close, "Clear", tint = ChTextMuted, modifier = Modifier.size(16.dp))
        }
        }
        Spacer(Modifier.height(12.dp))

        // - Tabs: Recent - Chats - Groups - Channels -
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        ForwardTab.values().forEach { t ->
        val active = tab == t
        Box(
        Modifier.clip(RoundedCornerShape(999.dp))
        .background(if (active) ChGreen else ChTertiary)
        .border(1.dp, if (active) ChGreen else ChBorder.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
        .clickable { tab = t }
        .padding(horizontal = 15.dp, vertical = 8.dp),
        ) {
        Text(tr(t.label), color = if (active) ChDeep else ChTextSec, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        }
        }
        Spacer(Modifier.height(6.dp))

        // - Destination list -
        if (loading) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ChGreen, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        } else {
        LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
        if (visibleTargets.isEmpty()) {
        item {
        Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Text(
        if (query.isNotBlank()) tr("No matches found")
        else when (tab) {
        ForwardTab.Channels -> tr("No channels yet")
        ForwardTab.Groups   -> tr("No groups yet")
        else -> tr("No chats yet")
        },
        color = ChTextMuted, fontSize = 14.sp,
        )
        }
        }
        } else {
        items(visibleTargets, key = { "${it.kind}_${it.id}" }) { t ->
        val checked = selected.contains(t.id)
        ForwardTargetRow(t, checked) {
        if (checked) selected.remove(t.id) else selected.add(t.id)
        }
        }
        }
        }
        }

        // - Bottom action bar: count - Cancel - Forward (loading only here) -
        Row(
        Modifier.fillMaxWidth().navigationBarsPadding()
        .background(ChSurface)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Text(
        if (selected.isEmpty()) tr("Select destinations") else "${selected.size} selected",
        color = if (selected.isEmpty()) ChTextMuted else ChText,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, enabled = !sending) {
        Text(tr("Cancel"), color = ChTextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(6.dp))
        Button(
        onClick = { forward() }, enabled = selected.isNotEmpty() && !sending,
        modifier = Modifier.height(46.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
        containerColor = ChGreen, contentColor = ChDeep,
        disabledContainerColor = ChGreen.copy(alpha = 0.35f), disabledContentColor = ChDeep.copy(alpha = 0.5f),
        ),
        ) {
        if (sending) {
        CircularProgressIndicator(color = ChDeep, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp)); Text(tr("Forwarding-"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        } else {
        CurvedForwardIcon(tint = ChDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp)); Text(tr("Forward"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        }
        }
        }
        }
    }
}

private data class ForwardTarget(
    val id: String,
    val name: String,
    val photo: String,
    val subtitle: String,
    val kind: String = "chat",     // "chat" | "group" | "channel"
    val activityAt: Long = 0L,
    val unread: Int = 0,
)

private enum class ForwardTab(val label: String) {
    Recent("Recent"), Chats("Chats"), Groups("Groups"), Channels("Channels")
}

@Composable
private fun InfoActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChannelSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ForwardTargetRow(target: ForwardTarget, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
        .heightIn(min = 62.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(if (checked) ChGreen.copy(alpha = 0.10f) else Color.Transparent)
        .clickable(onClick = onToggle)
        .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(photo = target.photo, name = target.name, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(target.name, color = ChText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(target.subtitle, color = ChTextMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (target.unread > 0 && !checked) {
        Box(
        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(ChGreen).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
        ) {
        Text(if (target.unread > 99) "99+" else target.unread.toString(), color = ChDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        }
        Box(
        Modifier.size(24.dp).clip(CircleShape)
        .background(if (checked) ChGreen else ChTertiary)
        .border(1.dp, if (checked) ChGreen else ChBorder, CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = ChDeep, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun CurvedForwardIcon(
    modifier: Modifier = Modifier,
    tint: Color = ChText,
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
private fun ForwardRecentTarget(target: ForwardTarget, checked: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
        .width(68.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
        ChannelAvatar(photo = target.photo, name = target.name, size = 52.dp)
        if (checked) {
        Box(
        Modifier
        .align(Alignment.BottomEnd)
        .size(20.dp)
        .clip(CircleShape)
        .background(ChGreen)
        .border(2.dp, ChSurface, CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.Check, null, tint = ChDeep, modifier = Modifier.size(13.dp))
        }
        }
        }
        Spacer(Modifier.height(6.dp))
        Text(
        target.name,
        color = if (checked) ChGreen else ChTextSec,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.forwardSection(
    label: String,
    targets: List<ForwardTarget>,
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
) {
    if (targets.isEmpty()) return
    item(key = "hdr_$label") {
        Text(label.uppercase(), color = ChTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    }
    items(targets, key = { "${label}_${it.id}" }) { t ->
        val checked = selected.contains(t.id)
        Row(
        Modifier.fillMaxWidth()
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(if (checked) ChGreen.copy(alpha = 0.10f) else Color.Transparent)
        .clickable { if (checked) selected.remove(t.id) else selected.add(t.id) }
        .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        ChannelAvatar(photo = t.photo, name = t.name, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(t.name, color = ChText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.subtitle, color = ChTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
        Modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(if (checked) ChGreen else ChTertiary)
        .border(1.dp, if (checked) ChGreen else ChBorder, CircleShape),
        contentAlignment = Alignment.Center,
        ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = ChDeep, modifier = Modifier.size(15.dp))
        }
        }
    }
}

// - Reactions -

/** Telegram-style default quick reaction set shown on every post. */
private val ChannelReactionSet = listOf("-", "-", "-", "-", "-", "-", "-", "-")

/** Larger palette for the long-press "full" reaction picker. */
private val ChannelReactionPalette = listOf(
    "-", "-", "-", "-", "-", "-", "-", "-",
    "-", "-", "-", "-", "-", "-", "-", "-",
    "-", "-", "-", "-", "-", "-", "-", "-",
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReactionPickerSheet(
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = ChSurface,
        shape      = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(tr("React"), color = ChText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp))
        FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        ChannelReactionPalette.forEach { emoji ->
        val active = current == emoji
        Box(
        Modifier.size(50.dp).clip(CircleShape)
        .background(if (active) ChGlow else ChTertiary)
        .border(1.dp, if (active) ChGreen else ChBorder, CircleShape)
        .clickable { onPick(emoji) },
        contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 26.sp) }
        }
        }
        Spacer(Modifier.height(12.dp))
        }
    }
}

// - Helpers -

/** 128400 -> "128.4K", 2_400_000 -> "2.4M". */
private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).removeSuffix(".0M") + if (n % 1_000_000 == 0) "M" else ""
    n >= 1_000     -> String.format(Locale.US, "%.1fK", n / 1_000.0).let { if (it.endsWith(".0K")) it.removeSuffix(".0K") + "K" else it }
    else -> n.toString()
}

private fun formatPostTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now  = System.currentTimeMillis()
    val diff = now - epochMs
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
    val day = when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L      -> "${diff / 60_000L}m ago"
        diff < 86_400_000L     -> "Today"
        diff < 2 * 86_400_000L -> "Yesterday"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
    return if (day == "Just now") day else "$day - $time"
}

// - Chat-style bottom composer bar (admin) -
// Persistent Telegram/WhatsApp-style input bar pinned to the bottom of the feed.
// Posts text, photo, video, document and voice - all through the existing
// ChannelViewModel.createPost / uploadMedia pipeline (no backend changes).
@Composable
private fun ChannelComposerBar(vm: ChannelViewModel) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
    }

    var draft       by remember { mutableStateOf("") }
    var showAttach  by remember { mutableStateOf(false) }
    // Which kind of upload is in flight, so we know how to post when the URL lands.
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var docName     by remember { mutableStateOf("") }
    var voiceMs     by remember { mutableStateOf(0L) }

    val voiceRecorder = remember { ChannelVoiceRecorder(context) }
    val isRecording = voiceRecorder.isRecording

    // When an upload finishes, publish the post immediately.
    LaunchedEffect(state.mediaUploadUrl) {
        val url  = state.mediaUploadUrl ?: return@LaunchedEffect
        val kind = pendingKind
        when (kind) {
        "image" -> vm.createPost(title = "", body = draft, postType = "image",
        mediaUrl = url, mediaMimeType = "image/jpeg")
        "video" -> vm.createPost(title = "", body = draft, postType = "video",
        mediaUrl = url, mediaMimeType = "video/mp4")
        "document" -> vm.createPost(title = "", body = draft, postType = "link",
        linkUrl = url, linkTitle = docName.ifBlank { "Document" })
        "voice" -> {
        val secs = (voiceMs / 1000).coerceAtLeast(1)
        vm.createPost(title = "", body = draft, postType = "link",
        linkUrl = url, linkTitle = "- Voice message - ${secs}s")
        }
        else -> {}
        }
        draft = ""; pendingKind = null; docName = ""; voiceMs = 0L
        vm.clearMediaUpload()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { pendingKind = "image"; vm.uploadMedia(uri) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { pendingKind = "video"; vm.uploadMedia(uri) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
        val f = File.createTempFile("post_cam_", ".jpg", context.cacheDir)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        pendingKind = "image"; vm.uploadMedia(Uri.fromFile(f))
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
        docName = channelQueryFileName(context, uri)
        pendingKind = "document"; vm.uploadMedia(uri)
        }
    }

    if (showAttach) {
        ChannelAttachSheet(
        onDismiss  = { showAttach = false },
        onPhoto    = { showAttach = false; imagePicker.launch("image/*") },
        onCamera   = {
        showAttach = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        cameraLauncher.launch(null)
        } else {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        },
        onVideo    = { showAttach = false; videoPicker.launch("video/*") },
        onDocument = { showAttach = false; documentPicker.launch("*/*") },
        )
    }

    val busy = state.mediaUploading || state.actionLoading

    Surface(
        color      = ChSurface,
        tonalElevation = 8.dp,
        modifier       = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
    ) {
        Column {
        if (busy && pendingKind != null) {
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ChGreen)
        Spacer(Modifier.width(10.dp))
        Text(
        "Publishing " + (pendingKind ?: "") + (if (docName.isNotBlank()) " - $docName" else "") + "-",
        color = ChTextSec, fontSize = 13.sp,
        )
        }
        }
        state.mediaUploadError?.let {
        Text(it, color = ChRed, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (isRecording) {
        // - Recording state -
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(ChRed))
        Spacer(Modifier.width(10.dp))
        Text(tr("Recording voice-"), color = ChText, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { voiceRecorder.cancel() }) {
        Text(tr("Cancel"), color = ChTextSec)
        }
        Box(
        Modifier.size(44.dp).clip(CircleShape).background(ChGreen)
        .clickable {
        voiceRecorder.stop()?.let { (f, ms) ->
        voiceMs = ms; pendingKind = "voice"; vm.uploadMedia(Uri.fromFile(f))
        }
        },
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send voice", tint = ChDeep, modifier = Modifier.size(20.dp)) }
        }
        } else {
        // - Normal state -
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        ) {
        // Attach
        Box(
        Modifier.size(44.dp).clip(CircleShape).background(ChTertiary)
        .clickable(enabled = !busy) { showAttach = true },
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.AttachFile, "Attach", tint = ChGreen, modifier = Modifier.size(22.dp)) }

        Spacer(Modifier.width(8.dp))

        // Text field
        Box(
        Modifier.weight(1f).heightIn(min = 44.dp)
        .clip(RoundedCornerShape(22.dp)).background(ChTertiary)
        .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
        ) {
        if (draft.isBlank()) {
        Text(tr("Broadcast a message-"), color = ChTextMuted, fontSize = 15.sp)
        }
        BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = TextStyle(color = ChText, fontSize = 15.sp),
        cursorBrush = SolidColor(ChGreen),
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
        )
        }

        Spacer(Modifier.width(8.dp))

        // Send (text) or Mic (voice)
        Box(
        Modifier.size(44.dp).clip(CircleShape).background(ChGreen)
        .clickable(enabled = !busy) {
        if (draft.isNotBlank()) {
        vm.createPost(title = "", body = draft, postType = "text")
        draft = ""
        } else {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        if (!voiceRecorder.start()) {
        Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
        } else {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        }
        },
        contentAlignment = Alignment.Center,
        ) {
        Icon(
        if (draft.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
        contentDescription = if (draft.isNotBlank()) "Send" else "Record voice",
        tint = ChDeep, modifier = Modifier.size(22.dp),
        )
        }
        }
        }
        }
    }
}

// - Channel info bottom sheet -
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelInfoSheet(
    channelId:    String,
    name:      String,
    photo:       String,
    description: String,
    subscribers: Int,
    verified:    Boolean,
    postCount:   Int,
    canManage:   Boolean,
    myUid:       String,
    onDismiss:   () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var giftBalance by remember(channelId) { mutableStateOf<com.adischat.app.network.ChannelGiftBalanceResponse?>(null) }
    var giftLoading by remember(channelId) { mutableStateOf(false) }
    var giftConverting by remember(channelId) { mutableStateOf(false) }
    var giftError by remember(channelId) { mutableStateOf<String?>(null) }

    fun loadGiftBalance() {
        if (!com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED || !canManage || giftLoading) return
        giftLoading = true
        giftError = null
        scope.launch {
        runCatching {
        com.adischat.app.network.CloudflareClient.walletApi.getChannelGiftBalance(channelId)
        }.onSuccess {
        giftBalance = it
        }.onFailure {
        giftError = CloudflareClient.friendlyErrorMessage(it, "Could not load channel gifts")
        }
        giftLoading = false
        }
    }

    LaunchedEffect(channelId, canManage) {
        if (com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED && canManage) loadGiftBalance()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ChSurface) {
        Column(
        Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .imePadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        ChannelAvatar(photo = photo, name = name, size = 88.dp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = ChText, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        textAlign = TextAlign.Center)
        if (verified) {
        Spacer(Modifier.width(6.dp))
        ChannelBlueVerifiedBadge(Modifier.size(20.dp))
        }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${formatCount(subscribers)} subscribers", color = ChTextSec, fontSize = 14.sp)
        Text("$postCount posts", color = ChTextSec, fontSize = 14.sp)
        }
        if (description.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        Surface(
        color = ChTertiary, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        ) {
        Text(description, color = ChText, fontSize = 15.sp, lineHeight = 21.sp,
        modifier = Modifier.padding(16.dp))
        }
        }

        // - Subscriber actions (everyone) -
        Spacer(Modifier.height(16.dp))
        var muted by remember(channelId) { mutableStateOf(false) }
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Surface(color = ChTertiary, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
        InfoActionRow(
        if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
        if (muted) tr("Notifications muted") else tr("Mute notifications"), ChText,
        ) {
        muted = !muted
        Toast.makeText(context, if (muted) "Notifications muted" else "Notifications on", Toast.LENGTH_SHORT).show()
        }
        HorizontalDivider(color = ChBorder)
        InfoActionRow(Icons.Default.Link, tr("Copy channel link"), ChText) {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(AdisLinks.channel(channelId)))
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
        }
        if (!canManage) {
        HorizontalDivider(color = ChBorder)
        InfoActionRow(Icons.Default.Flag, tr("Report channel"), ChRed) {
        Toast.makeText(context, "Report submitted for review", Toast.LENGTH_SHORT).show()
        }
        }
        }
        }

        Spacer(Modifier.height(12.dp))
        if (canManage && com.adischat.app.FeatureFlags.FEATURE_WALLET_ENABLED) {
        Text(
        "Game pot",
        color = ChText,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        GamePotBannerHost(
        channelId = channelId,
        myUid     = myUid,
        isAdmin   = true,
        modifier  = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        ChannelGiftDashboardCard(
        balance = giftBalance,
        loading = giftLoading,
        converting = giftConverting,
        error = giftError,
        onRefresh = { loadGiftBalance() },
        onConvert = { exchangeCoins ->
        if (exchangeCoins >= 100 && !giftConverting) {
        giftConverting = true
        giftError = null
        scope.launch {
        runCatching {
        com.adischat.app.network.CloudflareClient.walletApi.convertChannelGifts(
        channelId,
        com.adischat.app.network.ChannelGiftConvertBody(coins = exchangeCoins),
        )
        }.onSuccess {
        Toast.makeText(context, "Gift coins exchanged to wallet", Toast.LENGTH_LONG).show()
        giftConverting = false
        loadGiftBalance()
        }.onFailure {
        giftError = CloudflareClient.friendlyErrorMessage(it, "Could not exchange gift coins")
        giftConverting = false
        }
        }
        }
        },
        )
        Spacer(Modifier.height(12.dp))
        }
        }
    }
}

@Composable
private fun ChannelGiftDashboardCard(
    balance:    com.adischat.app.network.ChannelGiftBalanceResponse?,
    loading:    Boolean,
    converting: Boolean,
    error:      String?,
    onRefresh:  () -> Unit,
    onConvert:  (Int) -> Unit,
) {
    val coins = balance?.coins ?: 0
    val etb = balance?.etbValue ?: coins / 10.0
    val converted = balance?.convertedCoins ?: 0
    var exchangeCoinsText by remember(coins) { mutableStateOf(if (coins >= 100) "100" else "") }
    val exchangeCoins = exchangeCoinsText.toIntOrNull() ?: 0
    val grossEtb = exchangeCoins / 10.0
    val commissionEtb = grossEtb * 0.25
    val ownerEtb = grossEtb - commissionEtb
    val canExchange = exchangeCoins >= 100 && exchangeCoins <= coins && !loading && !converting
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B1510),
        border = BorderStroke(1.dp, ChGreen.copy(alpha = 0.28f)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
        Modifier.size(44.dp).clip(CircleShape).background(
        Brush.linearGradient(listOf(ChGreen, ChEmerald)),
        ),
        contentAlignment = Alignment.Center,
        ) {
        Icon(Icons.Default.CardGiftcard, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Gift dashboard"), color = ChText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(tr("Coins from channel supporters"), color = ChTextSec, fontSize = 13.sp)
        }
        IconButton(onClick = onRefresh, enabled = !loading && !converting) {
        if (loading) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ChGreen)
        } else {
        Icon(Icons.Default.Refresh, "Refresh gifts", tint = ChTextSec)
        }
        }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GiftMetric("Available", "$coins", "coins", Modifier.weight(1f))
        GiftMetric("Value", "${formatMoney(etb)} ETB", "10 coins = 1 ETB", Modifier.weight(1f))
        }
        GiftMetric("Lifetime exchanged", "$converted coins", "Already converted to wallet", Modifier.fillMaxWidth())

        OutlinedTextField(
        value = exchangeCoinsText,
        onValueChange = { value ->
        exchangeCoinsText = value.filter { it.isDigit() }.take(7)
        },
        label = { Text(tr("Coins to exchange")) },
        singleLine = true,
        enabled = !loading && !converting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
        Text(tr("Minimum 100 coins. AdisChat commission is 25%."), color = ChTextSec)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = ChText,
        unfocusedTextColor = ChText,
        focusedBorderColor = ChGreen,
        unfocusedBorderColor = ChBorder,
        cursorColor = ChGreen,
        focusedLabelColor = ChGreen,
        unfocusedLabelColor = ChTextSec,
        ),
        )

        if (error != null) {
        Text(error, color = ChRed, fontSize = 13.sp, lineHeight = 18.sp)
        }

        Button(
        onClick = { onConvert(exchangeCoins) },
        enabled = canExchange,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
        containerColor = ChGreen,
        disabledContainerColor = ChTertiary,
        disabledContentColor = ChTextMuted,
        ),
        ) {
        if (converting) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(tr("Exchanging..."))
        } else {
        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
        when {
        coins < 100 -> "Need 100 coins to exchange"
        exchangeCoins > coins -> "Not enough gift coins"
        exchangeCoins < 100 -> "Minimum exchange is 100 coins"
        else -> "Exchange ${exchangeCoins} coins"
        }
        )
        }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GiftMetric("You receive", "${formatMoney(ownerEtb.coerceAtLeast(0.0))} ETB", "75% net", Modifier.weight(1f))
        GiftMetric("AdisChat fee", "${formatMoney(commissionEtb.coerceAtLeast(0.0))} ETB", "25% commission", Modifier.weight(1f))
        }
        }
    }
}

@Composable
private fun GiftMetric(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(Modifier.padding(12.dp)) {
        Text(title, color = ChTextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = ChText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = ChTextSec, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

// - Bottom attachment sheet for the channel composer -
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelAttachSheet(
    onDismiss:  () -> Unit,
    onPhoto:    () -> Unit,
    onCamera:   () -> Unit,
    onVideo:    () -> Unit,
    onDocument: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ChSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
        Text(tr("Attach"), color = ChText, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChannelAttachOption("Photo", Icons.Default.PhotoLibrary, Color(0xFF2684FF), Modifier.weight(1f), onPhoto)
        ChannelAttachOption("Camera", Icons.Default.PhotoCamera, Color(0xFF18C964), Modifier.weight(1f), onCamera)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChannelAttachOption("Video", Icons.Default.Videocam, Color(0xFFFF8A1F), Modifier.weight(1f), onVideo)
        ChannelAttachOption("Document", Icons.Default.Description, Color(0xFFEF4444), Modifier.weight(1f), onDocument)
        }
        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChannelAttachOption(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    tint:     Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .background(ChTertiary).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp)) }
        Text(label, color = ChText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// - Post composer dialog -

@Composable
fun ChannelPostComposer(
    vm:      ChannelViewModel,
    existingPost: ChannelPost? = null,
    onDismiss:    () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isEditing = existingPost != null

    var postType      by remember { mutableStateOf(existingPost?.postType ?: "text") }
    var title      by remember { mutableStateOf(existingPost?.title ?: "") }
    var body      by remember { mutableStateOf(existingPost?.body ?: "") }
    var linkUrl       by remember { mutableStateOf(existingPost?.linkUrl ?: "") }
    var linkTitle     by remember { mutableStateOf(existingPost?.linkTitle ?: "") }
    var mediaUrl      by remember { mutableStateOf(existingPost?.mediaUrl ?: "") }
    var mediaMimeType by remember { mutableStateOf(existingPost?.mediaMimeType ?: "") }

    val context = LocalContext.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
    }
    // Where the next finished upload should land: "media" (image/video) or "document".
    var uploadTarget by remember { mutableStateOf("media") }
    var docName      by remember { mutableStateOf("") }

    LaunchedEffect(state.mediaUploadUrl) {
        state.mediaUploadUrl?.let { url ->
        if (uploadTarget == "document") {
        linkUrl   = url
        linkTitle = docName.ifBlank { "Document" }
        postType  = "link"
        } else {
        mediaUrl = url
        mediaMimeType = if (postType == "video") "video/mp4" else "image/jpeg"
        }
        vm.clearMediaUpload()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { uploadTarget = "media"; postType = "image"; vm.uploadMedia(uri) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { uploadTarget = "media"; postType = "video"; vm.uploadMedia(uri) }
    }
    // Camera capture - JPEG temp file - upload as image
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
        val f = File.createTempFile("post_cam_", ".jpg", context.cacheDir)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        uploadTarget = "media"; postType = "image"; vm.uploadMedia(Uri.fromFile(f))
        }
    }
    // Document / file - upload, then post as a link to the file
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
        docName = channelQueryFileName(context, uri)
        uploadTarget = "document"; vm.uploadMedia(uri)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
        shape    = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        ) {
        Column(Modifier.padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        if (isEditing) "Edit Post" else "New Post",
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss") }
        }

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        if (!isEditing) {
        Text(
        "Add to your post",
        color = ChTextSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        ComposerAttachTile("Text", Icons.Default.Subject, Color(0xFF8B5CF6), postType == "text", Modifier.weight(1f)) {
        postType = "text"
        }
        ComposerAttachTile("Photo", Icons.Default.PhotoLibrary, Color(0xFF2684FF), postType == "image" && uploadTarget == "media", Modifier.weight(1f)) {
        imagePicker.launch("image/*")
        }
        ComposerAttachTile("Camera", Icons.Default.PhotoCamera, Color(0xFF18C964), false, Modifier.weight(1f)) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        cameraLauncher.launch(null)
        } else {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        }
        }
        Spacer(Modifier.height(10.dp))
        Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        ComposerAttachTile("Video", Icons.Default.Videocam, Color(0xFFFF8A1F), postType == "video", Modifier.weight(1f)) {
        videoPicker.launch("video/*")
        }
        ComposerAttachTile("Document", Icons.Default.Description, Color(0xFFEF4444), uploadTarget == "document" && linkUrl.isNotBlank(), Modifier.weight(1f)) {
        documentPicker.launch("*/*")
        }
        ComposerAttachTile("Link", Icons.Default.Link, Color(0xFF06B6D4), postType == "link" && uploadTarget != "document", Modifier.weight(1f)) {
        uploadTarget = "media"; postType = "link"
        }
        }
        Spacer(Modifier.height(14.dp))
        }

        Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        OutlinedTextField(
        value = title, onValueChange = { title = it },
        label = { Text(tr("Title (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
        value = body, onValueChange = { body = it },
        label = { Text(tr("Message")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), maxLines = 8,
        )

        if (postType == "image" || postType == "video") {
        if (state.mediaUploading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp)); Text(tr("Uploading-"), fontSize = 16.sp)
        }
        } else if (mediaUrl.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(tr("Media ready"), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { mediaUrl = ""; mediaMimeType = "" }) { Text(tr("Remove")) }
        }
        } else {
        OutlinedButton(
        onClick = { if (postType == "video") videoPicker.launch("video/*") else imagePicker.launch("image/*") },
        modifier = Modifier.fillMaxWidth(),
        ) {
        Icon(if (postType == "video") Icons.Default.VideoFile else Icons.Default.Image, null)
        Spacer(Modifier.width(8.dp))
        Text(if (postType == "video") "Pick video" else "Pick image")
        }
        }
        state.mediaUploadError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
        }

        if (uploadTarget == "document" && state.mediaUploading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(tr("Uploading ") + docName.ifBlank { "document" } + "-", fontSize = 16.sp)
        }
        }

        if (postType == "link") {
        OutlinedTextField(
        value = linkUrl, onValueChange = { linkUrl = it },
        label = { Text(tr("URL")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
        value = linkTitle, onValueChange = { linkTitle = it },
        label = { Text(tr("Link title (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
        Spacer(Modifier.width(8.dp))
        Button(
        onClick = {
        if (isEditing) {
        vm.editPost(
        postId = existingPost!!.id,
        title = title.ifBlank { null }, body = body.ifBlank { null },
        linkUrl = linkUrl.ifBlank { null }, linkTitle = linkTitle.ifBlank { null },
        onSuccess = onDismiss,
        )
        } else {
        vm.createPost(
        title = title, body = body, postType = postType,
        mediaUrl = mediaUrl, mediaMimeType = mediaMimeType,
        linkUrl = linkUrl.ifBlank { "" }, linkTitle = linkTitle.ifBlank { "" },
        onSuccess = onDismiss,
        )
        }
        },
        enabled = body.isNotBlank() && !state.actionLoading && !state.mediaUploading,
        ) {
        if (state.actionLoading) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
        Text(if (isEditing) "Save" else "Publish")
        }
        }
        }
        }
        }
    }
}

// - Composer attachment tile -

@Composable
private fun ComposerAttachTile(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    tint:     Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    Column(
        modifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (selected) tint.copy(alpha = 0.16f) else ChTertiary)
        .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) tint else ChBorder,
        shape = RoundedCornerShape(16.dp),
        )
        .clickable(onClick = onClick)
        .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
        ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = ChText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Best-effort display name for a picked document Uri. */
private fun channelQueryFileName(context: android.content.Context, uri: Uri): String {
    var name = "Document"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) {
        c.getString(idx)?.takeIf { it.isNotBlank() }?.let { name = it }
        }
        }
    }
    return name
}

// - Channel edit dialog -

@Composable
private fun ChannelEditDialog(
    initialName:      String,
    initialDescription: String,
    initialPhotoUrl:    String,
    onUploadPhoto:      (Uri, (String?) -> Unit) -> Unit,
    onDismiss:      () -> Unit,
    onSave:      (name: String, desc: String, photoUrl: String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

    var name      by remember(initialName)      { mutableStateOf(initialName) }
    var desc      by remember(initialDescription) { mutableStateOf(initialDescription) }
    var photo       by remember(initialPhotoUrl)    { mutableStateOf(initialPhotoUrl) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploading = true; uploadError = null
        onUploadPhoto(uri) { newUrl ->
        isUploading = false
        if (newUrl != null) photo = newUrl else uploadError = "Upload failed. Try again."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text(tr("Edit Channel")) },
        text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
        modifier = Modifier
        .size(88.dp).clip(CircleShape)
        .background(accent.copy(alpha = 0.12f))
        .border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
        .clickable(enabled = !isUploading) { photoPicker.launch("image/*") },
        contentAlignment = Alignment.Center,
        ) {
        if (photo.isNotBlank()) {
        AsyncImage(model = photo, contentDescription = "Photo", contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
        Icon(Icons.Default.CameraAlt, "Pick photo", tint = accent, modifier = Modifier.size(32.dp))
        }
        if (isUploading) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
        }
        } else {
        Box(
        modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)
        .background(accent, CircleShape)
        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        }
        }

        uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }

        OutlinedTextField(value = name, onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(), label = { Text(tr("Name")) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(value = desc, onValueChange = { desc = it },
        modifier = Modifier.fillMaxWidth(), label = { Text(tr("Description")) }, minLines = 3, shape = RoundedCornerShape(14.dp))
        }
        },
        confirmButton = {
        Button(enabled = name.trim().length >= 2 && !isUploading, onClick = { onSave(name.trim(), desc.trim(), photo) }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isUploading) { Text(tr("Cancel")) } },
    )
}

// - Local voice recorder for the channel composer -
private class ChannelVoiceRecorder(private val context: android.content.Context) {
    private var recorder: android.media.MediaRecorder? = null
    private var outputFile: java.io.File? = null
    private var startTimeMs = 0L

    var isRecording by mutableStateOf(false)
        private set

    fun start(): Boolean {
        if (isRecording) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        return false
        }
        val file = File.createTempFile("ch_voice_", ".m4a", context.cacheDir)
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
