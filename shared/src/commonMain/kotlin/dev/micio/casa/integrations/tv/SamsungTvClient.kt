package dev.micio.casa.integrations.tv

import dev.micio.casa.core.Http
import dev.micio.casa.integrations.IntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * TVs Samsung Tizen (2016 em diante): WebSocket "samsung.remote.control" em
 * wss://IP:8002 (com token) ou ws://IP:8001 nos modelos mais antigos. Na primeira
 * conexão a TV pergunta se permite o acesso; o token devolvido é guardado.
 */
class SamsungTvClient(
    private val host: String,
    token: String?,
    private val onToken: (String) -> Unit,
    private val http: HttpClient = Http.lanClient,
) : TvController {

    private val _state = MutableStateFlow(TvConnection.DISCONNECTED)
    override val connection: StateFlow<TvConnection> = _state.asStateFlow()
    override val volume: StateFlow<Int?> = MutableStateFlow(null)

    var token: String? = token
        private set

    // Modelos antigos (porta 8001) não usam token; depois de conectar uma vez, não pedem mais nada.
    private var connectedOnce = false
    override val paired get() = token != null || connectedOnce

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectMutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var reader: Job? = null
    private var appsReply: CompletableDeferred<List<TvApp>>? = null
    private val appTypes = mutableMapOf<String, Int>()

    override suspend fun connect(timeoutMs: Long) = connectMutex.withLock {
        if (isConnected) return@withLock
        var lastError: Throwable? = null
        for (url in urls()) {
            try {
                open(url, timeoutMs)
                return@withLock
            } catch (e: Throwable) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                lastError = e
                val waitedForApproval = _state.value == TvConnection.WAITING_APPROVAL
                close()
                if (waitedForApproval) break
            }
        }
        _state.value = TvConnection.DISCONNECTED
        throw IntegrationException(
            if (lastError is TimeoutCancellationException) "A TV não respondeu. Ela está ligada e na mesma rede?"
            else lastError?.message ?: "Não foi possível conectar à TV."
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun urls(): List<String> {
        val name = Base64.encode(APP_NAME.encodeToByteArray())
        val path = "/api/v2/channels/samsung.remote.control?name=$name"
        return listOf(
            "wss://$host:8002$path" + (token?.let { "&token=$it" } ?: ""),
            "ws://$host:8001$path",
        )
    }

    private suspend fun open(url: String, timeoutMs: Long) {
        _state.value = TvConnection.CONNECTING
        withTimeout(timeoutMs) {
            val ws = http.webSocketSession(url)
            session = ws
            // A TV só responde depois que a pessoa aceita o pedido na tela.
            if (token == null) _state.value = TvConnection.WAITING_APPROVAL
            val ready = CompletableDeferred<Unit>()
            reader = scope.launch {
                try {
                    for (frame in ws.incoming) if (frame is Frame.Text) handle(frame.readText(), ready)
                } catch (_: Throwable) {
                } finally {
                    ready.completeExceptionally(IntegrationException("A TV encerrou a conexão."))
                    if (session === ws) markClosed()
                }
            }
            ready.await()
        }
    }

    private fun handle(text: String, ready: CompletableDeferred<Unit>) {
        val message = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val data = message["data"] as? JsonObject
        when (message["event"]?.jsonPrimitive?.contentOrNull) {
            "ms.channel.connect" -> {
                data?.get("token")?.jsonPrimitive?.contentOrNull?.takeIf { it != token }?.let { newToken ->
                    token = newToken
                    onToken(newToken)
                }
                connectedOnce = true
                _state.value = TvConnection.CONNECTED
                ready.complete(Unit)
            }
            "ms.channel.unauthorized" ->
                ready.completeExceptionally(IntegrationException("Acesso recusado na TV."))
            "ed.installedApp.get" -> {
                val list = data?.get("data")?.jsonArray.orEmpty().mapNotNull { element ->
                    val app = element.jsonObject
                    val id = app["appId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    appTypes[id] = app["app_type"]?.jsonPrimitive?.intOrNull ?: 2
                    TvApp(id, app["name"]?.jsonPrimitive?.contentOrNull ?: id, null)
                }
                appsReply?.complete(list)
            }
        }
    }

    override fun close() {
        val ws = session
        session = null
        reader?.cancel()
        reader = null
        scope.launch { runCatching { ws?.close() } }
        markClosed()
    }

    private fun markClosed() {
        session = null
        _state.value = TvConnection.DISCONNECTED
    }

    private suspend fun send(message: JsonObject) {
        if (!isConnected) connect(timeoutMs = 4_000)
        val ws = session ?: throw IntegrationException("A conexão com a TV caiu.")
        ws.send(Frame.Text(message.toString()))
    }

    private suspend fun sendKey(code: String) = send(remoteKey(code))

    override suspend fun powerOff() {
        sendKey("KEY_POWER")
        close()
    }

    override suspend fun press(key: TvKey) = sendKey(KEYS.getValue(key))

    override suspend fun apps(): List<TvApp> {
        val reply = CompletableDeferred<List<TvApp>>()
        appsReply = reply
        send(buildJsonObject {
            put("method", "ms.channel.emit")
            putJsonObject("params") {
                put("event", "ed.installedApp.get")
                put("to", "host")
            }
        })
        return try {
            withTimeout(8_000) { reply.await() }
        } catch (e: TimeoutCancellationException) {
            emptyList()
        }
    }

    override suspend fun launch(appId: String) = send(buildJsonObject {
        put("method", "ms.channel.emit")
        putJsonObject("params") {
            put("event", "ed.apps.launch")
            put("to", "host")
            putJsonObject("data") {
                put("appId", appId)
                // Apps nativos (tipo 4) abrem com NATIVE_LAUNCH; os demais, por deep link.
                put("action_type", if (appTypes[appId] == 4) "NATIVE_LAUNCH" else "DEEP_LINK")
            }
        }
    })

    companion object {
        const val APP_NAME = "Casa"

        val KEYS = mapOf(
            TvKey.UP to "KEY_UP", TvKey.DOWN to "KEY_DOWN", TvKey.LEFT to "KEY_LEFT", TvKey.RIGHT to "KEY_RIGHT",
            TvKey.OK to "KEY_ENTER", TvKey.BACK to "KEY_RETURN", TvKey.HOME to "KEY_HOME", TvKey.MENU to "KEY_MENU",
            TvKey.INPUT to "KEY_SOURCE", TvKey.VOLUME_UP to "KEY_VOLUP", TvKey.VOLUME_DOWN to "KEY_VOLDOWN",
            TvKey.MUTE to "KEY_MUTE", TvKey.CHANNEL_UP to "KEY_CHUP", TvKey.CHANNEL_DOWN to "KEY_CHDOWN",
            TvKey.PLAY to "KEY_PLAY", TvKey.PAUSE to "KEY_PAUSE",
        )

        fun remoteKey(code: String) = buildJsonObject {
            put("method", "ms.remote.control")
            putJsonObject("params") {
                put("Cmd", "Click")
                put("DataOfCmd", code)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            }
        }
    }
}
