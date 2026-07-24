package com.adischat.app.feature.shop.data

import com.adischat.app.network.CloudflareClient
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonParser
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
    suspend fun status(): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't load Seller Center. Try again.")
        val envelope = service.sellerStatus(identity.authorization())
        (envelope.status ?: envelope.application?.status ?: "not_started") to envelope.application
    }

    suspend fun submit(storeName: String): Result<Pair<String, ShopSellerApplicationDto?>> = runCatching {
        val service = api ?: error("Couldn't submit seller application. Try again.")
        val envelope = service.submitSellerApplication(identity.authorization(), ShopSellerApplicationRequest(storeName = storeName))
        (envelope.application?.status ?: envelope.status ?: "submitted") to envelope.application
    }

    suspend fun orderGroups(): Result<List<ShopOrderGroupDto>> = runCatching {
        val service = api ?: error("Couldn't load seller orders.")
        service.sellerOrderGroups(identity.authorization()).sellerOrderGroups.orEmpty()
    }

    suspend fun transition(orderGroupId: String, action: String, reason: String = "", carrier: String? = null, trackingNumber: String? = null): Result<List<ShopOrderGroupDto>> = runCatching {
        val service = api ?: error("Seller action is temporarily unavailable.")
        val body = ShopSellerOrderActionRequest(orderGroupId = orderGroupId, reason = reason, carrier = carrier, trackingNumber = trackingNumber)
        when (action) {
            "accept" -> service.sellerAcceptOrderGroup(identity.authorization(), body)
            "reject" -> service.sellerRejectOrderGroup(identity.authorization(), body)
            "processing" -> service.sellerMarkProcessing(identity.authorization(), body)
            "packed" -> service.sellerMarkPacked(identity.authorization(), body)
            "handed_to_carrier" -> service.sellerHandToCarrier(identity.authorization(), body)
            "delivered" -> service.sellerVerifyDeliveryCode(
                identity.authorization(),
                ShopSellerDeliveryCodeRequest(orderGroupId = orderGroupId, deliveryCode = trackingNumber.orEmpty()),
            )
            else -> error("Seller action is not supported.")
        }
        service.sellerOrderGroups(identity.authorization()).sellerOrderGroups.orEmpty()
    }
}

class ProfileShoppingRepository {
    fun protectedFeaturesAvailable(): Boolean = ShopFunctionClient.isConfigured
}
