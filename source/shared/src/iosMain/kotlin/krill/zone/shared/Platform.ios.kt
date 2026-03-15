package krill.zone.shared

import platform.Foundation.*
import platform.UIKit.*
import kotlin.experimental.*


private const val INSTALL_ID_KEY = "krill_install_id"

actual val installId: () -> String
    get() = { createOrReadInstallId() }

private fun createOrReadInstallId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    val existingId = defaults.stringForKey(INSTALL_ID_KEY)

    return if (existingId != null) {
        existingId
    } else {
        // Generate a new UUID and store it
        val newId = NSUUID().UUIDString()
        defaults.setObject(newId, forKey = INSTALL_ID_KEY)
        defaults.synchronize()
        newId
    }
}

actual val hostName: String
    get() {
        // Try to get a friendly device name
        // UIDevice.currentDevice.name gives user-set name like "John's iPhone"
        // UIDevice.currentDevice.model gives model like "iPhone", "iPad"
        // UIDevice.currentDevice.systemName gives "iOS"

        val device = UIDevice.currentDevice
        val deviceName = device.name // User's device name (e.g., "John's iPhone")
        val deviceModel = device.model // Model gateType (e.g., "iPhone", "iPad")

        // Try to get a more specific model identifier using UIDevice.modelIdentifier
        // This gives identifiers like "iPhone14,2" for iPhone 13 Pro
        return try {
            // Use device name if available and not default
            if (deviceName.isNotEmpty() && !deviceName.equals("iPhone", ignoreCase = true) &&
                !deviceName.equals("iPad", ignoreCase = true)) {
                deviceName
            } else {
                // Fall back to model
                deviceModel
            }
        } catch (_: Exception) {
            // Fallback to just model if anything fails
            device.model
        }
    }

@OptIn(ExperimentalNativeApi::class)
actual val platform: Platform
    get() = Platform.IOS

