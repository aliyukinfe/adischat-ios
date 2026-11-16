package com.adischat.app.data.repository

import com.adischat.app.network.CallActionBody
import com.adischat.app.network.CallSession
import com.adischat.app.network.CallSignal
import com.adischat.app.network.CallSignalBody
import com.adischat.app.network.CallStartBody
import com.adischat.app.network.CallTokenResponse
import com.adischat.app.network.CloudflareClient

class CallRepository {
    private val api = CloudflareClient.api

    suspend fun startVoiceCall(receiverUid: String): Result<CallSession> = startCall(receiverUid, "voice")

    suspend fun startVideoCall(receiverUid: String): Result<CallSession> = startCall(receiverUid, "video")

    suspend fun acceptCall(callId: String): Result<CallSession> = runCatching {
        api.acceptCall(CallActionBody(callId)).call
    }

    suspend fun rejectCall(callId: String): Result<CallSession> = runCatching {
        api.rejectCall(CallActionBody(callId)).call
    }

    suspend fun ringingAck(callId: String): Result<CallSession> = runCatching {
        api.ringingAck(CallActionBody(callId)).call
    }

    suspend fun cancelCall(callId: String): Result<CallSession> = runCatching {
        api.cancelCall(CallActionBody(callId)).call
    }

    suspend fun endCall(callId: String, endReason: String = ""): Result<CallSession> = runCatching {
        api.endCall(com.adischat.app.network.CallEndBody(callId, endReason)).call
    }

    /** Stamp connectedAt on the server so duration is measured from real media connect. */
    suspend fun markConnected(callId: String): Result<CallSession> = runCatching {
        api.connectedCall(CallActionBody(callId)).call
    }

    suspend fun getCallToken(callId: String): Result<CallTokenResponse> = runCatching {
        api.getCallToken(CallActionBody(callId))
    }

    suspend fun getCallHistory(limit: Int = 50): Result<List<CallSession>> = runCatching {
        api.getCallHistory(limit).calls
    }

    suspend fun startCall(receiverUid: String, type: String): Result<CallSession> = runCatching {
        api.startCall(CallStartBody(receiverUid = receiverUid, type = type)).call
    }

    // - Signaling (P2P WebRTC) -
    suspend fun getCall(callId: String): Result<CallSession> = runCatching {
        api.getCall(callId).call
    }

    suspend fun getActiveIncomingCall(): Result<CallSession?> = runCatching {
        api.getActiveCall().call
    }

    suspend fun getPendingIncomingCall(): Result<CallSession?> = runCatching {
        api.getPendingCall().call
    }

    suspend fun sendSignal(callId: String, kind: String, payload: String): Result<Unit> = runCatching {
        api.postCallSignal(callId, CallSignalBody(kind = kind, payload = payload))
        Unit
    }

    /** Returns the other peer's new signals plus the call's current status. */
    suspend fun pollSignals(callId: String, after: Long): Result<Pair<String, List<CallSignal>>> = runCatching {
        val res = api.getCallSignals(callId, after)
        res.status to res.signals
    }
}
