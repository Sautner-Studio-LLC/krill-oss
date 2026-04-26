/**
 * The set of boolean logic operations implemented by Krill's `LogicGate`
 * executor. Each value is the standard truth-table function of that name —
 * the executor's runtime evaluates the chosen function over the configured
 * sources and writes the result to its targets.
 */
package krill.zone.shared.krillapp.executor.logicgate

/** Boolean logic operations available to a `LogicGate` executor node. */
enum class LogicGate {
    AND, OR, BUFFER, NOT, NAND, NOR, XOR, XNOR, IMPLY, NIMPLY
}
