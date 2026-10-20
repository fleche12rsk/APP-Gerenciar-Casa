package dev.micio.casa.integrations

import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source

data class ProviderSnapshot(val devices: List<Device>, val scenes: List<Scene> = emptyList())

/** Uma marca/plataforma ligada ao app. Cada uma traduz o seu protocolo para [Device]. */
interface DeviceProvider {
    val source: Source
    /** Intervalo de atualização em segundo plano enquanto o app está aberto. */
    val pollSeconds: Int
    suspend fun fetch(): ProviderSnapshot
    suspend fun execute(device: Device, action: DeviceAction)
    suspend fun runScene(scene: Scene) = Unit
}

class IntegrationException(message: String) : Exception(message)
