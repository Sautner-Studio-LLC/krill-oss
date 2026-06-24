# Hard cast in `Node.https()` produces cryptic ClassCastException on metadata mismatch

**Issue:** [krill-oss#155](https://github.com/Sautner-Studio-LLC/krill-oss/issues/155)
**Root cause category:** Architectural lead — unsafe type coercion on an open hierarchy
**Module:** `module:krill-sdk`

## What happened

`NodeFunctions.https()` used a hard cast (`this.meta as ServerMetaData`) to obtain the server metadata before constructing the HTTPS URL. Since `NodeMetaData` is an open interface with many concrete subtypes, passing a non-server node (e.g., a `DataPoint`, `Pin`, or `LLMMetaData` node) throws a bare `ClassCastException` at the cast site with no context about which node triggered the failure. Diagnosing the root cause required correlating a stack trace with a node id — painful in a hot path like per-request URL resolution.

The same codebase already applied the safe-cast pattern (`as? DataPointMetaData ?: return 0xFF000000L`) correctly in `snapshotColorArgb()` on the very same file, confirming that the team knows the right idiom — `https()` was simply an oversight.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeFunctions.kt`: replaced `this.meta as ServerMetaData` with `this.meta as? ServerMetaData ?: throw IllegalArgumentException(…)`. The error message includes the expected type (`ServerMetaData`), the actual runtime type (`meta::class.simpleName`), and the node's `id` and `type`, giving an immediately actionable diagnostic without a debugger.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/node/NodeFunctionsTest.kt`: added three tests — correct URL for a local (`.local`-suffixed) server node, correct URL for an FQDN server node, and `IllegalArgumentException` with the expected message fields when called on a non-server node.
- `krill-sdk/build.gradle.kts`: bumped version `0.0.50` → `0.0.51`.

## Prevention

- **Replace every `as ConcreteMetaData` in `NodeFunctions.kt` with `as? ConcreteMetaData ?: throw IllegalArgumentException(…)`** — hard casts on `NodeMetaData` subtypes anywhere in SDK code are high-risk because the hierarchy is open and callers may not enforce subtype discipline. The safe cast + descriptive throw is the correct pattern.
- **When `snapshotColorArgb()` uses `as?` and a sibling function uses a hard cast, that inconsistency is a red flag** — audit functions in the same file for cast parity at review time.
- **Document the precondition, not just the caller's obligation.** Saying "caller must guarantee `meta` is `ServerMetaData`" in a KDoc comment does not catch mistakes at compile time or runtime. A runtime guard with a descriptive message is cheap insurance.
