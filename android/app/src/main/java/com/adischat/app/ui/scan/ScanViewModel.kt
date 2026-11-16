package com.adischat.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.FeatureFlags
import com.adischat.app.data.model.ScanEntry
import com.adischat.app.data.preferences.AppPreferences
import com.adischat.app.feature.restaurants.presentation.RestaurantQrParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanResult {
    object None      : ScanResult()
    data class Found(val data: String, val type: String) : ScanResult()
    data class RestaurantQr(val token: String) : ScanResult()
    data class RestaurantCenterLogin(
        val challengeId: String,
        val challengeNonce: String,
        val raw: String,
    ) : ScanResult()
}

class ScanViewModel(
    private val prefs: AppPreferences,
    private val restaurantsDineInEnabled: Boolean = FeatureFlags.RESTAURANTS_DINE_IN_ENABLED,
) : ViewModel() {

    private val _result    = MutableStateFlow<ScanResult>(ScanResult.None)
    val result: StateFlow<ScanResult> = _result

    private val _torchOn   = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn

    private var scanning   = true

    fun onBarcodeDetected(raw: String) {
        if (!scanning) return
        scanning = false
        when (val resolved = resolveMainScanResult(raw, restaurantsDineInEnabled)) {
            is ScanResult.RestaurantQr -> {
                _result.value = resolved
            }
            is ScanResult.RestaurantCenterLogin -> {
                _result.value = resolved
            }
            is ScanResult.Found -> {
                _result.value = resolved
                viewModelScope.launch {
                    prefs.addScanEntry(
                        ScanEntry(data = resolved.data, type = resolved.type, timestamp = System.currentTimeMillis()),
                    )
                }
            }
            ScanResult.None -> Unit
        }
    }

    fun dismissResult()  { _result.value = ScanResult.None; scanning = true }

    fun toggleTorch()    { _torchOn.value = !_torchOn.value }
    fun turnOffTorch()   { _torchOn.value = false }

    fun clearHistory()   { viewModelScope.launch { prefs.clearScanHistory() } }

    private fun detectType(data: String): String = detectScanType(data)
}

internal fun resolveMainScanResult(
    raw: String,
    restaurantsDineInEnabled: Boolean,
): ScanResult {
    val restaurantToken = if (restaurantsDineInEnabled) {
        RestaurantQrParser.parseStrictRestaurantQrUrl(raw)
    } else {
        null
    }
    if (restaurantToken != null) {
        return ScanResult.RestaurantQr(restaurantToken)
    }

    val centerLogin = RestaurantCenterQrParser.parse(raw)
    if (centerLogin != null) {
        return ScanResult.RestaurantCenterLogin(
            challengeId = centerLogin.challengeId,
            challengeNonce = centerLogin.challengeNonce,
            raw = raw,
        )
    }

    return ScanResult.Found(raw, detectScanType(raw))
}

object RestaurantCenterQrParser {
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    data class ParsedLoginChallenge(
        val challengeId: String,
        val challengeNonce: String,
    )

    fun parse(raw: String): ParsedLoginChallenge? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        if (!trimmed.contains("adischat_restaurant_center_login")) return null

        return try {
            val element = com.google.gson.JsonParser.parseString(trimmed)
            if (!element.isJsonObject) return null
            val json = element.asJsonObject

            val version = when {
                json.has("version") && !json.get("version").isJsonNull -> json.get("version").asString
                json.has("v") && !json.get("v").isJsonNull -> json.get("v").asString
                else -> null
            }
            if (version != null && version != "1.0") return null

            val type = if (json.has("type") && !json.get("type").isJsonNull) json.get("type").asString else ""
            if (type != "adischat_restaurant_center_login") return null

            val challengeId = (if (json.has("challenge_id") && !json.get("challenge_id").isJsonNull) json.get("challenge_id").asString else "").trim()
            if (!UUID_REGEX.matches(challengeId)) return null

            val nonce = (if (json.has("challenge_nonce") && !json.get("challenge_nonce").isJsonNull) json.get("challenge_nonce").asString else "").trim()
            if (nonce.isEmpty() || nonce.length < 8) return null

            val ts = (if (json.has("timestamp") && !json.get("timestamp").isJsonNull) json.get("timestamp").asString else "").trim()
            if (ts.isNotEmpty()) {
                val parsedTime = try {
                    java.time.Instant.parse(ts).toEpochMilli()
                } catch (_: Exception) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        sdf.parse(ts)?.time
                    } catch (_: Exception) {
                        null
                    }
                }
                if (parsedTime != null) {
                    val now = System.currentTimeMillis()
                    // Rejects if timestamp is older than 180s (120s TTL + 60s skew allowance)
                    // or if timestamp is in the future by more than 60s
                    if (now - parsedTime > 180_000L || parsedTime - now > 60_000L) {
                        return null
                    }
                }
            }

            ParsedLoginChallenge(
                challengeId = challengeId,
                challengeNonce = nonce,
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun detectScanType(data: String): String = when {
    data.startsWith("http://") || data.startsWith("https://") -> "url"
    data.startsWith("tel:") -> "phone"
    data.startsWith("mailto:") -> "email"
    data.startsWith("WIFI:") -> "wifi"
    else -> "text"
}
