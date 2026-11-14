package com.adischat.app.feature.restaurants.data

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * Typed domain failure hierarchy for AdisChat Restaurants API & session operations.
 */
sealed class RestaurantsFailure(
    val code: String,
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {
    class FeatureDisabled(message: String) :
        RestaurantsFailure("feature_disabled", message)

    class NotConfigured(message: String) :
        RestaurantsFailure("not_configured", message)

    class Unauthenticated(code: String = "unauthenticated", message: String) :
        RestaurantsFailure(code, message)

    class Forbidden(code: String = "forbidden", message: String) :
        RestaurantsFailure(code, message)

    class Validation(code: String = "validation_error", message: String) :
        RestaurantsFailure(code, message)

    class Conflict(code: String = "conflict", message: String) :
        RestaurantsFailure(code, message)

    class NotFound(code: String = "not_found", message: String) :
        RestaurantsFailure(code, message)

    class RateLimited(message: String) :
        RestaurantsFailure("rate_limited", message)

    class Network(message: String, cause: Throwable? = null) :
        RestaurantsFailure("network_error", message, cause)

    class Server(code: String = "server_error", message: String, cause: Throwable? = null) :
        RestaurantsFailure(code, message, cause)

    class QaBlocked(message: String) :
        RestaurantsFailure("qa_blocked", message)
}

/**
 * Maps Phase R3B Edge Function `{ ok: false, error: { code, message } }` payloads
 * and transport exceptions into safe, redactable [RestaurantsFailure] instances.
 */
object RestaurantsErrorMapper {
    private val gson = Gson()
    private val sensitiveMarkers = listOf(
        "jwt",
        "token",
        "uuid",
        "policy",
        "postgres",
        "supabase",
        "service_role",
        "stack",
        "authorization",
        "apikey",
        "sql",
        "relation ",
    )

    fun mapThrowable(t: Throwable): RestaurantsFailure = when (t) {
        is RestaurantsFailure -> t
        is HttpException -> mapHttpException(t)
        is IOException -> {
            if (t.message?.contains("QA", ignoreCase = true) == true) {
                RestaurantsFailure.QaBlocked("Order mutations are disabled in QA builds.")
            } else {
                RestaurantsFailure.Network(
                    "Unable to reach AdisChat Restaurants right now. Check your connection and try again.",
                    t,
                )
            }
        }
        else -> RestaurantsFailure.Server("unexpected_error", "Something went wrong. Please try again.", t)
    }

    fun mapHttpException(exception: HttpException): RestaurantsFailure {
        val statusCode = exception.code()
        val rawBody = try {
            exception.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        return mapHttpStatusAndBody(statusCode, rawBody, exception)
    }

    fun mapHttpStatusAndBody(
        statusCode: Int,
        rawBody: String?,
        cause: Throwable? = null,
    ): RestaurantsFailure {
        val envelope = parseErrorEnvelope(rawBody)
        val backendCode = envelope?.error?.code?.trim().orEmpty()
        val backendMsg = envelope?.error?.message?.trim().orEmpty()

        val friendlyByCode = mapBackendErrorCode(backendCode, backendMsg)
        if (friendlyByCode != null) return friendlyByCode

        return when (statusCode) {
            401 -> RestaurantsFailure.Unauthenticated(
                backendCode.ifEmpty { "unauthorized" },
                sanitizeMessage(backendMsg, "Please sign in again to continue."),
            )
            403 -> RestaurantsFailure.Forbidden(
                backendCode.ifEmpty { "forbidden" },
                sanitizeMessage(backendMsg, "You do not have permission for this restaurant action."),
            )
            404 -> RestaurantsFailure.NotFound(
                backendCode.ifEmpty { "not_found" },
                sanitizeMessage(backendMsg, "Requested restaurant resource was not found."),
            )
            409, 422 -> RestaurantsFailure.Conflict(
                backendCode.ifEmpty { "conflict" },
                sanitizeMessage(backendMsg, "This action conflicts with the current restaurant or order state."),
            )
            429 -> RestaurantsFailure.RateLimited(
                sanitizeMessage(backendMsg, "Too many requests. Please wait a moment and try again."),
            )
            in 400..499 -> RestaurantsFailure.Validation(
                backendCode.ifEmpty { "bad_request" },
                sanitizeMessage(backendMsg, "Please check your restaurant details and try again."),
            )
            else -> RestaurantsFailure.Server(
                backendCode.ifEmpty { "http_$statusCode" },
                sanitizeMessage(backendMsg, "Restaurants service is temporarily unavailable."),
                cause,
            )
        }
    }

    internal fun parseErrorEnvelope(rawBody: String?): RestaurantsApiErrorEnvelope? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            gson.fromJson(rawBody, RestaurantsApiErrorEnvelope::class.java)
        } catch (_: Exception) {
            null
        }
    }

    internal fun sanitizeMessage(raw: String?, fallback: String): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        val lower = trimmed.lowercase()
        if (sensitiveMarkers.any { lower.contains(it) } || trimmed.length > 220) {
            return fallback
        }
        return trimmed
    }

    private fun mapBackendErrorCode(code: String, rawMessage: String): RestaurantsFailure? {
        if (code.isEmpty()) return null
        return when (code) {
            "missing_token", "invalid_token", "missing_assertion", "invalid_assertion", "assertion_replayed" ->
                RestaurantsFailure.Unauthenticated(code, "Your restaurant session expired. Please sign in again.")
            "account_inactive", "forbidden", "forbidden_not_platform_admin", "forbidden_owner_only",
            "forbidden_suspended_restaurant", "forbidden_not_branch_staff", "forbidden_staff_invite",
            "forbidden_staff_revoke", "self_approval_forbidden", "branch_mismatch" ->
                RestaurantsFailure.Forbidden(code, sanitizeMessage(rawMessage, "Access denied for this restaurant account."))
            "not_found", "document_not_found", "restaurant_not_found", "application_not_found" ->
                RestaurantsFailure.NotFound(code, sanitizeMessage(rawMessage, "Requested item was not found."))
            "session_code_required", "invalid_session_code", "missing_qr_token", "invalid_order_request",
            "invalid_sync_cursor", "notes_required", "rejection_reason_required" ->
                RestaurantsFailure.Validation(code, sanitizeMessage(rawMessage, "Please check your input and try again."))
            "dine_in_only", "pay_at_restaurant_only", "missing_idempotency_key", "missing_fields" ->
                RestaurantsFailure.Validation(code, sanitizeMessage(rawMessage, "Invalid dine-in order request."))
            "dining_session_not_activated" ->
                RestaurantsFailure.Conflict(code, "Ask your waiter to activate this table before ordering.")
            "dining_session_expired", "dining_session_not_open", "session_closed", "session_expired" ->
                RestaurantsFailure.Conflict(code, sanitizeMessage(rawMessage, "This table dining session has closed or expired."))
            "qr_revoked_or_invalid", "qr_revoked", "qr_expired", "invalid_qr",
            "restaurant_unavailable", "restaurant_not_approved", "restaurant_not_activated",
            "incomplete_restaurant_profile", "active_branch_required", "active_menu_item_required",
            "branch_or_table_unavailable", "branch_inactive", "restaurant_not_live",
            "invalid_transition", "item_unavailable" ->
                RestaurantsFailure.Conflict(code, sanitizeMessage(rawMessage, "This restaurant table or item is not currently available."))
            else -> null
        }
    }
}
