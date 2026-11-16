package com.adischat.app.ui.calls

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adischat.app.calls.CallEngine
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * App-level host for the active call. Mount this once near the navigation root so
 * the call screen (incoming or outgoing) overlays whatever screen is showing.
 */
@Composable
fun CallHost() {
    val state by CallEngine.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val egl = CallEngine.eglBaseContext
    val remoteTrack by CallEngine.remoteVideoTrack.collectAsStateWithLifecycle()
    val localTrack by CallEngine.localVideoTrack.collectAsStateWithLifecycle()

    CallScreen(
        state = s,
        onAccept = { CallEngine.acceptIncoming() },
        onReject = { CallEngine.reject() },
        onEnd = { CallEngine.end() },
        onToggleMute = { CallEngine.toggleMute() },
        onToggleSpeaker = { CallEngine.toggleSpeaker() },
        onSwitchCamera = { CallEngine.switchCamera() },
        onToggleCamera = { CallEngine.toggleCamera() },
        onDismissEnded = { CallEngine.dismiss() },
        remoteVideo = if (egl != null && remoteTrack != null) {
        { VideoRenderer(remoteTrack, egl, mirror = false) }
        } else null,
        localVideo = if (egl != null && localTrack != null) {
        { VideoRenderer(localTrack, egl, mirror = true) }
        } else null,
    )
}

@Composable
private fun VideoRenderer(track: VideoTrack?, egl: EglBase.Context, mirror: Boolean) {
    val context = LocalContext.current
    val renderer = remember(egl) {
        SurfaceViewRenderer(context).apply {
        init(egl, null)
        setMirror(mirror)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        setEnableHardwareScaler(true)
        }
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer.release() } } }
    DisposableEffect(track, renderer) {
        runCatching { track?.addSink(renderer) }
        onDispose { runCatching { track?.removeSink(renderer) } }
    }
    AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
}
