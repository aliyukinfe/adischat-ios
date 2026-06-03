package com.adischat.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.adischat.app.data.repository.NotificationRepository
import com.adischat.app.navigation.AppNavHost
import com.adischat.app.notifications.AdisNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : FragmentActivity() {

    // Held so onNewIntent can forward to the NavController
    var navController: NavHostController? = null
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdisNotificationManager.createChannels(this)
        requestNotificationPermission()
        registerFcmToken()
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) registerFcmToken()
        }
        setContent {
            AppNavHost(activity = this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerFcmToken() {
        notificationScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                NotificationRepository().registerToken(token)
            }
        }
    }
}
