package krill.zone


import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import co.touchlab.kermit.*
import krill.zone.app.*
import krill.zone.app.di.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import org.koin.core.context.*
import java.awt.*

fun main() = application {
    // Configure Kermit to use SLF4J on JVM for proper logging with logback
    Logger.setLogWriters(JvmLogWriter())
    SystemInfo.setServer(false)
    startKoin {
        modules(
            sharedModule, appModule, composeModule, platformModule, clientProcessModule, clientNodeManagerModule)

    }

    val icon = Toolkit.getDefaultToolkit().getImage(
        ClassLoader.getSystemResource("icon.png")
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Krill",
        state = rememberWindowState(width = 768.dp, height = 1024.dp)
    ) {
        window.iconImage = icon
        App()
    }
}

