package com.adischat.app.data.repository

import com.adischat.app.data.model.AdminStats
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AppUser
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.GameApprovalDecisionBody
import com.adischat.app.network.GameApprovalResponse
import com.adischat.app.network.RejectBody
import com.adischat.app.network.SetRoleBody
import com.adischat.app.network.WalletAmountBody

class AdminRepository {

    private val api = CloudflareClient.api
    private val walletApi = CloudflareClient.walletApi

    suspend fun getStats(): Result<AdminStats> = runCatching {
        api.getAdminStats()
    }

    suspend fun getUsers(query: String = "", page: Int = 0): Result<List<AppUser>> = runCatching {
        api.getAdminUsers(query.trim(), page)
    }

    suspend fun setRole(uid: String, role: String): Result<Unit> = runCatching {
        api.setUserRole(uid, SetRoleBody(role))
        Unit
    }

    suspend fun getRequests(status: String = "pending"): Result<List<AdminVerificationRequest>> =
        runCatching { api.getAdminVerifications(status) }

    suspend fun approve(uid: String): Result<Unit> = runCatching {
        api.approveVerification(uid)
        Unit
    }

    suspend fun reject(uid: String, reason: String): Result<Unit> = runCatching {
        api.rejectVerification(uid, RejectBody(reason))
        Unit
    }

    suspend fun addWalletMoney(uid: String, amount: Double, reason: String): Result<Unit> = runCatching {
        walletApi.adminAdjustWallet(
        uid,
        WalletAmountBody(
        amount = amount,
        note = reason.ifBlank { "Admin wallet top-up" },
        )
        )
        Unit
    }

    // - Channel verification (blue badge) - same worker as channel posts -
    suspend fun getChannelVerifications(status: String = "pending"): Result<List<com.adischat.app.network.ChannelVerificationResponse>> =
        runCatching { walletApi.getChannelVerificationRequests(status) }

    suspend fun approveChannelVerification(channelId: String): Result<Unit> = runCatching {
        walletApi.approveChannelVerification(channelId); Unit
    }

    suspend fun rejectChannelVerification(channelId: String, reason: String): Result<Unit> = runCatching {
        walletApi.rejectChannelVerification(channelId, RejectBody(reason)); Unit
    }

    suspend fun getGameApprovals(status: String = "pending"): Result<List<GameApprovalResponse>> =
        runCatching { walletApi.getGameApprovalRequests(status) }

    suspend fun approveGame(channelId: String, note: String = ""): Result<Unit> = runCatching {
        walletApi.approveGameApproval(channelId, GameApprovalDecisionBody(note))
        Unit
    }

    suspend fun rejectGame(channelId: String, note: String): Result<Unit> = runCatching {
        walletApi.rejectGameApproval(channelId, GameApprovalDecisionBody(note))
        Unit
    }
}
