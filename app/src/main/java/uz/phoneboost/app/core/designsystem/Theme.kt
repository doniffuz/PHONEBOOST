package uz.phoneboost.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import uz.phoneboost.app.data.settings.ThemeMode

object BrandColors {
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF3B82F6)
    val Good = Color(0xFF16A34A)
    val Warn = Color(0xFFD97706)
    val Bad = Color(0xFFDC2626)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00363D),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE6EEF9),
    surface = Color(0xFF0E1626),
    onSurface = Color(0xFFE6EEF9),
    surfaceVariant = Color(0xFF16233A),
    onSurfaceVariant = Color(0xFF9FB1CC),
    outline = Color(0xFF3A4B68)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    secondary = Color(0xFF2563EB),
    background = Color(0xFFF4F8FC),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE3ECF7),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8)
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
        content = content
    )
}
