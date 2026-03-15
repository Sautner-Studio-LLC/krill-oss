package krill.zone.shared.io

import io.ktor.client.*
import io.ktor.http.*
import krill.zone.shared.node.*


expect val httpClient: HttpClient




expect val trustHttpClient: TrustHost

interface TrustHost {
    suspend fun fetchPeerCert(url: Url) : Boolean

    suspend fun deleteCert(node: Node)
}