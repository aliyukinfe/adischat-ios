package com.adischat.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme { LIGHT, DARK, SYSTEM }

private val LightColors = lightColorScheme(
    primary      = Primary,
    onPrimary      = Color.White,
    primaryContainer    = Color(0xFFDCFCE7),
    onPrimaryContainer  = Color(0xFF166534),
    secondary      = PrimaryDk,
    tertiary      = PrimaryDeep,
    background      = BgLight,
    surface      = CardLight,
    surfaceVariant      = SurfaceLight,
    onBackground      = TextL,
    onSurface      = TextL,
    onSurfaceVariant    = TextSecL,
    outline      = BorderLight,
    error      = ErrorRed,
    onError      = Color.White,
)

private val DarkColors = darkColorScheme(
    primary      = Primary,
    onPrimary      = Color.White,
    primaryContainer    = Color(0xFF166534),
    onPrimaryContainer  = Color(0xFF86EFAC),
    secondary      = PrimaryDk,
    tertiary      = PrimaryDeep,
    background      = BgDark,
    surface      = CardDark,
    surfaceVariant      = SurfaceDark,
    onBackground      = TextD,
    onSurface      = TextD,
    onSurfaceVariant    = TextSecD,
    outline      = BorderDark,
    error      = ErrorRed,
    onError      = Color.White,
)

@Composable
fun AdisChatTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = AppTypography,
        content     = content,
    )
}
