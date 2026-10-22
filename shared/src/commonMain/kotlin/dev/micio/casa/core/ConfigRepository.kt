package dev.micio.casa.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString

class ConfigRepository(private val storage: ConfigStorage) {

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    val current: AppConfig get() = _config.value

    fun update(transform: (AppConfig) -> AppConfig) {
        val before = _config.value
        _config.update(transform)
        val after = _config.value
        if (after == before) return
        // Se o armazenamento falhar, a mudança vale até fechar o app em vez de derrubá-lo.
        runCatching { storage.write(Http.json.encodeToString(after)) }
    }

    private fun load(): AppConfig {
        val stored = storage.read()?.let { runCatching { Http.json.decodeFromString<AppConfig>(it) }.getOrNull() }
        return stored?.migrated() ?: AppConfig()
    }
}

/** Atualiza configurações salvas por versões anteriores do app. */
fun AppConfig.migrated(): AppConfig {
    val legacy = tv ?: return this
    if (tvs.isNotEmpty()) return copy(tv = null)
    return copy(
        tvs = listOf(TvConfig("tv1", TvBrand.LG, legacy.host, legacy.mac, legacy.clientKey, legacy.name)),
        tv = null,
    )
}
