package com.adischat.app.data.repository

import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.ChatBlockBody
import com.adischat.app.network.ChatReportBody
import com.adischat.app.network.ChatSettingsBody
import com.adischat.app.network.CreateCommunityBody
import com.adischat.app.network.GenericResponse
import com.adischat.app.network.MessageDeleteBody
import com.adischat.app.network.MessageEditBody
import com.adischat.app.network.MessagePinBody
import com.adischat.app.network.MessageReactionBody
import com.adischat.app.network.MessageStarBody
import com.adischat.app.network.MessageTranslateBody
import com.adischat.app.network.SendMessageBody
import com.adischat.app.network.UpdateCommunityBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

class ChatRepository {

    private val api = CloudflareClient.api
    private val walletApi = CloudflareClient.walletApi

    // - Conversations -

    fun observeAllConversations(): Flow<List<Conversation>> = pollingFlow("conversations") {
        api.getConversations()
    }

    // One-shot fetches used by the global unread/badge aggregator (UnreadCenter).
    suspend fun getConversationsOnce(): List<Conversation> = api.getConversations()
    suspend fun getCommunitiesOnce(type: String): List<CommunityRoom> = walletApi.getCommunities(type, "")
    suspend fun getCommunityMessagesOnce(roomId: String): List<Message> = walletApi.getCommunityMessages(roomId)

    fun observeMessages(userId: String): Flow<List<Message>> = pollingFlow("messages:$userId") {
        api.getMessages(userId)
    }

    fun observeCommunities(type: String, query: String): Flow<List<CommunityRoom>> = pollingFlow("communities:$type") {
        walletApi.getCommunities(type, query)
    }

    fun observeCommunityMessages(roomId: String): Flow<List<Message>> = pollingFlow("communityMessages:$roomId") {
        walletApi.getCommunityMessages(roomId)
    }

    suspend fun createCommunity(type: String, name: String, description: String): Result<CommunityRoom> = runCatching {
        walletApi.createCommunity(CreateCommunityBody(type = type, name = name, description = description))
    }

    suspend fun updateCommunity(roomId: String, name: String, description: String, photoUrl: String): Result<CommunityRoom> = runCatching {
        walletApi.updateCommunity(roomId, UpdateCommunityBody(name = name, description = description, photoUrl = photoUrl))
    }

    suspend fun deleteCommunity(roomId: String): Result<Unit> = runCatching {
        walletApi.deleteCommunity(roomId)
    }

    /** Upload raw JPEG bytes and return the CDN URL. */
    suspend fun uploadCommunityPhoto(bytes: ByteArray): Result<String> = runCatching {
        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        api.uploadMedia("image", body).url
    }

    suspend fun joinCommunity(roomId: String): Result<Unit> = runCatching {
        walletApi.joinCommunity(roomId)
    }

    suspend fun leaveCommunity(roomId: String, type: String = "group"): Result<Unit> = runCatching {
        val isChannel = type.equals("channel", ignoreCase = true)
        runCatching {
        if (isChannel) walletApi.unfollowCommunity(roomId)
        else walletApi.leaveCommunity(roomId)
        }
        .recoverCatching { firstError ->
        val message = firstError.message.orEmpty().lowercase()
        if ("not found" in message || "404" in message) {
        runCatching {
        if (isChannel) walletApi.leaveCommunity(roomId)
        else walletApi.unfollowCommunity(roomId)
        }.recoverCatching { secondError ->
        val secondMessage = secondError.message.orEmpty().lowercase()
        if ("not found" in secondMessage || "404" in secondMessage) {
        runCatching { walletApi.leaveCommunityMembership(roomId) }
        .recoverCatching { thirdError ->
        val thirdMessage = thirdError.message.orEmpty().lowercase()
        if ("not found" in thirdMessage || "404" in thirdMessage) GenericResponse()
        else throw thirdError
        }
        .getOrThrow()
        }
        else throw secondError
        }.getOrThrow()
        } else {
        throw firstError
        }
        }
        .getOrThrow()
    }

    // - Text message -

    suspend fun sendMessage(
        conversationUserId: String,
        conversationUserName: String,
        conversationUserPhotoUrl: String,
        @Suppress("UNUSED_PARAMETER") senderId: String,
        @Suppress("UNUSED_PARAMETER") senderRole: String,
        text: String,
        replyToMessageId: String = "",
    ): Result<Message> = runCatching {
        api.sendMessage(
        conversationUserId,
        SendMessageBody(
        text      = text,
        type      = "text",
        replyToMessageId      = replyToMessageId,
        conversationUserName     = conversationUserName,
        conversationUserPhotoUrl = conversationUserPhotoUrl,
        ),
        )
    }

    suspend fun sendCommunityMessage(
        roomId: String,
        text: String,
        replyToMessageId: String = "",
    ): Result<Message> = runCatching {
        walletApi.sendCommunityMessage(roomId, SendMessageBody(text = text, type = "text", replyToMessageId = replyToMessageId))
    }

    /**
     * Forward an existing message to another chat / group / channel. Forwarded media is
     * already uploaded, so we just re-send a message that points at the same URL - no
     * re-upload. Falls back to a plain text message when it isn't real media.
     */
    suspend fun forwardMessage(
        targetId: String,
        isCommunity: Boolean,
        senderName: String,
        senderPhotoUrl: String,
        text: String,
        type: String = "text",
        mediaUrl: String = "",
        mediaMimeType: String = "",
        durationMs: Long = 0,
        forwardedFromMessageId: String = "",
        forwardedFromSenderName: String = "",
    ): Result<Message> = runCatching {
        val isMedia = type in setOf("image", "video", "voice") && mediaUrl.isNotBlank()
        val body = SendMessageBody(
        text      = text,
        type      = if (isMedia) type else "text",
        mediaUrl      = if (isMedia) mediaUrl else "",
        mediaMimeType      = if (isMedia) mediaMimeType else "",
        durationMs      = if (isMedia) durationMs else 0,
        forwarded      = true,
        forwardedFromMessageId   = forwardedFromMessageId,
        forwardedFromSenderName  = forwardedFromSenderName,
        conversationUserName     = senderName,
        conversationUserPhotoUrl = senderPhotoUrl,
        )
        if (isCommunity) walletApi.sendCommunityMessage(targetId, body)
        else api.sendMessage(targetId, body)
    }

    suspend fun reactToCommunityMessage(roomId: String, messageId: String, emoji: String): Result<Unit> = runCatching {
        walletApi.reactToCommunityMessage(roomId, messageId, MessageReactionBody(emoji))
    }

    suspend fun reactToMessage(isCommunity: Boolean, threadId: String, messageId: String, emoji: String): Result<Unit> = runCatching {
        if (isCommunity) walletApi.reactToCommunityMessage(threadId, messageId, MessageReactionBody(emoji))
        else api.reactToMessage(threadId, messageId, MessageReactionBody(emoji))
    }

    suspend fun deleteCommunityMessage(roomId: String, messageId: String): Result<Unit> = runCatching {
        walletApi.deleteCommunityMessage(roomId, messageId)
    }

    suspend fun editMessage(isCommunity: Boolean, threadId: String, messageId: String, text: String) = runCatching {
        if (isCommunity) walletApi.editCommunityMessage(threadId, messageId, MessageEditBody(text))
        else api.editMessage(threadId, messageId, MessageEditBody(text))
    }

    suspend fun deleteMessageAction(isCommunity: Boolean, threadId: String, messageId: String, mode: String) = runCatching {
        if (isCommunity) walletApi.deleteCommunityMessageAction(threadId, messageId, MessageDeleteBody(mode))
        else api.deleteMessageAction(threadId, messageId, MessageDeleteBody(mode))
    }

    suspend fun pinMessage(isCommunity: Boolean, threadId: String, messageId: String, pinned: Boolean) = runCatching {
        if (isCommunity) walletApi.pinCommunityMessage(threadId, messageId, MessagePinBody(pinned))
        else api.pinMessage(threadId, messageId, MessagePinBody(pinned))
    }

    suspend fun starMessage(isCommunity: Boolean, threadId: String, messageId: String, starred: Boolean) = runCatching {
        if (isCommunity) walletApi.starCommunityMessage(threadId, messageId, MessageStarBody(starred))
        else api.starMessage(threadId, messageId, MessageStarBody(starred))
    }

    suspend fun listenMessage(isCommunity: Boolean, threadId: String, messageId: String) = runCatching {
        if (isCommunity) walletApi.listenCommunityMessage(threadId, messageId)
        else api.listenMessage(threadId, messageId)
    }

    suspend fun translateMessage(isCommunity: Boolean, threadId: String, messageId: String, targetLanguage: String) = runCatching {
        if (isCommunity) walletApi.translateCommunityMessage(threadId, messageId, MessageTranslateBody(targetLanguage))
        else api.translateMessage(threadId, messageId, MessageTranslateBody(targetLanguage))
    }

    suspend fun searchThreadMessages(isCommunity: Boolean, threadId: String, query: String): Result<List<Message>> = runCatching {
        if (isCommunity) walletApi.searchCommunityMessages(threadId, query)
        else api.searchConversationMessages(threadId, query)
    }

    suspend fun exportThread(isCommunity: Boolean, threadId: String) = runCatching {
        if (isCommunity) walletApi.exportCommunityChat(threadId)
        else api.exportConversation(threadId)
    }

    suspend fun clearThread(isCommunity: Boolean, threadId: String) = runCatching {
        if (isCommunity) walletApi.clearCommunityChat(threadId)
        else api.clearConversation(threadId)
    }

    suspend fun updateThreadSettings(isCommunity: Boolean, threadId: String, body: ChatSettingsBody) = runCatching {
        if (isCommunity) walletApi.updateCommunityChatSettings(threadId, body)
        else api.updateConversationSettings(threadId, body)
    }

    suspend fun reportThread(isCommunity: Boolean, threadId: String, body: ChatReportBody) = runCatching {
        if (isCommunity) walletApi.reportCommunityChat(threadId, body)
        else api.reportConversation(threadId, body)
    }

    suspend fun blockConversationUser(threadId: String, blockedUid: String, reason: String = "") = runCatching {
        api.blockConversationUser(threadId, ChatBlockBody(blockedUid, reason))
    }

    suspend fun moderateCommunityMember(roomId: String, uid: String, action: String): Result<Unit> = runCatching {
        walletApi.moderateCommunityMember(roomId, uid, action)
    }

    // - Media message (image / video / voice) -

    suspend fun uploadAndSendMedia(
        conversationUserId: String,
        conversationUserName: String,
        conversationUserPhotoUrl: String,
        bytes: ByteArray,
        mimeType: String,
        type: String,      // "image" | "video" | "voice"
        caption: String = "",
        durationMs: Long = 0,
        onProgress: (Int) -> Unit = {},
    ): Result<Message> = runCatching {
        val body     = bytes.toProgressRequestBody(mimeType, onProgress)
        val uploaded = api.uploadMedia(type, body)
        onProgress(96)
        api.sendMessage(
        conversationUserId,
        SendMessageBody(
        text      = caption,
        type      = type,
        mediaUrl      = uploaded.url,
        mediaMimeType      = mimeType,
        durationMs      = durationMs,
        conversationUserName     = conversationUserName,
        conversationUserPhotoUrl = conversationUserPhotoUrl,
        ),
        ).also { onProgress(100) }
    }

    suspend fun uploadAndSendCommunityMedia(
        roomId: String,
        bytes: ByteArray,
        mimeType: String,
        type: String,
        caption: String = "",
        durationMs: Long = 0,
        onProgress: (Int) -> Unit = {},
    ): Result<Message> = runCatching {
        val body = bytes.toProgressRequestBody(mimeType, onProgress)
        val uploaded = api.uploadMedia(type, body)
        onProgress(96)
        walletApi.sendCommunityMessage(
        roomId,
        SendMessageBody(
        text = caption,
        type = type,
        mediaUrl = uploaded.url,
        mediaMimeType = mimeType,
        durationMs = durationMs,
        ),
        ).also { onProgress(100) }
    }

    // - P2P -

    suspend fun searchUsers(query: String): List<com.adischat.app.data.model.UserSummary> =
        api.searchUsers(query)

    // - Presence -

    /** Marks the current user as active. Silently ignores network failures. */
    suspend fun sendHeartbeat() { runCatching { api.heartbeat() } }

    /** Fetches a peer's online state and last-seen timestamp (unix seconds). */
    suspend fun getPresence(uid: String): com.adischat.app.network.PresenceResponse? =
        runCatching { api.getPresence(uid) }.getOrNull()

    suspend fun searchCommunities(type: String, query: String): List<CommunityRoom> =
        walletApi.getCommunities(type, query)

    suspend fun resolveAdisLink(slug: String): Result<AdisLinkTarget> = runCatching {
        walletApi.resolveAdisLink(slug)
    }

    /** Creates (or retrieves) a direct conversation; returns the conversation ID. */
    suspend fun startConversation(targetUid: String): Result<String> = runCatching {
        api.startConversation(targetUid).conversationId
    }

    // - Mark read -

    suspend fun markRead(conversationUserId: String, @Suppress("UNUSED_PARAMETER") asRole: String) {
        runCatching { api.markRead(conversationUserId) }
    }

    // - Helpers -

    /**
     * Emits the result of [fetch] immediately, then re-polls. History must appear
     * as soon as the API responds, never only after a websocket/poll cycle, so:
     *  - the FIRST fetch runs with no leading delay;
     *  - until the first successful emit, failures retry fast (1s) instead of
     *    waiting a full 3s, so a slow/transient first call can't leave the screen
     *    blank for ~10s;
     *  - once data has shown, it settles into a slower refresh so Cloudflare
     *    quota is not burned by every open screen.
     * Failures and empty results are logged (TODO: temporary diagnostics).
     */
    private fun <T> pollingFlow(label: String = "", fetch: suspend () -> T): Flow<T> = flow {
        var emittedOnce = false
        while (true) {
        runCatching { fetch() }
        .onSuccess { value ->
        val size = (value as? Collection<*>)?.size
        android.util.Log.d("ChatLoad", "[$label] ok" + (size?.let { " count=$it" } ?: ""))
        emit(value)
        emittedOnce = true
        }
        .onFailure { e ->
        android.util.Log.w("ChatLoad", "[$label] fetch failed: ${e.message}")
        }
        delay(if (emittedOnce) 15_000 else 2_500)
        }
    }

    private fun ByteArray.toProgressRequestBody(
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): RequestBody = ProgressRequestBody(this, mimeType.toMediaTypeOrNull(), onProgress)

    private class ProgressRequestBody(
        private val bytes: ByteArray,
        private val mediaType: MediaType?,
        private val onProgress: (Int) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType? = mediaType
        override fun contentLength(): Long = bytes.size.toLong()

        override fun writeTo(sink: BufferedSink) {
        val total = bytes.size.coerceAtLeast(1)
        var written = 0
        var lastPercent = 0
        val chunkSize = 32 * 1024

        onProgress(1)
        while (written < bytes.size) {
        val count = minOf(chunkSize, bytes.size - written)
        sink.write(bytes, written, count)
        written += count

        val percent = ((written.toDouble() / total.toDouble()) * 90.0)
        .toInt()
        .coerceIn(1, 90)
        if (percent != lastPercent) {
        lastPercent = percent
        onProgress(percent)
        }
        }
        }
    }
}
