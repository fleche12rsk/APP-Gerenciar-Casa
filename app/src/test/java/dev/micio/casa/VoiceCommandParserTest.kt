package dev.micio.casa

import dev.micio.casa.home.VoiceCommandParser
import dev.micio.casa.home.VoiceCommandParser.Command
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import dev.micio.casa.model.VacuumCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val rooms = listOf("Sala", "Cozinha", "Quarto")
    private val devices = listOf(
        Device("1", "Luz da Sala", DeviceKind.LIGHT, Source.TUYA, room = "Sala", isOn = false),
        Device("2", "Spot Sala", DeviceKind.LIGHT, Source.TUYA, room = "Sala", isOn = false),
        Device("3", "Luz da Cozinha", DeviceKind.LIGHT, Source.TUYA, room = "Cozinha", isOn = true),
        Device("4", "Ventilador", DeviceKind.FAN, Source.TUYA, room = "Quarto", isOn = false),
        Device("5", "Robô Eufy", DeviceKind.VACUUM, Source.HOME_ASSISTANT, isOn = false),
        Device("6", "Tomada Cafeteira", DeviceKind.OUTLET, Source.TUYA, room = "Cozinha", isOn = false),
    )
    private val scenes = listOf(Scene("s1", "Cinema", Source.TUYA), Scene("s2", "Boa noite", Source.HOME_ASSISTANT))

    private fun parse(text: String) = VoiceCommandParser.parse(text, devices, scenes, rooms)

    @Test fun `nome completo do aparelho`() {
        val cmd = parse("Acender a luz da sala") as Command.Power
        assertTrue(cmd.on)
        assertEquals(listOf("1"), cmd.devices.map { it.id })
    }

    @Test fun `desligar tem prioridade sobre ligar`() {
        val cmd = parse("desligar o ventilador") as Command.Power
        assertEquals(false, cmd.on)
        assertEquals(listOf("4"), cmd.devices.map { it.id })
    }

    @Test fun `luzes de um comodo`() {
        val cmd = parse("apaga as luzes da sala") as Command.Power
        assertEquals(setOf("1", "2"), cmd.devices.map { it.id }.toSet())
    }

    @Test fun `todas as luzes`() {
        val cmd = parse("apagar todas as luzes") as Command.Power
        assertEquals(setOf("1", "2", "3"), cmd.devices.map { it.id }.toSet())
    }

    @Test fun `palavra parcial do nome`() {
        val cmd = parse("liga a cafeteira") as Command.Power
        assertEquals(listOf("6"), cmd.devices.map { it.id })
    }

    @Test fun `aspirador`() {
        assertEquals(VacuumCommand.START, (parse("aspirador começar a limpar") as Command.Vacuum).command)
        assertEquals(VacuumCommand.DOCK, (parse("aspirador volta pra base") as Command.Vacuum).command)
    }

    @Test fun `cena`() {
        assertEquals("s1", (parse("ativar modo cinema") as Command.RunScene).scene.id)
        assertEquals("s2", (parse("boa noite") as Command.RunScene).scene.id)
    }

    @Test fun `pergunta sobre o tempo`() {
        assertEquals(Command.Weather, parse("qual a previsão do tempo amanhã"))
        assertEquals(Command.Weather, parse("vai chover hoje?"))
    }

    @Test fun `desconhecido segue para a alexa`() {
        assertEquals(Command.Unknown, parse("que horas são"))
        assertEquals(Command.Unknown, parse("tocar música relaxante"))
    }
}
