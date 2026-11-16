package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adischat.app.security.SessionSecurity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
class ShopSessionRevocationDeviceTest {
    @Test
    fun switchedAccountUsesItsOwnShopIdentity() = runBlocking {
        val expectedEmail = InstrumentationRegistry.getArguments().getString("expectedEmail").orEmpty()
        assertTrue("expectedEmail instrumentation argument is required", expectedEmail.isNotBlank())
        val firebaseUser = requireNotNull(FirebaseAuth.getInstance().currentUser)
        assertEquals(expectedEmail.lowercase(), firebaseUser.email?.lowercase())

        val bearer = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        assertEquals(firebaseUser.uid, ShopSessionMemory.profile()?.adisChatUserId)
        val api = requireNotNull(ShopFunctionClient.api)
        assertTrue(api.notifications(bearer, "buyer", null).ok)
        assertTrue(api.notificationUnreadCount(bearer, "buyer", null).ok)
        assertTrue(api.cart(bearer).ok)
    }

    @Test
    fun capturedBearerIsRejectedAfterRealLogout() = runBlocking {
        val expectedEmail = InstrumentationRegistry.getArguments().getString("expectedEmail").orEmpty()
        assertTrue("expectedEmail instrumentation argument is required", expectedEmail.isNotBlank())
        assertEquals(expectedEmail.lowercase(), FirebaseAuth.getInstance().currentUser?.email?.lowercase())

        val api = requireNotNull(ShopFunctionClient.api)
        val bearer = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val capturedSession = ShopSessionMemory.session()
        assertNotNull(capturedSession)

        val protectedReads: List<Pair<String, suspend () -> Unit>> = listOf(
            "notifications" to { api.notifications(bearer, "buyer", null) },
            "unread count" to { api.notificationUnreadCount(bearer, "buyer", null) },
            "cart" to { api.cart(bearer) },
            "favorites" to { api.favorites(bearer) },
            "addresses" to { api.addresses(bearer) },
            "orders" to { api.orders(bearer) },
            "seller status" to { api.sellerStatus(bearer) },
        )
        protectedReads.forEach { (_, request) ->
            request()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Server revocation must be confirmed before this test can pass", SessionSecurity.hardSignOut(context))
        assertEquals(null, FirebaseAuth.getInstance().currentUser)
        assertEquals(null, ShopSessionMemory.session())

        protectedReads.forEach { (name, request) ->
            var failure: Throwable? = null
            for (attempt in 0..2) {
                failure = runCatching { request() }.exceptionOrNull()
                if (failure !is HttpException || (failure as HttpException).code() != 503) break
                if (attempt < 2) delay(500)
            }
            assertTrue("$name must reject the captured bearer", failure is HttpException)
            assertEquals("$name must return 401", 401, (failure as HttpException).code())
        }

        assertTrue("Repeated family revocation must be idempotent", ShopIdentityRepository().revokeCapturedSession(capturedSession))
    }
}
