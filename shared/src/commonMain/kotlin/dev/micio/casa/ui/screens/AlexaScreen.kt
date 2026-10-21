package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.micio.casa.integrations.ha.HomeAssistantProvider
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.GlassCard
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.rememberVoiceInput
import dev.micio.casa.ui.theme.Palette
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

private val suggestions = listOf(
    "Que horas são?",
    "Como vai estar o tempo hoje?",
    "Tocar música relaxante",
    "Qual é a minha agenda de hoje?",
    "Colocar um timer de 10 minutos",
    "Parar",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlexaScreen(vm: MainViewModel, wide: Boolean, onOpenSettings: () -> Unit) {
    val providers by vm.home.providers.collectAsState()
    val config by vm.config.config.collectAsState()
    // Recompõe quando os estados do Home Assistant mudam.
    vm.home.allDevices.collectAsState().value
    val ha = providers[Source.HOME_ASSISTANT] as? HomeAssistantProvider
    val echos = ha?.alexaDevices().orEmpty()

    if (ha == null || echos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "Alexa no seu tablet",
                message = if (ha == null) {
                    "A Amazon não libera o Echo para outros apps. A ponte é o Home Assistant com a integração " +
                        "Alexa Media Player: com ela você manda comandos, anúncios e controla o volume do Echo Spot daqui."
                } else {
                    "O Home Assistant está conectado, mas nenhum Echo foi encontrado. Instale a integração Alexa Media Player (HACS)."
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ha == null) {
                        Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
                            Text("Configurar Home Assistant")
                        }
                    }
                    OutlinedButton(onClick = { vm.platform.openAlexaApp() }) { Text("Abrir app Alexa", color = Palette.Ivory) }
                }
            }
        }
        return
    }

    val selectedId = config.homeAssistant?.alexaEntity?.takeIf { id -> echos.any { it.entity_id == id } } ?: echos.first().entity_id
    val echo = echos.first { it.entity_id == selectedId }
    var text by rememberSaveable { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }

    val sendCommand: (String) -> Unit = { phrase ->
        val clean = phrase.trim()
        if (clean.isNotEmpty()) {
            vm.home.launchAction("A Alexa não recebeu o comando.") { ha.alexaCommand(selectedId, clean) }
            history.add(0, clean)
            if (history.size > 8) history.removeAt(history.lastIndex)
            text = ""
        }
    }
    val listen = rememberVoiceInput(onResult = sendCommand, onError = vm.home::notify)

    val hPad = if (wide) 32.dp else 16.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alexa", style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory)
                Text("Fale ou digite: o ${echo.friendlyName} executa como se tivesse ouvido você.", color = Palette.Mist)
            }
            OutlinedButton(onClick = { vm.platform.openAlexaApp() }) { Text("App Alexa", color = Palette.Ivory) }
        }

        if (echos.size > 1) {
            SectionTitle("Dispositivo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                echos.forEach { state ->
                    FilterChip(
                        selected = state.entity_id == selectedId,
                        onClick = { vm.updateConfig { c -> c.copy(homeAssistant = c.homeAssistant?.copy(alexaEntity = state.entity_id)) } },
                        label = { Text(state.friendlyName) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Palette.GlassFill,
                            labelColor = Palette.Ivory,
                            selectedContainerColor = Palette.Ivory,
                            selectedLabelColor = Palette.Ink,
                        ),
                        border = null,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = listen,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Sky, contentColor = Palette.Ink),
                ) { Icon(Icons.Rounded.Mic, contentDescription = "Falar com a Alexa") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Alexa, …") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCommand(text) }),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = { sendCommand(text) }, enabled = text.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Enviar")
                        }
                    },
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val message = text.trim()
                        vm.home.launchAction("Não foi possível anunciar.") { ha.alexaAnnounce(selectedId, message, announce = true) }
                        text = ""
                    },
                ) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Anunciar", modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val message = text.trim()
                        vm.home.launchAction("Não foi possível falar.") { ha.alexaAnnounce(selectedId, message, announce = false) }
                        text = ""
                    },
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Falar no Echo", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        SectionTitle("Sugestões")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { phrase ->
                AssistChip(
                    onClick = { sendCommand(phrase) },
                    label = { Text(phrase) },
                    shape = RoundedCornerShape(50),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Palette.GlassFill, labelColor = Palette.Ivory),
                    border = null,
                )
            }
        }

        SectionTitle("Tocando agora")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(echo.attr("media_title") ?: "Nada tocando", style = MaterialTheme.typography.titleMedium, color = Palette.Ivory)
            echo.attr("media_artist")?.let { Text(it, color = Palette.Mist) }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { vm.home.launchAction("Falha no Echo.") { ha.mediaCommand(selectedId, "media_previous_track") } }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Palette.Ivory)
                }
                IconButton(onClick = { vm.home.launchAction("Falha no Echo.") { ha.mediaCommand(selectedId, "media_play_pause") } }) {
                    Icon(if (echo.state == "playing") Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Tocar/pausar", tint = Palette.Ivory)
                }
                IconButton(onClick = { vm.home.launchAction("Falha no Echo.") { ha.mediaCommand(selectedId, "media_next_track") } }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima", tint = Palette.Ivory)
                }
            }
            val current = echo.attributes["volume_level"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.4f
            var volume by remember(selectedId, current) { mutableFloatStateOf(current) }
            Text("Volume ${(volume * 100).toInt()}%", color = Palette.Mist, style = MaterialTheme.typography.labelLarge)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { vm.home.launchAction("Falha no Echo.") { ha.setVolume(selectedId, volume) } },
                colors = SliderDefaults.colors(thumbColor = Palette.Sky, activeTrackColor = Palette.Sky),
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        AnimatedVisibility(history.isNotEmpty(), enter = fadeIn() + expandVertically()) {
            Column {
                SectionTitle("Enviados agora há pouco")
                history.forEach { item ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Sky))
                        Text(item, color = Palette.Ivory)
                    }
                }
            }
        }
    }
}
