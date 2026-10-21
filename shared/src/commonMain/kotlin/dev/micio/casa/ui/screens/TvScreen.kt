package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.micio.casa.integrations.lg.LgTvProvider
import dev.micio.casa.integrations.lg.TvApp
import dev.micio.casa.integrations.lg.TvInput
import dev.micio.casa.integrations.lg.WebOsClient
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.GlassCard
import dev.micio.casa.ui.components.RemoteImage
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.components.pressScale
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
fun TvScreen(vm: MainViewModel, wide: Boolean, onOpenSettings: () -> Unit) {
    val providers by vm.home.providers.collectAsState()
    val config by vm.config.config.collectAsState()
    val tv = (providers[Source.LG] as? LgTvProvider)?.tv

    if (tv == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Tv,
                title = "Conecte a sua TV LG",
                message = "Informe o IP da TV em Ajustes. O controle funciona pela rede local, sem depender da nuvem.",
            ) {
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
                    Text("Configurar TV")
                }
            }
        }
        return
    }

    val state by tv.state.collectAsState()
    val connected = state == WebOsClient.State.CONNECTED
    val name = config.tv?.name ?: "TV LG"
    val run: (suspend WebOsClient.() -> Unit) -> Unit = { block -> vm.home.launchAction("A TV não respondeu.") { tv.block() } }

    val power: () -> Unit = {
        vm.home.allDevices.value.firstOrNull { it.id == LgTvProvider.DEVICE_ID }?.let {
            vm.home.execute(it, DeviceAction.Power(!connected))
        }
    }

    val hPad = if (wide) 32.dp else 16.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory)
                AnimatedContent(state, label = "tvState") { s ->
                    Text(
                        when (s) {
                            WebOsClient.State.CONNECTED -> "Conectada"
                            WebOsClient.State.CONNECTING -> "Conectando…"
                            WebOsClient.State.WAITING_APPROVAL -> "Aceite o pedido que apareceu na TV"
                            WebOsClient.State.DISCONNECTED -> if (tv.clientKey == null) "Ainda não pareada" else "Desligada ou fora da rede"
                        },
                        color = if (s == WebOsClient.State.WAITING_APPROVAL) Palette.Ember else Palette.Mist,
                    )
                }
            }
            if (!connected && state != WebOsClient.State.CONNECTING && state != WebOsClient.State.WAITING_APPROVAL) {
                Button(
                    onClick = { run { connect() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.GlassFill, contentColor = Palette.Ivory),
                    modifier = Modifier.padding(end = 12.dp),
                ) { Text(if (tv.clientKey == null) "Parear" else "Conectar") }
            }
            RoundKey(Icons.Rounded.PowerSettingsNew, if (connected) "Desligar TV" else "Ligar TV", size = 56.dp, tint = if (connected) Palette.Coral else Palette.Sage, onClick = power)
        }

        Spacer(Modifier.height(24.dp))

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RemotePad(tv, connected, run, Modifier.widthIn(max = 380.dp))
                Column(Modifier.weight(1f)) { TvContent(tv, connected, run) }
            }
        } else {
            RemotePad(tv, connected, run, Modifier.fillMaxWidth())
            TvContent(tv, connected, run)
        }
    }
}

@Composable
private fun RemotePad(tv: WebOsClient, connected: Boolean, run: (suspend WebOsClient.() -> Unit) -> Unit, modifier: Modifier) {
    val volume by tv.volume.collectAsState()
    LaunchedEffect(connected) { if (connected) withContext(Dispatchers.IO) { runCatching { tv.refreshVolume() } } }

    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DPad(enabled = connected) { button -> run { button(button) } }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", enabled = connected) { run { button("BACK") } }
                RoundKey(Icons.Rounded.Home, "Início", enabled = connected) { run { button("HOME") } }
                RoundKey(Icons.Rounded.Menu, "Menu", enabled = connected) { run { button("MENU") } }
                RoundKey(Icons.Rounded.Settings, "Configurações rápidas", enabled = connected) { run { button("QMENU") } }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                RockerKey("VOL", connected, onUp = { run { volumeUp() } }, onDown = { run { volumeDown() } })
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundKey(Icons.AutoMirrored.Rounded.VolumeOff, "Silenciar", enabled = connected) { run { setMute(true) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundKey(Icons.Rounded.PlayArrow, "Reproduzir", enabled = connected) { run { play() } }
                        RoundKey(Icons.Rounded.Pause, "Pausar", enabled = connected) { run { pause() } }
                    }
                }
                RockerKey("CH", connected, onUp = { run { channelUp() } }, onDown = { run { channelDown() } })
            }
            volume?.let { current ->
                var slider by remember(current) { mutableFloatStateOf(current.toFloat()) }
                Spacer(Modifier.height(16.dp))
                Text("Volume ${slider.toInt()}", color = Palette.Mist, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { run { setVolume(slider.toInt()) } },
                    valueRange = 0f..100f,
                    enabled = connected,
                    colors = SliderDefaults.colors(thumbColor = Palette.Sky, activeTrackColor = Palette.Sky),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvContent(tv: WebOsClient, connected: Boolean, run: (suspend WebOsClient.() -> Unit) -> Unit) {
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<TvInput>>(emptyList()) }
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { tv.apps() }.onSuccess { list -> apps = list.sortedBy { app -> FEATURED.indexOf(app.id).let { if (it < 0) Int.MAX_VALUE else it } } }
            runCatching { tv.inputs() }.onSuccess { inputs = it }
        }
    }

    if (!connected) {
        GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.TvOff, contentDescription = null, tint = Palette.Mist)
                Text(
                    "Com a TV ligada e conectada, os seus apps e entradas HDMI aparecem aqui.",
                    color = Palette.Mist,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    if (inputs.isNotEmpty()) {
        SectionTitle("Entradas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inputs.forEach { input ->
                FilterChip(
                    selected = false,
                    onClick = { run { switchInput(input.id) } },
                    label = { Text(input.label.ifBlank { input.id }) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(containerColor = Palette.GlassFill, labelColor = Palette.Ivory),
                    border = null,
                )
            }
        }
    }

    if (apps.isNotEmpty()) {
        SectionTitle("Apps")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { app -> AppTile(app) { run { launch(app.id) } } }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(
        Modifier
            .width(104.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.GlassFill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Abrir ${app.title}", onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            app.icon?.let { RemoteImage(it, Modifier.size(48.dp)) }
        }
        Text(
            app.title,
            color = Palette.Ivory,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DPad(enabled: Boolean, onButton: (String) -> Unit) {
    Box(
        Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Palette.GlassLine, CircleShape),
    ) {
        PadArrow(Icons.Rounded.KeyboardArrowUp, "Cima", enabled, Modifier.align(Alignment.TopCenter)) { onButton("UP") }
        PadArrow(Icons.Rounded.KeyboardArrowDown, "Baixo", enabled, Modifier.align(Alignment.BottomCenter)) { onButton("DOWN") }
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Esquerda", enabled, Modifier.align(Alignment.CenterStart)) { onButton("LEFT") }
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Direita", enabled, Modifier.align(Alignment.CenterEnd)) { onButton("RIGHT") }
        val interaction = remember { MutableInteractionSource() }
        val scale = pressScale(interaction)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(88.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Palette.Ivory.copy(alpha = if (enabled) 1f else 0.3f))
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "OK") { onButton("ENTER") },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = Palette.Ink, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PadArrow(icon: ImageVector, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(66.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Palette.Ivory else Palette.Mist.copy(alpha = 0.4f), modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun RoundKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    tint: Color = Palette.Ivory,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Palette.GlassFill)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else Palette.Mist.copy(alpha = 0.4f))
    }
}

@Composable
private fun RockerKey(label: String, enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(32.dp)).background(Palette.GlassFill),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(60.dp).clickable(enabled = enabled, role = Role.Button, onClickLabel = "$label mais", onClick = onUp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = "$label +", tint = Palette.Ivory)
        }
        Text(label, color = Palette.Mist, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(60.dp).clickable(enabled = enabled, role = Role.Button, onClickLabel = "$label menos", onClick = onDown), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = "$label -", tint = Palette.Ivory)
        }
    }
}

/** Apps mais usados no Brasil aparecem primeiro. */
private val FEATURED = listOf(
    "netflix", "youtube.leanback.v4", "amazon", "com.disney.disneyplus-prod", "com.globo.globoplay",
    "spotify-beehive", "com.hbo.hbomax", "com.apple.appletv", "com.webos.app.livetv", "com.webos.app.browser",
)
