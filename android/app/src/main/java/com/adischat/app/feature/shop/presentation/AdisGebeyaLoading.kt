package com.adischat.app.feature.shop.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R
import com.adischat.app.ui.theme.AdisGebeyaColors

private val LoadingIndigo = Color(0xFF1E1B4B)
private val LoadingIndigoSoft = Color(0xFFF0EFFF)
private val LoadingAmber = Color(0xFFF5B800)

@Composable
internal fun BuyerMarketplaceInitialLoader() {
    AdisGebeyaInitialLoader(
        title = "AdisGebeya",
        subtitle = "Loading marketplace...",
        accent = false,
    ) {
        Image(
            painter = painterResource(R.drawable.adisgebeya_loader_buyer),
            contentDescription = null,
            modifier = Modifier.size(62.dp),
        )
    }
}

@Composable
internal fun SellerCenterInitialLoader() {
    AdisGebeyaInitialLoader(
        title = "Seller Center",
        subtitle = "Preparing your store...",
        accent = true,
    ) {
        Image(
            painter = painterResource(R.drawable.adisgebeya_loader_seller),
            contentDescription = null,
            modifier = Modifier.size(62.dp),
        )
    }
}

@Composable
private fun AdisGebeyaInitialLoader(
    title: String,
    subtitle: String,
    accent: Boolean,
    icon: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "adisgebeya loading ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading ring rotation",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .semantics { contentDescription = "$title. $subtitle" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation },
            ) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = LoadingIndigo.copy(alpha = .14f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = LoadingIndigo,
                    startAngle = -90f,
                    sweepAngle = if (accent) 260f else 286f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (accent) {
                    drawArc(
                        color = LoadingAmber,
                        startAngle = 184f,
                        sweepAngle = 18f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Surface(shape = CircleShape, color = LoadingIndigoSoft, modifier = Modifier.size(70.dp)) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = LoadingIndigo, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = AdisGebeyaColors.SecondaryText, fontSize = 14.sp)
    }
}

@Composable
internal fun BuyerHomeSkeleton() {
    val shimmer = rememberSkeletonBrush()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(shimmer, Modifier.size(42.dp), CircleShape)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.42f).height(13.dp))
                        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.28f).height(10.dp))
                    }
                    SkeletonBlock(shimmer, Modifier.size(38.dp), CircleShape)
                    SkeletonBlock(shimmer, Modifier.size(38.dp), CircleShape)
                }
                SkeletonBlock(shimmer, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(15.dp))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SkeletonBlock(shimmer, Modifier.fillMaxWidth().height(172.dp), RoundedCornerShape(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonBlock(shimmer, Modifier.size(52.dp), CircleShape)
                        SkeletonBlock(shimmer, Modifier.width(48.dp).height(9.dp))
                    }
                }
            }
            SkeletonSectionTitle(shimmer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { SkeletonProductCard(shimmer, Modifier.weight(1f), compact = true) }
            }
            SkeletonSectionTitle(shimmer)
            repeat(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonProductCard(shimmer, Modifier.weight(1f))
                    SkeletonProductCard(shimmer, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SellerCenterDashboardSkeleton() {
    val shimmer = rememberSkeletonBrush()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(shimmer, Modifier.size(52.dp), RoundedCornerShape(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonBlock(shimmer, Modifier.fillMaxWidth(.52f).height(15.dp))
                SkeletonBlock(shimmer, Modifier.fillMaxWidth(.34f).height(10.dp))
            }
            SkeletonBlock(shimmer, Modifier.size(40.dp), CircleShape)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { SkeletonKpiCard(shimmer, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { SkeletonKpiCard(shimmer, Modifier.weight(1f)) }
        }
        SellerSkeletonSection(shimmer, rows = 2)
        SellerSkeletonProductSection(shimmer)
        SellerSkeletonSection(shimmer, rows = 1)
        SkeletonSectionTitle(shimmer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SkeletonModuleCard(shimmer, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SellerSkeletonSection(shimmer: Brush, rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SkeletonSectionTitle(shimmer)
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, AdisGebeyaColors.Border, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                SkeletonBlock(shimmer, Modifier.size(46.dp), RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBlock(shimmer, Modifier.fillMaxWidth(.62f).height(11.dp))
                    SkeletonBlock(shimmer, Modifier.fillMaxWidth(.42f).height(9.dp))
                }
                SkeletonBlock(shimmer, Modifier.width(58.dp).height(24.dp), RoundedCornerShape(999.dp))
            }
        }
    }
}

@Composable
private fun SellerSkeletonProductSection(shimmer: Brush) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SkeletonSectionTitle(shimmer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { SkeletonProductCard(shimmer, Modifier.weight(1f), compact = true) }
        }
    }
}

@Composable
private fun SkeletonKpiCard(shimmer: Brush, modifier: Modifier) {
    Column(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AdisGebeyaColors.Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBlock(shimmer, Modifier.size(26.dp), RoundedCornerShape(8.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.45f).height(14.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.68f).height(9.dp))
    }
}

@Composable
private fun SkeletonModuleCard(shimmer: Brush, modifier: Modifier) {
    Column(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AdisGebeyaColors.Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(shimmer, Modifier.size(32.dp), RoundedCornerShape(10.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.8f).height(10.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.58f).height(9.dp))
    }
}

@Composable
private fun SkeletonProductCard(shimmer: Brush, modifier: Modifier, compact: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AdisGebeyaColors.Border, RoundedCornerShape(14.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SkeletonBlock(shimmer, Modifier.fillMaxWidth().aspectRatio(if (compact) 1.08f else 1f), RoundedCornerShape(11.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.88f).height(10.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.52f).height(9.dp))
        SkeletonBlock(shimmer, Modifier.fillMaxWidth(.66f).height(12.dp))
    }
}

@Composable
private fun SkeletonSectionTitle(shimmer: Brush) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SkeletonBlock(shimmer, Modifier.width(132.dp).height(15.dp))
        SkeletonBlock(shimmer, Modifier.width(48.dp).height(10.dp))
    }
}

@Composable
private fun rememberSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "adisgebeya skeleton shimmer")
    val offset by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1_400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton shimmer offset",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .46f)
    val highlight = AdisGebeyaColors.PrimaryLight.copy(alpha = .9f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset - 420f, 0f),
        end = Offset(offset, 420f),
    )
}

@Composable
private fun SkeletonBlock(
    brush: Brush,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(999.dp),
) {
    Box(modifier.clip(shape).background(brush))
}

internal fun shouldShowBuyerInitialLoader(initialPhaseComplete: Boolean, hasError: Boolean): Boolean =
    !initialPhaseComplete && !hasError

internal fun shouldShowSellerInitialLoader(initialPhaseComplete: Boolean, hasError: Boolean): Boolean =
    !initialPhaseComplete && !hasError

internal fun isSellerIdentityResolving(
    resolutionStarted: Boolean,
    loading: Boolean,
    sellerStatus: String,
    hasApplication: Boolean,
    hasError: Boolean,
): Boolean = !hasError && (
    !resolutionStarted || (loading && sellerStatus == "not_started" && !hasApplication)
    )
