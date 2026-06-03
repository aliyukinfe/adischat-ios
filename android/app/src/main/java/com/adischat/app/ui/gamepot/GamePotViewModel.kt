package com.adischat.app.ui.gamepot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.*
import com.adischat.app.data.repository.ChatRepository
import com.adischat.app.data.repository.GamePotRepository
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.GameApprovalResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

data class GamePotUiState(
    // Game data
    val game:           ChannelGame?        = null,
    val myTickets:      List<Int>           = emptyList(),
    val tickets:        List<GameTicket>    = emptyList(),
    val participants:   List<GameParticipant> = emptyList(),

    // Countdown (ms remaining, updated every second from endsAt)
    val msRemaining:    Long?               = null,

    // Ticket selection (user picks before buying)
    val selectedTickets: Set<Int>           = emptySet(),

    // UI feedback
    val isLoading:      Boolean             = false,
    val isBuying:       Boolean             = false,
    val isDrawing:      Boolean             = false,
    val errorMessage:   String?             = null,
    val successMessage: String?             = null,
    val approval:       GameApprovalResponse? = null,
    val isRequestingApproval: Boolean       = false,

    // Winner announcement overlay
    val winner:         GameWinner?         = null,
    val showWinnerOverlay: Boolean          = false,
)

sealed class GamePotEvent {
    data class ShowError(val message: String) : GamePotEvent()
    data class ShowSuccess(val message: String) : GamePotEvent()
    object TicketsBought : GamePotEvent()
    object GameCancelled : GamePotEvent()
    data class WinnerDrawn(val winner: GameWinner) : GamePotEvent()
    object NavigateToRoom : GamePotEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GamePotViewModel : ViewModel() {

    private val repo = GamePotRepository()
    private val chatRepo = ChatRepository()

    private val _uiState = MutableStateFlow(GamePotUiState())
    val uiState: StateFlow<GamePotUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GamePotEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<GamePotEvent> = _events.asSharedFlow()

    // Active polling jobs — cancelled/restarted on channel/game change
    private var activeGameJob:   Job? = null
    private var ticketJob:       Job? = null
    private var participantJob:  Job? = null
    private var countdownJob:    Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call when entering a channel's game room (or banner). */
    fun watchChannel(channelId: String) {
        activeGameJob?.cancel()
        activeGameJob = viewModelScope.launch {
            repo.activeGameFlow(channelId).collect { snapshot ->
                if (snapshot != null) {
                    onGameUpdated(snapshot)
                } else {
                    _uiState.update { it.copy(game = null, myTickets = emptyList()) }
                    stopDetailPolling()
                }
            }
        }
    }

    /** Call when entering the full GameRoomScreen for a specific game. */
    fun watchGame(gameId: String) {
        startDetailPolling(gameId)
    }

    fun loadApproval(channelId: String) {
        viewModelScope.launch {
            repo.getApproval(channelId).onSuccess { approval ->
                _uiState.update { it.copy(approval = approval) }
            }
        }
    }

    fun requestApproval(channelId: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingApproval = true, errorMessage = null) }
            repo.requestApproval(channelId, description).fold(
                onSuccess = { approval ->
                    _uiState.update { it.copy(isRequestingApproval = false, approval = approval, successMessage = "Approval request submitted") }
                    _events.emit(GamePotEvent.ShowSuccess("Approval request submitted"))
                },
                onFailure = { e ->
                    val message = friendly(e, "Failed to request approval")
                    _uiState.update { it.copy(isRequestingApproval = false, errorMessage = message) }
                    _events.emit(GamePotEvent.ShowError(message))
                },
            )
        }
    }

    /** Toggle a ticket number in / out of the selection set. */
    fun toggleTicket(ticketNumber: Int) {
        _uiState.update { state ->
            val sel = state.selectedTickets.toMutableSet()
            if (ticketNumber in sel) sel.remove(ticketNumber) else sel.add(ticketNumber)
            state.copy(selectedTickets = sel)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTickets = emptySet()) }
    }

    /** Buy all currently selected tickets after PIN verification. */
    fun buySelectedTickets(gameId: String, walletPin: String) {
        val selected = _uiState.value.selectedTickets
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBuying = true, errorMessage = null) }
            repo.buyTickets(gameId, selected.toList(), walletPin).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isBuying       = false,
                            selectedTickets = emptySet(),
                            game           = result.game,
                            myTickets      = result.myTickets,
                            successMessage = "Got ${result.myTickets.size} ticket(s)! Cost: ${result.cost} ETB",
                        )
                    }
                    _events.emit(GamePotEvent.TicketsBought)
                },
                onFailure = { e ->
                    val message = friendly(e, "Purchase failed")
                    _uiState.update { it.copy(isBuying = false, errorMessage = message) }
                    _events.emit(GamePotEvent.ShowError(message))
                }
            )
        }
    }

    // ── Admin actions ─────────────────────────────────────────────────────────

    fun createGame(
        channelId: String,
        title: String,
        totalTickets: Int,
        pricePerTicket: Double,
        adminCommissionPct: Double,
        maxTicketsPerUser: Int?,
        durationSeconds: Long,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.createGame(
                channelId          = channelId,
                title              = title,
                totalTickets       = totalTickets,
                pricePerTicket     = pricePerTicket,
                adminCommissionPct = adminCommissionPct,
                maxTicketsPerUser  = maxTicketsPerUser,
                durationSeconds    = durationSeconds,
            ).fold(
                onSuccess = { snapshot ->
                    _uiState.update { it.copy(isLoading = false, game = snapshot.game) }
                    _events.emit(GamePotEvent.NavigateToRoom)
                },
                onFailure = { e ->
                    val message = friendly(e, "Failed to create game")
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                    _events.emit(GamePotEvent.ShowError(message))
                }
            )
        }
    }

    fun activateGame(gameId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.activateGame(gameId).fold(
                onSuccess = { snapshot ->
                    _uiState.update { it.copy(isLoading = false, game = snapshot.game) }
                    restartCountdown(snapshot.game)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = friendly(e, "Failed to start game")) }
                }
            )
        }
    }

    fun drawWinner(gameId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDrawing = true, errorMessage = null) }
            repo.drawWinner(gameId).fold(
                onSuccess = { winner ->
                    val channelId = _uiState.value.game?.channelId ?: winner.channelId
                    _uiState.update {
                        it.copy(
                            isDrawing        = false,
                            winner           = winner,
                            showWinnerOverlay = true,
                        )
                    }
                    postWinnerAnnouncement(channelId, winner)
                    _events.emit(GamePotEvent.WinnerDrawn(winner))
                },
                onFailure = { e ->
                    val message = friendly(e, "Draw failed")
                    _uiState.update { it.copy(isDrawing = false, errorMessage = message) }
                    _events.emit(GamePotEvent.ShowError(message))
                }
            )
        }
    }

    fun cancelGame(gameId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.cancelGame(gameId).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, game = null) }
                    stopDetailPolling()
                    _events.emit(GamePotEvent.GameCancelled)
                },
                onFailure = { e ->
                    val message = friendly(e, "Cancel failed")
                    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                    _events.emit(GamePotEvent.ShowError(message))
                }
            )
        }
    }

    fun dismissWinnerOverlay() {
        _uiState.update { it.copy(showWinnerOverlay = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun friendly(error: Throwable, fallback: String): String =
        CloudflareClient.friendlyErrorMessage(error, fallback)

    private suspend fun postWinnerAnnouncement(channelId: String, winner: GameWinner) {
        if (channelId.isBlank()) return
        val winnerName = winner.winnerName.ifBlank { "Winner" }
        val message = buildString {
            appendLine("Game Pot Winner")
            appendLine("Winner: $winnerName")
            appendLine("Ticket No: #${winner.ticketNumber}")
            append("Win Amount: ETB ${"%.2f".format(winner.payoutAmount)}")
        }
        chatRepo.sendCommunityMessage(channelId, message)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun onGameUpdated(snapshot: GameSnapshot) {
        val game = snapshot.game
        _uiState.update {
            it.copy(
                game      = game,
                myTickets = snapshot.myTickets,
            )
        }
        startDetailPolling(game.id)
        restartCountdown(game)

        // Auto-show winner overlay when game transitions to completed
        if (game.isCompleted && game.winnerUid != null && _uiState.value.winner == null) {
            _uiState.update {
                it.copy(
                    winner            = GameWinner(
                        id              = "",
                        gameId          = game.id,
                        channelId       = game.channelId,
                        winnerUid       = game.winnerUid ?: "",
                        winnerName      = game.winnerName ?: "",
                        ticketNumber    = game.winnerTicketNumber ?: 0,
                        jackpotAmount   = game.jackpotAmount,
                        adminCommission = game.adminCommissionAmount ?: 0.0,
                        payoutAmount    = game.winnerPayout ?: 0.0,
                        payoutReference = "",
                        drawnAt         = game.updatedAt,
                    ),
                    showWinnerOverlay = true,
                )
            }
        }
    }

    private fun startDetailPolling(gameId: String) {
        // Avoid restarting if already polling this game
        if (ticketJob?.isActive == true) return

        ticketJob?.cancel()
        ticketJob = viewModelScope.launch {
            repo.ticketsFlow(gameId).collect { tickets ->
                _uiState.update { it.copy(tickets = tickets) }
            }
        }

        participantJob?.cancel()
        participantJob = viewModelScope.launch {
            repo.participantsFlow(gameId).collect { participants ->
                _uiState.update { it.copy(participants = participants) }
            }
        }
    }

    private fun stopDetailPolling() {
        ticketJob?.cancel();       ticketJob = null
        participantJob?.cancel();  participantJob = null
        countdownJob?.cancel();    countdownJob = null
    }

    private fun restartCountdown(game: ChannelGame) {
        countdownJob?.cancel()
        if (!game.isLive) return
        countdownJob = viewModelScope.launch {
            while (true) {
                val ms = game.msRemaining()
                _uiState.update { it.copy(msRemaining = ms) }
                if (ms != null && ms <= 0L) {
                    // Timer expired — trigger auto-draw (server also auto-draws on next poll,
                    // but calling here gives instant feedback in the UI).
                    if (game.isActive) {
                        launch { safeAutoDraw(game.id) }
                    }
                    break
                }
                delay(1_000)
            }
        }
    }

    /** Fire-and-forget draw that suppresses errors (server may have already drawn). */
    private suspend fun safeAutoDraw(gameId: String) {
        if (_uiState.value.isDrawing) return   // already in progress
        _uiState.update { it.copy(isDrawing = true) }
        repo.drawWinner(gameId).fold(
            onSuccess = { winner ->
                val channelId = _uiState.value.game?.channelId ?: winner.channelId
                _uiState.update {
                    it.copy(
                        isDrawing         = false,
                        winner            = winner,
                        showWinnerOverlay = true,
                    )
                }
                postWinnerAnnouncement(channelId, winner)
                _events.emit(GamePotEvent.WinnerDrawn(winner))
            },
            onFailure = {
                // Server may have already drawn or game had no tickets — ignore
                _uiState.update { it.copy(isDrawing = false) }
            },
        )
    }

    override fun onCleared() {
        super.onCleared()
        activeGameJob?.cancel()
        stopDetailPolling()
    }
}
