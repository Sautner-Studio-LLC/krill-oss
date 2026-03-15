package krill.zone

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import krill.zone.server.*
import krill.zone.shared.*
import java.io.*
import kotlin.system.*

fun main() {
    SystemInfo.setServer(true )
    val configjson = File("/etc/krill/config.json")
    if (!configjson.exists()) {
        println("Config file does not exist!")
        exitProcess(1)
    }
    else {
        val config = fastJson.decodeFromString<ServerConfig>(configjson.readText())
        embeddedServer(Netty, applicationEnvironment { }, {
            envConfig(config)
        }, module = Application::module).start(wait = true)
    }

}



