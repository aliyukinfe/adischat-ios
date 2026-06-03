package com.adischat.app.navigation

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.ui.auth.AuthScreen
import com.adischat.app.ui.UserViewModel
import com.adischat.app.ui.browser.InAppBrowserScreen
import com.adischat.app.ui.main.MainScreen
import com.adischat.app.ui.splash.SplashScreen
import com.adischat.app.ui.theme.AppTheme
import com.adischat.app.ui.theme.AdisChatTheme
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavHost(activity: com.adischat.app.MainActivity? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs   = remember { AppPreferences(context) }

    val themeStr by prefs.themeFlow.collectAsStateWithLifecycle(initialValue = "system")
    val fingerprintEnabled by prefs.walletFingerprintFlow.collectAsStateWithLifecycle(initialValue = false)
    val appTheme = when (themeStr) {
        "light" -> AppTheme.LIGHT
        "dark"  -> AppTheme.DARK
        else    -> AppTheme.SYSTEM
    }

    AdisChatTheme(appTheme = appTheme) {
        val navController = rememberNavController()
        val isLoggedIn    = FirebaseAuth.getInstance().currentUser != null
        val start         = if (isLoggedIn) "main" else "auth"
        var browserUrl by remember { mutableStateOf<String?>(null) }
        var appUnlocked by remember { mutableStateOf(false) }
        var appLockArmed by remember { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(fingerprintEnabled) {
            if (appLockArmed == null || !fingerprintEnabled) {
                appLockArmed = fingerprintEnabled
            }
        }

        DisposableEffect(lifecycleOwner, fingerprintEnabled, isLoggedIn) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (fingerprintEnabled && isLoggedIn) {
                            appUnlocked = false
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        appLockArmed = fingerprintEnabled && isLoggedIn
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Register with Activity so onNewIntent can forward deep links
        LaunchedEffect(navController) {
            activity?.navController = navController
            // Handle the intent that launched this Activity (e.g. deep link via ADB)
            activity?.intent?.let { navController.handleDeepLink(it) }
        }

        NavHost(navController = navController, startDestination = "splash") {

            composable("splash") {
                SplashScreen {
                    navController.navigate(start) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }

            composable("auth") {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate("main") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            composable("main") {
                // Shared with MainScreen — viewModel() inside this NavBackStackEntry
                // returns the same instance regardless of which composable calls it,
                // so the in-app browser sees exactly the user the app currently has.
                val userVm: UserViewModel = viewModel()
                val currentUser by userVm.user.collectAsStateWithLifecycle()

                val activeUrl = browserUrl
                val locked = isLoggedIn && appLockArmed == true && !appUnlocked
                if (locked) {
                    FingerprintUnlockScreen(
                        onUnlock = { appUnlocked = true },
                        onSignOut = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("auth") {
                                popUpTo("main") { inclusive = true }
                            }
                        },
                    )
                } else if (activeUrl != null) {
                    InAppBrowserScreen(
                        url         = normalizeBrowserUrl(activeUrl),
                        onClose     = { browserUrl = null },
                        currentUser = currentUser,
                    )
                } else {
                    MainScreen(
                        prefs       = prefs,
                        onOpenUrl   = { url -> browserUrl = url },
                        onSignOut   = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("auth") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintUnlockScreen(onUnlock: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Confirm fingerprint to open AdisChat") }

    fun prompt() {
        authenticateForAppOpen(
            context = context,
            onSuccess = onUnlock,
            onError = { message = it },
        )
    }

    LaunchedEffect(Unit) { prompt() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050706), Color(0xFF07140C)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF111612))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF7CFF8A), modifier = Modifier.height(58.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("AdisChat Locked", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFAEB7B2), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = ::prompt,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Unlock", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFAEB7B2)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Sign out")
            }
        }
    }
}

private fun authenticateForAppOpen(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onError("Fingerprint is not available on this screen")
        return
    }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    when (BiometricManager.from(context).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("Set up fingerprint on this phone first")
            return
        }
        else -> {
            onError("Fingerprint cannot be used right now")
            return
        }
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            override fun onAuthenticationFailed() = onError("Fingerprint did not match")
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock AdisChat")
        .setSubtitle("Confirm your fingerprint to open the app")
        .setAllowedAuthenticators(authenticators)
        .setNegativeButtonText("Cancel")
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun normalizeBrowserUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}
