package krill.zone.shared

import java.awt.*
import java.io.*
import java.net.*
import java.util.*

val installIdPath = if (SystemInfo.isServer()) { "/etc/krill/install_id" } else { "${System.getProperty("user.home")}/.krill/install_id" }

actual val installId: () -> String
    get() = {createOrReadInstallId()}

private fun createOrReadInstallId() : String {
    val idFile = File(installIdPath)
    return if (idFile.exists() && idFile.isFile) {
        idFile.readText().trim().replace("\n", "")
    } else {
        val id = UUID.randomUUID().toString()
        idFile.writeText(id)
        id
    }
}


actual val platform: Platform
    get() {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val isLinux = osName.contains("linux")
        arch.contains("arm") || arch.contains("aarch64")

        // 1. Detect Raspberry Pi from /proc/cpuinfo
        val cpuInfo = runCatching { File("/proc/cpuinfo").readText() }.getOrNull()
        val isRaspberryPi = cpuInfo?.contains("Raspberry Pi", ignoreCase = true) == true ||
                cpuInfo?.contains("BCM", ignoreCase = true) == true

        // 2. Detect if headless
        val isHeadless = GraphicsEnvironment.isHeadless()
        val isServer = SystemInfo.isServer()
        return when {
            isRaspberryPi -> Platform.RASPBERRY_PI
            isLinux && isHeadless && isServer -> Platform.HEADLESS_SERVER

            !isHeadless && !isServer -> Platform.DESKTOP
            else -> Platform.UNKNOWN
        }
    }


actual val hostName: String
    get() =  "${InetAddress.getLocalHost().hostName}"


