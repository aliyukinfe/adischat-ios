package com.adischat.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.AdminStats
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.repository.AdminRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.GameApprovalResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {

    private val repo = AdminRepository()

    // - Stats -
    private val _stats      = MutableStateFlow<AdminStats?>(null)
    val stats: StateFlow<AdminStats?> = _stats

    private val _loadingStats = MutableStateFlow(false)
    val loadingStats: StateFlow<Boolean> = _loadingStats

    // - Verification requests -
    private val _requests      = MutableStateFlow<List<AdminVerificationRequest>>(emptyList())
    val requests: StateFlow<List<AdminVerificationRequest>> = _requests

    private val _loadingRequests = MutableStateFlow(false)
    val loadingRequests: StateFlow<Boolean> = _loadingRequests

    private val _requestStatus   = MutableStateFlow("pending")
    val requestStatus: StateFlow<String> = _requestStatus

    private val _gameApprovals = MutableStateFlow<List<GameApprovalResponse>>(emptyList())
    val gameApprovals: StateFlow<List<GameApprovalResponse>> = _gameApprovals

    private val _loadingGameApprovals = MutableStateFlow(false)
    val loadingGameApprovals: StateFlow<Boolean> = _loadingGameApprovals

    private val _gameApprovalStatus = MutableStateFlow("pending")
    val gameApprovalStatus: StateFlow<String> = _gameApprovalStatus

    // - Channel verification requests -
    private val _channelRequests = MutableStateFlow<List<com.adischat.app.network.ChannelVerificationResponse>>(emptyList())
    val channelRequests: StateFlow<List<com.adischat.app.network.ChannelVerificationResponse>> = _channelRequests

    private val _loadingChannelRequests = MutableStateFlow(false)
    val loadingChannelRequests: StateFlow<Boolean> = _loadingChannelRequests

    // - Users -
    private val _users      = MutableStateFlow<List<AppUser>>(emptyList())
    val users: StateFlow<List<AppUser>> = _users

    private val _loadingUsers = MutableStateFlow(false)
    val loadingUsers: StateFlow<Boolean> = _loadingUsers

    private val _searchQuery  = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _loadingMore  = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var currentPage = 0

    // - Toast -
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    // - Init -
    init {
        loadStats()
        loadRequests("pending")
        loadGameApprovals("pending")
        loadChannelRequests("pending")
        loadUsers("")

        // Debounce search: collectLatest cancels previous on new value
        viewModelScope.launch {
        _searchQuery.collect { q ->
        delay(400L)
        currentPage = 0
        loadUsersInternal(q, 0, replace = true)
        }
        }
    }

    // - Stats -
    fun loadStats() {
        _loadingStats.value = true
        viewModelScope.launch {
        repo.getStats()
        .onSuccess  { _stats.value = it }
        .onFailure  { _toast.value = friendly(it) }
        _loadingStats.value = false
        }
    }

    // - Verification requests -
    fun loadRequests(status: String) {
        _requestStatus.value  = status
        _loadingRequests.value = true
        viewModelScope.launch {
        repo.getRequests(status)
        .onSuccess  { _requests.value = it }
        .onFailure  { _toast.value = friendly(it) }
        _loadingRequests.value = false
        }
    }

    fun approveVerification(uid: String) {
        viewModelScope.launch {
        repo.approve(uid)
        .onSuccess {
        _toast.value = "- Approved"
        loadRequests(_requestStatus.value)
        loadStats()
        }
        .onFailure { _toast.value = friendly(it, "Failed to approve") }
        }
    }

    /**
     * Admin grants the blue badge (verification level 3) directly to a user,
     * without requiring a submitted verification request. Reuses the approve
     * endpoint. A dedicated badge-request flow will be added later.
     */
    fun grantBlueBadge(uid: String) {
        viewModelScope.launch {
        repo.approve(uid)
        .onSuccess {
        _toast.value = "- Blue badge granted"
        _users.value = _users.value.map { u ->
        if (u.uid == uid) u.copy(verificationLevel = 3, verificationRequestStatus = "approved") else u
        }
        loadStats()
        }
        .onFailure { _toast.value = friendly(it, "Failed to grant badge") }
        }
    }

    fun rejectVerification(uid: String, reason: String) {
        viewModelScope.launch {
        repo.reject(uid, reason)
        .onSuccess {
        _toast.value = "Rejected"
        loadRequests(_requestStatus.value)
        loadStats()
        }
        .onFailure { _toast.value = friendly(it, "Failed to reject") }
        }
    }

    fun loadChannelRequests(status: String) {
        _loadingChannelRequests.value = true
        viewModelScope.launch {
        repo.getChannelVerifications(status)
        .onSuccess { _channelRequests.value = it }
        .onFailure { _toast.value = friendly(it) }
        _loadingChannelRequests.value = false
        }
    }

    fun approveChannelVerification(channelId: String) {
        viewModelScope.launch {
        repo.approveChannelVerification(channelId)
        .onSuccess {
        _toast.value = "- Channel verified"
        loadChannelRequests("pending")
        }
        .onFailure { _toast.value = friendly(it, "Failed to approve channel") }
        }
    }

    fun rejectChannelVerification(channelId: String, reason: String) {
        viewModelScope.launch {
        repo.rejectChannelVerification(channelId, reason)
        .onSuccess {
        _toast.value = "Channel request rejected"
        loadChannelRequests("pending")
        }
        .onFailure { _toast.value = friendly(it, "Failed to reject channel") }
        }
    }

    fun loadGameApprovals(status: String) {
        _gameApprovalStatus.value = status
        _loadingGameApprovals.value = true
        viewModelScope.launch {
        repo.getGameApprovals(status)
        .onSuccess { _gameApprovals.value = it }
        .onFailure { _toast.value = friendly(it) }
        _loadingGameApprovals.value = false
        }
    }

    fun approveGame(channelId: String) {
        viewModelScope.launch {
        repo.approveGame(channelId)
        .onSuccess {
        _toast.value = "Game approved"
        loadGameApprovals(_gameApprovalStatus.value)
        }
        .onFailure { _toast.value = friendly(it, "Failed to approve game") }
        }
    }

    fun rejectGame(channelId: String, note: String) {
        viewModelScope.launch {
        repo.rejectGame(channelId, note)
        .onSuccess {
        _toast.value = "Game rejected"
        loadGameApprovals(_gameApprovalStatus.value)
        }
        .onFailure { _toast.value = friendly(it, "Failed to reject game") }
        }
    }

    // - Users -
    fun updateSearch(q: String) {
        _searchQuery.value = q
    }

    fun loadNextPage() {
        if (_loadingMore.value) return
        currentPage++
        _loadingMore.value = true
        viewModelScope.launch {
        repo.getUsers(_searchQuery.value, currentPage)
        .onSuccess  { _users.value = _users.value + it }
        .onFailure  { currentPage--; _toast.value = friendly(it) }
        _loadingMore.value = false
        }
    }

    fun setUserRole(uid: String, role: String) {
        viewModelScope.launch {
        repo.setRole(uid, role)
        .onSuccess {
        _toast.value = when (role) {
        "admin" -> "Promoted to admin"
        "merchant" -> "Merchant role activated"
        else -> "Role changed to user"
        }
        // Refresh the user list in-place
        _users.value = _users.value.map { u ->
        if (u.uid == uid) u.copy(role = role) else u
        }
        }
        .onFailure { _toast.value = friendly(it, "Failed to update role") }
        }
    }

    fun addWalletMoney(uid: String, amount: Double, reason: String) {
        if (amount <= 0.0) {
        _toast.value = "Enter a valid amount"
        return
        }
        viewModelScope.launch {
        repo.addWalletMoney(uid, amount, reason)
        .onSuccess {
        _toast.value = "Wallet topped up"
        loadStats()
        }
        .onFailure { _toast.value = friendly(it, "Failed to add money") }
        }
    }

    private fun loadUsers(query: String) {
        _loadingUsers.value = true
        currentPage = 0
        viewModelScope.launch {
        repo.getUsers(query, 0)
        .onSuccess  { _users.value = it }
        .onFailure  { _toast.value = friendly(it) }
        _loadingUsers.value = false
        }
    }

    private fun loadUsersInternal(query: String, page: Int, replace: Boolean) {
        if (page == 0) _loadingUsers.value = true else _loadingMore.value = true
        viewModelScope.launch {
        repo.getUsers(query, page)
        .onSuccess  { list -> _users.value = if (replace) list else _users.value + list }
        .onFailure  { _toast.value = friendly(it) }
        if (page == 0) _loadingUsers.value = false else _loadingMore.value = false
        }
    }

    fun clearToast() { _toast.value = null }

    private fun friendly(error: Throwable?, fallback: String = "Request failed. Please try again."): String =
        CloudflareClient.friendlyErrorMessage(error, fallback)
}
