package dev.micio.casa.integrations.lg

import dev.micio.casa.core.LgTvConfig
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.IntegrationException
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import kotlinx.coroutines.delay

/** [settings] é lido a cada uso para refletir nome/MAC editados sem derrubar a conexão. */
class LgTvProvider(private val settings: () -> LgTvConfig, val tv: WebOsClient) : DeviceProvider {

    override val source = Source.LG
    override val pollSeconds = 15

    override suspend fun fetch(): ProviderSnapshot {
        // Só reconecta sozinho quando já existe pareamento; sem chave a TV mostraria o pedido toda hora.
        val config = settings()
        if (tv.clientKey != null && !tv.isConnected) runCatching { tv.connect(timeoutMs = 3_000) }
        return ProviderSnapshot(listOf(Device(
            id = DEVICE_ID,
            name = config.name,
            kind = DeviceKind.TV,
            source = Source.LG,
            isOn = tv.isConnected,
            status = if (tv.clientKey == null) "Não pareada" else null,
        )))
    }

    override suspend fun execute(device: Device, action: DeviceAction) {
        if (action !is DeviceAction.Power) return
        val config = settings()
        if (!action.on) {
            tv.powerOff()
            return
        }
        if (config.mac.isBlank()) throw IntegrationException("Informe o MAC da TV em Ajustes para ligá-la pela rede.")
        WakeOnLan.send(config.mac, config.host)
        // A TV leva alguns segundos para subir o serviço de rede depois de acordar.
        repeat(6) {
            delay(2_500)
            if (runCatching { tv.connect(timeoutMs = 3_000) }.isSuccess) return
        }
    }

    companion object {
        const val DEVICE_ID = "lg:tv"
    }
}
