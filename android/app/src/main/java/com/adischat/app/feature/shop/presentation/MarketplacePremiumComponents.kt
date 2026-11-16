package com.adischat.app.feature.shop.presentation

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.ui.theme.AdisGebeyaColors

private val marketplaceDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

internal fun formatMarketplaceDate(value: String?, fallback: String): String {
    if (value.isNullOrBlank()) return fallback
    return runCatching { OffsetDateTime.parse(value).format(marketplaceDateFormatter) }
        .getOrElse { fallback }
}

@Composable
internal fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = AdisGebeyaColors.PrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = AdisGebeyaColors.SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.height(44.dp)) {
                Text(actionLabel, color = AdisGebeyaColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun PremiumEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(AdisGebeyaColors.PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(24.dp))
            }
            Text(
                title,
                color = AdisGebeyaColors.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                color = AdisGebeyaColors.SecondaryText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdisGebeyaColors.Primary),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun PremiumErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(AdisGebeyaColors.ErrorSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AdisGebeyaColors.Error)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = AdisGebeyaColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                Text(message, color = AdisGebeyaColors.SecondaryText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            TextButton(onClick = onRetry) {
                Text("Retry", color = AdisGebeyaColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun PremiumMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(21.dp))
            }
            Text(
                value,
                color = AdisGebeyaColors.PrimaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, color = AdisGebeyaColors.SecondaryText, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun PremiumUnavailableModuleCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AdisGebeyaColors.Surface),
        border = BorderStroke(1.dp, AdisGebeyaColors.Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(AdisGebeyaColors.PrimaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = AdisGebeyaColors.Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = AdisGebeyaColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                message,
                color = AdisGebeyaColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Not available yet",
                color = AdisGebeyaColors.SecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
