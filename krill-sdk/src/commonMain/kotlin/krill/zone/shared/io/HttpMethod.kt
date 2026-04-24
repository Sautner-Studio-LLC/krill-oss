/**
 * HTTP verb enumeration used by the Krill client/server wire protocol.
 *
 * The Krill HTTP layer uses this enum instead of [io.ktor.http.HttpMethod] so
 * call sites across the shared KMP module (which is compiled for targets that
 * historically differed in their Ktor exposure — notably wasmJs) can work with
 * a single, framework-agnostic value type.
 */
package krill.zone.shared.io

/**
 * The set of HTTP verbs the Krill protocol emits or accepts.
 *
 * Kept intentionally small: Krill does not need OPTIONS/HEAD on the request
 * side, and responses are modelled separately. Entries map 1:1 onto the
 * corresponding Ktor HTTP method when a request is actually dispatched.
 */
enum class HttpMethod {
    /** Read-only retrieval. Used for fetching node state, snapshots, config. No request body. */
    GET,

    /** Create or submit. Used for node creation, triggering executors, uploading blobs. Carries a request body. */
    POST,

    /** Full replace of a resource. Used when a client replaces an entire node's metadata or configuration. Carries a request body. */
    PUT,

    /** Destructive removal. Used to delete a node, peer relationship, or persisted artifact. Body optional. */
    DELETE,

    /** Partial update. Used when a client mutates a subset of fields on an existing node without replacing the whole record. Carries a request body. */
    PATCH,
}
