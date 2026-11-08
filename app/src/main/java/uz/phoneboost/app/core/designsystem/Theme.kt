package uz.phoneboost.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.phoneboost.app.data.settings.ThemeMode

/** PHONEBOOST MAX yagona dizayn tizimi: neon ko'k/yashil aksent, premium qorong'i fon. */
object BrandColors {
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF2D7DFF)
    val NeonBlue = Color(0xFF3B9BFF)
    val Good = Color(0xFF22E55E)
    val Warn = Color(0xFFFFB020)
    val Bad = Color(0xFFFF4D5E)
    val Gold = Color(0xFFFFC53D)
    val Pink = Color(0xFFFF2D9B)

    val BgTop = Color(0xFF06102B)
    val BgBottom = Color(0xFF01030A)
    val CardTop = Color(0xFF0E1D45)
    val CardBottom = Color(0xFF081230)
    val CardBorder = Color(0xFF2559D6)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA9BCE3)

    val ScreenBrush: Brush get() = Brush.verticalGradient(listOf(BgTop, BgBottom))
    val CardBrush: Brush get() = Brush.verticalGradient(listOf(CardTop, CardBottom))
    val ButtonBrush: Brush get() = Brush.horizontalGradient(listOf(Color(0xFF1D5BFF), Color(0xFF22B8F0)))
    val BorderBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFF3B82FF), Color(0xFF1B3FA0), Color(0xFF22D3EE)))
}

object PbRadius {
    val Card = 20.dp
    val Button = 16.dp
    val Chip = 12.dp
}

private val DarkColors = darkColorScheme(
    primary = BrandColors.NeonBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF123A9E),
    onPrimaryContainer = Color.White,
    secondary = BrandColors.Cyan,
    onSecondary = Color(0xFF00212A),
    secondaryContainer = Color(0xFF14337F),
    onSecondaryContainer = Color.White,
    tertiary = BrandColors.Good,
    background = BrandColors.BgBottom,
    onBackground = BrandColors.TextPrimary,
    surface = BrandColors.CardBottom,
    onSurface = BrandColors.TextPrimary,
    surfaceVariant = Color(0xFF13214A),
    onSurfaceVariant = BrandColors.TextSecondary,
    surfaceContainer = BrandColors.CardBottom,
    surfaceContainerHigh = BrandColors.CardTop,
    surfaceContainerHighest = Color(0xFF16275A),
    outline = Color(0xFF2F4A93),
    outlineVariant = Color(0xFF1C2D63),
    error = BrandColors.Bad
)

// Reference dizayn qorong'i; yorug' rejim ham shu brend ranglarida, faqat och fon bilan.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5BFF),
    onPrimary = Color.White,
    secondary = Color(0xFF0E7490),
    background = Color(0xFFF1F5FF),
    onBackground = Color(0xFF07112E),
    surface = Color.White,
    onSurface = Color(0xFF07112E),
    surfaceVariant = Color(0xFFDDE7FF),
    onSurfaceVariant = Color(0xFF3F5280),
    outline = Color(0xFF8CA2D6),
    error = Color(0xFFD62839)
)

private val PbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(PbRadius.Chip),
    medium = RoundedCornerShape(PbRadius.Button),
    large = RoundedCornerShape(PbRadius.Card),
    extraLarge = RoundedCornerShape(28.dp)
)

private val PbTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PhoneBoostTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.isDark()) DarkColors else LightColors,
        shapes = PbShapes,
        typography = PbTypography,
        content = content
    )
}
