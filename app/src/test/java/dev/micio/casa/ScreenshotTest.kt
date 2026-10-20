package dev.micio.casa

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import dev.micio.casa.ui.CasaRoot
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.theme.CasaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renderiza as telas com dados de exemplo para revisar o visual sem um tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private class DemoProvider : DeviceProvider {
        override val source = Source.TUYA
        override val pollSeconds = 3600
        override suspend fun fetch() = ProviderSnapshot(
            devices = listOf(
                Device("1", "Luz principal", DeviceKind.LIGHT, source, "Sala", isOn = true, brightness = 0.7f),
                Device("2", "Spots da TV", DeviceKind.LIGHT, source, "Sala", isOn = false),
                Device("3", "Abajur", DeviceKind.LIGHT, source, "Sala", isOn = true),
                Device("4", "Interruptor varanda", DeviceKind.SWITCH, source, "Sala", isOn = false),
                Device("5", "Luz da bancada", DeviceKind.LIGHT, source, "Cozinha", isOn = false),
                Device("6", "Cafeteira", DeviceKind.OUTLET, source, "Cozinha", isOn = true),
                Device("7", "Ventilador de teto", DeviceKind.FAN, source, "Quarto", isOn = false),
                Device("8", "Luz do quarto", DeviceKind.LIGHT, source, "Quarto", isOn = false, online = false),
                Device("9", "Robô Eufy", DeviceKind.VACUUM, source, null, isOn = false, status = "Na base", battery = 100),
                Device("10", "Echo Spot", DeviceKind.SPEAKER, source, "Quarto", isOn = true, status = "Lo-fi beats"),
                Device("11", "Temperatura", DeviceKind.SENSOR, source, "Sala", status = "24,5 °C"),
            ),
            scenes = listOf(
                Scene("s1", "Cheguei em casa", source),
                Scene("s2", "Cinema", source),
                Scene("s3", "Boa noite", source),
                Scene("s4", "Sair de casa", source),
            ),
        )
        override suspend fun execute(device: Device, action: DeviceAction) = Unit
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun render(width: Int, height: Int, demo: Boolean, name: String, action: (() -> Unit)? = null) {
        val app = ApplicationProvider.getApplicationContext<CasaApp>()
        if (demo) app.home.attachForTest(DemoProvider())
        app.home.refreshAll()
        val vm = MainViewModel(app.config, app.home)
        compose.setContent {
            CasaTheme { CasaRoot(vm, WindowSizeClass.calculateFromSize(DpSize(width.dp, height.dp))) }
        }
        compose.waitUntil(5_000) { !demo || app.home.devices.value.isNotEmpty() }
        compose.waitForIdle()
        action?.invoke()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletHome() = render(1280, 800, demo = true, name = "tablet_home")

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletWelcome() = render(1280, 800, demo = false, name = "tablet_welcome")

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletSettings() = render(1280, 800, demo = true, name = "tablet_settings") {
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Smart Life / Tuya").performClick()
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletAlexa() = render(1280, 800, demo = false, name = "tablet_alexa") {
        compose.onNodeWithText("Alexa").performClick()
    }

    @Config(qualifiers = "w411dp-h891dp-mdpi")
    @Test fun phoneHome() = render(411, 891, demo = true, name = "phone_home")
}
