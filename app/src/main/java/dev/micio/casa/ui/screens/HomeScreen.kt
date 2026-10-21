package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Roofing
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.DeviceTile
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.SceneCard
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.components.statusLine
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FILTER_ALL = "__all"
private const val FILTER_FAVORITES = "__fav"

@Composable
fun HomeScreen(
    vm: MainViewModel,
    wide: Boolean,
    onOpenDevice: (Device) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeather: () -> Unit,
) {
    val weather by vm.weather.state.collectAsStateWithLifecycle()
    val devices by vm.home.devices.collectAsStateWithLifecycle()
    val scenes by vm.home.scenes.collectAsStateWithLifecycle()
    val errors by vm.home.errors.collectAsStateWithLifecycle()
    val providers by vm.home.providers.collectAsStateWithLifecycle()
    val loading by vm.home.loading.collectAsStateWithLifecycle()
    val config by vm.config.config.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val controllable = devices.filter { it.kind != DeviceKind.CAMERA }
    val visible = when (filter) {
        FILTER_ALL -> controllable
        FILTER_FAVORITES -> controllable.filter { it.favorite }
        else -> controllable.filter { it.room == filter }
    }
    val roomsInUse = config.rooms.filter { room -> controllable.any { it.room == room } }
    val hPad = if (wide) 32.dp else 16.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (wide) 172.dp else 150.dp),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 24.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("header") {
            Header(devices, wide, loading, onRefresh = vm.home::refreshAll) {
                WeatherBadge(weather, onClick = onOpenWeather)
            }
        }

        if (errors.isNotEmpty()) {
            fullWidth("errors") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    errors.forEach { (source, message) -> ErrorBanner("${source.label}: $message") }
                }
            }
        }

        if (providers.isEmpty()) {
            fullWidth("welcome") {
                EmptyState(
                    icon = Icons.Rounded.Roofing,
                    title = "Vamos montar a sua casa",
                    message = "Conecte o Smart Life (interruptores NovaDigital, Intelbras e WAG), a TV LG, " +
                        "as câmeras e o Home Assistant (aspirador Eufy e Alexa) em Ajustes.",
                ) {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink),
                    ) { Text("Abrir Ajustes") }
                }
            }
            return@LazyVerticalGrid
        }

        if (scenes.isNotEmpty()) {
            fullWidth("scenesTitle") { SectionTitle("Cenas") }
            fullWidth("scenes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(scenes, key = { it.id }) { scene -> SceneCard(scene, onRun = { vm.home.runScene(scene) }) }
                }
            }
        }

        fullWidth("filters") {
            RoomFilters(
                rooms = roomsInUse,
                selected = filter,
                hasFavorites = controllable.any { it.favorite },
                onSelect = { filter = it },
            )
        }

        val groups = if (filter == FILTER_ALL) {
            visible.groupBy { it.room }.toList().sortedBy { (room, _) -> room?.let { config.rooms.indexOf(it) }?.takeIf { it >= 0 } ?: Int.MAX_VALUE }
        } else {
            listOf(null to visible)
        }

        if (visible.isEmpty()) {
            fullWidth("emptyFilter") {
                Text(
                    if (filter == FILTER_FAVORITES) "Toque e segure um aparelho para marcá-lo como favorito."
                    else "Nenhum aparelho por aqui ainda.",
                    color = Palette.Mist,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        groups.forEach { (room, group) ->
            if (filter == FILTER_ALL) {
                fullWidth("room-$room") { SectionTitle(room ?: "Outros aparelhos") }
            }
            items(group, key = { it.id }) { device ->
                DeviceTile(
                    device = device,
                    onToggle = { vm.toggle(device) },
                    onOpen = { onOpenDevice(device) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Header(devices: List<Device>, wide: Boolean, loading: Boolean, onRefresh: () -> Unit, weather: @Composable () -> Unit) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L * (60 - value.second).coerceAtLeast(1))
        }
    }
    val locale = Locale.forLanguageTag("pt-BR")
    val greeting = when (now.hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Mist,
                )
                Text(
                    greeting,
                    style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = Palette.Ivory,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (wide) {
                weather()
                Spacer(Modifier.width(28.dp))
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayLarge, color = Palette.Ivory)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar", tint = Palette.Mist)
            }
        }
        AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp),
                color = Palette.Ember,
                trackColor = Color.Transparent,
            )
        }
        if (!wide) {
            Spacer(Modifier.height(16.dp))
            weather()
        }
        Spacer(Modifier.height(16.dp))
        SummaryRow(devices)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryRow(devices: List<Device>) {
    val lightsOn = devices.count { it.kind == DeviceKind.LIGHT && it.isOn == true }
    val powered = devices.count { (it.kind == DeviceKind.SWITCH || it.kind == DeviceKind.OUTLET || it.kind == DeviceKind.FAN) && it.isOn == true }
    val tv = devices.firstOrNull { it.kind == DeviceKind.TV }
    val vacuum = devices.firstOrNull { it.kind == DeviceKind.VACUUM }
    val climate = devices.firstOrNull { it.kind == DeviceKind.SENSOR && it.status?.contains("°") == true }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryChip(Icons.Rounded.Lightbulb, if (lightsOn == 0) "Luzes apagadas" else "$lightsOn ${if (lightsOn == 1) "luz acesa" else "luzes acesas"}", Palette.Ember, lightsOn > 0)
        if (powered > 0) SummaryChip(Icons.Rounded.Power, "$powered ${if (powered == 1) "aparelho ligado" else "aparelhos ligados"}", Palette.Sage, true)
        tv?.let { SummaryChip(Icons.Rounded.Tv, "TV ${if (it.isOn == true) "ligada" else "desligada"}", Palette.Sky, it.isOn == true) }
        vacuum?.let { SummaryChip(Icons.Rounded.CleaningServices, "Aspirador: ${it.statusLine().lowercase()}", Palette.Lagoon, it.isOn == true) }
        climate?.let { SummaryChip(Icons.Rounded.Thermostat, it.status.orEmpty(), Palette.Sky, false) }
    }
}

@Composable
private fun SummaryChip(icon: ImageVector, text: String, accent: Color, active: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) accent.copy(alpha = 0.16f) else Palette.GlassFill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) accent else Palette.Mist, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Ivory)
    }
}

@Composable
private fun RoomFilters(rooms: List<String>, selected: String, hasFavorites: Boolean, onSelect: (String) -> Unit) {
    val options = buildList {
        add(FILTER_ALL to "Todos")
        if (hasFavorites) add(FILTER_FAVORITES to "Favoritos")
        rooms.forEach { add(it to it) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(options, key = { it.first }) { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
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

@Composable
fun ErrorBanner(text: String) {
    var dismissed by rememberSaveable(text) { mutableStateOf(false) }
    AnimatedVisibility(!dismissed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Coral.copy(alpha = 0.14f))
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(20.dp))
            Text(text, color = Palette.Ivory, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { dismissed = true }) { Text("Ok", color = Palette.Ivory) }
        }
    }
}

