package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminSellerApplicationReviewDeviceTest {
    @Test
    fun retryingStartReviewIsIdempotent() = runBlocking {
        val applicationId = InstrumentationRegistry.getArguments()
            .getString("applicationId")
            .orEmpty()
        assertTrue("applicationId instrumentation argument is required", applicationId.isNotBlank())

        val identity = ShopIdentityRepository()
        val authorization = "Bearer ${identity.sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)

        val result = api.adminReviewSeller(
            authorization,
            ShopAdminReviewSellerRequest(
                applicationId = applicationId,
                action = "start_review",
                requestId = "android-admin-start-review-idempotency-probe",
            ),
        )

        assertTrue("Idempotent Admin retry must return success", result.ok)
    }
}
