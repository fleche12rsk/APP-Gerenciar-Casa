package dev.micio.casa

import android.app.Application
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.home.HomeRepository
import dev.micio.casa.home.RiverRepository
import dev.micio.casa.home.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CasaApp : Application() {

    lateinit var config: ConfigRepository
        private set
    lateinit var home: HomeRepository
        private set
    lateinit var weather: WeatherRepository
        private set
    lateinit var river: RiverRepository
        private set

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        home = HomeRepository(config, scope)
        weather = WeatherRepository(config, scope)
        river = RiverRepository(config, scope)
    }
}
