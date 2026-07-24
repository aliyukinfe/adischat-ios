package com.adischat.app.feature.shop.data

import retrofit2.http.GET

interface ShopSupabaseApi {
    @GET("rest/v1/shop_banners?select=id,title,subtitle,image_url,action_url,is_active,display_order&is_active=eq.true&order=display_order.asc&limit=5")
    suspend fun banners(): List<ShopBannerDto>

    @GET("rest/v1/shop_categories?select=id,name,slug,image_url,icon&is_active=eq.true&order=display_order.asc&limit=12")
    suspend fun categories(): List<ShopCategoryDto>

    @GET("rest/v1/shop_products?select=id,title,slug,brand,category_slug,price_minor,compare_at_price_minor,discount_percent,rating_average,rating_count,completed_sales_count,stock_status,shop_product_images(image_url,is_primary,display_order,alt_text),shop_stores(id,name,slug,description,logo_url,cover_url,category,city,approval_status,status,average_store_rating,total_sales,created_at)&status=eq.active&moderation_status=eq.approved&deleted_at=is.null&order=created_at.desc&limit=20")
    suspend fun products(): List<ShopProductDto>

    @GET("rest/v1/shop_stores?select=id,name,slug,description,logo_url,cover_url,category,city,status,approval_status,average_store_rating,total_sales,created_at&status=eq.active&approval_status=eq.approved&limit=12")
    suspend fun stores(): List<ShopStoreDto>
}
