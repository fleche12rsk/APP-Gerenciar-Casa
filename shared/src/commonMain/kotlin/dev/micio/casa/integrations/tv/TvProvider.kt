package dev.micio.casa.integrations.tv

import dev.micio.casa.core.TvBrand
import dev.micio.casa.core.TvConfig
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.IntegrationException
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import kotlinx.coroutines.delay

/**
 * Todas as TVs da casa controladas pela rede local. [settings] é lido a cada uso
 * para refletir nome e MAC editados sem derrubar as conexões.
 */
class TvProvider(
    private val settings: () -> List<TvConfig>,
    val controllers: Map<String, TvController>,
) : DeviceProvider {

    override val source = Source.TV
    override val pollSeconds = 15

    override suspend fun fetch(): ProviderSnapshot = ProviderSnapshot(settings().mapNotNull { tv ->
        val controller = controllers[tv.id] ?: return@mapNotNull null
        // Só reconecta sozinho quando já há autorização; sem ela a TV mostraria o pedido toda hora.
        if (controller.paired && !controller.isConnected) runCatching { controller.connect(timeoutMs = 3_000) }
        Device(
            id = deviceId(tv.id),
            name = tv.name,
            kind = DeviceKind.TV,
            source = Source.TV,
            isOn = controller.isConnected,
            status = if (!controller.paired) "Falta autorizar" else null,
        )
    })

    override suspend fun execute(device: Device, action: DeviceAction) {
        if (action !is DeviceAction.Power) return
        val id = device.id.removePrefix(PREFIX)
        val tv = settings().firstOrNull { it.id == id } ?: return
        val controller = controllers[id] ?: return
        if (!action.on) {
            controller.powerOff()
            return
        }
        if (!controller.powerOn()) {
            if (tv.mac.isBlank()) throw IntegrationException("Informe o MAC da TV em Ajustes para ligá-la pela rede.")
            WakeOnLan.send(tv.mac, tv.host)
        }
        // A TV leva alguns segundos para subir o serviço de rede depois de acordar.
        repeat(6) {
            delay(2_500)
            if (runCatching { controller.connect(timeoutMs = 3_000) }.isSuccess) return
        }
    }

    fun close() = controllers.values.forEach { it.close() }

    companion object {
        const val PREFIX = "tv:"
        fun deviceId(tvId: String) = PREFIX + tvId

        fun controllerFor(tv: TvConfig, onToken: (String) -> Unit): TvController = when (tv.brand) {
            TvBrand.LG -> WebOsClient(tv.host, tv.token, onToken)
            TvBrand.SAMSUNG -> SamsungTvClient(tv.host, tv.token, onToken)
            TvBrand.ROKU -> RokuClient(tv.host)
        }
    }
}
