package dev.micio.casa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import dev.micio.casa.ui.theme.Palette
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/** Avisos do player nativo para a tela (chamados na thread principal). */
interface VideoListener {
    fun onPlaying()
    fun onError()
}

/**
 * Implementada em Swift pelo app iOS (VLC), porque o player nativo do iOS não abre RTSP.
 * O Kotlin só posiciona a UIView devolvida dentro da tela Compose.
 */
interface VideoViewFactory {
    fun create(url: String, muted: Boolean, listener: VideoListener): UIView
    fun setMuted(view: UIView, muted: Boolean)
    fun release(view: UIView)
}

object IosVideo {
    var factory: VideoViewFactory? = null
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun LiveVideo(
    url: String,
    muted: Boolean,
    modifier: Modifier,
    fit: Boolean,
    frameKey: String?,
    noSignalAction: (@Composable () -> Unit)?,
) {
    val factory = IosVideo.factory
    var attempt by remember(url) { mutableIntStateOf(0) }
    var playing by remember(url, attempt) { mutableStateOf(false) }
    var failed by remember(url, attempt) { mutableStateOf(factory == null) }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (factory != null && !failed) {
            key(url, attempt) {
                val listener = remember {
                    object : VideoListener {
                        override fun onPlaying() { playing = true }
                        override fun onError() { failed = true }
                    }
                }
                UIKitView(
                    factory = { factory.create(url, muted, listener) },
                    modifier = Modifier.fillMaxSize(),
                    update = { view -> factory.setMuted(view, muted) },
                    onRelease = { view -> factory.release(view) },
                    // Toques passam para o Compose (tocar abre a tela cheia).
                    properties = UIKitInteropProperties(interactionMode = null),
                )
            }
            if (!playing) {
                CircularProgressIndicator(color = Palette.Ivory.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else {
            NoSignal(
                if (factory == null) "Vídeo indisponível neste aparelho" else "Sem sinal da câmera",
                onRetry = if (factory == null) null else ({ attempt++ }),
                extra = noSignalAction,
            )
        }
    }
}
