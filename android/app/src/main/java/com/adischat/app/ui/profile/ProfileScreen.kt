package com.adischat.app.ui.profile

import android.net.Uri
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.adischat.app.feature.shop.presentation.ShopRoute
import com.adischat.app.FeatureFlags
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AdisLinks
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.VerificationStatus
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.data.preferences.WalletState
import com.adischat.app.data.preferences.WalletTransaction
import com.adischat.app.ui.common.AdisVerifiedBadge
import com.adischat.app.ui.theme.Amber
import com.adischat.app.ui.theme.AmberBg
import com.adischat.app.ui.theme.Cyan
import com.adischat.app.ui.theme.CyanBg
import com.adischat.app.ui.theme.ErrorBg
import com.adischat.app.ui.theme.ErrorRed
import com.adischat.app.ui.theme.GreenBg
import com.adischat.app.ui.theme.InfoBg
import com.adischat.app.ui.theme.InfoBlue
import com.adischat.app.ui.theme.Orange
import com.adischat.app.ui.theme.OrangeBg
import com.adischat.app.ui.theme.Primary
import com.adischat.app.ui.theme.PrimaryDk
import com.adischat.app.ui.theme.RedBg
import com.adischat.app.ui.theme.RedColor
import com.adischat.app.ui.theme.Violet
import com.adischat.app.ui.theme.VioletBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import com.adischat.app.ui.localization.tr
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.QrCodeScanner
import com.adischat.app.data.repository.LinkedDevicesRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.LinkedDeviceResponse
import com.adischat.app.network.PrivacyUpdateBody
import com.adischat.app.notifications.AdisNotificationManager

// Palette aligned with the home screen (HomeScreen.kt): bg 0B0D12, surface 12151C, mint 34D399.
private val ProfileBlack: Color
    @Composable get() = Color(0xFF0B0D12)
private val ProfileSurface: Color
    @Composable get() = Color(0xFF12151C)
private val ProfileSurfaceSoft: Color
    @Composable get() = Color(0xFF171B24)
private val ProfileLine: Color
    @Composable get() = Color(0xFF1F2530)
private val ProfileMuted: Color
    @Composable get() = Color(0xFF9CA3AF)
private val ProfileGreen: Color
    @Composable get() = Color(0xFF34D399)
private val ProfileBlue: Color
    @Composable get() = Color(0xFF1DA1F2)   // verified blue badge
private val ProfileDanger: Color
    @Composable get() = MaterialTheme.colorScheme.error
private val ProfileText: Color
    @Composable get() = Color(0xFFF9FAFB)
private val ProfileOnAccent: Color
    @Composable get() = Color(0xFF04342C)
private val ProfileIconInactive: Color
    @Composable get() = ProfileMuted
private val ProfileMint: Color
    @Composable get() = Color(0xFF16241C)
private val ProfileCardBrush: Brush
    @Composable get() = Brush.linearGradient(
        listOf(Color(0xFF0E2A1F), Color(0xFF0E1A14), Color(0xFF12151C))
    )
private val ProfileWalletBrush: Brush
    @Composable get() = Brush.linearGradient(
        listOf(Color(0xFF0E2A1F), Color(0xFF0B120E), Color(0xFF13241B))
    )

private enum class WalletPinMode { SET, CHANGE }
private enum class AccountCenterPage(val title: String, val subtitle: String, val icon: ImageVector) {
    SECURITY("Security", "Password, PIN, 2FA, sessions", Icons.Default.Security),
    PRIVACY("Privacy", "Visibility and messaging controls", Icons.Default.VisibilityOff),
    DEVICES("Devices", "Current device and logged-in sessions", Icons.Default.Devices),
    NOTIFICATIONS("Notifications", "Messages, channels, wallet alerts", Icons.Default.Notifications),
    APPEARANCE("Appearance", "Theme, font, and language", Icons.Default.Palette),
    STORAGE("Storage & Data", "Cache, downloads, and data saver", Icons.Default.Storage),
    VERIFICATION("Verification", "Email, phone, profile, badge", Icons.Default.Verified),
    SUPPORT("Support", "Help, legal, bugs, feedback", Icons.Default.Help),
    ACCOUNT("Account", "Logout and account ownership", Icons.Default.Person),
}

private enum class PrivacyControl(val title: String) {
    ONLINE_STATUS("Online Status"),
    PROFILE_PHOTO("Profile Photo Visibility"),
    PHONE_NUMBER("Phone Number Visibility"),
    READ_RECEIPTS("Read Receipts"),
    MESSAGE_PERMISSIONS("Who Can Message Me"),
    CALL_PERMISSIONS("Who Can Call Me"),
    GROUP_INVITES("Who Can Add Me to Groups"),
    BLOCKED_USERS("Blocked Users"),
}

@Composable
private fun isProfileLight(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
private fun ProfileLuxuryBackground() {
    Box(Modifier.fillMaxSize().background(ProfileBlack))
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    prefs: AppPreferences,
    onSignOut: () -> Unit,
    onGoScan: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenShopRoute: (ShopRoute) -> Unit = {},
) {
    val vm: ProfileViewModel = viewModel()
    val user      by vm.user.collectAsStateWithLifecycle()
    val saving      by vm.saving.collectAsStateWithLifecycle()
    val toast      by vm.toast.collectAsStateWithLifecycle()
    val verificationStatus by vm.verificationStatus.collectAsStateWithLifecycle()
    val walletState      by vm.wallet.collectAsStateWithLifecycle()
    val walletLoading      by vm.walletLoading.collectAsStateWithLifecycle()
    val lastTransaction    by vm.lastTransaction.collectAsStateWithLifecycle()
    val fatherName      by prefs.fatherNameFlow.collectAsStateWithLifecycle(initialValue = "")
    val walletEnabled = FeatureFlags.FEATURE_WALLET_ENABLED
    val fingerprintEnabled by prefs.walletFingerprintFlow.collectAsStateWithLifecycle(initialValue = false)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollState  = rememberScrollState()
    val isScrolled   by remember { derivedStateOf { scrollState.value > 12 } }
    val fullSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showEditProfile   by remember { mutableStateOf(false) }
    var showReceive       by remember { mutableStateOf(false) }
    var showProfileQr     by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var changePwBusy      by remember { mutableStateOf(false) }
    var showSend      by remember { mutableStateOf(false) }
    var showDeposit       by remember { mutableStateOf(false) }
    var showPay      by remember { mutableStateOf(false) }
    var showHistory       by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showStorageSettings by remember { mutableStateOf(false) }
    var showPinSheet      by remember { mutableStateOf(false) }
    var pinSheetMode      by remember { mutableStateOf(WalletPinMode.SET) }
    var showDevices       by remember { mutableStateOf(false) }
    var showLoginHistory  by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLastSeenDialog by remember { mutableStateOf(false) }
    var activePrivacyControl by remember { mutableStateOf<PrivacyControl?>(null) }
    var showApplyBadge    by remember { mutableStateOf(false) }
    var showAdminPanel    by remember { mutableStateOf(false) }
    var showWalletPage    by remember { mutableStateOf(false) }
    var selectedAccountPage by remember { mutableStateOf<AccountCenterPage?>(null) }
    var selectedTransaction by remember { mutableStateOf<WalletTransaction?>(null) }

    LaunchedEffect(lastTransaction) {
        if (walletEnabled) lastTransaction?.let { selectedTransaction = it }
    }

    LaunchedEffect(walletEnabled) {
        if (!walletEnabled) {
        showWalletPage = false
        showReceive = false
        showSend = false
        showDeposit = false
        showPay = false
        showHistory = false
        showPinSheet = false
        selectedTransaction = null
        }
    }

    BackHandler(enabled = (walletEnabled && showWalletPage) || selectedAccountPage != null) {
        if (walletEnabled && showWalletPage) showWalletPage = false
        else selectedAccountPage = null
    }

    LaunchedEffect(toast) {
        toast?.let {
        snackbarHost.showSnackbar(it)
        vm.clearToast()
        }
    }

    val name     = user?.name.orEmpty().ifBlank { "Aliyu Alemu" }
    val realUsername = user?.username.orEmpty().trim().removePrefix("@")
    val username = realUsername.ifBlank { "set_username" }
    val handle   = "@$username"
    val phone    = user?.phone.orEmpty().ifBlank { "+251 XXX XXX XXX" }
    val email    = user?.email.orEmpty().ifBlank { "aliyu@adischat.app" }
    val initial  = name.firstOrNull()?.uppercase() ?: "A"
    val balance  = walletState.balance
    val userId   = walletState.userId.ifBlank { publicUserIdFor(user?.uid ?: username) }
    val walletId = walletState.walletId.ifBlank { walletIdFor(user, username) }
    val usernamePayload = realUsername.takeIf { it.isNotBlank() }?.let { usernameQrPayload(username = it, name = name, phone = phone) }.orEmpty()
    val verificationLevel = user?.verificationLevel ?: 0
    // Wallet unlocks at Level 2 (profile complete + admin approved).
    val walletVerified = (verificationStatus?.verificationLevel ?: verificationLevel) >= 2

    Scaffold(
        containerColor = ProfileBlack,
        snackbarHost   = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
        Modifier
        .fillMaxSize()
        .background(ProfileBlack)
        .padding(padding)
        ) {
        ProfileLuxuryBackground()
        PullToRefreshBox(
        isRefreshing = walletEnabled && walletLoading,
        onRefresh = { vm.loadUser() },
        modifier = Modifier.fillMaxSize()
        ) {
        Column(
        Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(top = if ((walletEnabled && showWalletPage) || selectedAccountPage != null) 18.dp else 104.dp, bottom = 112.dp)
        ) {
        if (walletEnabled && showWalletPage) {
        FullWalletPage(
        state = walletState,
        balance = balance,
        fingerprintEnabled = fingerprintEnabled,
        deviceName = currentDeviceName(),
        onBack = { showWalletPage = false },
        onActivate = {
        pinSheetMode = WalletPinMode.SET
        showPinSheet = true
        },
        onDeposit = {
        scope.launch { snackbarHost.showSnackbar(tr("Deposit is coming soon")) }
        },
        onWithdraw = {
        scope.launch { snackbarHost.showSnackbar(tr("Withdrawal setup is coming next")) }
        },
        onSend = {
        when {
        !walletVerified -> showApplyBadge = true
        !walletState.walletPinSet -> {
        pinSheetMode = WalletPinMode.SET
        showPinSheet = true
        }
        else -> showSend = true
        }
        },
        onReceive = {
        if (!walletVerified) showApplyBadge = true
        else { vm.loadReceiveQr(); showReceive = true }
        },
        onPay = {
        if (!walletVerified) showApplyBadge = true
        else { vm.loadPayCode(); showPay = true }
        },
        onHistory = { if (walletEnabled) { vm.loadWalletTransactions(); showHistory = true } },
        onSetPin = {
        pinSheetMode = WalletPinMode.SET
        showPinSheet = true
        },
        onChangePin = {
        pinSheetMode = WalletPinMode.CHANGE
        showPinSheet = true
        },
        onToggleFingerprint = { enabled ->
        if (enabled) {
        authenticateWithBiometric(
        context = context,
        title = "Activate fingerprint",
        subtitle = "Confirm your phone fingerprint to protect AdisChat opening",
        onSuccess = {
        scope.launch {
        prefs.setWalletFingerprintEnabled(true)
        snackbarHost.showSnackbar(tr("Fingerprint enabled for app opening"))
        }
        },
        onError = { message ->
        scope.launch { snackbarHost.showSnackbar(message) }
        }
        )
        } else {
        scope.launch {
        prefs.setWalletFingerprintEnabled(false)
        snackbarHost.showSnackbar(tr("Fingerprint disabled"))
        }
        }
        },
        onDevices = { showDevices = true },
        onLoginHistory = { showLoginHistory = true },
        )
        } else {
        val commonActions = AccountCenterActions(
        onWallet = { if (walletEnabled) showWalletPage = true },
        onDeposit = { if (walletEnabled) showDeposit = true },
        onWithdraw = { scope.launch { snackbarHost.showSnackbar(tr("Withdrawal setup is coming next")) } },
        onPaymentMethods = { scope.launch { snackbarHost.showSnackbar(tr("Payment methods are coming soon")) } },
        onHistory = { if (walletEnabled) { vm.loadWalletTransactions(); showHistory = true } },
        onChangePassword = { showChangePassword = true },
        onChangePin = {
        pinSheetMode = if (walletState.walletPinSet) WalletPinMode.CHANGE else WalletPinMode.SET
        showPinSheet = true
        },
        onToggleFingerprint = { enabled ->
        if (enabled) {
        authenticateWithBiometric(
        context = context,
        title = "Activate fingerprint",
        subtitle = "Confirm your phone fingerprint to protect AdisChat opening",
        onSuccess = {
        scope.launch {
        prefs.setWalletFingerprintEnabled(true)
        snackbarHost.showSnackbar(tr("Fingerprint enabled for app opening"))
        }
        },
        onError = { message -> scope.launch { snackbarHost.showSnackbar(message) } }
        )
        } else {
        scope.launch {
        prefs.setWalletFingerprintEnabled(false)
        snackbarHost.showSnackbar(tr("Fingerprint disabled"))
        }
        }
        },
        onTwoFactor = { scope.launch { snackbarHost.showSnackbar(tr("Two-factor authentication is coming soon")) } },
        onDevices = { showDevices = true },
        onLoginHistory = { showLoginHistory = true },
        onNotifications = { showNotifications = true },
        onStorageSettings = { showStorageSettings = true },
        onDarkMode = { scope.launch { prefs.setTheme("dark") } },
        onLightMode = { scope.launch { prefs.setTheme("light") } },
        onSystemTheme = { scope.launch { prefs.setTheme("system") } },
        onLanguage = { scope.launch { snackbarHost.showSnackbar(tr("Language can be changed between English and Amharic")) } },
        onApplyBadge = { showApplyBadge = true },
        onAdminPanel = { showAdminPanel = true },
        onSupport = { label ->
        // Open the relevant page inside the in-app browser.
        val url = when (label) {
        "Help Center" -> "https://www.adischat.com/help"
        "Contact Us" -> "https://www.adischat.com/contact"
        "Privacy Policy" -> "https://www.adischat.com/privacy"
        "Terms of Service"     -> "https://www.adischat.com/terms"
        "Community Guidelines" -> "https://www.adischat.com/community-guidelines"
        "Report Bug" -> "https://www.adischat.com/contact"
        "Send Feedback" -> "https://www.adischat.com/contact"
        else -> null
        }
        if (url != null) onOpenUrl(url)
        else scope.launch { snackbarHost.showSnackbar(tr("$label is coming soon")) }
        },
        onLogout = { showSignOutDialog = true },
        onLastSeenVisibility = { showLastSeenDialog = true },
        onPrivacyControl = { activePrivacyControl = it },
        onDeleteAccount = { showDeleteAccountDialog = true },
        )
        if (selectedAccountPage == null) {
        AccountCenterContent(
        name = name,
        handle = handle,
        phone = phone,
        email = email,
        fatherName = fatherName,
        initial = initial,
        photoUrl = user?.photoUrl.orEmpty(),
        verificationLevel = verificationLevel,
        state = walletState,
        balance = balance,
        onEdit = { showEditProfile = true },
        onMyQr = {
        // Profile QR - encodes me.adischat.com/<username> (NOT the wallet QR).
        if (realUsername.isBlank()) {
        scope.launch { snackbarHost.showSnackbar(tr("Set a username before sharing your QR code")) }
        } else {
        showProfileQr = true
        }
        },
        onShare = {
        if (realUsername.isBlank()) {
        scope.launch { snackbarHost.showSnackbar(tr("Set a username before sharing your profile")) }
        } else {
        shareText(context, "Connect with $name on AdisChat: ${AdisLinks.user(realUsername)}")
        }
        },
        onWallet = { if (walletEnabled) showWalletPage = true },
        onOpenPage = { selectedAccountPage = it },
        onOpenShopRoute = onOpenShopRoute,
        )
        } else {
        AccountCategoryPage(
        page = selectedAccountPage!!,
        user = user,
        verificationStatus = verificationStatus,
        state = walletState,
        balance = balance,
        fingerprintEnabled = fingerprintEnabled,
        walletEnabled = walletEnabled,
        actions = commonActions,
        onBack = { selectedAccountPage = null },
        )
        }
        }
        }
        }

        if (!(walletEnabled && showWalletPage) && selectedAccountPage == null) {
        FloatingProfileHeader(
        isScrolled = isScrolled,
        user       = user,
        onNotifications = { showNotifications = true },
        onDarkMode = {
        scope.launch {
        prefs.setTheme("dark")
        }
        },
        onLightMode = {
        scope.launch {
        prefs.setTheme("light")
        }
        },
        modifier   = Modifier.align(Alignment.TopCenter)
        )
        }
        }
    }

    // - Bottom sheets -

    if (walletEnabled && showReceive) {
        ModalBottomSheet(
        onDismissRequest = { showReceive = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        ReceiveMoneySheet(
        username = username,
        walletId = walletId,
        onPay = {
        showReceive = false
        vm.loadPayCode()
        showPay = true
        },
        onDismiss = { showReceive = false }
        )
        }
    }

    if (showProfileQr) {
        ModalBottomSheet(
        onDismissRequest = { showProfileQr = false },
        containerColor   = MaterialTheme.colorScheme.surface,
        ) {
        ProfileQrSheet(
        name     = name,
        username = realUsername,
        link     = AdisLinks.user(realUsername),
        onShare  = { shareText(context, "Connect with $name on AdisChat: ${AdisLinks.user(realUsername)}") },
        )
        }
    }

    if (showChangePassword) {
        ModalBottomSheet(
        onDismissRequest = { if (!changePwBusy) showChangePassword = false },
        containerColor   = MaterialTheme.colorScheme.surface,
        ) {
        ChangePasswordSheet(
        busy = changePwBusy,
        onSubmit = { oldPw, newPw ->
        scope.launch {
        changePwBusy = true
        val result = runCatching {
        val fbUser = FirebaseAuth.getInstance().currentUser ?: error("not-signed-in")
        val mail = fbUser.email ?: error("no-email")
        fbUser.reauthenticate(EmailAuthProvider.getCredential(mail, oldPw)).await()
        fbUser.updatePassword(newPw).await()
        }
        changePwBusy = false
        result
        .onSuccess {
        showChangePassword = false
        snackbarHost.showSnackbar(tr("Password updated"))
        }
        .onFailure { e ->
        val msg = e.message.orEmpty()
        snackbarHost.showSnackbar(
        when {
        msg.contains("no-email") -> tr("This account uses Google sign-in and has no password")
        msg.contains("password is invalid", true) || msg.contains("wrong-password", true) ||
        msg.contains("credential is incorrect", true) || msg.contains("INVALID_LOGIN", true) ->
        tr("Current password is incorrect")
        msg.contains("requires-recent-login", true) -> tr("Please sign in again, then change your password")
        else -> tr("Couldn't update password. Please try again.")
        }
        )
        }
        }
        },
        )
        }
    }

    if (walletEnabled && showPinSheet) {
        ModalBottomSheet(
        onDismissRequest = { showPinSheet = false },
        modifier = Modifier.fillMaxHeight(),
        sheetState = fullSheetState,
        containerColor = MaterialTheme.colorScheme.surface
        ) {
        WalletPinSheet(
        mode = pinSheetMode,
        onDismiss = { showPinSheet = false },
        onSave = { oldPin, newPin ->
        if (pinSheetMode == WalletPinMode.CHANGE) {
        vm.changeWalletPin(oldPin = oldPin, newPin = newPin)
        } else {
        vm.activateWallet(username = username, phone = phone, pin = newPin)
        }
        showPinSheet = false
        }
        )
        }
    }

    if (showDevices) {
        ModalBottomSheet(
        onDismissRequest = { showDevices = false },
        modifier = Modifier.fillMaxHeight(),
        sheetState = fullSheetState,
        containerColor = MaterialTheme.colorScheme.surface
        ) {
        DeviceAccessSheet(
        onDismiss = { showDevices = false },
        onLinkDesktop = { showDevices = false; onGoScan() }
        )
        }
    }

    if (showLoginHistory) {
        ModalBottomSheet(
        onDismissRequest = { showLoginHistory = false },
        modifier = Modifier.fillMaxHeight(),
        sheetState = fullSheetState,
        containerColor = MaterialTheme.colorScheme.surface
        ) {
        LoginHistorySheet(
        onDismiss = { showLoginHistory = false }
        )
        }
    }

    if (showNotifications) {
        AlertDialog(
        onDismissRequest = { showNotifications = false },
        title = { Text(tr("Notifications")) },
        text = { NotificationSettingsContent(context) },
        confirmButton = {
        TextButton(onClick = { showNotifications = false }) {
        Text(tr("Done"), color = ProfileGreen)
        }
        }
        )
    }

    if (showStorageSettings) {
        AlertDialog(
        onDismissRequest = { showStorageSettings = false },
        title = { Text(tr("Storage & Data")) },
        text = { StorageSettingsContent(context) },
        confirmButton = {
        TextButton(onClick = { showStorageSettings = false }) {
        Text(tr("Done"), color = ProfileGreen)
        }
        }
        )
    }

    if (walletEnabled && showPay) {
        ModalBottomSheet(
        onDismissRequest = { showPay = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        PremiumPayQrSheet(
        state = walletState,
        fallbackUsername = username,
        onDismiss = { showPay = false },
        onRefresh = { vm.loadPayCode() },
        )
        }
    }

    if (walletEnabled && showDeposit) {
        ModalBottomSheet(
        onDismissRequest = { showDeposit = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        DepositSheet(
        onDismiss = { showDeposit = false },
        onDeposit = { amount ->
        vm.deposit(amount)
        showDeposit = false
        }
        )
        }
    }

    if (walletEnabled && showSend) {
        ModalBottomSheet(
        onDismissRequest = { showSend = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        SendMoneySheet(
        state = walletState,
        onDismiss = { showSend = false },
        onScan = {
        showSend = false
        onGoScan()
        },
        onSend = { recipient, amount, pin ->
        vm.sendMoney(recipient, amount, pin)
        showSend = false
        }
        )
        }
    }

    if (walletEnabled && showHistory) {
        ModalBottomSheet(
        onDismissRequest = { showHistory = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        TransactionHistorySheet(
        state = walletState,
        onDismiss = { showHistory = false },
        onOpenTransaction = {
        showHistory = false
        selectedTransaction = it
        }
        )
        }
    }

    selectedTransaction?.let { transaction ->
        ModalBottomSheet(
        onDismissRequest = {
        selectedTransaction = null
        vm.clearLastTransaction()
        },
        modifier = Modifier.fillMaxHeight(),
        sheetState = fullSheetState,
        containerColor = MaterialTheme.colorScheme.surface
        ) {
        TransactionDetailSheet(
        transaction = transaction,
        onDismiss = {
        selectedTransaction = null
        vm.clearLastTransaction()
        }
        )
        }
    }

    if (showEditProfile) {
        ModalBottomSheet(
        onDismissRequest = { showEditProfile = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        EditProfileSheet(
        user    = user,
        fatherName = fatherName,
        identityLocked = walletVerified,
        saving  = saving,
        onSave  = { n, f, p, e, u, d ->
        scope.launch { prefs.setFatherName(if (walletVerified) fatherName else f) }
        vm.saveProfile(n, u, p, e, d)
        showEditProfile = false
        },
        onPhotoSelected = { bytes -> vm.uploadProfilePhoto(bytes) },
        onDismiss = { showEditProfile = false }
        )
        }
    }

    if (showApplyBadge) {
        ModalBottomSheet(
        onDismissRequest = { showApplyBadge = false; vm.resetUploadState() },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        ApplyBadgeSheet(
        user      = user,
        vm      = vm,
        onDismiss = { showApplyBadge = false }
        )
        }
    }

    if (showAdminPanel) {
        ModalBottomSheet(
        onDismissRequest = { showAdminPanel = false },
        modifier      = Modifier.fillMaxHeight(),
        sheetState       = fullSheetState,
        containerColor   = MaterialTheme.colorScheme.surface
        ) {
        AdminVerificationPanel(vm = vm, onDismiss = { showAdminPanel = false })
        }
    }

    // - Dialogs -

    if (showSignOutDialog) {
        AlertDialog(
        onDismissRequest = { showSignOutDialog = false },
        title   = { Text(tr("Logout")) },
        text    = { Text(tr("Are you sure you want to log out of AdisChat?")) },
        confirmButton = {
        TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
        Text(tr("Logout"), color = ErrorRed)
        }
        },
        dismissButton = {
        TextButton(onClick = { showSignOutDialog = false }) { Text(tr("Cancel")) }
        }
        )
    }

    if (showLastSeenDialog) {
        val currentVisibility = user?.lastSeenVisibility ?: "public"
        AlertDialog(
        onDismissRequest = { showLastSeenDialog = false },
        title = { Text(tr("Last Seen Visibility")) },
        text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Choose who can see your online and last seen status."))
        AccountRow(
        icon = Icons.Default.Visibility,
        title = "Public",
        subtitle = "Everyone can see your online and last seen time",
        tint = if (currentVisibility != "private") ProfileGreen else ProfileMuted,
        onClick = {
        showLastSeenDialog = false
        vm.updateLastSeenVisibility("public")
        }
        )
        AccountRow(
        icon = Icons.Default.VisibilityOff,
        title = "Private",
        subtitle = "Hide online and last seen from other users",
        tint = if (currentVisibility == "private") ProfileGreen else ProfileMuted,
        onClick = {
        showLastSeenDialog = false
        vm.updateLastSeenVisibility("private")
        }
        )
        }
        },
        confirmButton = {},
        dismissButton = {
        TextButton(onClick = { showLastSeenDialog = false }) { Text(tr("Cancel")) }
        }
        )
    }

    activePrivacyControl?.let { control ->
        val currentUser = user
        if (control == PrivacyControl.BLOCKED_USERS) {
        AlertDialog(
        onDismissRequest = { activePrivacyControl = null },
        title = { Text(tr("Blocked Users")) },
        text = { Text(tr("Open a chat profile and use Block to add someone to your blocked list. A full blocked-users list is coming soon.")) },
        confirmButton = {
        TextButton(onClick = { activePrivacyControl = null }) { Text(tr("OK")) }
        }
        )
        } else {
        val choices = when (control) {
        PrivacyControl.ONLINE_STATUS -> listOf(
        Triple("same_as_last_seen", "Same as last seen", "Use your Last Seen Visibility choice"),
        Triple("public", "Everyone", "Show when you are online"),
        Triple("private", "Nobody", "Hide your online status"),
        )
        PrivacyControl.PROFILE_PHOTO -> listOf(
        Triple("everyone", "Everyone", "Anyone can see your profile photo"),
        Triple("contacts", "Contacts", "Only people you chat with can see it"),
        Triple("nobody", "Nobody", "Hide your profile photo"),
        )
        PrivacyControl.PHONE_NUMBER -> listOf(
        Triple("nobody", "Nobody", "Hide your phone number"),
        Triple("contacts", "Contacts", "Only people you chat with can see it"),
        Triple("everyone", "Everyone", "Anyone can see your phone number"),
        )
        PrivacyControl.MESSAGE_PERMISSIONS -> listOf(
        Triple("everyone", "Everyone", "Anyone can start a chat with you"),
        Triple("contacts", "Contacts", "Only people you chat with can message you"),
        Triple("nobody", "Nobody", "Pause new direct messages"),
        )
        PrivacyControl.CALL_PERMISSIONS -> listOf(
        Triple("everyone", "Everyone", "Anyone can call you"),
        Triple("contacts", "Contacts", "Only people you chat with can call you"),
        Triple("nobody", "Nobody", "Pause incoming calls"),
        )
        PrivacyControl.GROUP_INVITES -> listOf(
        Triple("everyone", "Everyone", "Anyone can add you to groups"),
        Triple("contacts", "Contacts", "Only people you chat with can add you"),
        Triple("nobody", "Nobody", "Nobody can add you to groups"),
        )
        PrivacyControl.READ_RECEIPTS -> listOf(
        Triple("enabled", "Enabled", "Send read receipts when you view messages"),
        Triple("disabled", "Disabled", "Do not send read receipts"),
        )
        PrivacyControl.BLOCKED_USERS -> emptyList()
        }
        val selected = when (control) {
        PrivacyControl.ONLINE_STATUS -> currentUser?.onlineStatusVisibility ?: "same_as_last_seen"
        PrivacyControl.PROFILE_PHOTO -> currentUser?.profilePhotoVisibility ?: "everyone"
        PrivacyControl.PHONE_NUMBER -> currentUser?.phoneNumberVisibility ?: "nobody"
        PrivacyControl.MESSAGE_PERMISSIONS -> currentUser?.whoCanMessageMe ?: "everyone"
        PrivacyControl.CALL_PERMISSIONS -> currentUser?.whoCanCallMe ?: "everyone"
        PrivacyControl.GROUP_INVITES -> currentUser?.whoCanAddMeToGroups ?: "everyone"
        PrivacyControl.READ_RECEIPTS -> if (currentUser?.readReceiptsEnabled != false) "enabled" else "disabled"
        PrivacyControl.BLOCKED_USERS -> ""
        }
        AlertDialog(
        onDismissRequest = { activePrivacyControl = null },
        title = { Text(tr(control.title)) },
        text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { (value, title, subtitle) ->
        AccountRow(
        icon = if (value == selected) Icons.Default.CheckCircle else Icons.Default.Visibility,
        title = title,
        subtitle = subtitle,
        tint = if (value == selected) ProfileGreen else ProfileMuted,
        onClick = {
        activePrivacyControl = null
        val body = when (control) {
        PrivacyControl.ONLINE_STATUS -> PrivacyUpdateBody(onlineStatusVisibility = value)
        PrivacyControl.PROFILE_PHOTO -> PrivacyUpdateBody(profilePhotoVisibility = value)
        PrivacyControl.PHONE_NUMBER -> PrivacyUpdateBody(phoneNumberVisibility = value)
        PrivacyControl.MESSAGE_PERMISSIONS -> PrivacyUpdateBody(whoCanMessageMe = value)
        PrivacyControl.CALL_PERMISSIONS -> PrivacyUpdateBody(whoCanCallMe = value)
        PrivacyControl.GROUP_INVITES -> PrivacyUpdateBody(whoCanAddMeToGroups = value)
        PrivacyControl.READ_RECEIPTS -> PrivacyUpdateBody(readReceiptsEnabled = value == "enabled")
        PrivacyControl.BLOCKED_USERS -> PrivacyUpdateBody()
        }
        vm.updatePrivacy(body, "${control.title} updated")
        }
        )
        }
        }
        },
        confirmButton = {},
        dismissButton = {
        TextButton(onClick = { activePrivacyControl = null }) { Text(tr("Cancel")) }
        }
        )
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
        onDismissRequest = { if (!saving) showDeleteAccountDialog = false },
        title = { Text(tr("Delete Account")) },
        text = {
        Text(tr("This will delete your AdisChat account, revoke linked devices, revoke push tokens, delete your Firebase sign-in account, and sign you out. Your existing chat history may remain for other participants."))
        },
        confirmButton = {
        TextButton(
        enabled = !saving,
        onClick = {
        showDeleteAccountDialog = false
        vm.deleteAccount(onDeleted = {
        clearLocalAppState(context)
        onSignOut()
        })
        }
        ) {
        Text(tr("Delete Account"), color = ErrorRed)
        }
        },
        dismissButton = {
        TextButton(enabled = !saving, onClick = { showDeleteAccountDialog = false }) {
        Text(tr("Cancel"))
        }
        }
        )
    }
}

// - Floating header -

@Composable
private fun FloatingProfileHeader(
    isScrolled: Boolean,
    user: AppUser?,
    onNotifications: () -> Unit,
    onDarkMode: () -> Unit,
    onLightMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isScrolled) 12.dp else 0.dp,
        animationSpec = tween(180),
        label = "profileHeaderElevation"
    )
    Surface(
        modifier = modifier
        .statusBarsPadding()
        .padding(horizontal = 18.dp, vertical = 6.dp)
        .fillMaxWidth()
        .height(76.dp)
        .shadow(
        elevation,
        RoundedCornerShape(22.dp),
        ambientColor = ProfileGreen.copy(alpha = 0.10f),
        spotColor = ProfileGreen.copy(alpha = 0.10f)
        )
        .border(1.dp, ProfileLine, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0B0D12).copy(alpha = if (isScrolled) 0.96f else 0.90f),
        tonalElevation = 0.dp
    ) {
        Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        Column(Modifier.weight(1f)) {
        Text(
        tr("Profile"),
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        color = ProfileText
        )
        Spacer(Modifier.height(3.dp))
        Text(
        user?.email?.takeIf { it.isNotBlank() } ?: tr("Account, wallet and security"),
        color = ProfileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1
        )
        }
        HeaderIcon(Icons.Default.Notifications, "Notifications", showBadge = true, onClick = onNotifications)
        }
    }
}

@Composable
private fun HeaderBadge(text: String, tint: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = bg.copy(alpha = 0.16f)) {
        Row(
        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        BlueVerifiedBadge(Modifier.size(15.dp), contentDescription = null)
        Spacer(Modifier.width(5.dp))
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlueVerifiedBadge(modifier: Modifier = Modifier, contentDescription: String? = "Verified") {
    AdisVerifiedBadge(modifier = modifier, contentDescription = contentDescription)
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, showBadge: Boolean = false, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
        Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .background(ProfileSurfaceSoft)
        .border(1.dp, ProfileLine, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
        ) {
        Icon(icon, contentDescription = label, tint = ProfileText.copy(alpha = 0.88f), modifier = Modifier.size(20.dp))
        }
        if (showBadge) {
        Box(
        Modifier
        .size(9.dp)
        .clip(CircleShape)
        .background(ProfileGreen)
        .border(1.dp, ProfileBlack, CircleShape)
        )
        }
    }
}
// - Personal info -

@Composable
private fun AccountCenterContent(
    name: String,
    handle: String,
    phone: String,
    email: String,
    fatherName: String,
    initial: String,
    photoUrl: String,
    verificationLevel: Int,
    state: WalletState,
    balance: Double,
    onEdit: () -> Unit,
    onMyQr: () -> Unit,
    onShare: () -> Unit,
    onWallet: () -> Unit,
    onOpenPage: (AccountCenterPage) -> Unit,
    onOpenShopRoute: (ShopRoute) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        AccountProfileHeader(
        name = name,
        handle = handle,
        phone = phone,
        email = email,
        fatherName = fatherName,
        initial = initial,
        photoUrl = photoUrl,
        verificationLevel = verificationLevel,
        onEdit = onEdit,
        onMyQr = onMyQr,
        onShare = onShare
        )
        Spacer(Modifier.height(18.dp))
        AccountHubTable(
        state = state,
        balance = balance,
        onWallet = onWallet,
        onOpenPage = onOpenPage
        )
        Spacer(Modifier.height(8.dp))
    }
}

private data class AccountCenterActions(
    val onWallet: () -> Unit,
    val onDeposit: () -> Unit,
    val onWithdraw: () -> Unit,
    val onPaymentMethods: () -> Unit,
    val onHistory: () -> Unit,
    val onChangePassword: () -> Unit,
    val onChangePin: () -> Unit,
    val onToggleFingerprint: (Boolean) -> Unit,
    val onTwoFactor: () -> Unit,
    val onDevices: () -> Unit,
    val onLoginHistory: () -> Unit,
    val onNotifications: () -> Unit,
    val onStorageSettings: () -> Unit,
    val onDarkMode: () -> Unit,
    val onLightMode: () -> Unit,
    val onSystemTheme: () -> Unit,
    val onLanguage: () -> Unit,
    val onApplyBadge: () -> Unit,
    val onAdminPanel: () -> Unit,
    val onSupport: (String) -> Unit,
    val onLogout: () -> Unit,
    val onLastSeenVisibility: () -> Unit,
    val onPrivacyControl: (PrivacyControl) -> Unit,
    val onDeleteAccount: () -> Unit,
)

private fun lastSeenVisibilityLabel(value: String?): String =
    if (value == "private") "Private" else "Public"

private fun privacyLabel(value: String?): String = when (value) {
    "same_as_last_seen" -> "Same as last seen"
    "public", "everyone" -> "Everyone"
    "private", "nobody" -> "Nobody"
    "contacts" -> "Contacts"
    else -> "Everyone"
}

@Composable
private fun AccountHubTable(
    state: WalletState,
    balance: Double,
    onWallet: () -> Unit,
    onOpenPage: (AccountCenterPage) -> Unit,
) {
    val menu = listOfNotNull(
        // Wallet hidden while the wallet feature is paused (FeatureFlags.WALLET_ENABLED).
        if (com.adischat.app.FeatureFlags.WALLET_ENABLED)
        HubItem("Wallet", "ETB ${"%,.2f".format(balance)}", Icons.Default.AccountBalanceWallet, ProfileGreen, onWallet)
        else null,
        HubItem(AccountCenterPage.SECURITY.title, AccountCenterPage.SECURITY.subtitle, AccountCenterPage.SECURITY.icon, Amber) { onOpenPage(AccountCenterPage.SECURITY) },
        HubItem(AccountCenterPage.PRIVACY.title, AccountCenterPage.PRIVACY.subtitle, AccountCenterPage.PRIVACY.icon, Cyan) { onOpenPage(AccountCenterPage.PRIVACY) },
        HubItem(AccountCenterPage.DEVICES.title, currentDeviceName(), AccountCenterPage.DEVICES.icon, Violet) { onOpenPage(AccountCenterPage.DEVICES) },
        HubItem(AccountCenterPage.NOTIFICATIONS.title, AccountCenterPage.NOTIFICATIONS.subtitle, AccountCenterPage.NOTIFICATIONS.icon, ProfileGreen) { onOpenPage(AccountCenterPage.NOTIFICATIONS) },
        HubItem(AccountCenterPage.STORAGE.title, AccountCenterPage.STORAGE.subtitle, AccountCenterPage.STORAGE.icon, Amber) { onOpenPage(AccountCenterPage.STORAGE) },
        HubItem(AccountCenterPage.VERIFICATION.title, AccountCenterPage.VERIFICATION.subtitle, AccountCenterPage.VERIFICATION.icon, ProfileBlue) { onOpenPage(AccountCenterPage.VERIFICATION) },
        HubItem(AccountCenterPage.SUPPORT.title, AccountCenterPage.SUPPORT.subtitle, AccountCenterPage.SUPPORT.icon, Violet) { onOpenPage(AccountCenterPage.SUPPORT) },
        HubItem(AccountCenterPage.ACCOUNT.title, AccountCenterPage.ACCOUNT.subtitle, AccountCenterPage.ACCOUNT.icon, ProfileDanger) { onOpenPage(AccountCenterPage.ACCOUNT) },
    )

    Column(Modifier.padding(horizontal = 18.dp)) {
        Text("Account Center", color = ProfileText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text("Manage your AdisChat account from one clean hub", color = ProfileMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
        .fillMaxWidth()
        .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = ProfileGreen.copy(alpha = 0.08f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
        ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        menu.forEach { item -> HubTableRow(item) }
        }
        }
    }
}

private data class HubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun HubTableRow(item: HubItem) {
    Row(
        Modifier
        .fillMaxWidth()
        .height(66.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = item.onClick)
        .background(Color.White.copy(alpha = 0.025f))
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(item.icon, item.tint.copy(alpha = 0.12f), item.tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(item.title, color = ProfileText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(item.subtitle, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open ${item.title}", tint = ProfileMuted.copy(alpha = 0.72f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccountCategoryPage(
    page: AccountCenterPage,
    user: AppUser?,
    verificationStatus: VerificationStatus?,
    state: WalletState,
    balance: Double,
    fingerprintEnabled: Boolean,
    walletEnabled: Boolean,
    actions: AccountCenterActions,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icons.Default.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(page.title, color = ProfileText, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(page.subtitle, color = ProfileMuted, fontSize = 12.sp)
        }
        }
        Spacer(Modifier.height(16.dp))
        when (page) {
        AccountCenterPage.SECURITY -> AccountPageCard {
        AccountRow(Icons.Default.Lock, "Change Password", "Update provider password", onClick = actions.onChangePassword)
        if (walletEnabled) {
        AccountRow(Icons.Default.Security, "Change PIN", if (state.walletPinSet) "Update your wallet PIN" else "Set wallet PIN", onClick = actions.onChangePin)
        }
        AccountToggleRow(Icons.Default.Fingerprint, "Fingerprint Login", "Use biometrics to open protected flows", fingerprintEnabled, actions.onToggleFingerprint)
        AccountRow(Icons.Default.Shield, "Two-Factor Authentication (2FA)", "Add another account protection layer", onClick = actions.onTwoFactor)
        AccountRow(Icons.Default.Devices, "Active Sessions", currentDeviceName(), onClick = actions.onDevices)
        AccountRow(Icons.Default.History, "Login History", "Last active ${formatTime(FirebaseAuth.getInstance().currentUser?.metadata?.lastSignInTimestamp ?: 0L)}", onClick = actions.onLoginHistory)
        }
        AccountCenterPage.PRIVACY -> AccountPageCard {
        AccountRow(Icons.Default.Visibility, "Last Seen Visibility", lastSeenVisibilityLabel(user?.lastSeenVisibility), onClick = actions.onLastSeenVisibility)
        AccountRow(Icons.Default.AccountCircle, "Online Status", privacyLabel(user?.onlineStatusVisibility ?: "same_as_last_seen"), onClick = { actions.onPrivacyControl(PrivacyControl.ONLINE_STATUS) })
        AccountRow(Icons.Default.Person, "Profile Photo Visibility", privacyLabel(user?.profilePhotoVisibility ?: "everyone"), onClick = { actions.onPrivacyControl(PrivacyControl.PROFILE_PHOTO) })
        AccountRow(Icons.Default.Phone, "Phone Number Visibility", privacyLabel(user?.phoneNumberVisibility ?: "nobody"), onClick = { actions.onPrivacyControl(PrivacyControl.PHONE_NUMBER) })
        AccountRow(Icons.Default.Message, "Read Receipts", if (user?.readReceiptsEnabled != false) "Enabled" else "Disabled", onClick = { actions.onPrivacyControl(PrivacyControl.READ_RECEIPTS) })
        AccountRow(Icons.Default.Shield, "Blocked Users", "Manage blocked accounts", onClick = { actions.onPrivacyControl(PrivacyControl.BLOCKED_USERS) })
        AccountRow(Icons.Default.Send, "Who Can Message Me", privacyLabel(user?.whoCanMessageMe ?: "everyone"), onClick = { actions.onPrivacyControl(PrivacyControl.MESSAGE_PERMISSIONS) })
        AccountRow(Icons.Default.Phone, "Who Can Call Me", privacyLabel(user?.whoCanCallMe ?: "everyone"), onClick = { actions.onPrivacyControl(PrivacyControl.CALL_PERMISSIONS) })
        AccountRow(Icons.Default.Groups, "Who Can Add Me to Groups", privacyLabel(user?.whoCanAddMeToGroups ?: "everyone"), onClick = { actions.onPrivacyControl(PrivacyControl.GROUP_INVITES) })
        }
        AccountCenterPage.DEVICES -> AccountPageCard {
        DeviceSummaryCard()
        Spacer(Modifier.height(8.dp))
        AccountRow(Icons.Default.PhoneAndroid, "Manage Devices", "This Device, other devices, logout others", onClick = actions.onDevices)
        }
        AccountCenterPage.NOTIFICATIONS -> AccountPageCard {
        AccountRow(Icons.Default.Message, "Messages", "Push and in-app alerts", onClick = actions.onNotifications)
        AccountRow(Icons.Default.Groups, "Groups", "Mentions and replies", onClick = actions.onNotifications)
        AccountRow(Icons.Default.Notifications, "Channels", "Channel updates", onClick = actions.onNotifications)
        if (walletEnabled) AccountRow(Icons.Default.AccountBalanceWallet, "Wallet Alerts", "Deposits, withdrawals, payments", onClick = actions.onNotifications)
        AccountRow(Icons.Default.VolumeUp, "Sound & Vibration", "Default alert style", onClick = actions.onNotifications)
        AccountRow(Icons.Default.DoNotDisturb, "Do Not Disturb", "Quiet hours", onClick = actions.onNotifications)
        }
        AccountCenterPage.APPEARANCE -> AccountPageCard {
        AccountRow(Icons.Default.DarkMode, "Dark Mode", "Keep the AdisChat dark theme", onClick = actions.onDarkMode)
        AccountRow(Icons.Default.LightMode, "Light Mode", "Switch to light surfaces", onClick = actions.onLightMode)
        AccountRow(Icons.Default.Palette, "System Theme", "Follow Android settings", onClick = actions.onSystemTheme)
        AccountRow(Icons.Default.TextFields, "Font Size", "Default")
        AccountRow(Icons.Default.Translate, "App Language", "English / Amharic", onClick = actions.onLanguage)
        }
        AccountCenterPage.STORAGE -> AccountPageCard {
        AccountRow(Icons.Default.Storage, "Storage Usage", "Review app storage", onClick = actions.onStorageSettings)
        AccountRow(Icons.Default.DeleteSweep, "Media Cache", "Photos, videos, and previews", onClick = actions.onStorageSettings)
        AccountRow(Icons.Default.ArrowDownward, "Auto Download", "Media download rules", onClick = actions.onStorageSettings)
        AccountRow(Icons.Default.Settings, "Data Saver", "Reduce network usage", onClick = actions.onStorageSettings)
        AccountRow(Icons.Default.DeleteSweep, "Clear Cache", "Free local media cache", onClick = actions.onStorageSettings)
        }
        AccountCenterPage.VERIFICATION -> {
        val level = verificationStatus?.verificationLevel ?: user?.verificationLevel ?: 0
        val emailVerified = FirebaseAuth.getInstance().currentUser?.isEmailVerified == true || user?.email?.isNotBlank() == true
        val phoneVerified = user?.phone?.isNotBlank() == true
        AccountPageCard {
        AccountStatusRow(Icons.Default.Email, "Email Verification", if (emailVerified) "Verified" else "Needs verification", emailVerified)
        AccountStatusRow(Icons.Default.Phone, "Phone Verification", if (phoneVerified) "Verified" else "Add phone number", phoneVerified)
        AccountStatusRow(Icons.Default.Person, "Profile Completion", if (level >= 1) "Complete" else "Incomplete", level >= 1)
        AccountStatusRow(Icons.Default.Verified, "Blue Badge Status", if (level >= 3) "Verified" else user?.verificationRequestStatus?.replaceFirstChar { it.uppercase() } ?: "Not applied", level >= 3)
        AccountRow(Icons.Default.Badge, "Identity Verification (Future)", "Government ID and selfie review", onClick = actions.onApplyBadge)
        if (user?.role == "admin") {
        AccountRow(Icons.Default.Security, "Verification Admin", "Manage verification requests", onClick = actions.onAdminPanel)
        }
        }
        }
        AccountCenterPage.SUPPORT -> AccountPageCard {
        AccountRow(Icons.Default.Help, "Help Center", "Guides and account help") { actions.onSupport("Help Center") }
        AccountRow(Icons.Default.Email, "Contact Us", "Reach AdisChat support") { actions.onSupport("Contact Us") }
        AccountRow(Icons.Default.Policy, "Privacy Policy", "How data is protected") { actions.onSupport("Privacy Policy") }
        AccountRow(Icons.Default.Gavel, "Terms of Service", "App rules and terms") { actions.onSupport("Terms of Service") }
        AccountRow(Icons.Default.Groups, "Community Guidelines", "Rules for chats, groups, and channels") { actions.onSupport("Community Guidelines") }
        AccountRow(Icons.Default.BugReport, "Report Bug", "Send a technical issue") { actions.onSupport("Report Bug") }
        AccountRow(Icons.Default.Feedback, "Send Feedback", "Share product ideas") { actions.onSupport("Send Feedback") }
        }
        AccountCenterPage.ACCOUNT -> AccountPageCard {
        AccountRow(Icons.Default.Logout, "Logout", "Sign out from this device", ProfileDanger, actions.onLogout)
        AccountRow(Icons.Default.DeleteForever, "Delete Account", "Permanently remove your account", ProfileDanger, actions.onDeleteAccount)
        }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountPageCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
        .fillMaxWidth()
        .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = ProfileGreen.copy(alpha = 0.08f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun NotificationSettingsContent(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NotificationToggleRow(context, AdisNotificationManager.KEY_MESSAGES, "Message notifications", "Direct message alerts")
        NotificationToggleRow(context, AdisNotificationManager.KEY_GROUPS, "Group notifications", "Mentions and group messages")
        NotificationToggleRow(context, AdisNotificationManager.KEY_CHANNELS, "Channel notifications", "Channel posts and updates")
        NotificationToggleRow(context, AdisNotificationManager.KEY_CALLS, "Call notifications", "Incoming and missed calls")
        NotificationToggleRow(context, AdisNotificationManager.KEY_SOUND, "Sound", "Play notification sound")
        NotificationToggleRow(context, AdisNotificationManager.KEY_VIBRATION, "Vibration", "Vibrate for alerts")
        NotificationToggleRow(context, AdisNotificationManager.KEY_PREVIEW, "Preview", "Show message text in notifications")
    }
}

@Composable
private fun NotificationToggleRow(context: Context, key: String, title: String, subtitle: String) {
    var enabled by remember(key) { mutableStateOf(AdisNotificationManager.isEnabled(context, key, true)) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
        checked = enabled,
        onCheckedChange = {
        enabled = it
        AdisNotificationManager.setEnabled(context, key, it)
        },
        )
    }
}

@Composable
private fun StorageSettingsContent(context: Context) {
    val storagePrefs = remember { context.getSharedPreferences("adischat_storage_settings", Context.MODE_PRIVATE) }
    var cacheVersion by remember { mutableStateOf(0) }
    val cacheSize by remember(cacheVersion) { mutableStateOf(formatBytes(appCacheSize(context))) }
    var autoPhotos by remember { mutableStateOf(storagePrefs.getBoolean("auto_photos", true)) }
    var autoVideos by remember { mutableStateOf(storagePrefs.getBoolean("auto_videos", false)) }
    var autoVoice by remember { mutableStateOf(storagePrefs.getBoolean("auto_voice", true)) }
    var autoFiles by remember { mutableStateOf(storagePrefs.getBoolean("auto_files", false)) }
    var wifiOnly by remember { mutableStateOf(storagePrefs.getBoolean("wifi_only_downloads", true)) }
    var dataSaver by remember { mutableStateOf(storagePrefs.getBoolean("mobile_data_saver", false)) }

    fun set(key: String, value: Boolean) {
        storagePrefs.edit().putBoolean(key, value).apply()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Local cache: $cacheSize", fontWeight = FontWeight.Bold)
        Text("Manage downloaded media by clearing local cache. Server messages and media remain available.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StorageToggleRow("Auto-download photos", "Photos download automatically", autoPhotos) { autoPhotos = it; set("auto_photos", it) }
        StorageToggleRow("Auto-download videos", "Videos wait unless enabled", autoVideos) { autoVideos = it; set("auto_videos", it) }
        StorageToggleRow("Auto-download voice", "Voice notes download automatically", autoVoice) { autoVoice = it; set("auto_voice", it) }
        StorageToggleRow("Auto-download files", "Documents wait unless enabled", autoFiles) { autoFiles = it; set("auto_files", it) }
        StorageToggleRow("Wi-Fi only downloads", "Use Wi-Fi for larger media", wifiOnly) { wifiOnly = it; set("wifi_only_downloads", it) }
        StorageToggleRow("Mobile data saver", "Reduce media downloads on mobile data", dataSaver) { dataSaver = it; set("mobile_data_saver", it) }
        OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
        clearDirectory(context.cacheDir)
        context.externalCacheDir?.let(::clearDirectory)
        cacheVersion += 1
        },
        ) {
        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Clear cache")
        }
        Text("Manage storage by chat, group, or channel uses the same clear-cache control until per-thread media accounting is available.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StorageToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun appCacheSize(context: Context): Long =
    directorySize(context.cacheDir) + (context.externalCacheDir?.let(::directorySize) ?: 0L)

private fun directorySize(file: java.io.File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
}

private fun clearDirectory(file: java.io.File?) {
    if (file == null || !file.exists()) return
    file.listFiles()?.forEach { child ->
        if (child.isDirectory) clearDirectory(child)
        child.delete()
    }
}

private fun clearLocalAppState(context: Context) {
    clearDirectory(context.cacheDir)
    context.externalCacheDir?.let(::clearDirectory)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
}

@Composable
private fun AccountCenterContent(
    user: AppUser?,
    name: String,
    handle: String,
    phone: String,
    email: String,
    fatherName: String,
    initial: String,
    photoUrl: String,
    verificationLevel: Int,
    verificationStatus: VerificationStatus?,
    state: WalletState,
    balance: Double,
    fingerprintEnabled: Boolean,
    onEdit: () -> Unit,
    onMyQr: () -> Unit,
    onShare: () -> Unit,
    onWallet: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onPaymentMethods: () -> Unit,
    onHistory: () -> Unit,
    onChangePassword: () -> Unit,
    onChangePin: () -> Unit,
    onToggleFingerprint: (Boolean) -> Unit,
    onTwoFactor: () -> Unit,
    onDevices: () -> Unit,
    onLoginHistory: () -> Unit,
    onNotifications: () -> Unit,
    onDarkMode: () -> Unit,
    onLightMode: () -> Unit,
    onSystemTheme: () -> Unit,
    onLanguage: () -> Unit,
    onApplyBadge: () -> Unit,
    onAdminPanel: () -> Unit,
    onSupport: (String) -> Unit,
    onLogout: () -> Unit,
    onLastSeenVisibility: () -> Unit = {},
    onDeleteAccount: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        AccountProfileHeader(
        name = name,
        handle = handle,
        phone = phone,
        email = email,
        fatherName = fatherName,
        initial = initial,
        photoUrl = photoUrl,
        verificationLevel = verificationLevel,
        onEdit = onEdit,
        onMyQr = onMyQr,
        onShare = onShare
        )
        Spacer(Modifier.height(14.dp))
        QuickActionsGrid(
        actions = listOfNotNull(
        if (FeatureFlags.FEATURE_WALLET_ENABLED) AccountQuickAction("Wallet", "Balance and money tools", Icons.Default.AccountBalanceWallet, ProfileGreen, onWallet) else null,
        AccountQuickAction("Privacy", "Visibility controls", Icons.Default.VisibilityOff, Cyan, {}),
        AccountQuickAction("Security", "PIN, sessions, 2FA", Icons.Default.Security, Amber, {}),
        AccountQuickAction("Devices", currentDeviceName(), Icons.Default.Devices, Violet, onDevices),
        )
        )
        Spacer(Modifier.height(14.dp))
        if (FeatureFlags.FEATURE_WALLET_ENABLED) AccountWalletCard(state, balance, onWallet, onHistory, onDeposit, onWithdraw, onPaymentMethods)
        AccountSettingsSection("Security", "Protect sign-in, wallet, and sessions") {
        AccountRow(Icons.Default.Lock, "Change Password", "Update provider password", onClick = onChangePassword)
        ModuleDivider()
        if (FeatureFlags.FEATURE_WALLET_ENABLED) AccountRow(Icons.Default.Security, "Change PIN", if (state.walletPinSet) "Update your wallet PIN" else "Set wallet PIN", onClick = onChangePin)
        ModuleDivider()
        AccountToggleRow(Icons.Default.Fingerprint, "Fingerprint Login", "Use biometrics to open protected flows", fingerprintEnabled, onToggleFingerprint)
        ModuleDivider()
        AccountRow(Icons.Default.Shield, "Two-Factor Authentication (2FA)", "Add another account protection layer", onClick = onTwoFactor)
        ModuleDivider()
        AccountRow(Icons.Default.Devices, "Active Sessions", currentDeviceName(), onClick = onDevices)
        ModuleDivider()
        AccountRow(Icons.Default.History, "Login History", "Last active ${formatTime(FirebaseAuth.getInstance().currentUser?.metadata?.lastSignInTimestamp ?: 0L)}", onClick = onLoginHistory)
        }
        AccountSettingsSection("Privacy", "Control who can see and reach you") {
        AccountRow(Icons.Default.Visibility, "Last Seen Visibility", lastSeenVisibilityLabel(user?.lastSeenVisibility), onClick = onLastSeenVisibility)
        ModuleDivider()
        AccountRow(Icons.Default.AccountCircle, "Online Status", "Same as last seen")
        ModuleDivider()
        AccountRow(Icons.Default.Person, "Profile Photo Visibility", "Everyone")
        ModuleDivider()
        AccountRow(Icons.Default.Phone, "Phone Number Visibility", "Nobody")
        ModuleDivider()
        AccountRow(Icons.Default.Message, "Read Receipts", "Enabled")
        ModuleDivider()
        AccountRow(Icons.Default.Shield, "Blocked Users", "Manage blocked accounts")
        ModuleDivider()
        AccountRow(Icons.Default.Send, "Who Can Message Me", "Everyone")
        }
        AccountSettingsSection("Devices", "This device and logged-in access") {
        DeviceSummaryCard()
        Spacer(Modifier.height(8.dp))
        AccountRow(Icons.Default.PhoneAndroid, "Manage Devices", "This Device, other devices, logout others", onClick = onDevices)
        }
        AccountSettingsSection("Notifications", "Choose what gets your attention") {
        AccountRow(Icons.Default.Message, "Messages", "Push and in-app alerts", onClick = onNotifications)
        ModuleDivider()
        AccountRow(Icons.Default.Groups, "Groups", "Mentions and replies", onClick = onNotifications)
        ModuleDivider()
        AccountRow(Icons.Default.Notifications, "Channels", "Channel updates", onClick = onNotifications)
        ModuleDivider()
        if (FeatureFlags.FEATURE_WALLET_ENABLED) AccountRow(Icons.Default.AccountBalanceWallet, "Wallet Alerts", "Deposits, withdrawals, payments", onClick = onNotifications)
        ModuleDivider()
        AccountRow(Icons.Default.VolumeUp, "Sound & Vibration", "Default alert style", onClick = onNotifications)
        ModuleDivider()
        AccountRow(Icons.Default.DoNotDisturb, "Do Not Disturb", "Quiet hours", onClick = onNotifications)
        }
        AccountSettingsSection("Appearance", "Theme, language, and reading comfort") {
        AccountRow(Icons.Default.DarkMode, "Dark Mode", "Keep the AdisChat dark theme", onClick = onDarkMode)
        ModuleDivider()
        AccountRow(Icons.Default.LightMode, "Light Mode", "Switch to light surfaces", onClick = onLightMode)
        ModuleDivider()
        AccountRow(Icons.Default.Palette, "System Theme", "Follow Android settings", onClick = onSystemTheme)
        ModuleDivider()
        AccountRow(Icons.Default.TextFields, "Font Size", "Default")
        ModuleDivider()
        AccountRow(Icons.Default.Translate, "App Language", "English / Amharic", onClick = onLanguage)
        }
        AccountSettingsSection("Storage & Data", "Media and network usage") {
        AccountRow(Icons.Default.Storage, "Storage Usage", "Review app storage")
        ModuleDivider()
        AccountRow(Icons.Default.DeleteSweep, "Media Cache", "Photos, videos, and previews")
        ModuleDivider()
        AccountRow(Icons.Default.ArrowDownward, "Auto Download", "Media download rules")
        ModuleDivider()
        AccountRow(Icons.Default.Settings, "Data Saver", "Reduce network usage")
        ModuleDivider()
        AccountRow(Icons.Default.DeleteSweep, "Clear Cache", "Free local media cache")
        }
        VerificationCenterSection(
        user = user,
        status = verificationStatus,
        onApplyBadge = onApplyBadge,
        onAdminPanel = onAdminPanel
        )
        AccountSettingsSection("Support", "Help, legal, and feedback") {
        AccountRow(Icons.Default.Help, "Help Center", "Guides and account help") { onSupport("Help Center") }
        ModuleDivider()
        AccountRow(Icons.Default.Email, "Contact Us", "Reach AdisChat support") { onSupport("Contact Us") }
        ModuleDivider()
        AccountRow(Icons.Default.Policy, "Privacy Policy", "How data is protected") { onSupport("Privacy Policy") }
        ModuleDivider()
        AccountRow(Icons.Default.Gavel, "Terms of Service", "App rules and terms") { onSupport("Terms of Service") }
        ModuleDivider()
        AccountRow(Icons.Default.BugReport, "Report Bug", "Send a technical issue") { onSupport("Report Bug") }
        ModuleDivider()
        AccountRow(Icons.Default.Feedback, "Send Feedback", "Share product ideas") { onSupport("Send Feedback") }
        }
        AccountSettingsSection("Account", "Session and account ownership") {
        AccountRow(Icons.Default.Logout, "Logout", "Sign out from this device", ProfileDanger, onLogout)
        ModuleDivider()
        AccountRow(Icons.Default.DeleteForever, "Delete Account", "Permanently remove your account", ProfileDanger, onDeleteAccount)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class AccountQuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun AccountProfileHeader(
    name: String,
    handle: String,
    phone: String,
    email: String,
    fatherName: String,
    initial: String,
    photoUrl: String,
    verificationLevel: Int,
    onEdit: () -> Unit,
    onMyQr: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Surface(
        modifier = Modifier
        .fillMaxWidth()
        .shadow(22.dp, RoundedCornerShape(28.dp), ambientColor = ProfileGreen.copy(alpha = 0.08f), spotColor = ProfileGreen.copy(alpha = 0.18f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
        ) {
        Column(
        Modifier
        .background(
        Brush.linearGradient(
        listOf(
        Color.White.copy(alpha = 0.06f),
        Color(0xFF13241B).copy(alpha = 0.92f),
        Color(0xFF0B0D12)
        )
        )
        )
        .padding(18.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(initial = initial, photoUrl = photoUrl, size = 80.dp, verificationLevel = verificationLevel)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = ProfileText, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
        Spacer(Modifier.width(7.dp))
        BlueVerifiedBadge(Modifier.size(18.dp), contentDescription = "Blue badge status")
        }
        Text(handle, color = ProfileGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(phone, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
        Text(email, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
        }
        }
        if (fatherName.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        CompactInfoPill(fatherName)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HeaderActionButton("Edit Profile", Icons.Default.Edit, onEdit, Modifier.weight(1f))
        HeaderActionButton("My QR Code", Icons.Default.QrCode, onMyQr, Modifier.weight(1f))
        HeaderActionButton("Share Profile", Icons.Default.Send, onShare, Modifier.weight(1f))
        }
        }
        }
    }
}

@Composable
private fun HeaderActionButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
        .height(48.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .background(ProfileGreen.copy(alpha = 0.13f))
        .border(1.dp, ProfileGreen.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = ProfileGreen, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = ProfileText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun QuickActionsGrid(actions: List<AccountQuickAction>) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text("Quick Actions", color = ProfileText, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        actions.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { action ->
        QuickActionCard(action = action, modifier = Modifier.weight(1f))
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuickActionCard(action: AccountQuickAction, modifier: Modifier = Modifier) {
    val elevation by animateDpAsState(targetValue = 2.dp, animationSpec = tween(220), label = "quickActionElevation")
    Row(
        modifier
        .height(86.dp)
        .shadow(elevation, RoundedCornerShape(22.dp), ambientColor = action.tint.copy(alpha = 0.14f), spotColor = action.tint.copy(alpha = 0.22f))
        .clip(RoundedCornerShape(22.dp))
        .clickable(onClick = action.onClick)
        .background(Color.White.copy(alpha = 0.055f))
        .border(1.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(22.dp))
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(action.icon, action.tint.copy(alpha = 0.13f), action.tint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
        Text(action.title, color = ProfileText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(action.subtitle, color = ProfileMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
        }
    }
}

@Composable
private fun AccountWalletCard(
    state: WalletState,
    balance: Double,
    onWallet: () -> Unit,
    onHistory: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onPaymentMethods: () -> Unit,
) {
    AccountSettingsSection("Wallet", "Money center and payment tools") {
        Row(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onWallet)
        .background(ProfileWalletBrush)
        .border(1.dp, ProfileGreen.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
        .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        Column(Modifier.weight(1f)) {
        Text("Balance", color = ProfileMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("ETB ${"%,.2f".format(balance)}", color = ProfileText, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(if (state.active) "Wallet active" else "Activate wallet to start using money tools", color = ProfileGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open wallet", tint = ProfileGreen)
        }
        Spacer(Modifier.height(8.dp))
        AccountRow(Icons.Default.History, "Transaction History", "Deposits, withdrawals, payments", onClick = onHistory)
        ModuleDivider()
        AccountRow(Icons.Default.ArrowDownward, "Deposit", "Add money to wallet", onClick = onDeposit)
        ModuleDivider()
        AccountRow(Icons.Default.ArrowUpward, "Withdraw", "Move money out", onClick = onWithdraw)
        ModuleDivider()
        AccountRow(Icons.Default.CreditCard, "Payment Methods", "Cards, banks, and wallet rails", onClick = onPaymentMethods)
    }
}

@Composable
private fun VerificationCenterSection(
    user: AppUser?,
    status: VerificationStatus?,
    onApplyBadge: () -> Unit,
    onAdminPanel: () -> Unit,
) {
    val level = status?.verificationLevel ?: user?.verificationLevel ?: 0
    val emailVerified = FirebaseAuth.getInstance().currentUser?.isEmailVerified == true || user?.email?.isNotBlank() == true
    val phoneVerified = user?.phone?.isNotBlank() == true
    AccountSettingsSection("Verification", "Trust score and identity status") {
        AccountStatusRow(Icons.Default.Email, "Email Verification", if (emailVerified) "Verified" else "Needs verification", emailVerified)
        ModuleDivider()
        AccountStatusRow(Icons.Default.Phone, "Phone Verification", if (phoneVerified) "Verified" else "Add phone number", phoneVerified)
        ModuleDivider()
        AccountStatusRow(Icons.Default.Person, "Profile Completion", if (level >= 1) "Complete" else "Incomplete", level >= 1)
        ModuleDivider()
        AccountStatusRow(Icons.Default.Verified, "Blue Badge Status", if (level >= 3) "Verified" else user?.verificationRequestStatus?.replaceFirstChar { it.uppercase() } ?: "Not applied", level >= 3)
        ModuleDivider()
        AccountRow(Icons.Default.Badge, "Identity Verification (Future)", "Government ID and selfie review", onClick = onApplyBadge)
        if (user?.role == "admin") {
        ModuleDivider()
        AccountRow(Icons.Default.Security, "Verification Admin", "Manage verification requests", onClick = onAdminPanel)
        }
    }
}

@Composable
private fun DeviceSummaryCard() {
    val authUser = FirebaseAuth.getInstance().currentUser
    Column(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(ProfileSurfaceSoft.copy(alpha = 0.82f))
        .border(1.dp, ProfileLine, RoundedCornerShape(18.dp))
        .padding(14.dp)
    ) {
        SecurityInfoRow("Current Device", currentDeviceName())
        ModuleDivider()
        SecurityInfoRow("Android Version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        ModuleDivider()
        SecurityInfoRow("Last Active", formatTime(authUser?.metadata?.lastSignInTimestamp ?: System.currentTimeMillis()))
    }
}

@Composable
private fun AccountSettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = ProfileText)
        Text(subtitle, fontSize = 12.sp, color = ProfileMuted, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
        .fillMaxWidth()
        .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = ProfileGreen.copy(alpha = 0.08f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
        ) {
        Column(Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = ProfileGreen,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier
        .fillMaxWidth()
        .height(64.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint.copy(alpha = 0.12f), tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, color = ProfileText, fontSize = 14.sp, maxLines = 1)
        Text(subtitle, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open $title", tint = ProfileMuted.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccountStatusRow(icon: ImageVector, title: String, status: String, positive: Boolean) {
    Row(
        Modifier
        .fillMaxWidth()
        .height(64.dp)
        .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (positive) ProfileGreen else Amber
        IconTile(icon, tint.copy(alpha = 0.12f), tint)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Bold, color = ProfileText, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
        StatusPill(status, tint)
    }
}

@Composable
private fun AccountToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
        .fillMaxWidth()
        .height(64.dp)
        .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, ProfileGreen.copy(alpha = 0.12f), ProfileGreen)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, color = ProfileText, fontSize = 14.sp, maxLines = 1)
        Text(subtitle, color = ProfileMuted, fontSize = 12.sp, maxLines = 1)
        }
        Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
        checkedThumbColor = ProfileOnAccent,
        checkedTrackColor = ProfileGreen,
        uncheckedThumbColor = ProfileMuted,
        uncheckedTrackColor = ProfileSurfaceSoft,
        uncheckedBorderColor = ProfileLine
        )
        )
    }
}

@Composable
private fun PersonalInfoCard(
    name: String, handle: String, phone: String, email: String,
    fatherName: String, initial: String, photoUrl: String,
    verificationLevel: Int, onEdit: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Surface(
        modifier = Modifier
        .fillMaxWidth()
        .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = ProfileGreen.copy(alpha = 0.08f), spotColor = ProfileGreen.copy(alpha = 0.16f))
        .border(1.dp, ProfileLine, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
        ) {
        Row(
        Modifier.background(ProfileCardBrush).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        ProfileAvatar(initial = initial, photoUrl = photoUrl, size = 70.dp, verificationLevel = verificationLevel)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
        name,
        color = ProfileText,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        maxLines = 1
        )
        if (verificationLevel >= 1) {
        Spacer(Modifier.width(7.dp))
        BlueVerifiedBadge(Modifier.size(17.dp))
        }
        }
        Spacer(Modifier.height(3.dp))
        Text(
        email.ifBlank { handle },
        color = ProfileMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CompactInfoPill(handle)
        if (phone.isNotBlank()) CompactInfoPill(phone)
        }
        }
        Spacer(Modifier.width(10.dp))
        Box(
        Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onEdit)
        .background(ProfileGreen.copy(alpha = 0.14f))
        .border(1.dp, ProfileLine, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
        ) {
        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = ProfileGreen, modifier = Modifier.size(19.dp))
        }
        }
        }
        if (fatherName.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
        fatherName,
        color = ProfileMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
        )
        }
    }
}

@Composable
private fun CompactInfoPill(text: String) {
    if (text.isBlank()) return
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isProfileLight()) Color(0xFFF0F7F3) else Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, ProfileLine)
    ) {
        Text(
        text,
        color = ProfileMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileAvatar(initial: String, photoUrl: String, size: Dp, verificationLevel: Int = 0) {
    Box(
        Modifier
        .size(size)
        .clip(RoundedCornerShape(20.dp))
        .background(ProfileCardBrush)
        .border(1.5.dp, ProfileGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
        AsyncImage(
        model = photoUrl,
        contentDescription = "Profile photo",
        modifier = Modifier.fillMaxSize(),
        )
        } else {
        Text(initial, color = ProfileGreen, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
        }
    }
}
// - Wallet -

@Composable
private fun WalletEntryCard(
    state: WalletState,
    balance: Double,
    onOpen: () -> Unit,
) {
    SectionBlock(title = "Wallet", subtitle = "Open your full wallet page") {
        Row(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(ProfileWalletBrush)
        .border(1.dp, ProfileGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
        .clickable(onClick = onOpen)
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
        Modifier
        .size(50.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(ProfileGreen.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
        ) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ProfileGreen, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Adis Wallet"), color = ProfileText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(
        if (state.active) "ETB ${"%,.2f".format(balance)} available" else "Activate wallet to start using money tools",
        color = ProfileMuted,
        fontSize = 12.sp,
        maxLines = 1
        )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open wallet", tint = ProfileGreen)
        }
    }
}

@Composable
private fun FullWalletPage(
    state: WalletState,
    balance: Double,
    fingerprintEnabled: Boolean,
    deviceName: String,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onPay: () -> Unit,
    onHistory: () -> Unit,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onToggleFingerprint: (Boolean) -> Unit,
    onDevices: () -> Unit,
    onLoginHistory: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icons.Default.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Wallet"), color = ProfileText, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text(tr("Balance, payments, QR and security"), color = ProfileMuted, fontSize = 12.sp)
        }
        }
        Spacer(Modifier.height(12.dp))
        WalletCard(
        state = state,
        balance = balance,
        onActivate = onActivate,
        onDeposit = onDeposit,
        onWithdraw = onWithdraw,
        onSend = onSend,
        onReceive = onReceive,
        onPay = onPay,
        onHistory = onHistory,
        )
        Spacer(Modifier.height(12.dp))
        SecurityCenterCard(
        pinSet = state.walletPinSet,
        fingerprintEnabled = fingerprintEnabled,
        deviceName = deviceName,
        onSetPin = onSetPin,
        onChangePin = onChangePin,
        onToggleFingerprint = onToggleFingerprint,
        onDevices = onDevices,
        onLoginHistory = onLoginHistory,
        )
    }
}

@Composable
private fun WalletCard(
    state: WalletState,
    balance: Double,
    onActivate: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onPay: () -> Unit,
    onHistory: () -> Unit,
) {
    var balanceVisible by remember { mutableStateOf(true) }

    SectionBlock(title = "Wallet", subtitle = "Balance, payments, QR receive and history", horizontalPadding = 0.dp) {
        Box(
        Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(ProfileWalletBrush)
        .border(1.dp, ProfileGreen.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
        .shadow(if (isProfileLight()) 8.dp else 18.dp, RoundedCornerShape(28.dp), ambientColor = ProfileGreen.copy(if (isProfileLight()) 0.04f else 0.08f), spotColor = ProfileGreen.copy(if (isProfileLight()) 0.08f else 0.14f))
        .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ProfileText)
        Spacer(Modifier.width(8.dp))
        Text(tr("Adis Wallet"), color = ProfileText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
        color = if (state.active) ProfileGreen.copy(alpha = 0.14f) else ProfileLine.copy(alpha = 0.55f),
        shape = RoundedCornerShape(999.dp)
        ) {
        Text(
        if (state.active) "Active wallet" else "Wallet locked",
        color = ProfileText,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
        }
        }
        Column(horizontalAlignment = Alignment.End) {
        IconButton(
        onClick = { balanceVisible = !balanceVisible },
        modifier = Modifier.size(36.dp)
        ) {
        Icon(
        imageVector = if (balanceVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
        tint = ProfileGreen,
        modifier = Modifier.size(20.dp)
        )
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Total Balance"), color = ProfileMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
        if (balanceVisible) "ETB ${"%,.2f".format(balance)}" else "ETB ******",
        color = ProfileText,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp
        )
        }
        }
        }
        if (!state.active) {
        Spacer(Modifier.height(14.dp))
        Button(
        onClick = onActivate,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileOnAccent)
        ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Activate Wallet"), fontWeight = FontWeight.Bold)
        }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WalletAction(Icons.Default.ArrowDownward, "Deposit",  ProfileSurfaceSoft, ProfileGreen, onDeposit, Modifier.weight(1f))
        WalletAction(Icons.Default.ArrowUpward,   "Withdraw", ProfileSurfaceSoft, ProfileGreen, onWithdraw, Modifier.weight(1f))
        WalletAction(Icons.Default.Send,      "Send",    ProfileSurfaceSoft, ProfileGreen, onSend, Modifier.weight(1f))
        WalletAction(Icons.Default.QrCode,      "Receive", ProfileSurfaceSoft, ProfileGreen, onReceive, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        ModuleRow(Icons.Default.History, "Transaction history", "All deposits, withdrawals and payments", ProfileSurfaceSoft, ProfileGreen, onHistory)
    }
}

// - Security center -

@Composable
private fun SecurityCenterCard(
    pinSet: Boolean,
    fingerprintEnabled: Boolean,
    deviceName: String,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onToggleFingerprint: (Boolean) -> Unit,
    onDevices: () -> Unit,
    onLoginHistory: () -> Unit,
) {
    SectionBlock(
        title = "Security & Access",
        subtitle = if (pinSet) "PIN, biometrics and signed-in devices" else "Set a wallet PIN to unlock security controls"
    ) {
        if (!pinSet) {
        ModuleRow(Icons.Default.Lock, "Set Wallet PIN", "Required before sending money securely", ProfileSurfaceSoft, ProfileGreen, onSetPin)
        Spacer(Modifier.height(8.dp))
        Text(
        "After your PIN is set, fingerprint, active devices and login history will be available here.",
        color = ProfileMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp
        )
        } else {
        ModuleRow(Icons.Default.Lock, "Change PIN", "Update your transaction PIN", ProfileSurfaceSoft, ProfileGreen, onChangePin)
        ModuleDivider()
        ToggleModuleRow(
        icon = Icons.Default.Fingerprint,
        title = "Fingerprint Authentication",
        subtitle = if (fingerprintEnabled) "Enabled for wallet access" else "Use device biometrics for wallet access",
        checked = fingerprintEnabled,
        onCheckedChange = onToggleFingerprint
        )
        ModuleDivider()
        ModuleRow(Icons.Default.Devices, "Active Devices", deviceName, ProfileSurfaceSoft, ProfileGreen, onDevices)
        ModuleDivider()
        ModuleRow(Icons.Default.History, "Login History", "View your latest authenticated session", ProfileSurfaceSoft, ProfileGreen, onLoginHistory)
        }
    }
}

// - Verification status card -

@Composable
private fun WalletPinSheet(
    mode: WalletPinMode,
    onDismiss: () -> Unit,
    onSave: (oldPin: String, newPin: String) -> Unit,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val changing = mode == WalletPinMode.CHANGE
    var step by remember(mode) { mutableStateOf(if (changing) 0 else 1) }
    val valid = (!changing || oldPin.length == 6) && newPin.length == 6 && confirmPin.length == 6 && newPin == confirmPin
    val activeLabel = when (step) {
        0 -> "Old PIN"
        1 -> if (changing) "New PIN" else "Wallet PIN"
        else -> "Confirm PIN"
    }
    val activeValue = when (step) {
        0 -> oldPin
        1 -> newPin
        else -> confirmPin
    }
    val activeError = step == 2 && confirmPin.length == 6 && newPin != confirmPin

    LaunchedEffect(oldPin, newPin, confirmPin, changing) {
        when {
        changing && step == 0 && oldPin.length == 6 -> step = 1
        step == 1 && newPin.length == 6 -> step = 2
        }
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp)) {
        Text(if (changing) "Change Wallet PIN" else "Set Wallet PIN", color = ProfileText, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(
        when (step) {
        0 -> "Enter your old 6-digit wallet PIN."
        1 -> "Enter your new 6-digit wallet PIN."
        else -> "Confirm the new PIN to finish."
        },
        color = ProfileMuted,
        fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        PinBoxField(
        label = activeLabel,
        value = activeValue,
        onValueChange = {
        when (step) {
        0 -> oldPin = it
        1 -> {
        newPin = it
        confirmPin = ""
        }
        else -> confirmPin = it
        }
        },
        isError = activeError
        )
        if (confirmPin.length == 6 && newPin != confirmPin) {
        Spacer(Modifier.height(6.dp))
        Text(tr("PINs do not match"), color = ProfileDanger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
        when (step) {
        0 -> "After 6 digits, this same box will ask for the new PIN."
        1 -> "After 6 digits, this same box will ask you to confirm."
        else -> "Enter the same new PIN again."
        },
        color = ProfileMuted,
        fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        if (step > if (changing) 0 else 1) {
        TextButton(onClick = { step--; if (step == 1) confirmPin = "" }) {
        Text(tr("Back"), color = ProfileGreen, fontWeight = FontWeight.Bold)
        }
        }
        Spacer(Modifier.height(8.dp))
        Button(
        onClick = { onSave(oldPin, newPin) },
        enabled = valid,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileOnAccent)
        ) {
        Text(if (changing) "Update PIN" else "Set PIN", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Cancel"), color = ProfileMuted)
        }
    }
}

@Composable
private fun WalletPinStepIndicator(
    changing: Boolean,
    step: Int,
    oldDone: Boolean,
    newDone: Boolean,
    confirmDone: Boolean,
) {
    val steps = if (changing) {
        listOf("Old" to oldDone, "New" to newDone, "Confirm" to confirmDone)
    } else {
        listOf("New" to newDone, "Confirm" to confirmDone)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, item ->
        val realIndex = if (changing) index else index + 1
        val active = step == realIndex
        Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(999.dp),
        color = when {
        item.second -> ProfileGreen.copy(alpha = 0.10f)
        active -> ProfileGreen.copy(alpha = 0.10f)
        else -> ProfileSurfaceSoft
        },
        border = BorderStroke(1.dp, if (active || item.second) ProfileGreen.copy(alpha = 0.55f) else ProfileLine)
        ) {
        Text(
        item.first,
        color = if (active || item.second) ProfileGreen else ProfileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 8.dp)
        )
        }
        }
    }
}

@Composable
private fun PinBoxField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val blink by rememberInfiniteTransition(label = "pinCursor").animateFloat(
        initialValue = 0.20f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(520), repeatMode = RepeatMode.Reverse),
        label = "pinCursorAlpha"
    )
    LaunchedEffect(label) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Column {
        Text(label, color = ProfileText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .onFocusChanged { focused = it.isFocused }
        .clickable {
        focusRequester.requestFocus()
        keyboard?.show()
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(ProfileGreen),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
        decorationBox = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { index ->
        val filled = index < value.length
        val active = focused && index == value.length.coerceAtMost(5)
        Box(
        modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(ProfileGreen.copy(alpha = if (filled || active) 0.16f else 0.08f))
        .border(
        width = if (active) 2.2.dp else if (filled) 1.6.dp else 1.dp,
        color = when {
        isError -> ProfileDanger
        active -> ProfileGreen
        filled -> ProfileGreen
        else -> ProfileLine
        },
        shape = RoundedCornerShape(16.dp)
        ),
        contentAlignment = Alignment.Center
        ) {
        Text(
        text = if (filled) "*" else "",
        color = ProfileGreen,
        fontSize = 28.sp,
        fontWeight = FontWeight.Black
        )
        if (!filled && active) {
        Box(
        Modifier
        .width(3.dp)
        .height(24.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(ProfileGreen.copy(alpha = blink))
        )
        }
        }
        }
        }
        }
        )
    }
}

@Composable
private fun DeviceAccessSheet(onDismiss: () -> Unit, onLinkDesktop: () -> Unit) {
    val authUser = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    val repository = remember { LinkedDevicesRepository() }
    var devices by remember { mutableStateOf<List<LinkedDeviceResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
        repository.linkedDevices()
        .onSuccess { devices = it; message = null }
        .onFailure { message = CloudflareClient.friendlyErrorMessage(it, "Could not load linked devices") }
        loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp)) {
        Text(tr("Linked Devices"), color = ProfileText, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(tr("Manage AdisChat Desktop sessions for this account."), color = ProfileMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        AccountRow(Icons.Default.QrCodeScanner, "Link Desktop", "Scan the QR code shown on AdisChat Desktop", ProfileGreen, onLinkDesktop)
        Spacer(Modifier.height(14.dp))
        Text("This phone", color = ProfileText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        SecurityInfoRow("Device", currentDeviceName())
        SecurityInfoRow("Android", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        SecurityInfoRow("Account", authUser?.email ?: authUser?.phoneNumber ?: "Signed in")
        Spacer(Modifier.height(14.dp))
        Text("Other logged-in devices", color = ProfileText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (loading) {
        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ProfileGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        } else if (devices.none { it.revokedAt == 0L }) {
        Text("No active desktop devices yet.", color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
        devices.filter { it.revokedAt == 0L }.forEach { device ->
        LinkedDeviceRow(
        device = device,
        onLogout = {
        scope.launch {
        repository.logoutDevice(device.id)
        .onSuccess { refresh() }
        .onFailure { message = CloudflareClient.friendlyErrorMessage(it, "Could not log out device") }
        }
        }
        )
        Spacer(Modifier.height(8.dp))
        }
        }
        message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
        onClick = {
        scope.launch {
        repository.logoutAllDesktop()
        .onSuccess { refresh() }
        .onFailure { message = CloudflareClient.friendlyErrorMessage(it, "Could not log out desktop devices") }
        }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp)
        ) { Text("Log out from all desktop devices", fontWeight = FontWeight.Bold, color = ProfileDanger) }
        Spacer(Modifier.height(10.dp))
        Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileOnAccent)
        ) { Text(tr("Done"), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun LinkedDeviceRow(device: LinkedDeviceResponse, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ProfileSurfaceSoft).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ProfileMint), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.DesktopWindows, null, tint = ProfileGreen)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(device.deviceName.ifBlank { "AdisChat Desktop" }, color = ProfileText, fontWeight = FontWeight.Bold)
        Text(listOf(device.os, device.location.ifBlank { null }).filterNotNull().joinToString(" - ").ifBlank { "Desktop" }, color = ProfileMuted, fontSize = 12.sp)
        Text("Last active ${formatTime(device.lastActiveAt)}", color = ProfileMuted, fontSize = 12.sp)
        }
        TextButton(onClick = onLogout) { Text("Logout", color = ProfileDanger, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun LoginHistorySheet(onDismiss: () -> Unit) {
    val authUser = FirebaseAuth.getInstance().currentUser
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp)) {
        Text(tr("Login History"), color = ProfileText, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(tr("Firebase authenticated session details."), color = ProfileMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        SecurityInfoRow("Last sign-in", formatTime(authUser?.metadata?.lastSignInTimestamp ?: 0L))
        SecurityInfoRow("Account created", formatTime(authUser?.metadata?.creationTimestamp ?: 0L))
        SecurityInfoRow("Provider", authUser?.providerData?.firstOrNull { it.providerId != "firebase" }?.providerId ?: "Firebase")
        SecurityInfoRow("Device", currentDeviceName())
        Spacer(Modifier.height(14.dp))
        Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileOnAccent)
        ) {
        Text(tr("Done"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecurityInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = ProfileText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun VerificationStatusCard(
    user: AppUser?,
    status: VerificationStatus?,
    onApplyBadge: () -> Unit,
    onAdminPanel: () -> Unit,
) {
    val level      = user?.verificationLevel ?: 0
    val requestStatus = user?.verificationRequestStatus

    SectionBlock(title = "Verification status", subtitle = "Account trust and compliance") {

        if (user == null) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ProfileGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return@SectionBlock
        }

        // - Row 1: Email -
        VerificationRow(
        label  = "Email verification",
        status = if (level >= 1) "Verified" else "Not verified",
        tint   = if (level >= 1) ProfileGreen else ProfileMuted,
        icon   = Icons.Default.CheckCircle,
        )
        ModuleDivider()

        // - Row 2: Profile -
        VerificationRow(
        label  = "Profile complete",
        status = if (level >= 2) "Complete" else "Incomplete",
        tint   = if (level >= 2) ProfileGreen else ProfileMuted,
        icon   = Icons.Default.Person,
        )
        ModuleDivider()

        // - Row 3: Blue Badge (context-sensitive) -
        when {
        // Already has blue badge
        level >= 3 -> VerificationRow("Blue Badge", "Verified", ProfileBlue, Icons.Default.Verified)

        // Request under review
        requestStatus == "pending" ->
        VerificationRow("Blue Badge", "Under Review", ProfileGreen, Icons.Default.Badge)

        // Request rejected
        requestStatus == "rejected" -> {
        VerificationRow("Blue Badge", "Rejected", ErrorRed, Icons.Default.Badge)
        status?.request?.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
        Text(
        "Reason: $reason",
        fontSize = 11.sp,
        color    = ErrorRed.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 56.dp, top = 2.dp, bottom = 4.dp),
        )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onApplyBadge) {
        Text(tr("Re-apply for Blue Badge"), color = ProfileGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        }
        }

        // Profile complete - - can apply
        level >= 2 -> {
        Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconTile(Icons.Default.Badge, ProfileSurfaceSoft, ProfileGreen)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Blue Badge"), fontWeight = FontWeight.SemiBold, color = ProfileText)
        Text(tr("Apply for identity verification"), fontSize = 12.sp, color = ProfileMuted)
        }
        OutlinedButton(
        onClick      = onApplyBadge,
        shape      = RoundedCornerShape(12.dp),
        border      = BorderStroke(1.dp, ProfileGreen),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors      = ButtonDefaults.outlinedButtonColors(contentColor = ProfileGreen),
        ) {
        Text(tr("Apply"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        }
        }

        // Not yet eligible
        else -> {
        Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        IconTile(Icons.Default.Badge, ProfileSurfaceSoft, ProfileMuted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("Blue Badge"), fontWeight = FontWeight.SemiBold, color = ProfileText)
        Text(
        if (level == 0) "Verify your email first" else "Complete profile to unlock",
        fontSize = 12.sp,
        color    = ProfileMuted,
        )
        }
        Surface(shape = RoundedCornerShape(999.dp), color = ProfileSurfaceSoft) {
        Text(
        "Locked",
        color    = ProfileMuted,
        fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
        }
        }
        }
        }

        // - Admin Panel row (admins only) -
        if (user.role == "admin") {
        ModuleDivider()
        ModuleRow(
        icon     = Icons.Default.Security,
        title    = "Admin Panel",
        subtitle = "Review verification requests",
        iconBg   = ProfileSurfaceSoft,
        iconTint = ProfileGreen,
        onClick  = onAdminPanel,
        )
        }
    }
}

// - Logout -

@Composable
private fun LogoutCard(onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Card(
        shape      = RoundedCornerShape(22.dp),
        colors     = CardDefaults.cardColors(containerColor = ProfileSurface),
        elevation  = CardDefaults.cardElevation(0.dp),
        modifier   = Modifier.fillMaxWidth()
        .border(1.dp, ProfileDanger.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
        ) {
        ModuleRow(Icons.Default.Logout, "Logout", "Sign out from this device", ProfileSurfaceSoft, ProfileDanger, onClick)
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(horizontal = horizontalPadding)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = ProfileText)
        Text(subtitle, fontSize = 12.sp, color = ProfileMuted, lineHeight = 16.sp)
        Spacer(Modifier.height(7.dp))
        Card(
        shape    = RoundedCornerShape(22.dp),
        colors   = CardDefaults.cardColors(containerColor = ProfileSurface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
        .shadow(if (isProfileLight()) 3.dp else 0.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(0.04f), spotColor = Color.Black.copy(0.06f))
        .border(1.dp, ProfileLine, RoundedCornerShape(22.dp))
        ) {
        Column(Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun WalletAction(
    icon: ImageVector,
    label: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
        Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(17.dp))
        .background(bg)
        .border(1.dp, ProfileGreen.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
        ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ProfileText, maxLines = 1)
    }
}

@Composable
private fun ModuleRow(
    icon: ImageVector, title: String, subtitle: String,
    iconBg: Color, iconTint: Color, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, iconBg, iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = ProfileText)
        Text(subtitle, color = ProfileMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
        tint     = ProfileMuted.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToggleModuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, ProfileSurfaceSoft, ProfileGreen)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = ProfileText)
        Text(subtitle, color = ProfileMuted, fontSize = 12.sp)
        }
        Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
        checkedThumbColor = ProfileOnAccent,
        checkedTrackColor = ProfileGreen,
        uncheckedThumbColor = ProfileMuted,
        uncheckedTrackColor = ProfileSurfaceSoft,
        uncheckedBorderColor = ProfileLine
        )
        )
    }
}

@Composable
private fun VerificationRow(label: String, status: String, tint: Color, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        val rowTint = if (tint == ErrorRed || tint == ProfileDanger) ProfileDanger else ProfileGreen
        IconTile(icon, ProfileSurfaceSoft, rowTint)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = ProfileText)
        StatusPill(status, rowTint)
    }
}

@Composable
private fun StatusPill(text: String, tint: Color) {
    val safeTint = if (tint == ErrorRed || tint == ProfileDanger) ProfileDanger else ProfileGreen
    Surface(shape = RoundedCornerShape(999.dp), color = safeTint.copy(alpha = 0.14f)) {
        Text(text, color = safeTint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun IconTile(icon: ImageVector, bg: Color, tint: Color) {
    val safeTint = if (tint == ErrorRed || tint == RedColor || tint == ProfileDanger) ProfileDanger else ProfileGreen
    Box(
        Modifier
        .size(42.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(ProfileSurfaceSoft)
        .border(1.dp, safeTint.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = safeTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ModuleDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color    = ProfileLine.copy(alpha = 0.75f)
    )
}

// - Apply Blue Badge sheet -

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ApplyBadgeSheet(user: AppUser?, vm: ProfileViewModel, onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val scope      = rememberCoroutineScope()

    val documentUrl      by vm.documentUrl.collectAsStateWithLifecycle()
    val selfieUrl      by vm.selfieUrl.collectAsStateWithLifecycle()
    val uploadingDoc     by vm.uploadingDocument.collectAsStateWithLifecycle()
    val uploadingSelfie  by vm.uploadingSelfie.collectAsStateWithLifecycle()
    val submitting       by vm.submittingVerification.collectAsStateWithLifecycle()

    var fullName by remember { mutableStateOf(user?.name ?: "") }
    var docType  by remember { mutableStateOf("national_id") }

    LaunchedEffect(Unit) { vm.resetUploadState() }

    fun pickAndUpload(uri: Uri?, uploader: (ByteArray) -> Unit) {
        uri ?: return
        scope.launch {
        val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.readBytes()
        } ?: return@launch
        uploader(bytes)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickAndUpload(uri) { vm.uploadDocument(it) }
    }
    val selfiePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickAndUpload(uri) { vm.uploadSelfie(it) }
    }

    val docTypes = listOf("national_id" to "National ID", "passport" to "Passport", "driver_license" to "Driver License")
    val canSubmit = fullName.isNotBlank() && documentUrl.isNotBlank() && selfieUrl.isNotBlank() && !submitting

    Column(
        Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text(tr("Apply for Blue Badge"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(tr("Identity verification - step 3 of 3"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(16.dp))

        // Full name
        OutlinedTextField(
        value      = fullName,
        onValueChange = { fullName = it },
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Full legal name")) },
        leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
        shape      = RoundedCornerShape(14.dp),
        singleLine    = true,
        )
        Spacer(Modifier.height(16.dp))

        // Document type
        Text(tr("Document type"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        docTypes.forEach { (value, label) ->
        val selected = docType == value
        Surface(
        modifier = Modifier.weight(1f).clickable { docType = value },
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        border   = if (selected) BorderStroke(1.5.dp, Primary) else null,
        ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
        label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = if (selected) Primary else MaterialTheme.colorScheme.onSurface,
        )
        }
        }
        }
        }
        Spacer(Modifier.height(16.dp))

        // Document upload
        Text(tr("Document photo"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        UploadButton(
        label      = if (documentUrl.isBlank()) "Upload Document Photo" else "Document uploaded",
        isUploaded = documentUrl.isNotBlank(),
        isLoading  = uploadingDoc,
        onClick    = { documentPicker.launch("image/*") },
        )
        Spacer(Modifier.height(12.dp))

        // Selfie upload
        Text(tr("Selfie with document"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        UploadButton(
        label      = if (selfieUrl.isBlank()) "Take / Upload Selfie" else "Selfie uploaded",
        isUploaded = selfieUrl.isNotBlank(),
        isLoading  = uploadingSelfie,
        onClick    = { selfiePicker.launch("image/*") },
        )
        Spacer(Modifier.height(24.dp))

        // Submit
        Button(
        onClick  = { vm.submitVerification(fullName, docType) },
        enabled  = canSubmit,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
        if (submitting) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
        Text(tr("Submit for Review"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun UploadButton(label: String, isUploaded: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    val tint   = if (isUploaded) Primary else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (isUploaded) BorderStroke(1.5.dp, Primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    OutlinedButton(
        onClick      = onClick,
        modifier       = Modifier.fillMaxWidth().height(52.dp),
        shape      = RoundedCornerShape(14.dp),
        border      = border,
        enabled      = !isLoading,
        colors      = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
        Icon(if (isUploaded) Icons.Default.CheckCircle else Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp)
        }
    }
}

// - Admin Verification Panel -

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AdminVerificationPanel(vm: ProfileViewModel, onDismiss: () -> Unit) {
    val requests  by vm.adminRequests.collectAsStateWithLifecycle()
    val loading   by vm.adminLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs    = listOf("Pending", "Approved", "Rejected")
    val tabKeys = listOf("pending", "approved", "rejected")

    var rejectUid    by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) { vm.loadAdminRequests(tabKeys[selectedTab]) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        // Header
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(Modifier.weight(1f)) {
        Text(tr("Admin Panel"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(tr("Manage verification requests"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEachIndexed { index, title ->
        Tab(
        selected = selectedTab == index,
        onClick  = { selectedTab = index },
        text     = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
        )
        }
        }

        // List
        Column(
        Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        ) {
        when {
        loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Primary)
        }
        requests.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text("No ${tabs[selectedTab].lowercase()} requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> requests.forEach { req ->
        AdminRequestCard(
        request     = req,
        showActions = selectedTab == 0,
        onApprove   = { vm.approveVerification(req.uid) },
        onReject    = { rejectUid = req.uid; rejectReason = "" },
        )
        Spacer(Modifier.height(10.dp))
        }
        }
        Spacer(Modifier.height(12.dp))
        }
    }

    // Reject dialog
    if (rejectUid != null) {
        AlertDialog(
        onDismissRequest = { rejectUid = null },
        title = { Text(tr("Reject Verification")) },
        text  = {
        Column {
        Text(tr("Enter a rejection reason:"), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
        value      = rejectReason,
        onValueChange = { rejectReason = it },
        placeholder   = { Text(tr("e.g. Document photo unclear...")) },
        shape      = RoundedCornerShape(12.dp),
        minLines      = 2,
        modifier      = Modifier.fillMaxWidth(),
        )
        }
        },
        confirmButton = {
        TextButton(
        onClick  = { rejectUid?.let { vm.rejectVerification(it, rejectReason) }; rejectUid = null },
        enabled  = rejectReason.isNotBlank(),
        ) { Text(tr("Reject"), color = ErrorRed) }
        },
        dismissButton = { TextButton(onClick = { rejectUid = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun AdminRequestCard(
    request: AdminVerificationRequest,
    showActions: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
        Modifier.size(44.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(PrimaryDk, Primary))),
        contentAlignment = Alignment.Center
        ) {
        Text(
        request.userName.firstOrNull()?.uppercase() ?: "?",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
        )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(request.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(request.userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(
        text = request.status.replaceFirstChar { it.uppercase() },
        tint = when (request.status) {
        "approved" -> Primary
        "rejected" -> ErrorRed
        else -> Amber
        }
        )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
        Text(tr("Document"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
        request.documentType.replace("_", " ").replaceFirstChar { it.uppercase() },
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        }
        Column(Modifier.weight(1f)) {
        Text(tr("Phone"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(request.userPhone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        }
        request.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
        Spacer(Modifier.height(8.dp))
        Text("Rejection: $reason", fontSize = 12.sp, color = ErrorRed.copy(alpha = 0.8f))
        }
        if (showActions) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
        onClick      = onReject,
        modifier       = Modifier.weight(1f),
        shape      = RoundedCornerShape(12.dp),
        border      = BorderStroke(1.dp, ErrorRed),
        colors      = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
        ) { Text(tr("Reject"), fontWeight = FontWeight.Bold) }
        Button(
        onClick  = onApprove,
        modifier = Modifier.weight(1f),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
        ) { Text(tr("Approve"), fontWeight = FontWeight.Bold) }
        }
        }
        }
    }
}

// - Receive Money sheet -

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveMoneySheet(
    username: String,
    walletId: String,
    onPay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }
    val cleanUsername = username.removePrefix("@")
    val payload = remember(walletId, cleanUsername, amount) {
        walletReceivePayload(walletId, cleanUsername, amount.toDoubleOrNull())
    }
    Column(
        Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Receive Money"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Scan this QR code to send money to your wallet"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        RealQrCode(payload)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
        value = amount,
        onValueChange = { value -> amount = value.filter { it.isDigit() || it == '.' }.take(12) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(tr("Amount ETB optional")) },
        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Primary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(14.dp))
        Button(
        onClick = onPay,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Pay"), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Card(
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.fillMaxWidth()
        ) {
        Column(Modifier.padding(16.dp)) {
        DetailRow("Wallet ID", walletId)
        ModuleDivider()
        DetailRow("Username", "@$cleanUsername")
        }
        }
        Spacer(Modifier.height(16.dp))
        Button(
        onClick = {
        saveMessage = if (saveQrImageToGallery(context, payload, "adischat_receive_${System.currentTimeMillis()}")) {
        "Saved to gallery"
        } else {
        "Could not save image"
        }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
        Text(tr("Save QR Image"), fontWeight = FontWeight.Bold)
        }
        if (saveMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(saveMessage, color = if (saveMessage.startsWith("Saved")) Primary else MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun RealQrCode(payload: String) {
    val bitmap = remember(payload) { createQrBitmap(payload, 512) }
    Card(
        shape     = RoundedCornerShape(28.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code",
        modifier = Modifier.size(236.dp).padding(18.dp)
        )
    }
}

@Composable
private fun ProfileQrSheet(name: String, username: String, link: String, onShare: () -> Unit) {
    Column(
        Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 4.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("My QR Code", color = ProfileText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Scan to open my AdisChat profile", color = ProfileMuted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        RealQrCode(link)
        Spacer(Modifier.height(18.dp))
        Text(name, color = ProfileText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(link.removePrefix("https://"), color = ProfileGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        Button(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = Color.Black),
        ) {
        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Share profile link", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ChangePasswordSheet(busy: Boolean, onSubmit: (String, String) -> Unit) {
    var oldPw     by remember { mutableStateOf("") }
    var newPw     by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var reveal    by remember { mutableStateOf(false) }

    val newValid  = newPw.length >= 6
    val match     = newPw == confirmPw
    val mismatch  = confirmPw.isNotEmpty() && !match
    val canSubmit = oldPw.isNotBlank() && newValid && match && !busy
    val transform = if (reveal) VisualTransformation.None else PasswordVisualTransformation()
    val pwKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password)

    Column(
        Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Text("Change Password", color = ProfileText, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text("Enter your current password, then choose a new one.", color = ProfileMuted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
        value = oldPw, onValueChange = { oldPw = it },
        label = { Text("Current password") }, singleLine = true,
        visualTransformation = transform, keyboardOptions = pwKeyboard,
        modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
        value = newPw, onValueChange = { newPw = it },
        label = { Text("New password") }, singleLine = true,
        visualTransformation = transform, keyboardOptions = pwKeyboard,
        modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
        value = confirmPw, onValueChange = { confirmPw = it },
        label = { Text("Confirm new password") }, singleLine = true, isError = mismatch,
        visualTransformation = transform, keyboardOptions = pwKeyboard,
        modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Text(
        if (reveal) "Hide passwords" else "Show passwords",
        color = ProfileGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable { reveal = !reveal }.padding(vertical = 2.dp),
        )

        val hint = when {
        mismatch -> "Passwords do not match"
        newPw.isNotEmpty() && !newValid -> "Use at least 6 characters"
        else -> null
        }
        if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, color = ProfileDanger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(18.dp))
        Button(
        onClick = { onSubmit(oldPw, newPw) },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
        containerColor = ProfileGreen, contentColor = Color.Black,
        disabledContainerColor = ProfileGreen.copy(alpha = 0.35f), disabledContentColor = Color.Black.copy(alpha = 0.5f),
        ),
        ) {
        if (busy) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Text("Update Password", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun RealBarcode(payload: String) {
    val bitmap = remember(payload) { createBarcodeBitmap(payload, 720, 220) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Payment barcode",
        modifier = Modifier.fillMaxWidth().height(118.dp).padding(12.dp)
        )
    }
}

// - Edit Profile sheet -

@Composable
private fun PayQrSheet(
    state: WalletState,
    fallbackUsername: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    val payCode = state.payCode.ifBlank { "      " }
    val rawPayCode = payCode.replace(" ", "").ifBlank { state.payBarcodePayload.replace(" ", "") }
    val qrPayload = rawPayCode
    val barcodePayload = rawPayCode

    LaunchedEffect(Unit) {
        while (true) {
        delay(60_000)
        onRefresh()
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Merchant Pay Code"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(10.dp))
        Text(tr("Show this code to a merchant scanner. No PIN is needed after you reveal it."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
        ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text(tr("16-digit pay code"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
        if (visible) payCode else "- - - -",
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = 1.sp
        )
        }
        IconButton(onClick = { visible = !visible }) {
        Icon(Icons.Default.Visibility, contentDescription = if (visible) "Hide code" else "Show code", tint = Primary)
        }
        }
        Spacer(Modifier.height(12.dp))
        if (visible) {
        RealBarcode(barcodePayload)
        Spacer(Modifier.height(14.dp))
        RealQrCode(qrPayload)
        } else {
        Box(
        Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
        ) {
        Text(tr("Tap eye to show merchant code"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        }
        }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
        onClick = { copyText(context, "AdisChat pay code", payCode) },
        enabled = visible,
        modifier = Modifier.weight(1f).height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text(tr("Copy"), fontWeight = FontWeight.Bold) }
        OutlinedButton(
        onClick = onRefresh,
        modifier = Modifier.weight(1f).height(50.dp),
        shape = RoundedCornerShape(14.dp)
        ) { Text(tr("New Code")) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PremiumPayQrSheet(
    state: WalletState,
    fallbackUsername: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val payCode = state.payCode.ifBlank { "      " }
    val rawCode = payCode.replace(" ", "").ifBlank { state.payBarcodePayload.replace(" ", "") }

    LaunchedEffect(Unit) {
        while (true) {
        delay(60_000)
        onRefresh()
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Merchant Pay Code"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(10.dp))
        Text(tr("Show this code to a merchant scanner. It refreshes automatically every minute."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
        ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
        Text(tr("16-digit pay code"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr("QR and barcode use only the code"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { visible = !visible }) {
        Icon(Icons.Default.Visibility, contentDescription = if (visible) "Hide code" else "Show code", tint = Primary)
        }
        }
        Spacer(Modifier.height(12.dp))
        if (visible) {
        RealBarcode(rawCode)
        Spacer(Modifier.height(8.dp))
        Text(
        payCode,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        RealQrCode(rawCode)
        } else {
        Box(
        Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
        ) {
        Text(tr("Tap eye to show merchant code"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        }
        }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DepositSheet(onDismiss: () -> Unit, onDeposit: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Deposit Money"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(16.dp))
        EditField("Amount ETB", amount, Icons.Default.AccountBalanceWallet, KeyboardType.Decimal) { amount = it }
        Spacer(Modifier.height(12.dp))
        Button(
        onClick = { onDeposit(amount.toDoubleOrNull() ?: 0.0) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text(tr("Add Balance"), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SendMoneySheet(
    state: WalletState,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onSend: (String, Double, String) -> Unit,
) {
    var mode by remember { mutableStateOf<String?>(null) }
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPinStep by remember { mutableStateOf(false) }
    var sentForPin by remember { mutableStateOf("") }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val pinValid = pin.length == 6 && pin.all { it.isDigit() }
    val canPay = recipient.isNotBlank() && amountValue > 0.0

    LaunchedEffect(pin) {
        val transferKey = "${recipient.trim()}|$amountValue|$pin"
        if (showPinStep && pinValid && transferKey != sentForPin) {
        sentForPin = transferKey
        onSend(recipient.trim(), amountValue, pin)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Send Money"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Balance: ETB ${"%,.2f".format(state.balance)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        if (mode == null) {
        WalletSendOption(
        icon = Icons.Default.Send,
        title = "Transfer",
        subtitle = "Send by username, phone number, or email",
        onClick = { mode = "transfer" }
        )
        Spacer(Modifier.height(10.dp))
        WalletSendOption(
        icon = Icons.Default.QrCode,
        title = "Scan",
        subtitle = "Open scanner and pay from a wallet QR",
        onClick = onScan
        )
        Spacer(Modifier.height(28.dp))
        return@Column
        }
        TextButton(onClick = {
        mode = null
        showPinStep = false
        pin = ""
        }) {
        Text(tr("Change option"), color = Primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        EditField("Phone, username, or email", recipient, Icons.Default.AlternateEmail, enabled = !showPinStep) {
        recipient = it
        pin = ""
        showPinStep = false
        }
        Spacer(Modifier.height(12.dp))
        EditField("Amount ETB", amount, Icons.Default.AccountBalanceWallet, KeyboardType.Decimal, enabled = !showPinStep) {
        amount = it
        pin = ""
        showPinStep = false
        }
        Spacer(Modifier.height(12.dp))
        if (showPinStep) {
        Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ProfileGreen.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, ProfileGreen.copy(alpha = 0.28f))
        ) {
        Column(Modifier.padding(16.dp)) {
        Text(tr("Confirm Payment"), color = ProfileText, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("ETB ${"%,.2f".format(amountValue)} to ${recipient.trim()}", color = ProfileMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        PinBoxField("Transaction PIN", pin, { pin = it }, isError = pin.isNotBlank() && !pinValid)
        Spacer(Modifier.height(8.dp))
        Text(tr("Payment sends automatically after 6 digits."), color = ProfileMuted, fontSize = 12.sp)
        }
        }
        } else {
        Button(
        onClick = { showPinStep = true },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = canPay,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text(tr("Pay"), fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WalletSendOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(GreenBg),
        contentAlignment = Alignment.Center
        ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionHistorySheet(
    state: WalletState,
    onDismiss: () -> Unit,
    onOpenTransaction: (WalletTransaction) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Transaction History"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(12.dp))
        if (state.transactions.isEmpty()) {
        Text(tr("No wallet activity yet"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        } else {
        state.transactions.forEach { item ->
        TransactionHistoryRow(transaction = item, onClick = { onOpenTransaction(item) })
        ModuleDivider()
        }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TransactionHistoryRow(
    transaction: WalletTransaction,
    onClick: () -> Unit,
) {
    val isCredit = transaction.amount >= 0
    val signedAmount = "${if (isCredit) "+" else "-"} ETB ${"%,.2f".format(kotlin.math.abs(transaction.amount))}"
    val time = remember(transaction.timestamp) {
        java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(transaction.timestamp))
    }
    val icon = when {
        transaction.type == "merchant_receive" || transaction.type == "receive" || isCredit -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }
    val tint = if (isCredit) ProfileGreen else RedColor
    val bg = if (isCredit) GreenBg else RedBg

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(transaction.title, fontWeight = FontWeight.Bold, color = ProfileText, fontSize = 15.sp)
        Text(
        "${transaction.status.ifBlank { "completed" }.replaceFirstChar { it.uppercase() }} - $time",
        fontSize = 12.sp,
        color = ProfileMuted
        )
        }
        Text(
        signedAmount,
        color = tint,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp,
        textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TransactionDetailSheet(transaction: WalletTransaction, onDismiss: () -> Unit) {
    val isCredit = transaction.amount >= 0
    val name = transaction.counterpartyName.ifBlank {
        transaction.counterpartyUsername.ifBlank { if (isCredit) "Sender" else "Receiver" }
    }
    val txId = transaction.reference.ifBlank { transaction.id }
    val time = remember(transaction.timestamp) {
        java.text.SimpleDateFormat("MMM d, yyyy - h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(transaction.timestamp))
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Transaction Successful"), color = ProfileText, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(12.dp))
        Box(
        Modifier.size(82.dp).clip(CircleShape).background(ProfileGreen.copy(alpha = 0.16f)).border(1.dp, ProfileGreen.copy(alpha = 0.46f), CircleShape),
        contentAlignment = Alignment.Center
        ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfileGreen, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
        "${if (isCredit) "+" else "-"} ETB ${"%,.2f".format(kotlin.math.abs(transaction.amount))}",
        color = if (isCredit) ProfileGreen else RedColor,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        textAlign = TextAlign.Center
        )
        Text(if (isCredit) "Received successfully" else "Paid successfully", color = ProfileMuted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileSurface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, ProfileLine, RoundedCornerShape(22.dp))
        ) {
        Column(Modifier.padding(18.dp)) {
        ReceiptRow(if (isCredit) "Sender name" else "Receiver name", maskName(name))
        ModuleDivider()
        ReceiptRow("Username", transaction.counterpartyUsername.takeIf { it.isNotBlank() }?.let { "@${maskHandle(it)}" } ?: "Unavailable")
        ModuleDivider()
        ReceiptRow("Wallet ID", maskedId(transaction.counterpartyWalletId))
        ModuleDivider()
        ReceiptRow("Transaction ID", maskedTransactionId(txId))
        ModuleDivider()
        ReceiptRow("Amount", "${if (isCredit) "+" else "-"} ETB ${"%,.2f".format(kotlin.math.abs(transaction.amount))}")
        ModuleDivider()
        ReceiptRow("Status", transaction.status.ifBlank { "completed" }.replaceFirstChar { it.uppercase() })
        ModuleDivider()
        ReceiptRow("Date", time)
        }
        }
        Spacer(Modifier.height(12.dp))
        Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProfileGreen, contentColor = ProfileOnAccent)
        ) {
        Text(tr("Done"), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ProfileMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = ProfileText, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    user: AppUser?, fatherName: String, identityLocked: Boolean, saving: Boolean,
    onSave: (String, String, String, String, String, String) -> Unit,
    onPhotoSelected: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name     by remember(user) { mutableStateOf(user?.name ?: "") }
    var father   by remember(user, fatherName) { mutableStateOf(fatherName) }
    var phone    by remember(user) { mutableStateOf(user?.phone ?: "") }
    var email    by remember(user) { mutableStateOf(user?.email ?: "") }
    var username by remember(user) { mutableStateOf(user?.username ?: "") }
    var dob      by remember(user) { mutableStateOf(user?.dob ?: "") }
    val initial  = name.firstOrNull()?.uppercase() ?: "A"
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
        val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        bytes?.let(onPhotoSelected)
        }
    }

    Column(
        Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Edit Profile"), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).clickable { photoPicker.launch("image/*") }) {
        Box(
        Modifier.size(88.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(PrimaryDk, Primary))),
        contentAlignment = Alignment.Center
        ) {
        if (!user?.photoUrl.isNullOrBlank()) {
        AsyncImage(
        model = user?.photoUrl,
        contentDescription = "Profile photo",
        modifier = Modifier.fillMaxSize(),
        )
        } else {
        Text(initial, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        }
        Box(
        Modifier.size(30.dp).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.5.dp, Primary, CircleShape)
        .align(Alignment.BottomEnd),
        contentAlignment = Alignment.Center
        ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Primary, modifier = Modifier.size(15.dp))
        }
        }
        Spacer(Modifier.height(22.dp))
        if (identityLocked) {
        Surface(
        color = GreenBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
        ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Verified identity details are locked"), color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        }
        Spacer(Modifier.height(12.dp))
        }
        EditField("Full Name",     name,     Icons.Default.Person, enabled = !identityLocked) { name = it }
        Spacer(Modifier.height(12.dp))
        EditField("Father Name",   father,   Icons.Default.Badge, enabled = !identityLocked) { father = it }
        Spacer(Modifier.height(12.dp))
        EditField("Phone Number",  phone,    Icons.Default.Phone, KeyboardType.Phone) { phone = it }
        Spacer(Modifier.height(12.dp))
        EditField("Email",      email,    Icons.Default.AlternateEmail, KeyboardType.Email) { email = it }
        Spacer(Modifier.height(12.dp))
        EditField("Username",      username, Icons.Default.AlternateEmail) { username = it }
        Spacer(Modifier.height(12.dp))
        DateOfBirthField(
        value    = dob,
        enabled  = !identityLocked,
        onChange = { dob = it },
        )
        Spacer(Modifier.height(20.dp))
        Button(
        onClick  = { onSave(name, father, phone, email, username, dob) },
        enabled  = !saving,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
        if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(tr("Save Changes"), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value      = value,
        onValueChange = onChange,
        enabled       = enabled,
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(label) },
        leadingIcon   = { Icon(icon, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape      = RoundedCornerShape(14.dp),
        singleLine    = true
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthField(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val initialMillis = remember(value) {
        runCatching { if (value.isNotBlank()) fmt.parse(value)?.time else null }.getOrNull()
    }

    Box {
        OutlinedTextField(
        value      = value,
        onValueChange = {},
        enabled       = false,      // read-only; tap opens calendar
        readOnly      = true,
        modifier      = Modifier.fillMaxWidth(),
        label      = { Text(tr("Date of Birth")) },
        placeholder   = { Text(tr("YYYY-MM-DD")) },
        leadingIcon   = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        shape      = RoundedCornerShape(14.dp),
        singleLine    = true,
        colors = OutlinedTextFieldDefaults.colors(
        disabledTextColor      = ProfileText,
        disabledLabelColor     = ProfileMuted,
        disabledLeadingIconColor = ProfileMuted,
        disabledBorderColor    = MaterialTheme.colorScheme.outline,
        ),
        )
        // Transparent overlay captures taps to open the calendar.
        if (enabled) {
        Box(
        Modifier
        .matchParentSize()
        .clip(RoundedCornerShape(14.dp))
        .clickable { showPicker = true }
        )
        }
    }

    if (showPicker) {
        val today = System.currentTimeMillis()
        val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        override fun isSelectableYear(year: Int) = year <= 2026
        },
        )
        DatePickerDialog(
        onDismissRequest = { showPicker = false },
        confirmButton = {
        TextButton(onClick = {
        state.selectedDateMillis?.let { onChange(fmt.format(Date(it))) }
        showPicker = false
        }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(tr("Cancel")) } },
        ) {
        DatePicker(state = state, showModeToggle = true)
        }
    }
}

private fun walletIdFor(user: AppUser?, username: String): String {
    val seed = user?.uid.orEmpty().ifBlank { username }
    return "ADIS-${numericIdFromSeed(seed, '2', 12)}"
}

private fun publicUserIdFor(seed: String): String =
    numericIdFromSeed(seed, '1', 20)

private fun numericIdFromSeed(seed: String, prefix: Char, length: Int): String {
    var hash = 0
    seed.forEach { char -> hash = hash * 31 + char.code }
    val builder = StringBuilder().append(prefix)
    while (builder.length < length) {
        hash = hash * 1_664_525 + 1_013_904_223
        builder.append(kotlin.math.abs(hash % 10))
    }
    return builder.toString().take(length)
}

private fun maskName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Unavailable"
    return parts.joinToString(" ") { part ->
        when {
        part.length <= 2 -> "${part.first()}*"
        else -> "${part.first()}${"*".repeat((part.length - 2).coerceAtMost(6))}${part.last()}"
        }
    }
}

private fun maskHandle(handle: String): String =
    when {
        handle.length <= 3 -> "${handle.firstOrNull() ?: '*'}**"
        else -> "${handle.take(2)}${"*".repeat((handle.length - 3).coerceAtMost(8))}${handle.last()}"
    }

private fun maskedId(value: String): String =
    if (value.length <= 8) value.ifBlank { "Unavailable" }
    else "${value.take(6)}${"*".repeat(6)}${value.takeLast(4)}"

private fun maskedTransactionId(value: String): String =
    if (value.length <= 8) value.ifBlank { "Unavailable" }
    else "${value.take(4)}${"*".repeat(8)}${value.takeLast(4)}"

private fun walletQrPayload(name: String, username: String, phone: String, walletId: String): String =
    "adischat://wallet?walletId=$walletId&username=$username&phone=$phone&name=$name"

private fun walletReceivePayload(walletId: String, username: String, amount: Double?): String {
    val builder = Uri.Builder()
        .scheme("adischat")
        .authority("wallet")
        .appendQueryParameter("walletId", walletId)
        .appendQueryParameter("username", username)
    if (amount != null && amount > 0.0) {
        builder.appendQueryParameter("amount", String.format(Locale.US, "%.2f", amount))
    }
    return builder.build().toString()
}

private fun usernameQrPayload(username: String, name: String, phone: String): String =
    AdisLinks.user(username)

private fun createQrBitmap(payload: String, size: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
        for (y in 0 until size) {
        setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        }
    }
}

private fun saveQrImageToGallery(context: Context, payload: String, name: String): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val fileName = "$name.png"
        val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AdisChat")
        put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { output ->
        createQrBitmap(payload, 1024).compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
}

private fun createBarcodeBitmap(payload: String, width: Int, height: Int): Bitmap {
    val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.CODE_128, width, height)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until width) {
        for (y in 0 until height) {
        setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        }
    }
}

private fun currentDeviceName(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    return if (model.startsWith(Build.MANUFACTURER, ignoreCase = true)) model else "$maker $model"
}

private fun authenticateWithBiometric(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onError("Fingerprint is not available on this screen")
        return
    }

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    val manager = BiometricManager.from(context)
    when (manager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        onError("Set up fingerprint on this phone first")
        return
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
        onError("Fingerprint is not available on this phone")
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
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
        onError("Fingerprint did not match")
        }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .setNegativeButtonText(tr("Cancel"))
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Unavailable"
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share AdisChat"))
}
