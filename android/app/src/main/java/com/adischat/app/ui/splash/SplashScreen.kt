package com.adischat.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adischat.app.R
import com.adischat.app.ui.theme.Primary
import com.adischat.app.ui.theme.PrimaryDk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val iconScale = remember { Animatable(0.35f) }
    val iconAlpha = remember { Animatable(0f) }
    val iconRotation = remember { Animatable(-12f) }
    val progress = remember { Animatable(0f) }
    val footerAlpha = remember { Animatable(0f) }
    val letters = remember { "ADISCHAT".map { Animatable(0f) } }

    val infinite = rememberInfiniteTransition(label = "splash-loop")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "icon-pulse",
    )
    val glowRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "glow-rotation",
    )
    val shimmer by infinite.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "progress-shimmer",
    )
    val dotPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )

    LaunchedEffect(Unit) {
        delay(120)
        launch { iconAlpha.animateTo(1f, tween(280)) }
        launch {
        iconScale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        }
        launch { iconRotation.animateTo(0f, tween(760, easing = FastOutSlowInEasing)) }

        delay(360)
        letters.forEachIndexed { index, anim ->
        launch {
        delay(index * 62L)
        anim.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        }
        }

        delay(680)
        launch { footerAlpha.animateTo(1f, tween(520)) }
        progress.animateTo(1f, tween(1650, easing = FastOutSlowInEasing))
        delay(1250)
        onComplete()
    }

    Box(
        modifier = Modifier
        .fillMaxSize()
        .background(
        Brush.linearGradient(
        listOf(
        Color(0xFFF2FFF7),
        Color(0xFFE9FFF2),
        Color(0xFFF8FAFC),
        Color(0xFFEFFAF6)
        )
        )
        )
    ) {
        SoftBackground()

        Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(contentAlignment = Alignment.Center) {
        PulseRing(scale = pulse, alpha = iconAlpha.value * 0.28f)
        PulseRing(scale = pulse * 1.18f, alpha = iconAlpha.value * 0.14f)
        Box(
        Modifier
        .size(154.dp)
        .graphicsLayer { rotationZ = glowRotation }
        .clip(RoundedCornerShape(42.dp))
        .background(
        Brush.sweepGradient(
        listOf(
        Color.Transparent,
        Primary.copy(alpha = 0.28f),
        Color.Transparent,
        Color(0xFF86EFAC).copy(alpha = 0.26f),
        Color.Transparent
        )
        )
        )
        .alpha(iconAlpha.value * 0.72f)
        )
        Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "AdisChat",
        contentScale = ContentScale.Fit,
        modifier = Modifier
        .size(132.dp)
        .scale(iconScale.value)
        .alpha(iconAlpha.value)
        .graphicsLayer { rotationZ = iconRotation.value }
        .clip(RoundedCornerShape(36.dp)),
        )
        }

        Spacer(Modifier.height(30.dp))

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        "ADISCHAT".forEachIndexed { index, char ->
        val value = letters[index].value
        Text(
        text = char.toString(),
        fontSize = 43.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
        color = if (index >= 4) Primary else Color(0xFF071526),
        letterSpacing = 0.sp,
        modifier = Modifier
        .alpha(value)
        .offset(y = ((1f - value) * 26f).dp)
        .graphicsLayer {
        scaleX = 0.82f + (value * 0.18f)
        scaleY = 0.82f + (value * 0.18f)
        }
        )
        }
        }

        Box(
        Modifier
        .padding(top = 8.dp)
        .width(88.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(Brush.horizontalGradient(listOf(Primary, Color(0xFF86EFAC))))
        .alpha(letters.last().value)
        )

        }

        Column(
        modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 72.dp)
        .alpha(footerAlpha.value),
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(
        Modifier
        .width(224.dp)
        .height(6.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(Primary.copy(alpha = 0.10f))
        ) {
        Box(
        Modifier
        .fillMaxHeight()
        .fillMaxWidth(progress.value)
        .clip(RoundedCornerShape(999.dp))
        .background(
        Brush.horizontalGradient(
        colorStops = arrayOf(
        0f to Primary,
        shimmer.coerceIn(0f, 1f) to Color(0xFFA7F3D0),
        1f to Primary
        )
        )
        )
        )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
        text = "LOADING",
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = PrimaryDk,
        letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(8.dp))
        repeat(3) { index ->
        val active = ((dotPhase + index.toFloat()) % 3f) < 1.2f
        Box(
        Modifier
        .padding(horizontal = 2.dp)
        .size(if (active) 7.dp else 5.dp)
        .clip(CircleShape)
        .background(Primary.copy(alpha = if (active) 1f else 0.32f))
        )
        }
        }
        }

    }
}

@Composable
private fun SoftBackground() {
    Box(Modifier.fillMaxSize()) {
        Box(
        Modifier
        .size(320.dp)
        .align(Alignment.TopEnd)
        .offset(x = 96.dp, y = (-92).dp)
        .clip(CircleShape)
        .background(Primary.copy(alpha = 0.08f))
        )
        Box(
        Modifier
        .size(250.dp)
        .align(Alignment.BottomStart)
        .offset(x = (-78).dp, y = 52.dp)
        .clip(CircleShape)
        .background(Color(0xFF22C55E).copy(alpha = 0.06f))
        )
        Box(
        Modifier
        .size(160.dp)
        .align(Alignment.CenterStart)
        .offset(x = (-64).dp, y = (-42).dp)
        .clip(CircleShape)
        .background(PrimaryDk.copy(alpha = 0.045f))
        )
    }
}

@Composable
private fun PulseRing(scale: Float, alpha: Float) {
    Box(
        Modifier
        .size(142.dp)
        .scale(scale)
        .clip(RoundedCornerShape(42.dp))
        .background(Primary.copy(alpha = alpha))
    )
}
