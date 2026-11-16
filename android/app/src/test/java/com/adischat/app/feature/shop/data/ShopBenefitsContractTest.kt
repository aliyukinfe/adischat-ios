package com.adischat.app.feature.shop.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Part

class ShopBenefitsContractTest {
    @Test
    fun buyerAndManagementBenefitsRemainDisabled() {
        assertFalse(ShopBenefitFlags.buyerBadgesEnabled)
        assertFalse(ShopBenefitFlags.managementEnabled)
    }

    @Test
    fun publicBenefitResponseParsesWithoutPrivateEvidence() {
        val response = Gson().fromJson(
            """{"ok":true,"verifiedBenefits":[{"type":"original_product","label":"Original product verified","summary":"Reviewed evidence","effectiveUntil":"2027-01-01T00:00:00Z"}]}""",
            ShopBenefitResponse::class.java,
        )
        assertEquals(1, response.verifiedBenefits.size)
        assertEquals("original_product", response.verifiedBenefits.single().type)
        assertFalse(Gson().toJson(response.verifiedBenefits.single()).contains("storage_path"))
    }

    @Test
    fun sellerClaimRequestDoesNotContainVerificationDecision() {
        val request = ShopBenefitCreateRequest(
            storeId = "store-a", productId = "product-a", brandId = null,
            kind = "seller_warranty", termsSummary = "Repair for one year",
            coverageDetails = "Parts and labor", durationMonths = 12,
            authorizationScope = null, requestKey = "request-key-123",
        )
        val json = Gson().toJson(request)
        assertTrue(json.contains("storeId"))
        assertFalse(json.contains("approved"))
        assertFalse(json.contains("reviewerId"))
    }

    @Test
    fun existingTransportUrlsRemainStable() {
        val seller = ShopFunctionApi::class.java.methods.first { it.name == "sellerCreateBenefit" }
        val admin = ShopFunctionApi::class.java.methods.first { it.name == "adminBenefitDecision" }
        val buyer = ShopFunctionApi::class.java.methods.first { it.name == "productVerifiedBenefits" }
        assertEquals("functions/v1/adishop-seller-business?action=benefit-create", seller.getAnnotation(POST::class.java)?.value)
        assertEquals("functions/v1/adishop-admin-review-product?action=benefit-decision", admin.getAnnotation(POST::class.java)?.value)
        assertEquals("functions/v1/adishop-product-reviews", buyer.getAnnotation(GET::class.java)?.value)
    }

    @Test
    fun previewAndCatalogBatchReuseExistingFunctions() {
        val preview = ShopFunctionApi::class.java.methods.first { it.name == "adminBenefitPreview" }
        val batch = ShopFunctionApi::class.java.methods.first { it.name == "catalogVerifiedBenefits" }
        assertEquals("functions/v1/adishop-admin-product-detail", preview.getAnnotation(GET::class.java)?.value)
        assertEquals("functions/v1/adishop-product-reviews?action=verified-benefits-batch", batch.getAnnotation(POST::class.java)?.value)
    }

    @Test
    fun catalogAndCanonicalBrandResponsesParseWithoutEvidence() {
        val response = Gson().fromJson(
            """{"ok":true,"benefitsByProductId":{"product-a":[{"type":"seller_warranty","label":"Seller warranty","effectiveUntil":"2027-01-01T00:00:00Z"}]},"benefitBrands":[{"id":"brand-a","canonical_name":"Brand A","status":"active","version":2,"aliases":["brand a"]}]}""",
            ShopBenefitResponse::class.java,
        )
        assertEquals("seller_warranty", response.benefitsByProductId.getValue("product-a").single().type)
        assertEquals(2, response.benefitBrands.single().version)
        assertEquals(listOf("brand a"), response.benefitBrands.single().aliases)
        assertFalse(Gson().toJson(response.benefitsByProductId).contains("storage_path"))
    }

    @Test
    fun uploadCarriesIdempotencyKeyOnExistingSellerTransport() {
        val upload = ShopFunctionApi::class.java.methods.first { it.name == "sellerUploadBenefitEvidence" }
        assertEquals("functions/v1/adishop-seller-business?action=benefit-evidence", upload.getAnnotation(POST::class.java)?.value)
        assertTrue(upload.parameterAnnotations.flatMap { it.asList() }
            .filterIsInstance<Part>().any { it.value == "requestKey" })
    }

    @Test
    fun mergeReviewResponseCarriesNoAuthorizationTransfer() {
        val response = Gson().fromJson(
            """{"ok":true,"benefitMergeReviews":[{"id":"review-a","source_brand_id":"brand-a","target_brand_id":"brand-b","status":"confirmed_duplicate","version":2}]}""",
            ShopBenefitResponse::class.java,
        )
        assertEquals("confirmed_duplicate", response.benefitMergeReviews.single().status)
        assertFalse(Gson().toJson(response.benefitMergeReviews.single()).contains("authorization_scope"))
    }
}
