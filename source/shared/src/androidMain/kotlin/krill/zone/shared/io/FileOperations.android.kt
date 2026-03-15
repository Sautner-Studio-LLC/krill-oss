package krill.zone.shared.io


import android.content.*
import co.touchlab.kermit.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import org.koin.ext.*


class AndroidFileOperations : FileOperations {
    private val logger = Logger.withTag(this::class.getFullName())

    // Use lazy initialization to ensure Context is available from Koin
    private val store: SharedPreferences by lazy {
        ContextContainer.context.getSharedPreferences("krill_nodes_v3", Context.MODE_PRIVATE)
    }


    override fun read(id: String): Node? {
        try {
            val json = store.getString(id, null) ?: return null
            return fastJson.decodeFromString<Node>(json)
        } catch (e: Exception) {
            logger.e(e) { "Failed to read node with id $id " }
            return null
        }
    }

    override fun update(node: Node) {
        val success = store.edit().putString(node.id, fastJson.encodeToString(node )).commit()
        if (!success) {
            logger.e { "Failed to commit update for node ${node.id}" }
        }
    }

    override fun delete(id: String) {
        val success = store.edit().remove(id).commit()
        if (!success) {
            logger.e { "Failed to commit delete for node $id" }
        }
    }

    override fun load(): List<Node> {
        val list = mutableListOf<Node>()
        store.all.values.forEach {
            val json = it as String
            try {
                val n = fastJson.decodeFromString<Node>(json)
                list.add(n)
            } catch (ex: Exception) {
                logger.e("bad json in store", ex)
            }
        }
        return list

    }

}
