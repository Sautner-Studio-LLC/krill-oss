/**
 * Server-side half of the split processor contract introduced by
 * `separate-crud-from-invocation`.
 *
 * A [ServerNodeProcessor] is registered (via Koin on the server JVM) for
 * every [krill.zone.shared.KrillApp] subtype that has server-side execution
 * logic. The server looks up the processor via `KrillApp.processor()` and
 * calls [onInvoke] at the one deliberate invocation seam —
 * `ServerNodeManager.invoke(target, by, verb)`.
 *
 * ## Why the split?
 *
 * The old `NodeProcessor` conflated two unrelated concerns:
 *  - `post(node)` — a client-side observe-and-dispatch signal driven by the
 *    [krill.zone.shared.node.NodeObserver] state-flow loop.
 *  - `process(node)` / `onSourceTrigger(...)` — the server-side execution
 *    logic, now unified under [onInvoke].
 *
 * By separating them the server can evolve the invocation contract (verb,
 * identity, cascade rules) without touching client targets and vice versa.
 *
 * ## Local dispatch rule
 *
 * [onInvoke] is the **only** server-side invocation entry point. Do NOT use
 * [krill.zone.shared.events.SourceTriggerPayload] for local dispatch —
 * that type is reserved for cross-server SSE transport only.
 *
 * @see ClientNodeProcessor
 */
package krill.zone.shared.node

/**
 * Server-side processor contract: receives a deliberate invocation and runs
 * the node's execution logic.
 */
interface ServerNodeProcessor {

    /**
     * Invoked by `ServerNodeManager.invoke` when a deliberate action targets
     * this node.
     *
     * Default behaviour:
     *  - [NodeAction.EXECUTE] → delegates to [process]. No state-stamp wake
     *    (`post(node.copy(state = EXECUTED))`) is implied; the server's
     *    `executeSources` cascade (if any) is the caller's responsibility.
     *  - [NodeAction.RESET] → explicit no-op. RESET is terminal at the
     *    receiver; a generic node has no reset semantics and the rule forbids
     *    silently falling back to EXECUTE for a verb the processor does not
     *    handle.
     *
     * Processors with per-source or per-verb logic (Trigger family, TaskList,
     * DataPoint, Calculation) override this method entirely; the override fully
     * replaces the default so there is no double-run.
     *
     * @param node   The target node as it stands at invocation time.
     * @param by     Identity of the node (or actor) that triggered this call —
     *               always [NodeIdentity] (`nodeId` + `hostId`), never a bare
     *               string.
     * @param verb   The action being applied — [NodeAction.EXECUTE] or
     *               [NodeAction.RESET].
     */
    suspend fun onInvoke(node: Node, by: NodeIdentity, verb: NodeAction) {
        when (verb) {
            NodeAction.EXECUTE -> process(node)
            NodeAction.RESET -> { /* no reset semantics for a generic node */ }
        }
    }

    /**
     * Executes the node's primary work. Called by the default [onInvoke]
     * implementation on [NodeAction.EXECUTE].
     *
     * @return `true` if the node should cascade to its configured targets
     *         after this call completes; `false` to suppress the cascade.
     */
    suspend fun process(node: Node): Boolean
}
