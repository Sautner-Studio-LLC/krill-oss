package krill.zone.shared.io

import io.ktor.client.*
import io.ktor.http.*
import krill.zone.shared.node.*


expect val httpClient: HttpClient

/**
 * Dedicated HttpClient for SSE connections. On WASM/JS this is a separate instance
 * so the long-lived SSE stream does not block regular HTTP requests in the same client.
 */
expect val sseHttpClient: HttpClient




expect val trustHttpClient: TrustHost

interface TrustHost {
    suspend fun fetchPeerCert(url: Url) : Boolean

    suspend fun deleteCert(node: Node)
}