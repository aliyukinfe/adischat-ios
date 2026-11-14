package com.adischat.app.feature.shop.data

import com.adischat.app.BuildConfig
import com.google.gson.annotations.SerializedName

data class ShopCategoryDto(
    val id: String = "",
    @SerializedName("parent_id") val parentId: String? = null,
    val name: String = "",
    val slug: String = "",
    val code: String = "",
    val description: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val icon: String? = null,
    val level: Int = 0,
    @SerializedName("is_leaf") val isLeaf: Boolean = true,
    @SerializedName("seller_selectable") val sellerSelectable: Boolean = true,
    @SerializedName("buyer_visible") val buyerVisible: Boolean = true,
    @SerializedName("search_terms") val searchTerms: List<String> = emptyList(),
    @SerializedName("display_order") val displayOrder: Int = 0,
)

data class ShopSellingUnitDto(
    val code: String = "PIECE",
    val name: String = "Piece",
    val symbol: String = "pc",
    @SerializedName("quantity_scale") val quantityScale: Int = 0,
    @SerializedName("display_order") val displayOrder: Int = 0,
)

data class ShopBannerDto(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("action_url") val actionUrl: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("display_order") val displayOrder: Int? = null,
    @SerializedName("start_at") val startAt: String? = null,
    @SerializedName("end_at") val endAt: String? = null,
    @SerializedName("is_seed") val isSeed: Boolean? = null,
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

data class ShopProductSpecificationDto(
    val code: String = "",
    val name: String = "",
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val valueBoolean: Boolean? = null,
    val valueJson: Any? = null,
    val unit: String? = null,
)

data class ShopProductOptionValueDto(
    val id: String = "",
    val code: String = "",
    val value: String = "",
    val displayValue: String = "",
    val colorHex: String? = null,
    val position: Int = 0,
)

data class ShopProductOptionDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val position: Int = 0,
    val values: List<ShopProductOptionValueDto> = emptyList(),
)

data class ShopPublicVariantDto(
    val id: String = "",
    val title: String = "",
    val options: Map<String, String> = emptyMap(),
    @SerializedName(value = "price_minor", alternate = ["priceMinor"]) val priceMinor: Int = 0,
    @SerializedName(value = "compare_at_price_minor", alternate = ["compareAtPriceMinor"]) val compareAtPriceMinor: Int? = null,
    val status: String = "active",
    val availableQuantity: Int = 0,
    val lowStockThreshold: Int = 0,
    val imageIds: List<String> = emptyList(),
)

data class ShopPriceRangeDto(
    val minimumMinor: Int = 0,
    val maximumMinor: Int = 0,
)

data class ShopProductDto(
    val id: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    val title: String = "",
    val slug: String = "",
    val brand: String? = null,
    @SerializedName(value = "category_slug", alternate = ["categorySlug"]) val categorySlug: String? = null,
    @SerializedName(value = "price_minor", alternate = ["priceMinor"]) val priceMinor: Int = 0,
    @SerializedName(value = "compare_at_price_minor", alternate = ["compareAtPriceMinor"]) val compareAtPriceMinor: Int? = null,
    @SerializedName("discount_percent") val discountPercent: Int? = null,
    @SerializedName("rating_average") val averageRating: Double? = null,
    @SerializedName("rating_count") val reviewCount: Int? = null,
    @SerializedName("completed_sales_count") val soldCount: Int? = null,
    @SerializedName(value = "stock_status", alternate = ["stockStatus"]) val stockStatus: String? = null,
    @SerializedName("shop_product_images") val images: List<ShopProductImageDto>? = null,
    @SerializedName("shop_stores") val store: ShopProductStoreDto? = null,
    @SerializedName(value = "short_description", alternate = ["shortDescription"]) val shortDescription: String? = null,
    val description: String? = null,
    @SerializedName(value = "category_id", alternate = ["categoryId"]) val categoryId: String? = null,
    @SerializedName(value = "selling_unit_code", alternate = ["sellingUnitCode"]) val sellingUnitCode: String = "PIECE",
    @SerializedName(value = "selling_unit", alternate = ["sellingUnit"]) val sellingUnit: ShopSellingUnitDto? = null,
    @SerializedName(value = "minimum_order_quantity", alternate = ["minimumOrderQuantity"]) val minimumOrderQuantity: Int = 1,
    val specifications: List<ShopProductSpecificationDto> = emptyList(),
    val options: List<ShopProductOptionDto> = emptyList(),
    val variants: List<ShopPublicVariantDto> = emptyList(),
    val priceRange: ShopPriceRangeDto? = null,
    val aggregateAvailable: Int? = null,
    @SerializedName("has_variants") val hasVariants: Boolean = false,
    @SerializedName("price_max_minor") val priceMaxMinor: Int? = null,
    @SerializedName("aggregate_available_quantity") val aggregateAvailableQuantity: Int? = null,
    val verifiedBenefits: List<ShopVerifiedBenefitDto> = emptyList(),
)

data class ShopHomeState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val maintenance: Boolean = false,
    val banners: List<ShopBannerDto> = emptyList(),
    val categories: List<ShopCategoryDto> = emptyList(),
    val sellingUnits: List<ShopSellingUnitDto> = emptyList(),
    val products: List<ShopProductDto> = emptyList(),
    val stores: List<ShopStoreDto> = emptyList(),
    val cartCount: Int = 0,
    val shopIdentityReady: Boolean = false,
    val message: String? = null,
    val productDetails: Map<String, ShopProductDto> = emptyMap(),
    val buyerCategoryAttributes: Map<String, List<ShopCategoryAttributeDto>> = emptyMap(),
    val buyerFilterLoadingCategoryId: String? = null,
    val discoveryProducts: List<ShopProductDto>? = null,
    val discoveryLoading: Boolean = false,
)

data class ShopProductCatalogRequest(val p_product_id: String)
data class ShopCategoryAttributeRequest(val p_category_id: String)

data class ShopCategoryAttributeDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val displayName: String = "",
    val dataType: String = "text",
    val inputType: String = "text",
    val required: Boolean = false,
    val variantCapable: Boolean = false,
    val filterable: Boolean = false,
    val allowsMultiple: Boolean = false,
    val unit: String? = null,
    val suggestions: List<String> = emptyList(),
    val sortOrder: Int = 0,
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
data class ShopRevokeRequest(val refreshToken: String, val action: String = "revoke")
data class ShopRevokeAllRequest(val assertion: String, val action: String = "revoke-all")
data class ShopProductActionRequest(val productId: String)
data class ShopCartItemRequest(val productId: String, val variantId: String? = null, val quantity: Int = 1)
data class ShopCartItemUpdateRequest(val itemId: String, val quantity: Int)
data class ShopCartItemIdRequest(val itemId: String)
data class ShopAddressIdRequest(val id: String)
data class ShopOrderIdRequest(val orderId: String)
data class ShopBuyAgainRequest(val orderId: String, val requestId: String)
data class ShopCancelOrderRequest(val orderId: String, val reasonCode: String = "customer_request", val reasonText: String = "")
enum class MarketplaceNotificationContext(val wireValue: String) { BUYER("buyer"), SELLER("seller") }
data class ShopNotificationScopeRequest(val context: String, val storeId: String? = null)
data class ShopNotificationIdRequest(val notificationId: String, val context: String, val storeId: String? = null)
data class ShopOpenConversationRequest(
    val storeId: String? = null,
    val productId: String? = null,
    val variantId: String? = null,
    val orderGroupId: String? = null,
)
data class ShopSendMessageRequest(
    val conversationId: String,
    val body: String,
    val clientMessageId: String,
)
data class ShopConversationActionRequest(
    val conversationId: String,
    val action: String,
    val hours: Int? = null,
)
data class ShopSellerOrderActionRequest(
    val orderGroupId: String,
    val storeId: String? = null,
    val reason: String = "",
    val reasonCode: String? = null,
    val requestId: String? = null,
    val expectedVersion: Int? = null,
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
    val storeId: String? = null,
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
    @SerializedName("imageIds") val imageIds: List<String> = emptyList(),
)

data class ShopSellerProductSpecificationValueDto(
    val attributeCode: String = "",
    val displayName: String = "",
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val valueBoolean: Boolean? = null,
    val unit: String? = null,
)

data class ShopSellerProductOptionValueDto(
    val id: String = "",
    val code: String = "",
    val value: String = "",
    val displayValue: String = "",
    val colorHex: String? = null,
    val position: Int = 0,
    val status: String = "active",
)

data class ShopSellerProductOptionDto(
    val id: String = "",
    val attributeCode: String = "",
    val name: String = "",
    val position: Int = 0,
    val status: String = "active",
    val values: List<ShopSellerProductOptionValueDto> = emptyList(),
)

data class ShopSellerProductDto(
    val id: String = "",
    @SerializedName("store_id") val storeId: String = "",
    @SerializedName("category_id") val categoryId: String? = null,
    val brand: String? = null,
    val title: String = "",
    val slug: String = "",
    @SerializedName("category_slug") val categorySlug: String? = null,
    @SerializedName("selling_unit_code") val sellingUnitCode: String = "PIECE",
    @SerializedName("minimum_order_quantity") val minimumOrderQuantity: Int = 1,
    @SerializedName("product_type_description") val productTypeDescription: String? = null,
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
    val specificationValues: List<ShopSellerProductSpecificationValueDto> = emptyList(),
    val productOptions: List<ShopSellerProductOptionDto> = emptyList(),
)

data class ShopSellerSpecificationInput(
    val attributeCode: String,
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val valueBoolean: Boolean? = null,
    val unit: String? = null,
)

data class ShopSellerOptionValueInput(
    val code: String,
    val value: String,
    val displayValue: String,
    val colorHex: String? = null,
    val position: Int = 0,
)

data class ShopSellerProductOptionInput(
    val attributeCode: String,
    val name: String,
    val position: Int,
    val values: List<ShopSellerOptionValueInput>,
)

data class ShopSellerVariantInput(
    val id: String? = null,
    val title: String,
    val sku: String? = null,
    val barcode: String? = null,
    val options: Map<String, String>,
    val priceMinor: Int,
    val compareAtPriceMinor: Int? = null,
    val status: String = "active",
    val quantityAvailable: Int,
    val lowStockThreshold: Int = 3,
    val manageInventory: Boolean = true,
    val imageIds: List<String> = emptyList(),
)

data class ShopSellerProductDraftPayload(
    val title: String,
    val brand: String? = null,
    val categoryId: String? = null,
    val sellingUnitCode: String = "PIECE",
    val minimumOrderQuantity: Int = 1,
    val productTypeDescription: String? = null,
    val shortDescription: String,
    val description: String,
    val priceMinor: Int,
    val compareAtPriceMinor: Int? = null,
    val stockQuantity: Int,
    val lowStockThreshold: Int = 3,
    val cashOnDeliveryEnabled: Boolean = true,
    val sellerSku: String? = null,
    val images: List<Map<String, Any>>? = null,
    val specifications: Map<String, Any> = emptyMap(),
    val shippingDetails: Map<String, Any> = emptyMap(),
    val returnPolicy: Map<String, Any> = emptyMap(),
    val specificationValues: List<ShopSellerSpecificationInput>? = null,
    val productOptions: List<ShopSellerProductOptionInput>? = null,
    @SerializedName("variants") val variantCatalog: List<ShopSellerVariantInput>? = null,
)

data class ShopSellerProductCreateRequest(val storeId: String, val requestId: String)
data class ShopSellerProductUpdateRequest(val storeId: String, val productId: String, val product: ShopSellerProductDraftPayload, val requestId: String)
data class ShopSellerProductArchivePayload(val archiveProduct: Boolean = true)
data class ShopSellerProductArchiveRequest(val storeId: String, val productId: String, val product: ShopSellerProductArchivePayload, val requestId: String)
data class ShopSellerProductSubmitRequest(val storeId: String, val productId: String, val requestId: String)
data class ShopSellerProductMediaDeleteRequest(val storeId: String, val productId: String, val imageId: String)
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
    val storeName: String = "",
    @SerializedName("proposed_store_name") val proposedStoreName: String = storeName,
    @SerializedName("legal_business_name") val legalBusinessName: String = "",
    @SerializedName("business_type") val businessType: String = "",
    @SerializedName("business_registration_number") val businessRegistrationNumber: String = "",
    @SerializedName("tax_identifier") val taxIdentifier: String = "",
    @SerializedName("store_description") val storeDescription: String = "",
    @SerializedName("expected_product_categories") val expectedProductCategories: List<String> = emptyList(),
    @SerializedName("short_tagline") val shortTagline: String = "",
    @SerializedName("owner_full_name") val ownerFullName: String = "",
    @SerializedName("contact_person_name") val contactPersonName: String = "",
    val phone: String = "",
    val email: String = "",
    @SerializedName("support_phone") val supportPhone: String = "",
    @SerializedName("support_email") val supportEmail: String = "",
    val country: String = "Ethiopia",
    @SerializedName("region_state") val regionState: String = "",
    val city: String = "",
    val district: String = "",
    @SerializedName("address_line") val addressLine: String = "",
    @SerializedName("postal_code") val postalCode: String = "",
    @SerializedName("location_notes") val locationNotes: String = "",
    @SerializedName("fulfillment_method") val fulfillmentMethod: String = "",
    @SerializedName("service_area") val serviceArea: String = "",
    @SerializedName("estimated_handling_time") val estimatedHandlingTime: String = "",
    @SerializedName("return_policy") val returnPolicy: String = "",
    @SerializedName("exchange_policy") val exchangePolicy: String = "",
    @SerializedName("shipping_policy") val shippingPolicy: String = "",
    @SerializedName("warranty_policy") val warrantyPolicy: String = "",
    @SerializedName("customer_service_policy") val customerServicePolicy: String = "",
    @SerializedName("terms_accepted") val termsAccepted: Boolean = false,
    @SerializedName("privacy_acknowledged") val privacyAcknowledged: Boolean = false,
    @SerializedName("seller_policy_accepted") val sellerPolicyAccepted: Boolean = false,
    @SerializedName("declaration_of_accuracy") val declarationOfAccuracy: Boolean = false,
    @SerializedName("current_step") val currentStep: Int = 1,
    val submit: Boolean = true,
    val requestId: String = "",
)

data class ShopSellerApplicationInfoResponseRequest(
    val message: String,
    val requestId: String,
)

data class ShopSellerApplicationDocumentRemoveRequest(
    val documentId: String,
    val requestId: String,
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
data class ShopApplyCouponRequest(
    val checkoutSessionId: String,
    val couponCode: String,
)
data class ShopApplyVoucherRequest(
    val checkoutSessionId: String,
    val voucherCode: String,
)
data class ShopRemoveDiscountRequest(
    val checkoutSessionId: String,
    val discountType: String,
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
    @SerializedName("recipient_context") val recipientContext: String? = null,
    @SerializedName("store_id") val storeId: String? = null,
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
    val reason: String? = null,
    @SerializedName("actor_role") val actorRole: String? = null,
    @SerializedName("occurred_at") val occurredAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopOrderItemDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("title_snapshot") val titleSnapshot: String = "",
    val quantity: Int = 0,
    @SerializedName("unit_price_minor") val unitPriceMinor: Int = 0,
    @SerializedName("line_total_minor") val lineTotalMinor: Int = 0,
    @SerializedName("image_snapshot") val imageSnapshot: String? = null,
    @SerializedName("variant_snapshot") val variantSnapshot: ShopOrderVariantSnapshotDto? = null,
    @SerializedName("unit_snapshot") val unitSnapshot: ShopUnitSnapshotDto? = null,
)

data class ShopUnitSnapshotDto(
    val code: String = "PIECE",
    val name: String = "Piece",
    val symbol: String = "pc",
    val minimumOrderQuantity: Int = 1,
)

data class ShopOrderVariantSnapshotDto(
    val id: String? = null,
    val title: String? = null,
    val options: Map<String, String> = emptyMap(),
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
    @SerializedName("seller_response_required_at") val sellerResponseRequiredAt: String? = null,
    @SerializedName("seller_responded_at") val sellerRespondedAt: String? = null,
    @SerializedName("seller_response_expired_at") val sellerResponseExpiredAt: String? = null,
    @SerializedName("seller_response_status") val sellerResponseStatus: String = "pending",
    @SerializedName("seller_response_sla_hours") val sellerResponseSlaHours: Int = 24,
    @SerializedName("seller_response_version") val sellerResponseVersion: Int = 1,
    @SerializedName("seller_response_expiration_source") val sellerResponseExpirationSource: String? = null,
    @SerializedName("seller_response_refund_status") val sellerResponseRefundStatus: String = "not_required",
    @SerializedName("seller_response_refund_request_id") val sellerResponseRefundRequestId: String? = null,
    @SerializedName("rejected_reason") val rejectedReason: String? = null,
    @SerializedName("rejected_reason_code") val rejectedReasonCode: String? = null,
    @SerializedName("buyer_visible_rejection_reason") val buyerVisibleRejectionReason: String? = null,
    @SerializedName("server_now") val serverNow: String? = null,
    @Transient val serverReceivedAtMillis: Long? = null,
    @SerializedName("shipment_carrier") val shipmentCarrier: String? = null,
    @SerializedName("shipment_tracking_number") val shipmentTrackingNumber: String? = null,
    @SerializedName("shop_order_items") val items: List<ShopOrderItemDto>? = null,
    @SerializedName("shop_orders") val order: ShopOrderDto? = null,
    val customer: ShopSellerCustomerDto? = null,
    @SerializedName("delivery_address") val deliveryAddress: ShopOrderAddressSnapshotDto? = null,
    val payment: ShopOrderPaymentSnapshotDto? = null,
    val history: List<ShopOrderStatusHistoryDto>? = null,
    @SerializedName("delivery_code_status") val deliveryCodeStatus: ShopDeliveryCodeStatusDto? = null,
    val issues: List<ShopOrderIssueDto> = emptyList(),
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

data class ShopPostDeliveryActionsDto(
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("can_review_product") val canReviewProduct: Boolean = false,
    @SerializedName("can_review_store") val canReviewStore: Boolean = false,
    @SerializedName("can_request_refund") val canRequestRefund: Boolean = false,
    @SerializedName("can_request_exchange") val canRequestExchange: Boolean = false,
    @SerializedName("can_open_buyer_protection") val canOpenBuyerProtection: Boolean = true,
    @SerializedName("can_contact_seller") val canContactSeller: Boolean = true,
    @SerializedName("can_buy_again") val canBuyAgain: Boolean = false,
    @SerializedName("can_view_receipt") val canViewReceipt: Boolean = true,
    @SerializedName("can_view_timeline") val canViewTimeline: Boolean = true,
    @SerializedName("review_deadline") val reviewDeadline: String? = null,
    @SerializedName("refund_deadline") val refundDeadline: String? = null,
    @SerializedName("exchange_deadline") val exchangeDeadline: String? = null,
    @SerializedName("existing_product_review_ids") val existingProductReviewIds: Map<String, String> = emptyMap(),
    @SerializedName("existing_store_review_ids") val existingStoreReviewIds: Map<String, String> = emptyMap(),
    @SerializedName("active_refund_request_ids") val activeRefundRequestIds: List<String> = emptyList(),
    @SerializedName("active_exchange_request_ids") val activeExchangeRequestIds: List<String> = emptyList(),
    @SerializedName("disabled_reasons") val disabledReasons: Map<String, String> = emptyMap(),
)

data class ShopReviewDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("store_id") val storeId: String? = null,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("order_item_id") val orderItemId: String? = null,
    @SerializedName("order_group_id") val orderGroupId: String? = null,
    val rating: Int = 0,
    @SerializedName("service_rating") val serviceRating: Int? = null,
    @SerializedName("communication_rating") val communicationRating: Int? = null,
    @SerializedName("delivery_rating") val deliveryRating: Int? = null,
    val title: String? = null,
    val body: String? = null,
    val status: String = "",
    @SerializedName("verified_purchase") val verifiedPurchase: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopOrderReviewsDto(
    val productReviews: List<ShopReviewDto> = emptyList(),
    val storeReviews: List<ShopReviewDto> = emptyList(),
)

data class ShopReviewOptionsDto(
    val actions: ShopPostDeliveryActionsDto? = null,
    val reviews: ShopOrderReviewsDto? = null,
)

data class ShopReviewCreateRequest(
    val orderId: String,
    val orderItemId: String? = null,
    val orderGroupId: String? = null,
    val rating: Int,
    val serviceRating: Int? = null,
    val communicationRating: Int? = null,
    val deliveryRating: Int? = null,
    val title: String = "",
    val body: String = "",
    val idempotencyKey: String,
    val requestId: String,
)

data class ShopRequestEventDto(
    @SerializedName("event_code") val eventCode: String? = null,
    @SerializedName("next_status") val nextStatus: String? = null,
    @SerializedName("public_message") val publicMessage: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopRefundRequestDto(
    val id: String = "",
    @SerializedName("order_id") val orderId: String = "",
    @SerializedName("order_group_id") val orderGroupId: String? = null,
    @SerializedName("reason_code") val reasonCode: String = "",
    @SerializedName("reason_label") val reasonLabel: String? = null,
    val description: String? = null,
    @SerializedName("requested_amount") val requestedAmount: Int = 0,
    val currency: String = "ETB",
    val status: String = "submitted",
    val version: Int = 1,
    @SerializedName("seller_response") val sellerResponse: String? = null,
    @SerializedName("buyer_visible_update") val buyerVisibleUpdate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val events: List<ShopRequestEventDto> = emptyList(),
)

data class ShopExchangeRequestDto(
    val id: String = "",
    @SerializedName("order_id") val orderId: String = "",
    @SerializedName("order_group_id") val orderGroupId: String? = null,
    @SerializedName("reason_code") val reasonCode: String = "",
    @SerializedName("reason_label") val reasonLabel: String? = null,
    val description: String? = null,
    val status: String = "submitted",
    val version: Int = 1,
    @SerializedName("seller_response") val sellerResponse: String? = null,
    @SerializedName("buyer_visible_update") val buyerVisibleUpdate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val events: List<ShopRequestEventDto> = emptyList(),
)

data class ShopPostDeliveryRequestBody(
    val orderId: String,
    val orderItemId: String? = null,
    val requestedVariantId: String? = null,
    val quantity: Int = 1,
    val reasonCode: String = "other",
    val description: String = "",
    val idempotencyKey: String,
    val requestId: String,
)

data class ShopBuyAgainResultDto(
    @SerializedName("added_items") val addedItems: List<Map<String, Any>> = emptyList(),
    @SerializedName("unavailable_items") val unavailableItems: List<Map<String, Any>> = emptyList(),
    @SerializedName("price_changed_items") val priceChangedItems: List<Map<String, Any>> = emptyList(),
    @SerializedName("stock_changed_items") val stockChangedItems: List<Map<String, Any>> = emptyList(),
)

data class ShopReceiptDto(
    @SerializedName("order_number") val orderNumber: String? = null,
    @SerializedName("order_date") val orderDate: String? = null,
    val currency: String = "ETB",
    @SerializedName("subtotal_minor") val subtotalMinor: Int = 0,
    @SerializedName("delivery_fee_minor") val deliveryFeeMinor: Int = 0,
    @SerializedName("discount_minor") val discountMinor: Int = 0,
    @SerializedName("total_minor") val totalMinor: Int = 0,
    @SerializedName("payment_status") val paymentStatus: String? = null,
    @SerializedName("fulfillment_status") val fulfillmentStatus: String? = null,
    @SerializedName("delivery_status") val deliveryStatus: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    val stores: List<ShopReceiptStoreDto> = emptyList(),
    val payment: ShopReceiptPaymentDto? = null,
)

data class ShopReceiptStoreDto(
    @SerializedName("store_name") val storeName: String? = null,
    val status: String? = null,
    val items: List<ShopReceiptItemDto> = emptyList(),
)

data class ShopReceiptItemDto(
    val title: String? = null,
    val variant: Map<String, Any>? = null,
    val quantity: Int = 0,
    @SerializedName("unit_price_minor") val unitPriceMinor: Int = 0,
    @SerializedName("line_total_minor") val lineTotalMinor: Int = 0,
)

data class ShopReceiptPaymentDto(
    @SerializedName("method_code") val methodCode: String? = null,
    val status: String? = null,
    @SerializedName("amount_minor") val amountMinor: Int = 0,
    val currency: String? = null,
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
    val couponCode: String? = null,
    val couponDiscountMinor: Int = 0,
    val voucherCode: String? = null,
    val voucherAppliedMinor: Int = 0,
    val remainingPayableMinor: Int = 0,
    val isFullyCoveredByVoucher: Boolean = false,
)

data class ShopSellerStoreDto(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    val category: String? = null,
    val city: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val status: String = "",
    @SerializedName("approval_status") val approvalStatus: String = "",
    @SerializedName("address_line") val addressLine: String? = null,
    @SerializedName("region_state") val regionState: String? = null,
    val country: String = "Ethiopia",
    @SerializedName("contact_phone") val contactPhone: String? = null,
    @SerializedName("contact_email") val contactEmail: String? = null,
    @SerializedName("business_hours") val businessHours: Map<String, Any> = emptyMap(),
    @SerializedName("social_links") val socialLinks: Map<String, Any> = emptyMap(),
    @SerializedName("shipping_policy_text") val shippingPolicy: String? = null,
    @SerializedName("refund_policy_text") val returnPolicy: String? = null,
    @SerializedName("exchange_policy_text") val exchangePolicy: String? = null,
    @SerializedName("warranty_policy_text") val warrantyPolicy: String? = null,
    @SerializedName("customer_service_policy_text") val supportContact: String? = null,
    @SerializedName("accepts_cash_on_delivery") val acceptsCashOnDelivery: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class ShopInventoryHistoryDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("variant_id") val variantId: String? = null,
    @SerializedName("previous_quantity") val previousQuantity: Int = 0,
    @SerializedName("new_quantity") val newQuantity: Int = 0,
    @SerializedName("quantity_delta") val quantityDelta: Int = 0,
    val reason: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopSellerCouponDto(
    val id: String = "",
    val code: String = "",
    val description: String? = null,
    @SerializedName("discount_type") val discountType: String = "percent",
    @SerializedName("discount_value") val discountValue: Int = 0,
    @SerializedName("minimum_order_minor") val minimumOrderMinor: Int = 0,
    @SerializedName("usage_limit") val usageLimit: Int? = null,
    @SerializedName("used_count") val usedCount: Int = 0,
    val status: String = "draft",
)

data class ShopSellerCampaignDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("campaign_type") val campaignType: String = "campaign",
    @SerializedName("discount_percent") val discountPercent: Int? = null,
    val status: String = "draft",
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("start_at") val startsAt: String? = null,
    @SerializedName("end_at") val endsAt: String? = null,
)

data class ShopSellerAnalyticsDto(
    val revenueMinor: Long = 0,
    val orderCount: Int = 0,
    val deliveredOrderCount: Int = 0,
    val productsSold: Int = 0,
    val refundRate: Double = 0.0,
    val averageOrderValueMinor: Long = 0,
    val topProducts: List<ShopSellerTopProductDto> = emptyList(),
    val revenueTrend: List<ShopSellerRevenuePointDto> = emptyList(),
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    val visitors: Int? = null,
    val conversionRate: Double? = null,
)

data class ShopSellerTopProductDto(val title: String = "Product", val quantity: Int = 0, val revenueMinor: Long = 0)
data class ShopSellerRevenuePointDto(val date: String = "", val revenueMinor: Long = 0)
data class ShopSellerCustomerOrderDto(val orderNumber: String = "", val status: String = "", val totalMinor: Long = 0, val createdAt: String? = null)

data class ShopSellerBusinessCustomerDto(
    val displayName: String = "Customer",
    val orderCount: Int = 0,
    val lifetimeSpendMinor: Long = 0,
    val latestOrderAt: String? = null,
    val latestOrderGroupId: String = "",
    val note: String = "",
    val recentOrders: List<ShopSellerCustomerOrderDto> = emptyList(),
)

data class ShopSellerRatingCountDto(val rating: Int = 0, val count: Int = 0)
data class ShopSellerReviewSummaryDto(val total: Int = 0, val replied: Int = 0, val ratings: List<ShopSellerRatingCountDto> = emptyList())

data class ShopSellerReviewDto(
    val id: String = "",
    val rating: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val status: String = "",
    @SerializedName("verified_purchase") val verifiedPurchase: Boolean = false,
    @SerializedName("seller_response") val sellerResponse: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopSellerPaymentDto(
    val id: String = "",
    val status: String = "",
    @SerializedName("amount_minor") val amountMinor: Long = 0,
    val currency: String = "ETB",
    @SerializedName("method_code") val methodCode: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopSellerFinanceDto(
    val payoutsAvailable: Boolean = false,
    val capturedSalesMinor: Long = 0,
    val pendingMinor: Long = 0,
    val withdrawableMinor: Long? = null,
    val transactions: List<ShopSellerPaymentDto> = emptyList(),
)

data class ShopSellerSettingsDto(
    @SerializedName("store_id") val storeId: String = "",
    val notifications: Map<String, Any> = emptyMap(),
    val shipping: Map<String, Any> = emptyMap(),
    val returns: Map<String, Any> = emptyMap(),
    val exchanges: Map<String, Any> = emptyMap(),
    val language: String = "en",
    val theme: String = "system",
)

data class ShopSellerBusinessDto(
    val stores: List<ShopSellerStoreDto> = emptyList(),
    val selectedStore: ShopSellerStoreDto? = null,
    val products: List<ShopSellerProductDto> = emptyList(),
    val inventoryHistory: List<ShopInventoryHistoryDto> = emptyList(),
    val coupons: List<ShopSellerCouponDto> = emptyList(),
    val campaigns: List<ShopSellerCampaignDto> = emptyList(),
    val analytics: ShopSellerAnalyticsDto = ShopSellerAnalyticsDto(),
    val customers: List<ShopSellerBusinessCustomerDto> = emptyList(),
    val productReviews: List<ShopSellerReviewDto> = emptyList(),
    val storeReviews: List<ShopSellerReviewDto> = emptyList(),
    val reviewSummary: ShopSellerReviewSummaryDto = ShopSellerReviewSummaryDto(),
    val refunds: List<ShopRefundRequestDto> = emptyList(),
    val exchanges: List<ShopExchangeRequestDto> = emptyList(),
    val finance: ShopSellerFinanceDto = ShopSellerFinanceDto(),
    val settings: ShopSellerSettingsDto = ShopSellerSettingsDto(),
)

data class ShopSellerInventoryChange(val inventoryId: String, val quantityAvailable: Int, val lowStockThreshold: Int)
data class ShopSellerInventoryUpdateRequest(val storeId: String, val requestId: String, val reason: String, val items: List<ShopSellerInventoryChange>)
data class ShopSellerReviewReplyRequest(val storeId: String, val reviewId: String, val reviewType: String, val reply: String)
data class ShopSellerCustomerNoteRequest(val storeId: String, val orderGroupId: String, val note: String)

data class ShopEnvelope<T>(
    val ok: Boolean = false,
    val error: String? = null,
    val serverTime: String? = null,
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
    val conversations: List<ShopConversationDto>? = null,
    val conversation: ShopConversationDto? = null,
    val messages: List<ShopMessageDto>? = null,
    val message: ShopMessageDto? = null,
    val issueOptions: ShopIssueOptionsDto? = null,
    val issues: List<ShopOrderIssueDto>? = null,
    val issue: ShopOrderIssueDto? = null,
    val postDeliveryActions: ShopPostDeliveryActionsDto? = null,
    val reviewOptions: ShopReviewOptionsDto? = null,
    val productReview: ShopReviewDto? = null,
    val storeReview: ShopReviewDto? = null,
    val reviews: ShopOrderReviewsDto? = null,
    val refundRequest: ShopRefundRequestDto? = null,
    val refundRequests: List<ShopRefundRequestDto>? = null,
    val exchangeRequest: ShopExchangeRequestDto? = null,
    val exchangeRequests: List<ShopExchangeRequestDto>? = null,
    val buyAgain: ShopBuyAgainResultDto? = null,
    val receipt: ShopReceiptDto? = null,
    val sellerOrderGroups: List<ShopOrderGroupDto>? = null,
    val sellerOrderStoreId: String? = null,
    val sellerRequests: ShopSellerRequestsDto? = null,
    val sellerOrderGroup: ShopOrderGroupDto? = null,
    val sellerProducts: List<ShopSellerProductDto>? = null,
    val sellerProductStoreId: String? = null,
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
    val total: Int? = null,
    val productDetail: ShopAdminProductDetailDto? = null,
    val reviewQueue: ShopPublicContentQueueDto? = null,
    val reviewPreview: ShopPublicContentPreviewDto? = null,
    val reviewedRevision: Long? = null,
    val adminOrders: List<ShopAdminOrderDto>? = null,
    val orderDetail: ShopAdminOrderDetailDto? = null,
    val auditLog: List<ShopAdminAuditLogDto>? = null,
    val adminUsers: List<ShopAdminUserDto>? = null,
    val stores: List<ShopAdminStoreDto>? = null,
    val storeDetail: ShopAdminStoreDetailDto? = null,
    val adminReviews: List<ShopAdminReviewModerationDto>? = null,
    val adminReview: ShopAdminReviewModerationDto? = null,
    val sellerBusiness: ShopSellerBusinessDto? = null,
)

data class ShopConversationStoreDto(
    val id: String = "",
    val name: String = "",
    @SerializedName("logo_url") val logoUrl: String? = null,
)

data class ShopConversationCustomerDto(
    val id: String = "",
    @SerializedName("display_name") val displayName: String? = null,
)

data class ShopConversationParticipantStateDto(
    @SerializedName("last_read_at") val lastReadAt: String? = null,
    @SerializedName("archived_at") val archivedAt: String? = null,
    @SerializedName("muted_until") val mutedUntil: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("typing_expires_at") val typingExpiresAt: String? = null,
)

data class ShopProductContextOptionDto(
    val name: String = "",
    val value: String = "",
)

data class ShopProductContextDto(
    @SerializedName("product_id") val productId: String = "",
    val title: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("price_minor") val priceMinor: Int? = null,
    val currency: String? = null,
    @SerializedName("store_id") val storeId: String? = null,
    @SerializedName("store_name") val storeName: String? = null,
    @SerializedName("variant_id") val variantId: String? = null,
    @SerializedName("variant_title") val variantTitle: String? = null,
    @SerializedName("selected_options") val selectedOptions: List<ShopProductContextOptionDto> = emptyList(),
    @SerializedName("selling_unit_code") val sellingUnitCode: String? = null,
    @SerializedName("product_status") val productStatus: String = "unavailable",
    val availability: String = "unavailable",
    @SerializedName("can_open") val canOpen: Boolean = false,
)

data class ShopConversationDto(
    val id: String = "",
    @SerializedName("store_id") val storeId: String = "",
    @SerializedName("customer_profile_id") val customerProfileId: String = "",
    @SerializedName("context_type") val contextType: String = "general",
    @SerializedName("product_id") val productId: String? = null,
    @SerializedName("order_group_id") val orderGroupId: String? = null,
    val status: String = "active",
    @SerializedName("last_message_preview") val lastMessagePreview: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val store: ShopConversationStoreDto? = null,
    val customer: ShopConversationCustomerDto? = null,
    @SerializedName("participant_state") val participantState: ShopConversationParticipantStateDto? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("peer_typing") val peerTyping: Boolean = false,
    @SerializedName("product_context") val productContext: ShopProductContextDto? = null,
)

data class ShopMessageDto(
    val id: String = "",
    @SerializedName("conversation_id") val conversationId: String = "",
    @SerializedName("sender_profile_id") val senderProfileId: String = "",
    @SerializedName("message_type") val messageType: String = "text",
    val body: String? = null,
    @SerializedName("attachment_url") val attachmentUrl: String? = null,
    @SerializedName("attachment_name") val attachmentName: String? = null,
    @SerializedName("attachment_mime_type") val attachmentMimeType: String? = null,
    @SerializedName("attachment_size_bytes") val attachmentSizeBytes: Long? = null,
    @SerializedName("client_message_id") val clientMessageId: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopCartItemDto(
    val id: String = "",
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("variant_id") val variantId: String? = null,
    val quantity: Int = 0,
    @SerializedName("unit_price_minor") val unitPriceMinor: Int = 0,
    @SerializedName("shop_products") val product: ShopProductDto? = null,
    @SerializedName("shop_product_variants") val variant: ShopPublicVariantDto? = null,
    @SerializedName("unit_snapshot") val unitSnapshot: ShopUnitSnapshotDto? = null,
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
    @SerializedName("server_now") val serverNow: String? = null,
    @SerializedName("shop_order_groups") val groups: List<ShopOrderGroupDto>? = null,
    @SerializedName("shop_order_status_history") val history: List<ShopOrderStatusHistoryDto>? = null,
    val issues: List<ShopOrderIssueDto> = emptyList(),
    val postDeliveryActions: ShopPostDeliveryActionsDto? = null,
    val reviews: ShopOrderReviewsDto? = null,
    val refundRequests: List<ShopRefundRequestDto> = emptyList(),
    val exchangeRequests: List<ShopExchangeRequestDto> = emptyList(),
)

data class ShopSellerRequestsDto(
    val refundRequests: List<ShopRefundRequestDto> = emptyList(),
    val exchangeRequests: List<ShopExchangeRequestDto> = emptyList(),
)

data class ShopSellerApplicationDto(
    val id: String = "",
    val status: String = "not_started",
    @SerializedName("proposed_store_name") val proposedStoreName: String? = null,
    @SerializedName("legal_business_name") val legalBusinessName: String? = null,
    @SerializedName("business_type") val businessType: String? = null,
    @SerializedName("business_registration_number") val businessRegistrationNumber: String? = null,
    @SerializedName("tax_identifier") val taxIdentifier: String? = null,
    @SerializedName("store_description") val storeDescription: String? = null,
    @SerializedName("expected_product_categories") val expectedProductCategories: List<String> = emptyList(),
    @SerializedName("short_tagline") val shortTagline: String? = null,
    @SerializedName("owner_full_name") val ownerFullName: String? = null,
    @SerializedName("contact_person_name") val contactPersonName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("support_phone") val supportPhone: String? = null,
    @SerializedName("support_email") val supportEmail: String? = null,
    val country: String? = null,
    @SerializedName("region_state") val regionState: String? = null,
    val city: String? = null,
    val district: String? = null,
    @SerializedName("address_line") val addressLine: String? = null,
    @SerializedName("postal_code") val postalCode: String? = null,
    @SerializedName("location_notes") val locationNotes: String? = null,
    @SerializedName("fulfillment_method") val fulfillmentMethod: String? = null,
    @SerializedName("service_area") val serviceArea: String? = null,
    @SerializedName("estimated_handling_time") val estimatedHandlingTime: String? = null,
    @SerializedName("return_policy") val returnPolicy: String? = null,
    @SerializedName("exchange_policy") val exchangePolicy: String? = null,
    @SerializedName("shipping_policy") val shippingPolicy: String? = null,
    @SerializedName("warranty_policy") val warrantyPolicy: String? = null,
    @SerializedName("customer_service_policy") val customerServicePolicy: String? = null,
    @SerializedName("terms_accepted") val termsAccepted: Boolean = false,
    @SerializedName("privacy_acknowledged") val privacyAcknowledged: Boolean = false,
    @SerializedName("seller_policy_accepted") val sellerPolicyAccepted: Boolean = false,
    @SerializedName("declaration_of_accuracy") val declarationOfAccuracy: Boolean = false,
    @SerializedName("current_step") val currentStep: Int = 1,
    @SerializedName("draft_saved_at") val draftSavedAt: String? = null,
    @SerializedName("information_request_message") val informationRequestMessage: String? = null,
    @SerializedName("applicant_response") val applicantResponse: String? = null,
    val documents: List<ShopSellerApplicationDocumentDto> = emptyList(),
    val events: List<ShopSellerApplicationEventDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
)

data class ShopSellerApplicationDocumentDto(
    val id: String = "",
    @SerializedName("document_type") val documentType: String = "",
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("byte_size") val byteSize: Int = 0,
    val status: String = "",
    @SerializedName("uploaded_at") val uploadedAt: String? = null,
)

data class ShopSellerApplicationEventDto(
    val id: String = "",
    @SerializedName("actor_role") val actorRole: String? = null,
    @SerializedName("event_code") val eventCode: String? = null,
    @SerializedName("previous_status") val previousStatus: String? = null,
    @SerializedName("next_status") val nextStatus: String? = null,
    @SerializedName("public_message") val publicMessage: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
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
    @SerializedName("legal_business_name") val legalBusinessName: String? = null,
    @SerializedName("business_registration_number") val businessRegistrationNumber: String? = null,
    @SerializedName("tax_identifier") val taxIdentifier: String? = null,
    @SerializedName("store_description") val storeDescription: String? = null,
    @SerializedName("short_tagline") val shortTagline: String? = null,
    @SerializedName("owner_full_name") val ownerFullName: String? = null,
    @SerializedName("contact_person_name") val contactPersonName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("support_phone") val supportPhone: String? = null,
    @SerializedName("support_email") val supportEmail: String? = null,
    val country: String? = null,
    @SerializedName("region_state") val regionState: String? = null,
    val city: String? = null,
    val district: String? = null,
    @SerializedName("address_line") val addressLine: String? = null,
    @SerializedName("location_notes") val locationNotes: String? = null,
    @SerializedName("fulfillment_method") val fulfillmentMethod: String? = null,
    @SerializedName("service_area") val serviceArea: String? = null,
    @SerializedName("estimated_handling_time") val estimatedHandlingTime: String? = null,
    @SerializedName("return_policy") val returnPolicy: String? = null,
    @SerializedName("exchange_policy") val exchangePolicy: String? = null,
    @SerializedName("shipping_policy") val shippingPolicy: String? = null,
    @SerializedName("warranty_policy") val warrantyPolicy: String? = null,
    @SerializedName("customer_service_policy") val customerServicePolicy: String? = null,
    @SerializedName("terms_accepted") val termsAccepted: Boolean = false,
    @SerializedName("privacy_acknowledged") val privacyAcknowledged: Boolean = false,
    @SerializedName("seller_policy_accepted") val sellerPolicyAccepted: Boolean = false,
    @SerializedName("declaration_of_accuracy") val declarationOfAccuracy: Boolean = false,
    val documents: List<ShopSellerApplicationDocumentDto> = emptyList(),
    val events: List<ShopSellerApplicationEventDto> = emptyList(),
)

data class ShopAdminRequestUpdateRequest(
    val requestId: String,
    val action: String,
    val reason: String = "",
    val buyerVisibleMessage: String = reason,
    val privateNote: String = "",
    val mutationId: String,
)

data class ShopSellerRequestResponseRequest(
    val requestId: String,
    val message: String,
    val idempotencyKey: String,
    val expectedVersion: Int,
    val storeId: String? = null,
)

data class ShopAdminReviewModerationRequest(
    val reviewId: String,
    val reviewType: String,
    val action: String,
    val reason: String = "",
    val requestId: String,
)

data class ShopAdminReviewModerationDto(
    val id: String = "",
    @SerializedName("review_type") val reviewType: String = "product",
    val rating: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val status: String? = null,
    @SerializedName("verified_purchase") val verifiedPurchase: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("shop_products") val product: Map<String, Any>? = null,
    @SerializedName("shop_stores") val store: Map<String, Any>? = null,
    val buyer: Map<String, Any>? = null,
    val events: List<Map<String, Any>> = emptyList(),
    val moderation: List<Map<String, Any>> = emptyList(),
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
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("stock_status")
    val stockStatus: String? = null,
    val currency: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("seller_name")
    val sellerName: String? = null,
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
)

data class ShopAdminProductQuery(
    val search: String = "",
    val status: String = "all",
    val limit: Int = 30,
    val offset: Int = 0,
    val category: String = "",
    val storeId: String = "",
    val stock: String = "all",
    val sort: String = "newest",
) {
    fun normalized(): ShopAdminProductQuery = copy(
        search = search.trim().take(120),
        status = status.takeIf { it in setOf("all", "pending", "approved", "rejected", "changes_requested", "archived") } ?: "all",
        limit = limit.coerceIn(1, 100),
        offset = offset.coerceAtLeast(0),
        category = category.trim().take(120),
        storeId = storeId.trim().take(36),
        stock = stock.takeIf { it in setOf("all", "in_stock", "low_stock", "out_of_stock") } ?: "all",
        sort = sort.takeIf { it in setOf("newest", "oldest", "updated") } ?: "newest",
    )
}

data class ShopAdminProductPage(
    val products: List<ShopAdminProductDto> = emptyList(),
    val total: Int? = null,
    val limit: Int = 30,
    val offset: Int = 0,
) {
    val hasNext: Boolean get() = total?.let { offset + products.size < it } ?: (products.size == limit)
}

object ShopAdminAllProductsFlags {
    // Enable only after the scoped products function bundle is deployed and authorization-tested.
    const val advancedQueryEnabled = false
}

object ShopPublicContentReviewFlags {
    // Enabled in QA builds once staged review RPCs and authorized Admin transport are deployed and tested.
    val enabled: Boolean get() = BuildConfig.QA_BUILD
    const val pageSize = 20
}

data class ShopPublicContentItemDto(
    val entityKind: String = "",
    val entityId: String = "",
    val revision: Long = 0,
    val targetId: String = "",
    val reviewStatus: String = "",
    val expectedRevision: com.google.gson.JsonElement? = null,
)

data class ShopPublicContentQueueDto(
    val total: Int = 0,
    val items: List<ShopPublicContentItemDto> = emptyList(),
)

data class ShopPublicContentPreviewDto(
    val entityKind: String = "",
    val entityId: String = "",
    val revision: Long = 0,
    val targetId: String = "",
    val content: Map<String, Any?> = emptyMap(),
    val reviewStatus: String = "",
    val expectedRevision: com.google.gson.JsonElement? = null,
    val permittedActions: List<String> = emptyList(),
)

data class ShopPublicContentDecisionRequest(
    val kind: String,
    val id: String,
    val expectedRevision: Long,
    val decision: String,
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
    val specificationValues: List<ShopSellerProductSpecificationValueDto> = emptyList(),
    val productOptions: List<ShopSellerProductOptionDto> = emptyList(),
    val variants: List<ShopSellerProductVariantDto> = emptyList(),
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

data class ShopAdminStoreDto(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    @SerializedName("approval_status") val approvalStatus: String? = null,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("owner_email") val ownerEmail: String? = null,
    @SerializedName("rating_average") val ratingAverage: Double? = null,
    @SerializedName("rating_count") val ratingCount: Int? = null,
    @SerializedName("product_count") val productCount: Int = 0,
    @SerializedName("completed_order_count") val completedOrderCount: Int = 0,
    @SerializedName("open_issue_count") val openIssueCount: Int = 0,
    @SerializedName("warning_count") val warningCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ShopAdminStoreDetailDto(
    val store: ShopAdminStoreDto? = null,
    val owner: ShopProfileDto? = null,
    val products: List<ShopSellerProductDto> = emptyList(),
    val warnings: List<Map<String, Any>> = emptyList(),
    val violations: List<Map<String, Any>> = emptyList(),
    val statusEvents: List<Map<String, Any>> = emptyList(),
    val refundRequests: List<ShopRefundRequestDto> = emptyList(),
    val exchangeRequests: List<ShopExchangeRequestDto> = emptyList(),
)

data class ShopAdminStoreStatusRequest(
    val storeId: String,
    val action: String,
    val reasonCode: String = "policy_violation",
    val sellerVisibleMessage: String = "",
    val privateNote: String = "",
    val requestId: String,
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
    val expectedSubmittedAt: String? = null,
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
    val ownerUid: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val cart: ShopCartDto? = null,
    val cartAvailableQuantities: Map<String, Int> = emptyMap(),
    val favoritesLoaded: Boolean = false,
    val favorites: List<ShopFavoriteDto> = emptyList(),
    val recentlyViewed: List<ShopRecentlyViewedDto> = emptyList(),
    val addresses: List<ShopAddressDto> = emptyList(),
    val ordersLoaded: Boolean = false,
    val orders: List<ShopOrderDto> = emptyList(),
    val buyerReviewsLoaded: Boolean = false,
    val buyerReviews: ShopOrderReviewsDto? = null,
    val checkout: ShopCheckoutDto? = null,
    val checkoutOrder: ShopOrderDto? = null,
    val selectedOrder: ShopOrderDto? = null,
    val issueOptions: ShopIssueOptionsDto? = null,
    val selectedOrderIssues: List<ShopOrderIssueDto> = emptyList(),
    val selectedOrderActions: ShopPostDeliveryActionsDto? = null,
    val selectedOrderReviews: ShopOrderReviewsDto? = null,
    val selectedRefundRequests: List<ShopRefundRequestDto> = emptyList(),
    val selectedExchangeRequests: List<ShopExchangeRequestDto> = emptyList(),
    val selectedReceipt: ShopReceiptDto? = null,
    val lastBuyAgainResult: ShopBuyAgainResultDto? = null,
    val notifications: List<ShopNotificationDto> = emptyList(),
    val notificationUnreadCount: Int = 0,
    val sellerNotifications: List<ShopNotificationDto> = emptyList(),
    val sellerNotificationUnreadCount: Int = 0,
    val sellerNotificationStoreId: String? = null,
    val sellerConversations: List<ShopConversationDto> = emptyList(),
    val selectedConversation: ShopConversationDto? = null,
    val conversationMessages: List<ShopMessageDto> = emptyList(),
    val sellerMessageUnreadCount: Int = 0,
    val sellerOrderGroups: List<ShopOrderGroupDto> = emptyList(),
    val sellerOrderStoreId: String? = null,
    val sellerOrdersLoaded: Boolean = false,
    val sellerRequests: ShopSellerRequestsDto? = null,
    val sellerRequestsStoreId: String? = null,
    val sellerProducts: List<ShopSellerProductDto> = emptyList(),
    val sellerProductsStoreId: String? = null,
    val sellerProductsLoaded: Boolean = false,
    val selectedSellerProduct: ShopSellerProductDto? = null,
    val sellerMediaUpload: ShopSellerMediaUploadState? = null,
    val sellerCategoryAttributes: Map<String, List<ShopCategoryAttributeDto>> = emptyMap(),
    val sellerCategoryLoadingIds: Set<String> = emptySet(),
    val sellerCategoryErrors: Map<String, String> = emptyMap(),
    val deliveryCode: ShopDeliveryCodeDto? = null,
    val sellerStatus: String = "not_started",
    val sellerApplication: ShopSellerApplicationDto? = null,
    val sellerBusiness: ShopSellerBusinessDto? = null,
    val adminLoading: Boolean = false,
    val adminError: String? = null,
    val adminSession: ShopAdminSessionDto? = null,
    val adminSelectedTab: String = "Sellers",
    val allAdminProductsQuery: ShopAdminProductQuery = ShopAdminProductQuery(),
    val adminDashboard: ShopAdminDashboardDto? = null,
    val adminSellerApplications: List<ShopAdminSellerApplicationDto> = emptyList(),
    val adminProducts: List<ShopAdminProductDto> = emptyList(),
    val allAdminProducts: List<ShopAdminProductDto> = emptyList(),
    val allAdminProductsLoading: Boolean = false,
    val allAdminProductsError: String? = null,
    val allAdminProductsOffset: Int = 0,
    val allAdminProductsHasNext: Boolean = false,
    val selectedAdminProduct: ShopAdminProductDetailDto? = null,
    val publicContentQueue: ShopPublicContentQueueDto? = null,
    val publicContentOffset: Int = 0,
    val publicContentLoading: Boolean = false,
    val publicContentReviewing: Boolean = false,
    val publicContentError: String? = null,
    val publicContentPreview: ShopPublicContentPreviewDto? = null,
    val adminOrders: List<ShopAdminOrderDto> = emptyList(),
    val selectedAdminOrder: ShopAdminOrderDetailDto? = null,
    val adminAuditLog: List<ShopAdminAuditLogDto> = emptyList(),
    val adminUsers: List<ShopAdminUserDto> = emptyList(),
    val adminStores: List<ShopAdminStoreDto> = emptyList(),
    val selectedAdminStore: ShopAdminStoreDetailDto? = null,
    val adminRefundRequests: List<ShopRefundRequestDto> = emptyList(),
    val adminExchangeRequests: List<ShopExchangeRequestDto> = emptyList(),
    val adminReviews: List<ShopAdminReviewModerationDto> = emptyList(),
    val busyIds: Set<String> = emptySet(),
)

data class ShopSellerMediaUploadState(
    val productId: String,
    val total: Int,
    val completed: Int = 0,
    val failed: Int = 0,
    val uploading: Boolean = true,
    val message: String? = null,
)

sealed class ShopIdentityState {
    data object SignedOut : ShopIdentityState()
    data object Syncing : ShopIdentityState()
    data class Ready(val profile: ShopProfileDto) : ShopIdentityState()
    data class Unavailable(val message: String) : ShopIdentityState()
}
