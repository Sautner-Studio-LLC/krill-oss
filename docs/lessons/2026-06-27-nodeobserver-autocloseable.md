# NodeObserver lacked AutoCloseable — lifecycle contract was implicit

**Issue:** [krill-oss#176](https://github.com/Sautner-Studio-LLC/krill-oss/issues/176)
**Root cause category:** Architecture — missing structured-concurrency contract at interface seam
**Module:** `krill-sdk`

## What happened

`NodeObserver` declared `fun close()` in its interface body but did not extend
`AutoCloseable`. This meant:

- The `use {}` lifecycle pattern was unavailable to callers.
- There was no language-level signal that `close()` *must* be called and *must*
  cancel all child coroutines; the contract existed only in KDoc.
- Third-party or test implementations could omit coroutine cancellation without
  a compile-time or runtime guard.

The `DefaultNodeObserver` implementation in the private `krill` repo already
handled scope correctly (accepts a `CoroutineScope`, creates a child scope with
`SupervisorJob`, cancels on `close()`), so there was no production coroutine
leak in the current codebase. However, the interface did not enforce this for
other implementations.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeObserver.kt`:
  changed `interface NodeObserver` to `interface NodeObserver : AutoCloseable`.
  The existing `close()` declaration overrides `AutoCloseable.close()`. No
  caller changes were required — all existing implementations already defined
  `close()`.
- Updated KDoc to state explicitly that `close()` *must* cancel all child
  coroutines and to show the canonical scope-bound implementation pattern.
- Added `NodeObserverLifecycleTest` (in `commonTest`) covering:
  1. `NodeObserver` is assignable to `AutoCloseable` (compile-time proof).
  2. `use {}` invokes `close()`.
  3. A scope-bound implementation (`SupervisorJob` child scope) cancels child
     coroutines when `close()` is called.
- Bumped `krill-sdk` version `0.0.56 → 0.0.57`.

## Prevention

- When an interface declares a `close()` (or any resource-teardown method) that
  callers *must* call, extend `AutoCloseable`. It costs nothing for existing
  implementations (they already define the method) and provides `use {}` and
  IDE/lint reminders for free.
- SDK interfaces that represent lifecycle-scoped resources should make their
  resource model explicit at the type level, not just in KDoc. Type-enforced
  contracts survive refactors; docs do not.
- Coroutine cancellation in `close()` is not optional: an observer that holds
  child jobs but does not cancel them on `close()` leaks background work,
  drives disposed UI state, and accumulates `MutableStateFlow` subscriptions.
  The canonical pattern is `CoroutineScope(parentScope.coroutineContext +
  SupervisorJob(parentScope.coroutineContext[Job]))` with `scope.cancel()` in
  `close()`.
