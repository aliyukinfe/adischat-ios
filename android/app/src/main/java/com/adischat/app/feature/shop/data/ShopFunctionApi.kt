package com.adischat.app.feature.shop.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ShopFunctionApi {
    @POST("functions/v1/adishop-exchange-adischat-session")
    suspend fun exchange(@Body body: ShopSessionExchangeRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-refresh-shop-session")
    suspend fun refresh(@Body body: ShopRefreshRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-get-current-profile")
    suspend fun currentProfile(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-get-cart")
    suspend fun cart(@Header("Authorization") authorization: String): ShopEnvelope<ShopCartDto>

    @POST("functions/v1/adishop-add-cart-item")
    suspend fun addCartItem(@Header("Authorization") authorization: String, @Body body: ShopCartItemRequest): ShopEnvelope<ShopCartDto>

    @POST("functions/v1/adishop-update-cart-item")
    suspend fun updateCartItem(@Header("Authorization") authorization: String, @Body body: ShopCartItemUpdateRequest): ShopEnvelope<ShopCartDto>

    @POST("functions/v1/adishop-remove-cart-item")
    suspend fun removeCartItem(@Header("Authorization") authorization: String, @Body body: ShopCartItemIdRequest): ShopEnvelope<ShopCartDto>

    @POST("functions/v1/adishop-clear-cart")
    suspend fun clearCart(@Header("Authorization") authorization: String): ShopEnvelope<ShopCartDto>

    @POST("functions/v1/adishop-add-favorite")
    suspend fun addFavorite(@Header("Authorization") authorization: String, @Body body: ShopProductActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-remove-favorite")
    suspend fun removeFavorite(@Header("Authorization") authorization: String, @Body body: ShopProductActionRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-favorites")
    suspend fun favorites(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-record-product-view")
    suspend fun recordProductView(@Header("Authorization") authorization: String, @Body body: ShopProductActionRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-recently-viewed")
    suspend fun recentlyViewed(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-addresses")
    suspend fun addresses(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-create-address")
    suspend fun createAddress(@Header("Authorization") authorization: String, @Body body: ShopAddressRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-update-address")
    suspend fun updateAddress(@Header("Authorization") authorization: String, @Body body: ShopAddressRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-delete-address")
    suspend fun deleteAddress(@Header("Authorization") authorization: String, @Body body: ShopAddressIdRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-set-default-address")
    suspend fun setDefaultAddress(@Header("Authorization") authorization: String, @Body body: ShopAddressIdRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-orders")
    suspend fun orders(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-get-order")
    suspend fun order(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-request-order-cancellation")
    suspend fun cancelOrder(@Header("Authorization") authorization: String, @Body body: ShopCancelOrderRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-reveal-delivery-code")
    suspend fun revealDeliveryCode(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-notifications")
    suspend fun notifications(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-notification-unread-count")
    suspend fun notificationUnreadCount(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-mark-notification-read")
    suspend fun markNotificationRead(@Header("Authorization") authorization: String, @Body body: ShopNotificationIdRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-mark-all-notifications-read")
    suspend fun markAllNotificationsRead(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-create-checkout")
    suspend fun createCheckout(@Header("Authorization") authorization: String, @Body body: ShopCreateCheckoutRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-confirm-checkout")
    suspend fun confirmCheckout(@Header("Authorization") authorization: String, @Body body: ShopConfirmCheckoutRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-get-seller-application-status")
    suspend fun sellerStatus(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-submit-seller-application")
    suspend fun submitSellerApplication(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-seller-order-groups")
    suspend fun sellerOrderGroups(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-accept-order-group")
    suspend fun sellerAcceptOrderGroup(@Header("Authorization") authorization: String, @Body body: ShopSellerOrderActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-reject-order-group")
    suspend fun sellerRejectOrderGroup(@Header("Authorization") authorization: String, @Body body: ShopSellerOrderActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-mark-processing")
    suspend fun sellerMarkProcessing(@Header("Authorization") authorization: String, @Body body: ShopSellerOrderActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-mark-packed")
    suspend fun sellerMarkPacked(@Header("Authorization") authorization: String, @Body body: ShopSellerOrderActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-hand-to-carrier")
    suspend fun sellerHandToCarrier(@Header("Authorization") authorization: String, @Body body: ShopSellerOrderActionRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-verify-delivery-code")
    suspend fun sellerVerifyDeliveryCode(@Header("Authorization") authorization: String, @Body body: ShopSellerDeliveryCodeRequest): ShopEnvelope<Unit>
}
