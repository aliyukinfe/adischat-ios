package com.adischat.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.FeatureFlags
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.VerificationStatus
import com.adischat.app.data.preferences.WalletState
import com.adischat.app.data.preferences.WalletTransaction
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.VerificationRepository
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.PrivacyUpdateBody
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val authRepo   = AuthRepository()
    private val verifyRepo = VerificationRepository()
    private val walletRepo = WalletRepository()

    private val _user = MutableStateFlow<AppUser?>(null)
    val user: StateFlow<AppUser?> = _user

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    private val _wallet = MutableStateFlow(WalletState())
    val wallet: StateFlow<WalletState> = _wallet

    private val _lastTransaction = MutableStateFlow<WalletTransaction?>(null)
    val lastTransaction: StateFlow<WalletTransaction?> = _lastTransaction

    private val _walletLoading = MutableStateFlow(false)
    val walletLoading: StateFlow<Boolean> = _walletLoading

    // - Verification -

    private val _verificationStatus = MutableStateFlow<VerificationStatus?>(null)
    val verificationStatus: StateFlow<VerificationStatus?> = _verificationStatus

    private val _submittingVerification = MutableStateFlow(false)
    val submittingVerification: StateFlow<Boolean> = _submittingVerification

    // - Admin -

    private val _adminRequests = MutableStateFlow<List<AdminVerificationRequest>>(emptyList())
    val adminRequests: StateFlow<List<AdminVerificationRequest>> = _adminRequests

    private val _adminLoading = MutableStateFlow(false)
    val adminLoading: StateFlow<Boolean> = _adminLoading

    // - Upload state (badge application) -

    private val _documentUrl = MutableStateFlow("")
    val documentUrl: StateFlow<String> = _documentUrl

    private val _selfieUrl = MutableStateFlow("")
    val selfieUrl: StateFlow<String> = _selfieUrl

    private val _uploadingDocument = MutableStateFlow(false)
    val uploadingDocument: StateFlow<Boolean> = _uploadingDocument

    private val _uploadingSelfie = MutableStateFlow(false)
    val uploadingSelfie: StateFlow<Boolean> = _uploadingSelfie

    // - Init -

    init { loadUser() }

    fun loadUser() {
        viewModelScope.launch {
        try {
        val loaded = authRepo.getCurrentAppUser()
        val uid = authRepo.currentUser?.uid
        if (loaded != null && loaded.uid == uid) _user.value = loaded
        else if (_user.value?.uid != uid) _user.value = null
        if (loaded == null && uid != null) _toast.value = CloudflareClient.CONNECTION_ERROR_MESSAGE
        } catch (e: CancellationException) {
        throw e
        } catch (e: Exception) {
        if (_user.value?.uid != authRepo.currentUser?.uid) _user.value = null
        _toast.value = CloudflareClient.friendlyErrorMessage(e, "Could not load your profile")
        }
        refreshVerificationStatus()
        if (FeatureFlags.FEATURE_WALLET_ENABLED) loadWallet()
        }
    }

    fun loadWallet() {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.getWallet()
        .onSuccess { _wallet.value = it }
        .onFailure { _toast.value = friendly(it, "Failed to load wallet") }
        _walletLoading.value = false
        }
    }

    fun activateWallet(username: String, phone: String, pin: String = "") {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.activate(username, phone, pin)
        .onSuccess {
        _wallet.value = it
        _toast.value = "Wallet activated"
        }
        .onFailure { _toast.value = friendly(it, "Failed to activate wallet") }
        _walletLoading.value = false
        }
    }

    fun changeWalletPin(oldPin: String, newPin: String) {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.changePin(oldPin, newPin)
        .onSuccess {
        _wallet.value = it
        _toast.value = "Wallet PIN updated"
        }
        .onFailure { _toast.value = friendly(it, "Failed to change PIN") }
        _walletLoading.value = false
        }
    }

    fun deposit(amount: Double) {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.deposit(amount)
        .onSuccess {
        _wallet.value = it
        _toast.value = "Deposit added"
        }
        .onFailure { _toast.value = friendly(it, "Deposit failed") }
        _walletLoading.value = false
        }
    }

    fun sendMoney(recipient: String, amount: Double, pin: String = "") {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.sendDetailed(recipient, amount, pin)
        .onSuccess {
        _wallet.value = it.first
        _lastTransaction.value = it.second
        }
        .onFailure { _toast.value = friendly(it, "Send failed") }
        _walletLoading.value = false
        }
    }

    fun loadReceiveQr() {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        viewModelScope.launch {
        walletRepo.getReceivePayload()
        .onSuccess { _wallet.value = _wallet.value.copy(receiveQrPayload = it) }
        .onFailure { _toast.value = friendly(it, "Failed to load receive QR") }
        }
    }

    fun loadUsernameQr() {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        viewModelScope.launch {
        walletRepo.getUsernamePayload()
        .onSuccess { _wallet.value = _wallet.value.copy(usernameQrPayload = it) }
        .onFailure { _toast.value = friendly(it, "Failed to load username QR") }
        }
    }

    fun loadPayCode() {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        _walletLoading.value = true
        viewModelScope.launch {
        walletRepo.getPayCode()
        .onSuccess { _wallet.value = it }
        .onFailure { _toast.value = friendly(it, "Failed to load pay code") }
        _walletLoading.value = false
        }
    }

    fun loadWalletTransactions() {
        if (!FeatureFlags.FEATURE_WALLET_ENABLED) return
        viewModelScope.launch {
        walletRepo.getTransactions()
        .onSuccess { _wallet.value = _wallet.value.copy(transactions = it) }
        .onFailure { _toast.value = friendly(it, "Failed to load wallet history") }
        }
    }

    fun refreshVerificationStatus() {
        viewModelScope.launch {
        verifyRepo.getStatus()
        .onSuccess { _verificationStatus.value = it }
        }
    }

    // - Profile editing -

    fun saveProfile(name: String, username: String, phone: String, email: String, dob: String) {
        val current = _user.value ?: return
        val verified = (_verificationStatus.value?.verificationLevel ?: current.verificationLevel) >= 1
        _saving.value = true
        viewModelScope.launch {
        val updated = current.copy(
        name     = if (verified) current.name else name.trim().ifBlank { current.name },
        username = username.trim().ifBlank { current.username },
        phone    = phone.trim().ifBlank { current.phone },
        email    = email.trim().ifBlank { current.email },
        dob      = if (verified) current.dob else dob.ifBlank { current.dob },
        )
        val result = authRepo.updateUser(updated)
        _saving.value = false
        if (result.isSuccess) {
        _user.value = updated
        _toast.value = "Profile updated"
        refreshVerificationStatus()      // may auto-upgrade to level 2
        } else {
        val e = result.exceptionOrNull()
        _toast.value = when {
        e is HttpException && e.code() == 409 -> "Username already taken. Please choose another."
        else -> friendly(e, "Failed to save profile")
        }
        }
        }
    }

    fun uploadProfilePhoto(bytes: ByteArray) {
        val current = _user.value ?: return
        _saving.value = true
        viewModelScope.launch {
        authRepo.uploadProfilePhoto(bytes)
        .onSuccess { photoUrl ->
        _user.value = current.copy(photoUrl = photoUrl)
        _toast.value = "Profile photo updated"
        }
        .onFailure { _toast.value = friendly(it, "Failed to upload profile photo") }
        _saving.value = false
        }
    }

    fun updateLastSeenVisibility(visibility: String) {
        _saving.value = true
        viewModelScope.launch {
        authRepo.updateLastSeenVisibility(visibility)
        .onSuccess {
        _user.value = it
        _toast.value = if (it.lastSeenVisibility == "private") "Last seen is private" else "Last seen is public"
        }
        .onFailure { _toast.value = friendlyAction(it, "Unable to update privacy settings.") }
        _saving.value = false
        }
    }

    fun updatePrivacy(body: PrivacyUpdateBody, successMessage: String) {
        _saving.value = true
        viewModelScope.launch {
        authRepo.updatePrivacy(body)
        .onSuccess {
        _user.value = it
        _toast.value = successMessage
        }
        .onFailure { _toast.value = friendlyAction(it, "Unable to update privacy settings.") }
        _saving.value = false
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        _saving.value = true
        viewModelScope.launch {
        authRepo.deleteAccount()
        .onSuccess {
        _toast.value = "Account deleted"
        onDeleted()
        }
        .onFailure { _toast.value = friendlyAction(it, "Account deletion unavailable.") }
        _saving.value = false
        }
    }

    // - Badge application -

    fun uploadDocument(bytes: ByteArray) {
        _uploadingDocument.value = true
        viewModelScope.launch {
        verifyRepo.uploadImage("document", bytes)
        .onSuccess  { _documentUrl.value = it }
        .onFailure  { _toast.value = "Failed to upload document photo" }
        _uploadingDocument.value = false
        }
    }

    fun uploadSelfie(bytes: ByteArray) {
        _uploadingSelfie.value = true
        viewModelScope.launch {
        verifyRepo.uploadImage("selfie", bytes)
        .onSuccess  { _selfieUrl.value = it }
        .onFailure  { _toast.value = "Failed to upload selfie" }
        _uploadingSelfie.value = false
        }
    }

    fun resetUploadState() {
        _documentUrl.value      = ""
        _selfieUrl.value      = ""
        _uploadingDocument.value = false
        _uploadingSelfie.value   = false
    }

    fun submitVerification(fullName: String, documentType: String) {
        val docUrl  = _documentUrl.value
        val selfUrl = _selfieUrl.value
        if (docUrl.isBlank() || selfUrl.isBlank()) {
        _toast.value = "Please upload both document and selfie photos"
        return
        }
        _submittingVerification.value = true
        viewModelScope.launch {
        verifyRepo.submitRequest(fullName, documentType, docUrl, selfUrl)
        .onSuccess {
        _toast.value = "Verification request submitted!"
        _submittingVerification.value = false
        resetUploadState()
        loadUser()
        }
        .onFailure {
        _submittingVerification.value = false
        _toast.value = friendly(it, "Failed to submit request")
        }
        }
    }

    // - Admin -

    fun loadAdminRequests(status: String = "pending") {
        _adminLoading.value = true
        viewModelScope.launch {
        verifyRepo.getAdminRequests(status)
        .onSuccess { _adminRequests.value = it }
        .onFailure { _toast.value = friendly(it, "Failed to load requests") }
        _adminLoading.value = false
        }
    }

    fun approveVerification(uid: String) {
        viewModelScope.launch {
        verifyRepo.approve(uid)
        .onSuccess {
        _toast.value = "Approved"
        loadAdminRequests()
        loadUser()
        }
        .onFailure { _toast.value = friendly(it, "Failed to approve") }
        }
    }

    fun rejectVerification(uid: String, reason: String) {
        viewModelScope.launch {
        verifyRepo.reject(uid, reason)
        .onSuccess {
        _toast.value = "Rejected"
        loadAdminRequests()
        }
        .onFailure { _toast.value = friendly(it, "Failed to reject") }
        }
    }

    fun clearToast() { _toast.value = null }
    fun clearLastTransaction() { _lastTransaction.value = null }

    private fun friendly(error: Throwable?, fallback: String): String =
        CloudflareClient.friendlyErrorMessage(error, fallback)

    private fun friendlyAction(error: Throwable?, fallback: String): String {
        val message = CloudflareClient.friendlyErrorMessage(error, fallback)
        return if (message.contains("not found", ignoreCase = true)) fallback else message
    }
}
