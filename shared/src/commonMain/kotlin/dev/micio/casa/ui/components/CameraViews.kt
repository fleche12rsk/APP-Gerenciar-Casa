package dev.micio.casa.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.micio.casa.core.nowMillis
import dev.micio.casa.integrations.ha.HomeAssistantClient
import dev.micio.casa.ui.decodeImage
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Último quadro visto de cada câmera, para mostrar algo enquanto ela dorme. */
object FrameCache {
    data class Frame(val image: ImageBitmap, val capturedAt: Long)

    private val frames = mutableMapOf<String, Frame>()

    fun get(key: String): Frame? = frames[key]

    fun put(key: String, image: ImageBitmap) {
        frames[key] = Frame(image, nowMillis())
        if (frames.size > 12) frames.remove(frames.minBy { it.value.capturedAt }.key)
    }
}

/**
 * Vídeo ao vivo RTSP. No Android usa o ExoPlayer; no iOS, o VLC.
 * Com [frameKey], guarda periodicamente um quadro no [FrameCache] (quando o sistema permite).
 */
@Composable
expect fun LiveVideo(
    url: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    frameKey: String? = null,
    noSignalAction: (@Composable () -> Unit)? = null,
)

/** Câmeras vindas do Home Assistant: atualiza um retrato a cada dois segundos. */
@Composable
fun HaSnapshot(client: HomeAssistantClient, entityId: String, modifier: Modifier = Modifier, fit: Boolean = false) {
    var image by remember(entityId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(entityId) { mutableStateOf(false) }
    LaunchedEffect(client, entityId) {
        while (true) {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeImage(client.cameraSnapshot(entityId)) }.getOrNull()
            }
            if (bitmap != null) image = bitmap
            failed = bitmap == null && image == null
            delay(2_000)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        image?.let {
            Image(it, contentDescription = null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (image == null && !failed) CircularProgressIndicator(color = Palette.Ivory.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        if (failed) NoSignal("Sem imagem do Home Assistant", onRetry = null, extra = null)
    }
}

@Composable
fun NoSignal(message: String, onRetry: (() -> Unit)?, extra: (@Composable () -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.VideocamOff, contentDescription = null, tint = Palette.Mist)
        Text(message, color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
        onRetry?.let {
            TextButton(onClick = it) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Palette.Ivory)
                Text("Tentar de novo", color = Palette.Ivory)
            }
        }
        extra?.invoke()
    }
}
