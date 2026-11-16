package com.adischat.app.network

import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthConnectivityTest {
    @Test fun networkFailuresAreRecoverable() {
        assertTrue(UnknownHostException("offline").isRecoverableConnectivityFailure())
        assertTrue(IllegalStateException("wrapped", UnknownHostException("offline")).isRecoverableConnectivityFailure())
    }

    @Test fun invalidCredentialsAreNotConnectivityFailures() {
        assertFalse(IllegalArgumentException("invalid credentials").isRecoverableConnectivityFailure())
    }
}
