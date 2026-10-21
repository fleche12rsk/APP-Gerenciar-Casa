package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.micio.casa.integrations.tuya.AcState
import dev.micio.casa.integrations.tuya.IrKey
import dev.micio.casa.integrations.tuya.IrRemote
import dev.micio.casa.integrations.tuya.TuyaProvider
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.GlassCard
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Controles virtuais do hub infravermelho Intelbras (Tuya): ar-condicionado, TV box, som... */
@Composable
fun RemotesScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val providers by vm.home.providers.collectAsState()
    val devices by vm.home.allDevices.collectAsState()
    val tuya = providers[Source.TUYA] as? TuyaProvider

    if (tuya == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.SettingsRemote,
                title = "Controle infravermelho",
                message = "O controle IR da Intelbras funciona pela nuvem Smart Life/Tuya. Conecte a conta em Ajustes para usar os controles aqui.",
            ) {
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
                    Text("Conectar Smart Life")
                }
            }
        }
        return
    }

    var remotes by remember(tuya) { mutableStateOf<List<IrRemote>?>(null) }
    var selected by remember(tuya) { mutableStateOf<IrRemote?>(null) }
    var failure by remember(tuya) { mutableStateOf<String?>(null) }
    // A lista de hubs IR só existe depois da primeira leitura de aparelhos.
    val loaded = devices.any { it.source == Source.TUYA }
    LaunchedEffect(tuya, loaded) {
        if (!loaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { tuya.infraredHubs().flatMap { (hubId, _) -> tuya.remotes(hubId) } }
                .onSuccess { list -> remotes = list; selected = selected ?: list.firstOrNull() }
                .onFailure { failure = it.message; remotes = emptyList() }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("Controles", style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory)
        when (val list = remotes) {
            null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Ember)
            }
            else -> if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.SettingsRemote,
                    title = "Nenhum controle encontrado",
                    message = failure ?: "Cadastre os controles (ar-condicionado, TV, receptor) no app Smart Life usando o hub IR e eles aparecem aqui.",
                )
            } else {
                RemotePicker(list, selected) { selected = it }
                selected?.let { remote ->
                    AnimatedContent(remote, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "remote") { current ->
                        if (current.isAirConditioner) AirConditionerPanel(vm, tuya, current)
                        else KeyPanel(vm, tuya, current)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemotePicker(remotes: List<IrRemote>, selected: IrRemote?, onSelect: (IrRemote) -> Unit) {
    SectionTitle("Aparelhos")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        remotes.forEach { remote ->
            FilterChip(
                selected = remote == selected,
                onClick = { onSelect(remote) },
                label = { Text(remote.name) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyPanel(vm: MainViewModel, tuya: TuyaProvider, remote: IrRemote) {
    var keys by remember(remote) { mutableStateOf<List<IrKey>?>(null) }
    LaunchedEffect(remote) {
        keys = withContext(Dispatchers.IO) { runCatching { tuya.keys(remote) }.getOrDefault(emptyList()) }
    }
    SectionTitle("Botões")
    val list = keys
    when {
        list == null -> CircularProgressIndicator(color = Palette.Ember)
        list.isEmpty() -> Text("Este controle não tem botões padrão disponíveis pela nuvem.", color = Palette.Mist)
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { key ->
                val isPower = key.key.contains("power", ignoreCase = true)
                FilledTonalButton(
                    onClick = { vm.home.launchAction("O controle não respondeu.") { tuya.pressKey(remote, key) } },
                    modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isPower) Palette.Coral.copy(alpha = 0.2f) else Palette.GlassFill,
                        contentColor = Palette.Ivory,
                    ),
                ) {
                    if (isPower) Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(key.name, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirConditionerPanel(vm: MainViewModel, tuya: TuyaProvider, remote: IrRemote) {
    var state by remember(remote) { mutableStateOf(AcState(on = false)) }
    val send: (AcState) -> Unit = { next ->
        state = next
        vm.home.launchAction("O ar-condicionado não respondeu.") { tuya.sendAirConditioner(remote, next) }
    }
    GlassCard(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(remote.name, style = MaterialTheme.typography.titleLarge, color = Palette.Ivory)
                Text(if (state.on) "Ligado" else "Desligado", color = Palette.Mist)
            }
            FilledTonalIconButton(onClick = { send(state.copy(on = !state.on)) }) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (state.on) "Desligar" else "Ligar", tint = if (state.on) Palette.Coral else Palette.Sage)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { send(state.copy(temperature = (state.temperature - 1).coerceAtLeast(16), on = true)) }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Diminuir temperatura")
            }
            AnimatedContent(state.temperature, label = "temp") { temp ->
                Text("$temp°", style = MaterialTheme.typography.displayLarge, color = Palette.Ivory, modifier = Modifier.padding(horizontal = 32.dp))
            }
            FilledTonalIconButton(onClick = { send(state.copy(temperature = (state.temperature + 1).coerceAtMost(30), on = true)) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Aumentar temperatura")
            }
        }
        OptionRow("Modo", listOf("Frio", "Quente", "Auto", "Ventilar", "Secar"), state.mode) { send(state.copy(mode = it, on = true)) }
        OptionRow("Ventilação", listOf("Auto", "Baixa", "Média", "Alta"), state.fan) { send(state.copy(fan = it, on = true)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = Palette.Mist, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.Sky,
                    selectedLabelColor = Palette.Ink,
                    labelColor = Palette.Ivory,
                ),
            )
        }
    }
}
