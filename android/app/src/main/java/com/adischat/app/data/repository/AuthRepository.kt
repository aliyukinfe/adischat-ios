package com.adischat.app.data.repository

import com.adischat.app.data.model.AppUser
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.LookupBody
import com.adischat.app.network.SendEmailOtpBody
import com.adischat.app.network.UserSyncBody
import com.adischat.app.network.UserUpdateBody
import com.adischat.app.network.VerifyEmailOtpBody
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private val api  = CloudflareClient.api

    val currentUser get() = auth.currentUser

    // ── Email OTP (pre-auth, public endpoint) ─────────────────────────────────

    /** Returns devOtp (only in dev/testing when Resend is not configured). */
    suspend fun sendEmailOtp(email: String): Result<String?> = runCatching {
        val resp = api.sendEmailOtp(SendEmailOtpBody(email.trim().lowercase()))
        resp.devOtp
    }

    suspend fun verifyEmailOtp(email: String, otp: String): Result<Unit> = runCatching {
        val resp = api.verifyEmailOtp(VerifyEmailOtpBody(email.trim().lowercase(), otp.trim()))
        if (!resp.verified) error("Verification failed")
    }

    // ── Email / Password sign-up (called AFTER email OTP verified) ────────────

    suspend fun signUpWithEmail(
        email: String, password: String,
        name: String, phone: String,
    ): Result<AppUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val fbUser = result.user ?: error("Sign up failed")

        // Update Firebase display name
        fbUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
        ).await()

        // Sync to D1 — OTP was already verified so emailVerified = true
        api.syncUser(
            UserSyncBody(
                name          = name.trim(),
                username      = generateUsername(name.trim()),
                phone         = "+251${phone.trim()}",
                email         = email.trim().lowercase(),
                dob           = "",
                photoUrl      = "",
                role          = "user",
                emailVerified = true,
            )
        )
    }

    // ── Identifier lookup + email/password login ──────────────────────────────

    /** Resolve username / phone to an email, then sign in. */
    suspend fun signInWithIdentifier(identifier: String, password: String): Result<AppUser> =
        runCatching {
            val email = if (identifier.contains('@')) {
                identifier.trim()
            } else {
                api.lookupAccount(LookupBody(identifier.trim())).email
            }
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val fbUser = result.user ?: error("Sign in failed")
            getCurrentAppUser() ?: syncUserToD1(fbUser)
        }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    suspend fun signInWithGoogle(idToken: String): Result<AppUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result     = auth.signInWithCredential(credential).await()
        val fbUser     = result.user ?: error("Google sign-in failed")
        getCurrentAppUser() ?: syncUserToD1(fbUser)
    }

    // ── Password reset ────────────────────────────────────────────────────────

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    // ── Current user ──────────────────────────────────────────────────────────

    suspend fun getCurrentAppUser(): AppUser? {
        val fbUser = auth.currentUser ?: return null
        return try {
            api.getMe()
        } catch (e: Exception) {
            try { syncUserToD1(fbUser) } catch (_: Exception) { null }
        }
    }

    // ── Profile update ────────────────────────────────────────────────────────

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

    fun signOut() = auth.signOut()

    // ── Internals ─────────────────────────────────────────────────────────────

    internal suspend fun syncUserToD1(fbUser: FirebaseUser): AppUser =
        api.syncUser(
            UserSyncBody(
                name     = fbUser.displayName ?: "",
                username = generateUsername(fbUser.displayName),
                phone    = fbUser.phoneNumber ?: "",
                email    = fbUser.email ?: "",
                dob      = "",
                photoUrl = fbUser.photoUrl?.toString() ?: "",
                role     = "user",
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
