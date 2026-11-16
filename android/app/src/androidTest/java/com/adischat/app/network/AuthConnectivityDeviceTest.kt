package com.adischat.app.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthConnectivityDeviceTest {
    @Test fun firebaseNetworkFailureIsRecoverable() {
        val failure = FirebaseNetworkException("offline")
        assertTrue(failure.isRecoverableConnectivityFailure())
        assertTrue(IllegalStateException("wrapped", failure).isRecoverableConnectivityFailure())
    }
}
