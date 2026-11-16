package com.adischat.app.feature.shop.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException

@RunWith(AndroidJUnit4::class)
class StoreBNotificationDeviceTest {
    @Test
    fun controlledBuyerCannotReadStoreBSellerNotification() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val expectedBuyerProfileId = args.getString("expectedBuyerProfileId").orEmpty()
        val storeB = args.getString("storeB").orEmpty()
        assertTrue("Controlled buyer and Store B IDs are required", expectedBuyerProfileId.isNotBlank() && storeB.isNotBlank())

        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        assertEquals("Sign in as the controlled buyer before running this test", expectedBuyerProfileId, api.currentProfile(authorization).profile?.id)
        try {
            api.notifications(authorization, "seller", storeB)
            fail("A non-owner must not read Store B seller notifications")
        } catch (error: HttpException) {
            assertTrue(error.code() == 403 || error.code() == 404)
        }
        assertTrue(api.notifications(authorization, "buyer", null).notifications.orEmpty().none {
            it.body?.contains("V5.5 controlled QA check") == true
        })
    }

    @Test
    fun controlledBuyerMessageCreatesStoreBNotification() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val expectedBuyerProfileId = args.getString("expectedBuyerProfileId").orEmpty()
        val storeB = args.getString("storeB").orEmpty()
        assertTrue("Controlled buyer and Store B IDs are required", expectedBuyerProfileId.isNotBlank() && storeB.isNotBlank())

        val authorization = "Bearer ${ShopIdentityRepository().sessionToken().getOrThrow()}"
        val api = requireNotNull(ShopFunctionClient.api)
        assertEquals("Sign in as the controlled buyer before running this test", expectedBuyerProfileId, api.currentProfile(authorization).profile?.id)

        val conversation = requireNotNull(api.openConversation(authorization, ShopOpenConversationRequest(storeId = storeB)).conversation)
        assertEquals(storeB, conversation.storeId)
        assertEquals(expectedBuyerProfileId, conversation.customerProfileId)

        val message = requireNotNull(api.sendMessage(
            authorization,
            ShopSendMessageRequest(
                conversationId = conversation.id,
                body = "V5.5 controlled QA check: Store B notification navigation. No reply needed.",
                clientMessageId = "v55-storeb-notification-20261027",
            ),
        ).message)
        assertEquals(conversation.id, message.conversationId)
    }
}
