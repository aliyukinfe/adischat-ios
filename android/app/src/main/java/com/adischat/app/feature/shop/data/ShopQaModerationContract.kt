package com.adischat.app.feature.shop.data

import java.time.OffsetDateTime
import java.util.UUID
import com.google.gson.JsonElement

/** QA-only transport model. Public Content remains disabled. */
data class ShopQaModerationQueueResponse(val ok: Boolean, val reviewQueue: ShopPublicContentQueueDto? = null)
data class ShopQaModerationPreviewResponse(val ok: Boolean, val reviewPreview: ShopPublicContentPreviewDto? = null)
data class ShopQaModerationRequest(
    val kind: String,
    val id: String,
    val decision: String,
    val expectedRevision: Any,
    val publicName: String? = null,
    val publicDescription: String? = null,
    val publicCity: String? = null,
    val publicTitle: String? = null,
    val publicShortDescription: String? = null,
    val altText: String? = null,
) {
    init {
        require(kind in setOf("store", "product", "image"))
        UUID.fromString(id)
        require(decision in setOf("approve", "reject") || kind == "store" && decision == "revoke")
        if (kind == "image") {
            require(expectedRevision is Int && expectedRevision > 0)
        } else {
            require(expectedRevision is String && runCatching { OffsetDateTime.parse(expectedRevision as String) }.isSuccess)
        }
        if (decision == "approve") when (kind) {
            "store" -> require(!publicName.isNullOrBlank() && !publicCity.isNullOrBlank())
            "product" -> require(!publicTitle.isNullOrBlank())
        }
    }
}

data class ShopQaModerationResponse(val ok: Boolean, val review: ShopQaReviewResult? = null, val error: String? = null)
data class ShopQaReviewResult(
    val decision: String,
    val revision: JsonElement? = null,
    val storeId: String? = null,
    val productId: String? = null,
    val imageId: String? = null,
)

object ShopQaModerationContract {
    fun newIdempotencyKey(): String = UUID.randomUUID().toString()

    fun revision(kind: String, value: JsonElement?): Any {
        require(value != null && value.isJsonPrimitive) { "Content changed. Refresh before reviewing." }
        val primitive = value.asJsonPrimitive
        return if (kind == "image") {
            require(primitive.isNumber && primitive.asString.matches(Regex("[1-9][0-9]*")))
            primitive.asInt.also { require(it > 0) }
        } else {
            require(kind == "store" || kind == "product")
            require(primitive.isString && runCatching { OffsetDateTime.parse(primitive.asString) }.isSuccess)
            primitive.asString
        }
    }

    fun sameRevision(kind: String, first: JsonElement?, second: JsonElement?): Boolean =
        runCatching {
            val a = revision(kind, first)
            val b = revision(kind, second)
            if (kind == "image") a == b else OffsetDateTime.parse(a as String).toInstant() ==
                OffsetDateTime.parse(b as String).toInstant()
        }.getOrDefault(false)

    fun matchesDecision(preview: ShopPublicContentPreviewDto, action: String, result: ShopQaReviewResult): Boolean {
        val resultId = when (preview.entityKind) {
            "store" -> result.storeId
            "product" -> result.productId
            "image" -> result.imageId
            else -> null
        }
        return result.decision == action && resultId == preview.entityId &&
            sameRevision(preview.entityKind, result.revision, preview.expectedRevision)
    }

    fun decision(preview: ShopPublicContentPreviewDto, action: String): ShopQaModerationRequest {
        if (preview.entityKind == "image" && action == "approve") {
            val imageUrl = (preview.content["imageUrl"] ?: preview.content["logoUrl"]) as? String
            require(!imageUrl.isNullOrBlank()) { "Visual preview is required before image approval." }
        }
        require(action in preview.permittedActions) { "Review action is unavailable." }
        val content = preview.content
        fun text(key: String): String? = content[key] as? String
        return ShopQaModerationRequest(
            kind = preview.entityKind, id = preview.entityId, decision = action,
            expectedRevision = revision(preview.entityKind, preview.expectedRevision),
            publicName = if (action == "approve" && preview.entityKind == "store") text("name") else null,
            publicDescription = if (action == "approve" && preview.entityKind == "store") text("description") else null,
            publicCity = if (action == "approve" && preview.entityKind == "store") text("city") else null,
            publicTitle = if (action == "approve" && preview.entityKind == "product") text("title") else null,
            publicShortDescription = if (action == "approve" && preview.entityKind == "product") text("shortDescription") else null,
            altText = if (action == "approve" && preview.entityKind == "image") text("altText") else null,
        )
    }

    fun requireSuccess(response: ShopQaModerationResponse): ShopQaReviewResult {
        require(response.ok && response.review != null) { "Review was not confirmed by the server." }
        return response.review
    }

    fun errorMessage(status: Int, code: String?): String = when {
        status == 401 -> "Session expired. Sign in again."
        status == 403 -> "You are not authorized to review this content."
        status == 409 && code == "stale-revision" -> "Content changed. Refresh before reviewing."
        status == 409 -> "This review was already submitted. Refresh before trying again."
        status == 429 -> "Too many review requests. Try again later."
        else -> "Content review could not be saved."
    }
}

class ShopQaModerationRetryKeys(private val generate: () -> String = ShopQaModerationContract::newIdempotencyKey) {
    private var action: String? = null
    private var key: String? = null

    fun keyFor(actionIdentity: String): String {
        if (action != actionIdentity) {
            action = actionIdentity
            key = generate()
        }
        return checkNotNull(key)
    }

    fun clear() { action = null; key = null }
}
