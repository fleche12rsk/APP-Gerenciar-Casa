package dev.micio.casa.integrations.tv

import kotlinx.coroutines.flow.StateFlow

enum class TvConnection { DISCONNECTED, CONNECTING, WAITING_APPROVAL, CONNECTED }

/** Botões comuns a qualquer controle remoto; cada marca traduz para o seu protocolo. */
enum class TvKey { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, INPUT, VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN, PLAY, PAUSE }

data class TvApp(val id: String, val title: String, val icon: String?)
data class TvInput(val id: String, val label: String)

/** Uma TV na rede local, de qualquer marca suportada. */
interface TvController {
    val connection: StateFlow<TvConnection>

    /** Volume de 0 a 100, quando a TV informa; null quando não informa. */
    val volume: StateFlow<Int?>

    /** false enquanto a TV ainda não autorizou este app (marcas que pedem pareamento). */
    val paired: Boolean

    val isConnected get() = connection.value == TvConnection.CONNECTED

    /** Conecta; nas marcas com pareamento, a TV mostra um pedido na primeira vez. */
    suspend fun connect(timeoutMs: Long = 60_000)
    fun close()

    suspend fun powerOff()
    /** Tentativa de ligar sem Wake-on-LAN (só algumas marcas aceitam). */
    suspend fun powerOn(): Boolean = false

    suspend fun press(key: TvKey)
    suspend fun setVolume(level: Int) = Unit
    suspend fun refreshVolume() = Unit

    suspend fun apps(): List<TvApp>
    suspend fun launch(appId: String)
    suspend fun inputs(): List<TvInput> = emptyList()
    suspend fun switchInput(inputId: String) = Unit
}
