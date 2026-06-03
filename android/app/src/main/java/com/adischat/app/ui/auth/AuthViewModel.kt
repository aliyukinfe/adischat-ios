package com.adischat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.network.CloudflareClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()

    /** OTP was sent; devOtp is non-null only in dev (no Resend key configured). */
    data class EmailOtpSent(val email: String, val devOtp: String? = null) : AuthState()

    data class Success(val user: AppUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AuthViewModel : ViewModel() {

    private val repo = AuthRepository()

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    // Pending sign-up data (held between OTP send and OTP verify steps)
    private var pendingName     = ""
    private var pendingPhone    = ""
    private var pendingEmail    = ""
    private var pendingPassword = ""

    // ── Sign Up ───────────────────────────────────────────────────────────────

    /** Step 1: validate locally, send email OTP, navigate to OTP screen. */
    fun signUp(name: String, phone: String, email: String, password: String) {
        pendingName     = name
        pendingPhone    = phone
        pendingEmail    = email
        pendingPassword = password
        _state.value    = AuthState.Loading
        viewModelScope.launch {
            val result = repo.sendEmailOtp(email)
            _state.value = result.fold(
                onSuccess = { devOtp -> AuthState.EmailOtpSent(email, devOtp) },
                onFailure = { AuthState.Error(CloudflareClient.friendlyErrorMessage(it, "Failed to send code")) }
            )
        }
    }

    /** Step 2: verify OTP then create Firebase + D1 account. */
    fun verifySignUpOtp(otp: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            // Verify OTP on the Worker
            val verify = repo.verifyEmailOtp(pendingEmail, otp)
            if (verify.isFailure) {
                _state.value = AuthState.Error(CloudflareClient.friendlyErrorMessage(verify.exceptionOrNull(), "Invalid code"))
                return@launch
            }
            // Create Firebase account and sync to D1
            val signUp = repo.signUpWithEmail(
                email    = pendingEmail,
                password = pendingPassword,
                name     = pendingName,
                phone    = pendingPhone,
            )
            _state.value = signUp.fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(mapFirebaseError(it)) }
            )
        }
    }

    /** Resend OTP (keeps pending data intact). */
    fun resendEmailOtp() {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = repo.sendEmailOtp(pendingEmail)
            _state.value = result.fold(
                onSuccess = { devOtp -> AuthState.EmailOtpSent(pendingEmail, devOtp) },
                onFailure = { AuthState.Error(CloudflareClient.friendlyErrorMessage(it, "Failed to resend code")) }
            )
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /** identifier can be email, username, or phone (9 digits, no +251). */
    fun login(identifier: String, password: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = repo.signInWithIdentifier(identifier, password)
            _state.value = result.fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(mapFirebaseError(it)) }
            )
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────

    fun sendPasswordReset(email: String, onDone: (success: Boolean, msg: String) -> Unit) {
        viewModelScope.launch {
            val result = repo.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = { onDone(true,  "Reset link sent! Check your inbox.") },
                onFailure = { onDone(false, CloudflareClient.friendlyErrorMessage(it, "Failed to send reset email")) }
            )
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    fun signInWithGoogle(idToken: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = repo.signInWithGoogle(idToken)
            _state.value = result.fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(CloudflareClient.friendlyErrorMessage(it, "Google sign-in failed")) }
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun resetState() { _state.value = AuthState.Idle }

    private fun mapFirebaseError(error: Throwable?): String {
        if (error == null) return "Something went wrong"
        val friendly = CloudflareClient.friendlyErrorMessage(error, "")
        if (friendly == CloudflareClient.CONNECTION_ERROR_MESSAGE) return friendly
        val msg = error.message
        return when {
        msg == null                                                  -> "Something went wrong"
        msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)
            || msg.contains("invalid-credential", ignoreCase = true)
            || msg.contains("wrong-password",     ignoreCase = true) -> "Incorrect email or password"
        msg.contains("user-not-found",            ignoreCase = true) -> "No account with this email"
        msg.contains("email-already-in-use",      ignoreCase = true) -> "An account already exists with this email"
        msg.contains("too-many-requests",         ignoreCase = true) -> "Too many attempts. Please try again later."
        msg.contains("network",                   ignoreCase = true) -> CloudflareClient.CONNECTION_ERROR_MESSAGE
        msg.contains("unable to resolve host",    ignoreCase = true) -> CloudflareClient.CONNECTION_ERROR_MESSAGE
        msg.contains("timeout",                   ignoreCase = true) -> CloudflareClient.CONNECTION_ERROR_MESSAGE
        msg.contains("failed to connect",         ignoreCase = true) -> CloudflareClient.CONNECTION_ERROR_MESSAGE
        msg.contains("No account found",          ignoreCase = true) -> msg
        else                                                          -> msg
        }
    }
}
