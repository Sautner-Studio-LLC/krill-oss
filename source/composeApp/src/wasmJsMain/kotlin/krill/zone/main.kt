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
    SystemInfo.wasmApiKey = window.location.search
        .removePrefix("?")
        .split("&")
        .mapNotNull { param ->
            val (key, value) = param.split("=", limit = 2).let {
                it[0] to it.getOrElse(1) { "" }
            }
            if (key == "api_key") value else null
        }
        .firstOrNull()
        ?.takeIf { it.isNotBlank() }

    startKoin {
        modules(
            appModule, composeModule, platformModule, clientProcessModule, clientNodeManagerModule, sharedModule )

    }
    ComposeViewport(document.body!!) {
        App()
    }


}

