package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

class ShopPublicContentReviewContractTest {
    @Test
    fun reviewRoutesUseExistingAdminFunction() {
        val methods = ShopFunctionApi::class.java.declaredMethods.associateBy { it.name }
        assertEquals("functions/v1/adishop-admin-review-product?action=public-content-queue",
            methods.getValue("adminPublicContentQueue").getAnnotation(GET::class.java).value)
        assertEquals("functions/v1/adishop-admin-review-product?action=public-content-preview",
            methods.getValue("adminPublicContentPreview").getAnnotation(GET::class.java).value)
        assertEquals("functions/v1/adishop-admin-review-product?action=public-content-review",
            methods.getValue("adminPublicContentDecision").getAnnotation(POST::class.java).value)
        assertTrue(methods.getValue("adminPublicContentQueue").parameterAnnotations.any { annotations ->
            annotations.any { it is Query && it.value == "offset" }
        })
    }

    @Test
    fun decisionCarriesExactTargetAndRevisionWithoutActorOverride() {
        val json = Gson().toJson(ShopPublicContentDecisionRequest(
            kind = "product", id = "20000000-0000-4000-8000-000000000001",
            expectedRevision = 7, decision = "rejected",
        ))
        assertTrue(json.contains("\"expectedRevision\":7"))
        assertTrue(json.contains("\"decision\":\"rejected\""))
        assertFalse(json.contains("actor"))
        assertFalse(json.contains("storeId"))
    }

    @Test
    fun responseFieldsDecodeAndAdminResetClearsPrivatePreview() {
        val envelope = Gson().fromJson(
            """{"reviewQueue":{"total":1,"items":[{"entityKind":"image","entityId":"i","revision":4,"targetId":"p"}]},"reviewedRevision":4}""",
            ShopEnvelope::class.java,
        )
        assertEquals(1, envelope.reviewQueue?.total)
        assertEquals(4L, envelope.reviewQueue?.items?.single()?.revision)
        assertEquals(4L, envelope.reviewedRevision)
        val state = ShopProtectedState(publicContentQueue = envelope.reviewQueue,
            publicContentPreview = ShopPublicContentPreviewDto(entityId = "i", revision = 4))
        val cleared = ShopStateReducers.clearAdminWorkspace(state)
        assertNull(cleared.publicContentQueue)
        assertNull(cleared.publicContentPreview)
    }

    @Test
    fun productionTabRemainsDisabledAndPageSizeMatchesNavigation() {
        assertFalse(ShopPublicContentReviewFlags.enabled)
        assertEquals(20, ShopPublicContentReviewFlags.pageSize)
    }
}
