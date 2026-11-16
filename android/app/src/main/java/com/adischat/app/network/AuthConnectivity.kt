package com.adischat.app.network

import com.google.firebase.FirebaseNetworkException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal fun Throwable.isRecoverableConnectivityFailure(): Boolean {
    var failure: Throwable? = this
    repeat(8) {
        val current = failure ?: return false
        if (current is FirebaseNetworkException ||
            current is UnknownHostException ||
            current is SocketTimeoutException ||
            current is ConnectException ||
            current is SocketException ||
            current is SSLException
        ) return true
        failure = current.cause
    }
    return false
}
