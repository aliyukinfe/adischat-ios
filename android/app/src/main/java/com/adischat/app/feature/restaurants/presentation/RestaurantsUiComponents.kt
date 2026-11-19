package com.adischat.app.feature.restaurants.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

internal object RestaurantsUiDefaults {
    val CardRadius = 16.dp
    val InnerRadius = 12.dp
    val PillRadius = 50.dp
    val ScreenHorizontalPadding = 16.dp
    val MaxReadableContentWidth = 760.dp

    val WarmAmberAccent = Color(0xFFF59E0B)
    val WarmAmberSoft = Color(0x26F59E0B)
    val DineInGreenSoft = Color(0x2022C55E)
}

@Composable
internal fun rememberRestaurantsShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val transition = rememberInfiniteTransition(label = "restaurants_shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_shift",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift - 300f, 0f),
        end = Offset(shift, 220f),
    )
}

internal val LocalRestaurantsTopBarStatusPadding = compositionLocalOf { true }

@Composable
internal fun RestaurantsTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val applyStatusBarPadding = LocalRestaurantsTopBarStatusPadding.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        val sidebar = com.adischat.app.ui.home.LocalSidebarBounds.current
        val startPad = if (sidebar != null) 60.dp else if (onBackClick != null) 4.dp else 16.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (applyStatusBarPadding) it.statusBarsPadding() else it }
                .padding(
                    start = startPad,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
internal fun DineInNoticeBanner(
    headline: String,
    supportingText: String,
    icon: ImageVector = Icons.Rounded.QrCodeScanner,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun RestaurantTagPill(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RestaurantsUiDefaults.PillRadius),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun RestaurantArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    fallbackIcon: ImageVector = Icons.Rounded.Restaurant,
    fallbackLabel: String? = null,
    aspectRatio: Float? = 16f / 9f,
    cornerRadius: Dp = RestaurantsUiDefaults.InnerRadius,
    modifier: Modifier = Modifier,
    placeholderTint: Color? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val sizedModifier = if (aspectRatio != null) {
        modifier
            .clip(shape)
            .aspectRatio(aspectRatio)
    } else {
        modifier.clip(shape)
    }

    if (imageUrl.isNullOrBlank()) {
        RestaurantArtworkPlaceholder(
            icon = fallbackIcon,
            label = fallbackLabel,
            accentTint = placeholderTint,
            modifier = sizedModifier,
        )
    } else {
        val shimmerBrush = rememberRestaurantsShimmerBrush()
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = sizedModifier,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush),
                )
            },
            error = {
                RestaurantArtworkPlaceholder(
                    icon = fallbackIcon,
                    label = fallbackLabel,
                    accentTint = placeholderTint,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

@Composable
private fun RestaurantArtworkPlaceholder(
    icon: ImageVector,
    label: String?,
    accentTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    val baseAccent = accentTint ?: RestaurantsLightTheme.colors.brandGreen
    val gradient = Brush.linearGradient(
        colors = listOf(
            baseAccent.copy(alpha = 0.14f),
            Color(0xFFF6FBF8),
            RestaurantsLightTheme.colors.trustGoldSurface.copy(alpha = 0.55f),
        ),
    )
    Box(
        modifier = modifier.background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = baseAccent.copy(alpha = 0.78f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun RestaurantLogoAvatar(
    logoUrl: String?,
    brandName: String,
    size: Dp = 44.dp,
    brandColor: Color? = null,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    cornerRadius: Dp = 12.dp,
) {
    val initials = brandName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "R" }

    val bgColor = brandColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val textColor = if (brandColor != null) {
        RestaurantsLightPalette.getContrastTextColor(brandColor)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = 2.dp,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            val shimmerBrush = rememberRestaurantsShimmerBrush()
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = "$brandName logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize().background(shimmerBrush))
                },
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(bgColor)) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                },
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
internal fun RestaurantsLoadingSkeletonList(
    cardCount: Int = 3,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberRestaurantsShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cardCount) {
            Surface(
                shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(RestaurantsUiDefaults.InnerRadius))
                            .background(shimmer),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmer),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmer),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RestaurantsStateMessageCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RestaurantsUiDefaults.ScreenHorizontalPadding, vertical = 20.dp),
        shape = RoundedCornerShape(RestaurantsUiDefaults.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(RestaurantsUiDefaults.InnerRadius),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun RestaurantsDisabledNoticeCard(
    modifier: Modifier = Modifier,
) {
    RestaurantsStateMessageCard(
        icon = Icons.Rounded.Storefront,
        title = "AdisChat Restaurants preview unavailable",
        message = "Dine-in restaurant discovery is disabled in this release configuration.",
        modifier = modifier,
    )
}

@Composable
internal fun RestaurantVerifiedBadge(
    modifier: Modifier = Modifier,
    label: String = "Verified",
    showLabel: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RestaurantsUiDefaults.PillRadius),
        color = Color(0xFFE0F2FE),
        border = BorderStroke(0.8.dp, Color(0xFFBAE6FD)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Verified restaurant",
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(13.dp),
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF0369A1),
                )
            }
        }
    }
}

@Composable
internal fun RestaurantRatingPill(
    rating: Double,
    reviewCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RestaurantsUiDefaults.PillRadius),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(0.8.dp, Color(0xFFFDE68A)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(12.dp),
            )
            val text = if (reviewCount != null && reviewCount > 0) {
                "${String.format(java.util.Locale.US, "%.1f", rating)} ($reviewCount)"
            } else {
                String.format(java.util.Locale.US, "%.1f", rating)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color(0xFF92400E),
            )
        }
    }
}

@Composable
internal fun DemoPreviewAlertBanner(
    modifier: Modifier = Modifier,
    title: String = "QA DEMO MODE",
    description: String = "Previewing 8 sample restaurants & menus with simulated table ordering. Zero backend mutations or charges.",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFFCD34D)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = Color(0xFFB45309),
                )
            }
        }
    }
}
