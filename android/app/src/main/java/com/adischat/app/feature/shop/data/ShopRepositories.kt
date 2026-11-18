package com.adischat.app.feature.shop.data

import com.adischat.app.BuildConfig
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
            val banners = if (BuildConfig.QA_BUILD) emptyList() else service.banners()
            val categories = if (BuildConfig.QA_BUILD) service.qaCategories() else service.categories()
            val sellingUnits = if (BuildConfig.QA_BUILD) emptyList() else service.sellingUnits()
            val products = if (BuildConfig.QA_BUILD) service.qaCatalogPage(mapOf("p_sort" to "newest", "p_limit" to 100))
                else if (ShopReviewedMedia.enabled) service.reviewedProducts().map(ShopReviewedMedia::product)
                else service.products()
            val stores = if (BuildConfig.QA_BUILD) service.qaStores()
                else if (ShopReviewedMedia.enabled) service.reviewedStores().map(ShopReviewedMedia::store)
                else service.stores()
            val verified = if (ShopBenefitFlags.buyerBadgesEnabled && products.isNotEmpty()) {
                ShopBenefitsRepository().catalogBenefits(products.map { it.id }).getOrDefault(emptyMap())
            } else emptyMap()
            ShopHomeState(
                loading = false,
                banners = banners,
                categories = categories,
                sellingUnits = sellingUnits,
                products = products.map { it.copy(verifiedBenefits = verified[it.id].orEmpty()) },
                stores = stores,
            )
        } catch (error: Throwable) {
            android.util.Log.w("AdisShop", "Native shop home load failed", error)
            ShopHomeState(loading = false, error = true)
        }
    }
}

class CategoryRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun activeCategories(): List<ShopCategoryDto> = api?.let { if (BuildConfig.QA_BUILD) it.qaCategories() else it.categories() }.orEmpty()
    suspend fun activeSellingUnits(): List<ShopSellingUnitDto> = if (BuildConfig.QA_BUILD) emptyList() else api?.sellingUnits().orEmpty()
    suspend fun attributes(categoryId: String): Result<List<ShopCategoryAttributeDto>> = runCatching {
        require(categoryId.isNotBlank())
        val service = api ?: error("Marketplace is unavailable.")
        service.categoryAttributes(ShopCategoryAttributeRequest(categoryId))
    }
}

private fun qaCatalogSort(order: String): String = when {
    order.startsWith("created_at.desc") -> "newest"
    order.startsWith("created_at.asc") -> "oldest"
    order.startsWith("price_minor.asc") -> "price_low"
    order.startsWith("price_minor.desc") -> "price_high"
    else -> error("This sort is unavailable in QA until reviewed data supports it.")
}

class ProductRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun approvedProducts(): List<ShopProductDto> = api?.let { service ->
        if (BuildConfig.QA_BUILD) service.qaCatalogPage(mapOf("p_sort" to "newest", "p_limit" to 100))
        else if (ShopReviewedMedia.enabled) service.reviewedProducts().map(ShopReviewedMedia::product)
        else service.products()
    }.orEmpty()
    suspend fun searchCatalogPage(
        term: String,
        categorySlug: String?,
        inStockOnly: Boolean,
        order: String,
        offset: Int,
        pageSize: Int,
    ): Result<List<ShopProductDto>> = runCatching {
        val service = api ?: error("Marketplace is unavailable.")
        val safeTerm = term.trim().take(80)
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        require(safeTerm.length >= 2) { "Enter at least two letters or numbers." }
        require(offset >= 0 && pageSize in 1..24)
        if (BuildConfig.QA_BUILD) return@runCatching service.qaCatalogPage(mapOf(
            "p_term" to safeTerm, "p_category_slug" to categorySlug,
            "p_in_stock" to inStockOnly, "p_sort" to qaCatalogSort(order),
            "p_limit" to pageSize, "p_offset" to offset,
        ))
        val text = "or(title.ilike.*$safeTerm*,brand.ilike.*$safeTerm*)"
        val results = if (ShopReviewedMedia.enabled) service.reviewedSearchCatalogPage(
            textFilter = if (inStockOnly) null else "(title.ilike.*$safeTerm*,brand.ilike.*$safeTerm*)",
            combinedFilter = if (inStockOnly) "($text,or(stock_status.is.null,stock_status.neq.out_of_stock))" else null,
            categoryFilter = categorySlug?.let { "eq.$it" },
            order = order,
            limit = pageSize,
            offset = offset,
        ) else service.searchCatalogPage(
            textFilter = if (inStockOnly) null else "(title.ilike.*$safeTerm*,brand.ilike.*$safeTerm*)",
            combinedFilter = if (inStockOnly) "($text,or(stock_status.is.null,stock_status.neq.out_of_stock))" else null,
            categoryFilter = categorySlug?.let { "eq.$it" },
            order = order,
            limit = pageSize,
            offset = offset,
        )
        if (ShopReviewedMedia.enabled) results.map(ShopReviewedMedia::product) else results
    }
    suspend fun search(
        categorySlugs: Set<String>,
        minimumPriceMinor: Int?,
        maximumPriceMinor: Int?,
        minimumRating: Int,
        inStockOnly: Boolean,
        discountedOnly: Boolean,
        order: String,
    ): Result<List<ShopProductDto>> = runCatching {
        val service = api ?: error("Marketplace is unavailable.")
        if (BuildConfig.QA_BUILD) {
            require(minimumRating == 0 && !discountedOnly && categorySlugs.size <= 1) {
                "These filters are unavailable in QA until reviewed data supports them."
            }
            return@runCatching service.qaCatalogPage(mapOf(
                "p_category_slug" to categorySlugs.singleOrNull(),
                "p_min_price" to minimumPriceMinor, "p_max_price" to maximumPriceMinor,
                "p_in_stock" to inStockOnly, "p_sort" to qaCatalogSort(order),
                "p_limit" to 100,
            ))
        }
        val category = categorySlugs.takeIf { it.isNotEmpty() }?.joinToString(prefix = "in.(", postfix = ")")
        val priceParts = buildList {
            minimumPriceMinor?.let { add("price_minor.gte.$it") }
            maximumPriceMinor?.let { add("price_minor.lte.$it") }
        }
        val categoryFilter = category
        val priceFilter = priceParts.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")")
        val ratingFilter = minimumRating.takeIf { it > 0 }?.let { "gte.$it" }
        val stockFilter = if (inStockOnly) "(stock_status.is.null,stock_status.neq.out_of_stock)" else null
        val discountFilter = if (discountedOnly) "gt.0" else null
        val results = if (ShopReviewedMedia.enabled) service.reviewedSearchProducts(
            categoryFilter = categoryFilter,
            priceFilter = priceFilter,
            ratingFilter = ratingFilter,
            stockFilter = stockFilter,
            discountFilter = discountFilter,
            order = order,
        ) else service.searchProducts(
            categoryFilter = categoryFilter,
            priceFilter = priceFilter,
            ratingFilter = ratingFilter,
            stockFilter = stockFilter,
            discountFilter = discountFilter,
            order = order,
        )
        if (ShopReviewedMedia.enabled) results.map(ShopReviewedMedia::product) else results
    }
    suspend fun detail(productId: String): Result<ShopProductDto> = runCatching {
        require(productId.isNotBlank())
        val fetched = if (BuildConfig.QA_BUILD) api?.qaProductDetail(mapOf("p_product_id" to productId))
            else api?.productCatalog(ShopProductCatalogRequest(productId))
        val available = fetched ?: error("Product is unavailable.")
        val product = if (!BuildConfig.QA_BUILD && ShopReviewedMedia.enabled) ShopReviewedMedia.product(available) else available
        if (ShopBenefitFlags.buyerBadgesEnabled) {
            val verified = runCatching { ShopFunctionClient.api?.productVerifiedBenefits(productId)?.verifiedBenefits.orEmpty() }
                .getOrDefault(emptyList())
            product.copy(verifiedBenefits = verified)
        } else product
    }
}

class StoreRepository(private val api: ShopSupabaseApi? = ShopSupabaseClient.api) {
    suspend fun approvedStores(): List<ShopStoreDto> = api?.let { service ->
        if (BuildConfig.QA_BUILD) service.qaStores()
        else if (ShopReviewedMedia.enabled) service.reviewedStores().map(ShopReviewedMedia::store)
        else service.stores()
    }.orEmpty()

    suspend fun searchApprovedStores(query: String): List<ShopStoreDto> {
        val safeTerm = query.trim().take(80)
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        require(safeTerm.length >= 2)
        val service = api ?: error("Marketplace is unavailable.")
        return if (BuildConfig.QA_BUILD) service.qaStores("ilike.*$safeTerm*")
        else approvedStores().filter { it.name.contains(safeTerm, ignoreCase = true) }.take(12)
    }
}

object ShopSessionMemory {
    private var generation = 0L
    private var signingOut = false

    @Volatile
    private var cachedSession: ShopSessionDto? = null

    @Volatile
    private var cachedProfile: ShopProfileDto? = null

    @Synchronized fun session(): ShopSessionDto? = cachedSession

    @Synchronized fun profile(): ShopProfileDto? = cachedProfile

    @Synchronized fun generation(): Long = generation

    @Synchronized fun isSigningOut(): Boolean = signingOut

    @Synchronized fun beginSignOut(): ShopSessionDto? {
        signingOut = true
        generation++
        return cachedSession
    }

    @Synchronized fun finishSignOut() {
        cachedSession = null
        cachedProfile = null
        generation++
        signingOut = false
    }

    @Synchronized fun updateIfCurrent(expectedGeneration: Long, session: ShopSessionDto, profile: ShopProfileDto?): Boolean {
        if (signingOut || generation != expectedGeneration) return false
        cachedSession = session
        cachedProfile = profile
        return true
    }

    @Synchronized fun updateProfileIfCurrent(expectedGeneration: Long, profile: ShopProfileDto?): Boolean {
        if (signingOut || generation != expectedGeneration) return false
        cachedProfile = profile
        return true
    }

    @Synchronized fun clear() {
        cachedSession = null
        cachedProfile = null
        generation++
    }
}

private suspend fun ShopIdentityRepository.authorization(): String = "Bearer ${sessionToken().getOrThrow()}"

private suspend fun ShopIdentityRepository.qaRpc(action: String, params: Map<String, Any?>): Any? {
    val service = ShopQaSecureClient.api ?: error("QA Shop transport is unavailable.")
    val result = authorizedRequest { token ->
        service.call(token, mapOf("action" to action, "params" to params))
    }
    check(result["ok"] == true) { "QA Shop request could not be completed." }
    return result["data"]
}

class ShopIdentityRepository(
    private val cloudflareApi: com.adischat.app.network.CloudflareApi = CloudflareClient.api,
    private val functionApi: ShopFunctionApi? = ShopFunctionClient.api,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val assertionApi: com.adischat.app.network.CloudflareApi = if (BuildConfig.QA_BUILD) CloudflareClient.shopAssertionApi else cloudflareApi,
) {
    private val authRetryCoordinator = ShopAuthRetryCoordinator { error ->
        error is HttpException && error.code() == 401
    }

    fun activeUserId(): String = auth.currentUser?.uid.orEmpty()

    suspend fun sessionToken(): Result<String> = runCatching {
        if (ShopSessionMemory.isSigningOut()) throw ShopSessionExpiredException()
        val current = ShopSessionMemory.session()
        val profile = ShopSessionMemory.profile()
        val activeUid = auth.currentUser?.uid.orEmpty()
        if (ShopStateReducers.canUseCachedIdentity(current, profile, activeUid)) {
            return@runCatching current?.accessToken.orEmpty()
        }
        exchangeAuthorization(activeUid).removePrefix("Bearer ")
    }

    suspend fun <T> authorizedRequest(block: suspend (String) -> T): T =
        authRetryCoordinator.execute(
            authorization = { authorization() },
            refreshAuthorization = ::refreshAuthorization,
            request = { token ->
                val ownerUid = activeUserId()
                val generation = ShopSessionMemory.generation()
                val result = block(token)
                if (ShopSessionMemory.isSigningOut() || ShopSessionMemory.generation() != generation || activeUserId() != ownerUid) {
                    throw ShopSessionExpiredException()
                }
                result
            },
        )

    private suspend fun refreshAuthorization(failedAuthorization: String): String {
        val generation = ShopSessionMemory.generation()
        return try {
            if (ShopSessionMemory.isSigningOut()) throw ShopSessionExpiredException()
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

            val refreshedSession = currentSession?.refreshToken
                ?.takeIf { it.isNotBlank() }
                ?.let { refreshToken ->
                    runCatching {
                        functionApi?.refresh(ShopRefreshRequest(refreshToken))?.session
                    }.getOrNull()
                }
            if (
                refreshedSession != null &&
                refreshedSession.accessToken.isNotBlank() &&
                currentProfile?.adisChatUserId == activeUid
            ) {
                if (activeUserId() != activeUid || !ShopSessionMemory.updateIfCurrent(generation, refreshedSession, currentProfile)) {
                    throw ShopSessionExpiredException()
                }
                return "Bearer ${refreshedSession.accessToken}"
            }

            exchangeAuthorization(activeUid)
        } catch (error: Throwable) {
            if (ShopSessionMemory.generation() == generation) ShopSessionMemory.clear()
            if (error is ShopSessionExpiredException) throw error
            throw ShopSessionExpiredException(error)
        }
    }

    private suspend fun exchangeAuthorization(activeUid: String): String {
        if (activeUid.isBlank()) throw ShopSessionExpiredException()
        if (ShopSessionMemory.isSigningOut()) throw ShopSessionExpiredException()
        ShopSessionMemory.clear()
        val generation = ShopSessionMemory.generation()
        val service = functionApi ?: throw ShopSessionExpiredException()
        val assertion = assertionApi.requestShopSessionAssertion().assertion
        if (assertion.isBlank()) throw ShopSessionExpiredException()
        val exchange = service.exchange(ShopSessionExchangeRequest(assertion))
        val session = exchange.session ?: throw ShopSessionExpiredException()
        val profile = exchange.profile
        if (session.accessToken.isBlank() || profile?.adisChatUserId != activeUid) {
            if (ShopSessionMemory.generation() == generation) ShopSessionMemory.clear()
            throw ShopSessionExpiredException()
        }
        if (activeUserId() != activeUid || !ShopSessionMemory.updateIfCurrent(generation, session, profile)) {
            throw ShopSessionExpiredException()
        }
        return "Bearer ${session.accessToken}"
    }

    suspend fun revokeCapturedSession(session: ShopSessionDto?): Boolean {
        if (session == null) return true
        if (session.refreshToken.isBlank()) return false
        return runCatching {
            functionApi?.revoke(ShopRevokeRequest(session.refreshToken))?.ok == true
        }.getOrDefault(false)
    }

    suspend fun revokeCurrentAccountSessions(): Boolean = runCatching {
        if (activeUserId().isBlank()) return@runCatching false
        val assertion = assertionApi.requestShopSessionAssertion().assertion
        if (assertion.isBlank()) return@runCatching false
        functionApi?.revokeAll(ShopRevokeAllRequest(assertion))?.ok == true
    }.getOrDefault(false)

    suspend fun currentProfile(): Result<ShopProfileDto?> = runCatching {
        var requestGeneration = ShopSessionMemory.generation()
        val ownerUid = activeUserId()
        val service = functionApi ?: error("Shop account sync is temporarily unavailable.")
        val envelope = authorizedRequest { token ->
            requestGeneration = ShopSessionMemory.generation()
            service.currentProfile(token)
        }
        if (activeUserId() != ownerUid || !ShopSessionMemory.updateProfileIfCurrent(requestGeneration, envelope.profile ?: ShopSessionMemory.profile())) {
            throw ShopSessionExpiredException()
        }
        ShopSessionMemory.profile()
    }

    fun clear() {
        ShopSessionMemory.clear()
    }
}

class CartRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
    private val supabaseApi: ShopSupabaseApi? = ShopSupabaseClient.api,
) {
    private fun parseQaCart(data: Map<String, Any?>): ShopCartDto {
        val gson = com.google.gson.Gson()
        val json = gson.toJsonTree(data)
        return gson.fromJson(json, ShopCartDto::class.java)
    }

    suspend fun getCart(): Result<ShopCartDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("cart", mapOf(
                "p_action" to "read"
            )) as? Map<String, Any?> ?: error("Your cart is temporarily unavailable.")
            val cart = parseQaCart(res)
            return@runCatching if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
        }
        val service = api ?: error("Your cart is temporarily unavailable.")
        val cart = identity.authorizedRequest { token -> service.cart(token) }.cart
            ?: error("Your cart is temporarily unavailable.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
    }

    suspend fun add(productId: String, variantId: String? = null, quantity: Int = 1): Result<ShopCartDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("cart", mapOf(
                "p_action" to "add",
                "p_product_id" to productId,
                "p_variant_id" to variantId,
                "p_quantity" to quantity
            )) as? Map<String, Any?> ?: error("Your cart is temporarily unavailable.")
            val cart = parseQaCart(res)
            return@runCatching if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
        }
        val service = api ?: error("Your cart is temporarily unavailable.")
        val cart = identity.authorizedRequest { token ->
            service.addCartItem(token, ShopCartItemRequest(productId = productId, variantId = variantId, quantity = quantity))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
    }

    suspend fun update(itemId: String, quantity: Int): Result<ShopCartDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("cart", mapOf(
                "p_action" to "update",
                "p_item_id" to itemId,
                "p_quantity" to quantity
            )) as? Map<String, Any?> ?: error("Your cart is temporarily unavailable.")
            val cart = parseQaCart(res)
            return@runCatching if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
        }
        val service = api ?: error("Your cart is temporarily unavailable.")
        val cart = identity.authorizedRequest { token ->
            service.updateCartItem(token, ShopCartItemUpdateRequest(itemId = itemId, quantity = quantity))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
    }

    suspend fun remove(itemId: String): Result<ShopCartDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("cart", mapOf(
                "p_action" to "remove",
                "p_item_id" to itemId
            )) as? Map<String, Any?> ?: error("Your cart is temporarily unavailable.")
            val cart = parseQaCart(res)
            return@runCatching if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
        }
        val service = api ?: error("Your cart is temporarily unavailable.")
        val cart = identity.authorizedRequest { token ->
            service.removeCartItem(token, ShopCartItemIdRequest(itemId = itemId))
        }.cart
            ?: error("Your cart is temporarily unavailable.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
    }

    suspend fun clear(): Result<ShopCartDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("cart", mapOf(
                "p_action" to "clear"
            )) as? Map<String, Any?> ?: error("Your cart is temporarily unavailable.")
            val cart = parseQaCart(res)
            return@runCatching if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
        }
        val service = api ?: error("Your cart is temporarily unavailable.")
        val cart = identity.authorizedRequest { token -> service.clearCart(token) }.cart
            ?: error("Your cart is temporarily unavailable.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.cart(cart) else cart
    }

    fun isAvailable(): Boolean = if (BuildConfig.QA_BUILD && supabaseApi != null) true else api != null
}

class FavoriteRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        val favorites = identity.authorizedRequest { token -> service.favorites(token) }.favorites.orEmpty()
        if (ShopReviewedMedia.enabled) favorites.map(ShopReviewedMedia::favorite) else favorites
    }

    suspend fun add(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't save this right now. Try again.")
        identity.authorizedRequest { token ->
            service.addFavorite(token, ShopProductActionRequest(productId))
            service.favorites(token)
        }.favorites.orEmpty().let { favorites ->
            if (ShopReviewedMedia.enabled) favorites.map(ShopReviewedMedia::favorite) else favorites
        }
    }

    suspend fun remove(productId: String): Result<List<ShopFavoriteDto>> = runCatching {
        val service = api ?: error("Couldn't update saved products. Try again.")
        identity.authorizedRequest { token ->
            service.removeFavorite(token, ShopProductActionRequest(productId))
            service.favorites(token)
        }.favorites.orEmpty().let { favorites ->
            if (ShopReviewedMedia.enabled) favorites.map(ShopReviewedMedia::favorite) else favorites
        }
    }
}

class RecentlyViewedRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
) {
    suspend fun list(): Result<List<ShopRecentlyViewedDto>> = runCatching {
        val service = api ?: error("Couldn't load this right now. Try again.")
        val items = identity.authorizedRequest { token -> service.recentlyViewed(token) }.recentlyViewed.orEmpty()
        if (ShopReviewedMedia.enabled) items.map(ShopReviewedMedia::recentlyViewed) else items
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
        if (com.adischat.app.BuildConfig.QA_BUILD) {
            return@runCatching listOf(
                ShopAddressDto(
                    id = "a0000000-0000-4000-8000-000000000002",
                    label = "Home",
                    recipientName = "Aliyu (QA Buyer)",
                    phone = "+251911223344",
                    addressLine1 = "Bole Medhanialem, QA Suite 404",
                    city = "Addis Ababa",
                    isDefault = true
                )
            )
        }
        val service = api ?: error("Couldn't load this right now. Try again.")
        identity.authorizedRequest { token -> service.addresses(token) }.addresses.orEmpty()
    }

    suspend fun save(request: ShopAddressRequest): Result<List<ShopAddressDto>> = runCatching {
        if (com.adischat.app.BuildConfig.QA_BUILD) {
            return@runCatching listOf(
                ShopAddressDto(
                    id = request.id ?: "qa-staging-address-bole-1",
                    label = request.label.ifBlank { "Home" },
                    recipientName = request.recipientName,
                    phone = request.phone,
                    addressLine1 = request.addressLine1,
                    city = request.city,
                    isDefault = true
                )
            )
        }
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
    private val supabaseApi: ShopSupabaseApi? = ShopSupabaseClient.api,
) {
    private fun parseQaOrder(data: Map<String, Any?>): ShopOrderDto {
        val gson = com.google.gson.Gson()
        val json = gson.toJsonTree(data)
        return gson.fromJson(json, ShopOrderDto::class.java)
    }

    suspend fun list(): Result<List<ShopOrderDto>> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            @Suppress("UNCHECKED_CAST")
            val list = identity.qaRpc("buyerOrders", emptyMap()) as? List<Map<String, Any?>>
                ?: error("Couldn't load orders right now.")
            val receivedAtMillis = System.currentTimeMillis()
            return@runCatching list.map { raw ->
                val order = parseQaOrder(raw)
                val reviewed = if (ShopReviewedMedia.enabled) ShopReviewedMedia.order(order) else order
                reviewed.copy(
                    groups = reviewed.groups?.map {
                        it.copy(serverReceivedAtMillis = receivedAtMillis)
                    }
                )
            }
        }
        val service = api ?: error("Couldn't load this right now. Try again.")
        val envelope = identity.authorizedRequest { token -> service.orders(token) }
        val receivedAtMillis = System.currentTimeMillis()
        envelope.orders.orEmpty().map { received ->
            val order = if (ShopReviewedMedia.enabled) ShopReviewedMedia.order(received) else received
            order.copy(
                serverNow = envelope.serverTime,
                groups = order.groups?.map {
                    it.copy(serverNow = envelope.serverTime, serverReceivedAtMillis = receivedAtMillis)
                },
            )
        }
    }

    suspend fun detail(orderId: String): Result<ShopOrderDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            val res = identity.qaRpc("buyerOrderDetail", mapOf(
                "p_order_id" to orderId
            )) as? Map<String, Any?> ?: error("Couldn't load this order.")
            val order = parseQaOrder(res)
            val reviewed = if (ShopReviewedMedia.enabled) ShopReviewedMedia.order(order) else order
            val receivedAtMillis = System.currentTimeMillis()
            return@runCatching reviewed.copy(
                groups = reviewed.groups?.map {
                    it.copy(serverReceivedAtMillis = receivedAtMillis)
                }
            )
        }
        val service = api ?: error("Couldn't load this order. Try again.")
        val envelope = identity.authorizedRequest { token -> service.order(token, orderId) }
        val receivedAtMillis = System.currentTimeMillis()
        envelope.order?.let { received ->
            val order = if (ShopReviewedMedia.enabled) ShopReviewedMedia.order(received) else received
            order.copy(
                serverNow = envelope.serverTime,
                groups = order.groups?.map {
                    it.copy(serverNow = envelope.serverTime, serverReceivedAtMillis = receivedAtMillis)
                },
            )
        } ?: error("Couldn't load this order.")
    }

    suspend fun detailForGroup(orderGroupId: String): Result<ShopOrderDto> = runCatching {
        require(orderGroupId.isNotBlank()) { "Order is unavailable." }
        val orderId = list().getOrThrow()
            .firstOrNull { order -> order.groups.orEmpty().any { it.id == orderGroupId } }
            ?.id
            ?: error("Couldn't load this order.")
        detail(orderId).getOrThrow()
    }

    suspend fun cancel(orderId: String, reason: String): Result<ShopOrderDto> = runCatching {
        if (BuildConfig.QA_BUILD) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA order cancellation is disabled." }
            val result = identity.qaRpc("cancelOrder", mapOf("p_order_id" to orderId, "p_reason" to reason))
                as? Map<String, Any?> ?: error("This order cannot be cancelled right now.")
            return@runCatching parseQaOrder(result)
        }
        val service = api ?: error("This order cannot be cancelled right now.")
        val order = identity.authorizedRequest { token ->
            service.cancelOrder(token, ShopCancelOrderRequest(orderId = orderId, reasonText = reason))
        }.order
            ?: error("This order cannot be cancelled right now.")
        if (ShopReviewedMedia.enabled) ShopReviewedMedia.order(order) else order
    }

    suspend fun revealDeliveryCode(orderId: String): Result<ShopDeliveryCodeDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            error("Delivery code is not available in QA.")
        }
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
    suspend fun list(context: MarketplaceNotificationContext, storeId: String? = null): Result<List<ShopNotificationDto>> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        identity.authorizedRequest { token -> service.notifications(token, context.wireValue, storeId) }.notifications.orEmpty()
    }

    suspend fun unreadCount(context: MarketplaceNotificationContext, storeId: String? = null): Result<Int> = runCatching {
        val service = api ?: error("Couldn't load notifications.")
        identity.authorizedRequest { token -> service.notificationUnreadCount(token, context.wireValue, storeId) }.unreadCount ?: 0
    }

    suspend fun markAllRead(context: MarketplaceNotificationContext, storeId: String? = null): Result<Int> = runCatching {
        val service = api ?: error("Couldn't update notifications.")
        identity.authorizedRequest { token -> service.markAllNotificationsRead(token, ShopNotificationScopeRequest(context.wireValue, storeId)) }.unreadCount ?: 0
    }

    suspend fun markRead(notificationId: String, context: MarketplaceNotificationContext, storeId: String? = null): Result<Int> = runCatching {
        require(notificationId.isNotBlank()) { "Notification is unavailable." }
        val service = api ?: error("Couldn't update notifications.")
        identity.authorizedRequest { token ->
            service.markNotificationRead(token, ShopNotificationIdRequest(notificationId, context.wireValue, storeId))
        }.unreadCount ?: 0
    }
}

class CheckoutRepository(
    private val identity: ShopIdentityRepository = ShopIdentityRepository(),
    private val api: ShopFunctionApi? = ShopFunctionClient.api,
    private val supabaseApi: ShopSupabaseApi? = ShopSupabaseClient.api,
) {
    private fun parseQaCheckout(data: Map<String, Any?>): ShopCheckoutDto {
        val checkoutId = data["checkoutId"]?.toString().orEmpty()
        val subtotal = (data["subtotalMinor"] as? Number)?.toInt() ?: 0
        val couponDiscount = (data["couponDiscountMinor"] as? Number)?.toInt() ?: 0
        val voucherApplied = (data["voucherAppliedMinor"] as? Number)?.toInt() ?: 0
        val remaining = (data["remainingPayableMinor"] as? Number)?.toInt() ?: 0
        val shipping = (data["shippingMinor"] as? Number)?.toInt() ?: 0
        val tax = (data["taxMinor"] as? Number)?.toInt() ?: 0
        val grandTotal = (data["grandTotalMinor"] as? Number)?.toInt() ?: 0
        val isCovered = (data["isFullyCoveredByVoucher"] as? Boolean) ?: (remaining == 0 && voucherApplied > 0)
        val couponCode = data["couponCode"]?.toString()
        val voucherCode = data["voucherCode"]?.toString()

        val methods = if (isCovered) {
            listOf(ShopPaymentMethodDto(code = "voucher_prepaid", label = "Store Credit Voucher", provider = "voucher"))
        } else {
            listOf(
                ShopPaymentMethodDto(code = "cash_on_delivery", label = "Cash on Delivery", provider = "manual"),
                ShopPaymentMethodDto(code = "cbe_birr_manual", label = "CBE Birr (Manual Transfer)", provider = "manual"),
            )
        }

        return ShopCheckoutDto(
            id = checkoutId,
            state = "reserved",
            currency = "ETB",
            subtotalMinor = subtotal,
            discountMinor = couponDiscount + voucherApplied,
            shippingMinor = shipping,
            taxMinor = tax,
            grandTotalMinor = if (remaining > 0) remaining else grandTotal,
            paymentMethods = methods,
            couponCode = couponCode,
            couponDiscountMinor = couponDiscount,
            voucherCode = voucherCode,
            voucherAppliedMinor = voucherApplied,
            remainingPayableMinor = remaining,
            isFullyCoveredByVoucher = isCovered,
        )
    }

    suspend fun create(idempotencyKey: String): Result<ShopCheckoutDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA checkout is disabled." }
            val res = identity.qaRpc("createCheckout", mapOf(
                "p_source" to "CART",
                "p_idempotency_key" to idempotencyKey
            )) as? Map<String, Any?> ?: error("Checkout could not start.")
            return@runCatching parseQaCheckout(res)
        }
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
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA checkout is disabled." }
            val res = identity.qaRpc("createCheckout", mapOf(
                "p_source" to "BUY_NOW",
                "p_idempotency_key" to idempotencyKey,
                "p_product_id" to productId,
                "p_variant_id" to variantId,
                "p_quantity" to quantity.coerceIn(1, 99)
            )) as? Map<String, Any?> ?: error("Checkout could not start.")
            return@runCatching parseQaCheckout(res)
        }
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

    suspend fun applyCoupon(checkoutSessionId: String, couponCode: String): Result<ShopCheckoutDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA discounts are disabled." }
            val res = identity.qaRpc("applyCoupon", mapOf(
                "p_checkout_session_id" to checkoutSessionId,
                "p_coupon_code" to couponCode.trim().uppercase()
            )) as? Map<String, Any?> ?: error("Failed to apply coupon.")
            return@runCatching parseQaCheckout(res)
        }
        val service = api ?: error("Discounts are temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.applyCoupon(token, ShopApplyCouponRequest(checkoutSessionId, couponCode.trim().uppercase()))
        }.checkout ?: error("Failed to apply coupon.")
    }

    suspend fun applyVoucher(checkoutSessionId: String, voucherCode: String): Result<ShopCheckoutDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA discounts are disabled." }
            val res = identity.qaRpc("applyVoucher", mapOf(
                "p_checkout_session_id" to checkoutSessionId,
                "p_voucher_code" to voucherCode.trim().uppercase()
            )) as? Map<String, Any?> ?: error("Failed to apply voucher.")
            return@runCatching parseQaCheckout(res)
        }
        val service = api ?: error("Vouchers are temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.applyVoucher(token, ShopApplyVoucherRequest(checkoutSessionId, voucherCode.trim().uppercase()))
        }.checkout ?: error("Failed to apply voucher.")
    }

    suspend fun removeDiscount(checkoutSessionId: String, discountType: String): Result<ShopCheckoutDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED) { "QA discounts are disabled." }
            val res = identity.qaRpc("removeDiscount", mapOf(
                "p_checkout_session_id" to checkoutSessionId,
                "p_discount_type" to discountType.trim().lowercase()
            )) as? Map<String, Any?> ?: error("Failed to remove discount.")
            return@runCatching parseQaCheckout(res)
        }
        val service = api ?: error("Discounts are temporarily unavailable.")
        identity.authorizedRequest { token ->
            service.removeDiscount(token, ShopRemoveDiscountRequest(checkoutSessionId, discountType.trim().lowercase()))
        }.checkout ?: error("Failed to remove discount.")
    }

    suspend fun confirm(checkoutSessionId: String, addressId: String, paymentMethodCode: String, idempotencyKey: String): Result<ShopOrderDto> = runCatching {
        if (BuildConfig.QA_BUILD && supabaseApi != null) {
            check(BuildConfig.QA_ORDER_MUTATIONS_ENABLED && BuildConfig.QA_PAYMENTS_ENABLED) {
                "QA order confirmation is disabled."
            }
            val res = identity.qaRpc("confirmCheckout", mapOf(
                "p_checkout_session_id" to checkoutSessionId,
                "p_address_id" to addressId,
                "p_payment_method_code" to paymentMethodCode,
                "p_idempotency_key" to idempotencyKey
            )) as? Map<String, Any?> ?: error("Order confirmation is unavailable.")
            val orderId = res["orderId"]?.toString()?.takeIf { it.isNotBlank() }
                ?: error("Order confirmation is unavailable.")
            val orderNumber = res["orderNumber"]?.toString()?.takeIf { it.isNotBlank() }
                ?: error("Order confirmation is unavailable.")
            val paymentStatus = res["paymentStatus"]?.toString()?.takeIf { it.isNotBlank() }
                ?: error("Payment status is unavailable.")
            val status = res["status"]?.toString()?.takeIf { it.isNotBlank() }
                ?: error("Order status is unavailable.")
            return@runCatching ShopOrderDto(
                id = orderId,
                orderNumber = orderNumber,
                status = status,
                paymentStatus = paymentStatus,
                totalMinor = (res["totalMinor"] as? Number)?.toInt()
                    ?: error("Order total is unavailable.")
            )
        }
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

    suspend fun orderGroups(storeId: String): Result<List<ShopOrderGroupDto>> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Couldn't load seller orders.")
        val envelope = withSellerAuthRetry { token -> service.sellerOrderGroups(token, storeId) }
        check(envelope.sellerOrderStoreId == storeId) { "Seller order store scope is unavailable." }
        val receivedAtMillis = System.currentTimeMillis()
        envelope.sellerOrderGroups.orEmpty().map {
            check(it.storeId == storeId) { "Seller order store scope is unavailable." }
            it.copy(serverNow = envelope.serverTime, serverReceivedAtMillis = receivedAtMillis)
        }
    }

    suspend fun requests(storeId: String): Result<ShopSellerRequestsDto> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Couldn't load returns and requests.")
        val envelope = withSellerAuthRetry { token -> service.sellerRequests(token, storeId) }
        check(envelope.sellerOrderStoreId == storeId) { "Seller request store scope is unavailable." }
        envelope.sellerRequests ?: ShopSellerRequestsDto()
    }

    suspend fun respondToRequest(storeId: String, kind: String, requestId: String, message: String, expectedVersion: Int): Result<ShopSellerRequestsDto> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Request response is temporarily unavailable.")
        val body = ShopSellerRequestResponseRequest(
            requestId = requestId,
            message = message,
            idempotencyKey = sellerRequestIdempotencyKey(kind, requestId, expectedVersion),
            expectedVersion = expectedVersion,
            storeId = storeId,
        )
        val envelope = withSellerAuthRetry { token ->
            if (kind == "exchange") service.sellerRespondExchangeRequest(token, body) else service.sellerRespondRefundRequest(token, body)
        }
        check(envelope.sellerOrderStoreId == storeId) { "Seller request store scope is unavailable." }
        envelope.sellerRequests ?: ShopSellerRequestsDto()
    }

    suspend fun products(storeId: String, status: String = "all"): Result<List<ShopSellerProductDto>> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Couldn't load seller products.")
        val envelope = withSellerAuthRetry { token -> service.sellerProducts(token, storeId, status) }
        val products = envelope.sellerProducts.orEmpty()
        check(ShopStateReducers.sellerProductResponseMatchesStore(storeId, envelope.sellerProductStoreId, products)) {
            "Seller product store scope is unavailable."
        }
        products
    }

    suspend fun createProduct(storeId: String, requestId: String): Result<ShopSellerProductDto> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Product creation is temporarily unavailable.")
        val product = withSellerAuthRetry { token -> service.sellerCreateProduct(token, ShopSellerProductCreateRequest(storeId, requestId)) }.sellerProduct
            ?: error("Product creation is temporarily unavailable.")
        check(product.storeId == storeId) { "Seller product store scope mismatch." }
        product
    }

    suspend fun saveProduct(storeId: String, productId: String, payload: ShopSellerProductDraftPayload, requestId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Product save is temporarily unavailable.")
        try {
            val product = withSellerAuthRetry { token -> service.sellerUpdateProduct(token, ShopSellerProductUpdateRequest(storeId, productId, payload, requestId)) }.sellerProduct
                ?: error("Product save is temporarily unavailable.")
            check(product.storeId == storeId) { "Seller product store scope mismatch." }
            product
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Product save is temporarily unavailable."))
        }
    }

    suspend fun submitProduct(storeId: String, productId: String, requestId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Product submission is temporarily unavailable.")
        try {
            val product = withSellerAuthRetry { token -> service.sellerSubmitProduct(token, ShopSellerProductSubmitRequest(storeId, productId, requestId)) }.sellerProduct
                ?: error("Product submission is temporarily unavailable.")
            check(product.storeId == storeId) { "Seller product store scope mismatch." }
            product
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Product is missing required details."))
        }
    }

    suspend fun archiveProduct(storeId: String, productId: String, requestId: String): Result<Unit> = runCatching {
        val service = api ?: error("Product removal is temporarily unavailable.")
        try {
            withSellerAuthRetry { token ->
                service.sellerArchiveProduct(
                    token,
                    ShopSellerProductArchiveRequest(storeId, productId, ShopSellerProductArchivePayload(), requestId),
                )
            }
            Unit
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Product removal is temporarily unavailable."))
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

    suspend fun deleteProductMedia(storeId: String, productId: String, imageId: String): Result<ShopSellerProductDto> = runCatching {
        val service = api ?: error("Image removal is temporarily unavailable.")
        try {
            withSellerAuthRetry { token -> service.sellerDeleteProductMedia(token, ShopSellerProductMediaDeleteRequest(storeId, productId, imageId)) }.sellerProduct
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

    suspend fun transition(storeId: String, orderGroupId: String, action: String, reason: String = "", reasonCode: String? = null, carrier: String? = null, trackingNumber: String? = null, expectedVersion: Int? = null): Result<List<ShopOrderGroupDto>> = runCatching {
        require(storeId.isNotBlank())
        val service = api ?: error("Seller action is temporarily unavailable.")
        val body = ShopSellerOrderActionRequest(
            orderGroupId = orderGroupId,
            storeId = storeId,
            reason = reason,
            reasonCode = reasonCode,
            requestId = if (action in setOf("accept", "reject")) "android-$action-${java.util.UUID.randomUUID()}" else null,
            expectedVersion = expectedVersion,
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
                        ShopSellerDeliveryCodeRequest(orderGroupId = orderGroupId, deliveryCode = trackingNumber.orEmpty(), storeId = storeId),
                    )
                    else -> error("Seller action is not supported.")
                }
            }
        } catch (error: Throwable) {
            throw IllegalStateException(error.shopSellerActionMessage("Seller action is not allowed for this order state."))
        }
        val envelope = withSellerAuthRetry { token -> service.sellerOrderGroups(token, storeId) }
        check(envelope.sellerOrderStoreId == storeId) { "Seller order store scope is unavailable." }
        val receivedAtMillis = System.currentTimeMillis()
        envelope.sellerOrderGroups.orEmpty().map {
            check(it.storeId == storeId) { "Seller order store scope is unavailable." }
            it.copy(serverNow = envelope.serverTime, serverReceivedAtMillis = receivedAtMillis)
        }
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
        variantId: String? = null,
        orderGroupId: String? = null,
    ): Result<ShopConversationDto> = runCatching {
        val service = api ?: error("Conversation could not be started.")
        authorized { token ->
            service.openConversation(token, ShopOpenConversationRequest(storeId, productId, variantId, orderGroupId))
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
    private fun Throwable.qaModerationMessage(fallback: String): String {
        if (this is HttpException) {
            val code = runCatching {
                JsonParser.parseString(response()?.errorBody()?.string().orEmpty()).asJsonObject
                    .get("error")?.asString
            }.getOrNull()
            return if (code == "not-found") "Content is no longer available. Refresh the queue."
                else ShopQaModerationContract.errorMessage(this.code(), code)
        }
        return fallback
    }

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

    suspend fun products(status: String = "pending", search: String = "", limit: Int = 30, offset: Int = 0): Result<List<ShopAdminProductDto>> = runCatching {
        val service = api ?: error("Couldn't load products for review.")
        try {
            withAdminAuth { token -> service.adminProducts(token, status, search, limit, offset) }.products.orEmpty()
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load products for review."))
        }
    }

    suspend fun allProducts(query: ShopAdminProductQuery): Result<ShopAdminProductPage> = runCatching {
        val service = api ?: error("Couldn't load products.")
        val safe = query.normalized()
        val advanced = ShopAdminAllProductsFlags.advancedQueryEnabled
        try {
            val response = withAdminAuth { token ->
                service.adminProducts(
                    authorization = token,
                    status = safe.status,
                    search = safe.search,
                    limit = safe.limit,
                    offset = safe.offset,
                    view = if (advanced) "all-v2" else null,
                    category = if (advanced) safe.category.takeIf { it.isNotBlank() } else null,
                    storeId = if (advanced) safe.storeId.takeIf { it.isNotBlank() } else null,
                    stock = if (advanced) safe.stock else null,
                    sort = if (advanced) safe.sort else null,
                )
            }
            ShopAdminProductPage(response.products.orEmpty(), response.total, safe.limit, safe.offset)
        } catch (error: Throwable) {
            throw IllegalStateException(error.adminMessage("Couldn't load products."))
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

    suspend fun publicContentQueue(offset: Int, kind: String?): Result<ShopPublicContentQueueDto> = runCatching {
        check(ShopPublicContentReviewFlags.enabled) { "Public content review is unavailable." }
        val service = api ?: error("Public content review is unavailable.")
        try {
            val response = withAdminAuth { token -> service.qaModerationQueue(token, kind,
                ShopPublicContentReviewFlags.pageSize, offset) }
            response.reviewQueue?.takeIf { response.ok } ?: error("Public content queue is unavailable.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.qaModerationMessage("Couldn't load content for review."))
        }
    }

    suspend fun publicContentPreview(item: ShopPublicContentItemDto): Result<ShopPublicContentPreviewDto> = runCatching {
        check(ShopPublicContentReviewFlags.enabled) { "Public content review is unavailable." }
        val service = api ?: error("Public content review is unavailable.")
        try {
            val response = withAdminAuth { token -> service.qaModerationPreview(token, item.entityKind, item.entityId) }
            response.reviewPreview?.takeIf { response.ok } ?: error("Content is no longer available for review.")
        } catch (error: Throwable) {
            throw IllegalStateException(error.qaModerationMessage("Couldn't load the current content."))
        }
    }

    suspend fun reviewPublicContent(request: ShopQaModerationRequest, idempotencyKey: String): Result<ShopQaReviewResult> = runCatching {
        check(ShopPublicContentReviewFlags.enabled) { "Public content review is unavailable." }
        val service = api ?: error("Public content review is unavailable.")
        try {
            ShopQaModerationContract.requireSuccess(withAdminAuth { token ->
                service.qaModerationDecision(token, idempotencyKey, request) })
        } catch (error: Throwable) {
            throw IllegalStateException(error.qaModerationMessage("Content review could not be saved."))
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
