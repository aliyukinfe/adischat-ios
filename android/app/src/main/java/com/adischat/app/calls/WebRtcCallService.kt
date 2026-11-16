package com.adischat.app.calls

import android.content.Context
import com.adischat.app.network.CallRealtimeToken
import com.adischat.app.network.CallSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalMediaState(
    val microphoneEnabled: Boolean = true,
    val speakerEnabled: Boolean = false,
    val cameraEnabled: Boolean = false,
    val usingFrontCamera: Boolean = true,
)

class WebRtcCallService(private val context: Context) {
    private val _mediaState = MutableStateFlow(LocalMediaState())
    val mediaState: StateFlow<LocalMediaState> = _mediaState.asStateFlow()

    suspend fun startVoiceCall(call: CallSession, token: CallRealtimeToken): Result<Unit> = runCatching {
        ensureRealtimeReady(token)
        getLocalMedia(video = false)
        // TODO: Create PeerConnection/WebRTC tracks and connect to Cloudflare Realtime SFU.
        // Use token.sfuUrl and token.turnUrls from backend only; never hardcode TURN credentials.
    }

    suspend fun startVideoCall(call: CallSession, token: CallRealtimeToken): Result<Unit> = runCatching {
        ensureRealtimeReady(token)
        getLocalMedia(video = true)
        // TODO: Publish camera + microphone tracks to Cloudflare Realtime SFU.
        // Keep the same session model so this can grow into group calls later.
    }

    suspend fun acceptCall(call: CallSession, token: CallRealtimeToken): Result<Unit> = runCatching {
        ensureRealtimeReady(token)
        getLocalMedia(video = call.type == "video")
        // TODO: Join existing Cloudflare Realtime SFU session using call.cloudflareSessionId.
    }

    fun rejectCall() {
        cleanupCall()
    }

    fun endCall() {
        cleanupCall()
    }

    fun getLocalMedia(video: Boolean) {
        _mediaState.value = _mediaState.value.copy(cameraEnabled = video)
        // TODO: After adding the native WebRTC dependency, request AudioSource/VideoCapturer here.
        // Permissions are requested by the Compose call screen before this method is used.
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        _mediaState.value = _mediaState.value.copy(microphoneEnabled = enabled)
        // TODO: Toggle the local audio track when WebRTC tracks are attached.
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        _mediaState.value = _mediaState.value.copy(speakerEnabled = enabled)
        // TODO: Route audio through AudioManager speakerphone when active call audio is attached.
    }

    fun setCameraEnabled(enabled: Boolean) {
        _mediaState.value = _mediaState.value.copy(cameraEnabled = enabled)
        // TODO: Enable/disable the local video track.
    }

    fun switchCamera() {
        _mediaState.value = _mediaState.value.copy(usingFrontCamera = !_mediaState.value.usingFrontCamera)
        // TODO: Switch CameraVideoCapturer source when WebRTC dependency is wired.
    }

    fun cleanupCall() {
        _mediaState.value = LocalMediaState()
        // TODO: Dispose PeerConnection, media tracks, SFU session, and TURN resources.
    }

    private fun ensureRealtimeReady(token: CallRealtimeToken) {
        if (token.setupRequired || token.token.isBlank()) {
        throw IllegalStateException(token.message.ifBlank { "Cloudflare Realtime is not configured yet." })
        }
    }
}

