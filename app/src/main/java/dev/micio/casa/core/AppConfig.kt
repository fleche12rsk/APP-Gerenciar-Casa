package dev.micio.casa.core

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val tuya: TuyaConfig? = null,
    val tv: LgTvConfig? = null,
    val homeAssistant: HaConfig? = null,
    val cameras: List<CameraConfig> = emptyList(),
    val overrides: Map<String, DeviceOverride> = emptyMap(),
    val rooms: List<String> = listOf("Sala", "Cozinha", "Quarto", "Escritório", "Área externa"),
    val keepScreenOn: Boolean = true,
    val immersive: Boolean = false,
)

@Serializable
data class TuyaConfig(
    val clientId: String,
    val clientSecret: String,
    val region: TuyaRegion = TuyaRegion.AMERICA,
)

@Serializable
enum class TuyaRegion(val label: String, val host: String) {
    AMERICA("América Ocidental (Brasil)", "https://openapi.tuyaus.com"),
    AMERICA_EAST("América Oriental", "https://openapi-ueaz.tuyaus.com"),
    EUROPE("Europa Central", "https://openapi.tuyaeu.com"),
    EUROPE_WEST("Europa Ocidental", "https://openapi-weaz.tuyaeu.com"),
    CHINA("China", "https://openapi.tuyacn.com"),
    INDIA("Índia", "https://openapi.tuyain.com"),
}

@Serializable
data class LgTvConfig(
    val host: String,
    val mac: String = "",
    val clientKey: String? = null,
    val name: String = "TV LG",
)

@Serializable
data class HaConfig(
    val url: String,
    val token: String,
    /** entity_id do Echo usado para comandos de voz (Alexa Media Player). */
    val alexaEntity: String? = null,
)

@Serializable
data class CameraConfig(
    val id: String,
    val name: String,
    val brand: CameraBrand,
    val url: String,
    val subUrl: String? = null,
    val room: String? = null,
)

@Serializable
enum class CameraBrand(val label: String) {
    EZVIZ("EZVIZ"),
    ICSEE("ICSee / XMEye"),
    GENERIC("Outra (RTSP/ONVIF)"),
}

@Serializable
data class DeviceOverride(
    val name: String? = null,
    val room: String? = null,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
)
