package com.adischat.app.data.repository

import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Message
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.CreateCommunityBody
import com.adischat.app.network.MessageReactionBody
import com.adischat.app.network.SendMessageBody
import com.adischat.app.network.UpdateCommunityBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository {

    private val api = CloudflareClient.api
    private val walletApi = CloudflareClient.walletApi

    // ── Conversations ─────────────────────────────────────────────────────────

    fun observeAllConversations(): Flow<List<Conversation>> = pollingFlow {
        api.getConversations()
    }

    fun observeMessages(userId: String): Flow<List<Message>> = pollingFlow {
        api.getMessages(userId)
    }

    fun observeCommunities(type: String, query: String): Flow<List<CommunityRoom>> = pollingFlow {
        walletApi.getCommunities(type, query)
    }

    fun observeCommunityMessages(roomId: String): Flow<List<Message>> = pollingFlow {
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

    // ── Text message ──────────────────────────────────────────────────────────

    suspend fun sendMessage(
        conversationUserId: String,
        conversationUserName: String,
        conversationUserPhotoUrl: String,
        @Suppress("UNUSED_PARAMETER") senderId: String,
        @Suppress("UNUSED_PARAMETER") senderRole: String,
        text: String,
    ): Result<Unit> = runCatching {
        api.sendMessage(
            conversationUserId,
            SendMessageBody(
                text                     = text,
                type                     = "text",
                conversationUserName     = conversationUserName,
                conversationUserPhotoUrl = conversationUserPhotoUrl,
            ),
        )
    }

    suspend fun sendCommunityMessage(
        roomId: String,
        text: String,
    ): Result<Unit> = runCatching {
        walletApi.sendCommunityMessage(roomId, SendMessageBody(text = text, type = "text"))
    }

    suspend fun reactToCommunityMessage(roomId: String, messageId: String, emoji: String): Result<Unit> = runCatching {
        walletApi.reactToCommunityMessage(roomId, messageId, MessageReactionBody(emoji))
    }

    suspend fun deleteCommunityMessage(roomId: String, messageId: String): Result<Unit> = runCatching {
        walletApi.deleteCommunityMessage(roomId, messageId)
    }

    suspend fun moderateCommunityMember(roomId: String, uid: String, action: String): Result<Unit> = runCatching {
        walletApi.moderateCommunityMember(roomId, uid, action)
    }

    // ── Media message (image / video / voice) ─────────────────────────────────

    suspend fun uploadAndSendMedia(
        conversationUserId: String,
        conversationUserName: String,
        conversationUserPhotoUrl: String,
        bytes: ByteArray,
        mimeType: String,
        type: String,           // "image" | "video" | "voice"
        caption: String = "",
        durationMs: Long = 0,
    ): Result<Unit> = runCatching {
        val body     = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val uploaded = api.uploadMedia(type, body)
        api.sendMessage(
            conversationUserId,
            SendMessageBody(
                text                     = caption,
                type                     = type,
                mediaUrl                 = uploaded.url,
                mediaMimeType            = mimeType,
                durationMs               = durationMs,
                conversationUserName     = conversationUserName,
                conversationUserPhotoUrl = conversationUserPhotoUrl,
            ),
        )
    }

    // ── P2P ───────────────────────────────────────────────────────────────────

    suspend fun searchUsers(query: String): List<com.adischat.app.data.model.UserSummary> =
        api.searchUsers(query)

    /** Creates (or retrieves) a direct conversation; returns the conversation ID. */
    suspend fun startConversation(targetUid: String): Result<String> = runCatching {
        api.startConversation(targetUid).conversationId
    }

    // ── Mark read ─────────────────────────────────────────────────────────────

    suspend fun markRead(conversationUserId: String, @Suppress("UNUSED_PARAMETER") asRole: String) {
        runCatching { api.markRead(conversationUserId) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
        while (true) {
            runCatching { emit(fetch()) }
            delay(3_000)
        }
    }
}
