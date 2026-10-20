package dev.micio.casa.integrations.lg

import dev.micio.casa.core.Http
import dev.micio.casa.integrations.IntegrationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TvApp(val id: String, val title: String, val icon: String?)
data class TvInput(val id: String, val label: String)

/**
 * Protocolo SSAP das TVs LG webOS: WebSocket local em wss://IP:3001 (firmwares
 * recentes) ou ws://IP:3000. Na primeira conexão a TV pergunta se aceita o
 * controle; a chave devolvida é guardada para as próximas vezes.
 */
class WebOsClient(
    private val host: String,
    clientKey: String?,
    private val onClientKey: (String) -> Unit,
    private val http: OkHttpClient = Http.lanClient,
) {
    enum class State { DISCONNECTED, CONNECTING, WAITING_APPROVAL, CONNECTED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _volume = MutableStateFlow<Int?>(null)
    val volume: StateFlow<Int?> = _volume.asStateFlow()

    private val connectMutex = Mutex()
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var socket: WebSocket? = null
    private var pointer: WebSocket? = null
    private var registered: CompletableDeferred<Unit>? = null

    var clientKey: String? = clientKey
        private set

    val isConnected get() = _state.value == State.CONNECTED

    /** Conecta e registra. Sem chave salva, a TV mostra o pedido de autorização. */
    suspend fun connect(timeoutMs: Long = 60_000) = connectMutex.withLock {
        if (isConnected) return@withLock
        var lastError: Throwable? = null
        for (url in listOf("wss://$host:3001", "ws://$host:3000")) {
            try {
                open(url, timeoutMs)
                return@withLock
            } catch (e: Throwable) {
                lastError = e
                val wasWaitingApproval = _state.value == State.WAITING_APPROVAL
                close()
                if (wasWaitingApproval) break
            }
        }
        _state.value = State.DISCONNECTED
        throw IntegrationException(
            if (lastError is kotlinx.coroutines.TimeoutCancellationException) "A TV não respondeu. Ela está ligada e na mesma rede?"
            else "Não foi possível conectar à TV: ${lastError?.message ?: "erro desconhecido"}"
        )
    }

    private suspend fun open(url: String, timeoutMs: Long) {
        _state.value = State.CONNECTING
        val done = CompletableDeferred<Unit>()
        registered = done
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener(done))
        withTimeout(timeoutMs) { done.await() }
    }

    fun close() {
        pointer?.close(1000, null)
        pointer = null
        socket?.close(1000, null)
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        _state.value = State.DISCONNECTED
    }

    suspend fun request(uri: String, payload: JsonObject = JsonObject(emptyMap())): JsonObject {
        if (!isConnected) connect(timeoutMs = 4_000)
        val id = "req_${ids.incrementAndGet()}"
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val message = buildJsonObject {
            put("id", id)
            put("type", "request")
            put("uri", uri)
            put("payload", payload)
        }
        if (socket?.send(message.toString()) != true) {
            pending.remove(id)
            close()
            throw IntegrationException("A conexão com a TV caiu.")
        }
        return try {
            withTimeout(8_000) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    // ---- Comandos ----

    suspend fun powerOff() {
        request("ssap://system/turnOff")
        close()
    }

    suspend fun volumeUp() = request("ssap://audio/volumeUp").also { refreshVolume() }
    suspend fun volumeDown() = request("ssap://audio/volumeDown").also { refreshVolume() }
    suspend fun setVolume(value: Int) {
        request("ssap://audio/setVolume", buildJsonObject { put("volume", value.coerceIn(0, 100)) })
        _volume.value = value
    }
    suspend fun setMute(mute: Boolean) = request("ssap://audio/setMute", buildJsonObject { put("mute", mute) })
    suspend fun channelUp() = request("ssap://tv/channelUp")
    suspend fun channelDown() = request("ssap://tv/channelDown")
    suspend fun play() = request("ssap://media.controls/play")
    suspend fun pause() = request("ssap://media.controls/pause")
    suspend fun toast(message: String) = request("ssap://system.notifications/createToast", buildJsonObject { put("message", message) })
    suspend fun launch(appId: String) = request("ssap://system.launcher/launch", buildJsonObject { put("id", appId) })
    suspend fun switchInput(inputId: String) = request("ssap://tv/switchInput", buildJsonObject { put("inputId", inputId) })

    suspend fun refreshVolume() {
        val payload = runCatching { request("ssap://audio/getVolume") }.getOrNull() ?: return
        // webOS 5+ devolve dentro de volumeStatus; versões antigas, direto no payload.
        val status = payload["volumeStatus"]?.jsonObject ?: payload
        _volume.value = status["volume"]?.jsonPrimitive?.intOrNull
    }

    suspend fun apps(): List<TvApp> =
        request("ssap://com.webos.applicationManager/listLaunchPoints")["launchPoints"]?.jsonArray.orEmpty().map {
            val obj = it.jsonObject
            TvApp(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                icon = (obj["largeIcon"] ?: obj["icon"])?.jsonPrimitive?.contentOrNull,
            )
        }.filter { it.id.isNotEmpty() }

    suspend fun inputs(): List<TvInput> =
        request("ssap://tv/getExternalInputList")["devices"]?.jsonArray.orEmpty().map {
            val obj = it.jsonObject
            TvInput(obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }

    /** Botões do controle (setas, OK, voltar, home...) passam por um socket separado. */
    suspend fun button(name: String) {
        val socket = pointer ?: openPointer()
        if (!socket.send("type:button\nname:$name\n\n")) {
            pointer = null
            openPointer().send("type:button\nname:$name\n\n")
        }
    }

    private suspend fun openPointer(): WebSocket {
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")["socketPath"]
            ?.jsonPrimitive?.contentOrNull ?: throw IntegrationException("A TV não liberou o controle de botões.")
        val opened = CompletableDeferred<Unit>()
        val ws = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                if (pointer === webSocket) pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
            }
        })
        withTimeout(5_000) { opened.await() }
        pointer = ws
        return ws
    }

    private inner class Listener(private val done: CompletableDeferred<Unit>) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val register = buildJsonObject {
                put("id", "register_0")
                put("type", "register")
                put("payload", WebOsManifest.registerPayload(clientKey))
            }
            webSocket.send(register.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val type = message["type"]?.jsonPrimitive?.contentOrNull
            val id = message["id"]?.jsonPrimitive?.contentOrNull
            val payload = message["payload"] as? JsonObject ?: JsonObject(emptyMap())
            when {
                type == "registered" -> {
                    payload["client-key"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it != clientKey }
                        ?.let { key ->
                            clientKey = key
                            onClientKey(key)
                        }
                    _state.value = State.CONNECTED
                    done.complete(Unit)
                }
                id == "register_0" && payload["pairingType"]?.jsonPrimitive?.contentOrNull == "PROMPT" ->
                    _state.value = State.WAITING_APPROVAL
                id == "register_0" && type == "error" ->
                    done.completeExceptionally(IntegrationException("Pareamento recusado na TV."))
                id != null -> pending[id]?.let { deferred ->
                    val ok = type != "error" && payload["returnValue"]?.jsonPrimitive?.booleanOrNull != false
                    if (ok) deferred.complete(payload)
                    else deferred.completeExceptionally(IntegrationException(
                        message["error"]?.jsonPrimitive?.contentOrNull ?: "A TV recusou o comando."
                    ))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            done.completeExceptionally(t)
            if (socket === webSocket) close()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket) close()
        }
    }
}
