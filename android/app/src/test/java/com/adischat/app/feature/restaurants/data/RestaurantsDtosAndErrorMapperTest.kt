package com.adischat.app.feature.restaurants.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RestaurantsDtosAndErrorMapperTest {
    private val gson = Gson()

    @Test
    fun `parses exchange session response matching R3B backend contract`() {
        val json = """
            {
              "ok": true,
              "profile_id": "prof-123",
              "session": {
                "access_token": "acc-xyz",
                "refresh_token": "ref-xyz",
                "expires_at": 1893456000,
                "user_id": "usr-456"
              }
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RestaurantsExchangeSessionResponse::class.java)
        assertTrue(parsed.ok)
        assertEquals("prof-123", parsed.profileId)
        assertNotNull(parsed.session)
        assertEquals("acc-xyz", parsed.session?.accessToken)
        assertEquals("ref-xyz", parsed.session?.refreshToken)
        assertEquals(1893456000L, parsed.session?.expiresAt)
        assertEquals("usr-456", parsed.session?.userId)
    }

    @Test
    fun `parses resolve qr and tables list responses`() {
        val qrJson = """
            {
              "ok": true,
              "restaurant": { "id": "r-1", "name": "Addis Bistro" },
              "branch": { "id": "b-1", "name": "Bole Main" },
              "table": { "id": "t-1", "label": "Table 4" },
              "dining_session_id": "ds-99",
              "expires_at": "2026-11-10T16:00:00Z"
            }
        """.trimIndent()
        val qr = gson.fromJson(qrJson, ResolveQrResponse::class.java)
        assertTrue(qr.ok)
        assertEquals("ds-99", qr.diningSessionId)
        assertEquals("Table 4", qr.table?.label)

        val tablesJson = """
            {
              "ok": true,
              "tables": [
                {
                  "id": "t-1",
                  "restaurant_id": "r-1",
                  "branch_id": "b-1",
                  "table_code": "T04",
                  "label": "Table 4",
                  "zone": "Patio",
                  "capacity": 4,
                  "is_active": true,
                  "active_qr_version": 2,
                  "active_qr_status": "ACTIVE",
                  "open_session": {
                    "id": "ds-99",
                    "expires_at": "2026-11-10T16:00:00Z",
                    "has_session_code": true
                  }
                }
              ]
            }
        """.trimIndent()
        val tables = gson.fromJson(tablesJson, ListTablesResponse::class.java)
        assertEquals(1, tables.tables.size)
        assertEquals(2, tables.tables.first().activeQrVersion)
        assertTrue(tables.tables.first().openSession?.hasSessionCode == true)
    }

    @Test
    fun `serializes dine-in order request and parses response`() {
        val req = SubmitDineInOrderRequest(
            diningSessionId = "ds-1",
            branchId = "br-1",
            idempotencyKey = "idem-001",
            items = listOf(
                SubmitOrderItemRequest(
                    menuItemId = "item-1",
                    quantity = 2,
                    optionIds = listOf("opt-a"),
                    specialInstructions = "No onions",
                ),
            ),
        )
        val json = gson.toJson(req)
        assertTrue(json.contains("\"order_type\":\"DINE_IN\""))
        assertTrue(json.contains("\"payment_method\":\"PAY_AT_RESTAURANT\""))
        assertTrue(json.contains("\"idempotency_key\":\"idem-001\""))

        val resJson = """
            {
              "ok": true,
              "order": {
                "order_id": "ord-1",
                "order_number": "ORD-1001",
                "status": "SUBMITTED",
                "payment_method": "PAY_AT_RESTAURANT",
                "payment_status": "UNPAID",
                "total_minor": 45000,
                "idempotent_replay": false
              }
            }
        """.trimIndent()
        val res = gson.fromJson(resJson, SubmitOrderResponse::class.java)
        assertEquals("ord-1", res.order?.orderId)
        assertEquals("PAY_AT_RESTAURANT", res.order?.paymentMethod)
        assertFalse(res.order?.idempotentReplay == true)
    }

    @Test
    fun `maps backend error codes and redacts sensitive postgres or jwt strings`() {
        val sessionCodeErr = RestaurantsErrorMapper.mapHttpStatusAndBody(
            403,
            """{"ok":false,"error":{"code":"session_code_required","message":"Session code needed"}}""",
        )
        assertTrue(sessionCodeErr is RestaurantsFailure.Validation)
        assertEquals("session_code_required", sessionCodeErr.code)

        val leakErr = RestaurantsErrorMapper.mapHttpStatusAndBody(
            500,
            """{"ok":false,"error":{"code":"db_fail","message":"postgres RLS policy violation on jwt token relation orders"}}""",
        )
        assertTrue(leakErr is RestaurantsFailure.Server)
        assertFalse(leakErr.userMessage.contains("postgres", ignoreCase = true))
        assertFalse(leakErr.userMessage.contains("jwt", ignoreCase = true))
        assertEquals("Restaurants service is temporarily unavailable.", leakErr.userMessage)

        val http401 = HttpException(
            Response.error<String>(
                401,
                """{"ok":false,"error":{"code":"assertion_replayed","message":"Replay detected"}}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val mapped401 = RestaurantsErrorMapper.mapThrowable(http401)
        assertTrue(mapped401 is RestaurantsFailure.Unauthenticated)
        assertEquals("assertion_replayed", mapped401.code)

        val qaBlocked = RestaurantsErrorMapper.mapThrowable(IOException("QA blocked mutation"))
        assertTrue(qaBlocked is RestaurantsFailure.QaBlocked)
    }

    @Test
    fun `parses PostgREST SQL snake_case fields matching R2B and R3B schema`() {
        val restaurant = gson.fromJson(
            """{"id":"r1","slug":"hearth","name":"Habesha Hearth","status":"active","is_live_ordering_enabled":true}""",
            RestaurantSummaryDto::class.java,
        )
        assertEquals("Habesha Hearth", restaurant.name)
        assertEquals("active", restaurant.status)

        val branch = gson.fromJson(
            """{"id":"b1","restaurant_id":"r1","code":"BOLE","name":"Bole","address":"Cape Verde","status":"suspended","is_dine_in_active":false}""",
            RestaurantBranchDto::class.java,
        )
        assertFalse(branch.isActive)

        val orderItem = gson.fromJson(
            """{"id":"oi1","order_id":"o1","menu_item_id":"m1","item_name_snapshot":"Tibs","unit_price_minor_snapshot":68000,"quantity":2,"line_total_minor":136000,"options_delta_minor_snapshot":5000}""",
            OrderItemRecordDto::class.java,
        )
        assertEquals(68000L, orderItem.unitPriceMinorSnapshot)
        assertEquals(5000L, orderItem.optionsDeltaMinorSnapshot)

        val payment = gson.fromJson(
            """{"id":"p1","order_id":"o1","payment_method":"PAY_AT_RESTAURANT","status":"PAID","amount_minor":136000,"receipt_ref":"R-9"}""",
            OrderPaymentRecordDto::class.java,
        )
        assertEquals("PAY_AT_RESTAURANT", payment.paymentMethod)
        assertEquals("PAID", payment.status)
    }

    @Test
    fun `serializes StaffInviteRequest with AdisChat User ID, profile ID, and optional phone`() {
        val uidInvite = StaffInviteRequest(
            restaurantId = "r-1",
            branchId = "b-1",
            role = "WAITER",
            invitedIdentifier = "fb-email-waiter-1",
            invitedAdischatUserId = "fb-email-waiter-1",
        )
        val uidJson = gson.toJson(uidInvite)
        assertTrue(uidJson.contains("\"invited_adischat_user_id\":\"fb-email-waiter-1\""))
        assertTrue(uidJson.contains("\"invited_identifier\":\"fb-email-waiter-1\""))
        assertFalse(uidJson.contains("invited_phone"))

        val phoneInvite = StaffInviteRequest(
            restaurantId = "r-1",
            branchId = "b-1",
            role = "CASHIER",
            invitedIdentifier = "+251911223344",
            invitedPhone = "+251911223344",
        )
        val phoneJson = gson.toJson(phoneInvite)
        assertTrue(phoneJson.contains("\"invited_phone\":\"+251911223344\""))
        assertFalse(phoneJson.contains("invited_adischat_user_id"))

        val profileInvite = StaffInviteRequest(
            restaurantId = "r-1",
            branchId = "b-1",
            role = "KITCHEN_STAFF",
            invitedIdentifier = "33330000-0000-4000-8000-000000000003",
            invitedProfileId = "33330000-0000-4000-8000-000000000003",
        )
        val profileJson = gson.toJson(profileInvite)
        assertTrue(profileJson.contains("\"invited_profile_id\":\"33330000-0000-4000-8000-000000000003\""))
    }
}
