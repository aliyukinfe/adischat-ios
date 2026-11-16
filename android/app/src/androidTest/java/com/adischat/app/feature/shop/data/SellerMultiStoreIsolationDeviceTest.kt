package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adischat.app.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SellerMultiStoreIsolationDeviceTest {
    @Test
    fun controlledForeignOrderDetailIsHidden() = runBlocking {
        val orderGroupId = InstrumentationRegistry.getArguments().getString("foreignOrderGroupId").orEmpty()
        assertTrue("Controlled foreign order-group ID is required", orderGroupId.isNotBlank())
        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        val storeId = api.sellerBusiness(authorization).sellerBusiness?.selectedStore?.id.orEmpty()
        assertTrue(storeId.isNotBlank())
        assertTrue(api.sellerOrderGroups(authorization, storeId).sellerOrderGroups.orEmpty().none { it.id == orderGroupId })

        val url = BuildConfig.ADISHOP_SUPABASE_URL.trimEnd('/') +
            "/functions/v1/adishop-get-seller-order-group?orderGroupId=$orderGroupId&storeId=$storeId"
        val request = Request.Builder().url(url)
            .header("apikey", BuildConfig.ADISHOP_SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", authorization)
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            assertEquals(404, response.code)
        }
    }

    @Test
    fun controlledForeignStoreRequestsAreDenied() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val foreignStoreId = args.getString("foreignStoreId").orEmpty()
        val foreignProductId = args.getString("foreignProductId").orEmpty()
        assertTrue("Controlled foreign store and product IDs are required", foreignStoreId.isNotBlank() && foreignProductId.isNotBlank())

        val identity = ShopIdentityRepository()
        val authorization = "Bearer ${identity.sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        val ownStoreId = api.sellerBusiness(authorization).sellerBusiness?.selectedStore?.id.orEmpty()
        assertTrue("Sign in as the controlled seller first", ownStoreId.isNotBlank())
        assertNotEquals(foreignStoreId, ownStoreId)

        val ownProducts = api.sellerProducts(authorization, ownStoreId)
        assertEquals(ownStoreId, ownProducts.sellerProductStoreId)
        assertTrue(ownProducts.sellerProducts.orEmpty().all { it.storeId == ownStoreId })

        assertDenied(403) { api.sellerProducts(authorization, foreignStoreId) }
        assertDenied(403) { api.sellerProductDetail(authorization, foreignStoreId, foreignProductId) }
        assertDenied(403) { api.sellerCreateProduct(
            authorization,
            ShopSellerProductCreateRequest(foreignStoreId, "v5-foreign-create-${UUID.randomUUID()}"),
        ) }
        assertDenied(403) { api.sellerArchiveProduct(
            authorization,
            ShopSellerProductArchiveRequest(
                foreignStoreId, foreignProductId, ShopSellerProductArchivePayload(),
                "v5-foreign-edit-${UUID.randomUUID()}",
            ),
        ) }
        assertDenied(403) { api.sellerInventoryUpdate(
            authorization,
            body = ShopSellerInventoryUpdateRequest(
                foreignStoreId, "v5-foreign-inventory-${UUID.randomUUID()}", "qa-denial", emptyList(),
            ),
        ) }
        assertDenied(403) { api.sellerBusiness(authorization, storeId = foreignStoreId) }
        assertDenied(404) { api.notifications(authorization, "seller", foreignStoreId) }
        assertDenied(401) { api.sellerProducts("", ownStoreId) }
        assertDenied(401) { api.sellerProducts("Bearer invalid", ownStoreId) }
    }

    @Test
    fun controlledOwnerCanListEachStoreWithoutCrossStoreProducts() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val storeA = args.getString("storeA").orEmpty()
        val storeB = args.getString("storeB").orEmpty()
        assertTrue("Two controlled store IDs are required", storeA.isNotBlank() && storeB.isNotBlank())
        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        for (storeId in listOf(storeA, storeB)) {
            val result = api.sellerProducts(authorization, storeId)
            assertEquals(storeId, result.sellerProductStoreId)
            assertTrue(result.sellerProducts.orEmpty().all { it.storeId == storeId })
        }
        assertDenied(409) { api.sellerProducts(authorization, "") }
    }

    @Test
    fun selectedStoreScopesSellerOrdersBeforeMutation() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val storeA = args.getString("storeA").orEmpty()
        val storeB = args.getString("storeB").orEmpty()
        assertTrue("Two controlled store IDs are required", storeA.isNotBlank() && storeB.isNotBlank())
        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        val storeAOrders = api.sellerOrderGroups(authorization, storeA)
        val storeBOrders = api.sellerOrderGroups(authorization, storeB)
        assertEquals(storeA, storeAOrders.sellerOrderStoreId)
        assertEquals(storeB, storeBOrders.sellerOrderStoreId)
        assertTrue(storeAOrders.sellerOrderGroups.orEmpty().all { it.storeId == storeA })
        assertTrue(storeBOrders.sellerOrderGroups.orEmpty().all { it.storeId == storeB })
        val groupId = storeAOrders.sellerOrderGroups.orEmpty().firstOrNull()?.id.orEmpty()
        assertTrue("A controlled Store A order is required for denial probes", groupId.isNotBlank())
        assertDenied(404) { api.sellerAcceptOrderGroup(
            authorization, ShopSellerOrderActionRequest(orderGroupId = groupId, storeId = storeB),
        ) }
        assertDenied(400) { api.sellerAcceptOrderGroup(
            authorization, ShopSellerOrderActionRequest(orderGroupId = groupId),
        ) }
        assertDenied(404) { api.sellerAcceptOrderGroup(
            authorization, ShopSellerOrderActionRequest(orderGroupId = groupId, storeId = "not-a-uuid"),
        ) }
        assertDenied(404) { api.sellerVerifyDeliveryCode(
            authorization, ShopSellerDeliveryCodeRequest(groupId, "000000", storeB),
        ) }
    }

    @Test
    fun revokedControlledSessionCannotListProducts() = runBlocking {
        val identity = ShopIdentityRepository()
        val authorization = "Bearer ${identity.sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        val storeId = api.sellerBusiness(authorization).sellerBusiness?.selectedStore?.id.orEmpty()
        assertTrue("Sign in as the controlled seller first", storeId.isNotBlank())
        assertTrue("Controlled account session revocation must succeed", identity.revokeCurrentAccountSessions())
        assertDenied(401) { api.sellerProducts(authorization, storeId) }
    }

    private suspend fun assertDenied(expectedStatus: Int, request: suspend () -> Any?) {
        val failure = runCatching { request() }.exceptionOrNull()
        assertTrue("Protected request must be denied", failure is HttpException)
        assertEquals(expectedStatus, (failure as HttpException).code())
    }
}
