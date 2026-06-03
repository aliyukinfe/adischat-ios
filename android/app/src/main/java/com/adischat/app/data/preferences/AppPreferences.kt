package com.adischat.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.adischat.app.data.model.ScanEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adischat_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val THEME_KEY        = stringPreferencesKey("theme")
        private val SCAN_HISTORY_KEY = stringPreferencesKey("scan_history")
        private val WALLET_STATE_KEY = stringPreferencesKey("wallet_state")
        private val FATHER_NAME_KEY  = stringPreferencesKey("father_name")
        private val WALLET_FINGERPRINT_KEY = booleanPreferencesKey("wallet_fingerprint_enabled")
        // Set of hostnames that have been granted permission to read the AdisChat
        // user identity inside the in-app browser bridge.
        private val WEB_BRIDGE_GRANTS_KEY  = stringSetPreferencesKey("web_bridge_grants")
        private val gson             = Gson()
    }

    /** "light" | "dark" | "system" */
    val themeFlow: Flow<String> = context.dataStore.data
        .map { it[THEME_KEY] ?: "system" }

    val scanHistoryFlow: Flow<List<ScanEntry>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[SCAN_HISTORY_KEY] ?: "[]"
            try {
                val type = object : TypeToken<List<ScanEntry>>() {}.type
                gson.fromJson<List<ScanEntry>>(json, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }

    val fatherNameFlow: Flow<String> = context.dataStore.data
        .map { it[FATHER_NAME_KEY] ?: "" }

    val walletFingerprintFlow: Flow<Boolean> = context.dataStore.data
        .map { it[WALLET_FINGERPRINT_KEY] ?: false }

    /** Hostnames the user has authorised to read the AdisChat identity. */
    val webBridgeGrantsFlow: Flow<Set<String>> = context.dataStore.data
        .map { it[WEB_BRIDGE_GRANTS_KEY] ?: emptySet() }

    suspend fun isWebBridgeGranted(host: String): Boolean {
        if (host.isBlank()) return false
        val set = context.dataStore.data
            .map { it[WEB_BRIDGE_GRANTS_KEY] ?: emptySet() }
            .first()
        return host in set
    }

    suspend fun setWebBridgeGranted(host: String, granted: Boolean) {
        if (host.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[WEB_BRIDGE_GRANTS_KEY] ?: emptySet()
            prefs[WEB_BRIDGE_GRANTS_KEY] = if (granted) current + host else current - host
        }
    }

    val walletStateFlow: Flow<WalletState> = context.dataStore.data
        .map { prefs ->
            try {
                gson.fromJson(prefs[WALLET_STATE_KEY] ?: "", WalletState::class.java) ?: WalletState()
            } catch (e: Exception) {
                WalletState()
            }
        }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setFatherName(name: String) {
        context.dataStore.edit { it[FATHER_NAME_KEY] = name.trim() }
    }

    suspend fun setWalletFingerprintEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WALLET_FINGERPRINT_KEY] = enabled }
    }

    suspend fun addScanEntry(entry: ScanEntry) {
        context.dataStore.edit { prefs ->
            val current: List<ScanEntry> = try {
                val type = object : TypeToken<List<ScanEntry>>() {}.type
                gson.fromJson(prefs[SCAN_HISTORY_KEY] ?: "[]", type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
            val updated = (listOf(entry) + current).take(30)
            prefs[SCAN_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun clearScanHistory() {
        context.dataStore.edit { it[SCAN_HISTORY_KEY] = "[]" }
    }

    suspend fun saveWalletState(state: WalletState) {
        context.dataStore.edit { it[WALLET_STATE_KEY] = gson.toJson(state) }
    }
}

data class WalletState(
    val userId: String = "",
    val active: Boolean = false,
    val balance: Double = 0.0,
    val walletPinSet: Boolean = false,
    val frozen: Boolean = false,
    val walletId: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val name: String = "",
    val currency: String = "ETB",
    val receiveQrPayload: String = "",
    val usernameQrPayload: String = "",
    val payCode: String = "",
    val payQrPayload: String = "",
    val payBarcodePayload: String = "",
    val payExpiresAt: Long = 0,
    val transactions: List<WalletTransaction> = emptyList(),
)

data class WalletTransaction(
    val id: String = "",
    val reference: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "activity",
    val status: String = "",
    val currency: String = "ETB",
    val counterpartyWalletId: String = "",
    val counterpartyName: String = "",
    val counterpartyUsername: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
