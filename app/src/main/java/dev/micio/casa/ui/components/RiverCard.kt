package dev.micio.casa.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.micio.casa.home.RiverState
import dev.micio.casa.integrations.river.LevelPoint
import dev.micio.casa.integrations.river.RiverMonitor
import dev.micio.casa.integrations.river.RiverReport
import dev.micio.casa.integrations.river.RiverStation
import dev.micio.casa.integrations.river.Trend
import dev.micio.casa.ui.theme.Palette
import dev.micio.casa.ui.theme.SectionLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

fun meters(value: Double) = "%.2f m".format(ptBR, value)

/** Nível do rio em destaque na tela inicial; toque abre o site da Defesa Civil. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiverCard(state: RiverState, wide: Boolean, modifier: Modifier = Modifier) {
    val report = state.report
    val uri = LocalUriHandler.current
    val site = state.city?.let { RiverMonitor.siteFor(it.id) }
    val source = "Defesa Civil de ${state.city?.name ?: "sua cidade"}"
    val reference = report?.reference
    val alert = (reference?.bandOrdinal ?: 0) > 0
    val bandColor = reference?.let { Color(it.bandColor) } ?: Palette.Mist

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (alert) bandColor.copy(alpha = 0.14f) else Palette.GlassFill)
            .border(1.dp, if (alert) bandColor.copy(alpha = 0.5f) else Palette.GlassLine, RoundedCornerShape(24.dp))
            .then(if (site != null) Modifier.clickable(role = Role.Button, onClickLabel = "Abrir site da Defesa Civil") { uri.openUri(site) } else Modifier)
            .padding(20.dp),
    ) {
        if (reference == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Water, contentDescription = null, tint = Palette.Sky)
                Text(
                    state.error ?: "Carregando nível do rio…",
                    color = Palette.Mist,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Water, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(18.dp))
                    Text(
                        (reference.river ?: "Nível do rio").uppercase(ptBR),
                        style = SectionLabel,
                        color = Palette.Mist,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(reference.name, color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        reference.levelM?.let(::meters) ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Palette.Ivory,
                    )
                    BandChip(reference, Modifier.padding(start = 14.dp))
                    TrendLabel(report, Modifier.padding(start = 12.dp))
                }
                Text(
                    reference.thresholds.joinToString(" · ") { "${it.label} ${meters(it.levelM)}" },
                    color = Palette.Mist,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (wide && report.history.size >= 2) {
                Sparkline(report.history, reference, Modifier.width(260.dp).height(76.dp).padding(start = 16.dp))
            }
        }
        if (!wide && report.history.size >= 2) {
            Sparkline(report.history, reference, Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp))
        }

        val others = report.stations.filter { it.id != reference.id }
        if (others.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                others.forEach { StationChip(it) }
            }
        }
        Text(
            listOfNotNull(
                reference.readingAt?.let { "Leitura ${timeAgo(it)}" },
                reference.rain24h?.takeIf { it > 0 }?.let { "chuva 24 h: %.1f mm".format(ptBR, it) },
                source,
            ).joinToString(" · "),
            color = Palette.Mist.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BandChip(station: RiverStation, modifier: Modifier = Modifier) {
    val color = Color(station.bandColor)
    val pulse = rememberInfiniteTransition(label = "band")
    val alpha by pulse.animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bandPulse")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (station.bandOrdinal > 0) alpha else 1f).clip(CircleShape).background(color))
        Text(station.bandLabel, color = Palette.Ivory, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun TrendLabel(report: RiverReport, modifier: Modifier = Modifier) {
    val rate = report.centimetersPerHour ?: return
    val (icon, text, tint) = when (report.trend) {
        Trend.RISING -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, "Subindo %.0f cm/h".format(ptBR, rate), Palette.Ember)
        Trend.FALLING -> Triple(Icons.AutoMirrored.Rounded.TrendingDown, "Baixando %.0f cm/h".format(ptBR, abs(rate)), Palette.Sage)
        Trend.STEADY -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, "Estável", Palette.Mist)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun StationChip(station: RiverStation) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(station.bandColor)))
        Text(
            "${station.river?.removePrefix("Rio ") ?: station.name} ${station.levelM?.let(::meters) ?: "—"}",
            color = Palette.Ivory,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Gráfico das últimas 24 h; a linha tracejada é a cota de atenção, se estiver perto. */
@Composable
private fun Sparkline(points: List<LevelPoint>, station: RiverStation, modifier: Modifier) {
    val attention = station.thresholds.firstOrNull()
    Canvas(modifier) {
        val values = points.map { it.levelM }
        var low = values.min()
        var high = values.max()
        val showAttention = attention != null && attention.levelM - high < (high - low).coerceAtLeast(0.5) * 2
        if (showAttention) high = maxOf(high, attention!!.levelM)
        val pad = ((high - low).coerceAtLeast(0.2)) * 0.15
        low -= pad
        high += pad
        fun x(i: Int) = size.width * i / (points.size - 1)
        fun y(v: Double) = (size.height * (1 - (v - low) / (high - low))).toFloat()

        val line = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.levelM)) else lineTo(x(i), y(p.levelM)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Palette.Sky.copy(alpha = 0.28f), Color.Transparent)))
        if (showAttention) {
            val ay = y(attention!!.levelM)
            drawLine(
                Color(attention.color).copy(alpha = 0.7f), Offset(0f, ay), Offset(size.width, ay),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        drawPath(line, Palette.Sky, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Palette.Ivory, radius = 4.dp.toPx(), center = Offset(x(points.lastIndex), y(values.last())))
    }
}

@Composable
private fun timeAgo(time: Instant): String {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val minutes = Duration.between(time, now).toMinutes()
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        else -> "há ${minutes / 60} h"
    }
}
