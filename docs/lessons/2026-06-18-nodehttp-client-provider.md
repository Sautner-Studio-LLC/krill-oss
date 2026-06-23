## What happened

`NodeHttp` accepted a concrete `HttpClient` at construction time and captured it permanently:

```kotlin
class NodeHttp(private val httpClient: HttpClient, ...)
```

In the krill platform, `NodeHttp` is a Koin `single` that was created once at startup. When the app fetched a new peer's TLS certificate on first contact, it rebuilt the `HttpClient` with an updated trust store and closed the old one — completing its `SupervisorJob`. Every subsequent call through the captured (now-closed) client threw `JobCancellationException: Parent job is Completed`. On first app launch this caused `readNodes` to silently return no child nodes for a newly-discovered server; a restart (which created a fresh Koin graph with the new client) worked fine.

The immediate downstream fix was krill PR #531 (never close the client; reload TLS trust material in place). That resolved the user-facing symptom. But the SDK seam — capturing a non-swappable concrete client — remained a latent fragility: any consumer that legitimately needs to replace its client at runtime will hit the same class of bug, and the SDK gave them no safe way to do it.

## Fix

- Changed the primary constructor of `NodeHttp` (`krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeHttp.kt`) to accept `clientProvider: () -> HttpClient` instead of `httpClient: HttpClient`. Every request method calls `clientProvider()` immediately before use, so a swapped provider is always picked up.
- Added a secondary convenience constructor `NodeHttp(httpClient: HttpClient, ...)` that delegates to the primary as `clientProvider = { httpClient }`, preserving source and binary compatibility with all existing call sites.
- Added two regression tests in `NodeHttpErrorHandlingTest`: one verifies `clientProvider` is invoked once per request (not cached), the other verifies the convenience constructor is a transparent alias.
- Bumped `krill-sdk` version `0.0.49 → 0.0.50` per publish rules.

## Prevention

- Prefer `() -> T` provider lambdas over concrete instances in SDK classes whose lifecycle may outlast the object they were constructed with. A provider is called at use time; a captured instance is locked in at construction time.
- When a class is intended for DI registration as a long-lived singleton (`single` in Koin, `@Singleton` in Hilt), audit its constructor parameters: anything that can be rebuilt during the app's lifetime (TLS clients, authenticated HTTP clients, connection pools) is a candidate for a provider instead of a direct capture.
- When writing a backward-compatible API widening (adding a provider variant alongside an instance variant), keep the instance variant as a secondary constructor that wraps with `{ instance }` rather than as a default parameter — default parameters are `@JvmOverloads`-awkward in KMP and read less clearly in call sites.
