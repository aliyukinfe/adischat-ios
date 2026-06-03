package com.adischat.app.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object CloudflareClient {

    // ── Replace with your Worker URL after running `wrangler deploy` ──────────
    // Format: https://<worker-name>.<your-subdomain>.workers.dev/
    private const val BASE_URL = "https://adischat-api.adischat.workers.dev/"
    private const val WALLET_BASE_URL = "https://adischat-wallet-api.adischat.workers.dev/"
    const val CONNECTION_ERROR_MESSAGE = "Connection lost. Check your internet connection."

    private val authInterceptor = Interceptor { chain ->
        // getIdToken(false) uses the cached token; refreshes only when expired.
        // runBlocking is safe here because OkHttp runs interceptors off the main thread.
        val token = try {
            runBlocking {
                FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
            }
        } catch (e: Throwable) {
            if (e.isConnectionProblem()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
            throw e
        }
        val req = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        try {
            chain.proceed(req)
        } catch (e: IOException) {
            if (e.isConnectionProblem()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
            throw e
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val apiErrorInterceptor = Interceptor { chain ->
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            if (e.isConnectionProblem()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
            throw e
        }
        if (response.isSuccessful) return@Interceptor response

        val rawBody = response.body?.string().orEmpty()
        response.close()
        val serverMessage = runCatching {
            JSONObject(rawBody).optString("error")
                .ifBlank { JSONObject(rawBody).optString("message") }
        }.getOrNull().orEmpty()
        val message = serverMessage.ifBlank {
            when (response.code) {
                400 -> "Check the details and try again"
                401 -> "Please sign in again"
                403 -> "You do not have permission or your wallet is not ready"
                404 -> "This item was not found"
                409 -> "This action is already done"
                423 -> "Wallet is locked"
                else -> "Request failed. Please try again"
            }
        }
        throw IOException(message)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(apiErrorInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: CloudflareApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CloudflareApi::class.java)

    val walletApi: WalletApi = Retrofit.Builder()
        .baseUrl(WALLET_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WalletApi::class.java)

    val gamePotApi: GamePotApi = Retrofit.Builder()
        .baseUrl(WALLET_BASE_URL)          // Game Pot routes live on the wallet worker
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GamePotApi::class.java)

    fun friendlyErrorMessage(error: Throwable?, fallback: String = "Something went wrong. Please try again."): String {
        if (error == null) return fallback
        if (error.isConnectionProblem()) return CONNECTION_ERROR_MESSAGE
        val raw = error.message.orEmpty().trim()
        if (raw.isBlank()) return fallback
        val lower = raw.lowercase()
        val technical = listOf(
            "unable to resolve host",
            "failed to connect",
            "connection reset",
            "connection refused",
            "timeout",
            "timed out",
            "socket closed",
            "software caused connection abort",
            "network is unreachable",
            "no address associated",
            "java.net.",
            "retrofit2.",
            "okhttp",
            "adischat-api",
            "adischat-wallet-api",
        ).any { it in lower }
        return if (technical) CONNECTION_ERROR_MESSAGE else raw
    }

    private fun Throwable.isConnectionProblem(): Boolean =
        this is UnknownHostException ||
            this is SocketTimeoutException ||
            this is ConnectException ||
            this is SocketException ||
            this is SSLException ||
            cause?.isConnectionProblem() == true
}
