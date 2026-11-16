package com.adischat.app.feature.shop.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.io.ByteArrayOutputStream

class ShopBenefitsRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    private fun service(): ShopFunctionApi = api ?: error("Benefit tools are unavailable.")

    suspend fun brands(storeId: String): Result<List<ShopBenefitBrandDto>> = runCatching {
        identity.authorizedRequest { token -> service().sellerBenefitBrands(token, storeId) }.benefitBrands
    }

    suspend fun sellerClaims(storeId: String, productId: String? = null): Result<List<ShopBenefitCaseDto>> = runCatching {
        identity.authorizedRequest { token -> service().sellerBenefitClaims(token, storeId = storeId, productId = productId) }
            .benefitClaims
    }

    suspend fun createClaim(request: ShopBenefitCreateRequest): Result<ShopBenefitCaseDto> = runCatching {
        identity.authorizedRequest { token -> service().sellerCreateBenefit(token, request) }
            .benefitClaim ?: error("Claim was not created.")
    }

    suspend fun submitClaim(storeId: String, claim: ShopBenefitCaseDto): Result<ShopBenefitCaseDto> = runCatching {
        identity.authorizedRequest { token ->
            service().sellerSubmitBenefit(
                token, ShopBenefitSubmitRequest(storeId, claim.id, claim.version, UUID.randomUUID().toString()),
            )
        }.benefitClaim ?: error("Claim was not submitted.")
    }

    suspend fun uploadEvidence(
        storeId: String,
        caseId: String,
        bytes: ByteArray,
        mimeType: String,
        evidenceType: String,
        documentExpiresAt: String? = null,
    ): Result<ShopBenefitEvidenceDto> = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024)
        require(mimeType in setOf("image/jpeg", "image/png", "application/pdf"))
        val file = MultipartBody.Part.createFormData(
            "file", "evidence.${if (mimeType == "application/pdf") "pdf" else if (mimeType == "image/png") "png" else "jpg"}",
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        val textType = "text/plain".toMediaType()
        identity.authorizedRequest { token ->
            service().sellerUploadBenefitEvidence(
                token, file, storeId.toRequestBody(textType), caseId.toRequestBody(textType),
                evidenceType.toRequestBody(textType), documentExpiresAt.orEmpty().toRequestBody(textType),
                UUID.randomUUID().toString().toRequestBody(textType),
            )
        }.evidence.firstOrNull() ?: error("Evidence was not uploaded.")
    }

    suspend fun adminQueue(): Result<List<ShopBenefitCaseDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitQueue(token) }.benefitClaims
    }

    suspend fun adminBrands(): Result<List<ShopBenefitBrandDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitBrands(token) }.benefitBrands
    }

    suspend fun adminBrandSnapshot(): Result<ShopBenefitResponse> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitBrands(token) }
    }

    suspend fun adminManageBrand(request: ShopBenefitBrandRequest): Result<ShopBenefitBrandDto> = runCatching {
        identity.authorizedRequest { token -> service().adminManageBenefitBrand(token, request) }
            .benefitBrand ?: error("Brand action was not completed.")
    }

    suspend fun adminManageMerge(request: ShopBenefitMergeRequest): Result<ShopBenefitMergeReviewDto> = runCatching {
        identity.authorizedRequest { token -> service().adminManageBenefitMerge(token, request) }
            .benefitMergeReview ?: error("Duplicate review was not recorded.")
    }

    suspend fun adminProductClaims(productId: String): Result<List<ShopBenefitCaseDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminProductBenefits(token, productId) }.benefitClaims
    }

    suspend fun adminStoreClaims(storeId: String): Result<List<ShopBenefitCaseDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminStoreBenefits(token, storeId) }.benefitClaims
    }

    suspend fun adminEvidence(caseId: String): Result<List<ShopBenefitEvidenceDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitEvidence(token, caseId) }.evidence
    }

    suspend fun adminPreview(evidenceId: String): Result<ByteArray> = runCatching {
        require(evidenceId.isNotBlank())
        identity.authorizedRequest { token -> service().adminBenefitPreview(token, evidenceId) }.use { response ->
            val output = ByteArrayOutputStream()
            response.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 10 * 1024 * 1024) { "Evidence is too large." }
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray().also { require(it.isNotEmpty()) }
        }
    }

    suspend fun adminHistory(caseId: String): Result<List<ShopBenefitEventDto>> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitHistory(token, caseId) }.benefitEvents
    }

    suspend fun adminDecide(request: ShopBenefitDecisionRequest): Result<ShopBenefitCaseDto> = runCatching {
        identity.authorizedRequest { token -> service().adminBenefitDecision(token, request) }
            .benefitClaim ?: error("Review was not completed.")
    }

    suspend fun publicBenefits(productId: String): Result<List<ShopVerifiedBenefitDto>> = runCatching {
        service().productVerifiedBenefits(productId).verifiedBenefits
    }

    suspend fun catalogBenefits(productIds: List<String>): Result<Map<String, List<ShopVerifiedBenefitDto>>> = runCatching {
        require(productIds.size <= 100)
        service().catalogVerifiedBenefits(ShopBenefitCatalogRequest(productIds)).benefitsByProductId
    }
}
