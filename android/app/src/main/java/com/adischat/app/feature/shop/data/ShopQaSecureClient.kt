package com.adischat.app.feature.shop.data

import com.adischat.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface ShopQaSecureApi {
    @POST("functions/v1/adishop-qa-rpc-transport")
    suspend fun call(
        @Header("Authorization") authorization: String,
        @Body body: @JvmSuppressWildcards Map<String, Any?>,
    ): @JvmSuppressWildcards Map<String, Any?>
}

internal object ShopQaSecureClient {
    val api: ShopQaSecureApi? by lazy {
        if (!BuildConfig.QA_BUILD || !ShopQaMutationGuard.stagingUrlValid(BuildConfig.ADISHOP_SUPABASE_URL) ||
            BuildConfig.ADISHOP_SUPABASE_PUBLISHABLE_KEY.isBlank()) return@lazy null
        val key = BuildConfig.ADISHOP_SUPABASE_PUBLISHABLE_KEY
        val client = OkHttpClient.Builder()
            .addInterceptor(ShopQaMutationGuard.interceptor())
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("apikey", key).build())
            }).build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.ADISHOP_SUPABASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ShopQaSecureApi::class.java)
    }
}
