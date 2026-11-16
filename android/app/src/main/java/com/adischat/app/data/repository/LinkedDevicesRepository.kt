package com.adischat.app.data.repository

import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.DesktopQrMobileResponse
import com.adischat.app.network.DesktopQrPayloadBody
import com.adischat.app.network.LinkedDeviceResponse

class LinkedDevicesRepository {
    private val api = CloudflareClient.api

    suspend fun scanDesktopQr(payload: String): Result<DesktopQrMobileResponse> = runCatching {
        api.scanDesktopQr(DesktopQrPayloadBody(qrPayload = payload))
    }

    suspend fun approveDesktopQr(payload: String): Result<Unit> = runCatching {
        api.approveDesktopQr(DesktopQrPayloadBody(qrPayload = payload))
        Unit
    }

    suspend fun rejectDesktopQr(payload: String): Result<Unit> = runCatching {
        api.rejectDesktopQr(DesktopQrPayloadBody(qrPayload = payload))
        Unit
    }

    suspend fun linkedDevices(): Result<List<LinkedDeviceResponse>> = runCatching {
        api.getLinkedDevices().devices
    }

    suspend fun logoutDevice(id: String): Result<Unit> = runCatching {
        api.logoutLinkedDevice(id)
        Unit
    }

    suspend fun logoutAllDesktop(): Result<Unit> = runCatching {
        api.logoutAllDesktopDevices()
        Unit
    }
}