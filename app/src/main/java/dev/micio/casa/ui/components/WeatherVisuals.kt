package dev.micio.casa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.micio.casa.ui.theme.Palette

private enum class Sky { CLEAR, PARTLY, CLOUDY, DRIZZLE, RAIN, SNOW, STORM }

private fun sky(code: Int) = when (code) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY
    3, 45, 48 -> Sky.CLOUDY
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> Sky.CLOUDY
}

fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (sky(code)) {
    Sky.CLEAR -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    Sky.PARTLY -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    Sky.CLOUDY -> Icons.Rounded.Cloud
    Sky.DRIZZLE -> Icons.Rounded.Grain
    Sky.RAIN -> Icons.Rounded.WaterDrop
    Sky.SNOW -> Icons.Rounded.AcUnit
    Sky.STORM -> Icons.Rounded.Thunderstorm
}

fun weatherTint(code: Int, isDay: Boolean): Color = when (sky(code)) {
    Sky.CLEAR, Sky.PARTLY -> if (isDay) Palette.Ember else Color(0xFFC9C3E6)
    Sky.CLOUDY -> Palette.Mist
    Sky.DRIZZLE, Sky.RAIN -> Palette.Sky
    Sky.SNOW -> Palette.Ivory
    Sky.STORM -> Palette.Coral
}

/** Ícone do tempo com um movimento discreto: o sol gira devagar, nuvens e chuva flutuam. */
@Composable
fun AnimatedWeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "weather")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "spin")
    val drift by motion.animateFloat(-1f, 1f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "drift")
    val kind = sky(code)
    Icon(
        weatherIcon(code, isDay),
        contentDescription = null,
        tint = weatherTint(code, isDay),
        modifier = modifier.size(size).graphicsLayer {
            if (kind == Sky.CLEAR && isDay) rotationZ = spin
            else translationX = drift * size.toPx() * 0.04f
        },
    )
}

