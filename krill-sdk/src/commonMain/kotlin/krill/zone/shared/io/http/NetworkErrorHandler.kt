/**
 * Lightweight classification helpers for network-layer exceptions thrown by the
 * Krill HTTP client. Used by call sites that need to react differently to TLS
 * trust failures (where the right move is usually to drop and re-fetch the
 * peer's self-signed certificate) versus generic transport errors.
 */
package krill.zone.shared.io.http

/**
 * Returns `true` when this exception's message smells like a TLS / X.509
 * certificate validation failure.
 *
 * The check is deliberately string-based on the exception message — Krill's
 * shared module compiles for JVM, Android, iOS, and wasmJs, and each platform
 * raises a different concrete exception type for the same underlying TLS
 * problem. Matching `"signature"` and `"certification"` covers the messages
 * produced by the JDK (`SunCertPathBuilderException`, `SignatureException`),
 * Android (`CertPathValidatorException`), Darwin's secure transport, and the
 * browser's `fetch` failure surface.
 *
 * Callers (notably `NodeHttp`) use this to decide whether to evict a stored
 * peer certificate and trigger a fresh trust handshake before retrying.
 */
fun Exception.isSSLError(): Boolean {
    val message = this.message ?: ""

    return message.contains("signature") ||
            message.contains("certification")
}
