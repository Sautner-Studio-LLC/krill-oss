# SDK processor contract split: ServerNodeProcessor / ClientNodeProcessor

**Issue:** [krill-oss#102](https://github.com/Sautner-Studio-LLC/krill-oss/issues/102)
**Root cause category:** API design — conflated server and client concerns in a single interface
**Module:** `krill-sdk`

## What happened

`NodeProcessor` (in `krill/shared`) combined two unrelated dispatch paths in one
interface:

- `post(node)` — a client-side observe-and-paint signal driven by `NodeObserver`'s
  state-flow loop. Every Compose target implements this.
- `process(node)` / `onSourceTrigger(...)` — server-side execution logic that runs
  node work, decides cascade, and routes verb semantics.

Because both paths shared the same interface, `NodeObserver` calling `post()` on a
server also triggered server-side processing, creating a dual-wake hazard: a CRUD
write could inadvertently re-enter a processor that was only supposed to fire on
deliberate invocation. Conversely, refactoring the server invocation seam always had
to be careful not to break client targets that only cared about `post()`.

## Fix

- Added `ServerNodeProcessor` to `krill-sdk` (`krill.zone.shared.node`):
  - `suspend fun onInvoke(node, by: NodeIdentity, verb: NodeAction)` — the new single
    server-side entry point. Default: EXECUTE → `process(node)`; RESET → explicit no-op.
    No `post(node.copy(state = EXECUTED))` state-stamp wake.
  - `suspend fun process(node: Node): Boolean` — the primary work unit.
- Added `ClientNodeProcessor` to `krill-sdk` (`krill.zone.shared.node`):
  - `fun post(node: Node)` — the client-side observe-and-dispatch only.
- Updated `SourceTriggerPayload` KDoc: explicitly marked as cross-server SSE transport
  only; local dispatch SHALL NOT use it.
- Added `NodeProcessorContractTest`: verifies EXECUTE delegates, RESET is no-op, `by`
  carries full `NodeIdentity` (never collapsed to bare string), and `ClientNodeProcessor`
  shape is correct.
- Bumped `krill-sdk` to `0.0.33`; CI will publish to Maven Central.

## Prevention

- **Single-surface rule:** server execution logic and client observe-and-paint are
  separate concerns; they belong in separate interfaces. Any future `NodeProcessor`-
  shaped type should start with the question "is this server-side invocation or
  client-side observation?"
- **RESET is terminal at the receiver.** A generic node's default `onInvoke` must
  never fall through RESET to EXECUTE silently. The `when` arm must be exhaustive
  and RESET must be an explicit no-op (or an explicit override) — no `else` fallback.
- **`by` is always `NodeIdentity`, never `String`.** The `hostId` must survive every
  invocation hop so cross-server attribution is never lost. Enforce at the interface
  boundary, not by convention.
- **`SourceTriggerPayload` is cross-server SSE only.** Local dispatch through
  `ServerNodeManager.invoke` — not through `SourceTriggerPayload` — so the SSE type
  can evolve without touching local call sites.
