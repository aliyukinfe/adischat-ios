package com.adischat.app.feature.restaurants.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class RestaurantsSessionManagerTest {
    private lateinit var store: InMemoryRestaurantsSessionStore

    @Before
    fun setUp() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
        store = InMemoryRestaurantsSessionStore()
        RestaurantsSessionMemory.attachStore(store)
    }

    @After
    fun tearDown() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
        RestaurantsSessionMemory.attachStore(null)
    }

    @Test
    fun `disabled flags match R3C safe defaults and block live exchange`() = runBlocking {
        assertEquals(false, RestaurantsConfig.isDineInEnabled())
        assertEquals(false, RestaurantsConfig.isRestaurantCenterEnabled())
        assertEquals(false, RestaurantsConfig.isAdminEnabled())
        assertEquals(false, RestaurantsConfig.isIdentityBridgeEnabled())

        val firebaseCalls = AtomicInteger(0)
        val manager = RestaurantsSessionManager(
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://restaurants-test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge-test.example",
                    isIdentityBridgeEnabled = false,
                )
            },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String = "uid-1"
                override suspend fun getIdToken(forceRefresh: Boolean): String {
                    firebaseCalls.incrementAndGet()
                    return "fb-token"
                }
            },
        )

        val failure = runCatching { manager.getValidSession() }.exceptionOrNull()
        assertTrue(failure is RestaurantsFailure.FeatureDisabled)
        assertEquals(0, firebaseCalls.get())
    }

    @Test
    fun `concurrent callers share single-flight identity exchange without duplicate network calls`() = runBlocking {
        val bridgeCalls = AtomicInteger(0)
        val exchangeCalls = AtomicInteger(0)
        val fakeEdge = object : StubRestaurantsEdgeApi() {
            override suspend fun exchangeSession(
                action: String,
                body: RestaurantsExchangeSessionRequest,
            ): RestaurantsExchangeSessionResponse {
                exchangeCalls.incrementAndGet()
                delay(40)
                return RestaurantsExchangeSessionResponse(
                    ok = true,
                    profileId = "prof-1",
                    session = RestaurantsAuthSessionDto(
                        accessToken = "access-1",
                        refreshToken = "refresh-1",
                        expiresAt = 2000L,
                        userId = "supa-u-1",
                    ),
                )
            }
        }

        val manager = RestaurantsSessionManager(
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://restaurants-test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge-test.example",
                    isIdentityBridgeEnabled = true,
                )
            },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String = "uid-1"
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token-1"
            },
            identityBridgeClientProvider = {
                RestaurantsIdentityBridgeClient {
                    bridgeCalls.incrementAndGet()
                    delay(30)
                    "assertion-jwt-1"
                }
            },
            edgeApiProvider = { fakeEdge },
            goTrueApiProvider = { null },
            clock = EpochClock { 1000L },
        )

        val sessions = (1..10).map {
            async { manager.getValidSession() }
        }.awaitAll()

        assertEquals(1, bridgeCalls.get())
        assertEquals(1, exchangeCalls.get())
        sessions.forEach { session ->
            assertEquals("access-1", session.accessToken)
            assertEquals("uid-1", session.ownerAdisChatUid)
        }
        assertNotNull(store.load())
    }

    @Test
    fun `expiring session refreshes once via GoTrue and preserves profile`() = runBlocking {
        var nowSeconds = 1000L
        val refreshCalls = AtomicInteger(0)
        val bridgeCalls = AtomicInteger(0)

        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = "uid-1",
                profileId = "prof-1",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtEpochSeconds = 1020L, // within 60s leeway -> expiring soon
                userId = "supa-1",
            ),
            RestaurantsSessionMemory.generation(),
        )

        val fakeGoTrue = object : RestaurantsGoTrueApi {
            override suspend fun refreshSession(body: GoTrueRefreshRequest): GoTrueRefreshResponse {
                refreshCalls.incrementAndGet()
                assertEquals("old-refresh", body.refreshToken)
                delay(30)
                return GoTrueRefreshResponse(
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                    expiresIn = 3600L,
                    user = GoTrueUserDto("supa-1"),
                )
            }
        }

        val manager = RestaurantsSessionManager(
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://restaurants-test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge-test.example",
                    isIdentityBridgeEnabled = true,
                )
            },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String = "uid-1"
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token"
            },
            identityBridgeClientProvider = {
                RestaurantsIdentityBridgeClient {
                    bridgeCalls.incrementAndGet()
                    "should-not-run"
                }
            },
            edgeApiProvider = { StubRestaurantsEdgeApi() },
            goTrueApiProvider = { fakeGoTrue },
            clock = EpochClock { nowSeconds },
        )

        val sessions = (1..6).map { async { manager.getValidSession() } }.awaitAll()
        assertEquals(1, refreshCalls.get())
        assertEquals(0, bridgeCalls.get())
        assertEquals("new-access", sessions.first().accessToken)
        assertEquals("prof-1", sessions.first().profileId)
    }

    @Test
    fun `logout and account switch immediately wipe cached restaurants credentials`() = runBlocking {
        var currentUid: String? = "uid-A"
        val logoutApiCalls = AtomicInteger(0)

        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = "uid-A",
                profileId = "prof-A",
                accessToken = "token-A",
                refreshToken = "refresh-A",
                expiresAtEpochSeconds = 5000L,
            ),
            RestaurantsSessionMemory.generation(),
        )

        val fakeEdge = object : StubRestaurantsEdgeApi() {
            override suspend fun logoutSession(
                bearerAuth: String,
                action: String,
                body: Map<String, String>,
            ): RestaurantsOkResponse {
                logoutApiCalls.incrementAndGet()
                assertEquals("Bearer token-A", bearerAuth)
                return RestaurantsOkResponse(ok = true)
            }
        }

        val manager = RestaurantsSessionManager(
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://restaurants-test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge-test.example",
                    isIdentityBridgeEnabled = true,
                )
            },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String? = currentUid
                override suspend fun getIdToken(forceRefresh: Boolean): String? =
                    currentUid?.let { "fb-$it" }
            },
            edgeApiProvider = { fakeEdge },
            clock = EpochClock { 1000L },
        )

        // Account switch from uid-A to uid-B invalidates old session before any request.
        currentUid = "uid-B"
        manager.clearIfAccountChanged(currentUid)
        assertNull(RestaurantsSessionMemory.snapshot())
        assertNull(store.load())

        // Re-seed session for uid-B and test logout() + SessionSecurity hooks.
        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = "uid-A",
                profileId = "prof-A",
                accessToken = "token-A",
                refreshToken = "refresh-A",
                expiresAtEpochSeconds = 5000L,
            ),
            RestaurantsSessionMemory.generation(),
        )
        manager.logout()
        assertEquals(1, logoutApiCalls.get())
        assertNull(RestaurantsSessionMemory.snapshot())
        assertNull(store.load())
    }

    @Test
    fun `stale firebase token on bridge 401 refreshes firebase token and propagates forceRefresh`() = runBlocking {
        val requestedForceFlags = mutableListOf<Boolean>()
        val bridgeTokens = mutableListOf<String>()
        val fakeEdge = object : StubRestaurantsEdgeApi() {
            override suspend fun exchangeSession(
                action: String,
                body: RestaurantsExchangeSessionRequest,
            ): RestaurantsExchangeSessionResponse {
                return RestaurantsExchangeSessionResponse(
                    ok = true,
                    profileId = "prof-1",
                    session = RestaurantsAuthSessionDto(
                        accessToken = "access-${body.assertion}",
                        refreshToken = "refresh-1",
                        expiresAt = 2000L,
                        userId = "supa-u-1",
                    ),
                )
            }
        }

        val manager = RestaurantsSessionManager(
            environmentProvider = {
                RestaurantsEnvironment(
                    supabaseUrl = "https://restaurants-test.supabase.co",
                    supabasePublishableKey = "pk_test",
                    identityBridgeUrl = "https://bridge-test.example",
                    isIdentityBridgeEnabled = true,
                )
            },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String = "uid-1"
                override suspend fun getIdToken(forceRefresh: Boolean): String {
                    requestedForceFlags += forceRefresh
                    return if (forceRefresh) "fb-fresh" else "fb-stale"
                }
            },
            identityBridgeClientProvider = {
                RestaurantsIdentityBridgeClient { token ->
                    bridgeTokens += token
                    if (token == "fb-stale") {
                        throw HttpException(
                            Response.error<String>(
                                401,
                                """{"error":"firebase_token_expired"}""".toResponseBody("application/json".toMediaType()),
                            ),
                        )
                    }
                    "assertion-$token"
                }
            },
            edgeApiProvider = { fakeEdge },
            clock = EpochClock { 1000L },
        )

        val recovered = manager.getValidSession()
        assertEquals(listOf(false, true), requestedForceFlags)
        assertEquals(listOf("fb-stale", "fb-fresh"), bridgeTokens)
        assertEquals("access-assertion-fb-fresh", recovered.accessToken)

        // Direct forceRefresh = true skips initial non-forced token fetch when exchanging fresh session.
        RestaurantsSessionMemory.clear()
        requestedForceFlags.clear()
        bridgeTokens.clear()

        val forced = manager.getValidSession(forceRefresh = true)
        assertEquals(listOf(true), requestedForceFlags)
        assertEquals(listOf("fb-fresh"), bridgeTokens)
        assertEquals("access-assertion-fb-fresh", forced.accessToken)
    }
}
