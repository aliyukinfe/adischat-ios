package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.Header
import retrofit2.http.GET
import com.google.gson.JsonParser
import retrofit2.http.POST

class ShopQaModerationContractTest {
    private val gson = Gson()

    @Test fun routeAndIdempotencyHeaderAreIsolated() {
        val method = ShopFunctionApi::class.java.declaredMethods.single { it.name == "qaModerationDecision" }
        assertEquals("functions/v1/adishop-qa-moderation", method.getAnnotation(POST::class.java).value)
        assertTrue(method.parameterAnnotations[1].any { it is Header && it.value == "Idempotency-Key" })
        assertNotEquals(ShopQaModerationContract.newIdempotencyKey(), ShopQaModerationContract.newIdempotencyKey())
        assertFalse(ShopPublicContentReviewFlags.enabled)
    }

    @Test fun legacyQueueAndPreviewCannotSupplyQaRevisionContract() {
        val methods = ShopFunctionApi::class.java.declaredMethods.associateBy { it.name }
        assertEquals("functions/v1/adishop-admin-review-product?action=public-content-queue",
            methods.getValue("adminPublicContentQueue").getAnnotation(GET::class.java).value)
        assertEquals("functions/v1/adishop-admin-review-product?action=public-content-preview",
            methods.getValue("adminPublicContentPreview").getAnnotation(GET::class.java).value)
        val preview = gson.fromJson("""{"entityKind":"product","entityId":"20000000-0000-4000-8000-000000000001","revision":7,"content":{"title":"Laptop"}}""",
            ShopPublicContentPreviewDto::class.java)
        assertEquals(7L, preview.revision)
        assertThrows(IllegalArgumentException::class.java) {
            ShopQaModerationRequest(preview.entityKind, preview.entityId, "approve",
                preview.revision, publicTitle = preview.content["title"] as String)
        }
        assertFalse(ShopPublicContentReviewFlags.enabled)
    }

    @Test fun revisionsSerializeByEntityKindWithoutActorClaims() {
        val id = "20000000-0000-4000-8000-000000000001"
        val product = gson.toJson(ShopQaModerationRequest("product", id, "approve",
            "2026-11-14T12:00:00Z", publicTitle = "Reviewed laptop"))
        val image = gson.toJson(ShopQaModerationRequest("image", id, "reject", 4))
        assertTrue(product.contains("\"expectedRevision\":\"2026-11-14T12:00:00Z\""))
        assertTrue(image.contains("\"expectedRevision\":4"))
        assertFalse(product.contains("actor"))
        assertFalse(product.contains("sessionJti"))
        assertThrows(IllegalArgumentException::class.java) {
            ShopQaModerationRequest("store", id, "approve", 4, publicName = "Shop", publicCity = "Addis")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShopQaModerationRequest("image", id, "revoke", 4)
        }
    }

    @Test fun parsesEnvelopeAndKeepsErrorsSafe() {
        val response = gson.fromJson("""{"ok":true,"review":{"decision":"approve","revision":4}}""",
            ShopQaModerationResponse::class.java)
        assertEquals(4, ShopQaModerationContract.requireSuccess(response).revision?.asInt)
        val timestamp = gson.fromJson("""{"ok":true,"review":{"decision":"approve","revision":"2026-11-14T12:00:00+00:00"}}""",
            ShopQaModerationResponse::class.java)
        assertTrue(ShopQaModerationContract.requireSuccess(timestamp).revision?.asString!!.startsWith("2026"))
        val denied = gson.fromJson("""{"ok":false,"error":"forbidden"}""", ShopQaModerationResponse::class.java)
        assertThrows(IllegalArgumentException::class.java) { ShopQaModerationContract.requireSuccess(denied) }
        assertTrue(ShopQaModerationContract.errorMessage(403, "forbidden").contains("not authorized"))
        assertTrue(ShopQaModerationContract.errorMessage(409, "stale-revision").contains("Refresh"))
        assertEquals("Content review could not be saved.", ShopQaModerationContract.errorMessage(503, "secret-data"))
    }

    @Test fun queueAndPreviewParseTypedRevisionsAndPagination() {
        val api = ShopFunctionApi::class.java.declaredMethods.associateBy { it.name }
        assertEquals("functions/v1/adishop-qa-moderation?action=queue",
            api.getValue("qaModerationQueue").getAnnotation(GET::class.java).value)
        assertEquals("functions/v1/adishop-qa-moderation?action=preview",
            api.getValue("qaModerationPreview").getAnnotation(GET::class.java).value)
        val queue = gson.fromJson("""{"ok":true,"reviewQueue":{"total":2,"items":[{"entityKind":"store","entityId":"20000000-0000-4000-8000-000000000001","targetId":"20000000-0000-4000-8000-000000000001","reviewStatus":"pending","expectedRevision":"2026-11-14T12:00:00+00:00"}]}}""",
            ShopQaModerationQueueResponse::class.java)
        assertEquals(2, queue.reviewQueue?.total)
        assertEquals(1, queue.reviewQueue?.items?.size)
        val item = queue.reviewQueue!!.items.single()
        assertEquals("pending", item.reviewStatus)
        assertEquals("2026-11-14T12:00:00+00:00", ShopQaModerationContract.revision(item.entityKind, item.expectedRevision))
        val preview = gson.fromJson("""{"ok":true,"reviewPreview":{"entityKind":"store","entityId":"20000000-0000-4000-8000-000000000001","targetId":"20000000-0000-4000-8000-000000000001","reviewStatus":"pending","expectedRevision":"2026-11-14T12:00:00Z","content":{"name":"QA Store","city":"Addis"},"permittedActions":["approve","reject"]}}""",
            ShopQaModerationPreviewResponse::class.java).reviewPreview!!
        assertTrue(ShopQaModerationContract.sameRevision("store", item.expectedRevision, preview.expectedRevision))
        assertEquals("QA Store", ShopQaModerationContract.decision(preview, "approve").publicName)
        assertTrue(ShopQaModerationContract.matchesDecision(preview, "approve", ShopQaReviewResult(
            "approve", preview.expectedRevision, storeId = preview.entityId)))
        assertFalse(ShopQaModerationContract.matchesDecision(preview, "approve", ShopQaReviewResult(
            "approve", preview.expectedRevision, storeId = "20000000-0000-4000-8000-000000000002")))
    }

    @Test fun decisionUsesExactPreviewAndRejectsStaleOrMissingInputs() {
        val id = "20000000-0000-4000-8000-000000000001"
        val image = ShopPublicContentPreviewDto(entityKind = "image", entityId = id,
            expectedRevision = JsonParser.parseString("4"), content = mapOf("altText" to "QA image"),
            permittedActions = listOf("reject"))
        val body = gson.toJson(ShopQaModerationContract.decision(image, "reject"))
        assertTrue(body.contains("\"expectedRevision\":4"))
        assertTrue(body.contains("\"decision\":\"reject\""))
        assertThrows(IllegalArgumentException::class.java) { ShopQaModerationContract.decision(image, "approve") }
        assertThrows(IllegalArgumentException::class.java) { ShopQaModerationContract.decision(image, "revoke") }
        val imageWithPreview = ShopPublicContentPreviewDto(entityKind = "image", entityId = id,
            expectedRevision = JsonParser.parseString("4"),
            content = mapOf("altText" to "QA image", "imageUrl" to "https://example.com/preview.png"),
            permittedActions = listOf("approve", "reject"))
        val approvedBody = gson.toJson(ShopQaModerationContract.decision(imageWithPreview, "approve"))
        assertTrue(approvedBody.contains("\"decision\":\"approve\""))
        assertTrue(approvedBody.contains("\"altText\":\"QA image\""))
        assertFalse(ShopQaModerationContract.sameRevision("image", image.expectedRevision, JsonParser.parseString("5")))
        assertFalse(ShopQaModerationContract.sameRevision("product", JsonParser.parseString("7"), JsonParser.parseString("7")))
        val incomplete = ShopPublicContentPreviewDto(entityKind = "product", entityId = id,
            expectedRevision = JsonParser.parseString("\"2026-11-14T12:00:00Z\""),
            permittedActions = listOf("approve"))
        assertThrows(IllegalArgumentException::class.java) { ShopQaModerationContract.decision(incomplete, "approve") }
    }

    @Test fun retryKeyPersistsForSameActionAndResetsForAnother() {
        var next = 0
        val keys = ShopQaModerationRetryKeys { (++next).toString() }
        assertEquals("1", keys.keyFor("store:1:revision:approve"))
        assertEquals("1", keys.keyFor("store:1:revision:approve"))
        assertEquals("2", keys.keyFor("store:1:revision:reject"))
        keys.clear()
        assertEquals("3", keys.keyFor("store:1:revision:reject"))
    }
}
