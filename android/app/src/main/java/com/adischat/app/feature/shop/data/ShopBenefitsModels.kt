package com.adischat.app.feature.shop.data

import com.google.gson.annotations.SerializedName

internal object ShopBenefitFlags {
    const val buyerBadgesEnabled = false
    const val managementEnabled = false
}

data class ShopVerifiedBenefitDto(
    val type: String = "",
    val label: String = "",
    val summary: String? = null,
    val effectiveUntil: String? = null,
)

data class ShopBenefitCaseDto(
    val id: String = "",
    @SerializedName("store_id") val storeId: String = "",
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("brand_id") val brandId: String? = null,
    val kind: String = "",
    val status: String = "draft",
    val effectiveStatus: String? = null,
    val version: Int = 0,
    @SerializedName("public_reason") val publicReason: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("effective_at") val effectiveAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
)

data class ShopBenefitEvidenceDto(
    val id: String = "",
    val evidenceType: String = "",
    val mimeType: String = "",
    val documentExpiresAt: String? = null,
)

data class ShopBenefitBrandDto(
    val id: String = "",
    @SerializedName(value = "canonical_name", alternate = ["canonicalName"]) val canonicalName: String = "",
    val status: String = "active",
    val version: Int = 1,
    val aliases: List<String> = emptyList(),
)

data class ShopBenefitBrandEventDto(
    @SerializedName("brand_id") val brandId: String = "",
    val action: String = "",
    @SerializedName("normalized_name") val normalizedName: String? = null,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopBenefitMergeReviewDto(
    val id: String = "",
    @SerializedName("source_brand_id") val sourceBrandId: String = "",
    @SerializedName("target_brand_id") val targetBrandId: String = "",
    val status: String = "",
    val reason: String? = null,
    @SerializedName("decision_reason") val decisionReason: String? = null,
    val version: Int = 0,
)

data class ShopBenefitEventDto(
    val id: String = "",
    val action: String = "",
    @SerializedName("previous_status") val previousStatus: String? = null,
    @SerializedName("new_status") val newStatus: String = "",
    @SerializedName("case_version") val caseVersion: Int = 0,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopBenefitResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val benefitClaims: List<ShopBenefitCaseDto> = emptyList(),
    val benefitBrands: List<ShopBenefitBrandDto> = emptyList(),
    val benefitBrand: ShopBenefitBrandDto? = null,
    val benefitBrandEvents: List<ShopBenefitBrandEventDto> = emptyList(),
    val benefitMergeReviews: List<ShopBenefitMergeReviewDto> = emptyList(),
    val benefitMergeReview: ShopBenefitMergeReviewDto? = null,
    val benefitEvents: List<ShopBenefitEventDto> = emptyList(),
    val benefitClaim: ShopBenefitCaseDto? = null,
    val evidence: List<ShopBenefitEvidenceDto> = emptyList(),
    val verifiedBenefits: List<ShopVerifiedBenefitDto> = emptyList(),
    val benefitsByProductId: Map<String, List<ShopVerifiedBenefitDto>> = emptyMap(),
)

data class ShopBenefitBrandRequest(
    val brandAction: String,
    val brandId: String? = null,
    val name: String? = null,
    val expectedVersion: Int? = null,
    val reason: String? = null,
    val requestKey: String,
)

data class ShopBenefitMergeRequest(
    val brandAction: String,
    val sourceBrandId: String? = null,
    val targetBrandId: String? = null,
    val reviewId: String? = null,
    val expectedVersion: Int? = null,
    val decision: String? = null,
    val reason: String,
    val requestKey: String,
)

data class ShopBenefitCatalogRequest(val productIds: List<String>)

data class ShopBenefitCreateRequest(
    val storeId: String,
    val productId: String?,
    val brandId: String?,
    val kind: String,
    val termsSummary: String,
    val coverageDetails: String?,
    val durationMonths: Int?,
    val authorizationScope: String?,
    val coveredProductIds: List<String> = emptyList(),
    val requestKey: String,
)

data class ShopBenefitSubmitRequest(
    val storeId: String,
    val caseId: String,
    val expectedVersion: Int,
    val requestKey: String,
)

data class ShopBenefitDecisionRequest(
    val caseId: String,
    val expectedVersion: Int,
    val benefitAction: String,
    val reason: String,
    val effectiveAt: String?,
    val expiresAt: String?,
    val requestKey: String,
)
