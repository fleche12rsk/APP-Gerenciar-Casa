package dev.micio.casa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Source
import dev.micio.casa.ui.components.ambientBackground
import dev.micio.casa.ui.screens.AlexaScreen
import dev.micio.casa.ui.screens.CamerasScreen
import dev.micio.casa.ui.screens.DeviceSheet
import dev.micio.casa.ui.screens.HomeScreen
import dev.micio.casa.ui.screens.RemotesScreen
import dev.micio.casa.ui.screens.SettingsScreen
import dev.micio.casa.ui.screens.TvScreen
import dev.micio.casa.ui.screens.WeatherScreen
import dev.micio.casa.ui.theme.Palette

/** [inBottomBar] = false: no celular a tela é aberta por outro lugar (ex.: selo do clima no Início). */
enum class Destination(val label: String, val icon: ImageVector, val inBottomBar: Boolean = true) {
    HOME("Início", Icons.Rounded.Home),
    WEATHER("Clima", Icons.Rounded.WbSunny, inBottomBar = false),
    CAMERAS("Câmeras", Icons.Rounded.Videocam),
    TV("TV", Icons.Rounded.Tv),
    REMOTES("Controles", Icons.Rounded.SettingsRemote),
    ALEXA("Alexa", Icons.Rounded.RecordVoiceOver),
    SETTINGS("Ajustes", Icons.Rounded.Tune),
}

@Composable
fun CasaRoot(vm: MainViewModel) {
    // Tablet (ou celular deitado) ganha a barra lateral; celular em pé, a barra inferior.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CasaRoot(vm, wide = maxWidth >= 600.dp)
    }
}

@Composable
private fun CasaRoot(vm: MainViewModel, wide: Boolean) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var sheetDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val allDevices by vm.home.allDevices.collectAsState()

    LaunchedEffect(Unit) {
        vm.home.messages.collect { snackbar.showSnackbar(it) }
    }
    PlatformBackHandler(enabled = destination != Destination.HOME) { destination = Destination.HOME }

    val listenVoice = rememberVoiceInput(vm::handleVoice) { vm.home.notify(it) }

    val openDevice: (Device) -> Unit = { device ->
        when {
            device.kind == DeviceKind.TV && device.source == Source.LG -> destination = Destination.TV
            device.kind == DeviceKind.CAMERA -> destination = Destination.CAMERAS
            else -> sheetDeviceId = device.id
        }
    }

    Row(Modifier.fillMaxSize().ambientBackground()) {
        if (wide) {
            NavigationRail(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
                header = { Spacer(Modifier.height(12.dp)) },
            ) {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Palette.Ink,
                            indicatorColor = Palette.Ivory,
                            unselectedIconColor = Palette.Mist,
                            unselectedTextColor = Palette.Mist,
                            selectedTextColor = Palette.Ivory,
                        ),
                    )
                }
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = Palette.Night.copy(alpha = 0.92f)) {
                        Destination.entries.filter { it.inBottomBar }.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.Ink,
                                    indicatorColor = Palette.Ivory,
                                    unselectedIconColor = Palette.Mist,
                                    unselectedTextColor = Palette.Mist,
                                    selectedTextColor = Palette.Ivory,
                                ),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (destination == Destination.HOME) MicButton(listenVoice)
            },
        ) { padding ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 30 }) togetherWith fadeOut(tween(140))
                },
                modifier = Modifier.padding(padding),
                label = "destination",
            ) { target ->
                when (target) {
                    Destination.HOME -> HomeScreen(
                        vm, wide, openDevice,
                        onOpenSettings = { destination = Destination.SETTINGS },
                        onOpenWeather = { destination = Destination.WEATHER },
                    )
                    Destination.WEATHER -> WeatherScreen(vm, wide)
                    Destination.CAMERAS -> CamerasScreen(vm, wide, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.TV -> TvScreen(vm, wide, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.REMOTES -> RemotesScreen(vm, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.ALEXA -> AlexaScreen(vm, wide, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.SETTINGS -> SettingsScreen(vm, wide, onOpenDevice = { sheetDeviceId = it.id })
                }
            }
        }
    }

    allDevices.firstOrNull { it.id == sheetDeviceId }?.let { device ->
        DeviceSheet(vm, device, onDismiss = { sheetDeviceId = null })
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ring",
    )
    val ringAlpha by pulse.animateFloat(0.35f, 0f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "ringAlpha")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp)
                .graphicsLayer { scaleX = ring; scaleY = ring; alpha = ringAlpha }
                .clip(CircleShape)
                .background(Palette.Ember)
        )
        FloatingActionButton(
            onClick = onClick,
            containerColor = Palette.Ember,
            contentColor = Palette.Ink,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Comando de voz")
        }
    }
}
