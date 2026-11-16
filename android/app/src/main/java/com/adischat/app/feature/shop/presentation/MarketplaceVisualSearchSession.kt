package com.adischat.app.feature.shop.presentation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adischat.app.BuildConfig
import com.adischat.app.feature.shop.data.ShopProductDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun visualSearchDiagnostic(event: String) {
    if (BuildConfig.DEBUG) Log.d("GebeyaVisualSearch", event)
}

internal fun canStartCatalogRetry(hasImage: Boolean, awaitingCatalog: Boolean, requestInFlight: Boolean): Boolean =
    hasImage && !awaitingCatalog && !requestInFlight

// Owned by ShopViewModel, not by a conditional loading composable.
internal class MarketplaceVisualSearchSession(
    private val scope: CoroutineScope,
    private val diagnostic: (String) -> Unit = ::visualSearchDiagnostic,
) {
    var entryMode by mutableStateOf(MarketplaceVisualSearchEntryMode.Image)
        private set
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var phase by mutableStateOf(MarketplaceVisualSearchPhase.Choose)
        private set
    var results by mutableStateOf<List<ShopProductDto>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var resultFilters by mutableStateOf(ShopHomeFilters())
    var requestSignature: Long? = null
        private set
    var requestInFlight = false
        private set
    var awaitingCatalog by mutableStateOf(false)
        private set
    private var generation = 0L
    private var request: Job? = null

    fun begin(mode: MarketplaceVisualSearchEntryMode) {
        if (entryMode != mode) reset(mode)
    }

    fun select(bitmap: Bitmap) {
        reset(entryMode)
        sourceBitmap = normalizeSearchBitmap(bitmap)
        phase = MarketplaceVisualSearchPhase.Preview
        diagnostic("image_selected phase=Preview")
    }

    fun imageFailed() {
        reset(entryMode)
        message = "This image couldn't be opened. Please choose another photo."
        phase = MarketplaceVisualSearchPhase.Error
    }

    fun search(context: Context, products: List<ShopProductDto>) {
        val source = sourceBitmap ?: return
        val catalog = products.toList()
        startRequest(visualSearchRequestSignature(source)) {
            withTimeout(90_000L) {
                MarketplaceVisualSimilarityEngine.rank(context.applicationContext, source, catalog)
            }
        }
    }

    internal fun startRequest(signature: Long, work: suspend () -> List<ShopProductDto>) {
        if (!shouldStartVisualSearch(requestSignature, requestInFlight, signature)) {
            diagnostic("request_skipped duplicate=true")
            return
        }
        val current = ++generation
        requestSignature = signature
        requestInFlight = true
        message = null
        phase = MarketplaceVisualSearchPhase.Analyzing
        diagnostic("request_start sequence=$current")
        request = scope.launch {
            try {
                val matches = work()
                if (generation == current) {
                    results = matches.distinctBy { it.id }
                    phase = if (results.isEmpty()) MarketplaceVisualSearchPhase.Empty else MarketplaceVisualSearchPhase.Results
                    diagnostic("request_complete sequence=$current phase=$phase count=${results.size}")
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == current) fail("Search took too long. Please try again.")
            } catch (cancelled: CancellationException) {
                if (generation == current) {
                    phase = MarketplaceVisualSearchPhase.Preview
                    requestSignature = null
                    diagnostic("request_cancelled sequence=$current")
                }
                throw cancelled
            } catch (_: Exception) {
                if (generation == current) fail("Couldn't search right now. Check your connection and try again.")
            } finally {
                if (generation == current) requestInFlight = false
            }
        }
    }

    private fun fail(text: String) {
        message = text
        requestSignature = null
        phase = MarketplaceVisualSearchPhase.Error
        diagnostic("request_failed phase=Error")
    }

    fun retry() {
        requestSignature = null
        phase = if (sourceBitmap == null) MarketplaceVisualSearchPhase.Choose else MarketplaceVisualSearchPhase.Preview
    }

    fun awaitCatalog(): Boolean {
        if (!canStartCatalogRetry(sourceBitmap != null, awaitingCatalog, requestInFlight)) return false
        awaitingCatalog = true
        message = null
        phase = MarketplaceVisualSearchPhase.Analyzing
        diagnostic("catalog_refresh_start")
        return true
    }

    fun resumeWithCatalog(context: Context, products: List<ShopProductDto>) {
        if (!awaitingCatalog) return
        awaitingCatalog = false
        phase = MarketplaceVisualSearchPhase.Preview
        search(context, products)
    }

    fun catalogRefreshFailed() {
        if (!awaitingCatalog) return
        awaitingCatalog = false
        fail("Couldn't load products. Check your connection and try again.")
    }

    fun reset(mode: MarketplaceVisualSearchEntryMode = entryMode) {
        ++generation
        request?.cancel()
        request = null
        entryMode = mode
        sourceBitmap = null
        results = emptyList()
        resultFilters = ShopHomeFilters()
        message = null
        phase = MarketplaceVisualSearchPhase.Choose
        requestSignature = null
        requestInFlight = false
        awaitingCatalog = false
        diagnostic("session_reset")
    }
}
