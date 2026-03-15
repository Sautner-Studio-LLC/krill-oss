package krill.zone.shared.node.persistence

import co.touchlab.kermit.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import org.koin.ext.*
import java.io.*

private val NODE_STORE = if (SystemInfo.isServer()) {
    "/srv/krill/data/nodes"
} else {
    "${System.getProperty("user.home")}/.krill/data/nodes"
}

/**
 * File-based implementation of NodePersistence for desktop clients
 * Server uses database-based implementation instead
 */
class FileNodePersistence : NodePersistence {
    private val logger = Logger.withTag(this::class.getFullName())

    init {
        ensureStoreDir()
    }

    private fun ensureStoreDir() {
        val dir = File(NODE_STORE)
        if (!dir.exists()) dir.mkdirs()
    }

    override fun read(id: String): Node? {
        val file = File(NODE_STORE, id)
        try {
            if (!file.exists()) return null
            return fastJson.decodeFromString<Node>(file.readText())
        } catch (ex: Exception) {
            val quarantine = File(NODE_STORE, "QUARENTINE")
            if (!quarantine.exists()) {
                quarantine.mkdirs()
            }
            try {
                file.renameTo(File(quarantine, file.name))
            } catch (_: Exception) {
            }
            logger.e("FileNodePersistence Error reading node $id: ${ex.message}", ex)
            return null
        }
    }

    override fun save(node: Node) {
        val file = File(NODE_STORE, node.id)
        if (file.exists()) {
            file.delete()
        }
        file.writeText(fastJson.encodeToString(node))
    }

    override fun delete(id: String) {
        try {
            val file = File(NODE_STORE, id)
            if (file.exists()) {
                file.delete()
            }
        } finally {
        }
    }

    override fun loadByType(type: KrillApp): List<Node> {
         return loadAll().filter { it.type == type }
    }

    override fun children(node: Node): List<Node> {
        return loadAll().filter { it.parent == node.id }
    }

    override fun loadAll(): List<Node> {
        val files = File(NODE_STORE).listFiles() ?: return emptyList()
        val list: MutableList<Node> = mutableListOf()
        for (file in files) {
            if (!file.isFile) continue // skip directories like QUARENTINE
            val id = file.name

            try {
                read(id)?.let { list.add(it) }
            } finally {
            }
        }
        return list
    }
}
