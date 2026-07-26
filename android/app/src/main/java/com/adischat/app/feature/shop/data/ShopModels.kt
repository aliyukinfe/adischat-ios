package com.adischat.app.feature.shop.data

import com.google.gson.annotations.SerializedName

data class ShopCategoryDto(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    val icon: String? = null,
)

data class ShopBannerDto(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("action_url") val actionUrl: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("display_order") val displayOrder: Int? = null,
)

data class ShopStoreDto(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val category: String? = null,
    val city: String? = null,
    val status: String? = null,
    @SerializedName("approval_status") val approvalStatus: String? = null,
    @SerializedName("average_store_rating") val averageStoreRating: Double? = null,
    @SerializedName("total_sales") val totalSales: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopProductImageDto(
    val id: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("storage_path") val storagePath: String? = null,
    val bucket: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerializedName("size_bytes") val sizeBytes: Int? = null,
    @SerializedName("is_primary") val isPrimary: Boolean? = null,
    @SerializedName("display_order") val displayOrder: Int? = null,
    @SerializedName("alt_text") val altText: String? = null,
)

data class ShopProductStoreDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val description: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val category: String? = null,
    val city: String? = null,
    @SerializedName("approval_status") val approvalStatus: String? = null,
    val status: String? = null,
    @SerializedName("average_store_rating") val averageStoreRating: Double? = null,
    @SerializedName("total_sales") val totalSales: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopProductDto(
    val id: String = "",
    val title: String = "",
    val slug: String = "",
    val brand: String? = null,
    @SerializedName("category_slug") val categorySlug: String? = null,
    @SerializedName("price_minor") val priceMinor: Int = 0,
    @SerializedName("compare_at_price_minor") val compareAtPriceMinor: Int? = null,
    @SerializedName("discount_percent") val discountPercent: Int? = null,
    @SerializedName("rating_average") val averageRating: Double? = null,
    @SerializedName("rating_count") val reviewCount: Int? = null,
    @SerializedName("completed_sales_count") val soldCount: Int? = null,
    @SerializedName("stock_status") val stockStatus: String? = null,
    @SerializedName("shop_product_images") val images: List<ShopProductImageDto>? = null,
    @SerializedName("shop_stores") val store: ShopProductStoreDto? = null,
)

data class ShopHomeState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val maintenance: Boolean = false,
    val banners: List<ShopBannerDto> = emptyList(),
    val categories: List<ShopCategoryDto> = emptyList(),
    val products: List<ShopProductDto> = emptyList(),
    val stores: List<ShopStoreDto> = emptyList(),
    val cartCount: Int = 0,
    val shopIdentityReady: Boolean = false,
    val message: String? = null,
)

data class ShopAssertionRequestBody(val client: String = "android")

data class ShopAssertionResponse(
    val ok: Boolean = false,
    val assertion: String = "",
    val expiresAt: Long = 0,
)

data class ShopSessionDto(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
)

data class ShopProfileDto(
    val id: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val status: String? = null,
    @SerializedName("adischat_user_id") val adisChatUserId: String? = null,
)

data class ShopSessionExchangeRequest(val assertion: String)
data class ShopRefreshRequest(val refreshToken: String)
data class ShopProductActionRequest(val productId: String)
data class ShopCartItemRequest(val productId: String, val variantId: String? = null, val quantity: Int = 1)
data class ShopCartItemUpdateRequest(val itemId: String, val quantity: Int)
data class ShopCartItemIdRequest(val itemId: String)
data class ShopAddressIdRequest(val id: String)
data class ShopOrderIdRequest(val orderId: String)
data class ShopCancelOrderRequest(val orderId: String, val reasonCode: String = "customer_request", val reasonText: String = "")
data class ShopNotificationIdRequest(val notificationId: String)
data class ShopSellerOrderActionRequest(
    val orderGroupId: String,
    val reason: String = "",
    val reasonCode: String? = null,
    val requestId: String? = null,
    val carrier: String? = null,
    val trackingNumber: String? = null,
)

data class ShopBuyerIssueOptionsRequest(val orderId: String)

data class ShopBuyerCreateIssueRequest(
    val orderId: String,
    val orderGroupId: String? = null,
    val reasonCode: String,
    val description: String = "",
    val idempotencyKey: String,
    val requestId: String,
)

data class ShopSellerDeliveryCodeRequest(
    val orderGroupId: String,
    val deliveryCode: String,
)

data class ShopSellerProductInventoryDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("variant_id") val variantId: String? = null,
    @SerializedName("quantity_available") val quantityAvailable: Int = 0,
    @SerializedName("quantity_reserved") val quantityReserved: Int = 0,
    @SerializedName("low_stock_threshold") val lowStockThreshold: Int = 3,
)

data class ShopSellerProductVariantDto(
    val id: String = "",
    val sku: String? = null,
    val title: String = "",
    val options: Map<String, Any>? = null,
    @SerializedName("price_minor") val priceMinor: Int = 0,
    @SerializedName("compare_at_price_minor") val compareAtPriceMinor: Int? = null,
    val status: String = "active",
    val inventory: ShopSellerProductInventoryDto? = null,
)

data class ShopSellerProductDto(
    val id: String = "",
    @SerializedName("store_id") val storeId: String = "",
    @SerializedName("category_id") val categoryId: String? = null,
    val brand: String? = null,
    val title: String = "",
    val slug: String = "",
    @SerializedName("category_slug") val categorySlug: String? = null,
    @SerializedName("short_description") val shortDescription: String? = null,
    val description: String? = null,
    val status: String = "draft",
    @SerializedName("moderation_status") val moderationStatus: String = "pending",
    val currency: String = "ETB",
    @SerializedName("price_minor") val priceMinor: Int = 0,
    @SerializedName("compare_at_price_minor") val compareAtPriceMinor: Int? = null,
    @SerializedName("stock_status") val stockStatus: String = "out_of_stock",
    @SerializedName("cash_on_delivery_enabled") val cashOnDeliveryEnabled: Boolean = true,
    @SerializedName("seller_sku") val sellerSku: String? = null,
    val specifications: Map<String, Any>? = null,
    @SerializedName("shipping_details") val shippingDetails: Map<String, Any>? = null,
    @SerializedName("return_policy") val returnPolicy: Map<String, Any>? = null,
    @SerializedName("validation_errors") val validationErrors: List<String> = emptyList(),
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val images: List<ShopProductImageDto> = emptyList(),
    val variants: List<ShopSellerProductVariantDto> = emptyList(),
    val inventory: List<ShopSellerProductInventoryDto> = emptyList(),
)

data class ShopSellerProductDraftPayload(
    val title: String,
    val brand: String? = null,
    val categoryId: String? = null,
    val shortDescription: String,
    val description: String,
    val priceMinor: Int,
    val compareAtPriceMinor: Int? = null,
    val stockQuantity: Int,
    val lowStockThreshold: Int = 3,
    val cashOnDeliveryEnabled: Boolean = true,
    val sellerSku: String? = null,
    val images: List<Map<String, Any>>? = null,
    val variants: List<Map<String, Any>>? = null,
    val specifications: Map<String, Any> = emptyMap(),
    val shippingDetails: Map<String, Any> = emptyMap(),
    val returnPolicy: Map<String, Any> = emptyMap(),
)

data class ShopSellerProductCreateRequest(val requestId: String)
data class ShopSellerProductUpdateRequest(val productId: String, val product: ShopSellerProductDraftPayload, val requestId: String)
data class ShopSellerProductSubmitRequest(val productId: String, val requestId: String)
data class ShopSellerProductMediaDeleteRequest(val productId: String, val imageId: String)
data class ShopSellerProductMediaReorderRequest(val productId: String, val imageIds: List<String>, val primaryImageId: String)

data class ShopAddressRequest(
    val id: String? = null,
    val label: String = "Home",
    val recipientName: String = "",
    val phone: String = "",
    val city: String = "",
    val addressLine1: String = "",
    val addressLine2: String? = null,
    val isDefault: Boolean = false,
)
data class ShopSellerApplicationRequest(
    val storeName: String,
    val businessType: String = "individual",
    val submit: Boolean = true,
)

data class ShopCreateCheckoutRequest(
    val idempotencyKey: String,
    val source: String = "CART",
    val productId: String? = null,
    val variantId: String? = null,
    val quantity: Int? = null,
)
data class ShopConfirmCheckoutRequest(
    val checkoutSessionId: String,
    val addressId: String,
    val paymentMethodCode: String,
    val idempotencyKey: String,
)

data class ShopPaymentMethodDto(
    val code: String = "",
    val label: String = "",
    val provider: String = "",
)

data class ShopNotificationDto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String? = null,
    val data: Map<String, Any>? = null,
    @SerializedName("read_at") val readAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopOrderStatusHistoryDto(
    val id: String = "",
    val status: String = "",
    @SerializedName("event_code") val eventCode: String? = null,
    @SerializedName("public_message") val publicMessage: String? = null,
    @SerializedName("previous_status") val previousStatus: String? = null,
    @SerializedName("next_status") val nextStatus: String? = null,
    @SerializedName("actor_role") val actorRole: String? = null,
    @SerializedName("occurred_at") val occurredAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopOrderItemDto(
    val id: String = "",
    @SerializedName("title_snapshot") val titleSnapshot: String = "",
    val quantity: Int = 0,
    @SerializedName("unit_price_minor") val unitPriceMinor: Int = 0,
    @SerializedName("line_total_minor") val lineTotalMinor: Int = 0,
    @SerializedName("image_snapshot") val imageSnapshot: String? = null,
)

data class ShopSellerCustomerDto(
    @SerializedName("display_name") val displayName: String? = null,
    val phone: String? = null,
)

data class ShopOrderAddressSnapshotDto(
    @SerializedName("recipient_name") val recipientName: String? = null,
    val phone: String? = null,
    val city: String? = null,
    @SerializedName("address_line1") val addressLine1: String? = null,
    @SerializedName("address_line2") val addressLine2: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class ShopOrderPaymentSnapshotDto(
    @SerializedName("method_code") val methodCode: String? = null,
    val status: String? = null,
    @SerializedName("amount_minor") val amountMinor: Int? = null,
    val currency: String? = null,
)

data class ShopDeliveryCodeStatusDto(
    val revealed: Boolean = false,
    val verified: Boolean = false,
    @SerializedName("failed_attempts") val failedAttempts: Int = 0,
    val locked: Boolean = false,
)

data class ShopOrderGroupDto(
    val id: String = "",
    @SerializedName("store_id") val storeId: String = "",
    val status: String = "",
    @SerializedName("store_name_snapshot") val storeNameSnapshot: String? = null,
    @SerializedName("payment_status") val paymentStatus: String? = null,
    @SerializedName("fulfillment_status") val fulfillmentStatus: String? = null,
    @SerializedName("shipment_carrier") val shipmentCarrier: String? = null,
    @SerializedName("shipment_tracking_number") val shipmentTrackingNumber: String? = null,
    @SerializedName("shop_order_items") val items: List<ShopOrderItemDto>? = null,
    @SerializedName("shop_orders") val order: ShopOrderDto? = null,
    val customer: ShopSellerCustomerDto? = null,
    @SerializedName("delivery_address") val deliveryAddress: ShopOrderAddressSnapshotDto? = null,
    val payment: ShopOrderPaymentSnapshotDto? = null,
    val history: List<ShopOrderStatusHistoryDto>? = null,
    @SerializedName("delivery_code_status") val deliveryCodeStatus: ShopDeliveryCodeStatusDto? = null,
)

data class ShopDeliveryCodeDto(
    @SerializedName("orderId") val orderId: String = "",
    @SerializedName("deliveryCode") val deliveryCode: String = "",
    val message: String? = null,
)

data class ShopIssueReasonOptionDto(
    val code: String = "",
    val label: String = "",
    val requiresDescription: Boolean = false,
)

data class ShopIssueOptionsDto(
    val eligible: Boolean = false,
    val state: String = "",
    val options: List<ShopIssueReasonOptionDto> = emptyList(),
    val message: String = "",
)

data class ShopOrderIssueEventDto(
    val id: String = "",
    @SerializedName("event_code") val eventCode: String = "",
    @SerializedName("previous_status") val previousStatus: String? = null,
    @SerializedName("next_status") val nextStatus: String? = null,
    @SerializedName("public_message") val publicMessage: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopOrderIssueDto(
    val id: String = "",
    @SerializedName("order_id") val orderId: String = "",
    @SerializedName("order_group_id") val orderGroupId: String? = null,
    @SerializedName("reason_code") val reasonCode: String = "",
    @SerializedName("reason_label") val reasonLabel: String? = null,
    val description: String = "",
    val status: String = "open",
    @SerializedName("buyer_visible_resolution") val buyerVisibleResolution: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val events: List<ShopOrderIssueEventDto> = emptyList(),
)

data class ShopCheckoutDto(
    val id: String = "",
    val state: String = "",
    val currency: String = "ETB",
    val subtotalMinor: Int = 0,
    val discountMinor: Int = 0,
    val shippingMinor: Int = 0,
    val taxMinor: Int = 0,
    val grandTotalMinor: Int = 0,
    val expiresAt: String? = null,
    val paymentMethods: List<ShopPaymentMethodDto> = emptyList(),
)

data class ShopEnvelope<T>(
    val ok: Boolean = false,
    val error: String? = null,
    val profile: ShopProfileDto? = null,
    val session: ShopSessionDto? = null,
    val cart: T? = null,
    val favorite: ShopFavoriteDto? = null,
    val favorites: List<ShopFavoriteDto>? = null,
    val recentlyViewed: List<ShopRecentlyViewedDto>? = null,
    val addresses: List<ShopAddressDto>? = null,
    val address: ShopAddressDto? = null,
    val orders: List<ShopOrderDto>? = null,
    val order: ShopOrderDto? = null,
    val checkout: ShopCheckoutDto? = null,
    val notifications: List<ShopNotificationDto>? = null,
    val unreadCount: Int? = null,
    val issueOptions: ShopIssueOptionsDto? = null,
    val issues: List<ShopOrderIssueDto>? = null,
    val issue: ShopOrderIssueDto? = null,
    val sellerOrderGroups: List<ShopOrderGroupDto>? = null,
    val sellerOrderGroup: ShopOrderGroupDto? = null,
    val sellerProducts: List<ShopSellerProductDto>? = null,
    val sellerProduct: ShopSellerProductDto? = null,
    val sellerProductImage: ShopProductImageDto? = null,
    val deliveryCode: ShopDeliveryCodeDto? = null,
    val status: String? = null,
    val application: ShopSellerApplicationDto? = null,
    val adminSession: ShopAdminSessionDto? = null,
    val dashboard: ShopAdminDashboardDto? = null,
    val sellerApplications: List<ShopAdminSellerApplicationDto>? = null,
    val sellerApplicationDetail: ShopAdminSellerApplicationDto? = null,
    val products: List<ShopAdminProductDto>? = null,
    val productDetail: ShopAdminProductDetailDto? = null,
    val adminOrders: List<ShopAdminOrderDto>? = null,
    val orderDetail: ShopAdminOrderDetailDto? = null,
    val auditLog: List<ShopAdminAuditLogDto>? = null,
    val adminUsers: List<ShopAdminUserDto>? = null,
)

data class ShopCartItemDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("variant_id") val variantId: String? = null,
    val quantity: Int = 0,
    @SerializedName("unit_price_minor") val unitPriceMinor: Int = 0,
    @SerializedName("shop_products") val product: ShopProductDto? = null,
)

data class ShopCartTotalsDto(
    val subtotalMinor: Int = 0,
    val discountMinor: Int = 0,
    val deliveryEstimateMinor: Int = 0,
    val taxMinor: Int = 0,
    val totalMinor: Int = 0,
)

data class ShopCartDto(
    val id: String = "",
    val currency: String = "ETB",
    val items: List<ShopCartItemDto> = emptyList(),
    val totals: ShopCartTotalsDto = ShopCartTotalsDto(),
)

data class ShopFavoriteDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("shop_products") val product: ShopProductDto? = null,
)

data class ShopRecentlyViewedDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("viewed_at") val viewedAt: String? = null,
    @SerializedName("shop_products") val product: ShopProductDto? = null,
)

data class ShopAddressDto(
    val id: String = "",
    val label: String = "",
    @SerializedName("recipient_name") val recipientName: String = "",
    val phone: String = "",
    val city: String = "",
    @SerializedName("address_line1") val addressLine1: String = "",
    @SerializedName("address_line2") val addressLine2: String? = null,
    @SerializedName("is_default") val isDefault: Boolean = false,
)

data class ShopOrderDto(
    val id: String = "",
    @SerializedName("order_number") val orderNumber: String? = null,
    val status: String = "",
    @SerializedName("payment_status") val paymentStatus: String? = null,
    @SerializedName("fulfillment_status") val fulfillmentStatus: String? = null,
    @SerializedName("delivery_status") val deliveryStatus: String? = null,
    val currency: String = "ETB",
    @SerializedName("total_minor") val totalMinor: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("shop_order_groups") val groups: List<ShopOrderGroupDto>? = null,
    @SerializedName("shop_order_status_history") val history: List<ShopOrderStatusHistoryDto>? = null,
    val issues: List<ShopOrderIssueDto> = emptyList(),
)

data class ShopSellerApplicationDto(
    val id: String = "",
    val status: String = "not_started",
    @SerializedName("proposed_store_name") val proposedStoreName: String? = null,
    @SerializedName("business_type") val businessType: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
)

data class ShopAdminSessionDto(
    val profileId: String = "",
    val primaryRole: String = "",
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

data class ShopAdminDashboardDto(
    val pendingSellerApplications: Int = 0,
    val sellersUnderReview: Int = 0,
    @SerializedName("pendingProductReviews")
    val productsAwaitingReview: Int = 0,
    val productsRequiringChanges: Int = 0,
    val suspendedSellers: Int = 0,
    val suspendedProducts: Int = 0,
    @SerializedName("activeOrders")
    val openOrders: Int = 0,
    val ordersNeedingSupport: Int = 0,
    @SerializedName("todayModerationActions")
    val auditEvents24h: Int = 0,
)

data class ShopAdminSellerApplicationDto(
    val id: String = "",
    val status: String? = null,
    @SerializedName("proposed_store_name")
    val storeName: String? = null,
    @SerializedName("business_type")
    val businessType: String? = null,
    @SerializedName("applicant_name")
    val applicantName: String? = null,
    @SerializedName("applicant_email")
    val applicantEmail: String? = null,
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
)

data class ShopAdminProductDto(
    val id: String = "",
    val title: String? = null,
    val status: String? = null,
    @SerializedName("moderation_status")
    val moderationStatus: String? = null,
    @SerializedName("price_minor")
    val priceMinor: Int = 0,
    @SerializedName("store_name")
    val storeName: String? = null,
    @SerializedName("seller_name")
    val sellerName: String? = null,
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
)

data class ShopAdminProductReviewDto(
    val id: String = "",
    @SerializedName("decision")
    val decision: String? = null,
    @SerializedName("reason_code")
    val reasonCode: String? = null,
    @SerializedName("public_reason")
    val publicReason: String? = null,
    @SerializedName("private_notes")
    val privateNotes: String? = null,
    @SerializedName("previous_status")
    val previousStatus: String? = null,
    @SerializedName("new_status")
    val newStatus: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class ShopAdminProductDetailDto(
    val product: ShopSellerProductDto? = null,
    val store: ShopProductStoreDto? = null,
    val images: List<ShopProductImageDto> = emptyList(),
    val inventory: List<ShopSellerProductInventoryDto> = emptyList(),
    val reviews: List<ShopAdminProductReviewDto> = emptyList(),
)

data class ShopAdminOrderDto(
    val id: String = "",
    @SerializedName("order_number")
    val orderNumber: String? = null,
    val status: String? = null,
    @SerializedName("payment_status")
    val paymentStatus: String? = null,
    @SerializedName("fulfillment_status")
    val fulfillmentStatus: String? = null,
    @SerializedName("total_minor")
    val totalMinor: Int = 0,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class ShopAdminOrderNoteDto(
    val id: String = "",
    @SerializedName("order_group_id")
    val orderGroupId: String? = null,
    val visibility: String? = null,
    val note: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class ShopAdminDeliveryAttemptDto(
    val id: String = "",
    @SerializedName("order_group_id")
    val orderGroupId: String? = null,
    val success: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class ShopAdminInventorySnapshotDto(
    @SerializedName("product_id")
    val productId: String? = null,
    @SerializedName("variant_id")
    val variantId: String? = null,
    @SerializedName("quantity_available")
    val quantityAvailable: Int = 0,
    @SerializedName("quantity_reserved")
    val quantityReserved: Int = 0,
)

data class ShopAdminOrderDetailDto(
    val order: ShopAdminOrderDto? = null,
    val groups: List<ShopOrderGroupDto> = emptyList(),
    val address: ShopOrderAddressSnapshotDto? = null,
    val payments: List<ShopOrderPaymentSnapshotDto> = emptyList(),
    val history: List<ShopOrderStatusHistoryDto> = emptyList(),
    val deliveryCodeStatus: ShopDeliveryCodeStatusDto? = null,
    val deliveryAttempts: List<ShopAdminDeliveryAttemptDto> = emptyList(),
    val issues: List<ShopOrderIssueDto> = emptyList(),
    val notes: List<ShopAdminOrderNoteDto> = emptyList(),
    val audit: List<ShopAdminAuditLogDto> = emptyList(),
    val inventory: List<ShopAdminInventorySnapshotDto> = emptyList(),
)

data class ShopAdminAuditLogDto(
    val id: String = "",
    @SerializedName("action_type")
    val action: String? = null,
    @SerializedName("target_type")
    val entityType: String? = null,
    @SerializedName("target_id")
    val entityId: String? = null,
    @SerializedName("actor_role")
    val actorName: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
)

data class ShopAdminUserDto(
    val profileId: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val status: String? = null,
)

data class ShopAdminReviewSellerRequest(
    val applicationId: String,
    val action: String,
    val reason: String = "",
    val privateNotes: String = "",
    val requestId: String,
)

data class ShopAdminReviewProductRequest(
    val productId: String,
    val action: String,
    val reasonCode: String = "",
    val publicReason: String = "",
    val privateNotes: String = "",
    val requestId: String,
)

data class ShopAdminOrderNoteRequest(
    val orderId: String,
    val note: String,
    val visibility: String = "private",
)

data class ShopAdminRoleRequest(
    val profileId: String,
    val roleCode: String,
    val reason: String,
)

data class ShopAdminIssueUpdateRequest(
    val issueId: String,
    val status: String,
    val buyerVisibleMessage: String,
    val privateNote: String = "",
    val requestId: String,
)

data class ShopProtectedState(
    val loading: Boolean = false,
    val error: String? = null,
    val cart: ShopCartDto? = null,
    val favorites: List<ShopFavoriteDto> = emptyList(),
    val recentlyViewed: List<ShopRecentlyViewedDto> = emptyList(),
    val addresses: List<ShopAddressDto> = emptyList(),
    val orders: List<ShopOrderDto> = emptyList(),
    val checkout: ShopCheckoutDto? = null,
    val checkoutOrder: ShopOrderDto? = null,
    val selectedOrder: ShopOrderDto? = null,
    val issueOptions: ShopIssueOptionsDto? = null,
    val selectedOrderIssues: List<ShopOrderIssueDto> = emptyList(),
    val notifications: List<ShopNotificationDto> = emptyList(),
    val notificationUnreadCount: Int = 0,
    val sellerOrderGroups: List<ShopOrderGroupDto> = emptyList(),
    val sellerProducts: List<ShopSellerProductDto> = emptyList(),
    val selectedSellerProduct: ShopSellerProductDto? = null,
    val deliveryCode: ShopDeliveryCodeDto? = null,
    val sellerStatus: String = "not_started",
    val sellerApplication: ShopSellerApplicationDto? = null,
    val adminLoading: Boolean = false,
    val adminError: String? = null,
    val adminSession: ShopAdminSessionDto? = null,
    val adminDashboard: ShopAdminDashboardDto? = null,
    val adminSellerApplications: List<ShopAdminSellerApplicationDto> = emptyList(),
    val adminProducts: List<ShopAdminProductDto> = emptyList(),
    val selectedAdminProduct: ShopAdminProductDetailDto? = null,
    val adminOrders: List<ShopAdminOrderDto> = emptyList(),
    val selectedAdminOrder: ShopAdminOrderDetailDto? = null,
    val adminAuditLog: List<ShopAdminAuditLogDto> = emptyList(),
    val adminUsers: List<ShopAdminUserDto> = emptyList(),
    val busyIds: Set<String> = emptySet(),
)

sealed class ShopIdentityState {
    data object SignedOut : ShopIdentityState()
    data object Syncing : ShopIdentityState()
    data class Ready(val profile: ShopProfileDto) : ShopIdentityState()
    data class Unavailable(val message: String) : ShopIdentityState()
}
