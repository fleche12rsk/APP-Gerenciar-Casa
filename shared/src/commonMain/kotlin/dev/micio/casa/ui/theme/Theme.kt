package dev.micio.casa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Night = Color(0xFF14161C)
    val NightHigh = Color(0xFF1C1F27)
    val Ivory = Color(0xFFF4EFE6)
    val Ink = Color(0xFF1C1D22)
    val InkMuted = Color(0xFF6E6B66)
    val Mist = Color(0xFFA9ABB3)
    val Ember = Color(0xFFF2A65A)
    val Sage = Color(0xFF8CC7A1)
    val Sky = Color(0xFF86B6E8)
    val Lagoon = Color(0xFF6CC3C0)
    val Coral = Color(0xFFE9806E)
    val GlassFill = Color.White.copy(alpha = 0.07f)
    val GlassLine = Color.White.copy(alpha = 0.07f)
}

private val scheme = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Palette.Ink,
    secondary = Palette.Sage,
    onSecondary = Palette.Ink,
    tertiary = Palette.Sky,
    background = Palette.Night,
    onBackground = Palette.Ivory,
    surface = Palette.NightHigh,
    onSurface = Palette.Ivory,
    surfaceVariant = Color(0xFF262932),
    onSurfaceVariant = Palette.Mist,
    surfaceContainer = Palette.NightHigh,
    surfaceContainerHigh = Color(0xFF242731),
    surfaceContainerHighest = Color(0xFF2B2E38),
    surfaceContainerLow = Color(0xFF181A21),
    outline = Color(0xFF3A3D47),
    outlineVariant = Color(0xFF2C2F38),
    error = Palette.Coral,
    onError = Palette.Ink,
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

/** Rótulo pequeno em caixa-alta usado nos títulos de seção. */
val SectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

@Composable
fun CasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
