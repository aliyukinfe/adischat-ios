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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adischat.app.data.repository.WalletRepository
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.data.preferences.WalletState
import com.adischat.app.network.CloudflareClient
import com.adischat.app.ui.theme.*
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    prefs: AppPreferences,
    onOpenUrl: (String) -> Unit = {},
    onGoWallet: () -> Unit = {},
) {
    val vm: ScanViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(prefs) as T
    })

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> ScanContent(vm, prefs, onOpenUrl, onGoWallet)
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
        Text("Camera Permission Needed", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isPermanentlyDenied)
                "Camera access was denied. Please enable it in Settings to scan QR codes."
            else
                "AdisChat needs camera access to scan QR codes for payments.",
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
    prefs: AppPreferences,
    onOpenUrl: (String) -> Unit,
    onGoWallet: () -> Unit,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val result         by vm.result.collectAsStateWithLifecycle()
    val torchOn        by vm.torchOn.collectAsStateWithLifecycle()
    val walletState    by prefs.walletStateFlow.collectAsStateWithLifecycle(WalletState())

    var camera: Camera? by remember { mutableStateOf(null) }
    val cameraExecutor  = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner  = remember { BarcodeScanning.getClient() }
    val isMerchantScanner = walletState.active && isMerchantScannerWallet(walletState.username)
    var merchantAmountInput by remember { mutableStateOf("") }
    var merchantCollectAmount by remember { mutableStateOf<Double?>(null) }
    var merchantAmountError by remember { mutableStateOf<String?>(null) }

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

        // ── Camera view ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().weight(1f).statusBarsPadding()) {

            if (isMerchantScanner && merchantCollectAmount == null) {
                MerchantAmountBeforeScan(
                    amount = merchantAmountInput,
                    error = merchantAmountError,
                    onAmountChange = { value ->
                        merchantAmountInput = value.filter { it.isDigit() || it == '.' }.take(12)
                        merchantAmountError = null
                    },
                    onNext = {
                        val cleanAmount = merchantAmountInput.toDoubleOrNull() ?: 0.0
                        if (cleanAmount <= 0.0) {
                            merchantAmountError = "Enter a valid amount first"
                        } else {
                            merchantCollectAmount = cleanAmount
                        }
                    },
                )
            } else {
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
            }

            // Scan result overlay
            if (result is ScanResult.Found) {
                val found = result as ScanResult.Found
                ResultOverlay(
                    data      = found.data,
                    qrType    = found.type,
                    context   = context,
                    onDismiss = { vm.dismissResult() },
                    onOpenUrl = onOpenUrl,
                    merchantCollectAmount = merchantCollectAmount,
                    onPaymentSuccess = {
                        merchantCollectAmount = null
                        merchantAmountInput = ""
                        merchantAmountError = null
                        vm.dismissResult()
                        onGoWallet()
                    },
                )
            }
        }

        // ── History ──────────────────────────────────────────────────────────
    }
}

@Composable
private fun MerchantAmountBeforeScan(
    amount: String,
    error: String?,
    onAmountChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(GreenBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = Primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Collect wallet payment",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter the amount first. Then scan the customer's 16-digit pay code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount ETB") },
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = Primary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                isError = error != null,
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Next: Start Scan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanFrameOverlay() {
    val scanLineAnim = rememberInfiniteTransition(label = "scan")
    val scanY by scanLineAnim.animateFloat(
        initialValue   = 0f, targetValue = 1f,
        animationSpec  = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label          = "y"
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
    merchantCollectAmount: Double? = null,
    onPaymentSuccess: () -> Unit = {},
) {
    val merchantPayCode = remember(data) { merchantPayCodeFrom(data) }
    val walletTarget = remember(data) { walletTargetFrom(data) }
    val scope = rememberCoroutineScope()
    val walletRepository = remember { WalletRepository() }
    var amount by remember { mutableStateOf(walletTarget?.amount?.takeIf { it > 0.0 }?.let { "%.2f".format(it) } ?: "") }
    var pin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var merchantMessage by remember { mutableStateOf<String?>(null) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var merchantAutoStarted by remember { mutableStateOf(false) }

    LaunchedEffect(merchantPayCode, merchantCollectAmount) {
        val amountToCollect = merchantCollectAmount
        if (merchantPayCode != null && amountToCollect != null && !merchantAutoStarted) {
            merchantAutoStarted = true
            loading = true
            merchantMessage = "Collecting ETB ${"%.2f".format(amountToCollect)}..."
            walletRepository.redeemMerchantPayCode(merchantPayCode, amountToCollect)
                .onSuccess {
                    merchantMessage = "Payment successful"
                    delay(1200)
                    onPaymentSuccess()
                }
                .onFailure {
                    merchantMessage = CloudflareClient.friendlyErrorMessage(it, "Payment failed")
                }
            loading = false
        }
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
                        Text("QR Code Scanned", fontWeight = FontWeight.Bold,
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
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                if (merchantPayCode != null) {
                    Text(
                        "Merchant collection",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (merchantCollectAmount != null) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(GreenBg).padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (merchantMessage == "Payment successful") {
                                    Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(24.dp))
                                } else {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Primary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (merchantMessage == "Payment successful")
                                        "Payment successful"
                                    else
                                        "Collecting ETB ${"%.2f".format(merchantCollectAmount)}",
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { value ->
                                amount = value.filter { it.isDigit() || it == '.' }.take(12)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Amount") },
                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = Primary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    merchantMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = if (it.startsWith("Collected") || it == "Payment successful" || it.startsWith("Collecting")) Primary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (merchantCollectAmount == null) {
                        Button(
                            onClick = {
                                val cleanAmount = amount.toDoubleOrNull() ?: 0.0
                                if (cleanAmount <= 0.0) {
                                    merchantMessage = "Enter a valid amount"
                                    return@Button
                                }
                                loading = true
                                merchantMessage = null
                                scope.launch {
                                    walletRepository.redeemMerchantPayCode(merchantPayCode, cleanAmount)
                                        .onSuccess {
                                            merchantMessage = "Collected ${"%.2f".format(cleanAmount)} ETB"
                                        }
                                        .onFailure {
                                            merchantMessage = CloudflareClient.friendlyErrorMessage(it, "Payment failed")
                                        }
                                    loading = false
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            } else {
                                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Collect payment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else if (walletTarget != null) {
                    Text(
                        "Wallet transfer",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "To ${walletTarget.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> amount = value.filter { it.isDigit() || it == '.' }.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = Primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value -> pin = value.filter { it.isDigit() }.take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Transaction PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    transferMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = if (it.startsWith("Sent")) Primary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val cleanAmount = amount.toDoubleOrNull() ?: 0.0
                            if (cleanAmount <= 0.0) {
                                transferMessage = "Enter a valid amount"
                                return@Button
                            }
                            if (pin.length != 6) {
                                transferMessage = "Enter your 6 digit wallet PIN"
                                return@Button
                            }
                            loading = true
                            transferMessage = null
                            scope.launch {
                                walletRepository.send(walletTarget.recipient, cleanAmount, pin)
                                    .onSuccess {
                                        transferMessage = "Sent ${"%.2f".format(cleanAmount)} ETB"
                                    }
                                    .onFailure {
                                        transferMessage = CloudflareClient.friendlyErrorMessage(it, "Transfer failed")
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send transfer", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("QR", data))
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Copy")
                    }
                    if (qrType == "url" && merchantPayCode == null && walletTarget == null) {
                        Button(
                            onClick = { onDismiss(); onOpenUrl(data) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("Open")
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
                            Spacer(Modifier.width(6.dp)); Text("Share")
                        }
                    }
                }
            }
        }
    }
}

private fun merchantPayCodeFrom(data: String): String? {
    val uriCode = runCatching {
        val uri = Uri.parse(data)
        if (uri.scheme == "adischat" && uri.host == "merchant-pay") {
            uri.getQueryParameter("code")
        } else {
            null
        }
    }.getOrNull()

    val digits = (uriCode ?: data).filter { it.isDigit() }
    return digits.takeIf { it.length == 16 }
}

private fun isMerchantScannerWallet(username: String): Boolean {
    return username.trim().lowercase() in setOf("merchant_amrogn_chicken", "akab_biratu")
}

private data class WalletScanTarget(
    val recipient: String,
    val label: String,
    val amount: Double?,
)

private fun walletTargetFrom(data: String): WalletScanTarget? {
    return runCatching {
        val uri = Uri.parse(data)
        if (uri.scheme != "adischat" || uri.host != "wallet") return@runCatching null
        val walletId = uri.getQueryParameter("walletId").orEmpty()
        val username = uri.getQueryParameter("username").orEmpty()
        val recipient = walletId.ifBlank { username }
        if (recipient.isBlank()) return@runCatching null
        WalletScanTarget(
            recipient = recipient,
            label = if (username.isNotBlank()) "@$username" else walletId,
            amount = uri.getQueryParameter("amount")?.toDoubleOrNull(),
        )
    }.getOrNull()
}

private fun typeIcon(qrType: String) = when (qrType) {
    "url"   -> Icons.Default.Link
    "phone" -> Icons.Default.Phone
    "email" -> Icons.Default.Email
    "wifi"  -> Icons.Default.Wifi
    else    -> Icons.Default.TextFields
}
