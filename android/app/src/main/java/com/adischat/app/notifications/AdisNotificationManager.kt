package com.adischat.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.adischat.app.FeatureFlags
import com.adischat.app.MainActivity
import com.adischat.app.R
import kotlin.math.absoluteValue

object AdisNotificationManager {
    private const val TAG = "AdisNotifications"
    private const val PREFS = "adischat_notification_settings"
    const val KEY_MESSAGES = "messages_enabled"
    const val KEY_GROUPS = "groups_enabled"
    const val KEY_CHANNELS = "channels_enabled"
    const val KEY_CALLS = "calls_enabled"
    const val KEY_SOUND = "sound_enabled"
    const val KEY_VIBRATION = "vibration_enabled"
    const val KEY_PREVIEW = "preview_enabled"
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_WALLET = "adischat_wallet"
    const val CHANNEL_SYSTEM = "adischat_system"
    const val CHANNEL_CALLS = "incoming_calls"
    const val CHANNEL_MISSED_CALLS = "missed_calls"
    const val CALL_NOTIFICATION_ID = 424242
    const val ACTION_REPLY = "com.adischat.app.notifications.REPLY"
    const val EXTRA_CONVERSATION_ID = "conversationId"
    const val EXTRA_NOTIFICATION_ID = "notificationId"
    const val EXTRA_ROOM_TYPE = "roomType"
    const val KEY_TEXT_REPLY = "adis_reply_text"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtone = Settings.System.DEFAULT_RINGTONE_URI
        val callAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
        val channels = listOf(
        NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "New chat messages"
        },
        NotificationChannel(CHANNEL_SYSTEM, "System", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "AdisChat account and system notices"
        },
        NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Incoming voice and video calls"
        enableVibration(true)
        setSound(ringtone, callAudio)
        setShowBadge(false)
        },
        NotificationChannel(CHANNEL_MISSED_CALLS, "Missed calls", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Missed voice and video calls"
        enableVibration(true)
        },
        )
        manager.createNotificationChannels(channels)
        Log.d(TAG, "notification channels created")
    }

    /** Full-screen incoming-call alert that opens the app (and the call UI) even
     *  when AdisChat is in the background or the device is locked. */
    fun showIncomingCall(context: Context, callId: String, callerName: String, callType: String, callerUid: String = "") {
        createChannels(context)
        if (!isEnabled(context, KEY_CALLS, true)) {
        Log.d(TAG, "incoming call notification suppressed by setting callId=$callId")
        return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
        Log.w(TAG, "incoming call notification blocked: POST_NOTIFICATIONS not granted")
        return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("notificationType", "incoming_call")
        putExtra("callId", callId)
        putExtra("callerUid", callerUid)
        putExtra("callerName", callerName)
        putExtra("callType", callType)
        }
        val pendingIntent = PendingIntent.getActivity(
        context, callNotificationId(callId), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(callerName.ifBlank { "AdisChat" })
        .setContentText(if (callType == "video") "Incoming video call" else "Incoming voice call")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setOngoing(true)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setFullScreenIntent(pendingIntent, true)   // pops over the lock screen
        .setSilent(!isEnabled(context, KEY_SOUND, true))
        .setVibrate(if (isEnabled(context, KEY_VIBRATION, true)) longArrayOf(0, 450, 220, 450) else longArrayOf(0))
        .build()
        Log.d(TAG, "show incoming call notification callId=$callId fullScreenAllowed=${canUseFullScreenIntent(context)}")
        NotificationManagerCompat.from(context).notify(callNotificationId(callId), notification)
    }

    fun cancelIncomingCall(context: Context, callId: String = "") {
        NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
        if (callId.isNotBlank()) NotificationManagerCompat.from(context).cancel(callNotificationId(callId))
    }

    fun showMissedCall(context: Context, callId: String, callerName: String, callType: String, callerUid: String = "") {
        createChannels(context)
        if (!isEnabled(context, KEY_CALLS, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("notificationType", "incoming_call")
        putExtra("callId", callId)
        putExtra("callerUid", callerUid)
        putExtra("callerName", callerName)
        putExtra("callType", callType)
        }
        val pendingIntent = PendingIntent.getActivity(
        context,
        callNotificationId("missed-$callId"),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Missed call")
        .setContentText(callerName.ifBlank { if (callType == "video") "Missed video call" else "Missed voice call" })
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setSilent(!isEnabled(context, KEY_SOUND, true))
        .setVibrate(if (isEnabled(context, KEY_VIBRATION, true)) longArrayOf(0, 250, 120, 250) else longArrayOf(0))
        .build()
        NotificationManagerCompat.from(context).notify(callNotificationId("missed-$callId"), notification)
    }

    fun show(context: Context, type: String, title: String, body: String, extras: Map<String, String> = emptyMap()) {
        createChannels(context)
        if (type == "wallet" && !FeatureFlags.FEATURE_WALLET_ENABLED) {
        Log.d(TAG, "wallet notification suppressed: wallet disabled")
        return
        }
        if (!shouldShowType(context, type, extras)) {
        Log.d(TAG, "notification suppressed by setting type=$type")
        return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
        Log.w(TAG, "notification blocked: POST_NOTIFICATIONS not granted type=$type")
        return
        }

        val channel = when (type) {
        "message", "shop_message", "group", "channel", "community_message", "channel_post" -> CHANNEL_MESSAGES
        "wallet" -> if (FeatureFlags.FEATURE_WALLET_ENABLED) CHANNEL_WALLET else CHANNEL_SYSTEM
        else -> CHANNEL_SYSTEM
        }
        val conversationId = extras[EXTRA_CONVERSATION_ID].orEmpty()
        val notificationId = notificationId(type, conversationId, extras[EXTRA_NOTIFICATION_ID].orEmpty())
        val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("notificationType", type)
        extras.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val replyAction = if (type == "message" && conversationId.isNotBlank()) {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
        .setLabel("Reply")
        .build()
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
        action = ACTION_REPLY
        putExtra(EXTRA_CONVERSATION_ID, conversationId)
        putExtra(EXTRA_ROOM_TYPE, extras[EXTRA_ROOM_TYPE].orEmpty())
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyPendingIntent = PendingIntent.getBroadcast(context, notificationId + 17, replyIntent, flags)
        NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, "Reply", replyPendingIntent)
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
        } else null

        val notificationBuilder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(notificationBody(context, type, body))
        .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody(context, type, body)))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSilent(!isEnabled(context, KEY_SOUND, true))
        .setVibrate(if (isEnabled(context, KEY_VIBRATION, true)) longArrayOf(0, 220, 120, 220) else longArrayOf(0))
        replyAction?.let(notificationBuilder::addAction)

        NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
    }

    private fun notificationId(type: String, conversationId: String, serverNotificationId: String): Int {
        val base = when {
        serverNotificationId.isNotBlank() -> "$type-$serverNotificationId"
        conversationId.isNotBlank() -> "$type-$conversationId"
        else -> "$type-${System.currentTimeMillis()}"
        }
        return base.hashCode().absoluteValue
    }

    private fun callNotificationId(callId: String): Int =
        "call-${callId.ifBlank { "active" }}".hashCode().absoluteValue

    private fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
        runCatching {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        }.getOrDefault(false)

    fun settings(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, key: String, default: Boolean): Boolean =
        settings(context).getBoolean(key, default)

    fun setEnabled(context: Context, key: String, value: Boolean) {
        settings(context).edit().putBoolean(key, value).apply()
        Log.d(TAG, "setting updated key=$key value=$value")
    }

    private fun shouldShowType(context: Context, type: String, extras: Map<String, String>): Boolean {
        val normalized = when {
        type == "incoming_call" || type == "missed_call" -> KEY_CALLS
        type == "group" || type == "community_message" || extras[EXTRA_ROOM_TYPE] == "group" -> KEY_GROUPS
        type == "channel" || type == "channel_post" || extras[EXTRA_ROOM_TYPE] == "channel" -> KEY_CHANNELS
        type == "message" -> KEY_MESSAGES
        else -> ""
        }
        return normalized.isBlank() || isEnabled(context, normalized, true)
    }

    private fun notificationBody(context: Context, type: String, body: String): String =
        if (isEnabled(context, KEY_PREVIEW, true)) body else when (type) {
        "group", "community_message" -> "New group message"
        "channel", "channel_post" -> "New channel update"
        "message" -> "New message"
        else -> "New notification"
        }
}
