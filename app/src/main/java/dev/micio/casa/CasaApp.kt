package dev.micio.casa

import android.app.Application
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.home.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CasaApp : Application() {

    lateinit var config: ConfigRepository
        private set
    lateinit var home: HomeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        config = ConfigRepository(this)
        home = HomeRepository(config, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
}
