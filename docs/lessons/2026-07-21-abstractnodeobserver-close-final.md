# AbstractNodeObserver.close() was overridable — cleanup was opt-in

**Issue:** [krill-oss#207](https://github.com/Sautner-Studio-LLC/krill-oss/issues/207)
**Root cause category:** Architecture — structured-concurrency contract enforced only by convention
**Module:** `krill-sdk`

## What happened

`AbstractNodeObserver.close()` (added in #186, see
`docs/lessons/2026-06-27-nodeobserver-scoped-base.md`) implemented
`observerScope.cancel()` but did not mark the override `final`. Kotlin
overrides are open by default, so a subclass could override `close()`,
omit `super.close()`, and the compiler would not object — the KDoc said
"must call `super.close()`" but nothing enforced it. Any such subclass
would leak every coroutine launched into `observerScope` past the
observer's intended lifetime (duplicate HTTP polling, stale state
delivery to disposed UI, etc.).

No production subclass in this repo actually did this — `AbstractNodeObserver`
has no concrete subclass here (`DefaultNodeObserver` lives in the private
`krill` repo) — but the gap was flagged by Kraken's nightly architectural
scan as a structural risk for any current or future SDK consumer.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/AbstractNodeObserver.kt`:
  marked `close()` `final override` and added a `protected open fun onClose()`
  hook, called after `observerScope.cancel()`. Subclasses needing extra
  teardown now override `onClose()` instead of `close()`, so cancellation is
  guaranteed by the type system rather than by convention.
- Updated `NodeObserver.kt` KDoc to recommend extending `AbstractNodeObserver`
  over hand-rolling the scope-bound pattern, since the base class now
  type-enforces the cancellation contract.
- Updated `AbstractNodeObserverTest`: the existing "use block triggers close"
  test now overrides `onClose()` instead of `close()` (the old override would
  no longer compile); added a compile-time-proof test that a subclass cannot
  override `close()`.
- Bumped `krill-sdk` version `0.0.60 → 0.0.61`.

## Prevention

- When a base class exists specifically to guarantee a resource-cleanup
  invariant (cancel a scope, release a lock, close a handle), mark the
  method implementing that invariant `final` and expose a separate `open`
  hook for subclass-specific extension. "Must call `super.x()`" is a
  documentation-only contract that the compiler cannot check — a type-level
  final/hook split can.
- This complements `docs/lessons/2026-06-27-nodeobserver-autocloseable.md`,
  which made `close()` reachable via `AutoCloseable`/`use {}` but stopped
  short of closing the override gap; this lesson closes it.
