package com.adischat.app.feature.restaurants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantsQaIsolationTest {

    private fun validQaStagingEnvironment(
        supabaseUrl: String = "https://gnhemfikofnzpemgnqdv.supabase.co",
        supabasePublishableKey: String = "anon-staging-publishable-key",
        identityBridgeUrl: String = "https://adischat-api-staging.adischat.workers.dev",
        isIdentityBridgeEnabled: Boolean = true,
        isDineInEnabled: Boolean = true,
        isQaBuild: Boolean = true,
        isOfflinePreview: Boolean = false,
        areQaOrderMutationsEnabled: Boolean = true,
    ) = RestaurantsEnvironment(
        supabaseUrl = supabaseUrl,
        supabasePublishableKey = supabasePublishableKey,
        identityBridgeUrl = identityBridgeUrl,
        isIdentityBridgeEnabled = isIdentityBridgeEnabled,
        isDineInEnabled = isDineInEnabled,
        isQaBuild = isQaBuild,
        isOfflinePreview = isOfflinePreview,
        areQaOrderMutationsEnabled = areQaOrderMutationsEnabled,
    )

    @Test
    fun `qa staging hosts and keys pass fail-closed checks`() {
        val env = validQaStagingEnvironment()
        assertEquals("gnhemfikofnzpemgnqdv.supabase.co", RestaurantsQaIsolationValidator.EXPECTED_STAGING_SUPABASE_HOST)
        assertEquals("adischat-api-staging.adischat.workers.dev", RestaurantsQaIsolationValidator.EXPECTED_STAGING_WORKER_HOST)
        assertTrue(env.isBackendConfigured())
        assertTrue(env.isIdentityBridgeConfigured())
        assertTrue(env.isStagingConnected())
        assertTrue(RestaurantsQaIsolationValidator.isExactStagingEnvironment(env))
        assertFalse(env.areOrderMutationsBlockedInQa())

        val nonExactQaEnv = validQaStagingEnvironment(supabaseUrl = "https://other.supabase.co")
        assertFalse(RestaurantsQaIsolationValidator.isExactStagingEnvironment(nonExactQaEnv))
    }

    @Test
    fun `qa build fails closed on production supabase or worker hosts and malformed urls`() {
        val prodShopEnv = validQaStagingEnvironment(supabaseUrl = "https://mcewjfnkrtiiolzibsuh.supabase.co")
        assertFalse(prodShopEnv.isBackendConfigured())
        assertFalse(prodShopEnv.isStagingConnected())

        val retiredStagingEnv = validQaStagingEnvironment(supabaseUrl = "https://opkqyvttbyezjbqwkvho.supabase.co")
        assertFalse(retiredStagingEnv.isBackendConfigured())

        val prodWorkerEnv = validQaStagingEnvironment(identityBridgeUrl = "https://adischat-api.adischat.workers.dev")
        assertFalse(prodWorkerEnv.isIdentityBridgeConfigured())
        assertFalse(prodWorkerEnv.isStagingConnected())

        val queryWorkerEnv = validQaStagingEnvironment(
            identityBridgeUrl = "https://adischat-api-staging.adischat.workers.dev/?x=1",
        )
        assertFalse(queryWorkerEnv.isIdentityBridgeConfigured())

        val httpSupabaseEnv = validQaStagingEnvironment(
            supabaseUrl = "http://gnhemfikofnzpemgnqdv.supabase.co",
        )
        assertFalse(httpSupabaseEnv.isBackendConfigured())
    }

    @Test
    fun `qa build fails closed on forbidden secrets disabled bridge or disabled mutations`() {
        val serviceRoleEnv = validQaStagingEnvironment(supabasePublishableKey = "sb_service_role_leak")
        assertFalse(serviceRoleEnv.isBackendConfigured())

        val disabledBridgeEnv = validQaStagingEnvironment(isIdentityBridgeEnabled = false)
        assertFalse(disabledBridgeEnv.isIdentityBridgeConfigured())
        assertFalse(disabledBridgeEnv.isStagingConnected())

        val blockedMutationsEnv = validQaStagingEnvironment(areQaOrderMutationsEnabled = false)
        assertTrue(blockedMutationsEnv.areOrderMutationsBlockedInQa())
    }

    @Test
    fun `non-qa release-like disabled environment keeps restaurants dine-in disabled`() {
        val releaseEnv = RestaurantsEnvironment(
            supabaseUrl = "",
            supabasePublishableKey = "",
            identityBridgeUrl = "",
            isIdentityBridgeEnabled = false,
            isDineInEnabled = false,
            isRestaurantCenterEnabled = false,
            isAdminEnabled = false,
            isQaBuild = false,
            isOfflinePreview = false,
            areQaOrderMutationsEnabled = false,
        )
        assertFalse(releaseEnv.isDineInEnabled)
        assertFalse(releaseEnv.isBackendConfigured())
        assertFalse(releaseEnv.isIdentityBridgeConfigured())
    }
}
