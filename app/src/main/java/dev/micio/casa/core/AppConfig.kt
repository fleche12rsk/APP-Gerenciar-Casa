package dev.micio.casa.core

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val tuya: TuyaConfig? = null,
    val tv: LgTvConfig? = null,
    val homeAssistant: HaConfig? = null,
    val cameras: List<CameraConfig> = emptyList(),
    val weather: WeatherPlace? = null,
    /** Cidade monitorada pela Defesa Civil (nível dos rios na tela inicial); null = desligado. */
    val river: RiverCity? = null,
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
    val power: CameraPower = CameraPower.ALWAYS,
    /** Tempo que o vídeo fica aberto quando ligado com um toque (câmeras a bateria). */
    val autoStopSeconds: Int = 60,
)

/** Quando o app mantém o vídeo ao vivo. Câmeras solares/bateria gastam energia enquanto transmitem. */
@Serializable
enum class CameraPower(val label: String, val description: String) {
    ALWAYS("Sempre ao vivo", "Para câmeras ligadas na tomada."),
    DAYTIME("Ao vivo só de dia", "Do nascer ao pôr do sol. À noite, só quando você tocar."),
    ON_DEMAND("Só quando tocar", "Economiza ao máximo a bateria de câmeras solares."),
}

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

@Serializable
data class RiverCity(
    val id: Int,
    val name: String,
    val state: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class WeatherPlace(
    val name: String,
    val region: String? = null,
    val latitude: Double,
    val longitude: Double,
)
