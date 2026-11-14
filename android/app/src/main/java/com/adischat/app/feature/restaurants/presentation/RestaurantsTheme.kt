package com.adischat.app.feature.restaurants.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.adischat.app.ui.theme.AppTypography

/**
 * Light-mode palette tokens scoped exclusively to AdisChat Restaurants customer UI.
 * Deliberately separated from global [com.adischat.app.ui.theme.AdisChatTheme] and AdisGebeya.
 */
object RestaurantsLightPalette {
    val MainBackground = Color(0xFFFFFFFF)
    val FeedBackground = Color(0xFFF7F8FA)
    val SidebarBackground = Color(0xFFF8F6F2)
    val CardSurface = Color(0xFFFFFFFF)
    val MutedSurface = Color(0xFFF3F4F6)

    val PrimaryText = Color(0xFF202124)
    val SecondaryText = Color(0xFF5F6368)
    val TertiaryText = Color(0xFF80868B)

    val BrandGreen = Color(0xFF16A34A)
    val BrandGreenDeep = Color(0xFF15803D)
    val BrandGreenSoft = Color(0xFFECFDF3)
    val BrandGreenBorder = Color(0xFFBBF7D0)

    // Meituan / Ele.me-inspired super-app accents scoped to Restaurants customer UI
    val HeaderGradientTop = Color(0xFFCFF6DF)
    val HeaderGradientMid = Color(0xFFEAFBF1)
    val SidebarSelectedMintStart = Color(0xFFDCFCE7)
    val SidebarSelectedMintEnd = Color(0xFFF3FCF7)
    val SidebarBadgeHot = Color(0xFFFF5232)

    val TrustGoldSurface = Color(0xFFFFEED9)
    val TrustGoldText = Color(0xFFB45309)
    val TrustGoldBorder = Color(0xFFF5D09D)

    val VoucherStripLabelBg = Color(0xFFFFEFEA)
    val VoucherGoldSurface = Color(0xFFFFEED2)
    val VoucherGoldBorder = Color(0xFFF3C580)
    val VoucherGoldText = Color(0xFF8A4B00)
    val VoucherRedStart = Color(0xFFFF463D)
    val VoucherRedEnd = Color(0xFFFF6D3A)
    val VoucherCreamSurface = Color(0xFFFFF6EB)
    val VoucherCreamBorder = Color(0xFFF4CE9E)

    val TagCreamSurface = Color(0xFFFDF3E6)
    val TagCreamText = Color(0xFF8C4E12)
    val PromoRibbonStart = Color(0xFFFF7224)
    val PromoRibbonEnd = Color(0xFFFF3E33)

    val AddButtonStart = Color(0xFF16A34A)
    val AddButtonMid = Color(0xFF28B463)
    val AddButtonEnd = Color(0xFFFF782C)
    val StepperPlusOrange = Color(0xFFFF7A1A)

    val CartBarDarkGreen = Color(0xFF154534)
    val CartBarDarkGreenBorder = Color(0xFF25614C)
    val CheckoutGoldStart = Color(0xFFFFD85A)
    val CheckoutGoldEnd = Color(0xFFFFAC1C)
    val CheckoutGoldText = Color(0xFF1E1A12)

    val CardBorder = Color(0xFFEAECEF)
    val Divider = Color(0xFFEAECEF)

    val Error = Color(0xFFDC2626)
    val ErrorSoft = Color(0xFFFEF2F2)

    val QaNoticeBackground = Color(0xFFFFFBEB)
    val QaNoticeText = Color(0xFF92400E)
    val QaNoticeBorder = Color(0xFFFDE68A)
}

@Immutable
data class RestaurantsExtendedColors(
    val mainBackground: Color = RestaurantsLightPalette.MainBackground,
    val feedBackground: Color = RestaurantsLightPalette.FeedBackground,
    val sidebarBackground: Color = RestaurantsLightPalette.SidebarBackground,
    val cardSurface: Color = RestaurantsLightPalette.CardSurface,
    val mutedSurface: Color = RestaurantsLightPalette.MutedSurface,
    val primaryText: Color = RestaurantsLightPalette.PrimaryText,
    val secondaryText: Color = RestaurantsLightPalette.SecondaryText,
    val tertiaryText: Color = RestaurantsLightPalette.TertiaryText,
    val brandGreen: Color = RestaurantsLightPalette.BrandGreen,
    val brandGreenDeep: Color = RestaurantsLightPalette.BrandGreenDeep,
    val brandGreenSoft: Color = RestaurantsLightPalette.BrandGreenSoft,
    val brandGreenBorder: Color = RestaurantsLightPalette.BrandGreenBorder,
    val headerGradientTop: Color = RestaurantsLightPalette.HeaderGradientTop,
    val headerGradientMid: Color = RestaurantsLightPalette.HeaderGradientMid,
    val sidebarSelectedMintStart: Color = RestaurantsLightPalette.SidebarSelectedMintStart,
    val sidebarSelectedMintEnd: Color = RestaurantsLightPalette.SidebarSelectedMintEnd,
    val sidebarBadgeHot: Color = RestaurantsLightPalette.SidebarBadgeHot,
    val trustGoldSurface: Color = RestaurantsLightPalette.TrustGoldSurface,
    val trustGoldText: Color = RestaurantsLightPalette.TrustGoldText,
    val trustGoldBorder: Color = RestaurantsLightPalette.TrustGoldBorder,
    val voucherStripLabelBg: Color = RestaurantsLightPalette.VoucherStripLabelBg,
    val voucherGoldSurface: Color = RestaurantsLightPalette.VoucherGoldSurface,
    val voucherGoldBorder: Color = RestaurantsLightPalette.VoucherGoldBorder,
    val voucherGoldText: Color = RestaurantsLightPalette.VoucherGoldText,
    val voucherRedStart: Color = RestaurantsLightPalette.VoucherRedStart,
    val voucherRedEnd: Color = RestaurantsLightPalette.VoucherRedEnd,
    val voucherCreamSurface: Color = RestaurantsLightPalette.VoucherCreamSurface,
    val voucherCreamBorder: Color = RestaurantsLightPalette.VoucherCreamBorder,
    val tagCreamSurface: Color = RestaurantsLightPalette.TagCreamSurface,
    val tagCreamText: Color = RestaurantsLightPalette.TagCreamText,
    val promoRibbonStart: Color = RestaurantsLightPalette.PromoRibbonStart,
    val promoRibbonEnd: Color = RestaurantsLightPalette.PromoRibbonEnd,
    val addButtonStart: Color = RestaurantsLightPalette.AddButtonStart,
    val addButtonMid: Color = RestaurantsLightPalette.AddButtonMid,
    val addButtonEnd: Color = RestaurantsLightPalette.AddButtonEnd,
    val stepperPlusOrange: Color = RestaurantsLightPalette.StepperPlusOrange,
    val cartBarDarkGreen: Color = RestaurantsLightPalette.CartBarDarkGreen,
    val cartBarDarkGreenBorder: Color = RestaurantsLightPalette.CartBarDarkGreenBorder,
    val checkoutGoldStart: Color = RestaurantsLightPalette.CheckoutGoldStart,
    val checkoutGoldEnd: Color = RestaurantsLightPalette.CheckoutGoldEnd,
    val checkoutGoldText: Color = RestaurantsLightPalette.CheckoutGoldText,
    val cardBorder: Color = RestaurantsLightPalette.CardBorder,
    val divider: Color = RestaurantsLightPalette.Divider,
    val error: Color = RestaurantsLightPalette.Error,
    val errorSoft: Color = RestaurantsLightPalette.ErrorSoft,
    val qaNoticeBackground: Color = RestaurantsLightPalette.QaNoticeBackground,
    val qaNoticeText: Color = RestaurantsLightPalette.QaNoticeText,
    val qaNoticeBorder: Color = RestaurantsLightPalette.QaNoticeBorder,
)

internal val LocalRestaurantsExtendedColors = staticCompositionLocalOf {
    RestaurantsExtendedColors()
}

val RestaurantsLightColorScheme: ColorScheme = lightColorScheme(
    primary = RestaurantsLightPalette.BrandGreen,
    onPrimary = Color.White,
    primaryContainer = RestaurantsLightPalette.BrandGreenSoft,
    onPrimaryContainer = RestaurantsLightPalette.BrandGreenDeep,
    secondary = RestaurantsLightPalette.BrandGreenDeep,
    onSecondary = Color.White,
    secondaryContainer = RestaurantsLightPalette.SidebarBackground,
    onSecondaryContainer = RestaurantsLightPalette.PrimaryText,
    tertiary = RestaurantsLightPalette.BrandGreenDeep,
    onTertiary = Color.White,
    background = RestaurantsLightPalette.MainBackground,
    onBackground = RestaurantsLightPalette.PrimaryText,
    surface = RestaurantsLightPalette.CardSurface,
    onSurface = RestaurantsLightPalette.PrimaryText,
    surfaceTint = Color.Transparent,
    surfaceVariant = RestaurantsLightPalette.MutedSurface,
    onSurfaceVariant = RestaurantsLightPalette.SecondaryText,
    surfaceContainerLowest = RestaurantsLightPalette.MainBackground,
    surfaceContainerLow = RestaurantsLightPalette.SidebarBackground,
    surfaceContainer = RestaurantsLightPalette.CardSurface,
    surfaceContainerHigh = RestaurantsLightPalette.MutedSurface,
    outline = RestaurantsLightPalette.CardBorder,
    outlineVariant = RestaurantsLightPalette.Divider,
    error = RestaurantsLightPalette.Error,
    onError = Color.White,
    errorContainer = RestaurantsLightPalette.ErrorSoft,
    onErrorContainer = RestaurantsLightPalette.Error,
)

object RestaurantsLightTheme {
    val colors: RestaurantsExtendedColors
        @Composable
        get() = LocalRestaurantsExtendedColors.current
}

/**
 * Scoped light Material 3 theme strictly for AdisChat Restaurants customer screens.
 */
@Composable
fun RestaurantsLightTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRestaurantsExtendedColors provides RestaurantsExtendedColors(),
    ) {
        MaterialTheme(
            colorScheme = RestaurantsLightColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * Temporarily applies dark system-bar icons while white Restaurants UI is visible
 * and restores the host Activity's original window bar flags on exit.
 */
@Composable
fun RestaurantsSystemBarAppearance(useDarkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, useDarkIcons) {
        val activity = generateSequence<Context>(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val originalLightStatus = controller?.isAppearanceLightStatusBars
        val originalLightNav = controller?.isAppearanceLightNavigationBars
        if (controller != null) {
            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons
        }
        onDispose {
            if (controller != null) {
                if (originalLightStatus != null) {
                    controller.isAppearanceLightStatusBars = originalLightStatus
                }
                if (originalLightNav != null) {
                    controller.isAppearanceLightNavigationBars = originalLightNav
                }
            }
        }
    }
}
