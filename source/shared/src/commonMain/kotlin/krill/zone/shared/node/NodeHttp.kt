package krill.zone.shared.node

import co.touchlab.kermit.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.io.http.*
import krill.zone.shared.krillapp.server.*
import org.koin.ext.*
import kotlin.uuid.*

class NodeHttp(private val trustHost: TrustHost, private val bearerTokenProvider: () -> String?) {
    private val logger = Logger.withTag(this::class.getFullName())

    // ==================== Helpers ====================

    /** Resolves the base URL for a server, handling WASM localhost override. */
    private fun baseUrl(meta: ServerMetaData): String {
        val host = if (SystemInfo.wasmPort > 0) SystemInfo.wasmHost else meta.resolvedHost()
        return "https://$host:${meta.port}"
    }

    /** Applies bearer token authorization to the request if available. */
    private fun HttpRequestBuilder.withAuth() {
        bearerTokenProvider()?.let { token ->
            logger.w { "!!!!!!  Bearer $token" }
            header("Authorization", "Bearer $token")
        }
    }

    // ==================== Node CRUD ====================

    @OptIn(ExperimentalUuidApi::class)
    suspend fun readHealth(host: Node): Node? {
        try {
            logger.i("${host.details()}: read health")
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/health"

            val response = httpClient.get(url) { withAuth() }
            return if (response.status == HttpStatusCode.OK) {
                response.body<Node>().copy(state = NodeState.NONE)
            } else {
                logger.e("error calling $url ${response.status} ${response.status.description}")
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "${host.details()}: Exception while reading health endpoint" }
            return null
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun readNode(host: Node, id: String): Node? {
        logger.i("${host.details()}: read node")
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/node/$id"

            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
                withAuth()
            }
            return if (response.status == HttpStatusCode.OK) {
                val r = response.body<Node>()
                if (r.state == NodeState.EXECUTED) r.copy(state = NodeState.NONE) else r
            } else {
                logger.e { "${response.status}: error $url ${host.details()}" }
                null
            }
        } catch (e: Exception) {
            logger.e("Error getting node ${host.details()}", e)
            return null
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun readNodes(host: Node): List<Node> {
        logger.i("${host.details()}: read nodes")
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/nodes"

            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
                withAuth()
            }
            return if (response.status == HttpStatusCode.OK) {
                response.body<List<Node>>().map {
                    if (it.state == NodeState.EXECUTED) it.copy(state = NodeState.NONE) else it
                }
            } else {
                logger.w { "Failed to read nodes ${response.status}: $url ${host.details()}" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.e("Error getting nodes ${host.details()}", e)
            return emptyList()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun postNode(host: Node, node: Node) {
        logger.i("${host.details()}: post node")
        if (node.type == KrillApp.Client) return

        val meta = host.meta as ServerMetaData
        val url = "${baseUrl(meta)}/node/${node.id}"

        try {
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                withAuth()
                setBody(node)
            }
            if (!response.status.isSuccess()) {
                logger.e("error posting node ${node.host}: ${response.status.value}")
                throw Exception("Failed to save node ${node.host}: ${response.status.value}")
            }
        } catch (e: Exception) {
            if (e.isSSLError()) {
                trustHost.deleteCert(host)
            }
            throw e
        }
    }

    suspend fun deleteNode(host: Node, node: Node) {
        if (node.type == KrillApp.Client) return
        val meta = host.meta as ServerMetaData
        val url = "${baseUrl(meta)}/node/${node.id}"

        logger.i("${node.details()}: deleting $url")
        try {
            val response = httpClient.delete(url) {
                contentType(ContentType.Application.Json)
                withAuth()
                setBody(node)
            }
            if (!response.status.isSuccess()) {
                logger.e("${host.details()} error deleting node ${node.details()}: ${response.status.value}")
                throw Exception("Failed to delete node ${node.host}: ${response.status.value}")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    // ==================== Data ====================

    @OptIn(ExperimentalUuidApi::class)
    suspend fun chart(host: Node, id: String, startTime: Long, endTime: Long): ByteArray {
        logger.i("${host.details()}: read chart")
        val meta = host.meta as ServerMetaData
        val url = "${baseUrl(meta)}/node/$id/data/plot"

        val response = httpClient.get(url) {
            contentType(ContentType.Application.Xml)
            withAuth()
        }
        return if (response.status.isSuccess()) {
            val channel: ByteReadChannel = response.body()
            channel.readRemaining().readByteArray()
        } else {
            logger.e { "cannot connect to trusted url: ${response.status.description}" }
            byteArrayOf()
        }
    }

    /**
     * Fetches data series (snapshots) for a data point within a time range.
     * Used by GraphScreen to load and poll chart data.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun getDataSeries(host: Node, sourceId: String, startTime: Long, endTime: Long): List<krill.zone.shared.krillapp.datapoint.Snapshot> {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/node/$sourceId/data/series?st=$startTime&et=$endTime"

            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
                withAuth()
            }
            return if (response.status.isSuccess()) {
                response.body()
            } else {
                logger.w { "Failed to get data series ${response.status}: $url" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching data series for $sourceId" }
            return emptyList()
        }
    }

    // ==================== Server Management ====================

    /**
     * Fetches available GPIO pin headers from a server.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun getGpioHeader(host: Node): List<krill.zone.shared.krillapp.server.pin.PinMetaData> {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/header"

            val response = httpClient.get(url) { withAuth() }
            return if (response.status.isSuccess()) {
                response.body()
            } else {
                logger.w { "Failed to get GPIO header ${response.status}" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching GPIO header" }
            return emptyList()
        }
    }

    /**
     * Fetches a JPEG snapshot from a camera node.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun getCameraSnapshot(host: Node, cameraId: String): ByteArray? {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/camera/$cameraId/snapshot"

            val response = httpClient.get(url) { withAuth() }
            return if (response.status.isSuccess()) {
                response.body<ByteArray>()
            } else {
                logger.w { "Camera snapshot failed: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching camera snapshot" }
            return null
        }
    }

    /**
     * Lists saved camera snapshot filenames (newest first).
     */
    suspend fun getCameraThumbnails(host: Node, cameraId: String): List<String> {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/camera/$cameraId/thumbnails"
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
                withAuth()
            }
            return if (response.status.isSuccess()) {
                response.body()
            } else emptyList()
        } catch (e: Exception) {
            logger.e(e) { "Error fetching camera thumbnails" }
            return emptyList()
        }
    }

    /**
     * Fetches a saved camera thumbnail as bytes via the trusted httpClient.
     */
    suspend fun getCameraThumbnailBytes(host: Node, cameraId: String, filename: String): ByteArray? {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/camera/$cameraId/thumbnails/$filename"
            val response = httpClient.get(url) { withAuth() }
            return if (response.status.isSuccess()) response.body<ByteArray>() else null
        } catch (e: Exception) {
            logger.e(e) { "Error fetching thumbnail $filename" }
            return null
        }
    }

    /**
     * Returns the URL for a saved camera thumbnail.
     */
    fun getCameraThumbnailUrl(host: Node, cameraId: String, filename: String): String {
        val meta = host.meta as ServerMetaData
        return "${baseUrl(meta)}/camera/$cameraId/thumbnails/$filename"
    }

    // ==================== Backup ====================

    /**
     * Lists backup archives from the server.
     */
    suspend fun getBackupList(host: Node): List<Map<String, Any?>> {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/backup/list"
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
                withAuth()
            }
            @Suppress("UNCHECKED_CAST")
            return if (response.status.isSuccess()) response.body() else emptyList()
        } catch (e: Exception) {
            logger.e(e) { "Error fetching backup list" }
            return emptyList()
        }
    }

    /**
     * Deletes a backup archive file.
     */
    suspend fun deleteBackupFile(host: Node, filename: String): Boolean {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/backup/file?file=${filename.encodeURLPath()}"
            val response = httpClient.delete(url) { withAuth() }
            return response.status.isSuccess()
        } catch (e: Exception) {
            logger.e(e) { "Error deleting backup file $filename" }
            return false
        }
    }

    /**
     * Initiates a restore from a backup archive.
     */
    suspend fun restoreBackup(host: Node, filename: String): String? {
        try {
            val meta = host.meta as ServerMetaData
            val url = "${baseUrl(meta)}/backup/restore"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                withAuth()
                setBody(mapOf("filename" to filename))
            }
            return if (response.status.isSuccess()) {
                val result = response.body<Map<String, String>>()
                result["message"]
            } else {
                logger.w { "Restore failed: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Error restoring backup $filename" }
            return null
        }
    }

    /**
     * Uploads an SVG diagram file to a project.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun uploadDiagram(host: Node, projectId: String, fileName: String, svgBytes: ByteArray): Boolean {
        try {
            val meta = host.meta as ServerMetaData
            val encodedFileName = fileName.encodeURLPath()
            val url = "${baseUrl(meta)}/project/$projectId/diagram/$encodedFileName"

            val response = httpClient.put(url) {
                withAuth()
                contentType(ContentType.Image.SVG)
                setBody(svgBytes)
            }
            return response.status.isSuccess()
        } catch (e: Exception) {
            logger.e(e) { "Error uploading diagram $fileName" }
            return false
        }
    }
}
