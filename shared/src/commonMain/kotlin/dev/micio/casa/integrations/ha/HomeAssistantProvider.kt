package dev.micio.casa.integrations.ha

import dev.micio.casa.core.HaConfig
import dev.micio.casa.core.formatDecimal
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import dev.micio.casa.model.VacuumCommand
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * Tudo que já está no Home Assistant entra no app: aspiradores, Echos (Alexa Media
 * Player), câmeras, sensores, cenas e scripts.
 */
class HomeAssistantProvider(config: HaConfig) : DeviceProvider {

    override val source = Source.HOME_ASSISTANT
    override val pollSeconds = 5

    val client = HomeAssistantClient(config)

    /** Últimos estados lidos — a tela da Alexa usa para listar os Echos. */
    var lastStates: List<HaState> = emptyList()
        private set

    override suspend fun fetch(): ProviderSnapshot {
        val states = client.states()
        lastStates = states
        return ProviderSnapshot(
            devices = states.mapNotNull { it.toDevice() },
            scenes = states.filter { it.domain == "scene" || it.domain == "script" }
                .map { Scene("ha:${it.entity_id}", it.friendlyName, Source.HOME_ASSISTANT) },
        )
    }

    override suspend fun execute(device: Device, action: DeviceAction) {
        val entityId = device.id.removePrefix("ha:")
        val domain = entityId.substringBefore('.')
        val target = buildJsonObject { put("entity_id", entityId) }
        when (action) {
            is DeviceAction.Power -> {
                val service = when (domain) {
                    "cover" -> if (action.on) "open_cover" else "close_cover"
                    "vacuum" -> if (action.on) "start" else "return_to_base"
                    "lock" -> if (action.on) "unlock" else "lock"
                    else -> if (action.on) "turn_on" else "turn_off"
                }
                client.callService(domain, service, target)
            }
            is DeviceAction.Brightness -> client.callService("light", "turn_on", buildJsonObject {
                put("entity_id", entityId)
                put("brightness_pct", (action.level * 100).roundToInt().coerceIn(1, 100))
            })
            is DeviceAction.Vacuum -> client.callService("vacuum", when (action.command) {
                VacuumCommand.START -> "start"
                VacuumCommand.PAUSE -> "pause"
                VacuumCommand.DOCK -> "return_to_base"
                VacuumCommand.LOCATE -> "locate"
            }, target)
        }
    }

    override suspend fun runScene(scene: Scene) {
        val entityId = scene.id.removePrefix("ha:")
        client.callService(entityId.substringBefore('.'), "turn_on", buildJsonObject { put("entity_id", entityId) })
    }

    // ---- Alexa (integração "Alexa Media Player" do HACS) ----

    /** Echos aparecem como media_player com o atributo last_called. */
    fun alexaDevices(): List<HaState> =
        lastStates.filter { it.domain == "media_player" && it.isAlexa }

    /** A Alexa executa o texto como se tivesse ouvido o comando falado. */
    suspend fun alexaCommand(entityId: String, text: String) =
        client.callService("media_player", "play_media", buildJsonObject {
            put("entity_id", entityId)
            put("media_content_id", text)
            put("media_content_type", "custom")
        })

    suspend fun alexaAnnounce(entityId: String, text: String, announce: Boolean) =
        client.callService("notify", "alexa_media", buildJsonObject {
            put("message", text)
            putJsonArray("target") { add(entityId) }
            putJsonObject("data") { put("type", if (announce) "announce" else "tts") }
        })

    suspend fun setVolume(entityId: String, level: Float) =
        client.callService("media_player", "volume_set", buildJsonObject {
            put("entity_id", entityId)
            put("volume_level", level.coerceIn(0f, 1f))
        })

    suspend fun mediaCommand(entityId: String, service: String) =
        client.callService("media_player", service, buildJsonObject { put("entity_id", entityId) })

    private fun HaState.toDevice(): Device? {
        val kind = when (domain) {
            "light" -> DeviceKind.LIGHT
            "switch", "input_boolean" -> if (attr("device_class") == "outlet") DeviceKind.OUTLET else DeviceKind.SWITCH
            "fan" -> DeviceKind.FAN
            "cover" -> DeviceKind.COVER
            "vacuum" -> DeviceKind.VACUUM
            "media_player" -> if (isAlexa) DeviceKind.SPEAKER
                else if (attr("device_class") == "tv") DeviceKind.TV else DeviceKind.SPEAKER
            "camera" -> DeviceKind.CAMERA
            "climate" -> DeviceKind.CLIMATE
            "sensor" -> if (attr("device_class") in setOf("temperature", "humidity")) DeviceKind.SENSOR else return null
            else -> return null
        }
        val unavailable = state == "unavailable" || state == "unknown"
        val isOn = when (kind) {
            DeviceKind.CAMERA, DeviceKind.SENSOR, DeviceKind.CLIMATE -> null
            DeviceKind.COVER -> state == "open" || state == "opening"
            DeviceKind.VACUUM -> state == "cleaning"
            DeviceKind.TV, DeviceKind.SPEAKER -> state !in setOf("off", "standby", "unavailable", "unknown")
            else -> state == "on"
        }
        return Device(
            id = "ha:$entity_id",
            name = friendlyName,
            kind = kind,
            source = Source.HOME_ASSISTANT,
            online = !unavailable,
            isOn = if (unavailable) null else isOn,
            brightness = attributes["brightness"]?.jsonPrimitive?.doubleOrNull?.let { (it / 255).toFloat() },
            status = statusText(kind),
            battery = attributes["battery_level"]?.jsonPrimitive?.intOrNull,
        )
    }

    private fun HaState.statusText(kind: DeviceKind): String? = when (kind) {
        DeviceKind.VACUUM -> VACUUM_STATES[state] ?: state
        DeviceKind.SENSOR -> state.toDoubleOrNull()?.let { value ->
            val unit = attr("unit_of_measurement").orEmpty()
            "${formatDecimal(value, 1).removeSuffix(",0")} $unit".trim()
        }
        DeviceKind.CLIMATE -> attributes["current_temperature"]?.jsonPrimitive?.doubleOrNull?.let { formatDecimal(it, 1) + " °C" }
        DeviceKind.SPEAKER, DeviceKind.TV -> attr("media_title")
        else -> null
    }

    private companion object {
        val VACUUM_STATES = mapOf(
            "cleaning" to "Limpando",
            "docked" to "Na base",
            "returning" to "Voltando à base",
            "idle" to "Parado",
            "paused" to "Pausado",
            "error" to "Precisa de atenção",
        )
    }
}

val HaState.isAlexa: Boolean
    get() = domain == "media_player" && (attributes.containsKey("last_called") || attributes.containsKey("last_called_timestamp"))

