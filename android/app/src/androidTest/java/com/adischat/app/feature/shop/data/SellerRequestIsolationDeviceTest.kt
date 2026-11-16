package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SellerRequestIsolationDeviceTest {
    @Test
    fun sellerBIsDeniedWhenRespondingToForeignRefund() = runBlocking {
        val requestId = InstrumentationRegistry.getArguments().getString("foreignRefundId").orEmpty()
        assertTrue("foreignRefundId instrumentation argument is required", requestId.isNotBlank())

        val identity = ShopIdentityRepository()
        val authorization = "Bearer ${identity.sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)

        val failure = runCatching {
            api.sellerRespondRefundRequest(
                authorization,
                ShopSellerRequestResponseRequest(
                    requestId = requestId,
                    message = "Controlled isolation probe",
                    idempotencyKey = "seller-b-isolation-${UUID.randomUUID()}",
                    expectedVersion = 0,
                ),
            )
        }.exceptionOrNull()

        assertTrue("Foreign seller mutation must fail", failure is HttpException)
        assertEquals(404, (failure as HttpException).code())
    }
}
