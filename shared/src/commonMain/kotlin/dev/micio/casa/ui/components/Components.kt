package dev.micio.casa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.micio.casa.model.Device
import dev.micio.casa.model.Scene
import dev.micio.casa.ui.theme.Palette
import dev.micio.casa.ui.theme.SectionLabel

/** Fundo noturno com um brilho quente no canto, como uma luz acesa na sala. */
fun Modifier.ambientBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1E26), Palette.Night)))
    drawRect(
        Brush.radialGradient(
            colors = listOf(Palette.Ember.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.08f, size.height * 0.02f),
            radius = size.maxDimension * 0.6f,
        )
    )
}

@Composable
fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return scale
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceTile(
    device: Device,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = device.isOn == true && device.online
    val accent = device.kind.accent()
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val background by animateColorAsState(if (on) Palette.Ivory else Palette.GlassFill, tween(260), label = "tileBg")
    val title by animateColorAsState(if (on) Palette.Ink else Palette.Ivory, tween(260), label = "tileTitle")
    val subtitle by animateColorAsState(if (on) Palette.InkMuted else Palette.Mist, tween(260), label = "tileSub")
    val iconBg by animateColorAsState(if (on) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f), label = "iconBg")
    val iconTint by animateColorAsState(if (on) darken(accent) else Palette.Mist, label = "iconTint")
    val toggleable = device.isOn != null && device.online
    val status = device.statusLine()

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .height(128.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .border(1.dp, if (on) Color.Transparent else Palette.GlassLine, RoundedCornerShape(22.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = if (toggleable) Role.Switch else Role.Button,
                onClickLabel = if (toggleable) (if (on) "Desligar" else "Ligar") else "Abrir",
                onLongClickLabel = "Detalhes",
                onClick = if (toggleable) onToggle else onOpen,
                onLongClick = onOpen,
            )
            .semantics {
                contentDescription = device.name
                stateDescription = status
            }
            .alpha(if (device.online) 1f else 0.55f)
            .padding(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(device.kind.icon(), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            device.name,
            color = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(status, color = subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun darken(color: Color) = Color(color.red * 0.62f, color.green * 0.62f, color.blue * 0.62f)

@Composable
fun SceneCard(scene: Scene, onRun: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Row(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .widthIn(min = 180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.GlassFill)
            .border(1.dp, Palette.GlassLine, RoundedCornerShape(18.dp))
            .combinedClickableCompat(interaction, onRun, "Ativar cena ${scene.name}")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Palette.Ember.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Palette.Ember, modifier = Modifier.size(18.dp))
        }
        Text(scene.name, style = MaterialTheme.typography.titleSmall, color = Palette.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(interaction: MutableInteractionSource, onClick: () -> Unit, label: String) =
    combinedClickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = SectionLabel, color = Palette.Mist, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.GlassFill)
            .border(1.dp, Palette.GlassLine, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.GlassFill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.Ember, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Ivory)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Mist,
            modifier = Modifier.widthIn(max = 460.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
    }
}
