package com.adischat.app.feature.shop.data

import com.adischat.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ShopSupabaseClient {
    private val publishableKey: String = BuildConfig.ADISHOP_SUPABASE_PUBLISHABLE_KEY
    val isConfigured: Boolean = BuildConfig.ADISHOP_SUPABASE_URL.isNotBlank() && publishableKey.isNotBlank()

    val api: ShopSupabaseApi? by lazy {
        if (!isConfigured) return@lazy null
        val auth = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer $publishableKey")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.ADISHOP_SUPABASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ShopSupabaseApi::class.java)
    }
}
