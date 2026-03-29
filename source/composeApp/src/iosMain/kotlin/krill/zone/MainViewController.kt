package krill.zone

import androidx.compose.ui.window.*
import krill.zone.app.*
import krill.zone.app.di.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import krill.zone.shared.lifecycle.*
import org.koin.core.context.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}

fun initKoin() {
    SystemInfo.setServer(false)
    registerLifecycleObserver()
    startKoin {
        modules(
            sharedModule, appModule, composeModule,
            platformModule, clientProcessModule, clientNodeManagerModule
        )
    }
}

private var lifecycleRegistered = false

private fun registerLifecycleObserver() {
    if (lifecycleRegistered) return
    lifecycleRegistered = true

    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(
        UIApplicationDidEnterBackgroundNotification,
        null,
        NSOperationQueue.mainQueue
    ) { _ ->
        AppLifecycle.onBackground()
    }
    center.addObserverForName(
        UIApplicationWillEnterForegroundNotification,
        null,
        NSOperationQueue.mainQueue
    ) { _ ->
        AppLifecycle.onForeground()
    }
}


