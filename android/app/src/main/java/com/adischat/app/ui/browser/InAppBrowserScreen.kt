package com.adischat.app.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.network.CloudflareClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.adischat.app.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// Pending requests from the JS bridge — handled by Compose UI
// ─────────────────────────────────────────────────────────────────────────────

private data class PendingPermission(val resolverId: String, val host: String)

private data class PendingPayment(
    val resolverId: String,
    val host:       String,
    val merchantUid: String,
    val amount:      Double,
    val currency:    String,
    val orderRef:    String,
    val description: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserScreen(
    url: String,
    onClose: () -> Unit,
    currentUser: AppUser? = null,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = remember { AppPreferences(context) }

    var webView      by remember { mutableStateOf<WebView?>(null) }
    var loading      by remember { mutableStateOf(true) }
    var progress     by remember { mutableStateOf(0) }
    var currentUrl   by remember { mutableStateOf(url) }
    var canGoBack    by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Hostname currently in the address bar — used for per-origin permission gating.
    var currentHost  by remember { mutableStateOf(hostOf(url)) }
    // Hostnames the user has granted identity access to in this session.
    val grantedHosts = remember { mutableStateOf(setOf<String>()) }
    // JSON snapshot of the logged-in user. Updated whenever currentUser changes.
    var fullUserJson by remember { mutableStateOf(userToJson(currentUser)) }

    // Compose-driven dialog state — populated by the JS bridge, drained by the UI.
    var pendingPermission by remember { mutableStateOf<PendingPermission?>(null) }
    var pendingPayment    by remember { mutableStateOf<PendingPayment?>(null) }

    // Hydrate user JSON if not supplied
    LaunchedEffect(currentUser) {
        fullUserJson = userToJson(currentUser)
        webView?.evaluateJavascript(buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)), null)
    }
    LaunchedEffect(Unit) {
        if (currentUser == null) {
            runCatching { AuthRepository().getCurrentAppUser() }
                .getOrNull()
                ?.let {
                    fullUserJson = userToJson(it)
                    webView?.evaluateJavascript(
                        buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)),
                        null,
                    )
                }
        }
    }

    // Re-read persisted grants whenever the host changes.
    LaunchedEffect(currentHost) {
        if (currentHost.isNotBlank()) {
            val granted = runCatching { prefs.isWebBridgeGranted(currentHost) }.getOrDefault(false)
            grantedHosts.value = if (granted) grantedHosts.value + currentHost else grantedHosts.value - currentHost
            webView?.evaluateJavascript(
                buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)),
                null,
            )
        }
    }

    // The bridge — methods run on a background thread, so all UI work must hop
    // back via Compose state (setters are thread-safe on snapshots).
    val bridge = remember {
        AdisChatJsBridge(
            getUserJson  = { effectiveUserJson(currentHost, grantedHosts.value, fullUserJson) },
            isAdisChatFn = { true },
            getHostFn    = { currentHost },
            onRequestPermission = { resolverId ->
                // First-party origins (adischat.com etc.) are auto-granted —
                // the user is already authenticated in the host app, so we
                // don't make them tap a "Connect" prompt on our own site.
                if (isTrustedHost(currentHost)) {
                    webView?.post {
                        resolveJs(webView, resolverId, """{"granted":true,"trusted":true}""")
                    }
                } else {
                    pendingPermission = PendingPermission(resolverId, currentHost)
                }
            },
            onRequestPayment = { resolverId, json ->
                runCatching {
                    val o = JSONObject(json)
                    pendingPayment = PendingPayment(
                        resolverId  = resolverId,
                        host        = currentHost,
                        merchantUid = o.optString("merchantUid").ifBlank { o.optString("to") },
                        amount      = o.optDouble("amount", 0.0),
                        currency    = o.optString("currency", "ETB").uppercase(),
                        orderRef    = o.optString("orderRef"),
                        description = o.optString("description"),
                    )
                }.onFailure {
                    webView?.post {
                        resolveJs(webView, resolverId, """{"ok":false,"error":"Bad payment args"}""")
                    }
                }
            },
            onRequestBalance = { resolverId ->
                scope.launch {
                    // Pull balance from the same wallet the user sees in Profile —
                    // the main Adis Wallet on adischat-wallet-api. This is what the
                    // /api/wallet-pay endpoint actually debits, so the banner and
                    // the actual debited account stay in sync.
                    val result = runCatching {
                        withContext(Dispatchers.IO) { CloudflareClient.walletApi.getWallet() }
                    }
                    val json = result.fold(
                        onSuccess = { w -> """{"ok":true,"balance":${w.balance},"currency":"${w.currency.ifBlank { "ETB" }}"}""" },
                        onFailure = { e -> """{"ok":false,"error":${JSONObject.quote(CloudflareClient.friendlyErrorMessage(e, "Request failed. Please try again."))}}""" },
                    )
                    webView?.post { resolveJs(webView, resolverId, json) }
                }
            },
        )
    }

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(78.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // Always revalidate the HTML against the server so that
                        // freshly-deployed bridge JS (window.AdisChat hooks) is
                        // picked up the moment the merchant ships an update.
                        // Subresources (CSS, images) still use HTTP cache headers.
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        settings.textZoom = 100
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.setSupportZoom(false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        // Let pages detect they're inside AdisChat
                        settings.userAgentString = (settings.userAgentString ?: "") + " AdisChat/1.0"

                        addJavascriptInterface(bridge, "AdisChat")

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                loading = newProgress < 100
                            }
                            // Forward JS console.* messages to Logcat for debugging.
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                cm ?: return false
                                val tag  = "AdisChatWeb"
                                val line = "[${cm.messageLevel()}] ${cm.message()} (${cm.sourceId()}:${cm.lineNumber()})"
                                when (cm.messageLevel()) {
                                    ConsoleMessage.MessageLevel.ERROR   -> Log.e(tag, line)
                                    ConsoleMessage.MessageLevel.WARNING -> Log.w(tag, line)
                                    ConsoleMessage.MessageLevel.DEBUG   -> Log.d(tag, line)
                                    else                                 -> Log.i(tag, line)
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                view.loadUrl(request.url.toString())
                                return true
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                val u = url.orEmpty()
                                currentUrl  = u.ifBlank { currentUrl }
                                currentHost = hostOf(currentUrl)
                                canGoBack    = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                view?.evaluateJavascript(
                                    buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)),
                                    null,
                                )
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                val u = url.orEmpty()
                                currentUrl  = u.ifBlank { currentUrl }
                                currentHost = hostOf(currentUrl)
                                canGoBack    = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                view?.evaluateJavascript(
                                    buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)),
                                    null,
                                )
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                Log.e("AdisChatWeb",
                                    "load error url=${request?.url} mainFrame=${request?.isForMainFrame} code=${error?.errorCode} desc=${error?.description}")
                            }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                Log.e("AdisChatWeb",
                                    "http error url=${request?.url} mainFrame=${request?.isForMainFrame} status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase}")
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }

        // ── Browser chrome (top bar) ──────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserIconButton(enabled = canGoBack,    onClick = { webView?.goBack()    }) {
                    Icon(Icons.Default.KeyboardArrowLeft,  contentDescription = "Back")
                }
                BrowserIconButton(enabled = canGoForward, onClick = { webView?.goForward() }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Forward")
                }
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(7.dp))
                        Text(
                            cleanAddress(currentUrl),
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick  = { webView?.reload() },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)),
                ) { Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(
                    onClick  = onClose,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)),
                ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }

    // ── Permission dialog ────────────────────────────────────────────────────
    pendingPermission?.let { req ->
        PermissionDialog(
            host = req.host,
            user = currentUser,
            onAllow = {
                scope.launch {
                    runCatching { prefs.setWebBridgeGranted(req.host, true) }
                    grantedHosts.value = grantedHosts.value + req.host
                    val r = req
                    pendingPermission = null
                    webView?.post {
                        resolveJs(webView, r.resolverId, """{"granted":true}""")
                        // Re-broadcast the now-unlocked user identity
                        webView?.evaluateJavascript(
                            buildBootstrapJs(effectiveUserJson(currentHost, grantedHosts.value, fullUserJson)),
                            null,
                        )
                    }
                }
            },
            onDeny = {
                val r = req
                pendingPermission = null
                webView?.post { resolveJs(webView, r.resolverId, """{"granted":false}""") }
            },
        )
    }

    // ── Payment confirmation + biometric ─────────────────────────────────────
    // The bridge is purely a biometric AUTHORIZATION gate — it shows the
    // "Pay X to Y?" sheet, prompts fingerprint, and returns ok=true on
    // confirmation. The page is responsible for executing the actual wallet
    // debit by calling /api/wallet-pay (which hits the main Adis Wallet on
    // Supabase). Decoupling like this keeps the bridge wallet-agnostic.
    pendingPayment?.let { req ->
        PaymentDialog(
            payment     = req,
            currentUser = currentUser,
            onConfirm = {
                val r = req
                authenticateFingerprint(
                    context = context,
                    title   = "Confirm wallet payment",
                    subtitle = "Pay ${formatAmount(r.amount)} ${r.currency} from your AdisChat wallet",
                    onSuccess = {
                        // The serverless /api/wallet-pay proxy forwards the payment to
                        // adischat-api /api/wallet/pay, which requires a Firebase Bearer
                        // token. We mint a fresh ID token AND fetch wallet info (phone is
                        // canonical from the wallet record, not from the AdisChat profile).
                        scope.launch {
                            val idToken = runCatching {
                                withContext(Dispatchers.IO) {
                                    FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token.orEmpty()
                                }
                            }.getOrDefault("")
                            val walletInfo = runCatching {
                                withContext(Dispatchers.IO) { CloudflareClient.walletApi.getWallet() }
                            }.getOrNull()
                            val phone = walletInfo?.phone
                                ?.takeIf { it.isNotBlank() }
                                ?: currentUser?.phone.orEmpty()
                            val email = walletInfo?.email
                                ?.takeIf { it.isNotBlank() }
                                ?: currentUser?.email.orEmpty()
                            val payload = JSONObject()
                                .put("ok",            true)
                                .put("authorized",    true)
                                .put("uid",           currentUser?.uid ?: "")
                                .put("name",          currentUser?.name ?: "")
                                .put("email",         email)
                                .put("phone",         phone)
                                .put("walletId",      walletInfo?.walletId ?: "")
                                .put("walletBalance", walletInfo?.balance ?: 0.0)
                                .put("token",        idToken)
                                .put("idToken",      idToken)
                                .put("amount",       r.amount)
                                .put("currency",     r.currency)
                                .put("orderRef",     r.orderRef)
                                .put("description",  r.description)
                                .put("authorizedAt", System.currentTimeMillis())
                                .toString()
                            pendingPayment = null
                            webView?.post { resolveJs(webView, r.resolverId, payload) }
                        }
                    },
                    onError = { msg ->
                        pendingPayment = null
                        webView?.post {
                            resolveJs(webView, r.resolverId,
                                JSONObject().put("ok", false).put("authorized", false).put("error", msg).toString())
                        }
                    },
                )
            },
            onCancel = {
                val r = req
                pendingPayment = null
                webView?.post {
                    resolveJs(webView, r.resolverId,
                        """{"ok":false,"authorized":false,"error":"User cancelled"}""")
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionDialog(
    host: String,
    user: AppUser?,
    onAllow: () -> Unit,
    onDeny:  () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(Icons.Default.Verified, contentDescription = null, tint = Primary) },
        title = { Text("Share your AdisChat account?") },
        text = {
            Column {
                Text(
                    "“${host.ifBlank { "This page" }}” wants to access your AdisChat profile:",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Bullet("Name (${user?.name ?: "your full name"})")
                Bullet("Username (@${user?.username ?: "your username"})")
                Bullet("Email and phone")
                Bullet("Profile photo")
                Spacer(Modifier.height(10.dp))
                Text(
                    "This site will also be able to ask you to pay using your AdisChat wallet. You'll still confirm each payment with your fingerprint.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onAllow) { Text("Allow") } },
        dismissButton  = { OutlinedButton(onClick = onDeny) { Text("Deny") } },
    )
}

@Composable
private fun PaymentDialog(
    payment: PendingPayment,
    currentUser: AppUser?,
    onConfirm: () -> Unit,
    onCancel:  () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon  = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Primary) },
        title = { Text("Pay ${formatAmount(payment.amount)} ${payment.currency}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From ",  fontSize = 14.sp)
                    Text(currentUser?.name ?: "Your AdisChat wallet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("To ",     fontSize = 14.sp)
                    Text(payment.host.ifBlank { "Merchant" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                if (payment.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(payment.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (payment.orderRef.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Ref: ${payment.orderRef}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("You'll be asked for your fingerprint to confirm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Pay ${formatAmount(payment.amount)}") } },
        dismissButton  = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•  ", fontSize = 14.sp, color = Primary)
        Text(text,  fontSize = 14.sp)
    }
}

@Composable
private fun BrowserIconButton(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.78f else 0.38f)),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Biometric helper
// ─────────────────────────────────────────────────────────────────────────────

private fun authenticateFingerprint(
    context:   Context,
    title:     String,
    subtitle:  String,
    onSuccess: () -> Unit,
    onError:   (String) -> Unit,
) {
    val activity = context.findFragmentActivity()
        ?: return onError("Biometric not available on this screen")

    val auths = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
    when (BiometricManager.from(context).canAuthenticate(auths)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            return onError("Set up fingerprint on this phone first")
        else ->
            return onError("Biometric unavailable")
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            override fun onAuthenticationFailed() { /* let user retry; don't terminate here */ }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(auths)
        .setNegativeButtonText("Cancel")
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper   -> baseContext.findFragmentActivity()
    else                -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// JS bridge
// ─────────────────────────────────────────────────────────────────────────────

private class AdisChatJsBridge(
    private val getUserJson:        () -> String,
    private val isAdisChatFn:       () -> Boolean,
    private val getHostFn:          () -> String,
    private val onRequestPermission: (resolverId: String) -> Unit,
    private val onRequestPayment:    (resolverId: String, argsJson: String) -> Unit,
    private val onRequestBalance:    (resolverId: String) -> Unit,
) {
    @JavascriptInterface fun getUser():     String  = getUserJson()
    @JavascriptInterface fun isAdisChat():  Boolean = isAdisChatFn()
    @JavascriptInterface fun version():     String  = "1.0"
    @JavascriptInterface fun host():        String  = getHostFn()

    @JavascriptInterface
    fun requestUserPermission(resolverId: String) { onRequestPermission(resolverId) }

    @JavascriptInterface
    fun requestPayment(argsJson: String, resolverId: String) { onRequestPayment(resolverId, argsJson) }

    @JavascriptInterface
    fun requestBalance(resolverId: String) { onRequestBalance(resolverId) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun cleanAddress(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")

/** Resolve a JS Promise via the bridge's resolver registry. */
private fun resolveJs(webView: WebView?, resolverId: String, jsonResult: String) {
    webView ?: return
    val safeId   = resolverId.replace("\\", "\\\\").replace("'", "\\'")
    val safeJson = jsonResult.replace("\\", "\\\\").replace("'", "\\'")
    val js = """
        (function(){
          try {
            if (window.AdisChat && typeof window.AdisChat._resolve === 'function') {
              window.AdisChat._resolve('$safeId', '$safeJson');
            }
          } catch (e) { /* no-op */ }
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

/**
 * First-party AdisChat-owned origins. The bridge exposes the logged-in
 * identity to these pages immediately — no permission prompt, since the user
 * is opening "our own" site from inside our own app. Third-party pages still
 * have to go through window.AdisChat.permission().
 */
private val TRUSTED_HOSTS: Set<String> = setOf(
    "adischat.com",
    "www.adischat.com",
)
private fun isTrustedHost(host: String): Boolean =
    host.isNotBlank() && (host in TRUSTED_HOSTS || host.endsWith(".adischat.com"))

/** Identity returned to the page — gated by per-origin permission. */
private fun effectiveUserJson(host: String, grants: Set<String>, fullJson: String): String =
    if (host.isNotBlank() && (isTrustedHost(host) || host in grants)) fullJson else "null"

private fun userToJson(user: AppUser?): String {
    if (user == null) return "null"
    return JSONObject()
        .put("uid",               user.uid)
        .put("name",              user.name)
        .put("username",          user.username)
        .put("email",             user.email)
        .put("phone",             user.phone)
        .put("photoUrl",          user.photoUrl)
        .put("role",              user.role)
        .put("emailVerified",     user.emailVerified)
        .put("verificationLevel", user.verificationLevel)
        .toString()
}

/**
 * The script we inject on every page load. Wires up:
 *
 *   window.__adischatUser       — identity object (null until permission granted)
 *   adischatready event         — fires when bootstrap completes
 *   window.AdisChat._resolvers  — promise registry (used by native to resolve)
 *   window.AdisChat.permission()→ Promise<{granted}>
 *   window.AdisChat.payment(a)  → Promise<{ok, transactionId, balanceAfter, ...}>
 *   window.AdisChat.balance()   → Promise<{ok, balance, currency}>
 */
private fun buildBootstrapJs(userJson: String): String = """
    (function () {
      try {
        window.__adischatUser = $userJson;
        if (window.AdisChat) {
          window.AdisChat._resolvers = window.AdisChat._resolvers || {};
          window.AdisChat._resolve = function (id, jsonStr) {
            var fn = window.AdisChat._resolvers[id];
            if (fn) {
              delete window.AdisChat._resolvers[id];
              try { fn(JSON.parse(jsonStr)); } catch (e) { fn({ ok:false, error:'parse error' }); }
            }
          };
          var newId = function (p) { return p + '_' + Math.random().toString(36).slice(2) + Date.now().toString(36); };
          window.AdisChat.permission = function () {
            return new Promise(function (resolve) {
              var id = newId('perm');
              window.AdisChat._resolvers[id] = resolve;
              try { window.AdisChat.requestUserPermission(id); }
              catch (e) { resolve({ granted:false, error:e.message }); }
            });
          };
          window.AdisChat.payment = function (args) {
            return new Promise(function (resolve) {
              var id = newId('pay');
              window.AdisChat._resolvers[id] = resolve;
              try { window.AdisChat.requestPayment(JSON.stringify(args || {}), id); }
              catch (e) { resolve({ ok:false, error:e.message }); }
            });
          };
          window.AdisChat.balance = function () {
            return new Promise(function (resolve) {
              var id = newId('bal');
              window.AdisChat._resolvers[id] = resolve;
              try { window.AdisChat.requestBalance(id); }
              catch (e) { resolve({ ok:false, error:e.message }); }
            });
          };
        }
        try { document.dispatchEvent(new Event('adischatready')); } catch (_) {}
      } catch (e) { /* no-op */ }
    })();
""".trimIndent()

private fun formatAmount(value: Double): String {
    // 250 → "250", 250.5 → "250.50"
    return if (value % 1.0 == 0.0) value.toInt().toString()
           else String.format("%.2f", value)
}

private fun parseHttpErrorMessage(e: Throwable): String? = runCatching {
    val cls = e.javaClass
    if (cls.simpleName == "HttpException") {
        val resp = cls.getMethod("response").invoke(e)
        val errBody = resp?.javaClass?.getMethod("errorBody")?.invoke(resp)
        val str = errBody?.javaClass?.getMethod("string")?.invoke(errBody) as? String
        if (!str.isNullOrBlank()) {
            return@runCatching JSONObject(str).optString("error").ifBlank { null }
        }
    }
    null
}.getOrNull()
