package com.adischat.app.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.data.model.AdisLinkType
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.UserSummary
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.network.CloudflareClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File


sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    object ConversationsList : ChatUiState()
    object NewChat : ChatUiState()
    data class Thread(
        val conversationId:    String,
        val otherUserName:     String,
        val otherUserId:       String = "",
        val otherUserPhotoUrl: String,
        val isSupport:      Boolean = false,
        val isCommunity:       Boolean = false,
        val communityType:     String = "direct",
        val communityJoined:   Boolean = false,
        val communityDescription: String = "",
        val communityMemberCount: Int = 0,
        val communityOwnerUid: String = "",
        val unreadCount: Int = 0,
        val otherVerificationLevel: Int = 0,
        val otherVerified: Boolean = false,
    ) : ChatUiState()
}

/** Online state + last-seen (unix seconds) of the peer in an open direct thread. */
data class PeerPresence(val online: Boolean, val lastSeen: Long)

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

    private val _localMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())


    val conversations: StateFlow<List<Conversation>> = _uiState
        .flatMapLatest { state ->
        if (state is ChatUiState.ConversationsList) chatRepo.observeAllConversations()
        else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    val messages: StateFlow<List<Message>> = _uiState
        .flatMapLatest { state ->
        if (state is ChatUiState.Thread) {
        val remote =
        if (state.isCommunity) chatRepo.observeCommunityMessages(state.conversationId)
        else chatRepo.observeMessages(state.conversationId)
        combine(remote, _localMessages) { server, localByThread ->
        mergeLocalMessages(server, localByThread[state.conversationId].orEmpty())
        }
        } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private var pendingNotificationConversationId: String? = null


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


    // - - - Presence - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    private val _peerPresence = MutableStateFlow<PeerPresence?>(null)
    val peerPresence: StateFlow<PeerPresence?> = _peerPresence

    private var presenceJob: Job? = null

    init {
        loadUser()
        // Global heartbeat: mark ourselves active while the chat screen is alive.
        viewModelScope.launch {
        while (isActive) {
        chatRepo.sendHeartbeat()
        delay(30_000)
        }
        }
    }

    /**
     * Polls the given peer's presence every 30s for the open direct thread.
     * Pass a blank uid to stop polling (e.g. group/support threads).
     */
    fun observePeerPresence(peerUid: String) {
        presenceJob?.cancel()
        _peerPresence.value = null
        if (peerUid.isBlank()) return
        presenceJob = viewModelScope.launch {
        while (isActive) {
        chatRepo.getPresence(peerUid)?.let {
        _peerPresence.value = PeerPresence(online = it.online, lastSeen = it.lastSeen)
        }
        delay(30_000)
        }
        }
    }

    private fun loadUser() {
        _uiState.value = ChatUiState.Loading
        viewModelScope.launch {
        try {
        val user = authRepo.getCurrentAppUser()
        if (user == null) {
        _uiState.value = ChatUiState.Error("Could not load your profile. Check your connection.")
        } else {
        _me.value = user
        val pending = pendingNotificationConversationId
        if (pending.isNullOrBlank()) {
        _uiState.value = ChatUiState.ConversationsList
        } else {
        pendingNotificationConversationId = null
        openConversationByNotification(pending)
        }
        }
        } catch (e: Exception) {
        _uiState.value = ChatUiState.Error(CloudflareClient.friendlyErrorMessage(e, "Failed to connect. Please try again."))
        }
        }
    }

    fun retryLoad() = loadUser()


    fun openConversation(conv: Conversation) {
        // TODO(temporary): diagnostics for "private chat history not loading". A blank
        // id here would make api.getMessages hit /api/conversations//messages -> empty.
        android.util.Log.d("ChatLoad", "openConversation id='${conv.id}' otherUserId='${conv.otherUserId}' type='${conv.type}'")
        _uiState.value = ChatUiState.Thread(
        conversationId    = conv.id,
        otherUserName     = conv.otherUserName,
        otherUserId       = conv.otherUserId,
        otherUserPhotoUrl = conv.otherUserPhotoUrl,
        isSupport      = conv.type == "support" && conv.otherUserId == "support",
        unreadCount       = conv.unreadCount,
        otherVerificationLevel = conv.otherUserVerificationLevel,
        otherVerified = conv.otherUserVerified,
        )
        // Opening a private chat clears its unread badge immediately (server clears
        // server-side once messages are shown via markCurrentRead).
        com.adischat.app.notifications.UnreadCenter.markConversationRead(conv.id)
    }

    fun openConversationByNotification(conversationId: String) {
        val cleanId = conversationId.trim()
        if (cleanId.isBlank()) return
        if (_me.value == null) {
        pendingNotificationConversationId = cleanId
        return
        }
        viewModelScope.launch {
        try {
        chatRepo.getConversationsOnce()
        .firstOrNull { it.id == cleanId }
        ?.let {
        openConversation(it)
        return@launch
        }

        val community = (chatRepo.searchCommunities("group", "") + chatRepo.searchCommunities("channel", ""))
        .firstOrNull { it.id == cleanId }
        if (community != null) {
        openCommunity(community)
        return@launch
        }

        _error.value = "Could not open this conversation"
        } catch (e: Exception) {
        _error.value = CloudflareClient.friendlyErrorMessage(e, "Could not open this conversation")
        }
        }
    }

    fun navigateToNewChat() { _uiState.value = ChatUiState.NewChat }

    fun navigateBack() {
        _uiState.value = ChatUiState.ConversationsList
        _searchQuery.value = ""
    }

    fun markCurrentRead() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (state.isCommunity) {
        // Groups/channels have no server read-state - - clear locally only.
        com.adischat.app.notifications.UnreadCenter.markRoomRead(state.conversationId)
        } else {
        viewModelScope.launch { chatRepo.markRead(state.conversationId, "") }
        com.adischat.app.notifications.UnreadCenter.markConversationRead(state.conversationId)
        }
    }


    fun startConversation(target: UserSummary) {
        viewModelScope.launch {
        val result = chatRepo.startConversation(target.uid)
        result.onSuccess { convId ->
        _uiState.value = ChatUiState.Thread(
        conversationId    = convId,
        otherUserName     = target.name.ifBlank { target.username.ifBlank { target.phone } },
        otherUserId       = target.uid,
        otherUserPhotoUrl = target.photoUrl,
        otherVerificationLevel = target.verificationLevel,
        otherVerified = target.verified,
        )
        _searchQuery.value = ""
        }
        result.onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun onCommunityQueryChanged(q: String) { _communityQuery.value = q }

    fun openAdisLink(target: AdisLinkTarget) {
        val slug = target.slug.trim().removePrefix("@")
        if (slug.isBlank()) return
        viewModelScope.launch {
        val resolved = chatRepo.resolveAdisLink(slug).getOrElse {
        resolveAdisLinkFallback(slug, target.type).getOrElse { fallbackError ->
        _error.value = CloudflareClient.friendlyErrorMessage(fallbackError, "Could not open me.adischat.com/$slug")
        return@launch
        }
        }
        when (resolved.type) {
        AdisLinkType.USER -> {
        val user = resolved.user
        if (user == null) {
        _error.value = "User link is not available"
        } else {
        startConversation(user)
        }
        }
        AdisLinkType.CHANNEL,
        AdisLinkType.GROUP -> {
        val room = resolved.community
        if (room == null) {
        _error.value = "${resolved.type.name.lowercase().replaceFirstChar { it.uppercase() }} link is not available"
        } else {
        openCommunity(room)
        }
        }
        }
        }
    }

    private suspend fun resolveAdisLinkFallback(slug: String, type: AdisLinkType): Result<AdisLinkTarget> = runCatching {
        when (type) {
        AdisLinkType.USER -> {
        val clean = slug.removePrefix("@")
        val user = findUserBySharedSlug(clean) ?: throw IllegalStateException("User not found")
        AdisLinkTarget(slug = clean, type = AdisLinkType.USER, user = user)
        }
        AdisLinkType.CHANNEL,
        AdisLinkType.GROUP -> {
        val communityType = type.name.lowercase()
        // Channel/group links carry the room's internal ID, not its name. Search
        // by the slug (in case it IS a name) AND scan the full list to match by ID,
        // so a shared link actually opens the channel instead of "not found".
        val room = chatRepo.searchCommunities(communityType, slug).firstOrNull { candidate ->
        candidate.id.equals(slug, ignoreCase = true) ||
        candidate.name.equals(slug, ignoreCase = true)
        } ?: chatRepo.searchCommunities(communityType, "").firstOrNull { candidate ->
        candidate.id.equals(slug, ignoreCase = true)
        } ?: throw IllegalStateException("${communityType.replaceFirstChar { it.uppercase() }} not found")
        AdisLinkTarget(slug = slug, type = type, community = room)
        }
        }
    }

    private suspend fun findUserBySharedSlug(slug: String): UserSummary? {
        val clean = slug.trim().removePrefix("@")
        if (clean.isBlank()) return null
        val candidates = (chatRepo.searchUsers(clean) + chatRepo.searchUsers("@$clean"))
        .distinctBy { it.uid.ifBlank { "${it.username}:${it.phone}:${it.name}" } }
        return candidates.firstOrNull { user ->
        user.username.cleanLinkPart().equals(clean.cleanLinkPart(), ignoreCase = true) ||
        user.uid.equals(clean, ignoreCase = true) ||
        user.phone.cleanPhoneForLink().equals(clean.cleanPhoneForLink(), ignoreCase = true) ||
        user.name.cleanLinkPart().equals(clean.cleanLinkPart(), ignoreCase = true)
        } ?: candidates.singleOrNull()
    }

    fun openCommunity(room: CommunityRoom) {
        _uiState.value = ChatUiState.Thread(
        conversationId = room.id,
        otherUserName = room.name,
        otherUserPhotoUrl = room.photoUrl,
        isCommunity = true,
        communityType = room.type,
        communityJoined = room.joined,
        communityDescription = room.description,
        communityMemberCount = room.memberCount,
        communityOwnerUid = room.ownerUid,
        unreadCount = room.unreadCount,
        )
        // Opening a group/channel clears its unread badge immediately.
        com.adischat.app.notifications.UnreadCenter.markRoomRead(room.id)
    }

    fun joinCommunity(room: CommunityRoom) {
        viewModelScope.launch {
        chatRepo.joinCommunity(room.id)
        .onSuccess { openCommunity(room.copy(joined = true, memberCount = room.memberCount + 1)) }
        .onFailure {
        _error.value = CloudflareClient.friendlyErrorMessage(it)
        }
        }
    }

    fun joinCurrentCommunity() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity || state.communityJoined) return
        joinCommunity(
        CommunityRoom(
        id = state.conversationId,
        type = state.communityType,
        name = state.otherUserName,
        description = state.communityDescription,
        ownerUid = state.communityOwnerUid,
        photoUrl = state.otherUserPhotoUrl,
        memberCount = state.communityMemberCount,
        joined = false,
        ),
        )
    }

    fun leaveCurrentCommunity() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        if (!state.isCommunity || !state.communityJoined) return
        viewModelScope.launch {
        chatRepo.leaveCommunity(state.conversationId, state.communityType)
        .onSuccess {
        _uiState.value = state.copy(
        communityJoined = false,
        communityMemberCount = (state.communityMemberCount - 1).coerceAtLeast(0),
        )
        navigateBack()
        }
        .onFailure {
        _error.value = if (state.communityType == "channel") "Unable to unfollow channel." else "Unable to leave group."
        }
        }
    }

    fun createCommunity(type: String, name: String, description: String) {
        viewModelScope.launch {
        chatRepo.createCommunity(type, name, description)
        .onSuccess { openCommunity(it.copy(joined = true)) }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }


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

    fun sendMessage(text: String, replyTo: Message? = null) {
        val body   = text.trim().ifEmpty { return }
        val me     = _me.value ?: return
        val state  = _uiState.value as? ChatUiState.Thread ?: return
        if (state.isCommunity && !state.communityJoined && state.communityOwnerUid != me.uid && me.role != "admin") {
        _error.value = if (state.communityType == "channel") "Follow this channel before posting" else "Join this group before sending messages"
        return
        }
        _sending.value = true
        val local = optimisticMessage(
        state = state,
        me = me,
        type = "text",
        text = body,
        status = "sending",
        progress = 25,
        ).copy(
        replyToMessageId = replyTo?.id.orEmpty(),
        replySenderName = replyTo?.senderName.orEmpty(),
        replyPreview = replyTo?.text.orEmpty().ifBlank { replyTo?.type.orEmpty() }
        )
        addLocalMessage(state.conversationId, local)
        viewModelScope.launch {
        val result =
        if (state.isCommunity) chatRepo.sendCommunityMessage(state.conversationId, body, replyTo?.id.orEmpty())
        else chatRepo.sendMessage(
        conversationUserId       = state.conversationId,
        conversationUserName     = me.name,
        conversationUserPhotoUrl = me.photoUrl,
        senderId      = me.uid,
        senderRole      = me.role,
        text      = body,
        replyToMessageId      = replyTo?.id.orEmpty(),
        )
        result
        .onSuccess { confirmed ->
        replaceLocalMessage(state.conversationId, local.id, confirmed.copy(localStatus = "sent"))
        delay(2_500)
        removeLocalMessage(state.conversationId, confirmed.id)
        }
        .onFailure {
        markLocalMessageFailed(state.conversationId, local.id, CloudflareClient.friendlyErrorMessage(it))
        _error.value = CloudflareClient.friendlyErrorMessage(it)
        }
        _sending.value = false
        }
    }

    fun retryMessage(message: Message) {
        if (message.localStatus != "failed") return
        if (message.type != "text") {
        _error.value = "Please resend this media item"
        return
        }
        val state = _uiState.value as? ChatUiState.Thread ?: return
        removeLocalMessage(state.conversationId, message.id)
        sendMessage(message.text)
    }

    fun reactToMessage(message: Message, emoji: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.reactToMessage(state.isCommunity, state.conversationId, message.id, emoji)
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun deleteMessage(message: Message) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.deleteMessageAction(state.isCommunity, state.conversationId, message.id, "everyone")
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun editMessage(message: Message, text: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.editMessage(state.isCommunity, state.conversationId, message.id, text)
        .onSuccess { _error.value = "Message edited" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun deleteMessageForMe(message: Message) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.deleteMessageAction(state.isCommunity, state.conversationId, message.id, "me")
        .onSuccess { _error.value = "Message deleted for you" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun deleteMessageForEveryone(message: Message) = deleteMessage(message)

    fun pinMessage(message: Message, pinned: Boolean) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.pinMessage(state.isCommunity, state.conversationId, message.id, pinned)
        .onSuccess { _error.value = if (pinned) "Message pinned" else "Message unpinned" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun starMessage(message: Message, starred: Boolean) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.starMessage(state.isCommunity, state.conversationId, message.id, starred)
        .onSuccess { _error.value = if (starred) "Message saved" else "Message unsaved" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun markVoiceListened(message: Message) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.listenMessage(state.isCommunity, state.conversationId, message.id)
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun translateMessage(message: Message, targetLanguage: String = "en") {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.translateMessage(state.isCommunity, state.conversationId, message.id, targetLanguage)
        .onSuccess { _error.value = it.translatedText.ifBlank { "Translation unavailable" } }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun updateThreadSetting(action: String) {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        val body = when (action) {
        "mute" -> com.adischat.app.network.ChatSettingsBody(muted = true)
        "wallpaper" -> com.adischat.app.network.ChatSettingsBody(wallpaper = "adis_default")
        "disappearing" -> com.adischat.app.network.ChatSettingsBody(disappearingSeconds = 24 * 60 * 60)
        else -> com.adischat.app.network.ChatSettingsBody()
        }
        chatRepo.updateThreadSettings(state.isCommunity, state.conversationId, body)
        .onSuccess { _error.value = "${action.replaceFirstChar { it.uppercase() }} updated" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun clearCurrentThread() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.clearThread(state.isCommunity, state.conversationId)
        .onSuccess { _error.value = "Chat cleared for you" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun exportCurrentThread() {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.exportThread(state.isCommunity, state.conversationId)
        .onSuccess { _error.value = "Export ready: ${it.messages.size} messages" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun reportThread(message: Message? = null, reason: String = "report") {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        viewModelScope.launch {
        chatRepo.reportThread(
        state.isCommunity,
        state.conversationId,
        com.adischat.app.network.ChatReportBody(
        messageId = message?.id.orEmpty(),
        reportedUid = message?.senderId.orEmpty(),
        reason = reason,
        ),
        ).onSuccess { _error.value = "Report sent" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }

    fun blockCurrentUser(reason: String = "") {
        val state = _uiState.value as? ChatUiState.Thread ?: return
        val blockedUid = state.otherUserId.ifBlank { return }
        viewModelScope.launch {
        chatRepo.blockConversationUser(state.conversationId, blockedUid, reason)
        .onSuccess { _error.value = "User blocked" }
        .onFailure { _error.value = CloudflareClient.friendlyErrorMessage(it) }
        }
    }


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

    fun sendImage(uri: Uri, caption: String = "") = sendMediaUri(uri, "image", caption)
    fun sendVideo(uri: Uri, caption: String = "") = sendMediaUri(uri, "video", caption)

    private fun sendMediaUri(uri: Uri, type: String, caption: String = "") {
        val me    = _me.value ?: return
        val state = _uiState.value as? ChatUiState.Thread ?: return
        _sending.value = true
        val local = optimisticMessage(
        state = state,
        me = me,
        type = type,
        text = caption,   // caption rides with the media (shown below it)
        mediaUrl = uri.toString(),
        mediaMimeType = getApplication<Application>().contentResolver.getType(uri) ?: if (type == "image") "image/jpeg" else "video/mp4",
        status = "uploading",
        progress = 1,
        )
        addLocalMessage(state.conversationId, local)
        viewModelScope.launch(Dispatchers.IO) {
        val ctx      = getApplication<Application>()
        val mimeType = ctx.contentResolver.getType(uri) ?: if (type == "image") "image/jpeg" else "video/mp4"
        val bytes    = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
        markLocalMessageFailed(state.conversationId, local.id, "Could not read media")
        _sending.value = false
        return@launch
        }
        if (state.isCommunity) sendCommunityMedia(state.conversationId, bytes, mimeType, type, caption = caption, localId = local.id)
        else sendMedia(me, state.conversationId, bytes, mimeType, type, caption = caption, localId = local.id)
        }
    }

    fun sendVoice(file: File, durationMs: Long) {
        val me    = _me.value ?: return
        val state = _uiState.value as? ChatUiState.Thread ?: return
        _sending.value = true
        val local = optimisticMessage(
        state = state,
        me = me,
        type = "voice",
        text = "",
        mediaUrl = file.toURI().toString(),
        mediaMimeType = "audio/m4a",
        durationMs = durationMs,
        status = "uploading",
        progress = 1,
        )
        addLocalMessage(state.conversationId, local)
        viewModelScope.launch(Dispatchers.IO) {
        val bytes = file.readBytes()
        file.delete()
        if (state.isCommunity) sendCommunityMedia(state.conversationId, bytes, "audio/m4a", "voice", durationMs = durationMs, localId = local.id)
        else sendMedia(me, state.conversationId, bytes, "audio/m4a", "voice", durationMs = durationMs, localId = local.id)
        }
    }

    private suspend fun sendMedia(
        me: AppUser, convId: String,
        bytes: ByteArray, mimeType: String, type: String,
        caption: String = "", durationMs: Long = 0, localId: String = "",
    ) {
        chatRepo.uploadAndSendMedia(
        conversationUserId       = convId,
        conversationUserName     = me.name,
        conversationUserPhotoUrl = me.photoUrl,
        bytes      = bytes,
        mimeType      = mimeType,
        type      = type,
        caption      = caption,
        durationMs      = durationMs,
        onProgress      = { progress ->
        updateLocalMessage(convId, localId) {
        it.copy(localStatus = "uploading", localProgress = progress.coerceIn(1, 100))
        }
        },
        ).onSuccess { confirmed ->
        finishLocalMessage(convId, localId, confirmed)
        }.onFailure {
        markLocalMessageFailed(convId, localId, CloudflareClient.friendlyErrorMessage(it))
        _error.value = CloudflareClient.friendlyErrorMessage(it)
        }
        _sending.value = false
    }

    private suspend fun sendCommunityMedia(
        roomId: String,
        bytes: ByteArray,
        mimeType: String,
        type: String,
        caption: String = "",
        durationMs: Long = 0,
        localId: String = "",
    ) {
        chatRepo.uploadAndSendCommunityMedia(
        roomId = roomId,
        bytes = bytes,
        mimeType = mimeType,
        type = type,
        caption = caption,
        durationMs = durationMs,
        onProgress = { progress ->
        updateLocalMessage(roomId, localId) {
        it.copy(localStatus = "uploading", localProgress = progress.coerceIn(1, 100))
        }
        },
        ).onSuccess { confirmed ->
        finishLocalMessage(roomId, localId, confirmed)
        }.onFailure {
        markLocalMessageFailed(roomId, localId, CloudflareClient.friendlyErrorMessage(it))
        _error.value = CloudflareClient.friendlyErrorMessage(it)
        }
        _sending.value = false
    }

    fun clearError() { _error.value = null }

    private fun optimisticMessage(
        state: ChatUiState.Thread,
        me: AppUser,
        type: String,
        text: String,
        mediaUrl: String = "",
        mediaMimeType: String = "",
        durationMs: Long = 0,
        status: String,
        progress: Int,
    ): Message = Message(
        id = "local-${System.currentTimeMillis()}-${(1000..9999).random()}",
        senderId = me.uid,
        senderRole = me.role,
        senderName = me.name,
        senderPhotoUrl = me.photoUrl,
        type = type,
        text = text,
        mediaUrl = mediaUrl,
        mediaMimeType = mediaMimeType,
        durationMs = durationMs,
        timestamp = System.currentTimeMillis(),
        localStatus = status,
        localProgress = progress,
        senderVerificationLevel = if (state.isCommunity) 0 else state.otherVerificationLevel,
    )

    private fun addLocalMessage(threadId: String, message: Message) {
        _localMessages.update { current ->
        current + (threadId to (current[threadId].orEmpty().filterNot { it.id == message.id } + message))
        }
    }

    private fun replaceLocalMessage(threadId: String, oldId: String, message: Message) {
        _localMessages.update { current ->
        current + (threadId to current[threadId].orEmpty().map { if (it.id == oldId) message else it })
        }
    }

    private fun completeLocalMessage(threadId: String, localId: String, confirmed: Message) {
        _localMessages.update { current ->
        val messages = current[threadId].orEmpty()
        .filterNot { it.id == localId || it.id == confirmed.id }
        current + (threadId to messages)
        }
    }

    private suspend fun finishLocalMessage(threadId: String, localId: String, confirmed: Message) {
        replaceLocalMessage(threadId, localId, confirmed.copy(localStatus = "sent", localProgress = 100))
        delay(450)
    }

    private fun updateLocalMessage(threadId: String, messageId: String, transform: (Message) -> Message) {
        _localMessages.update { current ->
        current + (threadId to current[threadId].orEmpty().map { if (it.id == messageId) transform(it) else it })
        }
    }

    private fun markLocalMessageFailed(threadId: String, messageId: String, error: String) {
        updateLocalMessage(threadId, messageId) { it.copy(localStatus = "failed", localProgress = 100, localError = error) }
    }

    private fun removeLocalMessage(threadId: String, messageId: String) {
        _localMessages.update { current ->
        current + (threadId to current[threadId].orEmpty().filterNot { it.id == messageId })
        }
    }
}

private fun mergeLocalMessages(server: List<Message>, local: List<Message>): List<Message> {
    if (local.isEmpty()) return server
    val serverIds = server.mapTo(mutableSetOf()) { it.id }
    val pending = local.filter { candidate ->
        candidate.id !in serverIds && !server.any { it.matchesLocalMedia(candidate) }
    }
    return (server + pending)
        .distinctBy { it.id }
        .sortedBy { it.timestamp }
}

private fun Message.matchesLocalMedia(local: Message): Boolean {
    if (local.localStatus !in setOf("uploading", "sending", "sent")) return false
    if (local.type !in setOf("image", "video", "voice", "document", "file")) return false
    if (type != local.type || senderId != local.senderId || mediaUrl.isBlank()) return false
    val serverTime = timestamp.takeIf { it > 0L } ?: return true
    val localTime = local.timestamp.takeIf { it > 0L } ?: return true
    return serverTime in (localTime - 5_000)..(localTime + 10 * 60_000)
}

private fun String.cleanLinkPart(): String =
    trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun String.cleanPhoneForLink(): String =
    filter { it.isDigit() }
