package com.adischat.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.adischat.app.data.model.ChannelPost
import com.adischat.app.data.model.ChannelPostsPage
import com.adischat.app.network.ChannelPostReactBody
import com.adischat.app.network.ChannelPostResponse
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.CreateChannelPostBody
import com.adischat.app.network.EditChannelPostBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class ChannelRepository(private val context: Context) {

    private val api       = CloudflareClient.walletApi
    private val mainApi   = CloudflareClient.api          // uploadMedia lives here

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun ChannelPostResponse.toModel() = ChannelPost(
        id             = id,
        channelId      = channelId,
        authorUid      = authorUid,
        authorName     = authorName,
        authorPhotoUrl = authorPhotoUrl,
        title          = title,
        body           = body,
        postType       = postType,
        mediaUrl       = mediaUrl,
        mediaMimeType  = mediaMimeType,
        linkUrl        = linkUrl,
        linkTitle      = linkTitle,
        linkImage      = linkImage,
        pinned         = pinned,
        views          = views,
        reactions      = reactions,
        createdAt      = createdAt,
        updatedAt      = updatedAt,
        deleted        = deleted,
    )

    // ── Polling flow ──────────────────────────────────────────────────────────

    /**
     * Emits a fresh page of posts every [pollMs] milliseconds.
     * Pinned posts are floated to the top inside the flow.
     */
    fun postsFlow(
        channelId: String,
        pollMs: Long = 10_000L,
    ): Flow<Result<ChannelPostsPage>> = flow {
        while (true) {
            val result = runCatching {
                val resp = api.getChannelPosts(channelId, before = null, limit = 50)
                val models = resp.posts.map { it.toModel() }
                val sorted = models.sortedWith(
                    compareByDescending<ChannelPost> { it.pinned }
                        .thenByDescending { it.createdAt }
                )
                ChannelPostsPage(
                    posts      = sorted,
                    hasMore    = resp.hasMore,
                    nextCursor = resp.nextCursor,
                )
            }
            emit(result)
            delay(pollMs)
        }
    }

    /**
     * Load older posts (pagination). Returns the raw page; caller merges with existing list.
     */
    suspend fun loadMorePosts(channelId: String, before: String): Result<ChannelPostsPage> =
        runCatching {
            val resp = api.getChannelPosts(channelId, before = before, limit = 20)
            ChannelPostsPage(
                posts      = resp.posts.map { it.toModel() },
                hasMore    = resp.hasMore,
                nextCursor = resp.nextCursor,
            )
        }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    suspend fun createPost(
        channelId: String,
        body: CreateChannelPostBody,
    ): Result<ChannelPost> = runCatching {
        api.createChannelPost(channelId, body).toModel()
    }

    suspend fun editPost(
        channelId: String,
        postId: String,
        body: EditChannelPostBody,
    ): Result<ChannelPost> = runCatching {
        api.editChannelPost(channelId, postId, body).toModel()
    }

    suspend fun deletePost(channelId: String, postId: String): Result<Unit> = runCatching {
        api.deleteChannelPost(channelId, postId)
        Unit
    }

    suspend fun togglePin(channelId: String, postId: String): Result<Unit> = runCatching {
        api.toggleChannelPostPin(channelId, postId)
        Unit
    }

    // ── Member actions ────────────────────────────────────────────────────────

    suspend fun react(
        channelId: String,
        postId: String,
        emoji: String,
    ): Result<Unit> = runCatching {
        api.reactToChannelPost(channelId, postId, ChannelPostReactBody(emoji))
        Unit
    }

    suspend fun recordView(channelId: String, postId: String): Result<Unit> = runCatching {
        api.recordChannelPostView(channelId, postId)
        Unit
    }

    // ── Media upload ──────────────────────────────────────────────────────────

    /**
     * Compress an image URI to max 1024px, JPEG 85%, then upload via the
     * existing media upload endpoint.  Returns the remote URL.
     */
    suspend fun uploadPostMedia(uri: Uri): Result<String> = runCatching {
        val bytes = compressImage(uri) ?: error("Failed to decode image")
        val body  = bytes.toRequestBody("image/jpeg".toMediaType())
        val resp  = mainApi.uploadMedia("image", body)
        resp.url
    }

    private fun compressImage(uri: Uri): ByteArray? = runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        val orig   = BitmapFactory.decodeStream(stream)
        stream.close()

        val maxPx  = 1024
        val scaled = if (orig.width > maxPx || orig.height > maxPx) {
            val ratio  = maxPx.toFloat() / maxOf(orig.width, orig.height)
            Bitmap.createScaledBitmap(
                orig,
                (orig.width  * ratio).toInt(),
                (orig.height * ratio).toInt(),
                true,
            )
        } else orig

        ByteArrayOutputStream().also { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }.toByteArray()
    }.getOrNull()
}
