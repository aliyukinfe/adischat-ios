package com.adischat.app.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.adischat.app.FeatureFlags
import com.adischat.app.data.repository.CallRepository
import com.adischat.app.network.CallSession
import com.adischat.app.network.IceServer as ApiIceServer
import com.adischat.app.ui.calls.CallMode
import com.adischat.app.ui.calls.CallPhase
import com.adischat.app.ui.calls.CallUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Process-wide 1:1 P2P WebRTC call engine. Owns a single active call and drives
 * the full lifecycle (offer/answer/ICE) against the worker's signaling mailbox,
 * plus FCM-delivered incoming calls. The Compose layer just observes [state] and
 * renders [localVideoTrack] / [remoteVideoTrack].
 */
object CallEngine {

    private const val TAG = "CallEngine"
    private const val STREAM_ID = "adis_stream"
    private const val RING_TIMEOUT_MS = 45_000L
    private const val POLL_MS = 800L
    private const val STATUS_POLL_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<CallUiState?>(null)
    /** Null when there is no active call. */
    val state: StateFlow<CallUiState?> = _state.asStateFlow()

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private val repo = CallRepository()

    // - WebRTC singletons -
    private var appContext: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    val eglBaseContext: EglBase.Context? get() = eglBase?.eglBaseContext

    // - Per-call state -
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var isCaller = false
    private var lastSeq = 0L
    private var loopJob: Job? = null
    private var timeoutJob: Job? = null
    private var remoteDescriptionSet = false
    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var lastMediaSetupError: String? = null

    private fun candidateType(candidate: String): String {
        val parts = candidate.split(" ")
        val index = parts.indexOf("typ")
        return if (index >= 0 && index + 1 < parts.size) parts[index + 1] else "unknown"
    }

    // - Public init -
    fun init(context: Context) {
        appContext = context.applicationContext   // set first so calls can proceed even if WebRTC init fails
        if (factory != null) return
        try {
        val egl = EglBase.create()
        eglBase = egl
        PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
        .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
        .createPeerConnectionFactory()
        Log.d(TAG, "WebRTC factory initialized")
        } catch (t: Throwable) {
        Log.e(TAG, "WebRTC init failed", t)
        }
    }

    // - Outgoing call -
    fun startOutgoing(receiverUid: String, peerName: String, peerPhoto: String, mode: CallMode) {
        Log.d(TAG, "startOutgoing receiver=$receiverUid mode=$mode existing=${_state.value?.phase}")
        if (mode == CallMode.Video && !FeatureFlags.VIDEO_CALLS_ENABLED) {
        Log.w(TAG, "video call start blocked: feature disabled")
        return
        }
        // A finished call must not block the next one.
        if (_state.value?.phase?.isTerminal == true) _state.value = null
        if (_state.value != null) { Log.w(TAG, "startOutgoing ignored - a call is already active"); return }
        if (receiverUid.isBlank()) { Log.w(TAG, "startOutgoing ignored - blank receiverUid"); return }
        // Show the call UI immediately (before any network/WebRTC work).
        _state.value = CallUiState(
        mode = mode, phase = CallPhase.Calling, peerName = peerName,
        peerUid = receiverUid, peerPhotoUrl = peerPhoto, loading = true, startedAt = System.currentTimeMillis(),
        cameraEnabled = mode == CallMode.Video,
        )
        isCaller = true
        scope.launch {
        val type = if (mode == CallMode.Video) "video" else "voice"
        repo.startCall(receiverUid, type)
        .onSuccess { call ->
        Log.d(TAG, "call created id=${call.id}")
        update { it.copy(callId = call.id, phase = CallPhase.Calling, loading = false) }
        if (call.status == "unreachable") {
        terminate(CallPhase.Failed, "User not reachable")
        return@launch
        }
        if (!setupPeerAndMedia(call.id, mode)) {
        fail(lastMediaSetupError ?: "Could not prepare call audio", null)
        return@launch
        }
        // Caller creates the offer immediately; it waits in the mailbox.
        createOffer()
        startSignalingLoop(call.id)
        startStatusLoop(call.id)   // detect receiver reject/accept/timeout
        armRingTimeout(call.id)
        }
        .onFailure { e -> Log.e(TAG, "startCall failed", e); fail("Could not start call", e) }
        }
    }

    // - Incoming call (from FCM push) -
    fun onIncomingCall(
        callId: String,
        callerUid: String,
        callerName: String,
        callType: String,
        callerPhotoUrl: String = "",
    ) {
        Log.d(TAG, "onIncomingCall id=$callId from=$callerUid type=$callType existing=${_state.value?.phase}")
        if (callType == "video" && !FeatureFlags.VIDEO_CALLS_ENABLED) {
        Log.w(TAG, "incoming video call ignored: feature disabled callId=$callId")
        scope.launch { runCatching { repo.rejectCall(callId) } }
        return
        }
        if (_state.value?.phase?.isTerminal == true) _state.value = null
        if (_state.value != null) return  // already in a call
        isCaller = false
        _state.value = CallUiState(
        callId = callId,
        mode = if (callType == "video") CallMode.Video else CallMode.Voice,
        phase = CallPhase.IncomingRinging,
        peerUid = callerUid,
        peerName = callerName,
        peerPhotoUrl = callerPhotoUrl,
        startedAt = System.currentTimeMillis(),
        )
        scope.launch {
        repo.ringingAck(callId)
        .onSuccess { Log.d(TAG, "ringing_ack sent callId=$callId") }
        .onFailure { Log.w(TAG, "ringing_ack failed callId=$callId", it) }
        }
        // CRITICAL: poll the backend status while ringing so a caller cancel /
        // timeout closes this incoming screen immediately (no zombie ringing).
        startStatusLoop(callId)

        // If the user never answers, mark missed after the ring window. Also tell
        // the server (idempotent) so the missed call is recorded even if the
        // caller's app died first.
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
        delay(RING_TIMEOUT_MS)
        if (_state.value?.phase == CallPhase.IncomingRinging) {
        runCatching { repo.endCall(callId, "missed_timeout") }
        update { it.copy(phase = CallPhase.Missed, endReason = "Missed call") }
        cleanup()
        autoDismiss()
        }
        }
    }

    fun onCallUpdate(callId: String, status: String) {
        val s = _state.value
        if (s?.callId == callId) {
        applyRemoteStatus(status)
        }
        if (status in listOf("cancelled", "rejected", "missed", "completed", "ended", "failed", "unreachable")) {
        appContext?.let { com.adischat.app.notifications.AdisNotificationManager.cancelIncomingCall(it, callId) }
        }
    }

    fun acceptIncoming() {
        val s = _state.value ?: return
        if (s.phase != CallPhase.IncomingRinging) return
        timeoutJob?.cancel()
        appContext?.let { com.adischat.app.notifications.AdisNotificationManager.cancelIncomingCall(it) }
        update { it.copy(phase = CallPhase.Connecting, loading = true) }
        scope.launch {
        repo.acceptCall(s.callId)
        .onSuccess {
        if (!setupPeerAndMedia(s.callId, s.mode)) {
        fail(lastMediaSetupError ?: "Could not prepare call audio", null)
        return@launch
        }
        startSignalingLoop(s.callId)  // will pick up the caller's offer
        }
        .onFailure { e -> fail("Could not join call", e) }
        }
    }

    fun reject() {
        val s = _state.value ?: return
        if (s.callId.isNotBlank()) scope.launch { runCatching { repo.rejectCall(s.callId) } }
        update { it.copy(phase = CallPhase.Rejected, endReason = "Call declined", loading = false) }
        cleanup()
        autoDismiss()
    }

    fun end() {
        val s = _state.value ?: return
        // Always hit the backend FIRST (source of truth) so the other party syncs.
        if (isCaller && s.connectedAt == 0L && !s.phase.isTerminal) {
        // Caller hanging up before the call connects = cancel.
        if (s.callId.isNotBlank()) scope.launch { runCatching { repo.cancelCall(s.callId) } }
        terminate(CallPhase.Ended, "Call cancelled")
        } else {
        val reason = if (isCaller) "ended_by_caller" else "ended_by_receiver"
        if (s.callId.isNotBlank()) scope.launch { runCatching { repo.endCall(s.callId, reason) } }
        val dur = durationLabel(s.connectedAt)
        terminate(CallPhase.Ended, if (dur.isNotBlank()) "Call ended - $dur" else "Call ended")
        }
    }

    // - Live WebRTC stats - quality / latency / audio level -
    private var statsJob: Job? = null
    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
        while (isActive) {
        val pc = peer
        if (pc != null) {
        pc.getStats { report ->
        var rttMs = 0
        var level = 0f
        for (stat in report.statsMap.values) {
        when (stat.type) {
        "candidate-pair" -> {
        val nominated = stat.members["nominated"] as? Boolean ?: false
        val rtt = stat.members["currentRoundTripTime"] as? Double
        if (rtt != null && (nominated || rttMs == 0)) rttMs = (rtt * 1000).toInt()
        }
        "inbound-rtp" -> {
        if (stat.members["kind"] == "audio") {
        (stat.members["audioLevel"] as? Double)?.let { level = it.toFloat() }
        }
        }
        }
        }
        val quality = when {
        rttMs in 1..149   -> "Excellent"
        rttMs in 150..299 -> "Good"
        rttMs >= 300      -> "Poor"
        else -> "Excellent"
        }
        update { it.copy(connectionQuality = quality, latencyMs = rttMs, audioLevel = level) }
        }
        }
        delay(1500)
        }
        }
    }

    private fun durationLabel(connectedAt: Long): String {
        if (connectedAt <= 0L) return ""
        val secs = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
        return "%02d:%02d".format(secs / 60, secs % 60)
    }

    fun dismiss() {
        dismissJob?.cancel()
        _state.value = null
    }

    /** Hard boundary for logout/account switch. No call UI or media state may survive. */
    fun resetForAccountSwitch() {
        dismissJob?.cancel()
        cleanup()
        isCaller = false
        _state.value = null
    }

    private var dismissJob: Job? = null
    /** Clear the call screen automatically a moment after it ends. */
    private fun autoDismiss() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
        delay(1500)
        if (_state.value?.phase?.isTerminal == true) _state.value = null
        }
    }

    // - Controls -
    fun toggleMute() {
        val muted = !(_state.value?.muted ?: false)
        audioTrack?.setEnabled(!muted)
        update { it.copy(muted = muted) }
    }

    fun toggleSpeaker() {
        val on = !(_state.value?.speaker ?: false)
        setSpeakerphone(on)
        update { it.copy(speaker = on) }
    }

    /** Route call audio to the loudspeaker or earpiece. Uses the modern
     *  communication-device API on Android 12+ (the old isSpeakerphoneOn is
     *  unreliable there) and falls back on older versions. */
    private fun setSpeakerphone(on: Boolean) {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val wanted = if (on) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        val device = am.availableCommunicationDevices.firstOrNull { it.type == wanted }
        if (device != null) am.setCommunicationDevice(device)
        else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = on }
        } else {
        @Suppress("DEPRECATION") { am.isSpeakerphoneOn = on }
        }
        }
    }

    fun toggleCamera() {
        val on = !(_state.value?.cameraEnabled ?: false)
        videoTrack?.setEnabled(on)
        update { it.copy(cameraEnabled = on) }
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
        update { it.copy(usingFrontCamera = !it.usingFrontCamera) }
    }

    // - Internal: WebRTC setup -
    private suspend fun setupPeerAndMedia(callId: String, mode: CallMode): Boolean {
        lastMediaSetupError = null
        val f = factory
        if (f == null) { Log.e(TAG, "setupPeerAndMedia: WebRTC factory is null (init failed)"); return false }
        // ICE servers from backend (STUN, + TURN when configured).
        val ice = repo.getCallToken(callId).getOrNull()?.realtime?.iceServers ?: emptyList()
        val iceServers = buildIceServers(ice)
        val iceUrls = ice.flatMap { it.urls }
        val hasTurn = iceUrls.any { it.startsWith("turn:", ignoreCase = true) || it.startsWith("turns:", ignoreCase = true) }
        val hasStun = iceUrls.any { it.startsWith("stun:", ignoreCase = true) } || ice.isEmpty()
        Log.d(TAG, "setupPeerAndMedia callId=$callId mode=$mode iceServers=${iceServers.size} hasStun=$hasStun hasTurn=$hasTurn")

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        iceTransportsType = PeerConnection.IceTransportsType.ALL
        iceCandidatePoolSize = 4
        }
        peer = f.createPeerConnection(rtcConfig, pcObserver(callId))
        if (peer == null) return false

        // Audio (always)
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("audio0", audioSource).also {
        peer?.addTrack(it, listOf(STREAM_ID))
        }

        // Video (only for video calls)
        if (mode == CallMode.Video && !startLocalVideo(f)) {
        lastMediaSetupError = "Camera could not start."
        return false
        }

        // Route audio to the in-call earpiece by default.
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_IN_COMMUNICATION
        return true
    }

    private fun startLocalVideo(f: PeerConnectionFactory): Boolean {
        val ctx = appContext ?: run {
        Log.e(TAG, "camera start failed: app context missing")
        return false
        }
        val permissionGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "camera permission result granted=$permissionGranted")
        if (!permissionGranted) {
        Log.w(TAG, "camera start blocked: CAMERA permission not granted")
        return false
        }
        val egl = eglBase ?: run {
        Log.e(TAG, "camera start failed: EGL context missing")
        return false
        }
        val enumerator = Camera2Enumerator(ctx)
        Log.d(TAG, "camera enumerator devices=${enumerator.deviceNames.joinToString(",")}")
        val frontName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        ?: enumerator.deviceNames.firstOrNull() ?: run {
        Log.e(TAG, "camera start failed: no camera devices found")
        return false
        }
        Log.d(TAG, "camera selected device=$frontName")
        val cap = enumerator.createCapturer(frontName, null) ?: run {
        Log.e(TAG, "camera start failed: capturer creation returned null")
        return false
        }
        Log.d(TAG, "camera capturer created")
        capturer = cap
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
        val vs = f.createVideoSource(false)
        videoSource = vs
        cap.initialize(surfaceHelper, ctx, vs.capturerObserver)
        val captureStarted = runCatching { cap.startCapture(1280, 720, 30) }
        .onSuccess { Log.d(TAG, "camera startCapture success 1280x720@30") }
        .onFailure { Log.e(TAG, "camera startCapture failed", it) }
        .isSuccess
        if (!captureStarted) return false
        videoTrack = f.createVideoTrack("video0", vs).also {
        it.setEnabled(true)
        peer?.addTrack(it, listOf(STREAM_ID))
        }
        Log.d(TAG, "local video track created enabled=${videoTrack?.enabled()} id=${videoTrack?.id()}")
        _localVideo.value = videoTrack
        return videoTrack != null
    }

    private fun createOffer() {
        val pc = peer ?: return
        val constraints = mediaConstraints(_state.value?.mode == CallMode.Video)
        pc.createOffer(object : SimpleSdp() {
        override fun onCreateSuccess(desc: SessionDescription) {
        pc.setLocalDescription(SimpleSdp(), desc)
        sendDescription("offer", desc)
        }
        }, constraints)
    }

    private fun createAnswer() {
        val pc = peer ?: return
        val constraints = mediaConstraints(_state.value?.mode == CallMode.Video)
        pc.createAnswer(object : SimpleSdp() {
        override fun onCreateSuccess(desc: SessionDescription) {
        pc.setLocalDescription(SimpleSdp(), desc)
        sendDescription("answer", desc)
        }
        }, constraints)
    }

    private fun sendDescription(kind: String, desc: SessionDescription) {
        val callId = _state.value?.callId ?: return
        val payload = JSONObject().put("sdp", desc.description).toString()
        scope.launch { repo.sendSignal(callId, kind, payload) }
    }

    // - Signaling loop: poll the mailbox + call status -
    private fun startSignalingLoop(callId: String) {
        loopJob?.cancel()
        lastSeq = 0L
        loopJob = scope.launch {
        while (isActive) {
        repo.pollSignals(callId, lastSeq).onSuccess { (status, signals) ->
        for (sig in signals) {
        lastSeq = maxOf(lastSeq, sig.seq)
        handleSignal(sig.kind, sig.payload)
        }
        applyRemoteStatus(status)
        }
        delay(POLL_MS)
        }
        }
    }

    private fun handleSignal(kind: String, payload: String) {
        val pc = peer ?: return
        when (kind) {
        "offer" -> {
        val sdp = JSONObject(payload).optString("sdp")
        pc.setRemoteDescription(object : SimpleSdp() {
        override fun onSetSuccess() {
        remoteDescriptionSet = true
        drainPendingIce()
        createAnswer()
        }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
        }
        "answer" -> {
        val sdp = JSONObject(payload).optString("sdp")
        pc.setRemoteDescription(object : SimpleSdp() {
        override fun onSetSuccess() {
        remoteDescriptionSet = true
        drainPendingIce()
        }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
        "ice" -> {
        val o = JSONObject(payload)
        val candidate = IceCandidate(
        o.optString("sdpMid"),
        o.optInt("sdpMLineIndex"),
        o.optString("candidate"),
        )
        if (remoteDescriptionSet) pc.addIceCandidate(candidate)
        else pendingRemoteIce.add(candidate)
        }
        }
    }

    private fun drainPendingIce() {
        val pc = peer ?: return
        pendingRemoteIce.forEach { pc.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    /**
     * The backend status is the single source of truth. Both peers poll it (status
     * loop) AND read it from the signaling loop. Handles every terminal state so a
     * cancel/reject/end/missed on one side always closes the other. Idempotent.
     */
    private fun applyRemoteStatus(status: String) {
        val s = _state.value ?: return
        if (s.phase.isTerminal) return
        when (status) {
        "created", "delivering" -> { /* waiting for receiver device ack */ }
        "ringing"   -> if (isCaller && s.phase == CallPhase.Calling) update { it.copy(phase = CallPhase.Ringing, loading = false) }
        "accepted"  -> if (s.phase == CallPhase.Ringing || s.phase == CallPhase.Calling) update { it.copy(phase = CallPhase.Connecting, loading = true) }
        "connected" -> { /* WebRTC onConnectionChange drives Connected + connectedAt */ }
        "completed", "ended" -> {
        val d = durationLabel(s.connectedAt)
        terminate(CallPhase.Ended, if (d.isNotBlank()) "Call ended - $d" else "Call ended")
        }
        "cancelled" -> if (isCaller) terminate(CallPhase.Ended, "Call ended")
        else terminate(CallPhase.Missed, "Cancelled")
        "rejected"  -> terminate(CallPhase.Rejected, "Call declined")
        "missed"    -> terminate(CallPhase.Missed, if (isCaller) "No answer" else "Missed call")
        "unreachable" -> terminate(CallPhase.Failed, "User not reachable")
        "failed"    -> terminate(CallPhase.Failed, "Connection failed")
        }
    }

    /** Move to a terminal phase + full cleanup + auto-dismiss. Idempotent. */
    private fun terminate(phase: CallPhase, reason: String) {
        val s = _state.value ?: return
        if (s.phase.isTerminal) return
        update { it.copy(phase = phase, endReason = reason, loading = false) }
        cleanup()
        autoDismiss()
    }

    // - Shared lifecycle status loop (runs on BOTH sides for the whole call) -
    // This is the cross-peer sync: even while the receiver is still ringing (no
    // signaling loop yet) it polls the backend so a caller cancel/timeout closes
    // it immediately. Backend is the source of truth.
    private var statusJob: Job? = null
    private fun startStatusLoop(callId: String) {
        statusJob?.cancel()
        statusJob = scope.launch {
        while (isActive) {
        val s = _state.value
        if (s == null || s.phase.isTerminal) break
        repo.getCall(callId).onSuccess { call -> applyRemoteStatus(call.status) }
        delay(STATUS_POLL_MS)
        }
        }
    }

    private fun armRingTimeout(callId: String) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
        delay(RING_TIMEOUT_MS)
        val s = _state.value ?: return@launch
        if (s.phase == CallPhase.Ringing) {
        runCatching { repo.endCall(callId, "missed_timeout") }   // - server records 'missed'
        terminate(CallPhase.Missed, "No answer")
        } else if (s.phase == CallPhase.Calling) {
        runCatching { repo.endCall(callId, "no_receiver_ack") }
        terminate(CallPhase.Failed, "User not reachable")
        }
        }
    }

    // - PeerConnection observer -
    private fun markPeerConnected(callId: String) {
        val firstConnect = (_state.value?.connectedAt ?: 0L) == 0L
        update {
        if (it.phase.isTerminal) it
        else it.copy(
        phase = CallPhase.Connected,
        loading = false,
        connectedAt = if (it.connectedAt == 0L) System.currentTimeMillis() else it.connectedAt,
        )
        }
        if (firstConnect) scope.launch { runCatching { repo.markConnected(callId) } }
        startStatsLoop()
    }

    private fun pcObserver(callId: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "ICE candidate gathered callId=$callId type=${candidateType(candidate.sdp)}")
        val payload = JSONObject()
        .put("candidate", candidate.sdp)
        .put("sdpMid", candidate.sdpMid)
        .put("sdpMLineIndex", candidate.sdpMLineIndex)
        .toString()
        scope.launch { repo.sendSignal(callId, "ice", payload) }
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>?) {
        val track = receiver.track()
        if (track is VideoTrack) {
        _remoteVideo.value = track
        }
        // Remote audio plays automatically once the track is live.
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        when (newState) {
        PeerConnection.PeerConnectionState.CONNECTED -> markPeerConnected(callId)
        PeerConnection.PeerConnectionState.DISCONNECTED ->
        update { if (it.phase == CallPhase.Connected) it.copy(phase = CallPhase.Reconnecting) else it }
        PeerConnection.PeerConnectionState.FAILED -> fail("Connection lost", null)
        else -> {}
        }
        }

        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
        Log.d(TAG, "iceConnectionState callId=$callId state=$s")
        when (s) {
        PeerConnection.IceConnectionState.CONNECTED,
        PeerConnection.IceConnectionState.COMPLETED -> markPeerConnected(callId)
        PeerConnection.IceConnectionState.DISCONNECTED ->
        update { if (it.phase == CallPhase.Connected) it.copy(phase = CallPhase.Reconnecting) else it }
        PeerConnection.IceConnectionState.FAILED -> fail("Connection lost", null)
        else -> {}
        }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "iceGatheringState callId=$callId state=$s")
        }
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {
        Log.d(TAG, "signalingState callId=$callId state=$s")
        }
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(d: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    // - Helpers -
    private fun buildIceServers(api: List<ApiIceServer>): List<PeerConnection.IceServer> {
        val list = mutableListOf<PeerConnection.IceServer>()
        if (api.isEmpty()) {
        list.add(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer())
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        list.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
        } else {
        for (s in api) {
        val b = PeerConnection.IceServer.builder(s.urls)
        if (s.username.isNotBlank()) b.setUsername(s.username)
        if (s.credential.isNotBlank()) b.setPassword(s.credential)
        list.add(b.createIceServer())
        }
        }
        return list
    }

    private fun mediaConstraints(video: Boolean) = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
    }

    private fun fail(message: String, e: Throwable?) {
        Log.w(TAG, message, e)
        val callId = _state.value?.callId
        if (!callId.isNullOrBlank()) scope.launch { runCatching { repo.endCall(callId, "network_error") } }
        update { it.copy(phase = CallPhase.Failed, loading = false, endReason = message, error = message) }
        cleanup()
        autoDismiss()
    }

    private inline fun update(block: (CallUiState) -> CallUiState) {
        _state.value = _state.value?.let(block)
    }

    private fun cleanup() {
        loopJob?.cancel(); loopJob = null
        timeoutJob?.cancel(); timeoutJob = null
        statsJob?.cancel(); statsJob = null
        statusJob?.cancel(); statusJob = null
        lastSeq = 0L
        remoteDescriptionSet = false
        pendingRemoteIce.clear()
        _remoteVideo.value = null
        _localVideo.value = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }; capturer = null
        runCatching { surfaceHelper?.dispose() }; surfaceHelper = null
        runCatching { videoTrack?.dispose() }; videoTrack = null
        runCatching { videoSource?.dispose() }; videoSource = null
        runCatching { audioTrack?.dispose() }; audioTrack = null
        runCatching { audioSource?.dispose() }; audioSource = null
        runCatching { peer?.close(); peer?.dispose() }; peer = null
        (appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
        mode = AudioManager.MODE_NORMAL
        isSpeakerphoneOn = false
        }
        appContext?.let { com.adischat.app.notifications.AdisNotificationManager.cancelIncomingCall(it) }
    }
}

/** SDP observer with empty defaults so callers override only what they need. */
private open class SimpleSdp : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) { Log.w("CallEngine", "SDP create failed: $error") }
    override fun onSetFailure(error: String?) { Log.w("CallEngine", "SDP set failed: $error") }
}
