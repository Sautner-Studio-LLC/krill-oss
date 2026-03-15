package krill.zone.shared.io

import co.touchlab.kermit.*
import kotlinx.browser.*
import krill.zone.shared.*
import krill.zone.shared.node.*

/**
 * Browser-based FileOperations using localStorage for WASM platform.
 * Stores nodes as JSON strings in the browser's localStorage.
 */
class BrowserFileOperations : FileOperations {
    private val logger = Logger.withTag("FileOperations.wasm")
    private val storage = window.localStorage
    private val storagePrefix = "krill_node_"

    override fun read(id: String): Node? {
        val key = storagePrefix + id
        val json = storage.getItem(key) ?: return null
        return try {
            fastJson.decodeFromString<Node>(json)
        } catch (ex: Exception) {
            logger.e(ex) { "Failed to decode node $id" }
            null
        }
    }

    override fun update(node: Node) {
        val key = storagePrefix + node.id
        val json = fastJson.encodeToString(node )
        storage.setItem(key, json)
    }

    override fun delete(id: String) {
        val key = storagePrefix + id
        storage.removeItem(key)
    }

    override fun load(): List<Node> {
        val list = mutableListOf<Node>()
        val storageLength = storage.length

        for (i in 0 until storageLength) {
            val key = storage.key(i) ?: continue
            if (!key.startsWith(storagePrefix)) continue

            val json = storage.getItem(key) ?: continue
            try {
                val node = fastJson.decodeFromString<Node>(json)
                list.add(node )
            } catch (ex: Exception) {
                logger.e(ex) { "Failed to decode node from key $key" }
            }
        }

        return list
    }
}