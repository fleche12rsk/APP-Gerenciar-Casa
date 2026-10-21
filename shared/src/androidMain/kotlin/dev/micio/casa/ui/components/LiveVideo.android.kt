package dev.micio.casa.ui.components

import android.view.LayoutInflater
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.micio.casa.shared.R
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.delay

/** ExoPlayer com buffer curto, para a imagem ficar o mais próxima do tempo real. */
@OptIn(UnstableApi::class)
@Composable
actual fun LiveVideo(
    url: String,
    muted: Boolean,
    modifier: Modifier,
    fit: Boolean,
    frameKey: String?,
    noSignalAction: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var buffering by remember(url) { mutableStateOf(true) }
    var view by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember(url, attempt) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 2_000, 250, 500)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(10_000)
                    .createMediaSource(MediaItem.fromUri(url))
            )
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) error = null
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "Sem sinal da câmera"
            }
        }
        player.addListener(listener)
        onDispose {
            if (frameKey != null) view?.let { captureFrame(it, frameKey) }
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }

    if (frameKey != null) {
        LaunchedEffect(player) {
            while (true) {
                delay(10_000)
                if (!buffering && error == null) view?.let { captureFrame(it, frameKey) }
            }
        }
    }

    // Para o vídeo quando o app vai para segundo plano, poupando rede e bateria.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> if (player.playbackState == Player.STATE_IDLE) player.prepare()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> (LayoutInflater.from(ctx).inflate(R.layout.rtsp_player, null) as PlayerView).also { view = it } },
            update = { v ->
                v.player = player
                v.resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(buffering && error == null, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(color = Palette.Ivory.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        error?.let { message ->
            NoSignal(message, onRetry = { error = null; attempt++ }, extra = noSignalAction)
        }
    }
}

@OptIn(UnstableApi::class)
private fun captureFrame(view: PlayerView, key: String) {
    val texture = view.videoSurfaceView as? TextureView ?: return
    if (!texture.isAvailable || texture.width == 0) return
    val width = 640
    val height = (width * texture.height / texture.width).coerceAtLeast(1)
    texture.getBitmap(width, height)?.let { FrameCache.put(key, it.asImageBitmap()) }
}
