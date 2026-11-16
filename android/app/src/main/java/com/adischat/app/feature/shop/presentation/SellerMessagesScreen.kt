package com.adischat.app.feature.shop.presentation

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.adischat.app.feature.shop.data.ShopConversationDto
import com.adischat.app.feature.shop.data.ShopMessageDto
import com.adischat.app.feature.shop.data.ShopProductContextDto
import com.adischat.app.feature.shop.data.ShopProtectedState
import com.adischat.app.BuildConfig
import com.adischat.app.ui.theme.AdisGebeyaColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class SellerMessageActions(
    val load: (String, Boolean) -> Unit,
    val open: (String) -> Unit,
    val refresh: (String) -> Unit,
    val close: () -> Unit,
    val send: (String, String) -> Unit,
    val upload: (String, ByteArray, String, String) -> Unit,
    val update: (String, String) -> Unit,
    val typing: (String, Boolean) -> Unit,
    val openProduct: (String) -> Unit,
)

@Composable
internal fun SellerMessagesPage(
    state: ShopProtectedState,
    onLoad: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onRefreshConversation: (String) -> Unit,
    onSend: (String, String) -> Unit,
    onUpload: (String, ByteArray, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onTyping: (String, Boolean) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val selected = state.selectedConversation
    if (selected != null) {
        ShopConversationDetail(
            conversation = selected,
            messages = state.conversationMessages,
            sellerView = true,
            sending = state.busyIds.contains("send-${selected.id}") ||
                state.busyIds.contains("attachment-${selected.id}"),
            onBack = onClose,
            onRefresh = { onRefreshConversation(selected.id) },
            onSend = { onSend(selected.id, it) },
            onUpload = { bytes, mimeType, fileName -> onUpload(selected.id, bytes, mimeType, fileName) },
            onAction = { onAction(selected.id, it) },
            onTyping = { typing -> onTyping(selected.id, typing) },
            onOpenProduct = onOpenProduct,
        )
        return
    }

    var search by remember { mutableStateOf("") }
    var archived by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Messages", color = AdisGebeyaColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Customer, product and order conversations", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
            }
            if (state.sellerMessageUnreadCount > 0) {
                Badge(containerColor = AdisGebeyaColors.AccentOrange) {
                    Text(state.sellerMessageUnreadCount.coerceAtMost(99).toString())
                }
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                onLoad(it, archived)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search conversations") },
            shape = RoundedCornerShape(16.dp),
            colors = sellerMessageTextFieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !archived,
                onClick = {
                    archived = false
                    onLoad(search, false)
                },
                label = { Text("Inbox") },
            )
            FilterChip(
                selected = archived,
                onClick = {
                    archived = true
                    onLoad(search, true)
                },
                label = { Text("Archived") },
            )
        }
        if (state.sellerConversations.isEmpty()) {
            PremiumEmptyState(
                title = if (archived) "No archived conversations" else "No conversations yet",
                message = if (archived) {
                    "Conversations you archive will remain available here."
                } else {
                    "Customer questions about your products and orders will appear here."
                },
                icon = Icons.Default.ChatBubbleOutline,
            )
        } else {
            state.sellerConversations.forEach { conversation ->
                SellerConversationRow(conversation = conversation, onOpen = { onOpen(conversation.id) })
            }
        }
    }
}

@Composable
private fun SellerConversationRow(conversation: ShopConversationDto, onOpen: () -> Unit) {
    val customerName = conversation.customer?.displayName?.takeIf { it.isNotBlank() } ?: "Customer"
    val contextLabel = when (conversation.contextType) {
        "product" -> "Product question"
        "order" -> "Order conversation"
        else -> "Store conversation"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(AdisGebeyaColors.PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(customerName.take(1).uppercase(), color = AdisGebeyaColors.PrimaryDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(customerName, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                    Text(formatMarketplaceRelativeTime(conversation.lastMessageAt), color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp)
                }
                Text(
                    conversation.lastMessagePreview ?: "Conversation started",
                    color = AdisGebeyaColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
                Text(contextLabel, color = AdisGebeyaColors.Primary, fontSize = 11.sp)
            }
            if (conversation.unreadCount > 0) {
                Spacer(Modifier.width(8.dp))
                Badge(containerColor = AdisGebeyaColors.AccentOrange) {
                    Text(conversation.unreadCount.coerceAtMost(99).toString())
                }
            }
        }
    }
}

@Composable
internal fun BuyerConversationPage(
    state: ShopProtectedState,
    onBack: () -> Unit,
    onRefresh: (String) -> Unit,
    onSend: (String, String) -> Unit,
    onUpload: (String, ByteArray, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onTyping: (String, Boolean) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val conversation: ShopConversationDto? = state.selectedConversation
    if (conversation == null) {
        BuyerConversationUnavailable(
            error = state.error,
            onBack = onBack,
        )
    } else {
        BuyerConversationLoaded(
            conversation = conversation,
            messages = state.conversationMessages,
            sending = state.busyIds.contains("send-${conversation.id}") ||
                state.busyIds.contains("attachment-${conversation.id}"),
            onBack = onBack,
            onRefresh = onRefresh,
            onSend = onSend,
            onUpload = onUpload,
            onAction = onAction,
            onTyping = onTyping,
            onOpenProduct = onOpenProduct,
        )
    }
}

@Composable
private fun BuyerConversationUnavailable(
    error: String?,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            error ?: "Opening conversation...",
            color = if (error == null) AdisGebeyaColors.PrimaryText else AdisGebeyaColors.Error,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun BuyerConversationLoaded(
    conversation: ShopConversationDto,
    messages: List<ShopMessageDto>,
    sending: Boolean,
    onBack: () -> Unit,
    onRefresh: (String) -> Unit,
    onSend: (String, String) -> Unit,
    onUpload: (String, ByteArray, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onTyping: (String, Boolean) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val conversationId: String = conversation.id
    Box(Modifier.fillMaxSize().background(AdisGebeyaColors.Background).statusBarsPadding().padding(16.dp)) {
        ShopConversationDetail(
            conversation = conversation,
            messages = messages,
            sellerView = false,
            sending = sending,
            onBack = onBack,
            onRefresh = { onRefresh(conversationId) },
            onSend = { message: String -> onSend(conversationId, message) },
            onUpload = { bytes: ByteArray, mimeType: String, fileName: String ->
                onUpload(conversationId, bytes, mimeType, fileName)
            },
            onAction = { action: String ->
                onAction(conversationId, action)
                if (action == "archive" || action == "delete") onBack()
            },
            onTyping = { typing: Boolean -> onTyping(conversationId, typing) },
            onOpenProduct = onOpenProduct,
        )
    }
}

@Composable
private fun ShopConversationDetail(
    conversation: ShopConversationDto,
    messages: List<ShopMessageDto>,
    sellerView: Boolean,
    sending: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSend: (String) -> Unit,
    onUpload: (ByteArray, String, String) -> Unit,
    onAction: (String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onOpenProduct: ((String) -> Unit)? = null,
) {
    var showDeleteConfirmation by remember(conversation.id) { mutableStateOf(false) }
    var showOverflow by remember(conversation.id) { mutableStateOf(false) }
    val muted = conversation.participantState?.mutedUntil != null
    val archived = conversation.participantState?.archivedAt != null
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var positionedConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(conversation.id, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            onRefresh()
            while (true) {
                delay(ShopMessageRefreshPolicy.INTERVAL_MS)
                onRefresh()
            }
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            val initialPosition = positionedConversationId != conversation.id
            val layout = listState.layoutInfo
            val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val wasNearBottom = layout.totalItemsCount == 0 || lastVisibleIndex >= layout.totalItemsCount - 3
            delay(1)
            val lastItem = listState.layoutInfo.totalItemsCount - 1
            if (lastItem >= 0 && (initialPosition || wasNearBottom)) {
                listState.scrollToItem(lastItem)
                positionedConversationId = conversation.id
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color.White).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to conversations")
            }
            val peerName = if (sellerView) conversation.customer?.displayName ?: "Customer" else conversation.store?.name ?: "Seller"
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE9E7FF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(peerName.take(1).uppercase(), color = Color(0xFF1E1B4B), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    peerName,
                    color = AdisGebeyaColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                if (conversation.peerTyping) {
                    Text("Typing...", color = AdisGebeyaColors.Primary, fontSize = 12.sp)
                }
                Text(
                    when (conversation.contextType) {
                        "product" -> "Product chat"
                        "order" -> "Order chat"
                        else -> conversation.store?.name ?: "Store chat"
                    },
                    color = AdisGebeyaColors.SecondaryText,
                    fontSize = 12.sp,
                )
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Conversation options")
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text(if (muted) "Unmute notifications" else "Mute notifications") },
                        onClick = {
                            showOverflow = false
                            onAction(if (muted) "unmute" else "mute")
                        },
                        leadingIcon = { Icon(if (muted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (archived) "Move to inbox" else "Archive") },
                        onClick = {
                            showOverflow = false
                            onAction(if (archived) "unarchive" else "archive")
                        },
                        leadingIcon = { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete conversation", color = AdisGebeyaColors.Error) },
                        onClick = {
                            showOverflow = false
                            showDeleteConfirmation = true
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = AdisGebeyaColors.Error) },
                    )
                }
            }
        }
        conversation.productContext?.let { context ->
            ProductConversationContextCard(
                context = context,
                onOpenProduct = onOpenProduct,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    PremiumEmptyState(
                        title = "Start the conversation",
                        message = "Messages and attachments shared in this conversation will appear here.",
                        icon = Icons.Default.ChatBubbleOutline,
                    )
                }
            } else {
                val visibleMessages = messages.takeLast(50)
                visibleMessages.forEachIndexed { index, message ->
                    val previous = visibleMessages.getOrNull(index - 1)
                    if (shouldShowMarketplaceDateSeparator(previous?.createdAt, message.createdAt)) {
                        item(key = "date-${message.id}") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f).height(1.dp).background(AdisGebeyaColors.Divider))
                                Text(
                                    formatMarketplaceDateSeparator(message.createdAt),
                                    color = Color(0xFF4C4878),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Box(Modifier.weight(1f).height(1.dp).background(AdisGebeyaColors.Divider))
                            }
                        }
                    }
                    item(key = message.id) {
                        SellerMessageBubble(
                            message = message,
                            mine = if (sellerView) {
                                message.senderProfileId != conversation.customerProfileId
                            } else {
                                message.senderProfileId == conversation.customerProfileId
                            },
                        )
                    }
                }
            }
        }
        ShopMessageComposer(
            conversationId = conversation.id,
            sending = sending,
            onSend = onSend,
            onUpload = onUpload,
            onTyping = onTyping,
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the conversation from your inbox. It does not delete the other participant's copy.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onAction("delete")
                    },
                ) { Text("Delete", color = AdisGebeyaColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProductConversationContextCard(
    context: ShopProductContextDto,
    onOpenProduct: ((String) -> Unit)?,
) {
    val available = context.canOpen && context.availability != "unavailable"
    val clickable = available && onOpenProduct != null && context.productId.isNotBlank()
    val title = context.title?.takeIf(String::isNotBlank) ?: "Product unavailable"
    val options = context.selectedOptions
        .mapNotNull { option ->
            val name = option.name.trim()
            val value = option.value.trim()
            when {
                value.isBlank() -> null
                name.isBlank() -> value
                else -> "$name: $value"
            }
        }
        .joinToString("  |  ")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = clickable) {
            onOpenProduct?.invoke(context.productId)
        },
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ShopAssetImage(context.imageUrl, title, Modifier.fillMaxSize(), iconSize = 24.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = AdisGebeyaColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (available && context.priceMinor != null) {
                    Text(
                        formatProductContextPrice(context.priceMinor, context.currency, context.sellingUnitCode),
                        color = AdisGebeyaColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                } else {
                    Text("Product unavailable", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                }
                if (options.isNotBlank()) {
                    Text(options, color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (clickable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View", color = AdisGebeyaColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

internal fun formatProductContextPrice(priceMinor: Int, currency: String?, sellingUnitCode: String? = null): String {
    val code = currency?.trim()?.uppercase()?.takeIf(String::isNotBlank) ?: "ETB"
    val unit = when (sellingUnitCode?.trim()?.uppercase()) {
        "KILOGRAM" -> " / kg"
        "METER" -> " / m"
        "PIECE" -> " / piece"
        else -> ""
    }
    return "$code ${"%,d".format(priceMinor.coerceAtLeast(0) / 100)}$unit"
}

internal object ShopMessageRefreshPolicy {
    const val INTERVAL_MS: Long = 10_000L

    fun isActive(lifecycleState: Lifecycle.State, conversationVisible: Boolean): Boolean =
        conversationVisible && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
}

private data class ShopAttachmentSelection(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

private val supportedShopAttachmentTypes: Set<String> = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "application/pdf",
    "text/plain",
)

private fun readShopAttachment(context: Context, uri: Uri): ShopAttachmentSelection {
    val resolver = context.contentResolver
    var fileName = "attachment"
    var declaredSize = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
    }
    require(declaredSize < 0L || declaredSize <= 10L * 1024L * 1024L) {
        "Choose a file smaller than 10 MB."
    }
    val mimeType: String = resolver.getType(uri).orEmpty().lowercase()
    require(mimeType in supportedShopAttachmentTypes) { "This file type is not supported." }
    val bytes: ByteArray = resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        ?: error("The selected file could not be opened.")
    require(bytes.size <= 10 * 1024 * 1024) { "Choose a file smaller than 10 MB." }
    return ShopAttachmentSelection(bytes, mimeType, fileName)
}

@Composable
private fun ShopMessageComposer(
    conversationId: String,
    sending: Boolean,
    onSend: (String) -> Unit,
    onUpload: (ByteArray, String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
) {
    var draft by remember(conversationId) { mutableStateOf("") }
    var attachmentError by remember(conversationId) { mutableStateOf<String?>(null) }
    val context: Context = LocalContext.current
    ShopTypingActivityReporter(
        conversationId = conversationId,
        hasText = draft.isNotBlank(),
        onTyping = onTyping,
    )
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { readShopAttachment(context, uri) }
                .onSuccess { selection: ShopAttachmentSelection ->
                    attachmentError = null
                    onUpload(selection.bytes, selection.mimeType, selection.fileName)
                }
                .onFailure { error: Throwable ->
                    attachmentError = error.message ?: "Attachment could not be opened."
                }
        }
    }
    Card(
        colors = CardDefaults.cardColors(AdisGebeyaColors.Surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(
                onClick = {
                    attachmentError = null
                    attachmentPicker.launch(supportedShopAttachmentTypes.toTypedArray())
                },
                enabled = !sending,
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach image or file", tint = AdisGebeyaColors.Primary)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { value: String -> if (value.length <= 4000) draft = value },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Write a message") },
                shape = RoundedCornerShape(16.dp),
                colors = sellerMessageTextFieldColors(),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val message: String = draft.trim()
                    if (message.isNotEmpty()) {
                        onSend(message)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank() && !sending,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send message", tint = AdisGebeyaColors.OnColor)
            }
        }
    }
    attachmentError?.let { error: String ->
        Text(error, color = AdisGebeyaColors.Error, fontSize = 12.sp)
    }
}

@Composable
private fun SellerMessageBubble(message: ShopMessageDto, mine: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openingAttachment by remember(message.id) { mutableStateOf(false) }
    var attachmentOpenError by remember(message.id) { mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.fillMaxWidth(.82f).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val attachmentUrl = message.attachmentUrl
            if (!attachmentUrl.isNullOrBlank() && message.messageType == "image") {
                AsyncImage(
                    model = attachmentUrl,
                    contentDescription = message.attachmentName ?: "Message image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp)),
                )
            } else if (!attachmentUrl.isNullOrBlank()) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !openingAttachment) {
                        scope.launch {
                            openingAttachment = true
                            attachmentOpenError = null
                            runCatching {
                                val file = cacheShopAttachment(context, message)
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val viewIntent = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(contentUri, message.attachmentMimeType ?: "application/octet-stream")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(viewIntent, "Open attachment"))
                            }.onFailure {
                                attachmentOpenError = "Attachment could not be opened."
                            }
                            openingAttachment = false
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = "Open attachment",
                        tint = AdisGebeyaColors.Primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (openingAttachment) "Opening attachment..." else message.attachmentName ?: "Attachment",
                        color = AdisGebeyaColors.PrimaryText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                attachmentOpenError?.let { error ->
                    Text(error, color = AdisGebeyaColors.Error, fontSize = 12.sp)
                }
            }
            if (!message.body.isNullOrBlank()) {
                Text(
                    message.body,
                    color = AdisGebeyaColors.PrimaryText,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
            Text(
                formatMarketplaceTime(message.createdAt),
                color = AdisGebeyaColors.SecondaryText,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

internal fun shouldShowMarketplaceDateSeparator(previous: String?, current: String?): Boolean =
    marketplaceLocalDate(previous) != marketplaceLocalDate(current)

private suspend fun cacheShopAttachment(context: Context, message: ShopMessageDto): File = withContext(Dispatchers.IO) {
    val attachmentUrl = message.attachmentUrl ?: error("Attachment unavailable")
    val attachmentUri = Uri.parse(attachmentUrl)
    val expectedHost = Uri.parse(BuildConfig.ADISHOP_SUPABASE_URL).host
    require(attachmentUri.scheme == "https" && attachmentUri.host == expectedHost) { "Unexpected attachment host" }

    val safeName = (message.attachmentName ?: "attachment")
        .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        .take(120)
        .ifBlank { "attachment" }
    val messagePrefix = message.id.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "message" }
    val directory = File(context.cacheDir, "shop_message_attachments").apply { mkdirs() }
    val target = File(directory, "$messagePrefix-$safeName")
    val connection = (URL(attachmentUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = false
    }
    try {
        require(connection.responseCode in 200..299) { "Attachment download failed" }
        val maximumBytes = 10 * 1024 * 1024
        require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maximumBytes) { "Attachment too large" }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maximumBytes) { "Attachment too large" }
                    output.write(buffer, 0, read)
                }
            }
        }
        target
    } catch (error: Throwable) {
        target.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun sellerMessageTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AdisGebeyaColors.PrimaryText,
    unfocusedTextColor = AdisGebeyaColors.PrimaryText,
    cursorColor = AdisGebeyaColors.Primary,
    focusedBorderColor = AdisGebeyaColors.Primary,
    unfocusedBorderColor = AdisGebeyaColors.Border,
    focusedContainerColor = AdisGebeyaColors.Surface,
    unfocusedContainerColor = AdisGebeyaColors.Surface,
)
