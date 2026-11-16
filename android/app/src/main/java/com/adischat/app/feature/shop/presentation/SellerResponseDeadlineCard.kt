package com.adischat.app.feature.shop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.feature.shop.data.ShopOrderGroupDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

internal enum class SellerResponseAudience { BUYER, SELLER }

internal enum class SellerResponseUrgency { NORMAL, AMBER, CRITICAL, EXPIRED }

internal data class SellerResponseCountdownState(
    val remainingMillis: Long,
    val label: String,
    val urgency: SellerResponseUrgency,
    val progress: Float,
)

internal fun sellerResponseCountdownState(remainingMillis: Long, slaHours: Int = 24): SellerResponseCountdownState {
    val safeRemaining = remainingMillis.coerceAtLeast(0L)
    val hour = 60L * 60L * 1_000L
    val minute = 60L * 1_000L
    val label = when {
        remainingMillis <= 0L -> "Checking order status..."
        remainingMillis < hour -> {
            val minutes = safeRemaining / minute
            val seconds = (safeRemaining % minute) / 1_000L
            "${minutes}m ${seconds}s remaining"
        }
        else -> {
            val hours = safeRemaining / hour
            val minutes = (safeRemaining % hour) / minute
            "${hours}h ${minutes}m remaining"
        }
    }
    val urgency = when {
        remainingMillis <= 0L -> SellerResponseUrgency.EXPIRED
        remainingMillis < hour -> SellerResponseUrgency.CRITICAL
        remainingMillis <= 6L * hour -> SellerResponseUrgency.AMBER
        else -> SellerResponseUrgency.NORMAL
    }
    val totalMillis = slaHours.coerceIn(1, 168) * hour.toFloat()
    return SellerResponseCountdownState(
        remainingMillis = safeRemaining,
        label = label,
        urgency = urgency,
        progress = (safeRemaining / totalMillis).coerceIn(0f, 1f),
    )
}

internal fun formatSellerResponseDeadline(raw: String?): String = raw
    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
    ?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(it.atZone(ZoneId.systemDefault()))
    }
    ?: "Not available"

internal fun sellerResponseServerOffsetMillis(serverNow: String?, receivedAtMillis: Long?): Long {
    val serverMillis = serverNow?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return if (serverMillis != null && receivedAtMillis != null) serverMillis - receivedAtMillis else 0L
}

@Composable
internal fun SellerResponseDeadlineCard(
    group: ShopOrderGroupDto,
    audience: SellerResponseAudience,
    onDeadlineReached: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deadline = group.sellerResponseRequiredAt ?: return
    val fulfillment = group.fulfillmentStatus.orEmpty().ifBlank { group.status }
    if (group.sellerResponseStatus != "pending" || fulfillment !in setOf("awaiting_seller_confirmation", "created")) return

    val deadlineInstant = remember(deadline) { runCatching { Instant.parse(deadline) }.getOrNull() } ?: return
    val serverOffset = remember(group.serverNow, group.serverReceivedAtMillis) {
        sellerResponseServerOffsetMillis(group.serverNow, group.serverReceivedAtMillis)
    }
    var currentMillis by remember(deadline, group.serverNow, group.serverReceivedAtMillis) {
        mutableLongStateOf(System.currentTimeMillis() + serverOffset)
    }
    var nextRefreshAllowedAt by remember(deadline) { mutableLongStateOf(0L) }
    val state = sellerResponseCountdownState(deadlineInstant.toEpochMilli() - currentMillis, group.sellerResponseSlaHours)

    LaunchedEffect(deadline, group.serverNow) {
        while (true) {
            currentMillis = System.currentTimeMillis() + serverOffset
            val remaining = deadlineInstant.toEpochMilli() - currentMillis
            if (remaining <= 0L) {
                val localNow = System.currentTimeMillis()
                if (localNow >= nextRefreshAllowedAt) {
                    nextRefreshAllowedAt = localNow + 10_000L
                    onDeadlineReached()
                }
                delay((nextRefreshAllowedAt - localNow).coerceIn(1_000L, 10_000L))
                continue
            }
            delay(if (remaining < 60L * 60L * 1_000L) 1_000L else 30_000L)
        }
    }

    val accent = when (state.urgency) {
        SellerResponseUrgency.NORMAL -> Color(0xFF1E1B4B)
        SellerResponseUrgency.AMBER -> Color(0xFFD97706)
        SellerResponseUrgency.CRITICAL -> Color(0xFFEA580C)
        SellerResponseUrgency.EXPIRED -> Color(0xFF64748B)
    }
    val background = when (state.urgency) {
        SellerResponseUrgency.NORMAL -> Color(0xFFF5F3FF)
        SellerResponseUrgency.AMBER -> Color(0xFFFFFBEB)
        SellerResponseUrgency.CRITICAL -> Color(0xFFFFF7ED)
        SellerResponseUrgency.EXPIRED -> Color(0xFFF8FAFC)
    }
    val deadlineLabel = remember(deadline) { formatSellerResponseDeadline(deadline) }
    val storeName = group.storeNameSnapshot?.takeIf { it.isNotBlank() } ?: "The seller"
    val paid = (group.payment?.status ?: group.paymentStatus) in setOf("paid", "partially_refunded")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = "Seller response deadline",
                    tint = accent,
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.08f)).padding(2.dp),
                )
                Text(
                    if (audience == SellerResponseAudience.BUYER) "Waiting for seller confirmation" else "New order - response required",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Text(
                if (audience == SellerResponseAudience.BUYER) "$storeName has until $deadlineLabel" else "Accept or reject before $deadlineLabel",
                color = Color(0xFF475569),
                fontSize = 13.sp,
            )
            Text(state.label, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
            )
            if (audience == SellerResponseAudience.BUYER) {
                Text(
                    if (paid) {
                        "Your payment is protected. If the seller does not respond in time, this part of the order will be cancelled and a refund will be initiated automatically."
                    } else {
                        "You have not been charged. If the seller does not respond in time, this part of the order will be cancelled automatically."
                    },
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
