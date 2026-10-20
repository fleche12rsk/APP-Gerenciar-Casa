package dev.micio.casa.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.micio.casa.ui.theme.Palette

enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Rounded.Home),
    CAMERAS("Câmeras", Icons.Rounded.Videocam),
    TV("TV", Icons.Rounded.Tv),
    REMOTES("Controles", Icons.Rounded.SettingsRemote),
    ALEXA("Alexa", Icons.Rounded.RecordVoiceOver),
    SETTINGS("Ajustes", Icons.Rounded.Tune),
}

@Composable
fun CasaRoot(vm: MainViewModel, windowSize: WindowSizeClass) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var sheetDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val allDevices by vm.home.allDevices.collectAsStateWithLifecycle()
    val wide = windowSize.widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(Unit) {
        vm.home.messages.collect { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = destination != Destination.HOME) { destination = Destination.HOME }

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
                        Destination.entries.forEach { item ->
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
                    Destination.HOME -> HomeScreen(vm, wide, openDevice, onOpenSettings = { destination = Destination.SETTINGS })
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

/** Abre o reconhecimento de voz do Android em português e devolve a frase entendida. */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { latest(it) }
    }
    return remember(context) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga um comando para a casa")
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                onError("Este tablet não tem reconhecimento de voz instalado (app Google).")
            }
        }
    }
}
