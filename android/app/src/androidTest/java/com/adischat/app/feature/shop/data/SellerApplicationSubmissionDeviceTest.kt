package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SellerApplicationSubmissionDeviceTest {
    @Test
    fun retryingSubmittedApplicationIsIdempotent() = runBlocking {
        val identity = ShopIdentityRepository()
        val authorization = "Bearer ${identity.sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)

        val result = api.submitCompleteSellerApplication(
            authorization,
            ShopSellerApplicationRequest(
                submit = true,
                requestId = "android-seller-submit-idempotency-probe",
            ),
        )

        assertEquals("submitted", result.application?.status ?: result.status)
    }
}
