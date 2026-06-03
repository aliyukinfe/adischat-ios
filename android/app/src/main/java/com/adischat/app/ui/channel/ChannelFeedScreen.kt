package com.adischat.app.ui.channel

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import coil.compose.AsyncImage
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.ChannelPost
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.gamepot.GamePotBannerHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ── Premium design tokens (mirrors channel.html) ──────────────────────────────
private val ChCanvas    = Color(0xFF0A0A0A)
private val ChDeep      = Color(0xFF000000)
private val ChSurface   = Color(0xFF111111)
private val ChTertiary  = Color(0xFF1A1A1A)
private val ChGreen     = Color(0xFF18C964)
private val ChEmerald   = Color(0xFF34D399)
private val ChText      = Color(0xFFFFFFFF)
private val ChTextSec   = Color(0xFFA0A0A8)
private val ChTextMuted = Color(0xFF606068)
private val ChBorder    = Color(0x14FFFFFF)   // white @ ~8%
private val ChGlass     = Color(0xD90C0C0C)   // glass header bg
private val ChGlow      = Color(0x2618C964)
private val ChRed       = Color(0xFFFF4444)
private val ChBlue      = Color(0xFF1DA1F2)   // verified blue badge

// ── Screen entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelFeedScreen(
    channelId:             String,
    channelName:           String,
    channelPhoto:          String  = "",
    channelDescription:    String  = "",
    subscriberCount:       Int     = 0,
    isAdmin:               Boolean = false,
    myUid:                 String  = "",
    onBack:                () -> Unit = {},
    onUpdateCommunity:     (name: String, desc: String, photoUrl: String) -> Unit = { _, _, _ -> },
    onUploadCommunityPhoto:(Uri, (String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteCommunity:     () -> Unit = {},
    vm: ChannelViewModel = viewModel(key = channelId),
) {
    LaunchedEffect(channelId) { vm.init(channelId) }

    val state        = vm.state.collectAsStateWithLifecycle().value
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
            // Older posts live at the top now, so load more when scrolled near the top.
            listState.firstVisibleItemIndex <= 1 && state.hasMore && !state.isLoadingMore
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
    var showTip          by remember { mutableStateOf(false) }
    var showInfo         by remember { mutableStateOf(false) }
    var searchActive     by remember { mutableStateOf(false) }
    var query            by remember { mutableStateOf("") }
    var forwardText      by remember { mutableStateOf<String?>(null) }
    var liveGamePinned   by remember { mutableStateOf(false) }
    // Subscribe is a local UI state — backend subscribe endpoint not available yet.
    var subscribed       by remember { mutableStateOf(true) }

    val pinnedPost   = state.posts.firstOrNull { it.pinned }
    // Chat-style ordering: oldest at top, newest at the bottom.
    val orderedPosts = remember(state.posts) { state.posts.sortedBy { it.createdAt } }
    val feedPosts    = if (query.isBlank()) orderedPosts
        else orderedPosts.filter {
            it.body.contains(query, true) || it.title.contains(query, true)
        }

    // Auto-scroll to the newest post when the feed first loads or grows (not while searching).
    LaunchedEffect(orderedPosts.size, query.isBlank()) {
        if (query.isBlank() && orderedPosts.isNotEmpty()) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
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
                    state          = listState,
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    // ── Cover + channel header ────────────────────────────────
                    item(key = "cover") {
                        ChannelCoverHeader(
                            name        = channelName,
                            photo       = channelPhoto,
                            description = channelDescription,
                            subscribers = subscriberCount,
                            verified    = channelVerified,
                        )
                    }

                    // ── Channel game banner (real data) ───────────────────────
                    // ── Pinned banner ─────────────────────────────────────────
                    // ── Wallet / tip section (real wallet backend) ────────────
                    // ── Load-older spinner (older posts load at the top) ──────
                    if (state.isLoadingMore) {
                        item(key = "loadmore") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = ChGreen)
                            }
                        }
                    }

                    // ── Empty state ───────────────────────────────────────────
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

                    // ── Post feed (oldest first, newest at the bottom) ────────
                    items(feedPosts, key = { it.id }) { post ->
                        ChannelPostCard(
                            post         = post,
                            channelName  = channelName,
                            channelPhoto = channelPhoto,
                            isAdmin      = isAdmin,
                            myUid        = myUid,
                            onReact   = { emoji -> vm.react(post.id, emoji) },
                            onView    = { vm.recordView(post.id) },
                            onPin     = { vm.togglePin(post.id) },
                            onEdit    = { editingPost = post },
                            onDelete  = { vm.deletePost(post.id) },
                            onShare   = {
                                forwardText = buildString {
                                    if (post.title.isNotBlank()) appendLine(post.title)
                                    append(post.body.ifBlank { post.title })
                                }.trim().ifBlank { "Shared a post from $channelName" }
                            },
                        )
                    }

                }
            }

            // ── Sticky glass top bar overlay ──────────────────────────────────
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
                    // Search field — filters posts in this channel
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
                            Text("Search posts…", color = ChTextMuted, fontSize = 15.sp)
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
                    // Channel identity — tap to open channel info
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
                                    Icon(Icons.Default.Verified, "Verified", tint = ChBlue, modifier = Modifier.size(16.dp))
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
                                text = { Text("Edit channel") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showOverflow = false; showEditDialog = true },
                            )
                            when (channelVerificationStatus) {
                                "approved" -> DropdownMenuItem(
                                    text = { Text("Verified channel") },
                                    leadingIcon = { Icon(Icons.Default.Verified, null, tint = ChBlue) },
                                    enabled = false,
                                    onClick = {},
                                )
                                "pending" -> DropdownMenuItem(
                                    text = { Text("Blue badge pending") },
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
                                text = { Text("Delete channel", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showOverflow = false; showDeleteDialog = true },
                            )
                        }
                    }
                }
            }

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

            pinnedPost?.let { pin ->
                FloatingPinnedBanner(
                    text = pin.body.ifBlank { pin.title }.ifBlank { "Pinned post" },
                    onClick = {
                        query = ""
                        val postIndex = orderedPosts.indexOfFirst { it.id == pin.id }
                        if (postIndex >= 0) {
                            screenScope.launch {
                                val loadMoreOffset = if (state.isLoadingMore) 1 else 0
                                listState.animateScrollToItem(1 + loadMoreOffset + postIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = if (liveGamePinned) 138.dp else 66.dp, start = 14.dp, end = 14.dp),
                )
            }

            ChannelGiftButton(
                onClick = { showTip = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = if (isAdmin) 86.dp else 22.dp),
            )
        }
    }

    // ── Edit post composer ────────────────────────────────────────────────────
    editingPost?.let { post ->
        ChannelPostComposer(
            vm           = vm,
            existingPost = post,
            onDismiss    = { editingPost = null },
        )
    }

    // ── Tip dialog ────────────────────────────────────────────────────────────
    if (showTip) {
        ChannelGiftDialog(
            channelId = channelId,
            channelName = channelName,
            onDismiss   = { showTip = false },
        )
    }

    // ── Request blue badge (admin) ────────────────────────────────────────────
    if (showBadgeRequest) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBadgeRequest = false },
            icon  = { Icon(Icons.Default.Verified, null, tint = ChBlue) },
            title = { Text("Request blue badge") },
            text  = {
                Column {
                    Text("Submit this channel for verification. An admin will review and approve the blue badge.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Why should this channel be verified? (optional)") },
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
                }) { Text("Send request") }
            },
            dismissButton = { TextButton(onClick = { showBadgeRequest = false }) { Text("Cancel") } },
        )
    }

    // ── Channel info sheet ──────────────────────────────────────────────────
    if (showInfo) {
        ChannelInfoSheet(
            channelId   = channelId,
            name        = channelName,
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

    // ── In-app forward sheet ──────────────────────────────────────────────────
    forwardText?.let { text ->
        ForwardSheet(
            messageText = text,
            onDismiss   = { forwardText = null },
        )
    }

    // ── Edit channel dialog ───────────────────────────────────────────────────
    if (showEditDialog) {
        ChannelEditDialog(
            initialName        = channelName,
            initialDescription = channelDescription,
            initialPhotoUrl    = channelPhoto,
            onUploadPhoto      = onUploadCommunityPhoto,
            onDismiss          = { showEditDialog = false },
            onSave             = { name, desc, photo ->
                onUpdateCommunity(name, desc, photo)
                showEditDialog = false
            },
        )
    }

    // ── Delete channel confirmation ───────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon    = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete channel?") },
            text    = { Text("This removes the channel and all its posts for everyone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDeleteCommunity() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Cover + header block ───────────────────────────────────────────────────────

@Composable
private fun ChannelCoverHeader(
    name: String,
    photo: String,
    description: String,
    subscribers: Int,
    verified: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().background(ChDeep)) {
        // Cover image with gradient fade into the canvas
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            if (photo.isNotBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF15B85A), Color(0xFF0E7A3D)))
                    )
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x4D000000),
                        0.6f to Color.Transparent,
                        1f to ChDeep,
                    )
                )
            )
        }

        // Avatar + name + verified + subscribers + description
        Column(
            Modifier.fillMaxWidth().offset(y = (-32).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, ChDeep, CircleShape)
                    .background(ChTertiary),
                contentAlignment = Alignment.Center,
            ) { ChannelAvatar(photo = photo, name = name, size = 66.dp) }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = ChText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (verified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, "Verified", tint = ChBlue, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("${formatCount(subscribers)} subscribers",
                color = ChTextSec, fontSize = 16.sp)

            if (description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    description,
                    color = ChTextSec,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
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

// ── Action buttons ─────────────────────────────────────────────────────────────

// ── Pinned banner ──────────────────────────────────────────────────────────────

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
            Text("PINNED", color = ChGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(text, color = ChTextSec, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Wallet / tip section ───────────────────────────────────────────────────────

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
            Text("PINNED", color = ChGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text("AdisPay", color = ChText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Support this channel with a tip", color = ChTextMuted, fontSize = 15.sp)
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
                Text("Send Tip", color = ChDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Tip dialog (reuses real wallet send) ────────────────────────────────────────

@Composable
private fun ChannelGiftDialog(channelId: String, channelName: String, onDismiss: () -> Unit) {
    val scope      = rememberCoroutineScope()
    val context    = LocalContext.current

    var coins     by remember { mutableStateOf("10") }
    var emoji     by remember { mutableStateOf("🎁") }
    var pin       by remember { mutableStateOf("") }
    var sending   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    val coinValue = coins.toIntOrNull() ?: 0
    val etbValue = coinValue / 10.0
    val canSend = !sending && coinValue >= 10 && pin.length >= 4

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon  = { Icon(Icons.Default.CardGiftcard, null, tint = ChGreen) },
        title = { Text("Send a gift", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$channelName receives gifts as coins and emojis, not direct money.", color = ChTextSec, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🎁", "💚", "🔥", "👏").forEach { item ->
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
                    label = { Text("Coins") }, singleLine = true, enabled = !sending,
                    supportingText = { Text("Minimum 10 coins. 10 coins = 1 ETB. This gift is worth ETB ${"%.2f".format(etbValue)}.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { new -> pin = new.filter { it.isDigit() }.take(6) },
                    label = { Text("Wallet PIN") }, singleLine = true, enabled = !sending,
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
                else Text("Send Gift", color = ChGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Post card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChannelPostCard(
    post:         ChannelPost,
    channelName:  String,
    channelPhoto: String,
    isAdmin:      Boolean,
    myUid:        String,
    onReact:  (String) -> Unit,
    onView:   () -> Unit,
    onPin:    () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit,
    onShare:  () -> Unit,
) {
    var showMenu      by remember { mutableStateOf(false) }
    var showContext   by remember { mutableStateOf(false) }
    val clipboard     = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx           = LocalContext.current

    // Optimistic reactions — update instantly, sync when the server pushes new data.
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = ChGreen.copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF0F0F11)))
            )
            .border(1.dp, ChBorder, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = {}, onLongClick = { showContext = true }),
    ) {
        // ── Long-press context menu: Forward / Copy ───────────────────────────
        Box {
            DropdownMenu(expanded = showContext, onDismissRequest = { showContext = false }) {
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    onClick = { showContext = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        showContext = false
                        val text = buildString {
                            if (post.title.isNotBlank()) appendLine(post.title)
                            append(post.body.ifBlank { post.linkUrl })
                        }.trim()
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(photo = channelPhoto, name = channelName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channelName.ifBlank { "Channel" }, color = ChText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isAdmin) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(ChGlow).padding(horizontal = 7.dp, vertical = 2.dp),
                        ) { Text("ADMIN", color = ChGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(formatPostTime(post.createdAt), color = ChTextMuted, fontSize = 13.sp, letterSpacing = 0.1.sp)
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
                            text = { Text("Edit") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            }
        }

        // ── Title ───────────────────────────────────────────────────────────
        if (post.title.isNotBlank()) {
            Text(post.title, color = ChText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 28.sp, letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
        }

        // ── Body (comfortable 1.5 line height) ────────────────────────────────
        if (post.body.isNotBlank()) {
            Text(post.body, color = ChText.copy(alpha = 0.92f), fontSize = 16.sp, lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (post.title.isBlank()) 14.dp else 6.dp, bottom = 4.dp))
        }

        // ── Media (image / video) ─────────────────────────────────────────────
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

        // ── Link preview ──────────────────────────────────────────────────────
        if (post.linkUrl.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 14.dp)) {
                PremiumLinkPreview(url = post.linkUrl, title = post.linkTitle, image = post.linkImage)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ChBorder, modifier = Modifier.padding(horizontal = 16.dp))

        // ── All existing reactions (every emoji + count) ──────────────────────
        if (counts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                counts.entries.sortedByDescending { it.value }.forEach { (emoji, count) ->
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
            }
        }

        // ── Quick react bar (tap an emoji to react instantly) ─────────────────
        LazyRow(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(listOf("❤️", "🔥", "👏", "😂", "😮", "👍", "🎉", "💯")) { emoji ->
                val active = myReaction == emoji
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) ChGlow else Color.Transparent)
                        .clickable { react(emoji) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) { Text(emoji, fontSize = 24.sp) }
            }
        }

        // ── Footer: views + share ─────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Visibility, null, tint = ChTextMuted, modifier = Modifier.size(16.dp))
                Text(formatCount(post.views), color = ChTextMuted, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onShare).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Default.Share, "Share", tint = ChTextMuted, modifier = Modifier.size(16.dp))
                Text("Share", color = ChTextMuted, fontSize = 14.sp)
            }
        }
    }
}

// ── Premium link preview ────────────────────────────────────────────────────────

@Composable
private fun PremiumLinkPreview(url: String, title: String, image: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChTertiary)
            .border(1.dp, ChBorder, RoundedCornerShape(12.dp)),
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

// ── In-app forward sheet (chats / channels / groups only) ──────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(messageText: String, onDismiss: () -> Unit) {
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
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        // Guard every fetch with a timeout so the sheet never hangs on a flow that doesn't emit.
        me      = runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { authRepo.getCurrentAppUser() } }.getOrNull()
        directs = runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeAllConversations().first() } }.getOrNull() ?: emptyList()
        chans   = runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeCommunities("channel", "").first() } }.getOrNull() ?: emptyList()
        grps    = runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { chatRepo.observeCommunities("group", "").first() } }.getOrNull() ?: emptyList()
        loading = false
    }

    fun forward() {
        if (selected.isEmpty() || sending) return
        sending = true
        scope.launch {
            selected.toList().forEach { id ->
                when {
                    chans.any { it.id == id } || grps.any { it.id == id } ->
                        chatRepo.sendCommunityMessage(id, messageText)
                    else -> me?.let { u ->
                        chatRepo.sendMessage(id, u.name, u.photoUrl, u.uid, u.role, messageText)
                    }
                }
            }
            sending = false
            Toast.makeText(ctx, "Forwarded to ${selected.size} chat(s)", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ChSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
            Text("Forward to", color = ChText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Share inside AdisChat only", color = ChTextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ChGreen, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    forwardSection("Chats", directs.map { ForwardTarget(it.id, it.otherUserName.ifBlank { "Chat" }, it.otherUserPhotoUrl) }, selected)
                    forwardSection("Channels", chans.map { ForwardTarget(it.id, it.name, it.photoUrl) }, selected)
                    forwardSection("Groups", grps.map { ForwardTarget(it.id, it.name, it.photoUrl) }, selected)
                    if (directs.isEmpty() && chans.isEmpty() && grps.isEmpty()) {
                        item { Text("No chats yet", color = ChTextMuted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { forward() },
                    enabled = selected.isNotEmpty() && !sending,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChGreen, contentColor = ChDeep),
                ) {
                    if (sending) CircularProgressIndicator(color = ChDeep, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else Text(if (selected.isEmpty()) "Select chats" else "Send to ${selected.size}",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

private data class ForwardTarget(val id: String, val name: String, val photo: String)

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
                .clip(RoundedCornerShape(12.dp))
                .clickable { if (checked) selected.remove(t.id) else selected.add(t.id) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(photo = t.photo, name = t.name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(t.name, color = ChText, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (checked) ChGreen else ChTextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** 128400 -> "128.4K", 2_400_000 -> "2.4M". */
private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).removeSuffix(".0M") + if (n % 1_000_000 == 0) "M" else ""
    n >= 1_000     -> String.format(Locale.US, "%.1fK", n / 1_000.0).let { if (it.endsWith(".0K")) it.removeSuffix(".0K") + "K" else it }
    else           -> n.toString()
}

private fun formatPostTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val now  = System.currentTimeMillis()
    val diff = now - epochMs
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
    val day = when {
        diff < 60_000L         -> "Just now"
        diff < 3_600_000L      -> "${diff / 60_000L}m ago"
        diff < 86_400_000L     -> "Today"
        diff < 2 * 86_400_000L -> "Yesterday"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else                   -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
    return if (day == "Just now") day else "$day • $time"
}

// ── Chat-style bottom composer bar (admin) ────────────────────────────────────
// Persistent Telegram/WhatsApp-style input bar pinned to the bottom of the feed.
// Posts text, photo, video, document and voice — all through the existing
// ChannelViewModel.createPost / uploadMedia pipeline (no backend changes).
@Composable
private fun ChannelComposerBar(vm: ChannelViewModel) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                    linkUrl = url, linkTitle = "🎤 Voice message • ${secs}s")
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
            onCamera   = { showAttach = false; cameraLauncher.launch(null) },
            onVideo    = { showAttach = false; videoPicker.launch("video/*") },
            onDocument = { showAttach = false; documentPicker.launch("*/*") },
        )
    }

    val busy = state.mediaUploading || state.actionLoading

    Surface(
        color          = ChSurface,
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
                        "Publishing " + (pendingKind ?: "") + (if (docName.isNotBlank()) " · $docName" else "") + "…",
                        color = ChTextSec, fontSize = 13.sp,
                    )
                }
            }
            state.mediaUploadError?.let {
                Text(it, color = ChRed, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            if (isRecording) {
                // ── Recording state ──────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ChRed))
                    Spacer(Modifier.width(10.dp))
                    Text("Recording voice…", color = ChText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { voiceRecorder.cancel() }) {
                        Text("Cancel", color = ChTextSec)
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
                // ── Normal state ─────────────────────────────────────────────
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
                            Text("Broadcast a message…", color = ChTextMuted, fontSize = 15.sp)
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
                                    voiceRecorder.start()
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

// ── Channel info bottom sheet ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelInfoSheet(
    channelId:    String,
    name:        String,
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
        if (!canManage || giftLoading) return
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
        if (canManage) loadGiftBalance()
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
                    Icon(Icons.Default.Verified, "Verified", tint = ChBlue, modifier = Modifier.size(20.dp))
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
            Spacer(Modifier.height(12.dp))
            if (canManage) {
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
                    Text("Gift dashboard", color = ChText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Coins from channel supporters", color = ChTextSec, fontSize = 13.sp)
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
                label = { Text("Coins to exchange") },
                singleLine = true,
                enabled = !loading && !converting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text("Minimum 100 coins. AdisChat commission is 25%.", color = ChTextSec)
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
                    Text("Exchanging...")
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

// ── Bottom attachment sheet for the channel composer ──────────────────────────
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
            Text("Attach", color = ChText, fontWeight = FontWeight.Bold, fontSize = 16.sp,
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

// ── Post composer dialog ──────────────────────────────────────────────────────

@Composable
fun ChannelPostComposer(
    vm:           ChannelViewModel,
    existingPost: ChannelPost? = null,
    onDismiss:    () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isEditing = existingPost != null

    var postType      by remember { mutableStateOf(existingPost?.postType ?: "text") }
    var title         by remember { mutableStateOf(existingPost?.title ?: "") }
    var body          by remember { mutableStateOf(existingPost?.body ?: "") }
    var linkUrl       by remember { mutableStateOf(existingPost?.linkUrl ?: "") }
    var linkTitle     by remember { mutableStateOf(existingPost?.linkTitle ?: "") }
    var mediaUrl      by remember { mutableStateOf(existingPost?.mediaUrl ?: "") }
    var mediaMimeType by remember { mutableStateOf(existingPost?.mediaMimeType ?: "") }

    val context = LocalContext.current
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
    // Camera capture → JPEG temp file → upload as image
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val f = File.createTempFile("post_cam_", ".jpg", context.cacheDir)
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            uploadTarget = "media"; postType = "image"; vm.uploadMedia(Uri.fromFile(f))
        }
    }
    // Document / file → upload, then post as a link to the file
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
                            cameraLauncher.launch(null)
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
                        label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(
                        value = body, onValueChange = { body = it },
                        label = { Text("Message") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), maxLines = 8,
                    )

                    if (postType == "image" || postType == "video") {
                        if (state.mediaUploading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp)); Text("Uploading…", fontSize = 16.sp)
                            }
                        } else if (mediaUrl.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Media ready", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { mediaUrl = ""; mediaMimeType = "" }) { Text("Remove") }
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
                            Text("Uploading " + docName.ifBlank { "document" } + "…", fontSize = 16.sp)
                        }
                    }

                    if (postType == "link") {
                        OutlinedTextField(
                            value = linkUrl, onValueChange = { linkUrl = it },
                            label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        OutlinedTextField(
                            value = linkTitle, onValueChange = { linkTitle = it },
                            label = { Text("Link title (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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

// ── Composer attachment tile ──────────────────────────────────────────────────

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

// ── Channel edit dialog ───────────────────────────────────────────────────────

@Composable
private fun ChannelEditDialog(
    initialName:        String,
    initialDescription: String,
    initialPhotoUrl:    String,
    onUploadPhoto:      (Uri, (String?) -> Unit) -> Unit,
    onDismiss:          () -> Unit,
    onSave:             (name: String, desc: String, photoUrl: String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

    var name        by remember(initialName)        { mutableStateOf(initialName) }
    var desc        by remember(initialDescription) { mutableStateOf(initialDescription) }
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
        title = { Text("Edit Channel") },
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
                    modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            Button(enabled = name.trim().length >= 2 && !isUploading, onClick = { onSave(name.trim(), desc.trim(), photo) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") } },
    )
}

// ── Local voice recorder for the channel composer ─────────────────────────────
private class ChannelVoiceRecorder(private val context: android.content.Context) {
    private var recorder: android.media.MediaRecorder? = null
    private var outputFile: java.io.File? = null
    private var startTimeMs = 0L

    var isRecording by mutableStateOf(false)
        private set

    fun start() {
        if (isRecording) return
        val file = File.createTempFile("ch_voice_", ".m4a", context.cacheDir)
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
