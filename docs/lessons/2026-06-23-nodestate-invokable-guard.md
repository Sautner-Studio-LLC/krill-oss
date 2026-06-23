# NodeState invokable guard — client fires against PAUSED / DELETING nodes

**Issue:** [krill-oss#158](https://github.com/Sautner-Studio-LLC/krill-oss/issues/158)
**Root cause category:** Missing client-side state gate
**Module:** `krill-sdk`

## What happened

`NodeHttp.invokeNode()` had only one pre-flight guard: `if (target.type == KrillApp.Client) return`.
The `NodeState` documentation explicitly stated that `PAUSED` "suppresses
execution" and that `DELETING` nodes are mid-removal, but no code enforced
those semantics at the SDK boundary. Any SDK consumer — the Compose UI, the
MCP server, a lambda — could send a live `/invoke` HTTP request against a
PAUSED or DELETING node and the SDK would forward it without complaint.

The same code had `NodeState` without a `@Serializable` annotation while the
sibling `DigitalState` carried one; the behaviour was identical (the Kotlin
serialization plugin infers it for enums) but inconsistent and implicit.

The nightly architectural scan (Kraken, finding `a918d608124f`) flagged the
absence of enforced transition semantics. Investigation confirmed the scan
was correct on the invokable-guard gap and refuted its hypothesis about
processor interfaces — the `ClientNodeProcessor` / `ServerNodeProcessor`
split is intentional and correct; adding a shared interface would be wrong.

## Fix

1. `NodeState.kt`: Added `@Serializable` to make serialization explicit and
   consistent with `DigitalState`.
2. `NodeState.kt`: Added `fun NodeState.isInvokable(): Boolean` — returns
   `false` for `PAUSED` and `DELETING`, `true` for all other states. KDoc
   explains the rationale for each blocked state and the deliberate choice
   not to block error or transient states.
3. `NodeHttp.kt`: Applied the guard in `invokeNode()` immediately after the
   Client-type check: `if (!target.state.isInvokable()) return`.
4. `NodeStateTest.kt`: Tests covering every blocked state, a sample of
   allowed states, and the full-enum sweep.
5. `NodeHttpErrorHandlingTest.kt`: Three tests confirming the guard fires
   for PAUSED and DELETING and does NOT fire for NONE.

## Prevention

- When documenting that a state "suppresses" or "prevents" something,
  add the enforcement at the same time — doc-only invariants drift.
- Client-side guards complement server-side enforcement; a cheap early
  return in the SDK saves a round-trip and surfaces programmer errors
  during development.
- When refuting a nightly scan hypothesis, record the refutation explicitly
  (what was wrong in the hypothesis, why, and what was actually fixed) so
  the scanner can update its model.
