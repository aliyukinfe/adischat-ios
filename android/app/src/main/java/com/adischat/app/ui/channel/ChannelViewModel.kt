package com.adischat.app.ui.channel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.ChannelPost
import com.adischat.app.data.repository.ChannelRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.CreateChannelPostBody
import com.adischat.app.network.EditChannelPostBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

data class ChannelUiState(
    val posts:          List<ChannelPost> = emptyList(),
    val isLoading:      Boolean           = true,
    val isLoadingMore:  Boolean           = false,
    val hasMore:        Boolean           = false,
    val nextCursor:     String?           = null,
    val error:          String?           = null,
    /** Set while an admin action (create / edit / delete / pin) is running */
    val actionLoading:  Boolean           = false,
    val actionError:    String?           = null,
    /** Set while a media file is being compressed and uploaded */
    val mediaUploading: Boolean           = false,
    val mediaUploadUrl: String?           = null,
    val mediaUploadError: String?         = null,
    /** Transient success snackbar text */
    val successMessage: String?           = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChannelViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChannelRepository(app.applicationContext)

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private var currentChannelId: String = ""
    private var pollJob: kotlinx.coroutines.Job? = null

    // ── Init / polling ────────────────────────────────────────────────────────

    fun init(channelId: String) {
        if (currentChannelId == channelId) return
        currentChannelId = channelId
        // Reset state so stale posts from the previous channel don't flash
        _state.value = ChannelUiState()
        startPolling(channelId)
    }

    private fun startPolling(channelId: String) {
        pollJob?.cancel()          // stop any running poll for a previous channel
        pollJob = viewModelScope.launch {
            repo.postsFlow(channelId, pollMs = 10_000L)
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = CloudflareClient.friendlyErrorMessage(e)) }
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { page ->
                            _state.update { s ->
                                s.copy(
                                    posts      = page.posts,
                                    hasMore    = page.hasMore,
                                    nextCursor = page.nextCursor,
                                    isLoading  = false,
                                    error      = null,
                                )
                            }
                        },
                        onFailure = { e ->
                            _state.update { it.copy(isLoading = false, error = CloudflareClient.friendlyErrorMessage(e)) }
                        },
                    )
                }
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val result = repo.loadMorePosts(currentChannelId, cursor)
            result.fold(
                onSuccess = { page ->
                    _state.update { s ->
                        s.copy(
                            posts         = s.posts + page.posts,
                            hasMore       = page.hasMore,
                            nextCursor    = page.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoadingMore = false, error = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    // ── Admin: create post ────────────────────────────────────────────────────

    fun createPost(
        title:         String,
        body:          String,
        postType:      String,
        mediaUrl:      String      = "",
        mediaMimeType: String      = "",
        linkUrl:       String      = "",
        linkTitle:     String      = "",
        linkImage:     String      = "",
        onSuccess: () -> Unit      = {},
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, actionError = null) }
            val result = repo.createPost(
                currentChannelId,
                CreateChannelPostBody(
                    title         = title,
                    body          = body,
                    postType      = postType,
                    mediaUrl      = mediaUrl,
                    mediaMimeType = mediaMimeType,
                    linkUrl       = linkUrl,
                    linkTitle     = linkTitle,
                    linkImage     = linkImage,
                ),
            )
            result.fold(
                onSuccess = { post ->
                    _state.update { s ->
                        s.copy(
                            posts         = listOf(post) + s.posts,
                            actionLoading = false,
                            successMessage = "Post published",
                        )
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    _state.update { it.copy(actionLoading = false, actionError = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    // ── Admin: edit post ──────────────────────────────────────────────────────

    fun editPost(
        postId:    String,
        title:     String?,
        body:      String?,
        linkUrl:   String?,
        linkTitle: String?,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, actionError = null) }
            val result = repo.editPost(
                currentChannelId, postId,
                EditChannelPostBody(title = title, body = body, linkUrl = linkUrl, linkTitle = linkTitle),
            )
            result.fold(
                onSuccess = { updated ->
                    _state.update { s ->
                        s.copy(
                            posts         = s.posts.map { if (it.id == postId) updated else it },
                            actionLoading = false,
                            successMessage = "Post updated",
                        )
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    _state.update { it.copy(actionLoading = false, actionError = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    // ── Admin: delete post ────────────────────────────────────────────────────

    fun deletePost(postId: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionLoading = true, actionError = null) }
            repo.deletePost(currentChannelId, postId).fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            posts         = s.posts.filter { it.id != postId },
                            actionLoading = false,
                            successMessage = "Post deleted",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(actionLoading = false, actionError = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    // ── Admin: pin / unpin ────────────────────────────────────────────────────

    fun togglePin(postId: String) {
        viewModelScope.launch {
            repo.togglePin(currentChannelId, postId).fold(
                onSuccess = {
                    // Optimistic toggle; poll will refresh authoritative state
                    _state.update { s ->
                        s.copy(
                            posts = s.posts.map {
                                if (it.id == postId) it.copy(pinned = !it.pinned) else it
                            }.sortedWith(
                                compareByDescending<ChannelPost> { it.pinned }
                                    .thenByDescending { it.createdAt }
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(actionError = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    // ── Member: react ─────────────────────────────────────────────────────────

    fun react(postId: String, emoji: String) {
        viewModelScope.launch {
            repo.react(currentChannelId, postId, emoji)
            // Poll will bring the updated reaction map
        }
    }

    // ── Member: record view ───────────────────────────────────────────────────

    fun recordView(postId: String) {
        viewModelScope.launch {
            repo.recordView(currentChannelId, postId)
        }
    }

    // ── Media upload ──────────────────────────────────────────────────────────

    fun uploadMedia(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(mediaUploading = true, mediaUploadError = null, mediaUploadUrl = null) }
            repo.uploadPostMedia(uri).fold(
                onSuccess = { url ->
                    _state.update { it.copy(mediaUploading = false, mediaUploadUrl = url) }
                },
                onFailure = { e ->
                    _state.update { it.copy(mediaUploading = false, mediaUploadError = CloudflareClient.friendlyErrorMessage(e)) }
                },
            )
        }
    }

    fun clearMediaUpload() {
        _state.update { it.copy(mediaUploadUrl = null, mediaUploadError = null) }
    }

    // ── Dismiss helpers ───────────────────────────────────────────────────────

    fun dismissError()          = _state.update { it.copy(error = null, actionError = null) }
    fun dismissSuccessMessage() = _state.update { it.copy(successMessage = null) }
}
