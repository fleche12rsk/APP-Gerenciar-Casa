package dev.micio.casa.ui

import androidx.lifecycle.ViewModel
import dev.micio.casa.core.AppConfig
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.core.DeviceOverride
import dev.micio.casa.home.HomeRepository
import dev.micio.casa.home.VoiceCommandParser
import dev.micio.casa.home.VoiceCommandParser.Command
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.VacuumCommand

class MainViewModel(
    val config: ConfigRepository,
    val home: HomeRepository,
) : ViewModel() {

    fun toggle(device: Device) {
        val on = device.isOn ?: return
        home.execute(device, DeviceAction.Power(!on))
    }

    fun updateConfig(transform: (AppConfig) -> AppConfig) = config.update(transform)

    fun updateOverride(deviceId: String, transform: (DeviceOverride) -> DeviceOverride) = config.update { c ->
        val next = transform(c.overrides[deviceId] ?: DeviceOverride())
        c.copy(overrides = if (next == DeviceOverride()) c.overrides - deviceId else c.overrides + (deviceId to next))
    }

    /** Frase reconhecida pelo microfone: tenta resolver localmente, senão repassa à Alexa. */
    fun handleVoice(text: String) {
        val command = VoiceCommandParser.parse(text, home.devices.value, home.scenes.value, config.current.rooms)
        when (command) {
            is Command.Power -> {
                command.devices.forEach { home.execute(it, DeviceAction.Power(command.on)) }
                val names = command.devices.joinToString(", ") { it.name }
                home.notify("${if (command.on) "Ligando" else "Desligando"}: $names")
            }
            is Command.Vacuum -> {
                home.execute(command.device, DeviceAction.Vacuum(command.command))
                home.notify(when (command.command) {
                    VacuumCommand.START -> "Aspirador começando a limpeza"
                    VacuumCommand.PAUSE -> "Aspirador pausado"
                    VacuumCommand.DOCK -> "Aspirador voltando à base"
                    VacuumCommand.LOCATE -> "Aspirador vai emitir um som"
                })
            }
            is Command.RunScene -> home.runScene(command.scene)
            Command.Unknown -> sendToAlexa(text) ?: home.notify("Não entendi “$text”.")
        }
    }

    /** Devolve null quando não há Echo configurado. */
    fun sendToAlexa(text: String): Unit? {
        val ha = home.homeAssistant ?: return null
        val echo = config.current.homeAssistant?.alexaEntity ?: ha.alexaDevices().firstOrNull()?.entity_id ?: return null
        home.launchAction("A Alexa não recebeu o comando.") {
            ha.alexaCommand(echo, text)
            home.notify("Alexa: “$text”")
        }
        return Unit
    }
}
