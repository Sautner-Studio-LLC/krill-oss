/**
 * Abstraction over per-peer TLS-trust storage.
 *
 * Krill servers ship with self-signed certificates that clients pin on
 * first contact (via the server's `/trust` endpoint). Implementations of
 * [TrustHost] handle the platform-specific bits of fetching and persisting
 * those certificates — JVM/Android use a filesystem trust-store directory,
 * iOS uses the Keychain, wasmJs uses a no-op (the browser already pinned).
 */
package krill.zone.shared.io

import io.ktor.http.Url
import krill.zone.shared.node.Node

/**
 * Per-platform peer-certificate storage and retrieval.
 *
 * The shared HTTP layer asks a [TrustHost] to fetch a fresh cert when an
 * outbound call fails with an SSL error (see
 * [krill.zone.shared.io.http.isSSLError]) so the next attempt can succeed
 * without an explicit user action.
 */
interface TrustHost {
    /**
     * Fetches the server's public cert from `<url>/trust` and persists it
     * in the platform's trust store. Returns `true` if the cert was
     * stored or already matched the existing one; `false` if the fetch
     * failed.
     */
    suspend fun fetchPeerCert(url: Url): Boolean

    /**
     * Removes any cached cert for the [node]'s host. Called when the
     * client decides a stored cert is no longer valid (e.g. server
     * regenerated its key pair) so the next request triggers a fresh
     * [fetchPeerCert].
     */
    suspend fun deleteCert(node: Node)
}
