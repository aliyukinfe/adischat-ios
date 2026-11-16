package com.adischat.app.data.model

// - Core game record -

data class ChannelGame(
    val id:      String  = "",
    val channelId:      String  = "",
    val creatorUid:      String  = "",
    val title:      String  = "",
    val totalTickets:      Int     = 0,
    val pricePerTicket:      Double  = 0.0,
    val adminCommissionPct:    Double  = 5.0,
    val maxTicketsPerUser:     Int?    = null,
    val durationSeconds:       Long    = 1800,
    val startsAt:      Long?   = null,
    val endsAt:      Long?   = null,
    val status:      String  = "pending",
    // pending | active | drawing | completed | cancelled
    val jackpotAmount:      Double  = 0.0,
    val ticketsSold:      Int     = 0,
    val totalTicketCount:      Int     = 0,
    val winnerTicketNumber:    Int?    = null,
    val winnerUid:      String? = null,
    val winnerName:      String? = null,
    val winnerPayout:      Double? = null,
    val adminCommissionAmount: Double? = null,
    val createdAt:      Long    = 0L,
    val updatedAt:      Long    = 0L,
) {
    val isActive:    Boolean get() = status == "active"
    val isPending:   Boolean get() = status == "pending"
    val isDrawing:   Boolean get() = status == "drawing"
    val isCompleted: Boolean get() = status == "completed"
    val isCancelled: Boolean get() = status == "cancelled"
    val isLive:      Boolean get() = status == "active" || status == "drawing"

    val soldPct: Float
        get() = if (totalTickets == 0) 0f
        else ticketsSold.toFloat() / totalTickets.toFloat()

    val remainingTickets: Int
        get() = totalTickets - ticketsSold

    /** Milliseconds remaining until timer expires; null if not started. */
    fun msRemaining(nowMs: Long = System.currentTimeMillis()): Long? =
        endsAt?.let { (it - nowMs).coerceAtLeast(0) }
}

// - Ticket -

data class GameTicket(
    val ticketNumber: Int    = 0,
    val status:       String = "available",  // available | sold | won
    val ownerUid:     String? = null,
    val ownerName:    String? = null,
    val purchasedAt:  Long?   = null,
    val isMine:       Boolean = false,
)

// - Participant -

data class GameParticipant(
    val uid:      String = "",
    val name:      String = "",
    val photoUrl:     String = "",
    val ticketsCount: Int    = 0,
    val totalSpent:   Double = 0.0,
    val joinedAt:     Long   = 0L,
)

// - Winner record -

data class GameWinner(
    val id:      String = "",
    val gameId:      String = "",
    val channelId:      String = "",
    val winnerUid:      String = "",
    val winnerName:       String = "",
    val ticketNumber:     Int    = 0,
    val jackpotAmount:    Double = 0.0,
    val adminCommission:  Double = 0.0,
    val payoutAmount:     Double = 0.0,
    val payoutReference:  String = "",
    val drawnAt:      Long   = 0L,
)

// - Game snapshot (game + user context) -

data class GameSnapshot(
    val game:      ChannelGame,
    val myTickets: List<Int> = emptyList(),
)

// - Buy result -

data class TicketPurchaseResult(
    val game:      ChannelGame,
    val myTickets: List<Int>,
    val reference: String,
    val cost:      Double,
)
