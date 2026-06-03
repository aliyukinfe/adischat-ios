package com.adischat.app.network

import com.adischat.app.data.model.AdminStats
import com.adischat.app.data.model.AdminVerificationRequest
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.model.Conversation
import com.adischat.app.data.model.CommunityRoom
import com.adischat.app.data.model.Message
import com.adischat.app.data.model.UserSummary
import com.adischat.app.data.model.VerificationStatus
import okhttp3.RequestBody
import retrofit2.http.*

interface CloudflareApi {

    // ── Public auth (no token required) ──────────────────────────────────────

    @POST("api/auth/send-email-otp")
    suspend fun sendEmailOtp(@Body body: SendEmailOtpBody): SendEmailOtpResponse

    @POST("api/auth/verify-email-otp")
    suspend fun verifyEmailOtp(@Body body: VerifyEmailOtpBody): VerifyEmailOtpResponse

    @POST("api/auth/lookup")
    suspend fun lookupAccount(@Body body: LookupBody): LookupResponse

    // ── Users ─────────────────────────────────────────────────────────────────

    @POST("api/users/sync")
    suspend fun syncUser(@Body body: UserSyncBody): AppUser

    @GET("api/users/me")
    suspend fun getMe(): AppUser

    @PUT("api/users/{uid}")
    suspend fun updateUser(@Path("uid") uid: String, @Body body: UserUpdateBody): AppUser

    @POST("api/users/{uid}/avatar")
    @Headers("Content-Type: image/jpeg")
    suspend fun uploadAvatar(@Path("uid") uid: String, @Body bytes: RequestBody): AvatarResponse

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserSummary>

    // ── Media upload ──────────────────────────────────────────────────────────

    @POST("api/upload")
    suspend fun uploadMedia(@Query("type") type: String, @Body body: RequestBody): UploadResponse

    // ── Conversations / Messages ──────────────────────────────────────────────

    @POST("api/conversations/start/{targetUid}")
    suspend fun startConversation(@Path("targetUid") targetUid: String): StartConversationResponse

    @GET("api/conversations")
    suspend fun getConversations(): List<Conversation>

    @GET("api/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): List<Message>

    @POST("api/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body body: SendMessageBody): Message

    @PUT("api/conversations/{id}/read")
    suspend fun markRead(@Path("id") conversationId: String)

    // ── Verification ──────────────────────────────────────────────────────────

    @GET("api/verification/status")
    suspend fun getVerificationStatus(): VerificationStatus

    @POST("api/verification/submit")
    suspend fun submitVerification(@Body body: VerificationSubmitBody): GenericResponse

    // ── Admin verification management ─────────────────────────────────────────

    @GET("api/admin/verifications")
    suspend fun getAdminVerifications(@Query("status") status: String = "pending"): List<AdminVerificationRequest>

    @POST("api/admin/verifications/{uid}/approve")
    suspend fun approveVerification(@Path("uid") uid: String): GenericResponse

    @POST("api/admin/verifications/{uid}/reject")
    suspend fun rejectVerification(@Path("uid") uid: String, @Body body: RejectBody): GenericResponse

    // ── Wallet (in-app-browser merchant pay) ──────────────────────────────────
    // Lightweight, fingerprint-only flow used by the in-app browser bridge.
    // The full-featured wallet (PIN, QR pay codes, send/receive) is on the
    // separate WalletApi → adischat-wallet-api worker.

    @GET("api/wallet/balance")
    suspend fun getInAppWalletBalance(): InAppWalletBalanceResponse

    @POST("api/wallet/pay")
    suspend fun inAppWalletPay(@Body body: InAppWalletPayBody): InAppWalletPayResponse

    // ── Admin: stats + user management ───────────────────────────────────────

    @GET("api/admin/stats")
    suspend fun getAdminStats(): AdminStats

    @GET("api/admin/users")
    suspend fun getAdminUsers(
        @Query("q")    query: String = "",
        @Query("page") page: Int    = 0,
    ): List<AppUser>

    @PUT("api/admin/users/{uid}/role")
    suspend fun setUserRole(@Path("uid") uid: String, @Body body: SetRoleBody): GenericResponse

    @POST("api/notifications/register-token")
    suspend fun registerNotificationToken(@Body body: NotificationTokenBody): GenericResponse
}

interface WalletApi {
    @GET("api/wallet/me")
    suspend fun getWallet(): WalletResponse

    @POST("api/wallet/activate")
    suspend fun activateWallet(@Body body: WalletActivateBody): WalletResponse

    @POST("api/wallet/change-pin")
    suspend fun changeWalletPin(@Body body: WalletChangePinBody): WalletResponse

    @GET("api/wallet/receive")
    suspend fun receiveWallet(): WalletQrResponse

    @GET("api/wallet/username-qr")
    suspend fun usernameQr(): UsernameQrResponse

    @GET("api/wallet/pay-code")
    suspend fun payCode(): WalletPayCodeResponse

    @GET("api/wallet/transactions")
    suspend fun getWalletTransactions(@Query("limit") limit: Int = 50): List<WalletTransactionResponse>

    @POST("api/wallet/deposit")
    suspend fun depositWallet(@Body body: WalletAmountBody): WalletActionResponse

    @POST("api/wallet/send")
    suspend fun sendMoney(@Body body: WalletSendBody): WalletActionResponse

    @POST("api/merchant/pay-code/redeem")
    suspend fun redeemMerchantPayCode(@Body body: MerchantPayRedeemBody): WalletActionResponse

    @POST("api/admin/wallets/{uid}/adjust")
    suspend fun adminAdjustWallet(@Path("uid") uid: String, @Body body: WalletAmountBody): WalletActionResponse

    @POST("api/notifications/register-token")
    suspend fun registerNotificationToken(@Body body: NotificationTokenBody): GenericResponse

    @GET("api/communities")
    suspend fun getCommunities(
        @Query("type") type: String,
        @Query("q") query: String = "",
    ): List<CommunityRoom>

    @POST("api/communities")
    suspend fun createCommunity(@Body body: CreateCommunityBody): CommunityRoom

    @PUT("api/communities/{id}")
    suspend fun updateCommunity(@Path("id") id: String, @Body body: UpdateCommunityBody): CommunityRoom

    @DELETE("api/communities/{id}")
    suspend fun deleteCommunity(@Path("id") id: String): GenericResponse

    @POST("api/communities/{id}/join")
    suspend fun joinCommunity(@Path("id") id: String): GenericResponse

    @GET("api/communities/{id}/messages")
    suspend fun getCommunityMessages(@Path("id") id: String): List<Message>

    @POST("api/communities/{id}/messages")
    suspend fun sendCommunityMessage(@Path("id") id: String, @Body body: SendMessageBody): Message

    @DELETE("api/communities/{id}/messages/{messageId}")
    suspend fun deleteCommunityMessage(@Path("id") id: String, @Path("messageId") messageId: String): GenericResponse

    @POST("api/communities/{id}/messages/{messageId}/reaction")
    suspend fun reactToCommunityMessage(
        @Path("id") id: String,
        @Path("messageId") messageId: String,
        @Body body: MessageReactionBody,
    ): GenericResponse

    @POST("api/communities/{id}/members/{uid}/{action}")
    suspend fun moderateCommunityMember(
        @Path("id") id: String,
        @Path("uid") uid: String,
        @Path("action") action: String,
    ): GenericResponse

    @GET("api/games/{channelId}/approval")
    suspend fun getGameApproval(@Path("channelId") channelId: String): GameApprovalResponse

    @POST("api/games/{channelId}/approval")
    suspend fun requestGameApproval(
        @Path("channelId") channelId: String,
        @Body body: GameApprovalRequestBody,
    ): GameApprovalResponse

    @GET("api/games/admin/approvals")
    suspend fun getGameApprovalRequests(@Query("status") status: String = "pending"): List<GameApprovalResponse>

    @POST("api/games/admin/approvals/{channelId}/approve")
    suspend fun approveGameApproval(@Path("channelId") channelId: String, @Body body: GameApprovalDecisionBody): GameApprovalResponse

    @POST("api/games/admin/approvals/{channelId}/reject")
    suspend fun rejectGameApproval(@Path("channelId") channelId: String, @Body body: GameApprovalDecisionBody): GameApprovalResponse

    // ── Channel verification (blue badge) — same worker as channel posts ────────

    @GET("api/channels/{channelId}/verification")
    suspend fun getChannelVerification(@Path("channelId") channelId: String): ChannelVerificationResponse

    @POST("api/channels/{channelId}/verification")
    suspend fun requestChannelVerification(
        @Path("channelId") channelId: String,
        @Body body: ChannelVerificationRequest,
    ): ChannelVerificationResponse

    @POST("api/channels/{channelId}/gifts")
    suspend fun sendChannelGift(
        @Path("channelId") channelId: String,
        @Body body: ChannelGiftBody,
    ): ChannelGiftResponse

    @GET("api/channels/{channelId}/gifts/balance")
    suspend fun getChannelGiftBalance(@Path("channelId") channelId: String): ChannelGiftBalanceResponse

    @POST("api/channels/{channelId}/gifts/convert")
    suspend fun convertChannelGifts(
        @Path("channelId") channelId: String,
        @Body body: ChannelGiftConvertBody,
    ): ChannelGiftBalanceResponse

    @GET("api/admin/channel-verifications")
    suspend fun getChannelVerificationRequests(@Query("status") status: String = "pending"): List<ChannelVerificationResponse>

    @POST("api/admin/channel-verifications/{channelId}/approve")
    suspend fun approveChannelVerification(@Path("channelId") channelId: String): ChannelVerificationResponse

    @POST("api/admin/channel-verifications/{channelId}/reject")
    suspend fun rejectChannelVerification(
        @Path("channelId") channelId: String,
        @Body body: RejectBody,
    ): ChannelVerificationResponse

    // ── Channel post feed ─────────────────────────────────────────────────────

    @GET("api/channels/{channelId}/posts")
    suspend fun getChannelPosts(
        @Path("channelId") channelId: String,
        @Query("before")   before:    String? = null,
        @Query("limit")    limit:     Int     = 20,
    ): ChannelPostsPageResponse

    @POST("api/channels/{channelId}/posts")
    suspend fun createChannelPost(
        @Path("channelId") channelId: String,
        @Body body: CreateChannelPostBody,
    ): ChannelPostResponse

    @PATCH("api/channels/{channelId}/posts/{postId}")
    suspend fun editChannelPost(
        @Path("channelId") channelId: String,
        @Path("postId")    postId:    String,
        @Body body: EditChannelPostBody,
    ): ChannelPostResponse

    @DELETE("api/channels/{channelId}/posts/{postId}")
    suspend fun deleteChannelPost(
        @Path("channelId") channelId: String,
        @Path("postId")    postId:    String,
    ): GenericResponse

    @POST("api/channels/{channelId}/posts/{postId}/pin")
    suspend fun toggleChannelPostPin(
        @Path("channelId") channelId: String,
        @Path("postId")    postId:    String,
    ): GenericResponse

    @POST("api/channels/{channelId}/posts/{postId}/react")
    suspend fun reactToChannelPost(
        @Path("channelId") channelId: String,
        @Path("postId")    postId:    String,
        @Body body: ChannelPostReactBody,
    ): GenericResponse

    @POST("api/channels/{channelId}/posts/{postId}/view")
    suspend fun recordChannelPostView(
        @Path("channelId") channelId: String,
        @Path("postId")    postId:    String,
    ): GenericResponse
}

// ── Request / response bodies ─────────────────────────────────────────────────

data class SendEmailOtpBody(val email: String)
data class SendEmailOtpResponse(val sent: Boolean, val devOtp: String? = null)

data class ChannelGiftBody(
    val emoji: String,
    val coins: Int,
    val pin: String = "",
)

data class ChannelGiftConvertBody(val coins: Int)

data class ChannelGiftResponse(
    val reference: String = "",
    val channelId: String = "",
    val emoji: String = "",
    val coins: Int = 0,
    val etb: Double = 0.0,
    val balance: Double = 0.0,
)

data class ChannelGiftBalanceResponse(
    val channelId: String = "",
    val coins: Int = 0,
    val convertedCoins: Int = 0,
    val etbValue: Double = 0.0,
    val grossEtb: Double = 0.0,
    val ownerEtb: Double = 0.0,
    val adminCommissionEtb: Double = 0.0,
    val adminCommissionCoins: Int = 0,
    val remainingCoins: Int = 0,
)

data class VerifyEmailOtpBody(val email: String, val otp: String)
data class VerifyEmailOtpResponse(val verified: Boolean)

data class LookupBody(val identifier: String)
data class LookupResponse(val email: String)

data class UserSyncBody(
    val name: String, val username: String, val phone: String,
    val email: String, val dob: String, val photoUrl: String,
    val role: String, val emailVerified: Boolean = false,
)

data class UserUpdateBody(
    val name: String, val username: String, val phone: String,
    val email: String, val dob: String,
)

data class AvatarResponse(val photoUrl: String)
data class StartConversationResponse(val conversationId: String)
data class UploadResponse(val url: String)

data class SendMessageBody(
    val text:                     String = "",
    val type:                     String = "text",
    val mediaUrl:                 String = "",
    val mediaMimeType:            String = "",
    val durationMs:               Long   = 0,
    val conversationUserName:     String = "",
    val conversationUserPhotoUrl: String = "",
)

data class VerificationSubmitBody(
    val fullName:     String,
    val documentType: String,
    val documentUrl:  String,
    val selfieUrl:    String,
)

data class RejectBody(val reason: String)

data class ChannelVerificationRequest(val note: String = "", val channelName: String = "")
data class ChannelVerificationResponse(
    val channelId:   String  = "",
    val channelName: String  = "",
    val ownerUid:    String  = "",
    val note:        String  = "",
    val status:      String  = "none",   // none | pending | approved | rejected
    val adminNote:   String  = "",
    val createdAt:   Long    = 0L,
    val updatedAt:   Long    = 0L,
)
data class SetRoleBody(val role: String)

// ── In-app-browser wallet bodies ──────────────────────────────────────────────
data class InAppWalletBalanceResponse(
    val balance:  Double = 0.0,
    val currency: String = "ETB",
)
data class InAppWalletPayBody(
    val merchantUid: String,
    val amount:      Double,
    val currency:    String = "ETB",
    val orderRef:    String = "",
    val description: String = "",
)
data class InAppWalletPayResponse(
    val ok:            Boolean = false,
    val transactionId: String  = "",
    val amount:        Double  = 0.0,
    val currency:      String  = "ETB",
    val orderRef:      String  = "",
    val description:   String  = "",
    val balanceAfter:  Double  = 0.0,
    val createdAt:     Long    = 0L,
    val duplicate:     Boolean = false,
)
data class NotificationTokenBody(
    val token: String,
    val platform: String = "android",
)

data class UpdateCommunityBody(
    val name: String,
    val description: String = "",
    val photoUrl: String = "",
)

data class GenericResponse(
    val submitted: Boolean? = null,
    val approved:  Boolean? = null,
    val rejected:  Boolean? = null,
)

data class WalletResponse(
    val uid: String = "",
    val userId: String = "",
    val walletId: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val name: String = "",
    val balance: Double = 0.0,
    val currency: String = "ETB",
    val active: Boolean = false,
    val frozen: Boolean = false,
    val walletPinSet: Boolean = false,
    val activatedAt: Long? = null,
)

data class WalletChangePinBody(
    val oldPin: String,
    val newPin: String,
)

data class WalletQrResponse(
    val uid: String = "",
    val userId: String = "",
    val walletId: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val name: String = "",
    val balance: Double = 0.0,
    val currency: String = "ETB",
    val active: Boolean = false,
    val frozen: Boolean = false,
    val walletPinSet: Boolean = false,
    val qrPayload: String = "",
)

data class UsernameQrResponse(
    val username: String = "",
    val phone: String = "",
    val qrPayload: String = "",
)

data class WalletPayCodeResponse(
    val code: String = "",
    val formattedCode: String = "",
    val walletId: String = "",
    val username: String = "",
    val name: String = "",
    val expiresAt: Long = 0,
    val qrPayload: String = "",
    val barcodePayload: String = "",
)

data class WalletTransactionResponse(
    val id: String = "",
    val wallet_id: String = "",
    val counterparty_wallet_id: String? = null,
    val counterparty_uid: String? = null,
    val counterparty_name: String? = null,
    val counterparty_username: String? = null,
    val type: String = "",
    val status: String = "",
    val amount_cents: Long = 0,
    val amount: Double = 0.0,
    val currency: String = "ETB",
    val reference: String = "",
    val note: String = "",
    val created_at: Long = 0,
)

data class WalletActivateBody(
    val username: String,
    val phone: String,
    val pin: String = "",
)

data class WalletAmountBody(
    val amount: Double,
    val reference: String? = null,
    val note: String = "",
)

data class WalletSendBody(
    val recipient: String,
    val amount: Double,
    val note: String = "",
    val pin: String = "",
)

data class MerchantPayRedeemBody(
    val code: String,
    val amount: Double,
    val note: String = "Merchant scan payment",
)

data class CreateCommunityBody(
    val type: String,
    val name: String,
    val description: String = "",
    val photoUrl: String = "",
)

data class MessageReactionBody(val emoji: String)

data class GameApprovalRequestBody(val description: String)
data class GameApprovalDecisionBody(val note: String = "")
data class GameApprovalResponse(
    val id: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val description: String = "",
    val status: String = "none",
    val adminUid: String? = null,
    val adminNote: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

// ── Game Pot API ──────────────────────────────────────────────────────────────
// All game-pot endpoints live on the wallet worker (adischat-wallet-api).

interface GamePotApi {

    /** Create a new game (admin only). */
    @POST("api/games/{channelId}/create")
    suspend fun createGame(
        @Path("channelId") channelId: String,
        @Body body: CreateGameBody,
    ): GameSnapshotResponse

    /** Activate a pending game and start the countdown. */
    @POST("api/games/{gameId}/activate")
    suspend fun activateGame(@Path("gameId") gameId: String): GameSnapshotResponse

    /** Buy one or more tickets. */
    @POST("api/games/{gameId}/buy")
    suspend fun buyTickets(
        @Path("gameId") gameId: String,
        @Body body: BuyTicketsBody,
    ): BuyTicketsResponse

    /** Admin manually triggers the draw. */
    @POST("api/games/{gameId}/draw")
    suspend fun drawWinner(@Path("gameId") gameId: String): DrawResultResponse

    /** Admin cancels and issues full refunds. */
    @POST("api/games/{gameId}/cancel")
    suspend fun cancelGame(@Path("gameId") gameId: String): CancelGameResponse

    /** Get the live active game for a channel + the caller's tickets. */
    @GET("api/games/{channelId}/active")
    suspend fun getActiveGame(@Path("channelId") channelId: String): GameSnapshotResponse

    /** Full game detail + caller's tickets. */
    @GET("api/games/{gameId}/detail")
    suspend fun getGameDetail(@Path("gameId") gameId: String): GameSnapshotResponse

    /** All ticket rows for a game (used to render the grid). */
    @GET("api/games/{gameId}/tickets")
    suspend fun getGameTickets(@Path("gameId") gameId: String): List<GameTicketResponse>

    /** Top participants list. */
    @GET("api/games/{gameId}/participants")
    suspend fun getParticipants(@Path("gameId") gameId: String): List<ParticipantResponse>

    /** Completed/cancelled games for a channel. */
    @GET("api/games/{channelId}/history")
    suspend fun getGameHistory(@Path("channelId") channelId: String): List<GameResponse>
}

// ── Game Pot request bodies ───────────────────────────────────────────────────

data class CreateGameBody(
    val title:               String,
    val totalTickets:        Int,
    val pricePerTicket:      Double,
    val adminCommissionPct:  Double = 5.0,
    val maxTicketsPerUser:   Int?   = null,
    val durationSeconds:     Long,
)

data class BuyTicketsBody(
    val ticketNumbers: List<Int>,
    val walletPin:     String = "",
)

// ── Game Pot response DTOs ────────────────────────────────────────────────────

data class GameResponse(
    val id:                    String  = "",
    val channelId:             String  = "",
    val creatorUid:            String  = "",
    val title:                 String  = "",
    val totalTickets:          Int     = 0,
    val pricePerTicket:        Double  = 0.0,
    val adminCommissionPct:    Double  = 5.0,
    val maxTicketsPerUser:     Int?    = null,
    val durationSeconds:       Long    = 1800,
    val startsAt:              Long?   = null,
    val endsAt:                Long?   = null,
    val status:                String  = "pending",
    val jackpotAmount:         Double  = 0.0,
    val ticketsSold:           Int     = 0,
    val totalTicketCount:      Int     = 0,
    val winnerTicketNumber:    Int?    = null,
    val winnerUid:             String? = null,
    val winnerName:            String? = null,
    val winnerPayout:          Double? = null,
    val adminCommissionAmount: Double? = null,
    val createdAt:             Long    = 0L,
    val updatedAt:             Long    = 0L,
)

data class GameSnapshotResponse(
    val game:      GameResponse? = null,
    val myTickets: List<Int>     = emptyList(),
)

data class GameTicketResponse(
    val ticketNumber: Int     = 0,
    val status:       String  = "available",
    val ownerUid:     String? = null,
    val ownerName:    String? = null,
    val purchasedAt:  Long?   = null,
    val isMine:       Boolean = false,       // set by server based on caller uid
)

data class ParticipantResponse(
    val uid:          String = "",
    val name:         String = "",
    val photoUrl:     String = "",
    val ticketsCount: Int    = 0,
    val totalSpent:   Double = 0.0,
    val joinedAt:     Long   = 0L,
)

data class BuyTicketsResponse(
    val game:      GameResponse = GameResponse(),
    val myTickets: List<Int>    = emptyList(),
    val reference: String       = "",
    val cost:      Double       = 0.0,
)

data class DrawResultResponse(
    val game:               GameResponse = GameResponse(),
    val winnerTicketNumber: Int?         = null,
    val winnerName:         String?      = null,
    val payoutAmount:       Double       = 0.0,
    val commissionAmount:   Double       = 0.0,
    val seed:               String       = "",
)

data class CancelGameResponse(
    val cancelled:     Boolean = false,
    val refundsIssued: Int     = 0,
)

data class WalletActionResponse(
    val reference: String = "",
    val balance: Double = 0.0,
    val sent: Double? = null,
    val deposited: Double? = null,
    val amount: Double? = null,
    val payerWalletId: String? = null,
    val merchantWalletId: String? = null,
)

// ── Channel Post DTOs ─────────────────────────────────────────────────────────

data class ChannelPostResponse(
    val id:            String               = "",
    val channelId:     String               = "",
    val authorUid:     String               = "",
    val authorName:    String               = "",
    val authorPhotoUrl: String              = "",
    val title:         String               = "",
    val body:          String               = "",
    val postType:      String               = "text",
    val mediaUrl:      String               = "",
    val mediaMimeType: String               = "",
    val linkUrl:       String               = "",
    val linkTitle:     String               = "",
    val linkImage:     String               = "",
    val pinned:        Boolean              = false,
    val views:         Int                  = 0,
    val reactions:     Map<String, String>  = emptyMap(),
    val createdAt:     Long                 = 0L,
    val updatedAt:     Long                 = 0L,
    val deleted:       Boolean              = false,
)

data class ChannelPostsPageResponse(
    val posts:      List<ChannelPostResponse> = emptyList(),
    val hasMore:    Boolean                   = false,
    val nextCursor: String?                   = null,
)

data class CreateChannelPostBody(
    val title:         String = "",
    val body:          String = "",
    val postType:      String = "text",
    val mediaUrl:      String = "",
    val mediaMimeType: String = "",
    val linkUrl:       String = "",
    val linkTitle:     String = "",
    val linkImage:     String = "",
)

data class EditChannelPostBody(
    val title:    String? = null,
    val body:     String? = null,
    val linkUrl:  String? = null,
    val linkTitle: String? = null,
)

data class ChannelPostReactBody(val emoji: String)
