package dev.micio.casa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.micio.casa.ui.CasaRoot
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.theme.CasaTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as CasaApp

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = viewModelFactory {
                initializer { MainViewModel(app.config, app.home) }
            })
            val config by vm.config.config.collectAsStateWithLifecycle()
            val view = LocalView.current

            // Tablet de parede: tela sempre acesa e, se quiser, sem barras do sistema.
            DisposableEffect(config.keepScreenOn) {
                view.keepScreenOn = config.keepScreenOn
                onDispose { view.keepScreenOn = false }
            }
            DisposableEffect(config.immersive) {
                val controller = WindowCompat.getInsetsController(window, view)
                if (config.immersive) {
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            CasaTheme {
                CasaRoot(vm, calculateWindowSizeClass(this))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.home.setActive(true)
    }

    override fun onStop() {
        super.onStop()
        app.home.setActive(false)
    }
}
