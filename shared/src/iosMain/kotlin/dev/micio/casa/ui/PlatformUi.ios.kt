package dev.micio.casa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * No iPhone/iPad o comando abre um campo de texto; o ditado do próprio teclado
 * (microfone) transcreve a fala, sem o app precisar de permissão de microfone.
 */
@Composable
actual fun rememberVoiceInput(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onResult)
    if (open) {
        CommandDialog(
            onDismiss = { open = false },
            onSubmit = { text ->
                open = false
                latest(text)
            },
        )
    }
    return remember { { open = true } }
}

/** O iOS não tem botão "voltar"; a navegação é pelas abas. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
