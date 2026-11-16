package com.adischat.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Official-looking verified mark shared by users and channels.
 * Drawn as a vector-style scalloped badge so it stays sharp at every size.
 */
@Composable
fun AdisVerifiedBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Verified",
) {
    val semanticModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(
        modifier = semanticModifier
        .aspectRatio(1f)
        .fillMaxSize(),
    ) {
        val side = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val badge = verifiedBadgePath(center, side * 0.47f, side * 0.405f)

        withTransform({ translate(top = side * 0.055f) }) {
        drawPath(badge, Color.Black.copy(alpha = 0.24f))
        }

        drawPath(
        path = badge,
        brush = Brush.linearGradient(
        colors = listOf(
        Color(0xFF5BB8FF),
        Color(0xFF188BFF),
        Color(0xFF0868E8),
        ),
        start = Offset(center.x - side * 0.32f, center.y - side * 0.42f),
        end = Offset(center.x + side * 0.34f, center.y + side * 0.42f),
        ),
        )

        drawPath(
        path = badge,
        color = Color.White.copy(alpha = 0.28f),
        style = Stroke(width = side * 0.045f, join = StrokeJoin.Round),
        )

        val check = Path().apply {
        moveTo(center.x - side * 0.205f, center.y + side * 0.015f)
        lineTo(center.x - side * 0.055f, center.y + side * 0.17f)
        lineTo(center.x + side * 0.235f, center.y - side * 0.16f)
        }
        drawPath(
        path = check,
        color = Color.White,
        style = Stroke(
        width = side * 0.115f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        ),
        )
    }
}

private fun verifiedBadgePath(center: Offset, outerRadius: Float, innerRadius: Float): Path {
    val points = 18
    val startAngle = -PI / 2.0
    return Path().apply {
        repeat(points * 2) { index ->
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        val angle = startAngle + index * PI / points
        val x = center.x + cos(angle).toFloat() * radius
        val y = center.y + sin(angle).toFloat() * radius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
