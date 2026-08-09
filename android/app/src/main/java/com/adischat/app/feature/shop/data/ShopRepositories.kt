package com.adischat.app.feature.shop.data

import com.adischat.app.network.CloudflareClient
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

private fun Throwable.shopCheckoutMessage(fallback: String): String {
    if (this is HttpException) {
        val body = response()?.errorBody()?.string().orEmpty()
        val errorObject = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")
        }.getOrNull()
        val code = errorObject?.get("code")?.asString.orEmpty()
        val message = errorObject?.get("message")?.asString.orEmpty()
        return when {
            message.isNotBlank() -> ShopStateReducers.safeErrorMessage(message, fallback)
            code == "insufficient_inventory" -> "Daily Skin Cream is no longer available in that quantity."
            code == "checkout_expired" -> "Your checkout expired. Please review your cart and try again."
            code == "checkout_already_confirmed" -> "Your order was already placed."
            code == "cod_not_eligible" -> "Cash on delivery is not available for this order."
            code == "address_not_eligible" -> "Cash on delivery is not available for this delivery address."
            code == "payment_method_unavailable" -> "Cash on delivery is not available for this order."
            code == "product_unavailable" -> "Some items are no longer available."
            code == "empty_cart" -> "Your cart is empty."
            else -> fallback
        }
    }
    return ShopStateReducers.safeErrorMessage(message, fallback)
}

private fun Throwable.shopSellerActionMessage(fallback: String): String {
    if (this is HttpException) {
        val body = response()?.errorBody()?.string().orEmpty()
        val parsedError = runCatching { JsonParser.parseString(body).asJsonObject.get("error") }.getOrNull()
        val errorObject = parsedError?.takeIf { it.isJsonObject }?.asJsonObject
        val code = errorObject?.get("code")?.asString.orEmpty()
        val message = errorObject?.get("message")?.asString
            ?: parsedError?.takeIf { it.isJsonPrimitive }?.asString
            ?: ""
        val normalized = "${code} ${message}".lowercase()
        return when {
            normalized.contains("seller_product_locked") -> "This product is locked while it is in review."
            normalized.contains("seller_product_not_found") -> "Product not found for this seller."
            normalized.contains("seller_store_not_found") -> "Your approved seller store could not be found."
            normalized.contains("category_not_found") -> "Choose an active product category."
            normalized.contains("product_validation_failed") -> "Product is missing required details."
            normalized.contains("unsupported_media_type") -> "Use a JPEG, PNG or WebP image."
            normalized.contains("media_too_large") -> "Image is too large. Choose a smaller photo."
            normalized.contains("seller_product_media_limit") -> "A product can have up to 10 images."
            normalized.contains("seller_product_media_not_found") -> "Product image not found for this seller."
            normalized.contains("delivery_code_invalid") || normalized.contains("invalid-delivery-code") || normalized.contains("incorrect") -> "The delivery code is incorrect."
            normalized.contains("delivery_code_locked") || normalized.contains("delivery-code-locked") -> "Too many incorrect delivery code attempts. Try again later."
            normalized.contains("delivery_code_not_revealed") || normalized.contains("delivery-code-not-revealed") -> "Ask the customer to reveal the delivery code first."
            message.isNotBlank() -> ShopStateReducers.safeErrorMessage(message, fallback)
            response()?.code() == 409 -> "This order has already moved to a new state. Refreshing seller orders."
            code == "invalid_order_state" -> "This order has already moved to a new state. Refreshing seller orders."
            code == "seller_order_not_found" -> "This order is no longer available for this seller."
            else -> fallback
        }
    }
    return ShopStateReducers.safeErrorMessage(message, fallback)
}

class ShopHomeRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun loadHome(): ShopHomeState {
        val service = api ?: return ShopHomeState(loading = false, error = true, maintenance = true)
        return try {
            ShopHomeState(
                loading = false,
                banners = service.banners(),
                categories = service.categories(),
                products = service.products(),
                stores = service.stores(),
            )
        } catch (error: Throwable) {
            android.util.Log.w("AdisShop", "Native shop home load failed", error)
            ShopHomeState(loading = false, error = true)
        }
    }
}

class CategoryRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun activeCategories(): List<ShopCategoryDto> = api?.categories().orEmpty()
}

class ProductRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun approvedProducts(): List<ShopProductDto> = api?.products().orEmpty()
}

class StoreRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun approvedStores(): List<ShopStoreDto> = api?.stores().orEmpty()
}

object ShopSessionMemory {
    @Volatile
    private var cachedSession: ShopSessionDto? = null

    @Volatile
    private var cachedProfile: ShopProfileDto? = null

    fun session(): ShopSessionDto? = cachedSession

    fun profile(): ShopProfileDto? = cachedProfile

    fun update(session: ShopSessionDto, profile: ShopProfileDto?) {
        cachedSession = session
        cachedProfile = profile
    }

    fun updateProfile(profile: ShopProfileDto?) {
        cachedProfile = profile
    }

    fun clear() {
        cachedSession = null
        cachedProfile = null
    }
}

private suspend fun ShopIdentityRepository.authorization(): String = "Bearer ${sessionToken().getOrThrow()}"

class ShopIdentityRepository(
    private val cloudflareApi: com.adischat.app.network.CloudflareApi = CloudflareClient.api,
    private val functionApi: ShopFunctionApi? = ShopFunctionClient.api,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val authRetryCoordinator = ShopAuthRetryCoordinator { error ->
        error is HttpException && error.code() == 401
    }

    fun activeUserId(): String = auth.currentUser?.uid.orEmpty()

    suspend fun sessionToken(): Result<String> = runCatching {
        val current = ShopSessionMemory.session()
        val profile = ShopSessionMemory.profile()
        val activeUid = auth.currentUser?.uid.orEmpty()
        if (ShopStateReducers.canUseCachedIdentity(current, profile, activeUid)) {
            return@runCatching current?.accessToken.orEmpty()
        }
        ShopSessionMemory.clear()
        val service = functionApi ?: error("Shop account sync is temporarily unavailable.")
        val assertion = cloudflareApi.requestShopSessionAssertion().assertion
        if (assertion.isBlank()) error("We couldn't sync your Shop account.")
        val exchange = service.exchange(ShopSessionExchangeRequest(assertion))
        val session = exchange.session ?: error(exchange.error ?: "We couldn't sync your Shop account.")
        if (exchange.profile?.adisChatUserId != activeUid) {
            ShopSessionMemory.clear()
            error("Please sign in again.")
        }
        ShopSessionMemory.update(session, exchange.profile)
        session.accessToken
    }

    suspend fun <T> authorizedRequest(block: suspend (String) -> T): T =
        authRetryCoordinator.execute(
            authorization = { authorization() },
            refreshAuthorization = ::refreshAuthorization,
            request = block,
        )

    private suspend fun refreshAuthorization(failedAuthorization: String): String {
        return try {
            val activeUid = activeUserId()
            val currentSession = ShopSessionMemory.session()
            val currentProfile = ShopSessionMemory.profile()
            val currentAuthorization = currentSession?.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

            if (
                currentAuthorization != null &&
                currentAuthorization != failedAuthorization &&
                ShopStateReducers.canUseCachedIdentity(currentSession, currentProfile, activeUid)
            ) {
                return currentAuthorization
            }

            val refreshToken = currentSession?.refreshToken.orEmpty()
            if (refreshToken.isBlank()) throw ShopSessionExpiredException()
            val service = functionApi ?: throw ShopSessionExpiredException()
            val refreshedSession = service.refresh(ShopRefreshRequest(refreshToken)).session
                ?: throw ShopSessionExpiredException()
            if (
                refreshedSession.accessToken.isBlank() ||
                currentProfile?.adisChatUserId != activeUid
            ) {
                throw ShopSessionExpiredException()
            }
            ShopSessionMemory.update(refreshedSession, currentProfile)
            "Bearer ${refreshedSession.accessToken}"
        } catch (error: Throwable) {
            ShopSessionMemory.clear()
            if (error is ShopSessionExpiredException) throw error
            throw ShopSessionExpiredException(error)
        }
    }

    suspend fun currentProfile(): Result<ShopProfileDto?> = runCatching {
        val service = functionApi ?: error("Shop account sync is temporarily unavailable.")
        val envelope = authorizedRequest { token -> service.currentProfile(token) }
        ShopSessionMemory.updateProfile(envelope.profile ?: ShopSessionMemory.profile())
        ShopSessionMemory.profile()
    }

    fun clear() {
        ShopSessionMemory.clear()
    }
}

class CartRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun getCart(): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        identity.authorizedRequest { token -> service.cart(token) }.cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun add(productId: String, quantity: Int = 1): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.addCartItem(token, ShopCartItemRequest(productId = productId, quantity = quantity))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun update(itemId: String, quantity: Int): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.updateCartItem(token, ShopCartItemUpdateRequest(itemId = itemId, quantity = quantity))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun remove(itemId: String): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.removeCartItem(token, ShopCartItemIdRequest(itemId = itemId))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun clear(): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        identity.authorizedRequest { token -> service.clearCart(token) }.cart
            ?: error("Your cart is temporarily unavailable.")
    }

    fun isAvailable(): Boolean = api != null
}

class FavoriteRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        identity.authorizedRequest { token -> service.favorites(token) }.favorites.orEmpty()
    }

    suspend fun add(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't save this right now. Try again.")
        identity.authorizedRequest { token ->
            service.addFavorite(token, ShopProductActionRequest(productId))
            service.favorites(token)
        }.favorites.orEmpty()
    }

    suspend fun remove(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't update saved products. Try again.")
        identity.authorizedRequest { token ->
            service.removeFavorite(token, ShopProductActionRequest(productId))
            service.favorites(token)
        }.favorites.orEmpty()
    }
}

class RecentlyViewedRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopRecentlyViewedDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        identity.authorizedRequest { token -> service.recentlyViewed(token) }.recentlyViewed.orEmpty()
    }

    suspend fun record(productId: String): Result<Unit> = runCatching {
        val service = api ?: return@runCatching
        identity.authorizedRequest { token ->
            service.recordProductView(token, ShopProductActionRequest(productId))
        }
        Unit
    }
}

class AddressRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        identity.authorizedRequest { token -> service.addresses(token) }.addresses.orEmpty()
    }

    suspend fun save(request: ShopAddressRequest): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't save the address. Try again.")
        identity.authorizedRequest { token ->
            if (request.id.isNullOrBlank()) service.createAddress(token, request)
            else service.updateAddress(token, request)
            service.addresses(token)
        }.addresses.orEmpty()
    }

    suspend fun delete(id: String): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't delete the address. Try again.")
        identity.authorizedRequest { token ->
            service.deleteAddress(token, ShopAddressIdRequest(id))
            service.addresses(token)
        }.addresses.orEmpty()
    }

    suspend fun setDefault(id: String): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't update the address. Try again.")
        identity.authorizedRequest { token ->
            service.setDefaultAddress(token, ShopAddressIdRequest(id))
        }.addresses.orEmpty()
    }
}

class OrderRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopOrderDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        identity.authorizedRequest { token -> service.orders(token) }.orders.orEmpty()
    }

    suspend fun detail(orderId: String): Result<ShopOrderDto> = runCatching {
        val service = api ?: error("Couldn't load this order. Try again.")
        identity.authorizedRequest { token -> service.order(token, orderId) }.order
            ?: error("Couldn't load this order.")
    }

    suspend fun cancel(orderId: String, reason: String): Result<ShopOrderDto> = runCatching {
        val service = api ?: error("This order cannot be cancelled right now.")
        identity.authorizedRequest { token ->
            service.cancelOrder(token, ShopCancelOrderRequest(orderId = orderId, reasonText = reason))
        }.order
            ?: error("This order cannot be cancelled right now.")
    }

    suspend fun revealDeliveryCode(orderId: String): Result<ShopDeliveryCodeDto> = runCatching {
        val service = api ?: error("Delivery code is not available yet.")
        identity.authorizedRequest { token -> service.revealDeliveryCode(token, orderId) }.deliveryCode
            ?: error("Delivery code is not available yet.")
    }

    suspend fun issueOptions(orderId: String): Result<ShopIssueOptionsDto> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        identity.authorizedRequest { token -> service.buyerOrderIssueOptions(token, orderId) }.issueOptions
            ?: error("Buyer Protection is temporarily unavailable.")
    }

    suspend fun issues(orderId: String): Result<List<ShopOrderIssueDto>> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        identity.authorizedRequest { token -> service.buyerOrderIssues(token, orderId) }.issues.orEmpty()
    }

    suspend fun createIssue(orderId: String, orderGroupId: String?, reasonCode: String, description: String): Result<ShopOrderIssueDto> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        val requestId = "android-issue-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.buyerCreateOrderIssue(
                token,
                ShopBuyerCreateIssueRequest(
                    orderId = orderId,
                    orderGroupId = orderGroupId,
                    reasonCode = reasonCode,
                    description = description,
                    idempotencyKey = requestId,
                    requestId = requestId,
                ),
            )
        }.issue ?: error("Buyer Protection is temporarily unavailable.")
    }

    suspend fun postDeliveryActions(orderId: String): Result<ShopPostDeliveryActionsDto> = runCatching {
        val service = api ?: error("Order actions are temporarily unavailable.")
        identity.authorizedRequest { token -> service.postDeliveryActions(token, orderId) }.postDeliveryActions
            ?: error("Order actions are temporarily unavailable.")
    }

    suspend fun reviews(orderId: String): Result<ShopOrderReviewsDto> = runCatching {
        val service = api ?: error("Reviews are temporarily unavailable.")
        identity.authorizedRequest { token -> service.buyerOrderReviews(token, orderId) }.reviews
            ?: ShopOrderReviewsDto()
    }

    suspend fun createProductReview(orderId: String, orderItemId: String, rating: Int, title: String, body: String): Result<ShopReviewDto> = runCatching {
        val service = api ?: error("Review submission is temporarily unavailable.")
        val requestId = "android-product-review-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.createProductReview(
                token,
                ShopReviewCreateRequest(
                    orderId = orderId,
                    orderItemId = orderItemId,
                    rating = rating,
                    title = title,
                    body = body,
                    idempotencyKey = requestId,
                    requestId = requestId,
                ),
            )
        }.productReview ?: error("Review submission is temporarily unavailable.")
    }

    suspend fun createStoreReview(orderId: String, orderGroupId: String, rating: Int, body: String): Result<ShopReviewDto> = runCatching {
        val service = api ?: error("Store review is temporarily unavailable.")
        val requestId = "android-store-review-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.createStoreReview(
                token,
                ShopReviewCreateRequest(
                    orderId = orderId,
                    orderGroupId = orderGroupId,
                    rating = rating,
                    serviceRating = rating,
                    communicationRating = rating,
                    deliveryRating = rating,
                    body = body,
                    idempotencyKey = requestId,
                    requestId = requestId,
                ),
            )
        }.storeReview ?: error("Store review is temporarily unavailable.")
    }

    suspend fun createRefundRequest(orderId: String, orderItemId: String, quantity: Int, reasonCode: String, description: String): Result<ShopRefundRequestDto> = runCatching {
        val service = api ?: error("Refund requests are temporarily unavailable.")
        val requestId = "android-refund-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.createRefundRequest(
                token,
                ShopPostDeliveryRequestBody(orderId, orderItemId = orderItemId, quantity = quantity, reasonCode = reasonCode, description = description, idempotencyKey = requestId, requestId = requestId),
            )
        }.refundRequest ?: error("Refund requests are temporarily unavailable.")
    }

    suspend fun createExchangeRequest(orderId: String, orderItemId: String, quantity: Int, reasonCode: String, description: String): Result<ShopExchangeRequestDto> = runCatching {
        val service = api ?: error("Exchange requests are temporarily unavailable.")
        val requestId = "android-exchange-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.createExchangeRequest(
                token,
                ShopPostDeliveryRequestBody(orderId, orderItemId = orderItemId, quantity = quantity, reasonCode = reasonCode, description = description, idempotencyKey = requestId, requestId = requestId),
            )
        }.exchangeRequest ?: error("Exchange requests are temporarily unavailable.")
    }

    suspend fun requestPayloads(orderId: String): Result<Pair<List<ShopRefundRequestDto>, List<ShopExchangeRequestDto>>> = runCatching {
        val service = api ?: error("Order requests are temporarily unavailable.")
        identity.authorizedRequest { token ->
            val refunds = service.refundRequests(token, orderId).refundRequests.orEmpty()
            val exchanges = service.exchangeRequests(token, orderId).exchangeRequests.orEmpty()
            refunds to exchanges
        }
    }

    suspend fun buyAgain(orderId: String): Result<ShopBuyAgainResultDto> = runCatching {
        val service = api ?: error("Buy Again is temporarily unavailable.")
        val requestId = "android-buy-again-${java.util.UUID.randomUUID()}"
        identity.authorizedRequest { token ->
            service.buyAgain(token, ShopBuyAgainRequest(orderId = orderId, requestId = requestId))
        }.buyAgain
            ?: error("Buy Again is temporarily unavailable.")
    }

    suspend fun receipt(orderId: String): Result<ShopReceiptDto> = runCatching {
        val service = api ?: error("Receipt is temporarily unavailable.")
        identity.authorizedRequest { token -> service.orderReceipt(token, orderId) }.receipt
            ?: error("Receipt is temporarily unavailable.")
    }
}

class ShopNotificationRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopNotificationDto>> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        identity.authorizedRequest { token -> service.notifications(token) }.notifications.orEmpty()
    }

    suspend fun unreadCount(): Result<Int> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        identity.authorizedRequest { token -> service.notificationUnreadCount(token) }.unreadCount ?: 0
    }

    suspend fun markAllRead(): Result<Int> = runCatching {
        val service = api ?: error("Couldn't update notifications.")
        identity.authorizedRequest { token -> service.markAllNotificationsRead(token) }.unreadCount ?: 0
    }
}

class CheckoutRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun create(idempotencyKey: String): Result<ShopCheckoutDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            identity.authorizedRequest { token ->
                service.createCheckout(token, ShopCreateCheckoutRequest(idempotencyKey))
            }.checkout
                ?: error("Checkout could not start. Please refresh your cart and try again.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Checkout could not start. Please refresh your cart and try again."))
        }
    }

    suspend fun createBuyNow(productId: String, variantId: String? = null, quantity: Int = 1, idempotencyKey: String): Result<ShopCheckoutDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            identity.authorizedRequest { token ->
                service.createCheckout(
                    token,
                    ShopCreateCheckoutRequest(
                        idempotencyKey = idempotencyKey,
                        source = "BUY_NOW",
                        productId = productId,
                        variantId = variantId,
                        quantity = quantity.coerceIn(1, 99),
                    ),
                )
            }.checkout ?: error("Checkout could not start. Please refresh your cart and try again.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Checkout could not start. Please refresh your cart and try again."))
        }
    }

    suspend fun confirm(checkoutSessionId: String, addressId: String, paymentMethodCode: String, idempotencyKey: String): Result<ShopOrderDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            identity.authorizedRequest { token ->
                service.confirmCheckout(
                    token,
                    ShopConfirmCheckoutRequest(
                        checkoutSessionId = checkoutSessionId,
                        addressId = addressId,
                        paymentMethodCode = paymentMethodCode,
                        idempotencyKey = idempotencyKey,
                    ),
                )
            }.order ?: error("Order creation is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Order could not be created."))
        }
    }
}

internal fun sellerRequestIdempotencyKey(kind: String, requestId: String, expectedVersion: Int): String =
    "android-seller-$kind-$requestId-v$expectedVersion"

class SellerRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    private suspend fun <T> withSellerAuthRetry(block: suspend (String) -> T): T {
        return identity.authorizedRequest(block)
    }

    suspend fun status(): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't load Seller Center. Try again.")
        val envelope = withSellerAuthRetry { token -> service.sellerStatus(token) }
        (envelope.status ?: envelope.application?.status ?: "not_started") to envelope.application
    }

    suspend fun submit(storeName: String): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't submit seller application. Try again.")
        val envelope = withSellerAuthRetry { token ->
            service.submitSellerApplication(token, ShopSellerApplicationRequest(storeName = storeName))
        }
        (envelope.application?.status ?: envelope.status ?: "submitted") to envelope.application
    }

    suspend fun saveApplicationDraft(request: ShopSellerApplicationRequest): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't save seller application. Try again.")
        val envelope = withSellerAuthRetry { token -> service.saveSellerApplicationDraft(token, request.copy(submit = false)) }
        (envelope.application?.status ?: envelope.status ?: "draft") to envelope.application
    }

    suspend fun submitApplication(request: ShopSellerApplicationRequest): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't submit seller application. Try again.")
        val envelope = withSellerAuthRetry { token -> service.submitCompleteSellerApplication(token, request.copy(submit = true)) }
        (envelope.application?.status ?: envelope.status ?: "submitted") to envelope.application
    }

    suspend fun respondApplicationInfoRequest(message: String): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't send seller application response.")
        val envelope = withSellerAuthRetry { token ->
            service.respondSellerApplicationInfoRequest(
                token,
                ShopSellerApplicationInfoResponseRequest(message = message, requestId = "android-seller-info-${java.util.UUID.randomUUID()}"),
            )
        }
        (envelope.application?.status ?: envelope.status ?: "submitted") to envelope.application
    }

    suspend fun uploadApplicationDocument(
        documentType: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Document upload is temporarily unavailable.")
        require(bytes.isNotEmpty()) { "Choose a document to upload." }
        require(bytes.size <= 10 * 1024 * 1024) { "Document must be 10 MB or smaller." }
        require(mimeType in setOf("image/jpeg", "image/png", "application/pdf")) { "Upload JPG, PNG or PDF only." }
        withSellerAuthRetry { token ->
            service.uploadSellerApplicationDocument(
                authorization = token,
                document = MultipartBody.Part.createFormData(
                    "document",
                    fileName.take(120).ifBlank { "document" },
                    bytes.toRequestBody(mimeType.toMediaType()),
                ),
                documentType = documentType.toRequestBody("text/plain".toMediaType()),
                requestId = "android-seller-document-${java.util.UUID.randomUUID()}".toRequestBody("text/plain".toMediaType()),
            )
        }
        status().getOrThrow()
    }

    suspend fun removeApplicationDocument(documentId: String): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Document removal is temporarily unavailable.")
        val envelope = withSellerAuthRetry { token ->
            service.removeSellerApplicationDocument(
                token,
                ShopSellerApplicationDocumentRemoveRequest(
                    documentId = documentId,
                    requestId = "android-seller-document-remove-${java.util.UUID.randomUUID()}",
                ),
            )
        }
        (envelope.application?.status ?: envelope.status ?: "draft") to envelope.application
    }

    suspend fun orderGroups(): Result<List<ShopOrderGroupDto>> = runCatching {
        val service = api ?: error("Couldn't load seller orders.")
        withSellerAuthRetry { token -> service.sellerOrderGroups(token) }.sellerOrderGroups.orEmpty()
    }

    suspend fun requests(): Result<ShopSellerRequestsDto> = runCatching {
        val service = api ?: error("Couldn't load returns and requests.")
        withSellerAuthRetry { token -> service.sellerRequests(token) }.sellerRequests ?: ShopSellerRequestsDto()
    }

    suspend fun respondToRequest(kind: String, requestId: String, message: String, expectedVersion: Int): Result<ShopSellerRequestsDto> = runCatching {
        val service = api ?: error("Request response is temporarily unavailable.")
        val body = ShopSellerRequestResponseRequest(
            requestId = requestId,
            message = message,
            idempotencyKey = sellerRequestIdempotencyKey(kind, requestId, expectedVersion),
            expectedVersion = expectedVersion,
        )
        withSellerAuthRetry { token ->
            if (kind == "exchange") service.sellerRespondExchangeRequest(token, body) else service.sellerRespondRefundRequest(token, body)
        }.sellerRequests ?: ShopSellerRequestsDto()
    }

    suspend fun products(status: String = "all"): Result<List<ShopSellerProductDto>> = runCatching {
        val service = api ?: error("Couldn't load seller products.")
        withSellerAuthRetry { token -> service.sellerProducts(token, status) }.sellerProducts.orEmpty()
    }

    suspend fun createProduct(requestId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Product creation is temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerCreateProduct(token, ShopSellerProductCreateRequest(requestId)) }.sellerProduct
            ?: error("Product creation is temporarily unavailable.")
    }

    suspend fun saveProduct(productId: String, payload: ShopSellerProductDraftPayload, requestId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Product save is temporarily unavailable.")
        try {
            withSellerAuthRetry { token -> service.sellerUpdateProduct(token, ShopSellerProductUpdateRequest(productId, payload, requestId)) }.sellerProduct
                ?: error("Product save is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Product save is temporarily unavailable."))
        }
    }

    suspend fun submitProduct(productId: String, requestId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Product submission is temporarily unavailable.")
        try {
            withSellerAuthRetry { token -> service.sellerSubmitProduct(token, ShopSellerProductSubmitRequest(productId, requestId)) }.sellerProduct
                ?: error("Product submission is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Product is missing required details."))
        }
    }

    suspend fun uploadProductMedia(
        productId: String,
        bytes: ByteArray,
        mimeType: String,
        width: Int,
        height: Int,
        altText: String,
        requestId: String,
    ): Result<ShopProductImageDto> = runCatching {
        val service = api ?: error("Image upload is temporarily unavailable.")
        val mediaType = mimeType.toMediaType()
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val imageBody = bytes.toRequestBody(mediaType)
        val imagePart = MultipartBody.Part.createFormData("image", "product-$requestId.$extension", imageBody)
        val textMediaType = "text/plain".toMediaType()
        try {
            withSellerAuthRetry { token ->
                service.sellerUploadProductMedia(
                    authorization = token,
                    image = imagePart,
                    productId = productId.toRequestBody(textMediaType),
                    requestId = requestId.toRequestBody(textMediaType),
                    altText = altText.toRequestBody(textMediaType),
                    width = width.toString().toRequestBody(textMediaType),
                    height = height.toString().toRequestBody(textMediaType),
                )
            }.sellerProductImage ?: error("Image upload is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Image upload is temporarily unavailable."))
        }
    }

    suspend fun deleteProductMedia(productId: String, imageId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Image removal is temporarily unavailable.")
        try {
            withSellerAuthRetry { token -> service.sellerDeleteProductMedia(token, ShopSellerProductMediaDeleteRequest(productId, imageId)) }.sellerProduct
                ?: error("Image removal is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Image removal is temporarily unavailable."))
        }
    }

    suspend fun reorderProductMedia(productId: String, imageIds: List<String>, primaryImageId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Image order is temporarily unavailable.")
        try {
            withSellerAuthRetry { token -> service.sellerReorderProductMedia(token, ShopSellerProductMediaReorderRequest(productId, imageIds, primaryImageId)) }.sellerProduct
                ?: error("Image order is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Image order is temporarily unavailable."))
        }
    }

    suspend fun transition(orderGroupId: String, action: String, reason: String = "", reasonCode: String? = null, carrier: String? = null, trackingNumber: String? = null): Result<List<ShopOrderGroupDto>> = runCatching {
        val service = api ?: error("Seller action is temporarily unavailable.")
        val body = ShopSellerOrderActionRequest(
            orderGroupId = orderGroupId,
            reason = reason,
            reasonCode = reasonCode,
            requestId = if (action == "reject") "android-reject-${java.util.UUID.randomUUID()}" else null,
            carrier = carrier,
            trackingNumber = trackingNumber,
        )
        try {
            withSellerAuthRetry { token ->
                when (action) {
                    "accept" -> service.sellerAcceptOrderGroup(token, body)
                    "reject" -> service.sellerRejectOrderGroup(token, body)
                    "processing" -> service.sellerMarkProcessing(token, body)
                    "packed" -> service.sellerMarkPacked(token, body)
                    "handed_to_carrier" -> service.sellerHandToCarrier(token, body)
                    "delivered" -> service.sellerVerifyDeliveryCode(
                        token,
                        ShopSellerDeliveryCodeRequest(orderGroupId = orderGroupId, deliveryCode = trackingNumber.orEmpty()),
                    )
                    else -> error("Seller action is not supported.")
                }
            }
        } catch (error: Throwable) {
            if (error is HttpException && error.code() == 409 && action != "delivered") {
                return@runCatching withSellerAuthRetry { token -> service.sellerOrderGroups(token) }.sellerOrderGroups.orEmpty()
            }
            throw IllegalStateException(error.shopSellerActionMessage("Seller action is not allowed for this order state."))
        }
        withSellerAuthRetry { token -> service.sellerOrderGroups(token) }.sellerOrderGroups.orEmpty()
    }

    suspend fun business(storeId: String? = null, start: String? = null, end: String? = null): Result<ShopSellerBusinessDto> = runCatching {
        val service = api ?: error("Seller tools are temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerBusiness(token, storeId = storeId, start = start, end = end) }.sellerBusiness
            ?: error("Seller tools are temporarily unavailable.")
    }

    suspend fun businessAction(action: String, body: Map<String, Any?>): Result<Unit> = runCatching {
        val service = api ?: error("Seller action is temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerBusinessAction(token, action, body) }
        Unit
    }

    suspend fun updateInventory(request: ShopSellerInventoryUpdateRequest): Result<Unit> = runCatching {
        val service = api ?: error("Inventory update is temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerInventoryUpdate(token, body = request) }
        Unit
    }

    suspend fun replyToReview(request: ShopSellerReviewReplyRequest): Result<Unit> = runCatching {
        val service = api ?: error("Review reply is temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerReviewReply(token, body = request) }
        Unit
    }

    suspend fun saveCustomerNote(request: ShopSellerCustomerNoteRequest): Result<Unit> = runCatching {
        val service = api ?: error("Customer note is temporarily unavailable.")
        withSellerAuthRetry { token -> service.sellerCustomerNote(token, body = request) }
        Unit
    }

    suspend fun uploadStoreAsset(storeId: String, kind: String, bytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        val service = api ?: error("Store image upload is temporarily unavailable.")
        require(kind in setOf("logo", "cover")) { "Choose a valid store image type." }
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Use a JPG, PNG or WebP image." }
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "Store image must be under 5 MB." }
        val extension = when (mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val media = mimeType.toMediaType()
        val text = "text/plain".toMediaType()
        withSellerAuthRetry { token ->
            service.sellerStoreAsset(
                authorization = token,
                asset = MultipartBody.Part.createFormData("asset", "$kind.$extension", bytes.toRequestBody(media)),
                storeId = storeId.toRequestBody(text),
                kind = kind.toRequestBody(text),
            )
        }
        Unit
    }
}

class ShopMessageRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    private suspend fun <T> authorized(block: suspend (String) -> T): T =
        identity.authorizedRequest(block)

    suspend fun sellerConversations(search: String = "", archived: Boolean = false): Result<Pair<List<ShopConversationDto>, Int>> =
        runCatching {
            val service = api ?: error("Seller messages are temporarily unavailable.")
            val envelope = authorized { token -> service.sellerConversations(token, search.trim(), archived) }
            envelope.conversations.orEmpty() to (envelope.unreadCount ?: 0)
        }

    suspend fun buyerConversations(search: String = "", archived: Boolean = false): Result<List<ShopConversationDto>> =
        runCatching {
            val service = api ?: error("Messages are temporarily unavailable.")
            authorized { token -> service.buyerConversations(token, search.trim(), archived) }.conversations.orEmpty()
        }

    suspend fun open(
        storeId: String? = null,
        productId: String? = null,
        orderGroupId: String? = null,
    ): Result<ShopConversationDto> = runCatching {
        val service = api ?: error("Conversation could not be started.")
        authorized { token ->
            service.openConversation(token, ShopOpenConversationRequest(storeId, productId, orderGroupId))
        }.conversation ?: error("Conversation could not be started.")
    }

    suspend fun messages(conversationId: String, before: String? = null): Result<Pair<ShopConversationDto?, List<ShopMessageDto>>> =
        runCatching {
            val service = api ?: error("Messages are temporarily unavailable.")
            val envelope = authorized { token -> service.conversationMessages(token, conversationId, before) }
            envelope.conversation to envelope.messages.orEmpty()
        }

    suspend fun send(conversationId: String, body: String): Result<ShopMessageDto> = runCatching {
        val message = body.trim()
        require(message.isNotBlank()) { "Write a message first." }
        require(message.length <= 4000) { "Message is too long." }
        val service = api ?: error("Message could not be sent.")
        authorized { token ->
            service.sendMessage(
                token,
                ShopSendMessageRequest(
                    conversationId = conversationId,
                    body = message,
                    clientMessageId = "android-shop-message-${java.util.UUID.randomUUID()}",
                ),
            )
        }.message ?: error("Message could not be sent.")
    }

    suspend fun sendAttachment(
        conversationId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        caption: String = "",
    ): Result<ShopMessageDto> = runCatching {
        require(conversationId.isNotBlank()) { "Conversation is unavailable." }
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) { "Choose a file smaller than 10 MB." }
        val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain")
        require(mimeType in allowedTypes) { "This file type is not supported." }
        val service = api ?: error("Attachment could not be sent.")
        val requestId = "android-shop-attachment-${java.util.UUID.randomUUID()}"
        val safeName = fileName.take(120).ifBlank { "attachment" }
        val attachment = MultipartBody.Part.createFormData(
            "attachment",
            safeName,
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        val textType = "text/plain".toMediaType()
        authorized { token ->
            service.uploadMessageAttachment(
                authorization = token,
                attachment = attachment,
                conversationId = conversationId.toRequestBody(textType),
                clientMessageId = requestId.toRequestBody(textType),
                body = caption.trim().take(4000).toRequestBody(textType),
            )
        }.message ?: error("Attachment could not be sent.")
    }

    suspend fun update(conversationId: String, action: String, hours: Int? = null): Result<ShopConversationDto?> =
        runCatching {
            val service = api ?: error("Conversation could not be updated.")
            authorized { token ->
                service.updateConversation(token, ShopConversationActionRequest(conversationId, action, hours))
            }.conversation
        }
}

class ShopAdminRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    private fun Throwable.adminMessage(fallback: String): String {
        if (this is HttpException) {
            val body = response()?.errorBody()?.string().orEmpty()
            val errorObject = runCatching {
                JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")
            }.getOrNull()
            val message = errorObject?.get("message")?.asString.orEmpty()
            val code = errorObject?.get("code")?.asString.orEmpty()
            return when {
                message.isNotBlank() -> ShopStateReducers.safeErrorMessage(message, fallback)
                code == "admin_forbidden" -> "You do not have access to AdisGebeya Admin."
                code == "reason_required" -> "A clear reason is required."
                code == "invalid_state" -> "This item has already moved to another state."
                else -> fallback
            }
        }
        return ShopStateReducers.safeErrorMessage(message, fallback)
    }

    private suspend fun <T> withAdminAuth(block: suspend (String) -> T): T {
        return identity.authorizedRequest(block)
    }

    suspend fun session(): Result<ShopAdminSessionDto> = runCatching {
        val service = api ?: error("AdisGebeya Admin is temporarily unavailable.")
        try {
            withAdminAuth { token -> service.adminSession(token) }.adminSession
                ?: error("AdisGebeya Admin is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("You do not have access to AdisGebeya Admin."))
        }
    }

    suspend fun dashboard(): Result<ShopAdminDashboardDto> = runCatching {
        val service = api ?: error("Couldn't load Admin dashboard.")
        try {
            withAdminAuth { token -> service.adminDashboard(token) }.dashboard
                ?: error("Couldn't load Admin dashboard.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load Admin dashboard."))
        }
    }

    suspend fun sellerApplications(status: String = "submitted", search: String = ""): Result<List<ShopAdminSellerApplicationDto>> = runCatching {
        val service = api ?: error("Couldn't load seller applications.")
        try {
            withAdminAuth { token -> service.adminSellerApplications(token, status, search) }.sellerApplications.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load seller applications."))
        }
    }

    suspend fun reviewSeller(request: ShopAdminReviewSellerRequest): Result<ShopAdminSellerApplicationDto?> = runCatching {
        val service = api ?: error("Seller review is temporarily unavailable.")
        try {
            withAdminAuth { token -> service.adminReviewSeller(token, request) }.sellerApplicationDetail
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Seller review could not be saved."))
        }
    }

    suspend fun products(status: String = "pending", search: String = ""): Result<List<ShopAdminProductDto>> = runCatching {
        val service = api ?: error("Couldn't load products for review.")
        try {
            withAdminAuth { token -> service.adminProducts(token, status, search) }.products.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load products for review."))
        }
    }

    suspend fun productDetail(productId: String): Result<ShopAdminProductDetailDto> = runCatching {
        val service = api ?: error("Couldn't load product details.")
        try {
            withAdminAuth { token -> service.adminProductDetail(token, productId) }.productDetail
                ?: error("Couldn't load product details.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load product details."))
        }
    }

    suspend fun reviewProduct(request: ShopAdminReviewProductRequest): Result<ShopAdminProductDetailDto?> = runCatching {
        val service = api ?: error("Product review is temporarily unavailable.")
        try {
            withAdminAuth { token -> service.adminReviewProduct(token, request) }.productDetail
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Product review could not be saved."))
        }
    }

    suspend fun orders(search: String = ""): Result<List<ShopAdminOrderDto>> = runCatching {
        val service = api ?: error("Couldn't load orders.")
        try {
            withAdminAuth { token -> service.adminOrders(token, search) }.adminOrders.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load orders."))
        }
    }

    suspend fun orderDetail(orderId: String): Result<ShopAdminOrderDetailDto> = runCatching {
        val service = api ?: error("Couldn't load order details.")
        try {
            withAdminAuth { token -> service.adminOrderDetail(token, orderId) }.orderDetail
                ?: error("Couldn't load order details.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load order details."))
        }
    }

    suspend fun updateOrderIssue(issueId: String, status: String, buyerVisibleMessage: String, privateNote: String): Result<ShopOrderIssueDto?> = runCatching {
        val service = api ?: error("Issue update is temporarily unavailable.")
        val requestId = "android-admin-issue-${java.util.UUID.randomUUID()}"
        try {
            withAdminAuth { token ->
                service.adminUpdateOrderIssue(
                    token,
                    ShopAdminIssueUpdateRequest(issueId, status, buyerVisibleMessage, privateNote, requestId),
                )
            }.issue
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Issue update could not be saved."))
        }
    }

    suspend fun auditLog(): Result<List<ShopAdminAuditLogDto>> = runCatching {
        val service = api ?: error("Couldn't load audit log.")
        try {
            withAdminAuth { token -> service.adminAuditLog(token) }.auditLog.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load audit log."))
        }
    }

    suspend fun users(): Result<List<ShopAdminUserDto>> = runCatching {
        val service = api ?: error("Couldn't load admin users.")
        try {
            withAdminAuth { token -> service.adminUsers(token) }.adminUsers.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load admin users."))
        }
    }

    suspend fun stores(status: String = "all", search: String = ""): Result<List<ShopAdminStoreDto>> = runCatching {
        val service = api ?: error("Couldn't load stores.")
        try {
            withAdminAuth { token -> service.adminStores(token, status, search) }.stores.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load stores."))
        }
    }

    suspend fun storeDetail(storeId: String): Result<ShopAdminStoreDetailDto> = runCatching {
        val service = api ?: error("Couldn't load store detail.")
        try {
            withAdminAuth { token -> service.adminStoreDetail(token, storeId) }.storeDetail
                ?: error("Couldn't load store detail.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load store detail."))
        }
    }

    suspend fun updateStoreStatus(storeId: String, action: String, reason: String): Result<ShopAdminStoreDetailDto?> = runCatching {
        val service = api ?: error("Store moderation is temporarily unavailable.")
        val requestId = "android-admin-store-${java.util.UUID.randomUUID()}"
        try {
            withAdminAuth { token ->
                service.adminUpdateStoreStatus(
                    token,
                    ShopAdminStoreStatusRequest(
                        storeId = storeId,
                        action = action,
                        sellerVisibleMessage = reason,
                        privateNote = if (action == "warn") "" else reason,
                        requestId = requestId,
                    ),
                )
            }.storeDetail
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Store moderation could not be saved."))
        }
    }

    suspend fun refunds(status: String = "all", search: String = ""): Result<List<ShopRefundRequestDto>> = runCatching {
        val service = api ?: error("Couldn't load refunds.")
        try {
            withAdminAuth { token -> service.adminRefunds(token, status, search) }.refundRequests.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load refunds."))
        }
    }

    suspend fun exchanges(status: String = "all", search: String = ""): Result<List<ShopExchangeRequestDto>> = runCatching {
        val service = api ?: error("Couldn't load exchanges.")
        try {
            withAdminAuth { token -> service.adminExchanges(token, status, search) }.exchangeRequests.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load exchanges."))
        }
    }

    suspend fun updateRefund(requestId: String, action: String, reason: String): Result<ShopRefundRequestDto?> = runCatching {
        val service = api ?: error("Refund update is temporarily unavailable.")
        val body = ShopAdminRequestUpdateRequest(requestId = requestId, action = action, reason = reason, mutationId = "android-admin-refund-${java.util.UUID.randomUUID()}")
        try {
            withAdminAuth { token -> service.adminUpdateRefund(token, body) }.refundRequest
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Refund update could not be saved."))
        }
    }

    suspend fun updateExchange(requestId: String, action: String, reason: String): Result<ShopExchangeRequestDto?> = runCatching {
        val service = api ?: error("Exchange update is temporarily unavailable.")
        val body = ShopAdminRequestUpdateRequest(requestId = requestId, action = action, reason = reason, mutationId = "android-admin-exchange-${java.util.UUID.randomUUID()}")
        try {
            withAdminAuth { token -> service.adminUpdateExchange(token, body) }.exchangeRequest
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Exchange update could not be saved."))
        }
    }

    suspend fun reviews(status: String = "all", type: String = "all"): Result<List<ShopAdminReviewModerationDto>> = runCatching {
        val service = api ?: error("Couldn't load review moderation.")
        try {
            withAdminAuth { token -> service.adminReviews(token, status, type) }.adminReviews.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load review moderation."))
        }
    }

    suspend fun moderateReview(reviewId: String, reviewType: String, action: String, reason: String): Result<ShopAdminReviewModerationDto?> = runCatching {
        val service = api ?: error("Review moderation is temporarily unavailable.")
        val body = ShopAdminReviewModerationRequest(reviewId = reviewId, reviewType = reviewType, action = action, reason = reason, requestId = "android-admin-review-${java.util.UUID.randomUUID()}")
        try {
            withAdminAuth { token -> service.adminModerateReview(token, body) }.adminReview
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Review moderation could not be saved."))
        }
    }
}

class ProfileShoppingRepository {
    fun protectedFeaturesAvailable(): Boolean = ShopFunctionClient.isConfigured
}
