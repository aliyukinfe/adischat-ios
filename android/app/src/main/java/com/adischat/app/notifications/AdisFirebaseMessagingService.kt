package com.adischat.app.notifications

import com.adischat.app.FeatureFlags
import com.adischat.app.BuildConfig

import com.adischat.app.data.repository.NotificationRepository
import com.adischat.app.data.repository.CallRepository
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.calls.safeCallerName
import com.adischat.app.calls.safeCallerPhotoUrl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdisFirebaseMessagingService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "AdisNotifications"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (!BuildConfig.QA_PUSH_ENABLED) return
        scope.launch {
        Log.d(TAG, "FCM token refreshed length=${token.length}")
        NotificationRepository(applicationContext).registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.QA_PUSH_ENABLED) return
        val data = message.data
        val type = data["type"] ?: "system"
        val shopOrderNotificationTypes = setOf(
        "seller_confirmation_pending",
        "seller_order_received",
        "seller_response_reminder",
        "seller_response_expired",
        "automatic_refund_initiated",
        )
        if (type == "wallet" && !FeatureFlags.FEATURE_WALLET_ENABLED) {
        Log.d(TAG, "wallet push ignored: wallet disabled")
        return
        }

        if (type == "shop_message" || type in shopOrderNotificationTypes || data["area"] == "adischat_shop") {
        val target = marketplacePushTarget(
            data["area"], data["context"], data["receiverId"],
            FirebaseAuth.getInstance().currentUser?.uid, data["storeId"], type, data["conversationId"],
        )
        if (target == MarketplacePushTarget.IGNORE) {
        Log.w(TAG, "ignored unscoped or foreign AdisGebeya notification")
        return
        }
        }


        // Incoming call - drive the call engine so the full-screen call UI shows.
        // (This callback fires when the app is in the foreground; when it's
        // backgrounded/killed the OS shows the notification payload instead.)
        if (type == "incoming_call") {
        val callId = data["callId"].orEmpty()
        if (callId.isNotBlank()) {
        scope.launch {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (myUid.isBlank()) return@launch
        val pushedReceiverId = data["receiverId"].orEmpty()
        if (pushedReceiverId.isNotBlank() && pushedReceiverId != myUid) {
        Log.w(TAG, "ignored incoming_call for another account callId=$callId")
        return@launch
        }
        Log.d(TAG, "incoming_call push received callId=$callId")
        val call = CallRepository().getCall(callId).getOrNull() ?: return@launch
        if (call.receiverUid != myUid || call.status !in setOf("delivering", "ringing", "accepted", "connected")) {
        Log.w(TAG, "ignored incoming_call after verification callId=$callId status=${call.status}")
        return@launch
        }
        val currentPhotoUrl = AuthRepository().getCurrentAppUser()?.photoUrl.orEmpty()
        val callerName = call.safeCallerName(data["callerName"].orEmpty())
        val callerPhotoUrl = call.safeCallerPhotoUrl(myUid, currentPhotoUrl)
        val callType = call.type.ifBlank { data["callType"].orEmpty() }.ifBlank { "voice" }
        // Push data is only a wake-up hint. Use verified backend call
        // details so an old account's notification cannot ring here.
        com.adischat.app.calls.CallEngine.init(applicationContext)
        com.adischat.app.calls.CallEngine.onIncomingCall(
        callId = call.id,
        callerUid = call.callerUid,
        callerName = callerName,
        callType = callType,
        callerPhotoUrl = callerPhotoUrl,
        )
        AdisNotificationManager.showIncomingCall(applicationContext, call.id, callerName, callType, call.callerUid)
        Log.d(TAG, "incoming_call UI notified callId=${call.id} status=${call.status}")
        }
        return
        }
        }

        if (type == "call_update") {
        val callId = data["callId"].orEmpty()
        val status = data["callStatus"].orEmpty()
        if (callId.isNotBlank() && status.isNotBlank()) {
        com.adischat.app.calls.CallEngine.init(applicationContext)
        com.adischat.app.calls.CallEngine.onCallUpdate(callId, status)
        if (status in listOf("cancelled", "rejected", "missed", "completed", "ended", "failed", "unreachable")) {
        AdisNotificationManager.cancelIncomingCall(applicationContext, callId)
        }
        UnreadCenter.refreshSoon()
        }
        return
        }

        // Suppress a message notification for the conversation the user is
        // currently viewing - this callback only fires while the app is in the
        // foreground, so a match means they're already reading it.
        val conversationId = data["conversationId"]
        // A new message arrived - refresh unread badges as soon as possible.
        if (type == "message") UnreadCenter.refreshSoon()
        if (type == "message" && conversationId != null && conversationId == ActiveChat.conversationId) {
        return
        }
        val title = data["title"] ?: message.notification?.title ?: when (type) {
        "message" -> "New message"
        "shop_message" -> "New AdisGebeya message"
        "wallet" -> "Wallet activity"
        "incoming_call" -> if (data["callType"] == "video") "Incoming video call" else "Incoming voice call"
        else -> "AdisChat"
        }
        val body = data["body"] ?: message.notification?.body ?: when (type) {
        "incoming_call" -> "Tap to answer in AdisChat"
        else -> ""
        }
        AdisNotificationManager.show(applicationContext, type, title, body, data)
    }
}
