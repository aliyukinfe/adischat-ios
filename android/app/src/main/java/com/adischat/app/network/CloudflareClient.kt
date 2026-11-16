package com.adischat.app.network

import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import com.adischat.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class QaShopAssertionConfig(
    val endpoint: String,
    val shopUrl: String,
    val firebaseProject: String,
    val applicationId: String,
    val issuer: String,
    val audience: String,
)

internal fun shopAssertionBaseUrl(qa: Boolean, config: QaShopAssertionConfig?, production: String): String {
    if (!qa) return production
    require(config != null && config.shopUrl.trimEnd('/') == "https://opkqyvttbyezjbqwkvho.supabase.co" &&
        config.firebaseProject == "adischat-qa" && config.applicationId == "com.adischat.app.qa" &&
        config.issuer == "adischat-staging-opkqyvttbyezjbqwkvho" &&
        config.audience == "adishop-staging-edge") { "QA Shop identity configuration is unavailable" }
    val uri = runCatching { URI(config.endpoint) }.getOrNull()
    require(uri?.scheme == "https" && uri.host == "adischat-qa-shop-issuer.adischat.workers.dev" &&
        uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) { "QA Shop assertion endpoint is unavailable" }
    return config.endpoint.trimEnd('/') + "/"
}

object CloudflareClient {

    private val BASE_URL = BuildConfig.ADISCHAT_API_URL.trimEnd('/') + "/"
    private val WALLET_BASE_URL = BuildConfig.ADISCHAT_WALLET_API_URL.trimEnd('/') + "/"
    const val CONNECTION_ERROR_MESSAGE = "Connection lost. Check your internet connection."

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val protectedRoute = original.url.encodedPath.requiresFirebaseToken()

        // getIdToken(false) uses the cached token; when Firebase has not hydrated it yet,
        // force-refresh once so protected routes never reach the Worker without Bearer auth.
        val token = try {
        runBlocking {
        val user = FirebaseAuth.getInstance().currentUser
        val cached = user?.getIdToken(false)?.await()?.token.orEmpty()
        cached.ifBlank { user?.getIdToken(true)?.await()?.token.orEmpty() }
        }
        } catch (e: Throwable) {
        if (e.isRecoverableConnectivityFailure()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
        throw IOException("Please sign in again", e)
        }

        if (protectedRoute && token.isBlank()) {
        throw IOException("Please sign in again")
        }

        val req = original.newBuilder()
        .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
        .build()
        try {
        chain.proceed(req)
        } catch (e: IOException) {
        if (e.isRecoverableConnectivityFailure()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
        throw e
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        redactHeader("apikey")
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val apiErrorInterceptor = Interceptor { chain ->
        val response = try {
        chain.proceed(chain.request())
        } catch (e: IOException) {
        if (e.isRecoverableConnectivityFailure()) throw IOException(CONNECTION_ERROR_MESSAGE, e)
        throw e
        }
        if (response.isSuccessful) return@Interceptor response

        val rawBody = response.body?.string().orEmpty()
        response.close()
        val serverMessage = runCatching {
        JSONObject(rawBody).optString("error")
        .ifBlank { JSONObject(rawBody).optString("message") }
        }.getOrNull().orEmpty()
        val message = when {
        response.code == 429 && rawBody.contains("1027") -> "AdisChat server is temporarily busy. Please try again in a few minutes."
        response.code == 429 -> serverMessage.ifBlank { "Too many requests. Please wait a moment and try again." }
        else -> serverMessage.ifBlank {
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
        }
        throw IOException(message)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (BuildConfig.OFFLINE_QA_PREVIEW) {
                throw IOException("Network access is disabled in Offline QA Preview")
            }
            val path = chain.request().url.encodedPath
            if (!FeatureFlags.FEATURE_WALLET_ENABLED &&
                WalletReleaseGuard.blocks(chain.request().method, chain.request().url.pathSegments)) {
                throw IOException("Wallet features are not available in this release")
            }
            if (BuildConfig.QA_BUILD && !BuildConfig.QA_PAYMENTS_ENABLED &&
                (path.startsWith("/api/wallet/") || path.startsWith("/api/admin/wallets/"))) {
                throw IOException("Wallet and payment actions are disabled in QA")
            }
            chain.proceed(chain.request())
        }
        .addInterceptor(authInterceptor)
        .addInterceptor(apiErrorInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val walletClient = if (BuildConfig.QA_BUILD && !BuildConfig.QA_PAYMENTS_ENABLED) {
        okHttpClient.newBuilder().addInterceptor { throw IOException("Wallet and payment actions are disabled in QA") }.build()
    } else okHttpClient

    /**
     * Resilient JSON for message lists. Gson is strict: by default a single
     * malformed message (an unexpected field shape from the server) throws and the
     * WHOLE `List<Message>` fails to deserialize - which makes a private chat's
     * entire history render empty even though the data is fine (the desktop web
     * client tolerates it because JS parsing is lenient). This deserializer parses
     * each array element on its own and skips only the bad ones, so one odd record
     * can never blank an entire conversation.
     */
    private val messageListType = TypeToken.getParameterized(List::class.java, Message::class.java).type
    private val resilientGson: Gson = GsonBuilder()
        .registerTypeAdapter(messageListType, JsonDeserializer { json, _, context ->
        val out = ArrayList<Message>()
        if (json != null && json.isJsonArray) {
        for (element in json.asJsonArray) {
        try {
        context.deserialize<Message>(element, Message::class.java)?.let { out.add(it) }
        } catch (e: Exception) {
        android.util.Log.w("ChatLoad", "skipped unparseable message: ${e.message}")
        }
        }
        }
        out
        })
        .create()

    private val resilientConverter = GsonConverterFactory.create(resilientGson)

    val api: CloudflareApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(resilientConverter)
        .build()
        .create(CloudflareApi::class.java)

    val shopAssertionApi: CloudflareApi by lazy {
        if (!BuildConfig.QA_BUILD) api
        else Retrofit.Builder()
            .baseUrl(shopAssertionBaseUrl(true, QaShopAssertionConfig(
                BuildConfig.ADISHOP_IDENTITY_BRIDGE_URL,
                BuildConfig.ADISHOP_SUPABASE_URL,
                BuildConfig.ADISCHAT_FIREBASE_PROJECT_ID,
                BuildConfig.APPLICATION_ID,
                BuildConfig.ADISHOP_ASSERTION_ISSUER,
                BuildConfig.ADISHOP_ASSERTION_AUDIENCE,
            ), BASE_URL))
            .client(okHttpClient)
            .addConverterFactory(resilientConverter)
            .build()
            .create(CloudflareApi::class.java)
    }

    val walletApi: WalletApi = Retrofit.Builder()
        .baseUrl(WALLET_BASE_URL)
        .client(walletClient)
        .addConverterFactory(resilientConverter)
        .build()
        .create(WalletApi::class.java)

    val gamePotApi: GamePotApi = Retrofit.Builder()
        .baseUrl(WALLET_BASE_URL)      // Game Pot routes live on the wallet worker
        .client(walletClient)
        .addConverterFactory(resilientConverter)
        .build()
        .create(GamePotApi::class.java)


    private fun String.requiresFirebaseToken(): Boolean {
        val publicAuth = this == "/api/auth/lookup" ||
        this == "/api/auth/send-email-otp" ||
        this == "/api/auth/verify-email-otp"
        if (publicAuth) return false
        return startsWith("/api/") ||
        this == "/auth/desktop/qr/scan" ||
        this == "/auth/desktop/qr/approve" ||
        this == "/auth/desktop/qr/reject"
    }
    fun friendlyErrorMessage(error: Throwable?, fallback: String = "Something went wrong. Please try again."): String {
        if (error == null) return fallback
        if (error.isRecoverableConnectivityFailure()) return CONNECTION_ERROR_MESSAGE
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
        if ("1027" in lower || ("429" in lower && "too many" in lower)) {
        return "AdisChat server is temporarily busy. Please try again in a few minutes."
        }
        return if (technical) CONNECTION_ERROR_MESSAGE else raw
    }

}
