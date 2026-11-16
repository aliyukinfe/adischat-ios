package com.adischat.app.feature.shop.presentation

import com.adischat.app.feature.shop.data.ShopProductDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class VisualSearchSessionTest {
    private fun session(scope: CoroutineScope) = MarketplaceVisualSearchSession(scope, diagnostic = {})

    @Test fun catalogRetryRequiresImageAndOneRequestAtATime() {
        assertFalse(canStartCatalogRetry(false, false, false))
        assertFalse(canStartCatalogRetry(true, true, false))
        assertFalse(canStartCatalogRetry(true, false, true))
        assertTrue(canStartCatalogRetry(true, false, false))
    }

    @Test fun completedResultsSurviveReentryAndKeepFilters() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = session(scope)
        val product = ShopProductDto(id = "approved")
        session.startRequest(1) { listOf(product, product) }
        session.resultFilters = ShopHomeFilters(minimumRating = 4)
        session.begin(MarketplaceVisualSearchEntryMode.Image)
        assertEquals(MarketplaceVisualSearchPhase.Results, session.phase)
        assertEquals(listOf(product), session.results)
        assertEquals(4, session.resultFilters.minimumRating)
        assertFalse(session.requestInFlight)
        scope.cancel()
    }

    @Test fun duplicateTapDoesNotLaunchSecondJob() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = session(scope)
        val pending = CompletableDeferred<List<ShopProductDto>>()
        var calls = 0
        session.startRequest(1) { calls++; pending.await() }
        session.startRequest(1) { calls++; emptyList() }
        assertEquals(1, calls)
        assertTrue(session.requestInFlight)
        pending.complete(listOf(ShopProductDto(id = "one")))
        session.startRequest(1) { calls++; emptyList() }
        assertEquals(1, calls)
        assertEquals(MarketplaceVisualSearchPhase.Results, session.phase)
        scope.cancel()
    }

    @Test fun failureAndCancellationTerminateLoadingAndPermitRetry() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = session(scope)
        session.startRequest(1) { error("private backend diagnostic") }
        assertEquals(MarketplaceVisualSearchPhase.Error, session.phase)
        assertFalse(session.requestInFlight)
        assertFalse(session.message.orEmpty().contains("private"))
        session.retry()
        session.startRequest(1) { throw kotlinx.coroutines.CancellationException() }
        assertEquals(MarketplaceVisualSearchPhase.Preview, session.phase)
        assertFalse(session.requestInFlight)
        session.startRequest(1) { listOf(ShopProductDto(id = "success")) }
        assertEquals(MarketplaceVisualSearchPhase.Results, session.phase)
        scope.cancel()
    }

    @Test fun resetCancelsPreviousSearchWithoutOverwritingNextResults() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = session(scope)
        val old = CompletableDeferred<List<ShopProductDto>>()
        session.startRequest(1) { old.await() }
        session.reset()
        session.startRequest(2) { listOf(ShopProductDto(id = "new")) }
        old.complete(listOf(ShopProductDto(id = "old")))
        assertEquals(listOf("new"), session.results.map { it.id })
        assertFalse(session.requestInFlight)
        scope.cancel()
    }

    @Test fun noMatchesAreDistinctFromError() {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val session = session(scope)
        session.startRequest(1) { emptyList() }
        assertEquals(MarketplaceVisualSearchPhase.Empty, session.phase)
        assertNull(session.message)
        assertTrue(session.results.isEmpty())
        scope.cancel()
    }

    @Test fun timeoutIsRetryableError() = runBlocking {
        val session = session(this)
        session.startRequest(1) { withTimeout(1) { CompletableDeferred<List<ShopProductDto>>().await() } }
        coroutineContext[Job]!!.children.toList().forEach { it.join() }
        assertEquals(MarketplaceVisualSearchPhase.Error, session.phase)
        assertFalse(session.requestInFlight)
        assertNull(session.requestSignature)
    }

    private fun fingerprint(labels: Map<String, Float> = emptyMap(), hash: Long = 0x5555555555555555L, color: Float = .5f) =
        VisualFingerprint(labels, floatArrayOf(1f, 0f), hash, FloatArray(192) { color })

    @Test fun exactVisualMatchDoesNotRequireRecognizedLabels() {
        val image = fingerprint()
        assertTrue(visualSimilarity(image, image.copy()) >= VISUAL_MATCH_THRESHOLD)
    }

    @Test fun genericLabelsAndFlatImagesCannotCreateMatches() {
        val blank = fingerprint(mapOf("text" to .99f, "product" to .99f), 0L)
        assertEquals(0.0, visualSimilarity(blank, blank), .001)
    }

    @Test fun unrelatedShapesCannotMatchFromLabelsAlone() {
        val a = fingerprint(mapOf("shoe" to .99f))
        val b = a.copy(differenceHash = a.differenceHash.inv())
        assertEquals(0.0, visualSimilarity(a, b), .001)
    }
}
