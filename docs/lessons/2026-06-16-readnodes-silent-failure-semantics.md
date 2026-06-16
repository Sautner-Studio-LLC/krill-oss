# `readNodes` returned `emptyList()` on failure — indistinguishable from an empty server

**Issue:** [krill-oss#145](https://github.com/Sautner-Studio-LLC/krill-oss/issues/145)
**Root cause category:** Inconsistent error semantics within the same class
**Module:** `module:krill-sdk`

## What happened

A nightly architectural scan flagged divergent error-propagation patterns across `NodeHttp`:

- `readHealth()` and `readNode()` return `null` on HTTP error or exception — the null is unambiguous: it means "could not fetch."
- `readNodes()` returned `emptyList()` on the same conditions — the empty list is ambiguous: callers cannot tell whether the server has no nodes or whether the request failed. A `krill-mcp` agent asking "how many nodes exist?" would silently report zero on a temporary network blip.

The scan also flagged a dead `try/catch` in `DigitalState.toDouble()`: the `when` expression covers every branch of a non-null enum and cannot throw at runtime. Unknown-ordinal errors happen at deserialization time, not inside the `when`. The defensive comment misleadingly suggested the catch was load-bearing.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeHttp.kt`:
  - Changed `readNodes()` return type from `List<Node>` to `List<Node>?`.
  - Returns `null` on HTTP non-OK and on exception (was `emptyList()`).
  - Promoted HTTP-error log from `logger.w` to `logger.e` (consistent with `readHealth` / `readNode`).
  - Added KDoc describing the null vs. empty-list contract and the `?: emptyList()` migration path.
- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeState.kt`:
  - Removed dead `try/catch` from `DigitalState.toDouble()`. Simplified to a single-expression `when`.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/node/NodeHttpErrorHandlingTest.kt`:
  - Three new tests: `readNodes` returns null on HTTP error, null on exception, and empty list on `200 OK` with no nodes.
- `krill-sdk/build.gradle.kts`: bumped version `0.0.48` → `0.0.49`.

## Prevention

- **`readNodes()` returning a nullable type is now the stable contract.** Callers that want the old "treat failure as empty" behavior should do `readNodes(host) ?: emptyList()` explicitly — that makes the fallback visible at the call site rather than hidden inside the method.
- **When all read methods in a class return `T?` on failure, new methods should follow the same convention.** The confusion arose because `readNodes` used `List<T>` (non-nullable) while its sibling methods used `T?`. Any new `NodeHttp` read method returning a collection should return `List<T>?` (not `List<T>`).
- **Exhaustive `when` over a non-null enum never throws — don't wrap it in try/catch.** The only place to guard against unknown ordinals is in the deserialization layer (e.g., a custom `@Serializer`), not after the value is already materialized.
- **Cross-repo note:** Four callers in `krill` use `readNodes()` and must be updated to handle the nullable return. Tracked in `krill#485` (filed separately).
