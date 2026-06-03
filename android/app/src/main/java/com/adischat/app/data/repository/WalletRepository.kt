package com.adischat.app.data.repository

import com.adischat.app.data.preferences.WalletState
import com.adischat.app.data.preferences.WalletTransaction
import com.adischat.app.network.CloudflareClient
import com.adischat.app.network.MerchantPayRedeemBody
import com.adischat.app.network.WalletActivateBody
import com.adischat.app.network.WalletAmountBody
import com.adischat.app.network.WalletChangePinBody
import com.adischat.app.network.WalletResponse
import com.adischat.app.network.WalletSendBody
import com.adischat.app.network.WalletTransactionResponse
import org.json.JSONObject
import retrofit2.HttpException

class WalletRepository {
    private val api = CloudflareClient.walletApi

    suspend fun getWallet(): Result<WalletState> = walletResult {
        api.getWallet().toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun activate(username: String, phone: String, pin: String): Result<WalletState> = walletResult {
        api.activateWallet(WalletActivateBody(username = username, phone = phone, pin = pin))
            .toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun changePin(oldPin: String, newPin: String): Result<WalletState> = walletResult {
        api.changeWalletPin(WalletChangePinBody(oldPin = oldPin, newPin = newPin))
            .toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun deposit(amount: Double): Result<WalletState> = walletResult {
        api.depositWallet(WalletAmountBody(amount = amount, note = "Mobile app deposit"))
        api.getWallet().toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun send(recipient: String, amount: Double, pin: String): Result<WalletState> = walletResult {
        api.sendMoney(WalletSendBody(recipient = recipient, amount = amount, pin = pin, note = "Mobile app transfer"))
        api.getWallet().toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun sendDetailed(recipient: String, amount: Double, pin: String): Result<Pair<WalletState, WalletTransaction?>> = walletResult {
        val action = api.sendMoney(WalletSendBody(recipient = recipient, amount = amount, pin = pin, note = "Mobile app transfer"))
        val transactions = getTransactions().getOrDefault(emptyList())
        val state = api.getWallet().toState(transactions = transactions)
        state to transactions.firstOrNull { it.reference == action.reference }
    }

    suspend fun redeemMerchantPayCode(code: String, amount: Double): Result<WalletState> = walletResult {
        api.redeemMerchantPayCode(MerchantPayRedeemBody(code = code, amount = amount))
        api.getWallet().toState(transactions = getTransactions().getOrDefault(emptyList()))
    }

    suspend fun getReceivePayload(): Result<String> = walletResult {
        api.receiveWallet().qrPayload
    }

    suspend fun getUsernamePayload(): Result<String> = walletResult {
        api.usernameQr().qrPayload
    }

    suspend fun getPayCode(): Result<WalletState> = walletResult {
        val payCode = api.payCode()
        getWallet().getOrThrow().copy(
            payCode = payCode.formattedCode.ifBlank { payCode.code },
            payQrPayload = payCode.qrPayload,
            payBarcodePayload = payCode.barcodePayload.ifBlank { payCode.code },
            payExpiresAt = payCode.expiresAt,
        )
    }

    suspend fun getTransactions(): Result<List<WalletTransaction>> = walletResult {
        api.getWalletTransactions().map { it.toTransaction() }
    }
}

private suspend fun <T> walletResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(IllegalStateException(walletErrorMessage(e)))
    }

private fun walletErrorMessage(error: Throwable): String {
    if (error is HttpException) {
        val body = error.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        if (parsed.isNotBlank()) return parsed
        return when (error.code()) {
            400 -> "Check the wallet details and try again"
            401 -> "Please sign in again"
            403 -> "User verification or a valid PIN is required"
            404 -> "Recipient wallet not found"
            423 -> "Wallet is locked"
            else -> "Wallet request failed (${error.code()})"
        }
    }
    return CloudflareClient.friendlyErrorMessage(error, "Wallet request failed")
}

private fun WalletResponse.toState(transactions: List<WalletTransaction>): WalletState =
    WalletState(
        userId = userId,
        active = active,
        balance = balance,
        walletPinSet = walletPinSet,
        frozen = frozen,
        walletId = walletId,
        username = username,
        phone = phone,
        email = email,
        name = name,
        currency = currency,
        transactions = transactions,
    )

private fun WalletTransactionResponse.toTransaction(): WalletTransaction =
    WalletTransaction(
        id = id,
        reference = reference,
        title = when (type) {
            "send" -> "Sent money"
            "receive" -> "Received money"
            "deposit" -> "Deposit"
            "merchant_pay" -> "Merchant payment"
            "merchant_receive" -> "Merchant collection"
            "admin_adjust" -> "Admin adjustment"
            else -> type.replaceFirstChar { it.uppercase() }
        },
        amount = amount,
        type = type,
        status = status,
        currency = currency,
        counterpartyWalletId = counterparty_wallet_id.orEmpty(),
        counterpartyName = counterparty_name.orEmpty(),
        counterpartyUsername = counterparty_username.orEmpty(),
        note = note,
        timestamp = created_at,
    )
