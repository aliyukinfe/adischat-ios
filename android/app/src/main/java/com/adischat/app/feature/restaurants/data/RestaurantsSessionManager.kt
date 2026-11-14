package com.adischat.app.feature.restaurants.data

import com.google.firebase.auth.FirebaseAuth
import retrofit2.HttpException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * In-memory authenticated session state for AdisChat Restaurants.
 */
data class RestaurantsSession(
    val ownerAdisChatUid: String,
    val profileId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long? = null,
    val userId: String? = null,
) {
    fun isExpiredOrExpiringSoon(
        nowEpochSeconds: Long,
        leewaySeconds: Long = 60L,
    ): Boolean {
        val exp = expiresAtEpochSeconds ?: return false
        return nowEpochSeconds + leewaySeconds >= exp
    }
}

/**
 * Optional persistence abstraction (no Android Context dependency in core session logic).
 */
interface RestaurantsSessionStore {
    fun load(): RestaurantsSession?
    fun save(session: RestaurantsSession)
    fun clear()
}

class InMemoryRestaurantsSessionStore : RestaurantsSessionStore {
    @Volatile
    private var stored: RestaurantsSession? = null

    override fun load(): RestaurantsSession? = stored

    override fun save(session: RestaurantsSession) {
        stored = session
    }

    override fun clear() {
        stored = null
    }
}

/**
 * Process-wide generation-guarded memory cache for the Restaurants Supabase session.
 * Hooked directly into [com.adischat.app.security.SessionSecurity] sign-out cleanup so
 * logout or account switch immediately revokes cached credentials.
 */
object RestaurantsSessionMemory {
    private val lock = Any()
    private var cachedSession: RestaurantsSession? = null
    private var sessionGeneration: Long = 0L
    private var signingOut: Boolean = false
    private var optionalStore: RestaurantsSessionStore? = null

    fun attachStore(store: RestaurantsSessionStore?) {
        synchronized(lock) {
            optionalStore = store
        }
    }

    fun snapshot(): RestaurantsSession? = synchronized(lock) {
        if (signingOut) null else cachedSession
    }

    fun generation(): Long = synchronized(lock) { sessionGeneration }

    fun isSigningOut(): Boolean = synchronized(lock) { signingOut }

    fun setSessionIfCurrent(session: RestaurantsSession, expectedGeneration: Long): Boolean =
        synchronized(lock) {
            if (signingOut || sessionGeneration != expectedGeneration) {
                return@synchronized false
            }
            cachedSession = session
            optionalStore?.save(session)
            true
        }

    fun clear() {
        synchronized(lock) {
            cachedSession = null
            sessionGeneration += 1L
            optionalStore?.clear()
        }
    }

    /**
     * Ensures cached Restaurants tokens do not leak across AdisChat Firebase UID switches or sign-out.
     */
    fun reconcileAdisChatUid(currentAdisChatUid: String?): Boolean = synchronized(lock) {
        val existing = cachedSession ?: optionalStore?.load()?.also { loaded ->
            if (!signingOut) cachedSession = loaded
        }
        if (currentAdisChatUid.isNullOrBlank()) {
            if (existing != null) {
                cachedSession = null
                sessionGeneration += 1L
                optionalStore?.clear()
            }
            return@synchronized false
        }
        if (existing != null && existing.ownerAdisChatUid != currentAdisChatUid) {
            cachedSession = null
            sessionGeneration += 1L
            optionalStore?.clear()
            return@synchronized true
        }
        false
    }

    fun beginSignOut(): RestaurantsSession? = synchronized(lock) {
        signingOut = true
        val prev = cachedSession
        cachedSession = null
        sessionGeneration += 1L
        optionalStore?.clear()
        prev
    }

    fun finishSignOut() {
        synchronized(lock) {
            cachedSession = null
            sessionGeneration += 1L
            signingOut = false
            optionalStore?.clear()
        }
    }
}

interface FirebaseIdentityProvider {
    fun currentUid(): String?
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}

class DefaultFirebaseIdentityProvider(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
) : FirebaseIdentityProvider {
    override fun currentUid(): String? = try {
        authProvider().currentUser?.uid
    } catch (_: Exception) {
        null
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        val user = try {
            authProvider().currentUser
        } catch (_: Exception) {
            null
        } ?: return null

        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result?.token)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
    }
}

fun interface RestaurantsIdentityBridgeClient {
    suspend fun requestAssertion(firebaseIdToken: String): String
}

class RetrofitRestaurantsIdentityBridgeClient(
    private val api: RestaurantsIdentityBridgeApi,
) : RestaurantsIdentityBridgeClient {
    override suspend fun requestAssertion(firebaseIdToken: String): String {
        val response = api.requestSessionAssertion("Bearer $firebaseIdToken")
        val assertion = response.assertion?.trim().orEmpty()
        if (!response.ok || assertion.isEmpty()) {
            throw RestaurantsFailure.Unauthenticated(
                "missing_assertion",
                "Unable to obtain Restaurants identity assertion.",
            )
        }
        return assertion
    }
}

fun interface EpochClock {
    fun nowEpochSeconds(): Long
}

/**
 * Coordinates Restaurants Supabase session lifecycle:
 * - Acquires Firebase ID token from AdisChat
 * - Requests Restaurants-scoped assertion from Cloudflare bridge (disabled until R4+)
 * - Exchanges assertion for Restaurants Supabase Auth session via `restaurants-exchange-session`
 * - Refreshes session via GoTrue (`refresh_token`)
 * - Serializes concurrent refresh/exchange requests via a single-flight [Mutex]
 * - Clears session cleanly on AdisChat logout or UID switch
 */
class RestaurantsSessionManager(
    private val environmentProvider: () -> RestaurantsEnvironment = { RestaurantsConfig.currentEnvironment },
    private val firebaseIdentityProvider: FirebaseIdentityProvider = DefaultFirebaseIdentityProvider(),
    private val identityBridgeClientProvider: () -> RestaurantsIdentityBridgeClient? = { null },
    private val edgeApiProvider: () -> RestaurantsEdgeApi? = {
        RestaurantsApiClientFactory.createEdgeApi(environmentProvider())
    },
    private val goTrueApiProvider: () -> RestaurantsGoTrueApi? = {
        RestaurantsApiClientFactory.createGoTrueApi(environmentProvider())
    },
    private val memory: RestaurantsSessionMemory = RestaurantsSessionMemory,
    private val clock: EpochClock = EpochClock { System.currentTimeMillis() / 1000L },
) {
    private val refreshMutex = Mutex()

    fun currentUidAndGeneration(): Pair<String?, Long> {
        val uid = firebaseIdentityProvider.currentUid()?.takeIf { it.isNotBlank() }
        memory.reconcileAdisChatUid(uid)
        return uid to memory.generation()
    }

    /**
     * Returns a valid bearer token string (`Bearer <access_token>`) or throws a [RestaurantsFailure].
     */
    suspend fun getValidBearerToken(forceRefresh: Boolean = false): String {
        val session = getValidSession(forceRefresh)
        return "Bearer ${session.accessToken}"
    }

    /**
     * Returns a valid [RestaurantsSession], performing at most one concurrent refresh/exchange
     * across coroutines thanks to [refreshMutex] + generation checks.
     */
    suspend fun getValidSession(forceRefresh: Boolean = false): RestaurantsSession {
        val env = environmentProvider()
        if (!env.isIdentityBridgeEnabled) {
            throw RestaurantsFailure.FeatureDisabled(
                "AdisChat Restaurants identity exchange is disabled in this build.",
            )
        }
        if (!env.isBackendConfigured()) {
            throw RestaurantsFailure.NotConfigured(
                "AdisChat Restaurants backend configuration is not set.",
            )
        }
        if (memory.isSigningOut()) {
            throw RestaurantsFailure.Unauthenticated(
                "signing_out",
                "Sign-out is in progress.",
            )
        }

        val currentUid = firebaseIdentityProvider.currentUid()
        memory.reconcileAdisChatUid(currentUid)
        val uid = currentUid?.takeIf { it.isNotBlank() }
            ?: throw RestaurantsFailure.Unauthenticated(
                "not_signed_in",
                "Please sign in to AdisChat first.",
            )

        val now = clock.nowEpochSeconds()
        if (!forceRefresh) {
            val cached = memory.snapshot()
            if (cached != null &&
                cached.ownerAdisChatUid == uid &&
                !cached.isExpiredOrExpiringSoon(now)
            ) {
                return cached
            }
        }

        return refreshMutex.withLock {
            if (memory.isSigningOut()) {
                throw RestaurantsFailure.Unauthenticated("signing_out", "Sign-out is in progress.")
            }
            val lockedUid = firebaseIdentityProvider.currentUid()
            memory.reconcileAdisChatUid(lockedUid)
            if (lockedUid.isNullOrBlank() || lockedUid != uid) {
                throw RestaurantsFailure.Unauthenticated(
                    "not_signed_in",
                    "Please sign in to AdisChat first.",
                )
            }

            val startGeneration = memory.generation()
            val latestCached = memory.snapshot()
            val lockedNow = clock.nowEpochSeconds()

            // Re-check inside lock: another coroutine may have already refreshed the session.
            if (!forceRefresh &&
                latestCached != null &&
                latestCached.ownerAdisChatUid == lockedUid &&
                !latestCached.isExpiredOrExpiringSoon(lockedNow)
            ) {
                return@withLock latestCached
            }

            val refreshed = if (latestCached != null &&
                latestCached.ownerAdisChatUid == lockedUid &&
                latestCached.refreshToken.isNotBlank()
            ) {
                tryRefreshWithGoTrue(latestCached, lockedUid, lockedNow)
            } else {
                null
            }

            val resolved = refreshed ?: performFullIdentityExchange(
                uid = lockedUid,
                forceFirebaseRefresh = forceRefresh,
            )

            // Guard against race conditions where logout or UID switch occurred mid-flight.
            val postUid = firebaseIdentityProvider.currentUid()
            if (postUid != lockedUid || !memory.setSessionIfCurrent(resolved, startGeneration)) {
                throw RestaurantsFailure.Unauthenticated(
                    "session_invalidated",
                    "Account session changed during authentication. Please try again.",
                )
            }
            resolved
        }
    }

    /**
     * Best-effort backend logout plus immediate local credential wipe.
     */
    suspend fun logout() {
        val existing = memory.beginSignOut()
        try {
            val env = environmentProvider()
            val edgeApi = edgeApiProvider()
            if (env.isIdentityBridgeEnabled &&
                env.isBackendConfigured() &&
                existing != null &&
                existing.accessToken.isNotBlank() &&
                edgeApi != null
            ) {
                try {
                    edgeApi.logoutSession("Bearer ${existing.accessToken}")
                } catch (_: Exception) {
                    // Best-effort revocation; local wipe still guarantees cleanup.
                }
            }
        } finally {
            memory.finishSignOut()
        }
    }

    /**
     * Synchronous helper for account-switch detection before emitting network calls.
     */
    fun clearIfAccountChanged(currentAdisChatUid: String?) {
        memory.reconcileAdisChatUid(currentAdisChatUid)
    }

    private suspend fun tryRefreshWithGoTrue(
        current: RestaurantsSession,
        uid: String,
        nowEpochSeconds: Long,
    ): RestaurantsSession? {
        val goTrueApi = goTrueApiProvider() ?: return null
        return try {
            val res = goTrueApi.refreshSession(GoTrueRefreshRequest(current.refreshToken))
            val newAccess = res.accessToken?.trim().orEmpty()
            val newRefresh = res.refreshToken?.trim().orEmpty().ifEmpty { current.refreshToken }
            if (newAccess.isEmpty()) return null
            val computedExpiresAt = res.expiresAt
                ?: res.expiresIn?.let { nowEpochSeconds + it }
            RestaurantsSession(
                ownerAdisChatUid = uid,
                profileId = current.profileId,
                accessToken = newAccess,
                refreshToken = newRefresh,
                expiresAtEpochSeconds = computedExpiresAt,
                userId = res.user?.id ?: current.userId,
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun performFullIdentityExchange(
        uid: String,
        forceFirebaseRefresh: Boolean = false,
    ): RestaurantsSession {
        val env = environmentProvider()
        val bridgeClient = identityBridgeClientProvider()
            ?: RestaurantsApiClientFactory.createIdentityBridgeApi(env)
                ?.let { RetrofitRestaurantsIdentityBridgeClient(it) }
        if (!env.isIdentityBridgeConfigured() && bridgeClient == null) {
            throw RestaurantsFailure.FeatureDisabled(
                "Restaurants identity bridge URL is not configured or enabled.",
            )
        }
        val activeBridge = bridgeClient ?: throw RestaurantsFailure.FeatureDisabled(
            "Restaurants identity bridge is disabled.",
        )

        val edgeApi = edgeApiProvider()
            ?: throw RestaurantsFailure.NotConfigured("Restaurants Edge API client is unavailable.")

        val refreshAttempts = if (forceFirebaseRefresh) listOf(true) else listOf(false, true)
        val assertion = run {
            for ((index, attemptForceRefresh) in refreshAttempts.withIndex()) {
                val firebaseToken = firebaseIdentityProvider.getIdToken(forceRefresh = attemptForceRefresh)
                    ?.takeIf { it.isNotBlank() }
                    ?: throw RestaurantsFailure.Unauthenticated(
                        "missing_firebase_token",
                        "Please sign in to AdisChat again.",
                    )
                try {
                    return@run activeBridge.requestAssertion(firebaseToken)
                } catch (t: Throwable) {
                    val hasRetryRemaining = index + 1 < refreshAttempts.size
                    val isUnauthenticated = (t as? HttpException)?.code() == 401 ||
                        t is RestaurantsFailure.Unauthenticated
                    if (!hasRetryRemaining || !isUnauthenticated) {
                        throw RestaurantsErrorMapper.mapThrowable(t)
                    }
                }
            }
            error("Unreachable")
        }

        val exchangeRes = try {
            edgeApi.exchangeSession(
                body = RestaurantsExchangeSessionRequest(assertion = assertion),
            )
        } catch (t: Throwable) {
            throw RestaurantsErrorMapper.mapThrowable(t)
        }

        val dto = exchangeRes.session
        val profileId = exchangeRes.profileId?.trim().orEmpty()
        if (!exchangeRes.ok || dto == null || dto.accessToken.isBlank() || profileId.isEmpty()) {
            throw RestaurantsFailure.Unauthenticated(
                "invalid_exchange_response",
                "Unable to establish Restaurants session.",
            )
        }

        return RestaurantsSession(
            ownerAdisChatUid = uid,
            profileId = profileId,
            accessToken = dto.accessToken,
            refreshToken = dto.refreshToken,
            expiresAtEpochSeconds = dto.expiresAt,
            userId = dto.userId,
        )
    }

    fun clearMemoryBlocking() {
        runBlocking {
            memory.clear()
        }
    }
}
