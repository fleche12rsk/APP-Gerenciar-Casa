package dev.micio.casa

import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.core.ConfigStorage
import dev.micio.casa.core.PlatformActions
import dev.micio.casa.home.HomeRepository
import dev.micio.casa.home.RiverRepository
import dev.micio.casa.home.WeatherRepository
import dev.micio.casa.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Monta o app: configuração, integrações e o estado das telas. Um por processo. */
class AppContainer(storage: ConfigStorage, platform: PlatformActions) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val config = ConfigRepository(storage)
    val home = HomeRepository(config, scope)
    val weather = WeatherRepository(config, scope)
    val river = RiverRepository(config, scope)
    val viewModel = MainViewModel(config, home, weather, river, platform)

    /** Liga a atualização automática só com o app na tela. */
    fun setActive(active: Boolean) {
        home.setActive(active)
        weather.setActive(active)
        river.setActive(active)
    }
}
