package com.adischat.app.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.NotificationTokenBody
import com.adischat.app.BuildConfig

class NotificationRepository(private val context: Context? = null) {
    suspend fun registerToken(token: String) {
        if (BuildConfig.QA_BUILD && !BuildConfig.QA_PUSH_ENABLED) return
        val body = NotificationTokenBody(token = token, deviceId = deviceId())
        Log.d("AdisNotifications", "registering push token length=${token.length} device=${body.deviceId.isNotBlank()}")
        runCatching { CloudflareClient.api.registerNotificationToken(body) }
        .onSuccess { Log.d("AdisNotifications", "Main API token registered") }
        .onFailure { Log.w("AdisNotifications", "Main API token registration skipped", it) }
        runCatching { CloudflareClient.walletApi.registerNotificationToken(body) }
        .onSuccess { Log.d("AdisNotifications", "Wallet API token registered") }
        .onFailure { Log.w("AdisNotifications", "Wallet API token registration skipped", it) }
    }

    /**
     * Drop every push token for the current account. Called on logout so a
     * signed-out device stops receiving that account's notifications.
     * An empty token tells the backend to delete all tokens for this uid.
     * Must run BEFORE FirebaseAuth.signOut() while the Bearer token is valid.
     */
    suspend fun unregisterAll() {
        if (BuildConfig.QA_BUILD && !BuildConfig.QA_PUSH_ENABLED) return
        val body = NotificationTokenBody(token = "", deviceId = deviceId())
        Log.d("AdisNotifications", "unregistering push tokens for current account device=${body.deviceId.isNotBlank()}")
        runCatching { CloudflareClient.api.unregisterNotificationToken(body) }
        .onSuccess { Log.d("AdisNotifications", "Main API tokens unregistered") }
        .onFailure { Log.w("AdisNotifications", "Main API token unregister skipped", it) }
        runCatching { CloudflareClient.walletApi.unregisterNotificationToken(body) }
        .onSuccess { Log.d("AdisNotifications", "Wallet API tokens unregistered") }
        .onFailure { Log.w("AdisNotifications", "Wallet API token unregister skipped", it) }
    }

    private fun deviceId(): String {
        val appContext = context?.applicationContext ?: return ""
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }
}
