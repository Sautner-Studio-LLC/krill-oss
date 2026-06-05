# Inconsistent error handling in `NodeHttp` — dead catch block and missing try-catch

**Issue:** [krill-oss#118](https://github.com/Sautner-Studio-LLC/krill-oss/issues/118)
**Root cause category:** Architectural lead — incomplete parity between mutation methods on the same class
**Module:** `module:krill-sdk`, `module:krill-mcp`

## What happened

A nightly scan flagged two concrete gaps in `NodeHttp` error handling that left the swarm in a worse state than necessary on network failures:

1. **`deleteNode` had a dead `catch(e: Exception) { throw e }` block.** The `postNode` and `invokeNode` methods both check `e.isSSLError()` and call `trustHost.deleteCert(host)` in their catch blocks so that a stale self-signed certificate triggers a re-trust cycle on the next attempt. `deleteNode` silently skipped this — an SSL failure during a delete would rethrow without evicting the cert, leaving the client permanently blocked on subsequent calls to that server until a manual restart. The catch block also produced no log line, so the error was invisible.

2. **`chart` had no try-catch at all.** Every other `NodeHttp` method wraps its HTTP call in `try/catch` and returns a safe zero-value (`null`, `emptyList()`, `byteArrayOf()`) on failure. `chart` was the only outlier — a network failure would propagate as an unhandled exception to the Compose UI layer, which expects a `ByteArray`, not a crash.

The scan also noted that the `KtorApp` JSON parse error path swallowed the exception silently (no debug log), while unhandled exceptions from the `StatusPages` plugin did log. Minor gap but worth closing.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeHttp.kt`:
  - `deleteNode` catch block: added `if (e.isSSLError()) { trustHost.deleteCert(host) }` + `logger.e(e) { ... }` before the re-throw. Now matches `postNode` / `invokeNode`.
  - `chart`: wrapped in `try/catch` returning `byteArrayOf()` on any exception, matching the pattern used by every other method.
- `krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/http/KtorApp.kt`: added `log.debug("JSON parse error on /mcp: ${e.message}")` in the `runCatching.getOrElse` block so parse errors leave a trace.
- `krill-sdk/gradle/libs.versions.toml` + `krill-sdk/build.gradle.kts`: added `ktor-client-mock`, `ktor-client-content-negotiation`, and `ktor-serialization-kotlinx-json` as test dependencies so the new tests can exercise the full Ktor request pipeline with a mock engine.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/node/NodeHttpErrorHandlingTest.kt`: four tests covering (a) SSL exception → `deleteCert` called, (b) non-SSL exception → `deleteCert` not called, (c) any exception → rethrown, (d) `chart` exception → empty array returned.
- `krill-sdk/build.gradle.kts`: bumped version `0.0.40` → `0.0.41`.

## Prevention

- **When adding a new mutation method to `NodeHttp`, copy the SSL error handling pattern from `postNode` / `invokeNode`.** Any method that calls `httpClient.post/put/delete` with `setBody(...)` and rethrows should also check `e.isSSLError()` and call `trustHost.deleteCert(host)` — without this, a rotated server cert permanently blocks the operation.
- **Every `NodeHttp` method that returns a value type (`ByteArray`, `List<T>`, `String?`) must have a try-catch returning the zero value on exception.** Methods that throw intentionally (mutations that the caller must know about) should still log the exception before rethrowing.
- **Methods in the same class that handle the same error class should use the same pattern.** A nightly scan caught this because the pattern was present in two methods and absent in one — a code-review checklist item for `NodeHttp` specifically is to verify that new methods follow the existing SSL error + re-throw contract.
- **When testing Ktor HTTP calls with `MockEngine` against a method that serializes a polymorphic body**, a minimal `SerializersModule` is required in the test client's `ContentNegotiation` install. `NodeMetaData` is an interface (not sealed), so its subtypes are not auto-registered — the test must explicitly `polymorphic(NodeMetaData::class) { subclass(...) }` for each subtype used in the test fixture.
