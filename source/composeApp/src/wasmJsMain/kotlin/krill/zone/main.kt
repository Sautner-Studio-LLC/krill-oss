package krill.zone

import androidx.compose.ui.*
import androidx.compose.ui.window.*
import kotlinx.browser.*
import krill.zone.app.*
import krill.zone.app.di.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import org.koin.core.context.*


@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    SystemInfo.setServer(false)
    SystemInfo.wasmPort = window.location.port.toInt()

    startKoin {
        modules(
            appModule, composeModule, platformModule, clientProcessModule, clientNodeManagerModule, sharedModule )

    }
    ComposeViewport(document.body!!) {
        App()
    }


}

