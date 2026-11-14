package com.adischat.app.feature.restaurants.data

import com.adischat.app.ui.scan.RestaurantCenterQrParser
import com.adischat.app.ui.scan.ScanResult
import com.adischat.app.ui.scan.resolveMainScanResult
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantsPhaseB4AContractTest {
    private val gson = Gson()

    @Test
    fun `parses valid Restaurant Center desktop login QR payload`() {
        // Canonical schema with version: "1.0" as emitted by desktop buildQrDisplayPayload
        val canonicalPayload = """
            {
              "version": "1.0",
              "type": "adischat_restaurant_center_login",
              "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "challenge_nonce": "qr_non_8f3a9e2c1d0b",
              "timestamp": "${java.time.Instant.now()}"
            }
        """.trimIndent()

        val parsed = RestaurantCenterQrParser.parse(canonicalPayload)
        assertNotNull(parsed)
        assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", parsed?.challengeId)
        assertEquals("qr_non_8f3a9e2c1d0b", parsed?.challengeNonce)

        // Also accepts v: "1.0" for backwards/forwards compatibility
        val shortVPayload = """
            {
              "v": "1.0",
              "type": "adischat_restaurant_center_login",
              "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "challenge_nonce": "qr_non_8f3a9e2c1d0b",
              "timestamp": "${java.time.Instant.now()}"
            }
        """.trimIndent()
        val parsedV = RestaurantCenterQrParser.parse(shortVPayload)
        assertNotNull(parsedV)
        assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", parsedV?.challengeId)
    }

    @Test
    fun `rejects malformed, spoofed, or expired Restaurant Center QR codes`() {
        // Plain text
        assertNull(RestaurantCenterQrParser.parse("Hello World"))

        // Standard URL
        assertNull(RestaurantCenterQrParser.parse("https://example.com/login"))

        // Wrong type
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "1.0", "type": "other_login", "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "challenge_nonce": "qr_non_12345678"}"""
            )
        )

        // Unsupported schema version
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "2.0", "type": "adischat_restaurant_center_login", "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "challenge_nonce": "qr_non_12345678"}"""
            )
        )

        // Invalid challenge_id format (not UUID)
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "1.0", "type": "adischat_restaurant_center_login", "challenge_id": "not-a-uuid", "challenge_nonce": "qr_non_12345678"}"""
            )
        )

        // Missing challenge_nonce
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "1.0", "type": "adischat_restaurant_center_login", "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "challenge_nonce": ""}"""
            )
        )

        // Expired timestamp (10 minutes ago)
        val expiredTime = java.time.Instant.now().minusSeconds(600).toString()
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "1.0", "type": "adischat_restaurant_center_login", "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "challenge_nonce": "qr_non_12345678", "timestamp": "$expiredTime"}"""
            )
        )

        // Future timestamp (>60s in the future - clock desync / spoofing)
        val futureTime = java.time.Instant.now().plusSeconds(300).toString()
        assertNull(
            RestaurantCenterQrParser.parse(
                """{"version": "1.0", "type": "adischat_restaurant_center_login", "challenge_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "challenge_nonce": "qr_non_12345678", "timestamp": "$futureTime"}"""
            )
        )
    }

    @Test
    fun `resolveMainScanResult maps Restaurant Center login payload to RestaurantCenterLogin scan result`() {
        val payload = """
            {
              "type": "adischat_restaurant_center_login",
              "challenge_id": "11111111-2222-4333-8444-555555555555",
              "challenge_nonce": "qr_non_validnonce12345",
              "timestamp": "${java.time.Instant.now()}"
            }
        """.trimIndent()

        val result = resolveMainScanResult(payload, restaurantsDineInEnabled = true)
        assertTrue(result is ScanResult.RestaurantCenterLogin)
        val loginResult = result as ScanResult.RestaurantCenterLogin
        assertEquals("11111111-2222-4333-8444-555555555555", loginResult.challengeId)
        assertEquals("qr_non_validnonce12345", loginResult.challengeNonce)
    }

    @Test
    fun `resolveMainScanResult preserves generic scanning for other QR codes`() {
        val urlResult = resolveMainScanResult("https://google.com", restaurantsDineInEnabled = true)
        assertTrue(urlResult is ScanResult.Found)
        assertEquals("url", (urlResult as ScanResult.Found).type)

        val textResult = resolveMainScanResult("WIFI:S:MyNet;P:secret;;", restaurantsDineInEnabled = true)
        assertTrue(textResult is ScanResult.Found)
        assertEquals("wifi", (textResult as ScanResult.Found).type)
    }

    @Test
    fun `serializes AuthorizeQrChallengeRequest and deserializes response correctly`() {
        val req = AuthorizeQrChallengeRequest(
            challengeId = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            challengeNonce = "qr_non_8f3a9e2c1d0b",
        )
        val reqJson = gson.toJson(req)
        assertTrue(reqJson.contains(""""challenge_id":"3fa85f64-5717-4562-b3fc-2c963f66afa6""""))
        assertTrue(reqJson.contains(""""challenge_nonce":"qr_non_8f3a9e2c1d0b""""))

        val respJson = """
            {
              "ok": true,
              "authorized": true,
              "role": "RESTAURANT_OWNER"
            }
        """.trimIndent()
        val resp = gson.fromJson(respJson, AuthorizeQrChallengeResponse::class.java)
        assertTrue(resp.ok)
        assertTrue(resp.authorized)
        assertEquals("RESTAURANT_OWNER", resp.role)
    }

    @Test
    fun `serializes and deserializes WebView handoff ticket requests and responses`() {
        val ticketJson = """
            {
              "ok": true,
              "ticket_id": "tkt-uuid-1",
              "ticket": "hnd_abc123xyz789mockticket0000000000",
              "expires_at": "2026-11-14T12:00:30.000Z"
            }
        """.trimIndent()
        val ticketResp = gson.fromJson(ticketJson, CreateHandoffTicketResponse::class.java)
        assertTrue(ticketResp.ok)
        assertEquals("tkt-uuid-1", ticketResp.ticketId)
        assertEquals("hnd_abc123xyz789mockticket0000000000", ticketResp.ticket)
        assertEquals("2026-11-14T12:00:30.000Z", ticketResp.expiresAt)

        val consumeReq = ConsumeHandoffTicketRequest(ticket = "hnd_abc123xyz789mockticket0000000000")
        val consumeReqJson = gson.toJson(consumeReq)
        assertTrue(consumeReqJson.contains(""""ticket":"hnd_abc123xyz789mockticket0000000000""""))
    }

    @Test
    fun `RestaurantsEnvironment generates valid handoffAuthUrl`() {
        val env = RestaurantsEnvironment(restaurantCenterWebUrl = "https://adischat-restaurants-web.vercel.app")
        assertEquals(
            "https://adischat-restaurants-web.vercel.app/api/auth/handoff",
            env.handoffAuthUrl(),
        )

        val customEnv = RestaurantsEnvironment(restaurantCenterWebUrl = "http://10.0.2.2:3000/")
        assertEquals(
            "http://10.0.2.2:3000/api/auth/handoff",
            customEnv.handoffAuthUrl(),
        )
    }

    @Test
    fun `RestaurantCenterEligibility accurately verifies Owner and Manager roles`() {
        val ownerEligibility = RestaurantCenterEligibility(
            isEligible = true,
            role = "RESTAURANT_OWNER",
            restaurantId = "rest-1",
        )
        assertTrue(ownerEligibility.isEligible)
        assertEquals("RESTAURANT_OWNER", ownerEligibility.role)

        val managerEligibility = RestaurantCenterEligibility(
            isEligible = true,
            role = "RESTAURANT_MANAGER",
            restaurantId = "rest-1",
            branchId = "branch-1",
        )
        assertTrue(managerEligibility.isEligible)
        assertEquals("RESTAURANT_MANAGER", managerEligibility.role)

        val floorEligibility = RestaurantCenterEligibility(
            isEligible = false,
        )
        assertFalse(floorEligibility.isEligible)
        assertNull(floorEligibility.role)
    }
}
