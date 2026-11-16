package com.adischat.app.ui.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.data.repository.LinkedDevicesRepository
import com.adischat.app.network.DesktopQrMobileResponse
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.theme.*
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.adischat.app.ui.localization.tr
import com.adischat.app.feature.restaurants.data.RestaurantsRepository
import com.adischat.app.feature.restaurants.data.RestaurantsFailure

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    prefs: AppPreferences,
    onOpenUrl: (String) -> Unit = {},
    onOpenRestaurantQr: (String) -> Unit = {},
) {
    val vm: ScanViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(prefs) as T
    })

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> ScanContent(vm, onOpenUrl, onOpenRestaurantQr)
        else -> PermissionRequest(
        isPermanentlyDenied = !cameraPermission.status.isGranted &&
        !cameraPermission.status.shouldShowRationale,
        onRequest = { cameraPermission.launchPermissionRequest() }
        )
    }
}

@Composable
private fun PermissionRequest(isPermanentlyDenied: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CameraAlt, null, tint = Primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text(tr("Camera Permission Needed"), style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
        if (isPermanentlyDenied)
        "Camera access was denied. Please enable it in Settings to scan QR codes."
        else
        "AdisChat needs camera access to scan QR codes, links, contacts, Wi-Fi codes, and desktop login codes.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(if (isPermanentlyDenied) "Open Settings" else "Grant Permission",
        fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScanContent(
    vm: ScanViewModel,
    onOpenUrl: (String) -> Unit,
    onOpenRestaurantQr: (String) -> Unit,
) {
    val context      = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val result      by vm.result.collectAsStateWithLifecycle()
    val torchOn      by vm.torchOn.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val data = intent?.getStringExtra("data")
                    ?: intent?.getStringExtra("b64")?.let {
                        try {
                            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) { null }
                    }
                    ?: return
                vm.onBarcodeDetected(data)
            }
        }
        val filter = android.content.IntentFilter("com.adischat.app.QA_SCAN_BARCODE")
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    var camera: Camera? by remember { mutableStateOf(null) }
    val cameraExecutor  = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner  = remember { BarcodeScanning.getClient() }

    // Control torch via CameraX
    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
        val image  = InputImage.fromBitmap(bitmap, 0)
        BarcodeScanning.getClient().process(image)
        .addOnSuccessListener { barcodes ->
        barcodes.firstOrNull()?.rawValue?.let { vm.onBarcodeDetected(it) }
        }
        } catch (e: Exception) { /* ignore */ }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {

        // - Camera view -
        Box(Modifier.fillMaxWidth().weight(1f).statusBarsPadding()) {

        // CameraX preview.
        // COMPATIBLE = TextureView-backed. The default PERFORMANCE mode uses a
        // SurfaceView, which renders black inside a Compose AndroidView because
        // the parent Column's black background is composited on top of it.
        //
        // The camera is bound here in the factory (via the provider future's
        // listener) rather than in a LaunchedEffect: ScanContent briefly thrashes
        // composition on entry, which would cancel a LaunchedEffect coroutine
        // before bindToLifecycle() runs. The future listener is not tied to the
        // composition scope, so binding always completes.
        AndroidView(
        factory = { ctx ->
        val pv = PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
        try {
        val provider = providerFuture.get()
        val preview  = Preview.Builder().build()
        .also { it.setSurfaceProvider(pv.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        analysis.setAnalyzer(cameraExecutor) { proxy ->
        val mediaImage = proxy.image
        if (mediaImage != null) {
        val img = InputImage.fromMediaImage(
        mediaImage, proxy.imageInfo.rotationDegrees
        )
        barcodeScanner.process(img)
        .addOnSuccessListener { codes ->
        codes.firstOrNull()?.rawValue
        ?.let { vm.onBarcodeDetected(it) }
        }
        .addOnCompleteListener { proxy.close() }
        } else {
        proxy.close()
        }
        }
        provider.unbindAll()
        camera = provider.bindToLifecycle(
        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
        preview, analysis
        )
        } catch (e: Exception) {
        Log.e("ScanCam", "camera setup failed", e)
        }
        }, ContextCompat.getMainExecutor(ctx))
        pv
        },
        modifier = Modifier.fillMaxSize()
        )

        // QR frame overlay
        ScanFrameOverlay()

        // Header
        Row(
        Modifier.fillMaxWidth().align(Alignment.TopCenter)
        .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        Spacer(Modifier.width(44.dp))
        Text(
        "Scan QR Code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
        modifier = Modifier.weight(1f),
        style = LocalTextStyle.current.copy(
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        )
        Spacer(Modifier.width(44.dp))
        }

        // Bottom toolbar
        Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
        ) {
        ScanToolButton(
        icon    = Icons.Default.Photo,
        label   = "Gallery",
        onClick = { galleryLauncher.launch("image/*") }
        )
        ScanToolButton(
        icon    = if (torchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
        label   = if (torchOn) "Light Off" else "Light",
        active  = torchOn,
        onClick = { vm.toggleTorch() }
        )
        }

        // Scan result overlay
        if (result is ScanResult.RestaurantQr) {
            val qrResult = result as ScanResult.RestaurantQr
            LaunchedEffect(qrResult.token) {
                onOpenRestaurantQr(qrResult.token)
                vm.dismissResult()
            }
        }

        if (result is ScanResult.RestaurantCenterLogin) {
            val centerLogin = result as ScanResult.RestaurantCenterLogin
            RestaurantCenterQrLoginConfirmation(
                challengeId = centerLogin.challengeId,
                challengeNonce = centerLogin.challengeNonce,
                onDismiss = { vm.dismissResult() },
            )
        }

        if (result is ScanResult.Found) {
        val found = result as ScanResult.Found
        ResultOverlay(
        data      = found.data,
        qrType    = found.type,
        context   = context,
        onDismiss = { vm.dismissResult() },
        onOpenUrl = onOpenUrl,
        )
        }
        }

        // - History -
    }
}

@Composable
private fun ScanFrameOverlay() {
    val scanLineAnim = rememberInfiniteTransition(label = "scan")
    val scanY by scanLineAnim.animateFloat(
        initialValue   = 0f, targetValue = 1f,
        animationSpec  = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label      = "y"
    )
    val frameSize = 260.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Box(Modifier.size(frameSize).align(Alignment.Center)) {
        val cLen = 32.dp; val cW = 3.dp; val cC = Primary
        // Corners
        Box(Modifier.size(cLen, cW).background(cC).align(Alignment.TopStart))
        Box(Modifier.size(cW, cLen).background(cC).align(Alignment.TopStart))
        Box(Modifier.size(cLen, cW).background(cC).align(Alignment.TopEnd))
        Box(Modifier.size(cW, cLen).background(cC).align(Alignment.TopEnd))
        Box(Modifier.size(cLen, cW).background(cC).align(Alignment.BottomStart))
        Box(Modifier.size(cW, cLen).background(cC).align(Alignment.BottomStart))
        Box(Modifier.size(cLen, cW).background(cC).align(Alignment.BottomEnd))
        Box(Modifier.size(cW, cLen).background(cC).align(Alignment.BottomEnd))
        // Scan line
        Box(
        Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 4.dp)
        .offset(y = frameSize * scanY)
        .background(
        Brush.horizontalGradient(
        listOf(Color.Transparent, Primary.copy(alpha = 0.8f), Color.Transparent)
        )
        )
        )
        }
    }
}

@Composable
private fun ScanToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (active) GreenBg else Color.White.copy(alpha = 0.12f))
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label,
        tint = if (active) Primary else Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) Primary else Color.White, fontSize = 12.sp,
        fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResultOverlay(
    data: String,
    qrType: String,
    context: Context,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val desktopLoginQr = remember(data) { isDesktopLoginQr(data) }

    if (desktopLoginQr) {
        DesktopQrLoginConfirmation(data = data, onDismiss = onDismiss)
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss)) {
        Card(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        shape  = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
        Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(GreenBg),
        contentAlignment = Alignment.Center) {
        Icon(typeIcon(qrType), null, tint = Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text(tr("QR Code Scanned"), fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground)
        Text(qrType.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = Primary)
        }
        IconButton(onClick = onDismiss) {
        Icon(Icons.Default.Close, "Close",
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        Spacer(Modifier.height(14.dp))
        Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
        ) {
        Text(data, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
        maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
        onClick = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(tr("QR"), data))
        },
        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
        ) {
        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(tr("Copy"))
        }
        if (qrType == "url") {
        Button(
        onClick = { onDismiss(); onOpenUrl(data) },
        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
        Icon(Icons.Default.OpenInBrowser, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(tr("Open"))
        }
        } else {
        Button(
        onClick = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, data)
        this.type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
        },
        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text(tr("Share"))
        }
        }
        }
        }
        }
    }
}


@Composable
private fun DesktopQrLoginConfirmation(data: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repository = remember { LinkedDevicesRepository() }
    var session by remember { mutableStateOf<DesktopQrMobileResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(data) {
        loading = true
        repository.scanDesktopQr(data)
        .onSuccess { session = it; message = null }
        .onFailure { message = CloudflareClient.friendlyErrorMessage(it, "QR code expired. Generate a new code.") }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)).clickable(enabled = !actionBusy, onClick = onDismiss)) {
        Card(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101722))
        ) {
        Column(Modifier.padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF163322)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Devices, null, tint = Primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        Text("Log in to AdisChat Desktop?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
        Text("Approve only if this QR is on your computer.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
        }
        }
        Spacer(Modifier.height(18.dp))
        if (loading) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        }
        } else {
        val device = session?.device
        SecurityConfirmRow("Device", device?.name ?: "Windows Desktop")
        SecurityConfirmRow("OS", device?.os ?: "Desktop")
        SecurityConfirmRow("Location", device?.location?.ifBlank { "Approximate location unavailable" } ?: "Approximate location unavailable")
        SecurityConfirmRow("Time", formatScanTime(System.currentTimeMillis()))
        message?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
        onClick = {
        actionBusy = true
        scope.launch {
        repository.rejectDesktopQr(data)
        actionBusy = false
        onDismiss()
        }
        },
        enabled = !actionBusy,
        modifier = Modifier.weight(1f).height(52.dp),
        shape = RoundedCornerShape(16.dp)
        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
        Button(
        onClick = {
        actionBusy = true
        scope.launch {
        repository.approveDesktopQr(data)
        .onSuccess { message = "Desktop connected"; kotlinx.coroutines.delay(700); onDismiss() }
        .onFailure { message = CloudflareClient.friendlyErrorMessage(it, "Could not link desktop") }
        actionBusy = false
        }
        },
        enabled = !loading && !actionBusy && session != null,
        modifier = Modifier.weight(1f).height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black)
        ) {
        if (actionBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
        else Text("Link Device", fontWeight = FontWeight.Black)
        }
        }
        }
        }
    }
}

@Composable
private fun SecurityConfirmRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun RestaurantCenterQrLoginConfirmation(
    challengeId: String,
    challengeNonce: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { RestaurantsRepository() }
    var actionBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(enabled = !actionBusy, onClick = onDismiss),
    ) {
        Card(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101722)),
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF163322)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Storefront, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Log in to Restaurant Center?",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                        )
                        Text(
                            "Approve only if you are signing in on your desktop computer.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                SecurityConfirmRow("Service", "Restaurant Center")
                SecurityConfirmRow("Platform", "Desktop Browser")
                SecurityConfirmRow("Challenge ID", challengeId.take(8) + "...")
                SecurityConfirmRow("Time", formatScanTime(System.currentTimeMillis()))

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        color = if (isSuccess) Primary else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            actionBusy = true
                            scope.launch {
                                repository.authorizeRestaurantCenterQr(challengeId, challengeNonce, cancel = true)
                                actionBusy = false
                                onDismiss()
                            }
                        },
                        enabled = !actionBusy,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            actionBusy = true
                            scope.launch {
                                repository.authorizeRestaurantCenterQr(challengeId, challengeNonce, cancel = false)
                                    .onSuccess {
                                        isSuccess = true
                                        message = "Sign-in authorized for Restaurant Center"
                                        kotlinx.coroutines.delay(700)
                                        onDismiss()
                                    }
                                    .onFailure { err ->
                                        isSuccess = false
                                        val errText = when (err) {
                                            is RestaurantsFailure.Forbidden ->
                                                "Only restaurant owners or managers can authorize desktop login."
                                            is RestaurantsFailure.Validation ->
                                                "QR code expired or invalid. Please refresh the desktop code."
                                            else ->
                                                err.message?.takeIf { it.isNotBlank() }
                                                    ?: "Unable to authorize desktop sign-in. Please try again."
                                        }
                                        message = errText
                                    }
                                actionBusy = false
                            }
                        },
                        enabled = !actionBusy && !isSuccess,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black),
                    ) {
                        if (actionBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Text("Authorize Sign-In", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private fun isDesktopLoginQr(data: String): Boolean = runCatching {
    val uri = Uri.parse(data)
    (uri.scheme == "adischat" && uri.host == "desktop-login") || data.startsWith("adischat-desktop-login:")
}.getOrDefault(false)

private fun formatScanTime(value: Long): String = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))
private fun typeIcon(qrType: String) = when (qrType) {
    "url"   -> Icons.Default.Link
    "phone" -> Icons.Default.Phone
    "email" -> Icons.Default.Email
    "wifi"  -> Icons.Default.Wifi
    else    -> Icons.Default.TextFields
}
