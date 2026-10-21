package dev.micio.casa.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import java.io.File

class ConfigRepository(context: Context) {

    private val store = SecureStore(File(context.filesDir, "config.bin"))
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    val current: AppConfig get() = _config.value

    @Synchronized
    fun update(transform: (AppConfig) -> AppConfig) {
        val next = transform(_config.value)
        if (next == _config.value) return
        _config.value = next
        // Se o Keystore falhar, a mudança vale até fechar o app em vez de derrubá-lo.
        runCatching { store.write(Http.json.encodeToString(next)) }
    }

    private fun load(): AppConfig =
        store.read()?.let { runCatching { Http.json.decodeFromString<AppConfig>(it) }.getOrNull() } ?: AppConfig()
}
