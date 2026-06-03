package com.adischat.app.data.repository

import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.VerificationStatus
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.RejectBody
import com.adischat.app.network.VerificationSubmitBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class VerificationRepository {

    private val api = CloudflareClient.api
    private val walletApi = CloudflareClient.walletApi

    suspend fun getStatus(): Result<VerificationStatus> = runCatching {
        api.getVerificationStatus()
    }

    suspend fun submitRequest(
        fullName: String,
        documentType: String,
        documentUrl: String,
        selfieUrl: String,
    ): Result<Unit> = runCatching {
        api.submitVerification(
            VerificationSubmitBody(
                fullName     = fullName,
                documentType = documentType,
                documentUrl  = documentUrl,
                selfieUrl    = selfieUrl,
            )
        )
        Unit
    }

    suspend fun getAdminRequests(status: String = "pending"): Result<List<AdminVerificationRequest>> =
        runCatching { api.getAdminVerifications(status) }

    suspend fun approve(uid: String): Result<Unit> = runCatching {
        api.approveVerification(uid)
        Unit
    }

    suspend fun reject(uid: String, reason: String): Result<Unit> = runCatching {
        api.rejectVerification(uid, RejectBody(reason))
        Unit
    }

    // ── Channel verification (blue badge) ──────────────────────────────────────
    suspend fun getChannelVerification(channelId: String): Result<com.adischat.app.network.ChannelVerificationResponse> =
        runCatching { walletApi.getChannelVerification(channelId) }

    suspend fun requestChannelVerification(channelId: String, note: String, channelName: String = ""): Result<com.adischat.app.network.ChannelVerificationResponse> =
        runCatching { walletApi.requestChannelVerification(channelId, com.adischat.app.network.ChannelVerificationRequest(note, channelName)) }

    suspend fun getChannelRequests(status: String = "pending"): Result<List<com.adischat.app.network.ChannelVerificationResponse>> =
        runCatching { walletApi.getChannelVerificationRequests(status) }

    suspend fun approveChannel(channelId: String): Result<Unit> = runCatching {
        walletApi.approveChannelVerification(channelId); Unit
    }

    suspend fun rejectChannel(channelId: String, reason: String): Result<Unit> = runCatching {
        walletApi.rejectChannelVerification(channelId, RejectBody(reason)); Unit
    }

    suspend fun uploadImage(type: String, bytes: ByteArray): Result<String> = runCatching {
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        api.uploadMedia(type, body).url
    }
}
