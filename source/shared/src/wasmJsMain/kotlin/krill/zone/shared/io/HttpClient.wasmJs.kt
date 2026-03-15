package krill.zone.shared.io

import io.ktor.http.*
import krill.zone.shared.node.*

actual val trustHttpClient: TrustHost
    get() =TrustHostWasm()

class TrustHostWasm : TrustHost {
    override suspend fun fetchPeerCert(url: Url): Boolean {
        return true
    }

    override suspend fun deleteCert(node: Node) {
        //noop in wask
    }

}