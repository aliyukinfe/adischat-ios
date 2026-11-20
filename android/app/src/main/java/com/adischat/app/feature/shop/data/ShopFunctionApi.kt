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
import okhttp3.ResponseBody
import retrofit2.http.Streaming

interface ShopFunctionApi {
    @GET("functions/v1/adishop-seller-business")
    suspend fun sellerBenefitBrands(
        @Header("Authorization") authorization: String,
        @Query("storeId") storeId: String,
        @Query("action") action: String = "benefit-brands",
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-seller-business")
    suspend fun sellerBenefitClaims(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "benefit-list",
        @Query("storeId") storeId: String,
        @Query("productId") productId: String? = null,
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-seller-business?action=benefit-create")
    suspend fun sellerCreateBenefit(
        @Header("Authorization") authorization: String,
        @Body body: ShopBenefitCreateRequest,
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-seller-business?action=benefit-submit")
    suspend fun sellerSubmitBenefit(
        @Header("Authorization") authorization: String,
        @Body body: ShopBenefitSubmitRequest,
    ): ShopBenefitResponse

    @Multipart
    @POST("functions/v1/adishop-seller-business?action=benefit-evidence")
    suspend fun sellerUploadBenefitEvidence(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("storeId") storeId: RequestBody,
        @Part("caseId") caseId: RequestBody,
        @Part("evidenceType") evidenceType: RequestBody,
        @Part("documentExpiresAt") documentExpiresAt: RequestBody,
        @Part("requestKey") requestKey: RequestBody,
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-admin-products")
    suspend fun adminBenefitQueue(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "benefit-queue",
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-admin-products")
    suspend fun adminBenefitBrands(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "benefit-brands",
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-admin-products?action=benefit-brand-manage")
    suspend fun adminManageBenefitBrand(
        @Header("Authorization") authorization: String,
        @Body body: ShopBenefitBrandRequest,
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-admin-products?action=benefit-brand-manage")
    suspend fun adminManageBenefitMerge(
        @Header("Authorization") authorization: String,
        @Body body: ShopBenefitMergeRequest,
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminProductBenefits(
        @Header("Authorization") authorization: String,
        @Query("productId") productId: String,
        @Query("action") action: String = "benefit-list",
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-admin-store-detail")
    suspend fun adminStoreBenefits(
        @Header("Authorization") authorization: String,
        @Query("storeId") storeId: String,
        @Query("action") action: String = "benefit-list",
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminBenefitEvidence(
        @Header("Authorization") authorization: String,
        @Query("caseId") caseId: String,
        @Query("action") action: String = "benefit-evidence",
    ): ShopBenefitResponse

    @Streaming
    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminBenefitPreview(
        @Header("Authorization") authorization: String,
        @Query("evidenceId") evidenceId: String,
        @Query("action") action: String = "benefit-preview",
    ): ResponseBody

    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminBenefitHistory(
        @Header("Authorization") authorization: String,
        @Query("caseId") caseId: String,
        @Query("action") action: String = "benefit-history",
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-admin-review-product?action=benefit-decision")
    suspend fun adminBenefitDecision(
        @Header("Authorization") authorization: String,
        @Body body: ShopBenefitDecisionRequest,
    ): ShopBenefitResponse

    @GET("functions/v1/adishop-product-reviews")
    suspend fun productVerifiedBenefits(
        @Query("productId") productId: String,
        @Query("action") action: String = "verified-benefits",
    ): ShopBenefitResponse

    @POST("functions/v1/adishop-product-reviews?action=verified-benefits-batch")
    suspend fun catalogVerifiedBenefits(@Body body: ShopBenefitCatalogRequest): ShopBenefitResponse

    @POST("functions/v1/adishop-exchange-adischat-session")
    suspend fun exchange(@Body body: ShopSessionExchangeRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-exchange-adischat-session")
    suspend fun revokeAll(@Body body: ShopRevokeAllRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-refresh-shop-session")
    suspend fun refresh(@Body body: ShopRefreshRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-refresh-shop-session")
    suspend fun revoke(@Body body: ShopRevokeRequest): ShopEnvelope<Unit>

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

    @GET("functions/v1/adishop-buyer-post-delivery-actions")
    suspend fun postDeliveryActions(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-review-options")
    suspend fun buyerReviewOptions(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-create-product-review")
    suspend fun createProductReview(@Header("Authorization") authorization: String, @Body body: ShopReviewCreateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-create-store-review")
    suspend fun createStoreReview(@Header("Authorization") authorization: String, @Body body: ShopReviewCreateRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-order-reviews")
    suspend fun buyerOrderReviews(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-create-refund-request")
    suspend fun createRefundRequest(@Header("Authorization") authorization: String, @Body body: ShopPostDeliveryRequestBody): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-refund-requests")
    suspend fun refundRequests(@Header("Authorization") authorization: String, @Query("orderId") orderId: String? = null): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-create-exchange-request")
    suspend fun createExchangeRequest(@Header("Authorization") authorization: String, @Body body: ShopPostDeliveryRequestBody): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-exchange-requests")
    suspend fun exchangeRequests(@Header("Authorization") authorization: String, @Query("orderId") orderId: String? = null): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-buyer-buy-again")
    suspend fun buyAgain(@Header("Authorization") authorization: String, @Body body: ShopBuyAgainRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-buyer-order-receipt")
    suspend fun orderReceipt(@Header("Authorization") authorization: String, @Query("orderId") orderId: String): ShopEnvelope<Unit>

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
    suspend fun notifications(@Header("Authorization") authorization: String, @Query("context") context: String, @Query("storeId") storeId: String?): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-notification-unread-count")
    suspend fun notificationUnreadCount(@Header("Authorization") authorization: String, @Query("context") context: String, @Query("storeId") storeId: String?): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-mark-notification-read")
    suspend fun markNotificationRead(@Header("Authorization") authorization: String, @Body body: ShopNotificationIdRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-mark-all-notifications-read")
    suspend fun markAllNotificationsRead(@Header("Authorization") authorization: String, @Body body: ShopNotificationScopeRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-create-checkout")
    suspend fun createCheckout(@Header("Authorization") authorization: String, @Body body: ShopCreateCheckoutRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-apply-coupon")
    suspend fun applyCoupon(@Header("Authorization") authorization: String, @Body body: ShopApplyCouponRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-apply-voucher")
    suspend fun applyVoucher(@Header("Authorization") authorization: String, @Body body: ShopApplyVoucherRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-remove-discount")
    suspend fun removeDiscount(@Header("Authorization") authorization: String, @Body body: ShopRemoveDiscountRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-confirm-checkout")
    suspend fun confirmCheckout(@Header("Authorization") authorization: String, @Body body: ShopConfirmCheckoutRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-get-seller-application-status")
    suspend fun sellerStatus(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-submit-seller-application")
    suspend fun submitSellerApplication(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-save-application-draft")
    suspend fun saveSellerApplicationDraft(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-submit-application")
    suspend fun submitCompleteSellerApplication(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-respond-application-info-request")
    suspend fun respondSellerApplicationInfoRequest(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationInfoResponseRequest): ShopEnvelope<Unit>

    @Multipart
    @POST("functions/v1/adishop-seller-upload-application-document")
    suspend fun uploadSellerApplicationDocument(
        @Header("Authorization") authorization: String,
        @Part document: MultipartBody.Part,
        @Part("documentType") documentType: RequestBody,
        @Part("requestId") requestId: RequestBody,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-remove-application-document")
    suspend fun removeSellerApplicationDocument(@Header("Authorization") authorization: String, @Body body: ShopSellerApplicationDocumentRemoveRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-list-seller-order-groups")
    suspend fun sellerOrderGroups(@Header("Authorization") authorization: String, @Query("storeId") storeId: String): ShopEnvelope<Unit>

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

    @GET("functions/v1/adishop-seller-requests")
    suspend fun sellerRequests(@Header("Authorization") authorization: String, @Query("storeId") storeId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-respond-refund-request")
    suspend fun sellerRespondRefundRequest(@Header("Authorization") authorization: String, @Body body: ShopSellerRequestResponseRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-respond-exchange-request")
    suspend fun sellerRespondExchangeRequest(@Header("Authorization") authorization: String, @Body body: ShopSellerRequestResponseRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-seller-products")
    suspend fun sellerProducts(
        @Header("Authorization") authorization: String,
        @Query("storeId") storeId: String,
        @Query("status") status: String = "all",
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-seller-product-detail")
    suspend fun sellerProductDetail(@Header("Authorization") authorization: String, @Query("storeId") storeId: String, @Query("productId") productId: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-create-product")
    suspend fun sellerCreateProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductCreateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-update-product")
    suspend fun sellerUpdateProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductUpdateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-update-product")
    suspend fun sellerArchiveProduct(@Header("Authorization") authorization: String, @Body body: ShopSellerProductArchiveRequest): ShopEnvelope<Unit>

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

    @GET("functions/v1/adishop-seller-business")
    suspend fun sellerBusiness(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "dashboard",
        @Query("storeId") storeId: String? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-business")
    suspend fun sellerBusinessAction(
        @Header("Authorization") authorization: String,
        @Query("action") action: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-business")
    suspend fun sellerInventoryUpdate(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "inventory-update",
        @Body body: ShopSellerInventoryUpdateRequest,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-business")
    suspend fun sellerReviewReply(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "review-reply",
        @Body body: ShopSellerReviewReplyRequest,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-seller-business")
    suspend fun sellerCustomerNote(
        @Header("Authorization") authorization: String,
        @Query("action") action: String = "customer-note",
        @Body body: ShopSellerCustomerNoteRequest,
    ): ShopEnvelope<Unit>

    @Multipart
    @POST("functions/v1/adishop-seller-business?action=store-asset")
    suspend fun sellerStoreAsset(
        @Header("Authorization") authorization: String,
        @Part asset: MultipartBody.Part,
        @Part("storeId") storeId: RequestBody,
        @Part("kind") kind: RequestBody,
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-messages?action=seller-list")
    suspend fun sellerConversations(
        @Header("Authorization") authorization: String,
        @Query("search") search: String = "",
        @Query("archived") archived: Boolean = false,
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-messages?action=buyer-list")
    suspend fun buyerConversations(
        @Header("Authorization") authorization: String,
        @Query("search") search: String = "",
        @Query("archived") archived: Boolean = false,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-messages?action=open")
    suspend fun openConversation(@Header("Authorization") authorization: String, @Body body: ShopOpenConversationRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-messages?action=messages")
    suspend fun conversationMessages(
        @Header("Authorization") authorization: String,
        @Query("conversationId") conversationId: String,
        @Query("before") before: String? = null,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-messages?action=send")
    suspend fun sendMessage(@Header("Authorization") authorization: String, @Body body: ShopSendMessageRequest): ShopEnvelope<Unit>

    @Multipart
    @POST("functions/v1/adishop-messages?action=upload")
    suspend fun uploadMessageAttachment(
        @Header("Authorization") authorization: String,
        @Part attachment: MultipartBody.Part,
        @Part("conversationId") conversationId: RequestBody,
        @Part("clientMessageId") clientMessageId: RequestBody,
        @Part("body") body: RequestBody,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-messages?action=update")
    suspend fun updateConversation(@Header("Authorization") authorization: String, @Body body: ShopConversationActionRequest): ShopEnvelope<Unit>

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
        @Query("limit") limit: Int = ShopPublicContentReviewFlags.pageSize,
        @Query("offset") offset: Int = 0,
        @Query("view") view: String? = null,
        @Query("category") category: String? = null,
        @Query("storeId") storeId: String? = null,
        @Query("stock") stock: String? = null,
        @Query("sort") sort: String? = null,
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-product-detail")
    suspend fun adminProductDetail(
        @Header("Authorization") authorization: String,
        @Query("productId") productId: String,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-review-product")
    suspend fun adminReviewProduct(@Header("Authorization") authorization: String, @Body body: ShopAdminReviewProductRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-review-product?action=public-content-queue")
    suspend fun adminPublicContentQueue(
        @Header("Authorization") authorization: String,
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-review-product?action=public-content-preview")
    suspend fun adminPublicContentPreview(
        @Header("Authorization") authorization: String,
        @Query("kind") kind: String,
        @Query("id") id: String,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-review-product?action=public-content-review")
    suspend fun adminPublicContentDecision(
        @Header("Authorization") authorization: String,
        @Body body: ShopPublicContentDecisionRequest,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-qa-moderation")
    suspend fun qaModerationDecision(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ShopQaModerationRequest,
    ): ShopQaModerationResponse

    @GET("functions/v1/adishop-qa-moderation?action=queue")
    suspend fun qaModerationQueue(
        @Header("Authorization") authorization: String,
        @Query("kind") kind: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): ShopQaModerationQueueResponse

    @GET("functions/v1/adishop-qa-moderation?action=preview")
    suspend fun qaModerationPreview(
        @Header("Authorization") authorization: String,
        @Query("kind") kind: String,
        @Query("id") id: String,
    ): ShopQaModerationPreviewResponse

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

    @GET("functions/v1/adishop-admin-stores")
    suspend fun adminStores(
        @Header("Authorization") authorization: String,
        @Query("status") status: String = "all",
        @Query("search") search: String = "",
    ): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-store-detail")
    suspend fun adminStoreDetail(
        @Header("Authorization") authorization: String,
        @Query("storeId") storeId: String,
    ): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-update-store-status")
    suspend fun adminUpdateStoreStatus(@Header("Authorization") authorization: String, @Body body: ShopAdminStoreStatusRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-refunds")
    suspend fun adminRefunds(@Header("Authorization") authorization: String, @Query("status") status: String = "all", @Query("search") search: String = ""): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-update-refund")
    suspend fun adminUpdateRefund(@Header("Authorization") authorization: String, @Body body: ShopAdminRequestUpdateRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-exchanges")
    suspend fun adminExchanges(@Header("Authorization") authorization: String, @Query("status") status: String = "all", @Query("search") search: String = ""): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-update-exchange")
    suspend fun adminUpdateExchange(@Header("Authorization") authorization: String, @Body body: ShopAdminRequestUpdateRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-reviews")
    suspend fun adminReviews(@Header("Authorization") authorization: String, @Query("status") status: String = "all", @Query("type") type: String = "all"): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-review-detail")
    suspend fun adminModerateReview(@Header("Authorization") authorization: String, @Body body: ShopAdminReviewModerationRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-promotions")
    suspend fun adminPromotions(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-promotions")
    suspend fun adminCreatePromotion(@Header("Authorization") authorization: String, @Body body: ShopAdminPromotionCreateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-promotions")
    suspend fun adminUpdatePromotionStatus(@Header("Authorization") authorization: String, @Body body: ShopAdminPromotionStatusRequest): ShopEnvelope<Unit>

    @GET("functions/v1/adishop-admin-coupons")
    suspend fun adminCoupons(@Header("Authorization") authorization: String): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-coupons")
    suspend fun adminCreateCoupon(@Header("Authorization") authorization: String, @Body body: ShopAdminCouponCreateRequest): ShopEnvelope<Unit>

    @POST("functions/v1/adishop-admin-coupons")
    suspend fun adminUpdateCouponStatus(@Header("Authorization") authorization: String, @Body body: ShopAdminCouponStatusRequest): ShopEnvelope<Unit>
}
