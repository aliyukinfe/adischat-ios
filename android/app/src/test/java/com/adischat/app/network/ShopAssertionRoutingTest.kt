package com.adischat.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShopAssertionRoutingTest {
    private val production = "https://adischat-api.adischat.workers.dev/"
    private val qa = "https://adischat-qa-shop-issuer.adischat.workers.dev/"
    private val valid = QaShopAssertionConfig(
        endpoint = qa,
        shopUrl = "https://opkqyvttbyezjbqwkvho.supabase.co",
        firebaseProject = "adischat-qa",
        applicationId = "com.adischat.app.qa",
        issuer = "adischat-staging-opkqyvttbyezjbqwkvho",
        audience = "adishop-staging-edge",
    )

    @Test fun productionKeepsExistingRoute() {
        assertEquals(production, shopAssertionBaseUrl(false, null, production))
        assertEquals(production, shopAssertionBaseUrl(false, valid, production))
    }

    @Test fun qaUsesOnlyDedicatedIssuer() {
        assertEquals(qa, shopAssertionBaseUrl(true, valid.copy(endpoint = qa.dropLast(1)), production))
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, null, production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(endpoint = ""), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(endpoint = production), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(endpoint = "http://adischat-qa-shop-issuer.adischat.workers.dev"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(endpoint = "$qa/other"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(endpoint = "${qa}#other"), production) }
    }

    @Test fun qaRejectsMissingOrWrongProjectAndClaims() {
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(shopUrl = ""), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(shopUrl = "https://mcewjfnkrtiiolzibsuh.supabase.co"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(firebaseProject = "adischat-f4a77"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(applicationId = "com.adischat.app"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(issuer = "adischat-api"), production) }
        assertThrows(IllegalArgumentException::class.java) { shopAssertionBaseUrl(true, valid.copy(audience = "adishop-edge"), production) }
    }
}
