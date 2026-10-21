package dev.micio.casa.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.micio.casa.core.CameraBrand
import dev.micio.casa.core.CameraPower
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.FrameCache
import dev.micio.casa.ui.components.HaSnapshot
import dev.micio.casa.ui.components.RtspPlayer
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val ICSEE_PACKAGE = "com.xm.csee"

/** Câmera exibível: RTSP direto (EZVIZ, ICSee) ou entidade camera.* do Home Assistant. */
private data class CameraFeed(
    val id: String,
    val name: String,
    val room: String?,
    val url: String?,
    val subUrl: String?,
    val haEntity: String?,
    val brand: CameraBrand? = null,
    val power: CameraPower = CameraPower.ALWAYS,
    val autoStopSeconds: Int = 60,
)

@Composable
fun CamerasScreen(vm: MainViewModel, wide: Boolean, onOpenSettings: () -> Unit) {
    val config by vm.config.config.collectAsStateWithLifecycle()
    val devices by vm.home.devices.collectAsStateWithLifecycle()
    val weather by vm.weather.state.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf<String?>(null) }

    val feeds = config.cameras.map {
        CameraFeed(
            id = it.id, name = config.overrides["cam:${it.id}"]?.name ?: it.name, room = it.room,
            url = it.url, subUrl = it.subUrl, haEntity = null, brand = it.brand,
            power = it.power, autoStopSeconds = it.autoStopSeconds,
        )
    } + devices.filter { it.kind == DeviceKind.CAMERA && it.source == Source.HOME_ASSISTANT }
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

    // Dia = entre o nascer e o pôr do sol da previsão; sem previsão, das 6h às 18h.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val today = weather.weather?.today
    val isDaytime = if (today?.sunrise != null && today.sunset != null) {
        now.toLocalTime() in today.sunrise.toLocalTime()..today.sunset.toLocalTime()
    } else {
        now.hour in 6..17
    }

    // Sessões abertas com um toque: id da câmera -> horário (ms) em que o vídeo desliga.
    val sessions = remember { mutableStateMapOf<String, Long>() }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessions.isNotEmpty()) {
        while (sessions.isNotEmpty()) {
            clock = System.currentTimeMillis()
            sessions.entries.removeAll { it.value <= clock }
            delay(1_000)
        }
    }

    fun autoLive(feed: CameraFeed) = feed.haEntity != null || feed.power == CameraPower.ALWAYS ||
        (feed.power == CameraPower.DAYTIME && isDaytime)
    fun isLive(feed: CameraFeed) = autoLive(feed) || (sessions[feed.id] ?: 0L) > clock
    fun startSession(feed: CameraFeed) {
        clock = System.currentTimeMillis()
        sessions[feed.id] = clock + feed.autoStopSeconds * 1000L
    }
    fun extend(feed: CameraFeed) {
        sessions[feed.id] = (sessions[feed.id] ?: System.currentTimeMillis()) + 60_000L
    }
    fun remaining(feed: CameraFeed): Int? =
        if (autoLive(feed)) null else sessions[feed.id]?.let { ((it - clock) / 1000).toInt().coerceAtLeast(0) }

    val liveCount = visible.count(::isLive)
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            val resting = visible.size - liveCount
            SectionTitle(listOfNotNull("$liveCount ao vivo", resting.takeIf { it > 0 }?.let { "$it em repouso" }).joinToString(" · "))
        }
        items(visible, key = { it.id }) { feed ->
            val live = isLive(feed)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClickLabel = if (live) "Ampliar ${feed.name}" else "Ver ${feed.name} ao vivo") {
                        if (live) fullscreen = feed.id else startSession(feed)
                    },
            ) {
                AnimatedContent(live && fullscreen != feed.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "camLive") { playing ->
                    if (playing) FeedView(vm, feed, preferSub = true, muted = true, fit = false)
                    else RestingCamera(feed, isDaytime, inFullscreen = fullscreen == feed.id)
                }
                CameraLabel(feed.name, feed.room, live, Modifier.align(Alignment.BottomStart))
                remaining(feed)?.let { seconds ->
                    SessionChip(seconds, onExtend = { extend(feed) }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                }
            }
        }
    }

    visible.firstOrNull { it.id == fullscreen }?.let { feed ->
        var muted by rememberSaveable { mutableStateOf(true) }
        val live = isLive(feed)
        Dialog(onDismissRequest = { fullscreen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (live) {
                    FeedView(vm, feed, preferSub = false, muted = muted, fit = true)
                } else {
                    RestingCamera(feed, isDaytime, inFullscreen = false, onWake = { startSession(feed) })
                }
                CameraLabel(feed.name, feed.room, live, Modifier.align(Alignment.BottomStart))
                Row(
                    Modifier.align(Alignment.TopEnd).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    remaining(feed)?.let { SessionChip(it, onExtend = { extend(feed) }) }
                    if (feed.url != null && live) {
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
    val context = LocalContext.current
    val modifier = Modifier.fillMaxSize()
    when {
        feed.url != null -> RtspPlayer(
            url = if (preferSub) feed.subUrl ?: feed.url else feed.url,
            muted = muted,
            modifier = modifier,
            fit = fit,
            frameKey = feed.id,
            noSignalAction = if (feed.brand == CameraBrand.ICSEE) {
                { TextButton(onClick = { openIcsee(context) }) { Text("Abrir no app iCSee", color = Palette.Ember) } }
            } else null,
        )
        feed.haEntity != null -> vm.home.homeAssistant?.let { HaSnapshot(it.client, feed.haEntity, modifier, fit) }
    }
}

/** Câmera dormindo para poupar bateria: mostra o último quadro e convida a ligar. */
@Composable
private fun RestingCamera(feed: CameraFeed, isDaytime: Boolean, inFullscreen: Boolean, onWake: (() -> Unit)? = null) {
    val frame = FrameCache.get(feed.id)
    Box(Modifier.fillMaxSize().background(Palette.NightHigh), contentAlignment = Alignment.Center) {
        frame?.let {
            Image(it.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(
                    if (inFullscreen) Icons.Rounded.PlayArrow else if (isDaytime) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = Palette.Ivory,
                )
            }
            Text(
                when {
                    inFullscreen -> "Aberta em tela cheia"
                    feed.power == CameraPower.DAYTIME && !isDaytime -> "Em repouso durante a noite"
                    else -> "Em repouso para poupar bateria"
                },
                color = Palette.Ivory,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!inFullscreen) {
                Text(
                    listOfNotNull("Toque para ver ao vivo", frame?.let { "imagem de ${minutesAgo(it.capturedAt)}" }).joinToString(" · "),
                    color = Palette.Mist,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            onWake?.let {
                Button(onClick = it, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
                    Text("Ver de novo")
                }
            }
        }
    }
}

private fun minutesAgo(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "agora há pouco"
        minutes < 60 -> "há $minutes min"
        else -> "há ${minutes / 60} h"
    }
}

@Composable
private fun SessionChip(seconds: Int, onExtend: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = Palette.Sage, modifier = Modifier.size(16.dp))
        Text(
            "Desliga em %d:%02d".format(seconds / 60, seconds % 60),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
        TextButton(onClick = onExtend) { Text("+1 min", color = Palette.Ember) }
    }
}

@Composable
private fun CameraLabel(name: String, room: String?, live: Boolean, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "live")
    val dot by pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(if (live) dot else 1f).clip(CircleShape).background(if (live) Palette.Coral else Palette.Mist))
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

/** Câmeras solares do iCSee que não aceitam RTSP só acordam pelo app do fabricante. */
private fun openIcsee(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ICSEE_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$ICSEE_PACKAGE"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
