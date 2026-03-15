package krill.zone.server

import krill.zone.shared.krillapp.server.*
import java.util.concurrent.*

object SystemCommandOperator {
    fun getSystemCommandOutput(command: String): String {
        return try {
            val parts = command.split("\\s".toRegex())
            val proc = ProcessBuilder(*parts.toTypedArray())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.waitFor(60, TimeUnit.SECONDS)
            proc.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun getSystemInfo(): ServerInfo {
        val os = getSystemCommandOutput("cat /etc/debian_version")
        val kernel = getSystemCommandOutput("uname -r")
        // Note: dmidecode usually requires root privileges
        val model = getSystemCommandOutput("cat /sys/class/dmi/id/product_name")

        return ServerInfo(
            os = os,
            model = model,
            kernel = kernel,
        )
    }
}