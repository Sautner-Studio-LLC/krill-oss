# NodeObserver lacked an abstract base class for safe CoroutineScope management

**Issue:** [krill-oss#186](https://github.com/Sautner-Studio-LLC/krill-oss/issues/186)
**Root cause category:** Architecture — missing structured-concurrency scaffolding at the SDK boundary
**Module:** `krill-sdk`

## What happened

`NodeObserver` was extended with `AutoCloseable` (#176) and documents the canonical
scope-bound pattern in KDoc, but the SDK shipped no concrete scaffolding. Every SDK
consumer or test harness implementing `NodeObserver` had to reproduce the same
boilerplate:

```kotlin
private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(...))
override fun close() = scope.cancel()
```

Without a base class, there was no compile-time guard against omitting the
`SupervisorJob`, using the wrong parent context, or forgetting `scope.cancel()` in
`close()`. The risk was highest in test harnesses and one-off SDK integrations where
the author might not know to follow the KDoc pattern.

## Fix

- Added `AbstractNodeObserver` (`krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/AbstractNodeObserver.kt`):
  - Accepts a `parentScope: CoroutineScope` in its constructor.
  - Exposes `protected val observerScope` as a supervised child of `parentScope`
    (`SupervisorJob(parentScope.coroutineContext[Job])`).
  - Implements `close()` as `observerScope.cancel()`.
  - Leaves `observe()` and `remove()` abstract.
- Added `AbstractNodeObserverTest` (`commonTest`) covering:
  1. `close()` cancels all child coroutines (finalizer runs in `try/finally`).
  2. `use {}` invokes `close()` via the `AutoCloseable` chain.
  3. Parent-scope cancellation propagates through the supervisor hierarchy.
- Bumped `krill-sdk` version `0.0.57 → 0.0.58`.

## Prevention

- When an interface declares a resource-lifecycle contract (observe/cancel/close),
  ship a companion abstract base class that implements the boilerplate correctly once.
  Documentation alone does not prevent callers from getting it wrong.
- `SupervisorJob(parent[Job])` is the correct parent linkage — omitting the parent
  argument creates an orphan scope whose cancellation is not propagated upward.
- Always cover the abstract class with tests that prove parent-propagated cancellation;
  the test is the contract, not the KDoc.
