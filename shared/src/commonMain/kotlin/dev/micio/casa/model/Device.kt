package dev.micio.casa.model

enum class DeviceKind { LIGHT, SWITCH, OUTLET, FAN, COVER, VACUUM, TV, SPEAKER, CAMERA, CLIMATE, SENSOR, OTHER }

enum class Source(val label: String) {
    TUYA("Smart Life / Tuya"),
    HOME_ASSISTANT("Home Assistant"),
    TV("TVs"),
    CAMERA("Câmeras"),
}

/**
 * Um aparelho como o app enxerga, independente da marca. O [id] é único entre
 * todas as integrações (ex.: "tuya:<id>:switch_2", "ha:light.sala", "lg:tv").
 */
data class Device(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val source: Source,
    val room: String? = null,
    val online: Boolean = true,
    /** null quando o aparelho não tem estado liga/desliga (câmera, sensor). */
    val isOn: Boolean? = null,
    /** Brilho de 0 a 1, quando a lâmpada suporta. */
    val brightness: Float? = null,
    /** Texto curto de estado vindo da integração ("Limpando", "23,5 °C"). */
    val status: String? = null,
    val battery: Int? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
)

data class Scene(val id: String, val name: String, val source: Source)

sealed interface DeviceAction {
    data class Power(val on: Boolean) : DeviceAction
    data class Brightness(val level: Float) : DeviceAction
    data class Vacuum(val command: VacuumCommand) : DeviceAction
}

enum class VacuumCommand { START, PAUSE, DOCK, LOCATE }
