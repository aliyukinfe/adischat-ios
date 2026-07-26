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

    suspend fun currentProfile(): Result<ShopProfileDto?> = runCatching {
        val token = sessionToken().getOrThrow()
        val service = functionApi ?: error("Shop account sync is temporarily unavailable.")
        val envelope = service.currentProfile("Bearer $token")
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
        val token = identity.sessionToken().getOrThrow()
        service.cart("Bearer $token").cart ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun add(productId: String, quantity: Int = 1): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        val token = identity.sessionToken().getOrThrow()
        service.addCartItem("Bearer $token", ShopCartItemRequest(productId = productId, quantity = quantity)).cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun update(itemId: String, quantity: Int): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        service.updateCartItem(identity.authorization(), ShopCartItemUpdateRequest(itemId = itemId, quantity = quantity)).cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun remove(itemId: String): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        service.removeCartItem(identity.authorization(), ShopCartItemIdRequest(itemId = itemId)).cart
            ?: error("Your cart is temporarily unavailable.")
    }

    suspend fun clear(): Result<ShopCartDto> = runCatching {
        val service = api ?: error("Your cart is temporarily unavailable.")
        service.clearCart(identity.authorization()).cart ?: error("Your cart is temporarily unavailable.")
    }

    fun isAvailable(): Boolean = api != null
}

class FavoriteRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        service.favorites(identity.authorization()).favorites.orEmpty()
    }

    suspend fun add(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't save this right now. Try again.")
        service.addFavorite(identity.authorization(), ShopProductActionRequest(productId))
        service.favorites(identity.authorization()).favorites.orEmpty()
    }

    suspend fun remove(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't update saved products. Try again.")
        service.removeFavorite(identity.authorization(), ShopProductActionRequest(productId))
        service.favorites(identity.authorization()).favorites.orEmpty()
    }
}

class RecentlyViewedRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopRecentlyViewedDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        service.recentlyViewed(identity.authorization()).recentlyViewed.orEmpty()
    }

    suspend fun record(productId: String): Result<Unit> = runCatching {
        val service = api ?: return@runCatching
        service.recordProductView(identity.authorization(), ShopProductActionRequest(productId))
        Unit
    }
}

class AddressRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        service.addresses(identity.authorization()).addresses.orEmpty()
    }

    suspend fun save(request: ShopAddressRequest): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't save the address. Try again.")
        if (request.id.isNullOrBlank()) service.createAddress(identity.authorization(), request)
        else service.updateAddress(identity.authorization(), request)
        service.addresses(identity.authorization()).addresses.orEmpty()
    }

    suspend fun delete(id: String): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't delete the address. Try again.")
        service.deleteAddress(identity.authorization(), ShopAddressIdRequest(id))
        service.addresses(identity.authorization()).addresses.orEmpty()
    }

    suspend fun setDefault(id: String): Result<List<ShopAddressDto>> = runCatching {
        val service = api ?: error("Couldn't update the address. Try again.")
        service.setDefaultAddress(identity.authorization(), ShopAddressIdRequest(id)).addresses.orEmpty()
    }
}

class OrderRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopOrderDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        service.orders(identity.authorization()).orders.orEmpty()
    }

    suspend fun detail(orderId: String): Result<ShopOrderDto> = runCatching {
        val service = api ?: error("Couldn't load this order. Try again.")
        service.order(identity.authorization(), orderId).order ?: error("Couldn't load this order.")
    }

    suspend fun cancel(orderId: String, reason: String): Result<ShopOrderDto> = runCatching {
        val service = api ?: error("This order cannot be cancelled right now.")
        service.cancelOrder(identity.authorization(), ShopCancelOrderRequest(orderId = orderId, reasonText = reason)).order
            ?: error("This order cannot be cancelled right now.")
    }

    suspend fun revealDeliveryCode(orderId: String): Result<ShopDeliveryCodeDto> = runCatching {
        val service = api ?: error("Delivery code is not available yet.")
        service.revealDeliveryCode(identity.authorization(), orderId).deliveryCode ?: error("Delivery code is not available yet.")
    }

    suspend fun issueOptions(orderId: String): Result<ShopIssueOptionsDto> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        service.buyerOrderIssueOptions(identity.authorization(), orderId).issueOptions
            ?: error("Buyer Protection is temporarily unavailable.")
    }

    suspend fun issues(orderId: String): Result<List<ShopOrderIssueDto>> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        service.buyerOrderIssues(identity.authorization(), orderId).issues.orEmpty()
    }

    suspend fun createIssue(orderId: String, orderGroupId: String?, reasonCode: String, description: String): Result<ShopOrderIssueDto> = runCatching {
        val service = api ?: error("Buyer Protection is temporarily unavailable.")
        val requestId = "android-issue-${java.util.UUID.randomUUID()}"
        service.buyerCreateOrderIssue(
            identity.authorization(),
            ShopBuyerCreateIssueRequest(
                orderId = orderId,
                orderGroupId = orderGroupId,
                reasonCode = reasonCode,
                description = description,
                idempotencyKey = requestId,
                requestId = requestId,
            ),
        ).issue ?: error("Buyer Protection is temporarily unavailable.")
    }
}

class ShopNotificationRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopNotificationDto>> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        service.notifications(identity.authorization()).notifications.orEmpty()
    }

    suspend fun unreadCount(): Result<Int> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        service.notificationUnreadCount(identity.authorization()).unreadCount ?: 0
    }

    suspend fun markAllRead(): Result<Int> = runCatching {
        val service = api ?: error("Couldn't update notifications.")
        service.markAllNotificationsRead(identity.authorization()).unreadCount ?: 0
    }
}

class CheckoutRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun create(idempotencyKey: String): Result<ShopCheckoutDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            service.createCheckout(identity.authorization(), ShopCreateCheckoutRequest(idempotencyKey)).checkout
                ?: error("Checkout could not start. Please refresh your cart and try again.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Checkout could not start. Please refresh your cart and try again."))
        }
    }

    suspend fun createBuyNow(productId: String, variantId: String? = null, quantity: Int = 1, idempotencyKey: String): Result<ShopCheckoutDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            service.createCheckout(
                identity.authorization(),
                ShopCreateCheckoutRequest(
                    idempotencyKey = idempotencyKey,
                    source = "BUY_NOW",
                    productId = productId,
                    variantId = variantId,
                    quantity = quantity.coerceIn(1, 99),
                ),
            ).checkout ?: error("Checkout could not start. Please refresh your cart and try again.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Checkout could not start. Please refresh your cart and try again."))
        }
    }

    suspend fun confirm(checkoutSessionId: String, addressId: String, paymentMethodCode: String, idempotencyKey: String): Result<ShopOrderDto> = runCatching {
        try {
            val service = api ?: error("Checkout could not start. Please refresh your cart and try again.")
            service.confirmCheckout(
                identity.authorization(),
                ShopConfirmCheckoutRequest(
                    checkoutSessionId = checkoutSessionId,
                    addressId = addressId,
                    paymentMethodCode = paymentMethodCode,
                    idempotencyKey = idempotencyKey,
                ),
            ).order ?: error("Order creation is temporarily unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopCheckoutMessage("Order could not be created."))
        }
    }
}

class SellerRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    private suspend fun <T> withSellerAuthRetry(block: suspend (String) -> T): T {
        return try {
            block(identity.authorization())
        } catch (error: Throwable) {
            if (error is HttpException && error.code() == 401) {
                identity.clear()
                block(identity.authorization())
            } else {
                throw error
            }
        }
    }

    suspend fun status(): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't load Seller Center. Try again.")
        val envelope = withSellerAuthRetry { token -> service.sellerStatus(token) }
        (envelope.status ?: envelope.application?.status ?: "not_started") to envelope.application
    }

    suspend fun submit(storeName: String): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't submit seller application. Try again.")
        val envelope = service.submitSellerApplication(identity.authorization(), ShopSellerApplicationRequest(storeName = storeName))
        (envelope.application?.status ?: envelope.status ?: "submitted") to envelope.application
    }

    suspend fun orderGroups(): Result<List<ShopOrderGroupDto>> = runCatching {
        val service = api ?: error("Couldn't load seller orders.")
        withSellerAuthRetry { token -> service.sellerOrderGroups(token) }.sellerOrderGroups.orEmpty()
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
        return try {
            block(identity.authorization())
        } catch (error: Throwable) {
            if (error is HttpException && error.code() == 401) {
                identity.clear()
                block(identity.authorization())
            } else {
                throw error
            }
        }
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
}

class ProfileShoppingRepository {
    fun protectedFeaturesAvailable(): Boolean = ShopFunctionClient.isConfigured
}
