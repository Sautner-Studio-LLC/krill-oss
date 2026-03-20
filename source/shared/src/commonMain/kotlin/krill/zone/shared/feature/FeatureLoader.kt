package krill.zone.shared.feature

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import krill.zone.shared.*
import krill.zone.shared.io.*

private const val PACKAGE_PREFIX = "krill.zone.shared."

/**
 * Reads a text resource from the classpath (or platform-equivalent bundle).
 * The [name] is the resource filename, e.g. "KrillApp.Server.json".
 * Returns null if the resource is not found on the current platform.
 *
 * Suspend because WASM must use async browser fetch; JVM/Android/iOS
 * implementations complete synchronously.
 */
expect suspend fun readClasspathResource(name: String): String?

/**
 * Derives the resource name for a [KrillApp] type without reflection by encoding it
 * through kotlinx.serialization's sealed-class polymorphic discriminator.
 *
 * For example, [KrillApp.Server] serializes with discriminator
 * `"krill.zone.shared.KrillApp.Server"`, which is trimmed to `"KrillApp.Server"`.
 */
fun KrillApp.resourceName(): String {
    val element = fastJson.encodeToJsonElement(KrillApp.serializer(), this)
    val serialName = element.jsonObject["type"]?.jsonPrimitive?.content
        ?: error("Cannot determine serial name for $this")
    return serialName.removePrefix(PACKAGE_PREFIX)
}

/**
 * Loads and deserializes a [KrillFeature] from the JSON resource file that corresponds
 * to the given [KrillApp] type.  Resource files live in
 * `shared/src/commonMain/resources/` and are named after the type,
 * e.g. `KrillApp.Server.json`.
 */
suspend fun featureLoader(type: KrillApp): KrillFeature {
    val name = type.resourceName()
    if (platform != Platform.WASM) {
        val json = readClasspathResource("$name.json")
            ?: error("Feature resource not found: $name.json")
        return fastJson.decodeFromString<KrillFeature>(json)
    } else {
        val response = httpClient.get("https://$hostName:${SystemInfo.wasmPort}/$name.json") {
            contentType(ContentType.Application.Json)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            error("Feature resource not found url: $name.json")
        }
    }

}