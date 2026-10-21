package dev.micio.casa

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.micio.casa.core.CameraBrand
import dev.micio.casa.core.CameraConfig
import dev.micio.casa.core.CameraPower
import dev.micio.casa.core.RiverCity
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.home.RiverState
import dev.micio.casa.integrations.river.RiverMonitor
import dev.micio.casa.integrations.river.RiverReport
import dev.micio.casa.home.WeatherState
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.weather.OpenMeteo
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import dev.micio.casa.ui.CasaRoot
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

    private fun render(width: Int, height: Int, demo: Boolean, name: String, action: (() -> Unit)? = null) {
        val app = ApplicationProvider.getApplicationContext<CasaApp>()
        val c = app.container
        if (demo) c.home.attachForTest(DemoProvider())
        c.home.refreshAll()
        if (demo) c.weather.setForTest(
            WeatherState(
                place = WeatherPlace("São Paulo", "São Paulo, Brasil", -23.55, -46.63),
                weather = OpenMeteo.parseForecast(javaClass.getResource("/open-meteo-forecast.json")!!.readText()),
            )
        )
        c.river.setForTest(if (demo) RiverState(RiverCity(4214805, "Rio do Sul", "SC"), riverSample()) else RiverState())
        compose.setContent {
            CasaTheme { CasaRoot(c.viewModel) }
        }
        compose.waitUntil(5_000) { !demo || c.home.devices.value.isNotEmpty() }
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

    private fun riverSample(): RiverReport {
        fun read(name: String) = javaClass.getResource(name)!!.readText()
        val stations = RiverMonitor.merge(RiverMonitor.parseStationList(read("/rio-stations.json")), RiverMonitor.parsePanel(read("/rio-panel.json")))
        return RiverReport(stations, RiverMonitor.parseHistory(read("/rio-history.json")))
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletCameras() {
        val app = ApplicationProvider.getApplicationContext<CasaApp>()
        app.container.config.update { c ->
            c.copy(cameras = listOf(
                CameraConfig("a", "Portão", CameraBrand.ICSEE, "rtsp://192.0.2.1/x", room = "Área externa", power = CameraPower.ON_DEMAND),
                CameraConfig("b", "Quintal", CameraBrand.ICSEE, "rtsp://192.0.2.2/x", room = "Área externa", power = CameraPower.ON_DEMAND),
                CameraConfig("c", "Garagem", CameraBrand.ICSEE, "rtsp://192.0.2.3/x", power = CameraPower.ON_DEMAND),
            ))
        }
        render(1280, 800, demo = true, name = "tablet_cameras") {
            compose.onNodeWithText("Câmeras").performClick()
        }
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun tabletWeather() = render(1280, 800, demo = true, name = "tablet_weather") {
        compose.onNodeWithText("Clima").performClick()
    }

    @Config(qualifiers = "w411dp-h891dp-mdpi")
    @Test fun phoneWeather() = render(411, 891, demo = true, name = "phone_weather") {
        compose.onNodeWithText("Parcialmente nublado").performClick()
    }

    @Config(qualifiers = "w411dp-h891dp-mdpi")
    @Test fun phoneHome() = render(411, 891, demo = true, name = "phone_home")
}
