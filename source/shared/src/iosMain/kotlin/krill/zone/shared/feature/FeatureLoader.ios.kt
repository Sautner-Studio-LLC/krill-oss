@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package krill.zone.shared.feature

import platform.Foundation.*

/**
 * iOS implementation — reads from the main NSBundle.
 * KMP resources from `shared/src/commonMain/resources/` are bundled into the
 * framework and accessible via NSBundle.
 */
actual suspend fun readClasspathResource(name: String): String? {
    // Strip the .json extension to get the resource name for NSBundle lookup
    val baseName = name.substringBeforeLast(".")
    val ext = name.substringAfterLast(".", "")

    val path = NSBundle.mainBundle.pathForResource(baseName, ext)
        ?: return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}

