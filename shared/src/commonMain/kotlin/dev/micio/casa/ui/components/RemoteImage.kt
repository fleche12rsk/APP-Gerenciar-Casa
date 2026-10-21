package dev.micio.casa.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.micio.casa.core.Http
import dev.micio.casa.ui.decodeImage
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Pequeno cache em memória: guarda as últimas [capacity] imagens usadas. */
class ImageCache<K>(private val capacity: Int) {
    private val entries = LinkedHashMap<K, ImageBitmap>()

    fun get(key: K): ImageBitmap? = entries.remove(key)?.also { entries[key] = it }

    fun put(key: K, value: ImageBitmap) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}

private val cache = ImageCache<String>(64)

/** Ícones dos apps da TV, servidos pela própria TV na rede local. */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(cache.get(url)) }
    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeImage(Http.lanClient.get(url).body<ByteArray>()) }.getOrNull()?.also { cache.put(url, it) }
        }
    }
    bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier) }
}
