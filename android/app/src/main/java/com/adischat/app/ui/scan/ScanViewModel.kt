package com.adischat.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.ScanEntry
import com.adischat.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanResult {
    object None                      : ScanResult()
    data class Found(val data: String, val type: String) : ScanResult()
}

class ScanViewModel(private val prefs: AppPreferences) : ViewModel() {

    private val _result    = MutableStateFlow<ScanResult>(ScanResult.None)
    val result: StateFlow<ScanResult> = _result

    private val _torchOn   = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn

    private var scanning   = true

    fun onBarcodeDetected(raw: String) {
        if (!scanning) return
        scanning = false
        val type = detectType(raw)
        _result.value = ScanResult.Found(raw, type)
        viewModelScope.launch {
            prefs.addScanEntry(ScanEntry(data = raw, type = type, timestamp = System.currentTimeMillis()))
        }
    }

    fun dismissResult()  { _result.value = ScanResult.None; scanning = true }

    fun toggleTorch()    { _torchOn.value = !_torchOn.value }
    fun turnOffTorch()   { _torchOn.value = false }

    fun clearHistory()   { viewModelScope.launch { prefs.clearScanHistory() } }

    private fun detectType(data: String): String = when {
        data.startsWith("http://") || data.startsWith("https://") -> "url"
        data.startsWith("tel:")                                    -> "phone"
        data.startsWith("mailto:")                                 -> "email"
        data.startsWith("WIFI:")                                   -> "wifi"
        else                                                       -> "text"
    }
}
