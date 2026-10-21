package dev.micio.casa.integrations.camera

import dev.micio.casa.core.CameraBrand
import dev.micio.casa.core.CameraConfig
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import io.ktor.http.encodeURLParameter

/** Câmeras de rede assistidas direto por RTSP, sem passar pela nuvem do fabricante. */
class CameraProvider(private val cameras: List<CameraConfig>) : DeviceProvider {

    override val source = Source.CAMERA
    override val pollSeconds = 3600

    override suspend fun fetch() = ProviderSnapshot(cameras.map {
        Device("cam:${it.id}", it.name, DeviceKind.CAMERA, Source.CAMERA, room = it.room, status = it.brand.label)
    })

    override suspend fun execute(device: Device, action: DeviceAction) = Unit
}

/** Endereços RTSP padrão de cada marca, montados a partir do IP e da senha. */
object CameraTemplates {

    data class Urls(val main: String, val sub: String?)

    fun build(brand: CameraBrand, host: String, user: String, password: String, channel: Int = 1): Urls {
        val h = host.trim()
        return when (brand) {
            // EZVIZ: usuário "admin" e o código de verificação de 6 letras da etiqueta da câmera.
            CameraBrand.EZVIZ -> {
                val auth = "${user.ifBlank { "admin" }.encodeURLParameter()}:${password.encodeURLParameter()}@"
                Urls(
                    main = "rtsp://$auth$h:554/h264/ch$channel/main/av_stream",
                    sub = "rtsp://$auth$h:554/h264/ch$channel/sub/av_stream",
                )
            }
            // ICSee/XMEye (Xiongmai): credenciais vão no próprio caminho.
            CameraBrand.ICSEE -> {
                val creds = "user=${user.ifBlank { "admin" }.encodeURLParameter()}&password=${password.encodeURLParameter()}&channel=$channel"
                Urls(
                    main = "rtsp://$h:554/$creds&stream=0.sdp?real_stream",
                    sub = "rtsp://$h:554/$creds&stream=1.sdp?real_stream",
                )
            }
            CameraBrand.GENERIC -> {
                val auth = if (user.isBlank()) "" else "${user.encodeURLParameter()}:${password.encodeURLParameter()}@"
                Urls(main = "rtsp://$auth$h:554/", sub = null)
            }
        }
    }
}
