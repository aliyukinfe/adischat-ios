package com.adischat.app.navigation

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
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
import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.feature.restaurants.qa.OfflinePreviewShellScreen
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.security.SessionSecurity
import kotlinx.coroutines.launch
import com.adischat.app.data.model.AdisLinkTarget
import com.adischat.app.data.model.AdisLinkType
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

    val signOutScope = rememberCoroutineScope()
    val themeStr by prefs.themeFlow.collectAsStateWithLifecycle(initialValue = "system")
    val storedFingerprintEnabled by prefs.walletFingerprintFlow.collectAsStateWithLifecycle(initialValue = false)
    val fingerprintEnabled = FeatureFlags.FEATURE_WALLET_ENABLED && storedFingerprintEnabled
    val appTheme = when (themeStr) {
        "light" -> AppTheme.LIGHT
        "dark"  -> AppTheme.DARK
        else    -> AppTheme.SYSTEM
    }

    AdisChatTheme(appTheme = appTheme) {
        if (BuildConfig.OFFLINE_QA_PREVIEW) {
            OfflinePreviewShellScreen()
            return@AdisChatTheme
        }
        val navController = rememberNavController()
        val isLoggedIn    = FirebaseAuth.getInstance().currentUser != null
        val start      = if (isLoggedIn) "main" else "auth"
        var browserUrl by remember { mutableStateOf<String?>(null) }
        var pendingAdisLink by remember { mutableStateOf(activity?.deepLinkData?.toAdisLinkTarget()) }
        var pendingNotificationConversationId by remember { mutableStateOf(activity?.pendingNotificationConversationId) }
        var pendingShopConversationId by remember { mutableStateOf(activity?.pendingShopConversationId) }
        var pendingShopNotificationContext by remember { mutableStateOf(activity?.pendingShopNotificationContext) }
        var pendingShopNotificationStoreId by remember { mutableStateOf(activity?.pendingShopNotificationStoreId) }
        var pendingRestaurantQrToken by remember { mutableStateOf(activity?.pendingRestaurantQrToken) }
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

        LaunchedEffect(activity?.deepLinkData) {
        activity?.deepLinkData?.let { uri ->
        uri.toAdisLinkTarget()?.let { pendingAdisLink = it }
        }
        }

        LaunchedEffect(activity?.pendingNotificationConversationId) {
        pendingNotificationConversationId = activity?.pendingNotificationConversationId
        }

        LaunchedEffect(activity?.pendingShopConversationId) {
        pendingShopConversationId = activity?.pendingShopConversationId
        }
        LaunchedEffect(activity?.pendingShopNotificationContext) {
        pendingShopNotificationContext = activity?.pendingShopNotificationContext
        }
        LaunchedEffect(activity?.pendingShopNotificationStoreId) {
        pendingShopNotificationStoreId = activity?.pendingShopNotificationStoreId
        }

        LaunchedEffect(activity?.pendingRestaurantQrToken) {
        pendingRestaurantQrToken = activity?.pendingRestaurantQrToken
        }

        Box(Modifier.fillMaxSize()) {
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
        // Shared with MainScreen - viewModel() inside this NavBackStackEntry
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
        signOutScope.launch {
        SessionSecurity.hardSignOut(context)
        navController.navigate("auth") {
        popUpTo("main") { inclusive = true }
        }
        }
        },
        )
        } else if (activeUrl != null) {
        InAppBrowserScreen(
        url      = normalizeBrowserUrl(activeUrl),
        onClose     = { browserUrl = null },
        currentUser = currentUser,
        )
        } else {
        MainScreen(
        prefs       = prefs,
        pendingAdisLink = pendingAdisLink,
        onAdisLinkConsumed = { pendingAdisLink = null },
        pendingNotificationConversationId = pendingNotificationConversationId,
        onNotificationConversationConsumed = {
        pendingNotificationConversationId = null
        activity?.pendingNotificationConversationId = null
        },
        pendingShopConversationId = pendingShopConversationId,
        onShopConversationConsumed = {
        pendingShopConversationId = null
        activity?.pendingShopConversationId = null
        },
        pendingShopNotificationContext = pendingShopNotificationContext,
        pendingShopNotificationStoreId = pendingShopNotificationStoreId,
        onShopNotificationConsumed = {
        pendingShopNotificationContext = null
        activity?.pendingShopNotificationContext = null
        pendingShopNotificationStoreId = null
        activity?.pendingShopNotificationStoreId = null
        },
        initialRestaurantQrToken = pendingRestaurantQrToken,
        onRestaurantQrTokenConsumed = {
        pendingRestaurantQrToken = null
        activity?.pendingRestaurantQrToken = null
        },
        onOpenUrl   = { url -> browserUrl = url },
        onSignOut   = {
        signOutScope.launch {
        SessionSecurity.hardSignOut(context)
        navController.navigate("auth") {
        popUpTo("main") { inclusive = true }
        }
        }
        }
        )
        }
        }
        }
        com.adischat.app.ui.calls.CallHost()
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

private fun Uri.toAdisLinkTarget(): AdisLinkTarget? {
    val isWebLink = scheme in listOf("http", "https") && host.equals("me.adischat.com", ignoreCase = true)
    val isAppLink = scheme.equals("adischat", ignoreCase = true) &&
        host?.lowercase() in listOf("link", "user", "channel", "group")
    if (!isWebLink && !isAppLink) return null
    val segments = pathSegments.filter { it.isNotBlank() }
    val rawSlug = when {
        isAppLink && host.equals("user", ignoreCase = true) -> getQueryParameter("username") ?: segments.firstOrNull()
        isAppLink && host.equals("channel", ignoreCase = true) -> getQueryParameter("id") ?: segments.firstOrNull()
        isAppLink && host.equals("group", ignoreCase = true) -> getQueryParameter("id") ?: segments.firstOrNull()
        else -> segments.firstOrNull()
    }?.trim()?.removePrefix("@") ?: return null
    if (rawSlug.isBlank()) return null
    val explicitType = getQueryParameter("type")?.lowercase()
    val firstSegment = if (isAppLink && host.equals("link", ignoreCase = true)) {
        segments.firstOrNull()?.lowercase()
    } else {
        host?.lowercase().takeIf { isAppLink && it in listOf("user", "channel", "group") }
        ?: segments.firstOrNull()?.lowercase()
    }
    val type = when {
        explicitType == "channel" || firstSegment in listOf("c", "channel") -> AdisLinkType.CHANNEL
        explicitType == "group" || firstSegment in listOf("g", "group") -> AdisLinkType.GROUP
        else -> AdisLinkType.USER
    }
    val slug = when {
        isWebLink && firstSegment in listOf("u", "user", "c", "channel", "g", "group") -> segments.getOrNull(1)
        isAppLink && host.equals("link", ignoreCase = true) && firstSegment in listOf("u", "user", "c", "channel", "g", "group") -> segments.getOrNull(1)
        else -> rawSlug
    }?.removePrefix("@")?.trim().orEmpty().ifBlank { rawSlug }
    return if (slug.isBlank()) null else AdisLinkTarget(slug = slug, type = type)
}
