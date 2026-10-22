package dev.micio.casa.integrations.tv

import dev.micio.casa.core.Http
import dev.micio.casa.integrations.IntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Roku e TVs com Roku embutido (TCL, Semp, AOC...): protocolo ECP, HTTP simples em
 * http://IP:8060. Não pede pareamento, mas "Controle por apps móveis" precisa
 * estar liberado na TV.
 */
class RokuClient(private val host: String, private val http: HttpClient = Http.lanClient) : TvController {

    private val base = "http://$host:8060"
    private val _state = MutableStateFlow(TvConnection.DISCONNECTED)
    override val connection: StateFlow<TvConnection> = _state.asStateFlow()
    override val volume: StateFlow<Int?> = MutableStateFlow(null)
    override val paired = true

    override suspend fun connect(timeoutMs: Long) {
        _state.value = TvConnection.CONNECTING
        try {
            withTimeout(timeoutMs.coerceAtMost(8_000)) { http.get("$base/query/device-info").ensureOk() }
            _state.value = TvConnection.CONNECTED
        } catch (e: Exception) {
            _state.value = TvConnection.DISCONNECTED
            throw IntegrationException("O Roku não respondeu. Ele está ligado e na mesma rede?")
        }
    }

    override fun close() {
        _state.value = TvConnection.DISCONNECTED
    }

    override suspend fun powerOff() {
        keypress("PowerOff")
        _state.value = TvConnection.DISCONNECTED
    }

    /** TVs Roku com "início rápido" ligado aceitam ligar pela rede. */
    override suspend fun powerOn(): Boolean = runCatching { keypress("PowerOn") }.isSuccess

    override suspend fun press(key: TvKey) = keypress(KEYS.getValue(key))

    override suspend fun apps(): List<TvApp> =
        parseApps(http.get("$base/query/apps").ensureOk().bodyAsText())
            .filter { it.second != "tvin" }
            .map { (app, _) -> app.copy(icon = "$base/query/icon/${app.id}") }

    override suspend fun launch(appId: String) {
        http.post("$base/launch/$appId").ensureOk()
    }

    override suspend fun inputs(): List<TvInput> =
        parseApps(http.get("$base/query/apps").ensureOk().bodyAsText())
            .filter { it.second == "tvin" }
            .map { (app, _) -> TvInput(app.id, app.title) }

    override suspend fun switchInput(inputId: String) = launch(inputId)

    private suspend fun keypress(key: String) {
        http.post("$base/keypress/$key").ensureOk()
        _state.value = TvConnection.CONNECTED
    }

    private fun HttpResponse.ensureOk(): HttpResponse {
        if (status.value == 403) throw IntegrationException("O Roku bloqueou o controle. Libere “Controle por apps móveis” nas configurações dele.")
        if (!status.isSuccess()) throw IntegrationException("O Roku respondeu com erro ${status.value}.")
        return this
    }

    companion object {
        val KEYS = mapOf(
            TvKey.UP to "Up", TvKey.DOWN to "Down", TvKey.LEFT to "Left", TvKey.RIGHT to "Right",
            TvKey.OK to "Select", TvKey.BACK to "Back", TvKey.HOME to "Home", TvKey.MENU to "Info",
            TvKey.INPUT to "InputTuner", TvKey.VOLUME_UP to "VolumeUp", TvKey.VOLUME_DOWN to "VolumeDown",
            TvKey.MUTE to "VolumeMute", TvKey.CHANNEL_UP to "ChannelUp", TvKey.CHANNEL_DOWN to "ChannelDown",
            // O Roku tem uma tecla só para tocar/pausar.
            TvKey.PLAY to "Play", TvKey.PAUSE to "Play",
        )

        private val APP = Regex("""<app\s+([^>]*)>([^<]*)</app>""")
        private val ATTR = Regex("""(\w+)="([^"]*)"""")

        /** Lê o XML de /query/apps: pares (app, tipo), onde tipo "tvin" são entradas HDMI. */
        fun parseApps(xml: String): List<Pair<TvApp, String>> = APP.findAll(xml).mapNotNull { match ->
            val attrs = ATTR.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val id = attrs["id"] ?: return@mapNotNull null
            TvApp(id, unescape(match.groupValues[2].trim()), null) to (attrs["type"] ?: "appl")
        }.toList()

        private fun unescape(text: String) = text
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    }
}
