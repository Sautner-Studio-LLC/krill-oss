package krill.zone

import androidx.compose.ui.window.*
import krill.zone.app.*
import krill.zone.app.di.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import org.koin.core.context.*

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}

fun initKoin() {
    SystemInfo.setServer(false)
    startKoin {
        modules(
            sharedModule, appModule, composeModule,
            platformModule, clientProcessModule, clientNodeManagerModule
        )
    }
}


