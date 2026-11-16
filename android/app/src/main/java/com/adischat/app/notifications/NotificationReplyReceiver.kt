package com.adischat.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.SendMessageBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdisNotificationManager.ACTION_REPLY) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
        val replyText = RemoteInput.getResultsFromIntent(intent)
        ?.getCharSequence(AdisNotificationManager.KEY_TEXT_REPLY)
        ?.toString()
        ?.trim()
        .orEmpty()
        val conversationId = intent.getStringExtra(AdisNotificationManager.EXTRA_CONVERSATION_ID).orEmpty()
        val roomType = intent.getStringExtra(AdisNotificationManager.EXTRA_ROOM_TYPE).orEmpty()
        if (replyText.isNotBlank() && conversationId.isNotBlank()) {
        val body = SendMessageBody(text = replyText, type = "text")
        if (roomType == "group" || roomType == "channel") {
        CloudflareClient.walletApi.sendCommunityMessage(conversationId, body)
        } else {
        CloudflareClient.api.sendMessage(conversationId, body)
        }
        UnreadCenter.refreshSoon()
        }
        val notificationId = intent.getIntExtra(AdisNotificationManager.EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
        NotificationManagerCompat.from(context).cancel(notificationId)
        }
        } finally {
        pendingResult.finish()
        }
        }
    }
}
