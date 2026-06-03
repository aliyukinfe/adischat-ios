package com.adischat.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adischat.app.MainActivity
import com.adischat.app.R
import kotlin.math.absoluteValue

object AdisNotificationManager {
    const val CHANNEL_MESSAGES = "adischat_messages"
    const val CHANNEL_WALLET = "adischat_wallet"
    const val CHANNEL_SYSTEM = "adischat_system"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages"
            },
            NotificationChannel(CHANNEL_WALLET, "Wallet", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Money received and wallet activity"
            },
            NotificationChannel(CHANNEL_SYSTEM, "System", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "AdisChat account and system notices"
            },
        )
        manager.createNotificationChannels(channels)
    }

    fun show(context: Context, type: String, title: String, body: String) {
        createChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val channel = when (type) {
            "message" -> CHANNEL_MESSAGES
            "wallet" -> CHANNEL_WALLET
            else -> CHANNEL_SYSTEM
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notificationType", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.hashCode().absoluteValue,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
