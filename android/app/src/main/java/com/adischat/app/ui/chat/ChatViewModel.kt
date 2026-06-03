package com.adischat.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.UserSummary
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.network.CloudflareClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// ── Screen state ──────────────────────────────────────────────────────────────

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    object ConversationsList : ChatUiState()
    object NewChat : ChatUiState()
    data class Thread(
        val conversationId:    String,
        val otherUserName:     String,
        val otherUserPhotoUrl: String,
        val isSupport:         Boolean = false,
        val isCommunity:       Boolean = false,
        val communityType:     String = "direct",
        val communityDescription: String = "",
        val communityMemberCount: Int = 0,
        val communityOwnerUid: String = "",
    ) : ChatUiState()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = AuthRepository()
    private val chatRepo = ChatRepository()

    private val _me = MutableStateFlow<AppUser?>(null)
    val me: StateFlow<AppUser?> = _me

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ── Conversations list (all) ──────────────────────────────────────────────

    val conversations: StateFlow<List<Conversation>> = _uiState
        .flatMapLatest { state ->
            if (state is ChatUiState.ConversationsList) chatRepo.observeAllConversations()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Active thread ─────────────────────────────────────────────────────────

    val messages: StateFlow<List<Message>> = _uiState
        .flatMapLatest { state ->
            if (state is ChatUiState.Thread) {
                if (state.isCommunity) chatRepo.observeCommunityMessages(state.conversationId)
                else chatRepo.observeMessages(state.conversationId)
            } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    // ── User search ───────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<UserSummary>> = _searchQuery
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) flowOf(emptyList())
            else flow {
                runCatching { emit(chatRepo.searchUsers(q)) }
                    .onFailure { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _communityQuery = MutableStateFlow("")
    val communityQuery: StateFlow<String> = _communityQuery

    val channels: StateFlow<List<CommunityRoom>> = _communityQuery
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { q -> chatRepo.observeCommunities("channel", q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<CommunityRoom>> = _communityQuery
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { q -> chatRepo.observeCommunities("group", q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Init ──────────────────────────────────────────────────────────────────

    init { loadUser() }

    private fun loadUser() {
        _uiState.value = ChatUiState.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.getCurrentAppUser()
                if (user == null) {
                    _uiState.value = ChatUiState.Error("Could not load your profile. Check your connection.")
                } else {
                    _me.value = user
                    _uiState.value = ChatUiState.ConversationsList
                }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(CloudflareClient.friendlyErrorMessage(e, "Failed to connect. Please try again."))
            }
        }
    }

    fun retryLoad() = loadUser()

    // ── Navigation ────────────────────────────────────────────────────────────

    fun openConversation(conv: Conversation) {
        _uiState.value = ChatUiState.Thread(
            conversationId    = conv.id,
            otherUserName     = conv.otherUserName,
            otherUserPhotoUrl = conv.otherUserPhotoUrl,
            isSupport         = conv.type == "support" && conv.otherUserId == "support",
        )
        viewModelScope.launch { chatRepo.markRead(conv.id, "") }
    }

    fun navigateToNewChat() { _uiState.value = ChatUiState.NewChat }

    fun navigateBack() {
        _uiState.value = ChatUiState.ConversationsList
        _searchQuery.value = ""
    }

    fun markCurrentRead() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch { chatRepo.markRead(state.conversationId, "") }
    }

    // ── Start direct conversation ─────────────────────────────────────────────

    fun startConversation(target: UserSummary) {
        viewModelScope.launch {
            val result = chatRepo.startConversation(target.uid)
            result.onSuccess { convId ->
                _uiState.value = ChatUiState.Thread(
                    conversationId    = convId,
                    otherUserName     = target.name.ifBlank { target.username.ifBlank { target.phone } },
                    otherUserPhotoUrl = target.photoUrl,
                )
                _searchQuery.value = ""
            }
            result.onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun onCommunityQueryChanged(q: String) { _communityQuery.value = q }

    fun openCommunity(room: CommunityRoom) {
        if (!room.joined) {
            joinCommunity(room)
            return
        }
        _uiState.value = ChatUiState.Thread(
            conversationId = room.id,
            otherUserName = room.name,
            otherUserPhotoUrl = room.photoUrl,
            isCommunity = true,
            communityType = room.type,
            communityDescription = room.description,
            communityMemberCount = room.memberCount,
            communityOwnerUid = room.ownerUid,
        )
    }

    fun joinCommunity(room: CommunityRoom) {
        viewModelScope.launch {
            chatRepo.joinCommunity(room.id)
                .onSuccess { openCommunity(room.copy(joined = true)) }
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun createCommunity(type: String, name: String, description: String) {
        viewModelScope.launch {
            chatRepo.createCommunity(type, name, description)
                .onSuccess { openCommunity(it.copy(joined = true)) }
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    // ── Send text ─────────────────────────────────────────────────────────────

    fun updateCurrentCommunity(name: String, description: String, photoUrl: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity) return
        viewModelScope.launch {
            chatRepo.updateCommunity(state.conversationId, name, description, photoUrl)
                .onSuccess { room ->
                    _uiState.value = state.copy(
                        otherUserName = room.name,
                        otherUserPhotoUrl = room.photoUrl,
                        communityDescription = room.description,
                        communityMemberCount = room.memberCount,
                        communityOwnerUid = room.ownerUid,
                    )
                }
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    /**
     * Picks, compresses (max 800px, JPEG 85%), uploads and returns the CDN URL via [onResult].
     * [onResult] receives null if anything goes wrong (caller should show an error).
     */
    fun uploadCommunityPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { compressCommunityImage(uri) }
            if (bytes == null) { onResult(null); return@launch }
            chatRepo.uploadCommunityPhoto(bytes)
                .onSuccess { onResult(it) }
                .onFailure { onResult(null) }
        }
    }

    private fun compressCommunityImage(uri: Uri): ByteArray? = runCatching {
        val resolver = getApplication<Application>().contentResolver
        val bitmap   = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            ?: return@runCatching null
        val maxPx = 800
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > maxPx) {
            val ratio = maxPx.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width  * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true,
            )
        } else bitmap
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }.getOrNull()

    fun deleteCurrentCommunity() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity) return
        viewModelScope.launch {
            chatRepo.deleteCommunity(state.conversationId)
                .onSuccess { navigateBack() }
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun sendMessage(text: String) {
        val body   = text.trim().ifEmpty { return }
        val me     = _me.value ?: return
        val state  = _uiState.value as? ChatUiState.Thread ?: return
        _sending.value = true
        viewModelScope.launch {
            val result =
                if (state.isCommunity) chatRepo.sendCommunityMessage(state.conversationId, body)
                else chatRepo.sendMessage(
                    conversationUserId       = state.conversationId,
                    conversationUserName     = me.name,
                    conversationUserPhotoUrl = me.photoUrl,
                    senderId                 = me.uid,
                    senderRole               = me.role,
                    text                     = body,
                )
            result.onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
            _sending.value = false
        }
    }

    fun reactToMessage(message: Message, emoji: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity) {
            _error.value = "Reactions are active in channels and groups now"
            return
        }
        viewModelScope.launch {
            chatRepo.reactToCommunityMessage(state.conversationId, message.id, emoji)
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun deleteMessage(message: Message) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity) {
            _error.value = "Delete is active in channels and groups now"
            return
        }
        viewModelScope.launch {
            chatRepo.deleteCommunityMessage(state.conversationId, message.id)
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    // ── Send media ────────────────────────────────────────────────────────────

    fun moderateGroupMember(memberUid: String, action: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity || state.communityType != "group") return
        val cleanAction = when (action) {
            "suspend", "block", "restore" -> action
            else -> return
        }
        viewModelScope.launch {
            chatRepo.moderateCommunityMember(state.conversationId, memberUid, cleanAction)
                .onSuccess {
                    _error.value = when (cleanAction) {
                        "suspend" -> "Member suspended from posting"
                        "block" -> "Member blocked from the group"
                        else -> "Member restored"
                    }
                }
                .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun sendImage(uri: Uri) = sendMediaUri(uri, "image")
    fun sendVideo(uri: Uri) = sendMediaUri(uri, "video")

    private fun sendMediaUri(uri: Uri, type: String) {
        val me    = _me.value ?: return
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (state.isCommunity) {
            _error.value = "Media in channels and groups is coming next"
            return
        }
        _sending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val ctx      = getApplication<Application>()
            val mimeType = ctx.contentResolver.getType(uri) ?: if (type == "image") "image/jpeg" else "video/mp4"
            val bytes    = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) { _sending.value = false; return@launch }
            sendMedia(me, state.conversationId, bytes, mimeType, type)
        }
    }

    fun sendVoice(file: File, durationMs: Long) {
        val me    = _me.value ?: return
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (state.isCommunity) {
            file.delete()
            _error.value = "Voice in channels and groups is coming next"
            return
        }
        _sending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = file.readBytes()
            file.delete()
            sendMedia(me, state.conversationId, bytes, "audio/m4a", "voice", durationMs = durationMs)
        }
    }

    private suspend fun sendMedia(
        me: AppUser, convId: String,
        bytes: ByteArray, mimeType: String, type: String,
        caption: String = "", durationMs: Long = 0,
    ) {
        chatRepo.uploadAndSendMedia(
            conversationUserId       = convId,
            conversationUserName     = me.name,
            conversationUserPhotoUrl = me.photoUrl,
            bytes                    = bytes,
            mimeType                 = mimeType,
            type                     = type,
            caption                  = caption,
            durationMs               = durationMs,
        ).onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        _sending.value = false
    }

    fun clearError() { _error.value = null }
}
