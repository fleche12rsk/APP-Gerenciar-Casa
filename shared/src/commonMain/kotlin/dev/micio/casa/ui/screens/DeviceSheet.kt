package dev.micio.casa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.VacuumCommand
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.components.accent
import dev.micio.casa.ui.components.icon
import dev.micio.casa.ui.components.statusLine
import dev.micio.casa.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceSheet(vm: MainViewModel, device: Device, onDismiss: () -> Unit) {
    val config by vm.config.config.collectAsState()
    val override = config.overrides[device.id]

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.NightHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(device.kind.accent().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(device.kind.icon(), contentDescription = null, tint = device.kind.accent())
                }
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleLarge, color = Palette.Ivory)
                    Text(
                        listOfNotNull(device.room, device.source.label, device.statusLine()).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Mist,
                    )
                }
                if (device.isOn != null && device.kind != DeviceKind.VACUUM) {
                    Switch(
                        checked = device.isOn,
                        onCheckedChange = { vm.home.execute(device, DeviceAction.Power(it)) },
                        enabled = device.online,
                        colors = SwitchDefaults.colors(checkedTrackColor = device.kind.accent(), checkedThumbColor = Palette.Ivory),
                    )
                }
            }

            if (device.kind == DeviceKind.LIGHT && device.brightness != null) {
                SectionTitle("Brilho")
                var level by remember { mutableFloatStateOf(device.brightness) }
                LaunchedEffect(device.brightness) { level = device.brightness }
                Slider(
                    value = level,
                    onValueChange = { level = it },
                    onValueChangeFinished = { vm.home.execute(device, DeviceAction.Brightness(level)) },
                    valueRange = 0.01f..1f,
                    colors = SliderDefaults.colors(thumbColor = Palette.Ember, activeTrackColor = Palette.Ember),
                )
            }

            if (device.kind == DeviceKind.VACUUM) {
                SectionTitle("Aspirador")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VacuumButton("Limpar", Icons.Rounded.PlayArrow) { vm.home.execute(device, DeviceAction.Vacuum(VacuumCommand.START)) }
                    VacuumButton("Pausar", Icons.Rounded.Pause) { vm.home.execute(device, DeviceAction.Vacuum(VacuumCommand.PAUSE)) }
                    VacuumButton("Voltar à base", Icons.Rounded.Home) { vm.home.execute(device, DeviceAction.Vacuum(VacuumCommand.DOCK)) }
                    VacuumButton("Localizar", Icons.AutoMirrored.Rounded.VolumeUp) { vm.home.execute(device, DeviceAction.Vacuum(VacuumCommand.LOCATE)) }
                }
            }

            SectionTitle("Personalizar")
            var name by remember(device.id) { mutableStateOf(override?.name.orEmpty()) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome no app") },
                placeholder = { Text(device.name) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LaunchedEffect(name) {
                kotlinx.coroutines.delay(500)
                vm.updateOverride(device.id) { it.copy(name = name.trim().ifEmpty { null }) }
            }

            Text("Cômodo", style = MaterialTheme.typography.labelLarge, color = Palette.Mist, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + config.rooms).forEach { room ->
                    val selected = device.room == room
                    FilterChip(
                        selected = selected,
                        onClick = { vm.updateOverride(device.id) { it.copy(room = room) } },
                        label = { Text(room ?: "Nenhum") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Palette.Ivory,
                            selectedLabelColor = Palette.Ink,
                        ),
                    )
                }
            }

            ToggleRow("Favorito na tela inicial", device.favorite) { fav -> vm.updateOverride(device.id) { it.copy(favorite = fav) } }
            ToggleRow("Ocultar do app", device.hidden) { hidden -> vm.updateOverride(device.id) { it.copy(hidden = hidden) } }
        }
    }
}

@Composable
private fun VacuumButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.Ivory)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Mist) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Ember, checkedThumbColor = Palette.Ivory),
        )
    }
}
