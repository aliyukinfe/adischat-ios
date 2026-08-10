package com.adischat.app.feature.shop.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ShopSessionExpiredException(
    cause: Throwable? = null,
) : IllegalStateException("Your session expired. Please sign in again.", cause)

internal class ShopAuthRetryCoordinator(
    private val isUnauthorized: (Throwable) -> Boolean,
) {
    private val refreshMutex = Mutex()

    suspend fun <T> execute(
        authorization: suspend () -> String,
        refreshAuthorization: suspend (failedAuthorization: String) -> String,
        request: suspend (authorization: String) -> T,
    ): T {
        val initialAuthorization = authorization()
        return try {
            request(initialAuthorization)
        } catch (error: Throwable) {
            if (!isUnauthorized(error)) throw error

            val retryAuthorization = refreshMutex.withLock {
                val latestAuthorization = authorization()
                if (latestAuthorization != initialAuthorization) {
                    latestAuthorization
                } else {
                    refreshAuthorization(initialAuthorization)
                }
            }

            try {
                request(retryAuthorization)
            } catch (retryError: Throwable) {
                if (isUnauthorized(retryError)) {
                    throw ShopSessionExpiredException(retryError)
                }
                throw retryError
            }
        }
    }
}
