package dev.micio.casa

import android.app.Application
import dev.micio.casa.core.AndroidPlatformActions
import dev.micio.casa.core.SecureStore
import java.io.File

class CasaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            storage = SecureStore(File(filesDir, "config.bin")),
            platform = AndroidPlatformActions(this),
        )
    }
}
