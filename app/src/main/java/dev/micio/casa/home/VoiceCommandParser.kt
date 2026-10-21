package dev.micio.casa.home

import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.VacuumCommand
import java.text.Normalizer

/**
 * Entende frases simples em português ("apagar as luzes da sala", "ligar o
 * ventilador", "ativar cena cinema"). O que não reconhecer pode seguir para a Alexa.
 */
object VoiceCommandParser {

    sealed interface Command {
        data class Power(val devices: List<Device>, val on: Boolean) : Command
        data class Vacuum(val device: Device, val command: VacuumCommand) : Command
        data class RunScene(val scene: Scene) : Command
        data object Weather : Command
        data object Unknown : Command
    }

    private val offVerbs = listOf("deslig", "apag", "fech", "desativ", "deslique")
    private val onVerbs = listOf("lig", "acend", "acenda", "abr", "ativ")
    private val stopWords = setOf("a", "o", "as", "os", "da", "do", "das", "dos", "de", "na", "no", "em", "e", "por", "favor", "luz", "luzes")

    private val weatherWords = setOf("tempo", "clima", "previsao", "chuva", "guarda", "temperatura", "frio", "calor")

    private val kindWords = mapOf(
        DeviceKind.LIGHT to listOf("luz", "luzes", "lampada", "lampadas", "abajur", "lustre", "led"),
        DeviceKind.OUTLET to listOf("tomada", "tomadas"),
        DeviceKind.FAN to listOf("ventilador", "ventiladores"),
        DeviceKind.TV to listOf("tv", "televisao", "televisor"),
        DeviceKind.COVER to listOf("cortina", "cortinas", "persiana", "persianas"),
        DeviceKind.SWITCH to listOf("interruptor", "interruptores"),
    )

    fun parse(text: String, devices: List<Device>, scenes: List<Scene>, rooms: List<String>): Command {
        val phrase = normalize(text)
        if (phrase.isBlank()) return Command.Unknown
        val words = phrase.split(' ')

        parseVacuum(phrase, words, devices)?.let { return it }

        val isOff = words.any { w -> offVerbs.any { w.startsWith(it) } }
        val isOn = !isOff && words.any { w -> onVerbs.any { w.startsWith(it) } }
        val wantsScene = words.any { it == "cena" || it == "modo" }

        if (!wantsScene && (isOn || isOff)) {
            val targets = matchDevices(phrase, words, devices.filter { it.isOn != null && it.online }, rooms)
            if (targets.isNotEmpty()) return Command.Power(targets, on = isOn)
        }
        if (words.any { it in weatherWords || it.startsWith("chov") }) return Command.Weather
        matchScene(phrase, scenes)?.let { return Command.RunScene(it) }
        return Command.Unknown
    }

    private fun parseVacuum(phrase: String, words: List<String>, devices: List<Device>): Command? {
        val mentionsVacuum = words.any { it.startsWith("aspira") || it.startsWith("robo") } ||
            words.any { it.startsWith("limp") && words.size <= 4 }
        if (!mentionsVacuum) return null
        val vacuum = devices.firstOrNull { it.kind == DeviceKind.VACUUM } ?: return null
        val command = when {
            listOf("volt", "base", "carreg", "deslig").any { k -> words.any { it.startsWith(k) } } -> VacuumCommand.DOCK
            listOf("pausa", "pause", "parar", "para").any { k -> words.any { it == k || it.startsWith("paus") } } -> VacuumCommand.PAUSE
            listOf("onde", "localiz", "encontr", "achar").any { k -> words.any { it.startsWith(k) } } -> VacuumCommand.LOCATE
            else -> VacuumCommand.START
        }
        return Command.Vacuum(vacuum, command)
    }

    private fun matchDevices(phrase: String, words: List<String>, devices: List<Device>, rooms: List<String>): List<Device> {
        val room = rooms.map { it to normalize(it) }.filter { (_, n) -> n.isNotEmpty() && contains(phrase, n) }
            .maxByOrNull { it.second.length }?.first
        val inRoom = if (room == null) devices else devices.filter { it.room == room }

        // 1) Nome completo do aparelho dito na frase (o mais longo vence).
        val byName = devices.map { it to normalize(it.name) }.filter { (_, n) -> n.length >= 3 && contains(phrase, n) }
        if (byName.isNotEmpty()) {
            val longest = byName.maxOf { it.second.length }
            return byName.filter { it.second.length == longest }.map { it.first }
        }

        // 2) Tipo de aparelho ("luzes", "tomada"), opcionalmente filtrado pelo cômodo.
        val kind = kindWords.entries.firstOrNull { (_, keys) -> words.any { it in keys } }?.key
        val everything = words.any { it in setOf("tudo", "todas", "todos") }
        if (kind != null) {
            val ofKind = inRoom.filter { it.kind == kind }
            if (ofKind.size == 1 || everything || room != null || words.any { it.endsWith("s") && it in kindWords[kind].orEmpty() }) return ofKind
        }
        if (everything) {
            return inRoom.filter { it.kind in setOf(DeviceKind.LIGHT, DeviceKind.SWITCH, DeviceKind.OUTLET, DeviceKind.FAN) }
        }

        // 3) Palavras significativas do nome presentes na frase ("ligar ventilador quarto").
        val scored = devices.map { device ->
            val tokens = normalize(device.name).split(' ').filter { it.length >= 3 && it !in stopWords }
            device to tokens.count { it in words }.toFloat() / tokens.size.coerceAtLeast(1)
        }.filter { it.second >= 0.5f }
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second == best }.map { it.first }
    }

    private fun matchScene(phrase: String, scenes: List<Scene>): Scene? =
        scenes.map { it to normalize(it.name) }
            .filter { (_, n) -> n.length >= 3 && contains(phrase, n) }
            .maxByOrNull { it.second.length }?.first

    private fun contains(phrase: String, part: String) = " $phrase ".contains(" $part ")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
