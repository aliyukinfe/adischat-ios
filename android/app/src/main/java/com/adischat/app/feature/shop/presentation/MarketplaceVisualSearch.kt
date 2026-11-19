package com.adischat.app.feature.shop.presentation

import com.adischat.app.ui.home.AdaptiveFlowAround
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.adischat.app.feature.shop.data.ShopCategoryDto
import com.adischat.app.feature.shop.data.ShopProductDto
import com.adischat.app.ui.theme.AdisGebeyaColors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

enum class MarketplaceVisualSearchEntryMode { Image, Scan }

internal enum class MarketplaceVisualSearchPhase { Choose, Preview, Analyzing, Results, Empty, Error }

internal fun visualSearchRequestSignature(bitmap: Bitmap): Long = imageDifferenceHash(normalizeSearchBitmap(bitmap))

internal fun shouldStartVisualSearch(previousSignature: Long?, inFlight: Boolean, nextSignature: Long): Boolean =
    !inFlight && previousSignature != nextSignature

@Composable
internal fun MarketplaceVisualSearchScreen(
    entryMode: MarketplaceVisualSearchEntryMode,
    session: MarketplaceVisualSearchSession,
    products: List<ShopProductDto>,
    catalogLoading: Boolean,
    catalogError: Boolean,
    onRefreshCatalog: () -> Unit,
    categories: List<ShopCategoryDto>,
    savedIds: Set<String>,
    onBack: () -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    val context = LocalContext.current
    var showLiveCamera by remember { mutableStateOf(false) }
    var resultFilters by session::resultFilters
    var discoverySurface by remember { mutableStateOf<MarketplaceDiscoverySurface>(MarketplaceDiscoverySurface.None) }
    var cameraDenied by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            decodeSearchBitmap(context, it)?.let { bitmap ->
                // The picker may be launched from the live product camera. Close it first so
                // the selected image can render in the native preview and be searched.
                showLiveCamera = false
                session.select(bitmap)
            } ?: session.imageFailed()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        showLiveCamera = granted
    }
    fun requestLiveCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraDenied = false
            showLiveCamera = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(entryMode) {
        session.begin(entryMode)
        if (entryMode == MarketplaceVisualSearchEntryMode.Scan && session.sourceBitmap == null) requestLiveCamera()
    }
    DisposableEffect(session) {
        visualSearchDiagnostic("screen_attached phase=${session.phase}")
        onDispose { visualSearchDiagnostic("screen_disposed phase=${session.phase}") }
    }
    LaunchedEffect(session.phase) { visualSearchDiagnostic("screen_phase=${session.phase}") }
    LaunchedEffect(session.awaitingCatalog, catalogLoading, catalogError, products) {
        if (session.awaitingCatalog && !catalogLoading) {
            when {
                !catalogError && products.isNotEmpty() -> session.resumeWithCatalog(context, products)
                else -> session.catalogRefreshFailed()
            }
        }
    }

    if (showLiveCamera) {
        MarketplaceLiveProductCamera(
            onBack = { showLiveCamera = false; if (session.sourceBitmap == null) onBack() },
            onCaptured = { bitmap -> session.select(bitmap); showLiveCamera = false },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(AdisGebeyaColors.Background)) {
        VisualSearchTopBar(if (session.phase == MarketplaceVisualSearchPhase.Results) "Similar products" else "Search by image", onBack)
        when (session.phase) {
            MarketplaceVisualSearchPhase.Choose -> VisualSearchChooser(
                cameraDenied = cameraDenied,
                onCamera = ::requestLiveCamera,
                onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSettings = { context.startActivity(android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
            )
            MarketplaceVisualSearchPhase.Preview -> VisualSearchPreview(
                bitmap = session.sourceBitmap,
                onRetake = { session.reset(entryMode); requestLiveCamera() },
                onSearch = {
                    if (products.isEmpty() || catalogError) {
                        if (session.awaitCatalog()) onRefreshCatalog()
                    } else session.search(context, products)
                },
            )
            MarketplaceVisualSearchPhase.Analyzing -> {
                VisualSearchLoading(session.sourceBitmap)
            }
            MarketplaceVisualSearchPhase.Results -> {
                val visible = remember(session.results, resultFilters, categories) {
                    filterVisualResults(session.results, resultFilters, categories)
                }
                VisualSearchResults(
                    source = session.sourceBitmap,
                    products = visible,
                    savedIds = savedIds,
                    onChangeImage = { session.reset(entryMode) },
                    onClearFilters = { resultFilters = ShopHomeFilters() },
                    onDiscovery = { discoverySurface = it },
                    onProduct = onProduct,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            MarketplaceVisualSearchPhase.Empty -> VisualSearchMessage("We couldn't find similar products.", "Try another image") { session.reset(entryMode) }
            MarketplaceVisualSearchPhase.Error -> VisualSearchMessage(session.message ?: "Couldn't search right now.", "Retry") {
                if (session.sourceBitmap != null && (products.isEmpty() || catalogError)) {
                    if (session.awaitCatalog()) onRefreshCatalog()
                } else {
                    session.retry()
                    if (session.sourceBitmap != null) session.search(context, products)
                }
            }
        }
    }

    MarketplaceDiscoverySurfaceHost(
        surface = discoverySurface,
        filters = resultFilters,
        categories = categories,
        attributes = emptyList(),
        products = session.results,
        productDetails = emptyMap(),
        loadingAttributes = false,
        onDismiss = { discoverySurface = MarketplaceDiscoverySurface.None },
        onApply = { resultFilters = it; discoverySurface = MarketplaceDiscoverySurface.None },
    )
}

@Composable
private fun VisualSearchTopBar(title: String, onBack: () -> Unit) {
    val sidebar = com.adischat.app.ui.home.LocalSidebarBounds.current
    val startPad = if (sidebar != null) 60.dp else 8.dp
    Surface(color = AdisGebeyaColors.Surface, border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(start = startPad, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AdisGebeyaColors.PrimaryText) }
            Text(title, color = AdisGebeyaColors.PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VisualSearchChooser(cameraDenied: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).background(AdisGebeyaColors.PrimaryLight, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ImageSearch, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Find products from a photo", color = AdisGebeyaColors.PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text("Use a clear image of the product you want to find.", color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Take a photo")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Choose from gallery")
        }
        if (cameraDenied) {
            Text("Camera access is needed to search with a photo.", color = AdisGebeyaColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            OutlinedButton(onClick = onSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun VisualSearchPreview(bitmap: Bitmap?, onRetake: () -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Selected image", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(20.dp), color = AdisGebeyaColors.SecondarySurface, border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
            bitmap?.let { Image(it.asImageBitmap(), "Product search image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetake, Modifier.weight(1f).height(52.dp)) { Text("Retake") }
            Button(onClick = onSearch, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary)) {
                Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Search")
            }
        }
    }
}

@Composable
private fun VisualSearchLoading(bitmap: Bitmap?) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(112.dp), shape = RoundedCornerShape(20.dp), color = AdisGebeyaColors.SecondarySurface) {
            bitmap?.let { Image(it.asImageBitmap(), "Search source", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Finding similar products...", color = AdisGebeyaColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Comparing visual details with the AdisGebeya catalog", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun VisualSearchResults(
    source: Bitmap?,
    products: List<ShopProductDto>,
    savedIds: Set<String>,
    onChangeImage: () -> Unit,
    onClearFilters: () -> Unit,
    onDiscovery: (MarketplaceDiscoverySurface) -> Unit,
    onProduct: (ShopProductDto) -> Unit,
    onToggleFavorite: (ShopProductDto) -> Unit,
) {
    AdaptiveFlowAround(besideStart = 60.dp, belowStart = 8.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(54.dp), shape = RoundedCornerShape(12.dp), color = AdisGebeyaColors.SecondarySurface) {
                    source?.let { Image(it.asImageBitmap(), "Search source", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Visual search", color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                    Text("${products.size} matching products", color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onChangeImage) { Text("Change image") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { VisualControl("Categories") { onDiscovery(MarketplaceDiscoverySurface.Categories) } }
                item { VisualControl("Filter") { onDiscovery(MarketplaceDiscoverySurface.Filters) } }
                item { VisualControl("Sort") { onDiscovery(MarketplaceDiscoverySurface.Sort) } }
                item { VisualControl("Price") { onDiscovery(MarketplaceDiscoverySurface.Price) } }
                item { VisualControl("Rating") { onDiscovery(MarketplaceDiscoverySurface.Rating) } }
            }
            if (products.isEmpty()) {
                VisualSearchMessage("No products match your filters", "Clear filters", onClearFilters)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(products, key = { it.id }) { product -> VisualResultCard(product, product.id in savedIds, { onProduct(product) }, { onToggleFavorite(product) }) }
                }
            }
        }
    }
}

@Composable
private fun VisualControl(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = AdisGebeyaColors.Surface, border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = AdisGebeyaColors.PrimaryText, fontSize = 12.sp)
    }
}

@Composable
private fun VisualResultCard(product: ShopProductDto, saved: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface), border = BorderStroke(1.dp, AdisGebeyaColors.Border)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(AdisGebeyaColors.SecondarySurface)) {
                AsyncImage(model = product.visualSearchImageUrl(), contentDescription = product.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).size(44.dp).background(AdisGebeyaColors.Surface.copy(alpha = .9f), CircleShape)) {
                    Icon(if (saved) Icons.Default.Star else Icons.Default.Star, contentDescription = if (saved) "Remove from favorites" else "Add to favorites", tint = if (saved) AdisGebeyaColors.AccentOrange else AdisGebeyaColors.SecondaryText)
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(product.title, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                product.brand?.takeIf(String::isNotBlank)?.let { Text(it, color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp, maxLines = 1) }
                Text(formatEtb(product.priceMinor), color = AdisGebeyaColors.Primary, fontWeight = FontWeight.SemiBold)
                product.averageRating?.let { Text("$it rating", color = AdisGebeyaColors.SecondaryText, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun VisualSearchMessage(message: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = AdisGebeyaColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 14.dp)) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(action) }
    }
}

@Composable
private fun MarketplaceLiveProductCamera(onBack: () -> Unit, onCaptured: (Bitmap) -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(lifecycleOwner) { onDispose { provider?.unbindAll() } }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        runCatching {
                            val cameraProvider = future.get()
                            provider = cameraProvider
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            capture = imageCapture
                        }.onFailure { error = "Camera could not start." }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .45f), CircleShape)) {
            Icon(Icons.Default.Close, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.White)
        }
        Box(Modifier.align(Alignment.Center).fillMaxWidth(.78f).aspectRatio(1f).border(2.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .8f), RoundedCornerShape(28.dp)))
        Text("Align product inside frame", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center).padding(top = 380.dp), fontWeight = FontWeight.Medium)
        error?.let { Text(it, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center)) }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            IconButton(onClick = onGallery, modifier = Modifier.size(54.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .2f), CircleShape)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose from gallery", tint = androidx.compose.ui.graphics.Color.White)
            }
            IconButton(
                onClick = {
                    val imageCapture = capture ?: return@IconButton
                    val file = File.createTempFile("visual-search-", ".jpg", context.cacheDir)
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                BitmapFactory.decodeFile(file.absolutePath)?.let(onCaptured)
                                file.delete()
                            }
                            override fun onError(exception: ImageCaptureException) { error = "Photo could not be captured."; file.delete() }
                        },
                    )
                },
                modifier = Modifier.size(76.dp).background(AdisGebeyaColors.AccentOrange, CircleShape).border(5.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
            ) { Icon(Icons.Default.CameraAlt, contentDescription = "Capture product", tint = AdisGebeyaColors.Primary, modifier = Modifier.size(30.dp)) }
        }
    }
}

internal object MarketplaceVisualSimilarityEngine {
    private val cache = ConcurrentHashMap<String, VisualFingerprint>()

    suspend fun rank(context: Context, source: Bitmap, products: List<ShopProductDto>): List<ShopProductDto> = withContext(Dispatchers.IO) {
        check(products.isNotEmpty()) { "Catalog unavailable" }
        if (cache.size > 256) cache.clear()
        val started = android.os.SystemClock.elapsedRealtime()
        val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val query = fingerprint(source, client)
            val eligible = products.distinctBy { it.id }.filter { it.id.isNotBlank() && !it.visualSearchImageUrl().isNullOrBlank() }.take(100)
            visualSearchDiagnostic("comparison_start candidates=${eligible.size}")
            val scored = mutableListOf<Pair<ShopProductDto, Double>>()
            var loadedProducts = 0
            for (batch in eligible.chunked(3)) {
                ensureActive()
                val compared = coroutineScope {
                    batch.map { product -> async {
                        var bestScore: Double? = null
                        // Deleted media rows can precede valid photos; do not cut off the
                        // matching photo after a few failed URLs. Seller media is capped at ten.
                        for (url in product.visualSearchImageUrls().take(10)) {
                            ensureActive()
                            val candidate = cache[url] ?: withTimeoutOrNull(6_000L) {
                                loadBitmap(context, url)?.let { fingerprint(it, client) }
                            }?.also { cache[url] = it } ?: continue
                            val score = visualSimilarity(query, candidate)
                            bestScore = maxOf(bestScore ?: 0.0, score)
                            if (bestScore >= .98) break
                        }
                        product to bestScore
                    } }.awaitAll()
                }
                for ((product, score) in compared) if (score != null) {
                    loadedProducts++
                    scored += product to score
                }
            }
            visualSearchDiagnostic("comparison_complete loaded=$loadedProducts unavailable=${eligible.size - loadedProducts} elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}")
            check(eligible.isEmpty() || loadedProducts > 0) { "Catalog images unavailable" }
            val strongest = scored.maxOfOrNull { it.second } ?: 0.0
            val cutoff = if (strongest >= .94) maxOf(VISUAL_MATCH_THRESHOLD, strongest - .10) else VISUAL_MATCH_THRESHOLD
            scored.filter { it.second >= cutoff }.sortedByDescending { it.second }.take(30).map { it.first }
        } finally {
            client.close()
        }
    }

    private suspend fun fingerprint(bitmap: Bitmap, client: ImageLabeler): VisualFingerprint {
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 320f / maxOf(bitmap.width, bitmap.height)).toInt().coerceAtLeast(1), (bitmap.height * 320f / maxOf(bitmap.width, bitmap.height)).toInt().coerceAtLeast(1), true)
        return VisualFingerprint(label(scaled, client), colorHistogram(scaled), imageDifferenceHash(scaled), spatialColors(scaled))
    }

    private suspend fun label(bitmap: Bitmap, client: ImageLabeler): Map<String, Float> = suspendCancellableCoroutine { continuation ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                if (continuation.isActive) continuation.resume(labels.filter { it.confidence >= .5f }.associate { it.text.lowercase() to it.confidence })
            }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(320).build()
        val result = context.imageLoader.execute(request)
        return (result as? SuccessResult)?.drawable?.toBitmap()
    }
}

private fun spatialColors(bitmap: Bitmap): FloatArray {
    val sample = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
    val colors = FloatArray(8 * 8 * 3)
    for (y in 0 until 8) for (x in 0 until 8) {
        val pixel = sample.getPixel(x, y)
        val index = (y * 8 + x) * 3
        colors[index] = Color.red(pixel) / 255f
        colors[index + 1] = Color.green(pixel) / 255f
        colors[index + 2] = Color.blue(pixel) / 255f
    }
    return colors
}

internal fun normalizeSearchBitmap(bitmap: Bitmap): Bitmap {
    val max = 640
    if (bitmap.width <= max && bitmap.height <= max) return bitmap
    val scale = max.toFloat() / maxOf(bitmap.width, bitmap.height)
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
}

private fun decodeSearchBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1280) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.let(::normalizeSearchBitmap)
}.getOrNull()

private fun ShopProductDto.visualSearchImageUrls(): List<String> =
    images.orEmpty().sortedWith(compareByDescending<com.adischat.app.feature.shop.data.ShopProductImageDto> { it.isPrimary == true }.thenBy { it.displayOrder ?: Int.MAX_VALUE }).mapNotNull { it.imageUrl?.takeIf(String::isNotBlank) }.distinct()

private fun ShopProductDto.visualSearchImageUrl(): String? = visualSearchImageUrls().firstOrNull()

private fun colorHistogram(bitmap: Bitmap): FloatArray {
    val sample = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    val histogram = FloatArray(18)
    val hsv = FloatArray(3)
    for (y in 0 until sample.height step 2) for (x in 0 until sample.width step 2) {
        Color.colorToHSV(sample.getPixel(x, y), hsv)
        val hue = ((hsv[0] / 360f) * 12).toInt().coerceIn(0, 11)
        histogram[hue]++
        histogram[12 + (hsv[1] * 3).toInt().coerceIn(0, 2)]++
        histogram[15 + (hsv[2] * 3).toInt().coerceIn(0, 2)]++
    }
    val magnitude = sqrt(histogram.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1f)
    for (index in histogram.indices) histogram[index] /= magnitude
    return histogram
}

private fun imageDifferenceHash(bitmap: Bitmap): Long {
    val sample = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
    var hash = 0L
    var bit = 0
    for (y in 0 until 8) for (x in 0 until 8) {
        val left = sample.getPixel(x, y)
        val right = sample.getPixel(x + 1, y)
        val leftLight = Color.red(left) * 3 + Color.green(left) * 6 + Color.blue(left)
        val rightLight = Color.red(right) * 3 + Color.green(right) * 6 + Color.blue(right)
        if (leftLight > rightLight) hash = hash or (1L shl bit)
        bit++
    }
    return hash
}

internal fun labelCosine(a: Map<String, Float>, b: Map<String, Float>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val dot = a.entries.sumOf { (label, confidence) -> confidence.toDouble() * (b[label] ?: 0f) }
    val ma = sqrt(a.values.sumOf { (it * it).toDouble() })
    val mb = sqrt(b.values.sumOf { (it * it).toDouble() })
    return if (ma == 0.0 || mb == 0.0) 0.0 else dot / (ma * mb)
}

internal fun vectorCosine(a: FloatArray, b: FloatArray): Double {
    var dot = 0.0
    var ma = 0.0
    var mb = 0.0
    for (index in a.indices) { dot += a[index] * b[index]; ma += a[index] * a[index]; mb += b[index] * b[index] }
    return if (ma == 0.0 || mb == 0.0) 0.0 else dot / sqrt(ma * mb)
}

internal fun filterVisualResults(products: List<ShopProductDto>, filters: ShopHomeFilters, categories: List<ShopCategoryDto>): List<ShopProductDto> {
    val descendants = marketplaceCategoryAndDescendantSlugs(categories, filters.categorySlug)
    val minimum = filters.minimumPriceEtb.toIntOrNull()?.times(100)
    val maximum = filters.maximumPriceEtb.toIntOrNull()?.times(100)
    val filtered = products.filter { product ->
        (filters.categorySlug == null || product.categorySlug in descendants) &&
            (minimum == null || product.priceMinor >= minimum) &&
            (maximum == null || product.priceMinor <= maximum) &&
            (filters.minimumRating <= 0 || (product.averageRating ?: 0.0) >= filters.minimumRating) &&
            (!filters.inStockOnly || product.stockStatus?.contains("out", true) != true) &&
            (!filters.discountedOnly || homePreviousPriceMinor(product) != null)
    }
    return when (filters.sort) {
        MarketplaceSort.PriceAscending.label -> filtered.sortedBy { it.priceMinor }
        MarketplaceSort.PriceDescending.label -> filtered.sortedByDescending { it.priceMinor }
        MarketplaceSort.RatingDescending.label -> filtered.sortedByDescending { it.averageRating ?: 0.0 }
        else -> filtered
    }
}
