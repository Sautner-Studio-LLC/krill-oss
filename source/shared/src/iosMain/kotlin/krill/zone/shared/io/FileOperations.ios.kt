package krill.zone.shared.io

import co.touchlab.kermit.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import platform.Foundation.*


class IosFileOperations : FileOperations {
    private val logger = Logger.withTag("FileOperations.ios")
    private val defaults = NSUserDefaults(suiteName = "krill_nodes_v3")

    override fun read(id: String): Node? {
        val json = defaults.stringForKey("$PREFIX$id") ?: return null
        return try {
            fastJson.decodeFromString<Node>(json)
        } catch (ex: Exception) {
            logger.e("bad json for node $id", ex)
            null
        }
    }

    override fun update(node: Node) {
        defaults.setObject(fastJson.encodeToString(node), forKey = "$PREFIX${node.id}")
    }

    override fun delete(id: String) {
        defaults.removeObjectForKey("$PREFIX$id")
    }

    override fun load(): List<Node> {
        val list = mutableListOf<Node>()
        val keys = defaults.dictionaryRepresentation().keys
        for (key in keys) {
            val keyString = key as? String ?: continue
            if (!keyString.startsWith(PREFIX)) continue
            val json = defaults.stringForKey(keyString) ?: continue
            try {
                val node = fastJson.decodeFromString<Node>(json)
                list.add(node.copy(state = NodeState.NONE))
            } catch (ex: Exception) {
                logger.e("bad json in store for key $keyString", ex)
            }
        }
        return list
    }

    companion object {
        private const val PREFIX = "krill_node_"
    }
}