/**
 * Client-side half of the split processor contract introduced by
 * `separate-crud-from-invocation`.
 *
 * A [ClientNodeProcessor] is registered (via Koin on Compose Multiplatform
 * targets) for every [krill.zone.shared.KrillApp] subtype whose state changes
 * need to be reflected in the UI. The client's [krill.zone.shared.node.NodeObserver]
 * loop calls [post] for every value the node's `MutableStateFlow` emits.
 *
 * ## Scope
 *
 * Client-only — the server has no [post] analogue. All server-side invocations
 * go through [ServerNodeProcessor.onInvoke]. The companion `KrillApp.emit(node)`
 * extension therefore resolves to [ClientNodeProcessor.post] on client targets
 * and is absent on the server JVM.
 *
 * @see ServerNodeProcessor
 */
package krill.zone.shared.node

/**
 * Client-side processor contract: receives a node value emitted by the local
 * state-flow observer and dispatches it to the UI layer.
 */
interface ClientNodeProcessor {

    /**
     * Called by `NodeObserver` whenever [node]'s [MutableStateFlow] emits a
     * new value. Implementations typically update Compose state, drive
     * animations, or forward the value to a ViewModel.
     *
     * This method is **not** a server invocation — it is a pure observe-and-
     * dispatch signal. For deliberate execution, see [ServerNodeProcessor.onInvoke].
     *
     * @param node The updated node snapshot emitted by the state flow.
     */
    fun post(node: Node)
}
