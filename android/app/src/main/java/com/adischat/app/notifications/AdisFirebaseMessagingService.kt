package com.adischat.app.notifications

import com.adischat.app.data.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdisFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            NotificationRepository().registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "system"
        val title = data["title"] ?: message.notification?.title ?: when (type) {
            "message" -> "New message"
            "wallet" -> "Wallet activity"
            else -> "AdisChat"
        }
        val body = data["body"] ?: message.notification?.body ?: ""
        AdisNotificationManager.show(applicationContext, type, title, body)
    }
}
