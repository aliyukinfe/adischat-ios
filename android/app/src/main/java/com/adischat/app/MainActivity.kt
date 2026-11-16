package com.adischat.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.adischat.app.calls.safeCallerName
import com.adischat.app.calls.safeCallerPhotoUrl
import com.adischat.app.feature.restaurants.presentation.RestaurantQrParser
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.data.repository.CallRepository
import com.adischat.app.data.preferences.ADISCHAT_LANGUAGE_PREF
import com.adischat.app.data.preferences.ADISCHAT_LOCALE_PREFS
import com.adischat.app.data.repository.NotificationRepository
import com.adischat.app.navigation.AppNavHost
import com.adischat.app.notifications.AdisNotificationManager
import com.adischat.app.notifications.MarketplacePushTarget
import com.adischat.app.notifications.marketplacePushTarget
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class MainActivity : FragmentActivity() {
    private companion object {
        const val TAG = "AdisMain"
        const val PENDING_CALL_POLL_MS = 9_000L
    }

    // Held so onNewIntent can forward to the NavController
    var navController: NavHostController? = null
    var deepLinkData: Uri? by mutableStateOf(null)
    var pendingNotificationConversationId: String? by mutableStateOf(null)
    var pendingShopConversationId: String? by mutableStateOf(null)
    var pendingShopNotificationContext: String? by mutableStateOf(null)
    var pendingShopNotificationStoreId: String? by mutableStateOf(null)
    var pendingRestaurantQrToken: String? by mutableStateOf(null)
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingCallPollJob: Job? = null
    @Volatile private var pendingCallRequestRunning = false
    private val pendingPollTimes = mutableListOf<Long>()
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withSavedAdisChatLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!BuildConfig.OFFLINE_QA_PREVIEW) {
            AdisNotificationManager.createChannels(this)
            if (BuildConfig.QA_PUSH_ENABLED) {
                requestNotificationPermission()
                registerFcmToken()
            }
            deepLinkData = allowedDeepLink(intent?.data)
            pendingRestaurantQrToken = RestaurantQrParser.extractRestaurantDeepLink(intent?.data?.toString())
            handleNotificationIntent(intent)
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null && BuildConfig.QA_PUSH_ENABLED) {
            registerFcmToken()
            } else {
            pendingCallPollJob?.cancel()
            pendingCallPollJob = null
            pendingCallRequestRunning = false
            pendingShopConversationId = null
            pendingShopNotificationContext = null
            pendingShopNotificationStoreId = null
            }
            }
        }
        setContent {
        AppNavHost(activity = this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.OFFLINE_QA_PREVIEW) startPendingCallPolling()
    }

    override fun onPause() {
        pendingCallPollJob?.cancel()
        pendingCallPollJob = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.OFFLINE_QA_PREVIEW) return
        setIntent(intent)
        deepLinkData = allowedDeepLink(intent.data)
        pendingRestaurantQrToken = RestaurantQrParser.extractRestaurantDeepLink(intent.data?.toString())
        handleNotificationIntent(intent)
        navController?.handleDeepLink(intent)
    }

    private fun allowedDeepLink(uri: Uri?): Uri? {
        if (uri == null) return null
        if (FeatureFlags.FEATURE_WALLET_ENABLED) return uri
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val query = uri.query.orEmpty().lowercase(Locale.ROOT)
        val blocked = listOf("wallet", "pay", "payment", "merchant-pay", "deposit", "withdraw", "transaction")
        return if (blocked.any { token -> host.contains(token) || path.contains(token) || query.contains(token) }) {
        Log.d(TAG, "wallet/payment deep link ignored: wallet disabled")
        null
        } else uri
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent?.getStringExtra("notificationType").orEmpty()
        if (intent.getStringExtra("area") == "adischat_shop") {
        val target = marketplacePushTarget(
            intent.getStringExtra("area"), intent.getStringExtra("context"),
            intent.getStringExtra("receiverId"), FirebaseAuth.getInstance().currentUser?.uid,
            intent.getStringExtra("storeId"), type, intent.getStringExtra("conversationId"),
        )
        if (target == MarketplacePushTarget.IGNORE) return
        pendingShopConversationId = if (target == MarketplacePushTarget.BUYER_MESSAGE) {
            intent.getStringExtra("conversationId")?.takeIf { it.isNotBlank() }
        } else null
        pendingShopNotificationContext = when (target) {
            MarketplacePushTarget.BUYER_NOTIFICATIONS -> "buyer"
            MarketplacePushTarget.SELLER_NOTIFICATIONS -> "seller"
            else -> null
        }
        pendingShopNotificationStoreId = if (target == MarketplacePushTarget.SELLER_NOTIFICATIONS) {
            intent.getStringExtra("storeId")
        } else null
        return
        }
        if (type in setOf("message", "group", "channel", "community_message", "channel_post")) {
        Log.d(TAG, "message notification opened")
        pendingNotificationConversationId = intent.getStringExtra("conversationId")
        ?.takeIf { it.isNotBlank() }
        return
        }
        if (type != "incoming_call") return
        val callId = intent.getStringExtra("callId").orEmpty()
        if (callId.isBlank()) return
        Log.d(TAG, "incoming call notification opened callId=$callId")
        openVerifiedIncomingCall(
        callId = callId,
        fallbackCallerName = intent.getStringExtra("callerName").orEmpty(),
        fallbackCallType = intent.getStringExtra("callType").orEmpty(),
        )
    }

    private fun openVerifiedIncomingCall(callId: String, fallbackCallerName: String = "", fallbackCallType: String = "") {
        notificationScope.launch {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (myUid.isBlank()) return@launch
        val call = CallRepository().getCall(callId).getOrNull() ?: return@launch
        if (call.receiverUid != myUid || call.status !in setOf("delivering", "ringing", "accepted", "connected")) {
        Log.w(TAG, "ignored call open after verification callId=$callId status=${call.status}")
        return@launch
        }
        val currentPhotoUrl = AuthRepository().getCurrentAppUser()?.photoUrl.orEmpty()
        val callerName = call.safeCallerName(fallbackCallerName)
        val callerPhotoUrl = call.safeCallerPhotoUrl(myUid, currentPhotoUrl)
        val callType = call.type.ifBlank { fallbackCallType }.ifBlank { "voice" }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
        com.adischat.app.calls.CallEngine.init(applicationContext)
        com.adischat.app.calls.CallEngine.onIncomingCall(
        callId = call.id,
        callerUid = call.callerUid,
        callerName = callerName,
        callType = callType,
        callerPhotoUrl = callerPhotoUrl,
        )
        }
        }
    }

    private fun pollPendingIncomingCall() {
        notificationScope.launch {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (myUid.isBlank()) return@launch
        val call = CallRepository().getPendingIncomingCall().getOrNull() ?: return@launch
        if (call.receiverUid != myUid || call.status !in setOf("delivering", "ringing")) return@launch
        Log.d(TAG, "pending incoming call recovered callId=${call.id} status=${call.status}")
        openVerifiedIncomingCall(
        callId = call.id,
        fallbackCallerName = call.safeCallerName(""),
        fallbackCallType = call.type,
        )
        }
    }

    private fun startPendingCallPolling() {
        if (pendingCallPollJob?.isActive == true) return
        pendingCallPollJob = notificationScope.launch {
        Log.d(TAG, "pending-call polling interval=${PENDING_CALL_POLL_MS}ms foreground=true")
        while (isActive) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (myUid.isNotBlank()) {
        val call = fetchPendingIncomingCallOnce()
        if (call != null && call.receiverUid == myUid && call.status in setOf("delivering", "ringing")) {
        Log.d(TAG, "pending incoming call poll hit callId=${call.id} status=${call.status}")
        openVerifiedIncomingCall(
        callId = call.id,
        fallbackCallerName = call.safeCallerName(""),
        fallbackCallType = call.type,
        )
        return@launch
        }
        }
        delay(PENDING_CALL_POLL_MS)
        }
        }
    }

    private suspend fun fetchPendingIncomingCallOnce(): com.adischat.app.network.CallSession? {
        if (pendingCallRequestRunning) return null
        pendingCallRequestRunning = true
        recordPendingCallPoll()
        return try {
        CallRepository().getPendingIncomingCall().getOrNull()
        } finally {
        pendingCallRequestRunning = false
        }
    }

    private fun recordPendingCallPoll() {
        val now = System.currentTimeMillis()
        synchronized(pendingPollTimes) {
        pendingPollTimes.removeAll { now - it >= 60_000L }
        pendingPollTimes.add(now)
        val rpm = pendingPollTimes.size
        Log.d(TAG, "pending-call polling rate requestsPerMinute=$rpm")
        if (rpm > 10) Log.w(TAG, "pending-call polling exceeds target requestsPerMinute=$rpm")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerFcmToken() {
        if (!BuildConfig.QA_PUSH_ENABLED) return
        notificationScope.launch {
        runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        NotificationRepository(applicationContext).registerToken(token)
        }
        }
    }
}
private fun Context.withSavedAdisChatLocale(): Context {
    val locale = Locale("en")
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
