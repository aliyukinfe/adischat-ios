package com.adischat.app.feature.shop.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

interface ShopSupabaseApi {
    @GET("rest/v1/shop_categories?select=id,parent_id,name,slug,code,description,icon,level,is_leaf,seller_selectable,buyer_visible,search_terms,display_order&is_active=eq.true&buyer_visible=eq.true&order=level.asc,display_order.asc,name.asc&limit=500")
    suspend fun qaCategories(): List<ShopCategoryDto>

    @GET("rest/v1/shop_qa_public_stores?select=id,name,description,city,created_at&limit=12")
    suspend fun qaStores(): List<ShopStoreDto>

    @POST("rest/v1/rpc/adishop_qa_catalog_page")
    suspend fun qaCatalogPage(@Body request: @JvmSuppressWildcards Map<String, Any?>): List<ShopProductDto>

    @POST("rest/v1/rpc/adishop_qa_product_detail")
    suspend fun qaProductDetail(@Body request: Map<String, String>): ShopProductDto?

    @POST("rest/v1/rpc/adishop_qa_create_checkout")
    suspend fun qaCreateCheckout(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_cart_action")
    suspend fun qaCartAction(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_buyer_orders")
    suspend fun qaBuyerOrders(@Body request: @JvmSuppressWildcards Map<String, Any?>): List<@JvmSuppressWildcards Map<String, Any?>>

    @POST("rest/v1/rpc/adishop_qa_buyer_order_detail")
    suspend fun qaBuyerOrderDetail(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_reveal_delivery_code")
    suspend fun qaRevealDeliveryCode(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_apply_coupon")
    suspend fun qaApplyCoupon(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_apply_voucher")
    suspend fun qaApplyVoucher(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_remove_discount")
    suspend fun qaRemoveDiscount(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @POST("rest/v1/rpc/adishop_qa_confirm_checkout")
    suspend fun qaConfirmCheckout(@Body request: @JvmSuppressWildcards Map<String, Any?>): @JvmSuppressWildcards Map<String, Any?>

    @GET("rest/v1/shop_banners?select=id,title,subtitle,image_url,action_url,is_active,display_order,start_at,end_at,is_seed&is_active=eq.true&order=display_order.asc&limit=5")
    suspend fun banners(): List<ShopBannerDto>

    @GET("rest/v1/shop_categories?select=id,parent_id,name,slug,code,description,image_url,icon,level,is_leaf,seller_selectable,buyer_visible,search_terms,display_order&is_active=eq.true&buyer_visible=eq.true&order=level.asc,display_order.asc,name.asc&limit=500")
    suspend fun categories(): List<ShopCategoryDto>

    @GET("rest/v1/shop_selling_units?select=code,name,symbol,quantity_scale,display_order&is_active=eq.true&order=display_order.asc&limit=100")
    suspend fun sellingUnits(): List<ShopSellingUnitDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,image_url,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,logo_url,cover_url,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null&order=created_at.desc&limit=100")
    suspend fun products(): List<ShopProductDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null&order=created_at.desc&limit=100")
    suspend fun reviewedProducts(): List<ShopProductDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,image_url,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,logo_url,cover_url,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null&limit=100")
    suspend fun searchProducts(
        @Query(value = "category_slug", encoded = true) categoryFilter: String? = null,
        @Query(value = "and", encoded = true) priceFilter: String? = null,
        @Query(value = "rating_average", encoded = true) ratingFilter: String? = null,
        @Query(value = "or", encoded = true) stockFilter: String? = null,
        @Query(value = "discount_percent", encoded = true) discountFilter: String? = null,
        @Query(value = "order", encoded = true) order: String = "created_at.desc",
    ): List<ShopProductDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null&limit=100")
    suspend fun reviewedSearchProducts(
        @Query(value = "category_slug", encoded = true) categoryFilter: String? = null,
        @Query(value = "and", encoded = true) priceFilter: String? = null,
        @Query(value = "rating_average", encoded = true) ratingFilter: String? = null,
        @Query(value = "or", encoded = true) stockFilter: String? = null,
        @Query(value = "discount_percent", encoded = true) discountFilter: String? = null,
        @Query(value = "order", encoded = true) order: String = "created_at.desc",
    ): List<ShopProductDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,image_url,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,logo_url,cover_url,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null")
    suspend fun searchCatalogPage(
        @Query(value = "or", encoded = true) textFilter: String? = null,
        @Query(value = "and", encoded = true) combinedFilter: String? = null,
        @Query("category_slug") categoryFilter: String? = null,
        @Query("order") order: String = "created_at.desc,id.desc",
        @Query("limit") limit: Int = 24,
        @Query("offset") offset: Int = 0,
    ): List<ShopProductDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,created_at,category_id,category_slug,short_description,selling_unit_code,minimum_order_quantity,price_minor,price_max_minor,has_variants,aggregate_available_quantity,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(id,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null")
    suspend fun reviewedSearchCatalogPage(
        @Query(value = "or", encoded = true) textFilter: String? = null,
        @Query(value = "and", encoded = true) combinedFilter: String? = null,
        @Query("category_slug") categoryFilter: String? = null,
        @Query("order") order: String = "created_at.desc,id.desc",
        @Query("limit") limit: Int = 24,
        @Query("offset") offset: Int = 0,
    ): List<ShopProductDto>

    @GET("rest/v1/shop_stores?select=id,name,slug,description,logo_url,cover_url,category,city,status,approval_status,average_store_rating,total_sales,created_at&status=eq.active&approval_status=eq.approved&limit=12")
    suspend fun stores(): List<ShopStoreDto>

    @GET("rest/v1/shop_stores?select=id,name,slug,description,category,city,status,approval_status,average_store_rating,total_sales,created_at&status=eq.active&approval_status=eq.approved&limit=12")
    suspend fun reviewedStores(): List<ShopStoreDto>

    @POST("rest/v1/rpc/adishop_product_catalog_payload")
    suspend fun productCatalog(@Body request: ShopProductCatalogRequest): ShopProductDto?

    @POST("rest/v1/rpc/adishop_category_attribute_schema")
    suspend fun categoryAttributes(@Body request: ShopCategoryAttributeRequest): List<ShopCategoryAttributeDto>
}
