package com.adischat.app.data.repository

import android.util.Log
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.NotificationTokenBody

class NotificationRepository {
    suspend fun registerToken(token: String) {
        val body = NotificationTokenBody(token = token)
        runCatching { CloudflareClient.api.registerNotificationToken(body) }
            .onFailure { Log.w("AdisNotifications", "Main API token registration skipped", it) }
        runCatching { CloudflareClient.walletApi.registerNotificationToken(body) }
            .onFailure { Log.w("AdisNotifications", "Wallet API token registration skipped", it) }
    }
}
