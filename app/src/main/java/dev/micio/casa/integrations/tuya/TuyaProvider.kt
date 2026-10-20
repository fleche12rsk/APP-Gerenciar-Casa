package dev.micio.casa.integrations.tuya

import dev.micio.casa.core.TuyaConfig
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.IntegrationException
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import dev.micio.casa.model.VacuumCommand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

/**
 * Interruptores, tomadas, lâmpadas e controles IR que usam a nuvem Tuya. NovaDigital,
 * Intelbras Izy, WAG e boa parte das marcas "smart" vendidas no Brasil são Tuya por dentro.
 */
class TuyaProvider(config: TuyaConfig) : DeviceProvider {

    override val source = Source.TUYA
    override val pollSeconds = 15

    private val client = TuyaClient(config)
    private var uid: String? = null
    private var homeIds: List<String> = emptyList()
    private var raw: Map<String, TuyaDevice> = emptyMap()

    override suspend fun fetch(): ProviderSnapshot {
        val devices = listDevices()
        raw = devices.associateBy { it.id }
        if (uid == null) uid = devices.firstNotNullOfOrNull { it.uid }
        return ProviderSnapshot(
            devices = devices.filterNot { it.isInfrared }.flatMap { it.toDevices() },
            scenes = runCatching { listScenes() }.getOrDefault(emptyList()),
        )
    }

    override suspend fun execute(device: Device, action: DeviceAction) {
        val (tuyaId, code) = device.id.removePrefix("tuya:").split(":", limit = 2).let { it[0] to it.getOrNull(1) }
        val tuya = raw[tuyaId] ?: throw IntegrationException("Aparelho Tuya não encontrado. Atualize a lista.")
        val commands: List<Pair<String, JsonPrimitive>> = when (action) {
            is DeviceAction.Power -> when (device.kind) {
                DeviceKind.COVER -> listOf("control" to JsonPrimitive(if (action.on) "open" else "close"))
                DeviceKind.VACUUM -> listOf("power_go" to JsonPrimitive(action.on))
                else -> listOf((code ?: tuya.powerCodes.first()) to JsonPrimitive(action.on))
            }
            is DeviceAction.Brightness -> {
                val brightCode = tuya.brightnessCode ?: return
                val (min, max) = if (brightCode.endsWith("v2")) 10 to 1000 else 25 to 255
                listOf(brightCode to JsonPrimitive((min + (max - min) * action.level).toInt()))
            }
            is DeviceAction.Vacuum -> when (action.command) {
                VacuumCommand.START -> listOf("power_go" to JsonPrimitive(true))
                VacuumCommand.PAUSE -> listOf("pause" to JsonPrimitive(true))
                VacuumCommand.DOCK -> listOf("switch_charge" to JsonPrimitive(true))
                VacuumCommand.LOCATE -> listOf("seek" to JsonPrimitive(true))
            }
        }
        sendCommands(tuyaId, commands)
    }

    override suspend fun runScene(scene: Scene) {
        val (homeId, sceneId) = scene.id.removePrefix("tuya:").split(":", limit = 2)
        client.post("/v1.0/homes/$homeId/scenes/$sceneId/trigger", JsonObject(emptyMap()))
    }

    private suspend fun sendCommands(deviceId: String, commands: List<Pair<String, JsonPrimitive>>) {
        client.post("/v1.0/devices/$deviceId/commands", buildJsonObject {
            putJsonArray("commands") {
                commands.forEach { (code, value) -> addJsonObject { put("code", code); put("value", value) } }
            }
        })
    }

    private suspend fun listDevices(): List<TuyaDevice> {
        val out = mutableListOf<TuyaDevice>()
        var lastRowKey: String? = null
        do {
            val query = "/v1.0/iot-01/associated-users/devices?size=100" + (lastRowKey?.let { "&last_row_key=$it" } ?: "")
            val result = client.get(query).jsonObject
            result["devices"]?.jsonArray?.mapTo(out) { TuyaDevice.from(it.jsonObject) }
            val hasMore = result["has_more"]?.jsonPrimitive?.booleanOrNull == true
            lastRowKey = result["last_row_key"]?.jsonPrimitive?.contentOrNull?.takeIf { hasMore && it.isNotEmpty() }
        } while (lastRowKey != null)
        return out
    }

    private suspend fun listScenes(): List<Scene> {
        val user = uid ?: return emptyList()
        if (homeIds.isEmpty()) {
            homeIds = client.get("/v1.0/users/$user/homes").asList().mapNotNull {
                it.jsonObject["home_id"]?.jsonPrimitive?.contentOrNull
            }
        }
        return homeIds.flatMap { homeId ->
            client.get("/v1.1/homes/$homeId/scenes").asList().mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["scene_id"]?.jsonPrimitive?.contentOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull
                val name = obj["name"]?.jsonPrimitive?.contentOrNull
                if (id == null || name == null) null else Scene("tuya:$homeId:$id", name, Source.TUYA)
            }
        }
    }

    // ---- Controles infravermelho (hub "wnykq") ----

    fun infraredHubs(): List<Pair<String, String>> =
        raw.values.filter { it.category == "wnykq" }.map { it.id to it.name }

    suspend fun remotes(hubId: String): List<IrRemote> =
        client.get("/v2.0/infrareds/$hubId/remotes").asList().map { element ->
            val obj = element.jsonObject
            IrRemote(
                hubId = hubId,
                id = obj.string("remote_id").orEmpty(),
                name = obj.string("remote_name") ?: "Controle",
                categoryId = obj["category_id"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }

    suspend fun keys(remote: IrRemote): List<IrKey> {
        val result = client.get("/v2.0/infrareds/${remote.hubId}/remotes/${remote.id}/keys").jsonObject
        return result["key_list"]?.jsonArray.orEmpty().map { element ->
            val obj = element.jsonObject
            IrKey(
                key = obj.string("key").orEmpty(),
                keyId = obj["key_id"]?.jsonPrimitive?.intOrNull ?: 0,
                name = obj.string("key_name") ?: obj.string("key").orEmpty(),
            )
        }
    }

    suspend fun pressKey(remote: IrRemote, key: IrKey) {
        client.post("/v2.0/infrareds/${remote.hubId}/remotes/${remote.id}/command", buildJsonObject {
            put("category_id", remote.categoryId)
            put("key_id", key.keyId)
            put("key", key.key)
        })
    }

    /** Ar-condicionado: o estado completo vai junto em cada comando. */
    suspend fun sendAirConditioner(remote: IrRemote, state: AcState) {
        client.post("/v2.0/infrareds/${remote.hubId}/air-conditioners/${remote.id}/scenes-command", buildJsonObject {
            put("power", if (state.on) 1 else 0)
            put("mode", state.mode)
            put("temp", state.temperature)
            put("wind", state.fan)
        })
    }
}

data class IrRemote(val hubId: String, val id: String, val name: String, val categoryId: Int) {
    val isAirConditioner get() = categoryId == 5
}

data class IrKey(val key: String, val keyId: Int, val name: String)

/** mode: 0 frio, 1 quente, 2 auto, 3 ventilar, 4 desumidificar · fan: 0 auto, 1 baixo, 2 médio, 3 alto */
data class AcState(val on: Boolean = true, val mode: Int = 0, val temperature: Int = 23, val fan: Int = 0)

private data class TuyaDevice(
    val id: String,
    val name: String,
    val category: String,
    val online: Boolean,
    val uid: String?,
    val status: Map<String, JsonElement>,
) {
    val isInfrared get() = category == "wnykq" || category.startsWith("infrared") || category == "qt"

    val powerCodes: List<String>
        get() {
            val gangs = status.keys.filter { GANG.matches(it) }.sortedBy { it.removePrefix("switch_").toInt() }
            return gangs.ifEmpty { listOfNotNull(status.keys.firstOrNull { it in SINGLE_POWER }) }
        }

    val brightnessCode get() = listOf("bright_value_v2", "bright_value", "bright_value_1").firstOrNull { it in status }

    fun toDevices(): List<Device> {
        val kind = kindFor(category)
        if (kind == DeviceKind.VACUUM || kind == DeviceKind.COVER || kind == DeviceKind.SENSOR) {
            return listOf(Device(
                id = "tuya:$id", name = name, kind = kind, source = Source.TUYA, online = online,
                isOn = when (kind) {
                    DeviceKind.COVER -> status["percent_state"]?.jsonPrimitive?.intOrNull?.let { it > 0 }
                    DeviceKind.VACUUM -> status["power_go"]?.jsonPrimitive?.booleanOrNull
                    else -> null
                },
                status = sensorText(),
                battery = (status["electricity_left"] ?: status["battery_percentage"])?.jsonPrimitive?.intOrNull,
            ))
        }
        val codes = powerCodes
        if (codes.isEmpty()) {
            return listOf(Device("tuya:$id", name, kind, Source.TUYA, online = online, status = sensorText()))
        }
        val brightness = brightnessCode?.let { code ->
            val value = status[code]?.jsonPrimitive?.doubleOrNull ?: return@let null
            val (min, max) = if (code.endsWith("v2")) 10.0 to 1000.0 else 25.0 to 255.0
            ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
        }
        return codes.mapIndexed { index, code ->
            Device(
                id = "tuya:$id:$code",
                name = if (codes.size > 1) "$name ${index + 1}" else name,
                kind = kind,
                source = Source.TUYA,
                online = online,
                isOn = status[code]?.jsonPrimitive?.booleanOrNull,
                brightness = brightness,
            )
        }
    }

    private fun sensorText(): String? {
        val temp = (status["va_temperature"] ?: status["temp_current"])?.jsonPrimitive?.doubleOrNull
        val humidity = (status["va_humidity"] ?: status["humidity_value"])?.jsonPrimitive?.doubleOrNull
        return listOfNotNull(
            temp?.let { "%.1f °C".format(if (it > 100) it / 10 else it) },
            humidity?.let { "%.0f%%".format(if (it > 100) it / 10 else it) },
        ).joinToString(" · ").ifEmpty { null }
    }

    companion object {
        val GANG = Regex("switch_\\d+")
        val SINGLE_POWER = listOf("switch_led", "switch", "fan_switch")

        fun from(obj: JsonObject) = TuyaDevice(
            id = obj.string("id").orEmpty(),
            name = obj.string("name") ?: "Aparelho",
            category = obj.string("category").orEmpty(),
            online = obj["online"]?.jsonPrimitive?.booleanOrNull ?: true,
            uid = obj.string("uid"),
            status = obj["status"]?.jsonArray.orEmpty().mapNotNull { element ->
                val s = element.jsonObject
                val code = s.string("code") ?: return@mapNotNull null
                code to (s["value"] ?: return@mapNotNull null)
            }.toMap(),
        )

        fun kindFor(category: String) = when (category) {
            "dj", "dd", "xdd", "fwd", "dc", "tgq", "tgkg", "gyd", "fsd" -> DeviceKind.LIGHT
            "cz", "pc" -> DeviceKind.OUTLET
            "kg", "tdq", "qjdcz" -> DeviceKind.SWITCH
            "fs", "fskg" -> DeviceKind.FAN
            "cl", "clkg" -> DeviceKind.COVER
            "sd" -> DeviceKind.VACUUM
            "sp" -> DeviceKind.CAMERA
            "kt", "ktkzq", "wk" -> DeviceKind.CLIMATE
            "wsdcg", "mcs", "pir", "ywbj", "rqbj", "sj" -> DeviceKind.SENSOR
            else -> DeviceKind.OTHER
        }
    }
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonElement.asList(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> (this["list"] ?: this["scenes"])?.jsonArray.orEmpty()
    else -> emptyList()
}
