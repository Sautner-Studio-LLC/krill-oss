package krill.zone.shared.feature

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import krill.zone.shared.io.*

/**
 * WASM/JS implementation — uses an async Ktor [HttpClient] backed by the
 * browser's `fetch()` API.  The bare path (e.g. "KrillApp.Server.json") is
 * resolved relative to the page origin, so the Ktor server's `staticZip("/")`
 * route serves it from the wasm-archive.zip root.
 */
//private val resourceClient by lazy { HttpClient(Js) }

actual suspend fun readClasspathResource(name: String): String? {
    return try {
        val response = httpClient.get(name)
        if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
    } catch (_: Throwable) {
        null
    }
}
