package dev.micio.casa.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.HaSnapshot
import dev.micio.casa.ui.components.RtspPlayer
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.theme.Palette

/** Câmera exibível: RTSP direto (EZVIZ, ICSee) ou entidade camera.* do Home Assistant. */
private data class CameraFeed(val id: String, val name: String, val room: String?, val url: String?, val subUrl: String?, val haEntity: String?)

@Composable
fun CamerasScreen(vm: MainViewModel, wide: Boolean, onOpenSettings: () -> Unit) {
    val config by vm.config.config.collectAsStateWithLifecycle()
    val devices by vm.home.devices.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf<String?>(null) }

    val feeds = config.cameras.map { CameraFeed(it.id, config.overrides["cam:${it.id}"]?.name ?: it.name, it.room, it.url, it.subUrl, null) } +
        devices.filter { it.kind == DeviceKind.CAMERA && it.source == Source.HOME_ASSISTANT }
            .map { CameraFeed(it.id, it.name, it.room, null, null, it.id.removePrefix("ha:")) }
    val hidden = config.overrides.filterValues { it.hidden }.keys
    val visible = feeds.filterNot { "cam:${it.id}" in hidden }

    if (visible.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.VideocamOff,
                title = "Nenhuma câmera ainda",
                message = "Adicione as câmeras EZVIZ e ICSee pelo IP da rede local. A imagem vem direto da câmera, sem passar pela nuvem.",
            ) {
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
                    Text("Adicionar câmera")
                }
            }
        }
        return
    }

    val hPad = if (wide) 32.dp else 16.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (wide) 380.dp else 300.dp),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 24.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Câmeras", style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("${visible.size} ao vivo") }
        items(visible, key = { it.id }) { feed ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClickLabel = "Ampliar ${feed.name}") { fullscreen = feed.id },
            ) {
                FeedView(vm, feed, preferSub = true, muted = true, fit = false)
                CameraLabel(feed.name, feed.room, Modifier.align(Alignment.BottomStart))
            }
        }
    }

    visible.firstOrNull { it.id == fullscreen }?.let { feed ->
        var muted by rememberSaveable { mutableStateOf(true) }
        Dialog(onDismissRequest = { fullscreen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                FeedView(vm, feed, preferSub = false, muted = muted, fit = true)
                CameraLabel(feed.name, feed.room, Modifier.align(Alignment.BottomStart))
                Row(Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (feed.url != null) {
                        OverlayButton(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Ativar som" else "Silenciar") { muted = !muted }
                    }
                    OverlayButton(Icons.Rounded.Close, "Fechar") { fullscreen = null }
                }
            }
        }
    }
}

@Composable
private fun FeedView(vm: MainViewModel, feed: CameraFeed, preferSub: Boolean, muted: Boolean, fit: Boolean) {
    val modifier = Modifier.fillMaxSize()
    when {
        feed.url != null -> RtspPlayer(if (preferSub) feed.subUrl ?: feed.url else feed.url, muted, modifier, fit)
        feed.haEntity != null -> vm.home.homeAssistant?.let { HaSnapshot(it.client, feed.haEntity, modifier, fit) }
    }
}

@Composable
private fun CameraLabel(name: String, room: String?, modifier: Modifier) {
    val live = rememberInfiniteTransition(label = "live")
    val dot by live.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(dot).clip(CircleShape).background(Palette.Coral))
        Text(name, color = Color.White, style = MaterialTheme.typography.titleSmall)
        room?.let { Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun OverlayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = description)
    }
}
