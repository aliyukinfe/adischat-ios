package com.adischat.app.data.repository

import com.adischat.app.data.model.AppUser
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.LookupBody
import com.adischat.app.network.PrivacyUpdateBody
import com.adischat.app.network.SendEmailOtpBody
import com.adischat.app.network.UserSyncBody
import com.adischat.app.network.UserUpdateBody
import com.adischat.app.network.VerifyEmailOtpBody
import com.adischat.app.network.isRecoverableConnectivityFailure
import com.adischat.app.feature.shop.data.ShopSessionMemory
import com.adischat.app.feature.shop.data.ShopIdentityRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private val api  = CloudflareClient.api

    val currentUser get() = auth.currentUser

    // - Email OTP (pre-auth, public endpoint) -

    /** Returns devOtp (only in dev/testing when Resend is not configured). */
    suspend fun sendEmailOtp(email: String): Result<String?> = runCatching {
        val resp = api.sendEmailOtp(SendEmailOtpBody(email.trim().lowercase()))
        resp.devOtp
    }

    suspend fun verifyEmailOtp(email: String, otp: String): Result<Unit> = runCatching {
        val resp = api.verifyEmailOtp(VerifyEmailOtpBody(email.trim().lowercase(), otp.trim()))
        if (!resp.verified) error("Verification failed")
    }

    // - Email / Password sign-up (called AFTER email OTP verified) -

    suspend fun signUpWithEmail(
        email: String, password: String,
        name: String, phone: String,
    ): Result<AppUser> = runCatching {
        if (auth.currentUser != null) switchAccountSignOut() else ShopSessionMemory.clear()
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val fbUser = result.user ?: error("Sign up failed")

        // Update Firebase display name
        fbUser.updateProfile(
        UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
        ).await()

        // Sync to D1 - OTP was already verified so emailVerified = true
        api.syncUser(
        UserSyncBody(
        name      = name.trim(),
        username      = generateUsername(name.trim()),
        phone      = "+251${phone.trim()}",
        email      = email.trim().lowercase(),
        dob      = "",
        photoUrl      = "",
        role      = "user",
        emailVerified = true,
        )
        ).also { assertSameAccount(fbUser.uid, it) }
    }

    // - Identifier lookup + email/password login -

    /** Resolve username / phone to an email, then sign in. */
    suspend fun signInWithIdentifier(identifier: String, password: String): Result<AppUser> =
        runCatching {
        val cleaned = identifier.trim()
        val looksLikeEmail = cleaned.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
        val lookup = if (looksLikeEmail) {
        null
        } else {
        api.lookupAccount(LookupBody(cleaned))
        }
        val email = lookup?.email ?: cleaned.lowercase()
        if (auth.currentUser != null) {
        switchAccountSignOut()
        }
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val fbUser = result.user ?: error("Sign in failed")
        if (!lookup?.uid.isNullOrBlank() && lookup?.uid != fbUser.uid) {
        clearLocalAuthState()
        error("Username or password incorrect")
        }
        getCurrentAppUser() ?: syncUserToD1(fbUser).also { assertSameAccount(fbUser.uid, it) }
        }

    // - Google Sign-In -

    suspend fun signInWithGoogle(idToken: String): Result<AppUser> = runCatching {
        if (auth.currentUser != null) {
        switchAccountSignOut()
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result     = auth.signInWithCredential(credential).await()
        val fbUser     = result.user ?: error("Google sign-in failed")
        getCurrentAppUser() ?: syncUserToD1(fbUser).also { assertSameAccount(fbUser.uid, it) }
    }

    // - Password reset -

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    // - Current user -

    suspend fun getCurrentAppUser(): AppUser? {
        val fbUser = auth.currentUser ?: return null
        repeat(3) { attempt ->
        try {
        ensureFirebaseIdToken(fbUser)
        return api.getMe().also { assertSameAccount(fbUser.uid, it) }
        } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (e.message?.contains("Session security issue detected", ignoreCase = true) == true) throw e
        if (e.isRecoverableConnectivityFailure()) return null
        if (attempt < 2) delay(350L * (attempt + 1))
        }
        }

        return try {
            ensureFirebaseIdToken(fbUser, forceRefresh = true)
            syncUserToD1(fbUser).also { assertSameAccount(fbUser.uid, it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e.isRecoverableConnectivityFailure()) null else throw e
        }
    }

    // - Profile update -

    suspend fun updateUser(user: AppUser): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        api.updateUser(uid, UserUpdateBody(
        name     = user.name,
        username = user.username,
        phone    = user.phone,
        email    = user.email,
        dob      = user.dob,
        ))
        Unit
    }

    suspend fun uploadProfilePhoto(bytes: ByteArray): Result<String> = runCatching {
        val uid  = auth.currentUser?.uid ?: error("Not signed in")
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        api.uploadAvatar(uid, body).photoUrl
    }

    suspend fun updateLastSeenVisibility(visibility: String): Result<AppUser> = runCatching {
        val value = if (visibility == "private") "private" else "public"
        updatePrivacyRequest(PrivacyUpdateBody(lastSeenVisibility = value)).also {
        assertSameAccount(auth.currentUser?.uid.orEmpty(), it)
        }
    }

    suspend fun updatePrivacy(body: PrivacyUpdateBody): Result<AppUser> = runCatching {
        updatePrivacyRequest(body).also {
        assertSameAccount(auth.currentUser?.uid.orEmpty(), it)
        }
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val firebaseUser = auth.currentUser ?: error("Not signed in")
        deleteCloudflareAccount()
        try {
        firebaseUser.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
        throw IllegalStateException("Please log in again to delete your account.", e)
        }
        clearLocalAuthState()
        Unit
    }

    suspend fun signOut(): Boolean = switchAccountSignOut()

    private suspend fun switchAccountSignOut(): Boolean {
        val ownerUid = ShopSessionMemory.profile()?.adisChatUserId
        val activeUid = auth.currentUser?.uid
        val shopSession = ShopSessionMemory.beginSignOut()
        val revoked = try {
            withTimeoutOrNull(7_000L) {
                val identity = ShopIdentityRepository()
                val accountRevoked = identity.revokeCurrentAccountSessions()
                if (!accountRevoked && shopSession != null && ownerUid == activeUid) {
                    identity.revokeCapturedSession(shopSession)
                }
                accountRevoked
            } ?: false
        } finally {
            clearLocalAuthState()
            ShopSessionMemory.finishSignOut()
        }
        return revoked
    }

    private fun clearLocalAuthState() {
        ShopSessionMemory.clear()
        auth.signOut()
        runCatching {
            val cm = android.webkit.CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }

    // - Internals -

    private suspend fun ensureFirebaseIdToken(fbUser: FirebaseUser, forceRefresh: Boolean = false): String {
        val token = fbUser.getIdToken(forceRefresh).await().token.orEmpty()
        if (token.isBlank()) error("Firebase session is not ready")
        return token
    }

    private fun assertSameAccount(firebaseUid: String, appUser: AppUser) {
        if (appUser.uid == firebaseUid) return
        clearLocalAuthState()
        error("Session security issue detected. Please log in again.")
    }

    private suspend fun updatePrivacyRequest(body: PrivacyUpdateBody): AppUser =
        runCatching { api.updatePrivacy(body) }
        .recoverCatching { firstError ->
        val message = firstError.message.orEmpty().lowercase()
        if ("not found" in message || "forbidden" in message || "permission" in message || "404" in message || "403" in message) {
        api.updateMyPrivacy(body)
        } else {
        throw firstError
        }
        }
        .getOrThrow()

    private suspend fun deleteCloudflareAccount() {
        val attempts = listOf<suspend () -> Unit>(
        { api.deleteAccountCanonicalPost(); Unit },
        { api.deleteAccountCanonicalDelete(); Unit },
        { api.deleteAccount(); Unit },
        { api.deleteAccountPost(); Unit },
        )
        var lastError: Throwable? = null
        for (attempt in attempts) {
        val result = runCatching { attempt() }
        if (result.isSuccess) return
        lastError = result.exceptionOrNull()
        val message = lastError?.message.orEmpty().lowercase()
        if ("not found" !in message && "404" !in message && "method" !in message && "405" !in message) throw lastError!!
        }
        throw lastError ?: IllegalStateException("Account deletion unavailable.")
    }

    private fun FirebaseUser.toAppUserFallback(): AppUser = AppUser(
        uid = uid,
        name = displayName ?: email?.substringBefore('@') ?: phoneNumber ?: "AdisChat user",
        username = generateUsername(displayName ?: email?.substringBefore('@') ?: phoneNumber),
        phone = phoneNumber ?: "",
        email = email ?: "",
        photoUrl = "",
        role = "user",
        emailVerified = isEmailVerified,
    )
    internal suspend fun syncUserToD1(fbUser: FirebaseUser): AppUser =
        api.syncUser(
        UserSyncBody(
        name     = fbUser.displayName ?: "",
        username = generateUsername(fbUser.displayName),
        phone    = fbUser.phoneNumber ?: "",
        email    = fbUser.email ?: "",
        dob      = "",
        photoUrl = "",
        role     = "user",
        emailVerified = fbUser.isEmailVerified,
        )
        )

    private fun generateUsername(displayName: String?): String {
        if (displayName.isNullOrBlank()) return "user_${(1000..9999).random()}"
        return displayName.trim().lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .take(20)
        .trimEnd('_')
        .let { if (it.length < 3) "${it}_${(100..999).random()}" else it }
    }
}
