package dev.micio.casa

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.micio.casa.core.FileConfigStorage
import dev.micio.casa.core.IosPlatformActions
import dev.micio.casa.ui.CasaRoot
import dev.micio.casa.ui.components.IosVideo
import dev.micio.casa.ui.components.VideoViewFactory
import dev.micio.casa.ui.theme.CasaTheme
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController

/** Um container por processo, como o Application do Android. */
private object IosApp {
    val container: AppContainer by lazy {
        AppContainer(FileConfigStorage(), IosPlatformActions()).also { container ->
            // Atualizações automáticas só com o app em primeiro plano.
            val center = NSNotificationCenter.defaultCenter
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
                container.setActive(true)
            }
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
                container.setActive(false)
            }
        }
    }
}

/** Chamado pelo app Swift (iosApp/Casa/CasaApp.swift). */
fun MainViewController(videoFactory: VideoViewFactory): UIViewController {
    IosVideo.factory = videoFactory
    val container = IosApp.container
    container.setActive(true)
    return ComposeUIViewController {
        val config by container.config.config.collectAsState()
        // Tablet de parede: impede o iPad de apagar a tela sozinho.
        LaunchedEffect(config.keepScreenOn) {
            UIApplication.sharedApplication.idleTimerDisabled = config.keepScreenOn
        }
        CasaTheme { CasaRoot(container.viewModel) }
    }
}
