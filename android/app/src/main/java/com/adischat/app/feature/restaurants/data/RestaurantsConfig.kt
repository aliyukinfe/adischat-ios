package com.adischat.app.feature.restaurants.data

import com.adischat.app.BuildConfig
import com.adischat.app.FeatureFlags
import java.net.URI

/**
 * Isolated environment & flag configuration for AdisChat Restaurants.
 * Keeps Restaurants completely decoupled from AdisGebeya (`ADISHOP_*`) credentials and endpoints.
 */
data class RestaurantsEnvironment(
    val supabaseUrl: String = BuildConfig.RESTAURANTS_SUPABASE_URL,
    val supabasePublishableKey: String = BuildConfig.RESTAURANTS_SUPABASE_PUBLISHABLE_KEY,
    val identityBridgeUrl: String = BuildConfig.RESTAURANTS_IDENTITY_BRIDGE_URL,
    val isIdentityBridgeEnabled: Boolean = BuildConfig.RESTAURANTS_IDENTITY_BRIDGE_ENABLED,
    val isDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
    val isRestaurantCenterEnabled: Boolean = FeatureFlags.RESTAURANT_CENTER_ENABLED,
    val isAdminEnabled: Boolean = FeatureFlags.RESTAURANT_ADMIN_ENABLED,
    val isQaBuild: Boolean = BuildConfig.QA_BUILD,
    val isOfflinePreview: Boolean = BuildConfig.OFFLINE_QA_PREVIEW,
    val areQaOrderMutationsEnabled: Boolean = BuildConfig.RESTAURANTS_QA_ORDER_MUTATIONS_ENABLED,
    val restaurantCenterWebUrl: String = "https://adischat-restaurants-web.vercel.app",
) {
    fun normalizedSupabaseBaseUrl(): String = ensureTrailingSlash(supabaseUrl)

    fun normalizedCenterWebUrl(): String = ensureTrailingSlash(restaurantCenterWebUrl)

    fun handoffAuthUrl(): String = "${normalizedCenterWebUrl()}api/auth/handoff"

    fun functionsBaseUrl(): String {
        val base = normalizedSupabaseBaseUrl()
        return if (base.isEmpty()) "" else "${base}functions/v1/"
    }

    fun restBaseUrl(): String {
        val base = normalizedSupabaseBaseUrl()
        return if (base.isEmpty()) "" else "${base}rest/v1/"
    }

    fun authBaseUrl(): String {
        val base = normalizedSupabaseBaseUrl()
        return if (base.isEmpty()) "" else "${base}auth/v1/"
    }

    fun normalizedIdentityBridgeBaseUrl(): String = ensureTrailingSlash(identityBridgeUrl)

    fun isBackendConfigured(): Boolean =
        !isOfflinePreview &&
            isValidHttpsUrl(supabaseUrl) &&
            supabasePublishableKey.isNotBlank() &&
            !containsForbiddenSecret(supabasePublishableKey) &&
            (!isQaBuild || RestaurantsQaIsolationValidator.isAllowedQaEndpoint(supabaseUrl))

    fun isIdentityBridgeConfigured(): Boolean =
        !isOfflinePreview &&
            isIdentityBridgeEnabled &&
            isValidHttpsUrl(identityBridgeUrl) &&
            (!isQaBuild || RestaurantsQaIsolationValidator.isAllowedQaEndpoint(identityBridgeUrl))

    fun isStagingConnected(): Boolean =
        !isOfflinePreview && isBackendConfigured() && isIdentityBridgeConfigured()

    fun stagingStatusMessage(actionLabel: String? = null): String = when {
        isOfflinePreview -> if (actionLabel.isNullOrBlank()) {
            "$OFFLINE_QA_PREVIEW_LABEL • ${STAGING_NOT_CONNECTED_MESSAGE}"
        } else {
            "$OFFLINE_QA_PREVIEW_LABEL • $actionLabel disabled (no backend)"
        }
        isStagingConnected() -> "Connected to isolated QA backend"
        actionLabel.isNullOrBlank() -> STAGING_NOT_CONNECTED_MESSAGE
        else -> "$STAGING_NOT_CONNECTED_MESSAGE ($actionLabel unavailable offline)"
    }

    fun areOrderMutationsBlockedInQa(): Boolean =
        isOfflinePreview || (isQaBuild && !areQaOrderMutationsEnabled)

    companion object {
        const val OFFLINE_QA_PREVIEW_LABEL = "OFFLINE QA PREVIEW"
        const val STAGING_NOT_CONNECTED_MESSAGE = "Staging backend not connected"
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val READ_TIMEOUT_SECONDS = 20L
        const val WRITE_TIMEOUT_SECONDS = 20L
        const val UPLOAD_TIMEOUT_SECONDS = 60L

        private fun ensureTrailingSlash(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }

        internal fun isValidHttpsUrl(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return false
            return try {
                val uri = URI(trimmed)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            } catch (_: Exception) {
                false
            }
        }

        internal fun containsForbiddenSecret(rawKey: String): Boolean {
            val lower = rawKey.trim().lowercase()
            return lower.contains("service_role") ||
                lower.contains("private_key") ||
                lower.contains("begin private key")
        }
    }
}

/**
 * Runtime fail-closed QA isolation validator enforcing staging-only hosts and zero secret keys.
 */
object RestaurantsQaIsolationValidator {
    const val EXPECTED_STAGING_SUPABASE_HOST = "gnhemfikofnzpemgnqdv.supabase.co"
    const val EXPECTED_STAGING_WORKER_HOST = "adischat-api-staging.adischat.workers.dev"
    const val EXPECTED_QA_FIREBASE_PROJECT_ID = "adischat-qa"

    private val FORBIDDEN_PROD_HOSTS = setOf(
        "mcewjfnkrtiiolzibsuh.supabase.co",
        "opkqyvttbyezjbqwkvho.supabase.co",
        "npmniymdxabjvagierwq.supabase.co",
        "zhqtutrjxhazejrwhzrv.supabase.co",
        "dsehufuzcojsuxcggvct.supabase.co",
        "adischat-api.adischat.workers.dev",
        "adischat-wallet-api.adischat.workers.dev",
    )

    fun isStagingSupabaseUrl(rawUrl: String): Boolean =
        matchesExpectedHttpsHost(rawUrl, EXPECTED_STAGING_SUPABASE_HOST)

    fun isStagingWorkerUrl(rawUrl: String): Boolean =
        matchesExpectedHttpsHost(rawUrl, EXPECTED_STAGING_WORKER_HOST)

    fun isExactStagingEnvironment(env: RestaurantsEnvironment): Boolean =
        env.isQaBuild &&
            !env.isOfflinePreview &&
            env.isStagingConnected() &&
            isStagingSupabaseUrl(env.supabaseUrl) &&
            isStagingWorkerUrl(env.identityBridgeUrl)

    internal fun isAllowedQaEndpoint(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            uri.scheme.equals("https", ignoreCase = true) &&
                host.isNotBlank() &&
                uri.port == -1 &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                !host.endsWith(".invalid") &&
                !host.contains("adischat-f4a77") &&
                host !in FORBIDDEN_PROD_HOSTS
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesExpectedHttpsHost(rawUrl: String, expectedHost: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            uri.scheme.equals("https", ignoreCase = true) &&
                host == expectedHost &&
                uri.port == -1 &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
                host !in FORBIDDEN_PROD_HOSTS
        } catch (_: Exception) {
            false
        }
    }
}

object RestaurantsConfig {
    @Volatile
    var currentEnvironment: RestaurantsEnvironment = RestaurantsEnvironment()
        private set

    fun isDineInEnabled(): Boolean = currentEnvironment.isDineInEnabled
    fun isRestaurantCenterEnabled(): Boolean = currentEnvironment.isRestaurantCenterEnabled
    fun isAdminEnabled(): Boolean = currentEnvironment.isAdminEnabled
    fun isIdentityBridgeEnabled(): Boolean = currentEnvironment.isIdentityBridgeEnabled
    fun isBackendConfigured(): Boolean = currentEnvironment.isBackendConfigured()
}
