package krill.zone.mcp.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * An MCP tool. `inputSchema` is a JSON Schema object describing the `arguments`
 * a client is expected to supply to `tools/call`.
 *
 * `execute` returns a JSON tree that will be wrapped as a text content block.
 */
interface Tool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    suspend fun execute(arguments: JsonObject): JsonElement
}
