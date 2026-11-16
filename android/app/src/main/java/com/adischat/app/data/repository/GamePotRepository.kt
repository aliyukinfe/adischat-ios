package com.adischat.app.data.repository

import com.adischat.app.data.model.*
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.CreateGameBody
import com.adischat.app.network.BuyTicketsBody
import com.adischat.app.network.GameApprovalRequestBody
import com.adischat.app.network.GameApprovalResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import retrofit2.HttpException

class GamePotRepository {

    private val api = CloudflareClient.gamePotApi
    private val walletApi = CloudflareClient.walletApi

    // - Polling flows -

    /**
     * Emits the active game snapshot for a channel every 3 seconds.
     * Emits null if there is no active game (404 / empty).
     */
    fun activeGameFlow(channelId: String): Flow<GameSnapshot?> = flow {
        while (true) {
        val snapshot = runCatching { api.getActiveGame(channelId) }
        .getOrNull()
        ?.toGameSnapshotOrNull()
        emit(snapshot)
        delay(3_000)
        }
    }

    /**
     * Emits the full detail snapshot for a specific game every 3 seconds.
     */
    fun gameDetailFlow(gameId: String): Flow<GameSnapshot?> = flow {
        while (true) {
        val snapshot = runCatching { api.getGameDetail(gameId) }
        .getOrNull()
        ?.toGameSnapshotOrNull()
        emit(snapshot)
        delay(3_000)
        }
    }

    /**
     * Emits the ticket list for a game every 3 seconds.
     */
    fun ticketsFlow(gameId: String): Flow<List<GameTicket>> = flow {
        while (true) {
        val tickets = runCatching { api.getGameTickets(gameId) }
        .getOrDefault(emptyList())
        .map { it.toGameTicket() }
        emit(tickets)
        delay(3_000)
        }
    }

    /**
     * Emits the participant list for a game every 5 seconds.
     */
    fun participantsFlow(gameId: String): Flow<List<GameParticipant>> = flow {
        while (true) {
        val participants = runCatching { api.getParticipants(gameId) }
        .getOrDefault(emptyList())
        .map { it.toGameParticipant() }
        emit(participants)
        delay(5_000)
        }
    }

    // - One-shot suspend functions -

    suspend fun getApproval(channelId: String): Result<GameApprovalResponse> = gameResult {
        walletApi.getGameApproval(channelId)
    }

    suspend fun requestApproval(channelId: String, description: String): Result<GameApprovalResponse> = gameResult {
        walletApi.requestGameApproval(channelId, GameApprovalRequestBody(description))
    }

    suspend fun createGame(
        channelId: String,
        title: String,
        totalTickets: Int,
        pricePerTicket: Double,
        adminCommissionPct: Double,
        maxTicketsPerUser: Int?,
        durationSeconds: Long,
    ): Result<GameSnapshot> = gameResult {
        api.createGame(
        channelId = channelId,
        body = CreateGameBody(
        title      = title,
        totalTickets       = totalTickets,
        pricePerTicket     = pricePerTicket,
        adminCommissionPct = adminCommissionPct,
        maxTicketsPerUser  = maxTicketsPerUser,
        durationSeconds    = durationSeconds,
        )
        ).toGameSnapshot()
    }

    suspend fun activateGame(gameId: String): Result<GameSnapshot> = gameResult {
        api.activateGame(gameId).toGameSnapshot()
    }

    suspend fun buyTickets(
        gameId: String,
        ticketNumbers: List<Int>,
        walletPin: String,
    ): Result<TicketPurchaseResult> = gameResult {
        val resp = api.buyTickets(
        gameId = gameId,
        body   = BuyTicketsBody(ticketNumbers = ticketNumbers, walletPin = walletPin),
        )
        TicketPurchaseResult(
        game      = resp.game.toChannelGame(),
        myTickets = resp.myTickets,
        reference = resp.reference,
        cost      = resp.cost,
        )
    }

    suspend fun drawWinner(gameId: String): Result<GameWinner> = gameResult {
        val resp = api.drawWinner(gameId)
        // Worker returns: { game, winnerTicketNumber, winnerName, payoutAmount, commissionAmount, seed }
        val g = resp.game
        GameWinner(
        id      = "",
        gameId      = gameId,
        channelId       = g.channelId,
        winnerUid       = g.winnerUid ?: "",
        winnerName      = resp.winnerName ?: g.winnerName ?: "",
        ticketNumber    = resp.winnerTicketNumber ?: g.winnerTicketNumber ?: 0,
        jackpotAmount   = g.jackpotAmount,
        adminCommission = resp.commissionAmount,
        payoutAmount    = resp.payoutAmount,
        payoutReference = "",
        drawnAt      = System.currentTimeMillis(),
        )
    }

    suspend fun cancelGame(gameId: String): Result<Unit> = gameResult {
        api.cancelGame(gameId)
        Unit
    }

    suspend fun getGameHistory(channelId: String): Result<List<ChannelGame>> = gameResult {
        api.getGameHistory(channelId).map { it.toChannelGame() }
    }
}

private suspend fun <T> gameResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(IllegalStateException(gameErrorMessage(e)))
    }

private fun gameErrorMessage(error: Throwable): String {
    if (error is HttpException) {
        val body = error.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        if (parsed.isNotBlank()) return parsed
        return when (error.code()) {
        400 -> "Check the game details and try again"
        401 -> "Please sign in again"
        403 -> "You do not have permission for this game"
        404 -> "Game not found"
        423 -> "Wallet is locked"
        else -> "Game request failed (${error.code()})"
        }
    }
    return CloudflareClient.friendlyErrorMessage(error, "Game request failed")
}

// - Response - Domain mappers -

private fun com.adischat.app.network.GameSnapshotResponse.toGameSnapshot(): GameSnapshot =
    toGameSnapshotOrNull() ?: error("Game not found")

private fun com.adischat.app.network.GameSnapshotResponse.toGameSnapshotOrNull(): GameSnapshot? {
    val activeGame = game ?: return null
    return GameSnapshot(
        game = activeGame.toChannelGame(),
        myTickets = myTickets,
    )
}

private fun com.adischat.app.network.GameResponse.toChannelGame() = ChannelGame(
    id      = id,
    channelId      = channelId,
    creatorUid      = creatorUid,
    title      = title,
    totalTickets      = totalTickets,
    pricePerTicket      = pricePerTicket,
    adminCommissionPct    = adminCommissionPct,
    maxTicketsPerUser     = maxTicketsPerUser,
    durationSeconds       = durationSeconds,
    startsAt      = startsAt,
    endsAt      = endsAt,
    status      = status,
    jackpotAmount      = jackpotAmount,
    ticketsSold      = ticketsSold,
    totalTicketCount      = totalTickets,
    winnerTicketNumber    = winnerTicketNumber,
    winnerUid      = winnerUid,
    winnerName      = winnerName,
    winnerPayout      = winnerPayout,
    adminCommissionAmount = adminCommissionAmount,
    createdAt      = createdAt,
    updatedAt      = updatedAt,
)

private fun com.adischat.app.network.GameTicketResponse.toGameTicket() = GameTicket(
    ticketNumber = ticketNumber,
    status       = status,
    ownerUid     = ownerUid,
    ownerName    = ownerName,
    purchasedAt  = purchasedAt,
    isMine       = isMine,
)

private fun com.adischat.app.network.ParticipantResponse.toGameParticipant() = GameParticipant(
    uid      = uid,
    name      = name,
    photoUrl     = photoUrl,
    ticketsCount = ticketsCount,
    totalSpent   = totalSpent,
    joinedAt     = joinedAt,
)
