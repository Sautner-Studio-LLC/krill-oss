## What happened

Kraken's nightly architectural scan (finding `a02082373691`) flagged `NodeHttp` for repeated serialization/deserialization overhead. The hypothesis included "no caching or pooling of deserialized Node instances" — the caching claim is a false positive (HTTP calls must return fresh data; caching at `NodeHttp` would serve stale state), but the scan also surfaced a real, narrower issue: two methods allocated objects unnecessarily on every call even in the common no-op case.

**`readNodes()`** always called `.map { … }` on the deserialized list:

```kotlin
response.body<List<Node>>().map {
    if (it.state == NodeState.EXECUTED) it.copy(state = NodeState.NONE) else it
}
```

This creates a new `List` on every `/nodes` poll even when zero nodes carry `EXECUTED` state (the normal case — `EXECUTED` is transient and clears quickly). The old list and the new list are the same size; the only difference is the wrapper allocation.

**`readHealth()`** always called `.copy(state = NodeState.NONE)`:

```kotlin
response.body<Node>().copy(state = NodeState.NONE)
```

A healthy server returns state `NONE`; the copy was redundant in every successful health check.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeHttp.kt` — `readNodes()`: guard the `.map {}` with a `none { }` check; skip the map entirely when no normalization is needed.
- Same file — `readHealth()`: check state before copying; return the deserialized node directly when state is already `NONE`.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/node/NodeHttpStateNormalizationTest.kt`: eight regression tests covering all EXECUTED/mixed/NONE combinations for both methods.
- Bumped `krill-sdk` version `0.0.50 → 0.0.51` per publish rules.

## Prevention

- `.map { if (cond) copy else it }` is not free even when `cond` is always false — it always allocates a new `List`. Guard bulk-transform paths with a `none { }` / `any { }` pre-check so the common no-op case returns the original collection.
- When evaluating a performance lead from an automated scanner, separate the actionable structural finding (unnecessary allocation per call) from speculation about higher-level caching (which requires different evidence and a different fix site). Address the concrete finding; document the dismissed hypothesis so future readers don't re-open it.
- `readNode()` already applied this pattern correctly (conditional copy, no map). Use it as the canonical model for single-node state normalization.
