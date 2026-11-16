package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adischat.app.R
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SellerTwoStoreMediaDeviceTest {
    @Test
    fun unpublishedMediaAndVariantsStayInTheirStore() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val stores = listOf(args.getString("storeA").orEmpty(), args.getString("storeB").orEmpty())
        assertTrue("Two controlled store IDs are required", stores.all { it.isNotBlank() } && stores[0] != stores[1])

        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        val created = mutableListOf<Pair<String, String>>()
        val uploaded = mutableListOf<Pair<String, String>>()
        val nonce = UUID.randomUUID().toString().take(8)
        try {
            val results = stores.mapIndexed { index, storeId ->
                val label = if (index == 0) "A" else "B"
                val product = api.sellerCreateProduct(
                    authorization,
                    ShopSellerProductCreateRequest(storeId, "v51-create-$nonce-$label"),
                ).sellerProduct ?: error("QA draft was not created")
                assertEquals(storeId, product.storeId)
                assertEquals("draft", product.status)
                created += storeId to product.id

                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val bytes = context.resources.openRawResource(R.drawable.adisgebeya_loader_buyer).use { it.readBytes() }
                val image = api.sellerUploadProductMedia(
                    authorization,
                    MultipartBody.Part.createFormData("image", "v51-qa.png", bytes.toRequestBody("image/png".toMediaType())),
                    product.id.plainText(),
                    "v51-media-$nonce-$label".plainText(),
                    "Unpublished QA fixture".plainText(),
                    "1254".plainText(),
                    "1254".plainText(),
                ).sellerProductImage ?: error("QA image was not uploaded")
                uploaded += product.id to image.id
                assertTrue(image.storagePath.orEmpty().contains("/$storeId/${product.id}/"))
                assertEquals("image/png", image.mimeType)
                val imageUrl = image.imageUrl.orEmpty()
                assertTrue(imageUrl.startsWith("https://"))
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    assertEquals(200, connection.responseCode)
                    assertTrue(connection.inputStream.use { it.read() } >= 0)
                } finally {
                    connection.disconnect()
                }

                val payload = qaPayload(nonce, label, image.id)
                val saved = api.sellerUpdateProduct(
                    authorization,
                    ShopSellerProductUpdateRequest(storeId, product.id, payload, "v51-save-$nonce-$label"),
                ).sellerProduct ?: error("QA variant was not saved")
                assertTrue(saved.variants.any { image.id in it.imageIds })
                val reloaded = api.sellerProductDetail(authorization, storeId, product.id).sellerProduct
                assertNotNull(reloaded)
                assertTrue(reloaded!!.images.any { it.id == image.id })
                assertTrue(reloaded.variants.any { image.id in it.imageIds })
                Triple(storeId, product.id, image.id)
            }

            val first = results[0]
            val second = results[1]
            val beforeCross = api.sellerProductDetail(authorization, second.first, second.second).sellerProduct!!
            assertDenied("Store A detail cannot read Store B product") { api.sellerProductDetail(authorization, first.first, second.second) }
            assertDenied("Store B detail cannot read Store A product") { api.sellerProductDetail(authorization, second.first, first.second) }
            assertDenied("Store A update cannot change Store B product") {
                api.sellerUpdateProduct(
                    authorization,
                    ShopSellerProductUpdateRequest(first.first, second.second, qaPayload(nonce, "B", first.third), "v51-cross-store-$nonce"),
                )
            }
            assertDenied("Store B variant cannot use Store A image", expectedStatus = 404) {
                api.sellerUpdateProduct(
                    authorization,
                    ShopSellerProductUpdateRequest(second.first, second.second, qaPayload(nonce, "B", first.third), "v51-cross-image-$nonce"),
                )
            }
            val finalB = api.sellerProductDetail(authorization, second.first, second.second).sellerProduct!!
            assertEquals(beforeCross.title, finalB.title)
            assertEquals(beforeCross.priceMinor, finalB.priceMinor)
            assertEquals(beforeCross.inventory, finalB.inventory)
            assertEquals(beforeCross.variants, finalB.variants)
            assertEquals(beforeCross.images, finalB.images)
            assertEquals(beforeCross.updatedAt, finalB.updatedAt)
            assertTrue(finalB.variants.any { second.third in it.imageIds })
            assertFalse(finalB.variants.any { first.third in it.imageIds })
        } finally {
            var cleanupFailure: Throwable? = null
            uploaded.asReversed().forEach { (productId, imageId) ->
                runCatching {
                    val storeId = created.first { it.second == productId }.first
                    api.sellerDeleteProductMedia(authorization, ShopSellerProductMediaDeleteRequest(storeId, productId, imageId))
                }.onFailure { cleanupFailure = it }
            }
            created.asReversed().forEach { (storeId, productId) ->
                runCatching {
                    api.sellerArchiveProduct(
                        authorization,
                        ShopSellerProductArchiveRequest(storeId, productId, ShopSellerProductArchivePayload(), "v51-archive-${UUID.randomUUID()}"),
                    )
                }.onFailure { cleanupFailure = it }
            }
            cleanupFailure?.let { throw AssertionError("QA cleanup failed; inspect unpublished fixtures", it) }
        }
    }

    @Test
    fun controlledDatabaseExpiredSessionDeniesSellerOperations() = runBlocking {
        val storeId = InstrumentationRegistry.getArguments().getString("storeA").orEmpty()
        assertTrue("Controlled store ID is required", storeId.isNotBlank())
        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        assertEquals(storeId, api.sellerProducts(authorization, storeId).sellerProductStoreId)

        // The test operator expires only this controlled session in the database during this window.
        Thread.sleep(90_000)

        assertExpired { api.sellerProducts(authorization, storeId) }
        assertExpired { api.sellerCreateProduct(authorization, ShopSellerProductCreateRequest(storeId, "v51-expired-${UUID.randomUUID()}")) }
        assertExpired {
            api.sellerUpdateProduct(
                authorization,
                ShopSellerProductUpdateRequest(
                    storeId,
                    UUID.randomUUID().toString(),
                    qaPayload("expired", "none", UUID.randomUUID().toString()),
                    "v51-expired-${UUID.randomUUID()}",
                ),
            )
        }
        assertExpired {
            api.sellerInventoryUpdate(
                authorization,
                body = ShopSellerInventoryUpdateRequest(storeId, "v51-expired-${UUID.randomUUID()}", "qa-denial", emptyList()),
            )
        }
        assertExpired { api.notifications(authorization, "seller", storeId) }
        assertExpired { api.sellerBusiness(authorization, storeId = storeId) }
        assertExpired { api.sellerOrderGroups(authorization, storeId) }
    }

    private fun qaPayload(nonce: String, label: String, imageId: String) = ShopSellerProductDraftPayload(
        title = "V5.1 QA media variant $label $nonce",
        categoryId = "73060705-1d2b-4f7b-8f3a-4d6bd8ac0cfb",
        shortDescription = "Unpublished isolation fixture",
        description = "Controlled unpublished media and variant round-trip fixture.",
        priceMinor = 100,
        stockQuantity = 0,
        variantCatalog = listOf(
            ShopSellerVariantInput(
                title = "QA default",
                options = emptyMap(),
                priceMinor = 100,
                quantityAvailable = 0,
                imageIds = listOf(imageId),
            ),
        ),
    )

    private fun String.plainText() = toRequestBody("text/plain".toMediaType())

    private suspend fun assertDenied(label: String, expectedStatus: Int? = null, request: suspend () -> Any?) {
        val failure = runCatching { request() }.exceptionOrNull()
        assertTrue("$label must be denied", failure is HttpException)
        val code = (failure as HttpException).code()
        assertTrue("$label returned HTTP $code", code in listOf(403, 404, 409))
        if (expectedStatus != null) assertEquals("$label returned HTTP $code", expectedStatus, code)
    }

    private suspend fun assertExpired(request: suspend () -> Any?) {
        val failure = runCatching { request() }.exceptionOrNull()
        assertTrue("Expired session must be denied", failure is HttpException)
        assertEquals(401, (failure as HttpException).code())
    }
}
