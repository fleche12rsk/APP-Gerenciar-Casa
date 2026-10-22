package dev.micio.casa.integrations.tv

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Manifesto de pareamento do webOS. É o mesmo usado pelos controles remotos
 * de código aberto (aiowebostv/Home Assistant, lgtv2), aceito pelas TVs LG.
 */
internal object WebOsManifest {

    private val permissions = listOf(
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
        "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_TV_SCREEN",
        "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE",
        "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE",
        "READ_COUNTRY_INFO", "READ_SETTINGS", "CONTROL_TV_STANBY", "CONTROL_INPUT_TEXT",
        "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_NOTIFICATIONS",
        "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT", "CHECK_BLUETOOTH_DEVICE",
        "CONTROL_BLUETOOTH", "CONTROL_USER_INFO", "READ_TV_CURRENT_TIME", "READ_LGE_TV_INPUT_EVENTS",
    )

    private val signedPermissions = listOf(
        "TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS",
        "READ_LGE_SDX", "READ_NOTIFICATIONS", "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT",
        "CONTROL_POWER", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_UPDATE_INFO",
        "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME",
    )

    private const val SIGNATURE =
        "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9." +
            "hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw=="

    fun registerPayload(clientKey: String?): JsonObject = buildJsonObject {
        put("forcePairing", false)
        put("pairingType", "PROMPT")
        clientKey?.let { put("client-key", it) }
        putJsonObject("manifest") {
            put("manifestVersion", 1)
            put("appVersion", "1.1")
            putJsonObject("signed") {
                put("created", "20140509")
                put("appId", "com.lge.test")
                put("vendorId", "com.lge")
                putJsonObject("localizedAppNames") {
                    put("", "LG Remote App")
                    put("ko-KR", "리모컨 앱")
                    put("zxx-XX", "ЛГ Rэмotэ AПП")
                }
                putJsonObject("localizedVendorNames") { put("", "LG Electronics") }
                putJsonArray("permissions") { signedPermissions.forEach { add(it) } }
                put("serial", "2f930e2d2cfe083771f68e4fe7bb07")
            }
            putJsonArray("permissions") { permissions.forEach { add(it) } }
            putJsonArray("signatures") {
                addJsonObject {
                    put("signatureVersion", 1)
                    put("signature", SIGNATURE)
                }
            }
        }
    }
}
