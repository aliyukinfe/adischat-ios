package com.adischat.app.feature.shop.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody

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

    @GET("functions/v1/adishop-buyer-order-issue-options")
    suspend fun buyerOrderIssueOptions(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-order-issues")
    suspend fun buyerOrderIssues(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-create-order-issue")
    suspend fun buyerCreateOrderIssue(@Header("Authorization") authorization: String, @Body body: ShopBuyerCreateIssueRequest): ShopEnvelope<Unit>

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

    @GET("functions/v1/adishop-seller-products")
    suspend fun sellerProducts(
        @Header("Authorization") authorization: String,
        @Query("status") status: String = "all",
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-seller-product-detail")
    suspend fun sellerProductDetail(@Header("Authorization") authorization: String, @Query("productId") productId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-create-product")
    suspend fun sellerCreateProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductCreateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-update-product")
    suspend fun sellerUpdateProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductUpdateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-submit-product")
    suspend fun sellerSubmitProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductSubmitRequest): ShopEnvelope<Unit>

    @Multipart
    @POST("functions/v1/adishop-seller-upload-product-media")
    suspend fun sellerUploadProductMedia(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part,
        @Part("productId") productId: RequestBody,
        @Part("requestId") requestId: RequestBody,
        @Part("altText") altText: RequestBody,
        @Part("width") width: RequestBody,
        @Part("height") height: RequestBody,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-delete-product-media")
    suspend fun sellerDeleteProductMedia(@Header("Authorization") authorization: String, @Body body: ShopSellerProductMediaDeleteRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-reorder-product-media")
    suspend fun sellerReorderProductMedia(@Header("Authorization") authorization: String, @Body body: ShopSellerProductMediaReorderRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-session")
    suspend fun adminSession(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-dashboard")
    suspend fun adminDashboard(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-seller-applications")
    suspend fun adminSellerApplications(
        @Header("Authorization") authorization: String,
        @Query("status") status: String = "submitted",
        @Query("search") search: String = "",
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-review-seller")
    suspend fun adminReviewSeller(@Header("Authorization") authorization: String, @Body body: ShopAdminReviewSellerRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-products")
    suspend fun adminProducts(
        @Header("Authorization") authorization: String,
        @Query("status") status: String = "pending",
        @Query("search") search: String = "",
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminProductDetail(
        @Header("Authorization") authorization: String,
        @Query("productId") productId: String,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-review-product")
    suspend fun adminReviewProduct(@Header("Authorization") authorization: String, @Body body: ShopAdminReviewProductRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-orders")
    suspend fun adminOrders(@Header("Authorization") authorization: String, @Query("search") search: String = ""): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-order-detail")
    suspend fun adminOrderDetail(
        @Header("Authorization") authorization: String,
        @Query("orderId") orderId: String,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-add-order-note")
    suspend fun adminAddOrderNote(@Header("Authorization") authorization: String, @Body body: ShopAdminOrderNoteRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-update-order-issue")
    suspend fun adminUpdateOrderIssue(@Header("Authorization") authorization: String, @Body body: ShopAdminIssueUpdateRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-audit-log")
    suspend fun adminAuditLog(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-users")
    suspend fun adminUsers(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-assign-role")
    suspend fun adminAssignRole(@Header("Authorization") authorization: String, @Body body: ShopAdminRoleRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-revoke-role")
    suspend fun adminRevokeRole(@Header("Authorization") authorization: String, @Body body: ShopAdminRoleRequest): ShopEnvelope<Unit>
}
