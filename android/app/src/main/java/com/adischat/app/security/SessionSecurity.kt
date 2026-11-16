package com.adischat.app.security

import android.content.Context
import com.adischat.app.calls.CallEngine
import com.adischat.app.data.repository.NotificationRepository
import com.adischat.app.feature.restaurants.data.RestaurantsSessionMemory
import com.adischat.app.feature.shop.data.ShopIdentityRepository
import com.adischat.app.feature.shop.data.ShopSessionMemory
import com.adischat.app.notifications.ActiveChat
import com.adischat.app.notifications.UnreadCenter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single account boundary for Android. Anything process-wide or locally cached
 * must be cleared before a different Firebase user is allowed to drive the UI.
 */
object SessionSecurity {
    private val sensitivePrefs = listOf(
        "adis_unread_reads",
        "adis_chat_scroll_positions",
        "adis_channel_scroll_positions",
    )

    suspend fun hardSignOut(context: Context): Boolean = withContext(NonCancellable) {
        val appContext = context.applicationContext
        RestaurantsSessionMemory.beginSignOut()
        val ownerUid = ShopSessionMemory.profile()?.adisChatUserId
        val activeUid = FirebaseAuth.getInstance().currentUser?.uid
        val shopSession = ShopSessionMemory.beginSignOut()
        val shopRevoked = runCatching {
            withTimeoutOrNull(7_000L) {
                val identity = ShopIdentityRepository()
                val accountRevoked = identity.revokeCurrentAccountSessions()
                if (!accountRevoked && shopSession != null && ownerUid == activeUid) {
                    identity.revokeCapturedSession(shopSession)
                }
                accountRevoked
            } ?: false
        }.getOrDefault(false)

        try {
            // Must run while the current Firebase bearer token is still available.
            runCatching { NotificationRepository(appContext).unregisterAll() }

            CallEngine.resetForAccountSwitch()
            ActiveChat.clear()
            UnreadCenter.resetForAccountSwitch()
            sensitivePrefs.forEach { name ->
                appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            }

            runCatching {
                val cm = android.webkit.CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
            }

            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(appContext, gso).signOut().await()
            }
        } finally {
            FirebaseAuth.getInstance().signOut()
            ShopSessionMemory.finishSignOut()
            RestaurantsSessionMemory.finishSignOut()
        }
        shopRevoked
    }
}
