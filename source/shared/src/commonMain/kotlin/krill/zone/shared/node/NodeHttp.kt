package krill.zone.shared.node

import co.touchlab.kermit.*
import io.ktor.client.*
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

class NodeHttp(private val trustHost: TrustHost) {
    private val logger = Logger.withTag(this::class.getFullName())

    @OptIn(ExperimentalUuidApi::class)
    suspend fun readHealth(host: Node): Node? {
        try {
            logger.i("${host.details()}: read health")
            val meta = host.meta as ServerMetaData
            val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name
            val url = "https://$name:${meta.port}/health"
            val apiKey = meta.apiKey

            val response = httpClient.get(url) {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                return response.body<Node>().copy(state = NodeState.NONE)

            } else {
                logger.e("error calling $url ${response.status} ${response.status.description}")
                return null
            }

        } catch (e: Exception) {
            logger.e(e) { "${host.details()}: Exception while reading health endpoint" }
            return null

        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun chart(host: Node, id: String, startTime: Long, endTime : Long): ByteArray {
        logger.i("${host.details()}: read chart")

            val meta = host.meta as ServerMetaData
            val apiKey = getApiKey(host)

            val client: HttpClient = httpClient
            val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name

            val url = "https://${name}:${meta.port}/node/$id/data/plot"
            val response = client.get(url) {
                contentType(ContentType.Application.Xml)
                header("Authorization", "Bearer $apiKey")
            }

            return if (response.status.isSuccess()) {
                val channel: ByteReadChannel = response.body()
                channel.readRemaining().readByteArray()
            } else {
                logger.e { "cannot connect to trusted url: ${response.status.description}" }
                byteArrayOf()
            }


        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun readNode(host: Node, id: String): Node? {
            logger.i("${host.details()}: read node")
            try {

                val meta = host.meta as ServerMetaData
                val apiKey = getApiKey(host)

                val client: HttpClient = httpClient
                val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name

                val url = "https://${name}:${meta.port}/node/${id}"
                val response = client.get(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                }
                return if (response.status == HttpStatusCode.OK) {
                    val r = response.body<Node>()
                    if (r.state == NodeState.EXECUTED) {
                        r.copy(state = NodeState.NONE)
                    } else {
                        r
                    }
                } else {
                    logger.e { "${response.status}: error $url ${host.details()} " }
                    null
                }

            } catch (e: Exception) {
                logger.e("Error getting node ${host.details()} ", e)
                return null
            }
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun readNodes(host: Node): List<Node> {
            logger.i("${host.details()}: read nodes")
            try {

                val meta = host.meta as ServerMetaData
                val apiKey = getApiKey(host)


                val client: HttpClient = httpClient
                val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name

                val url = "https://${name}:${meta.port}/nodes"
                val response = client.get(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                }
                return if (response.status == HttpStatusCode.OK) {
                    response.body<List<Node>>().map {
                        if (it.state == NodeState.EXECUTED) {
                            it.copy(state = NodeState.NONE)
                        } else {
                            it
                        }
                    }
                } else {
                    logger.w { "Failed to read node ${response.status}: $url ${host.details()} " }
                    emptyList()
                }

            } catch (e: Exception) {
                logger.e("Error getting nodes ${host.details()} ", e)
                return emptyList()
            }
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun postNode(host: Node, node: Node) {
            logger.i("${host.details()}: post node")
            if (node.type == KrillApp.Client) return


            val meta = host.meta as ServerMetaData
            val apiKey = getApiKey(host)

            val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name

            val url = "https://${name}:${meta.port}/node/${node.id}"

            logger.i("posting to node: $url ${node.details()} $node")
            val client: HttpClient = httpClient


            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(node)

                }
                if (response.status.isSuccess()) {
                    return
                } else {
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

        private fun getApiKey(host: Node): String {

            val meta = host.meta as ServerMetaData //maybe we're in a delete flow
            if (meta.apiKey.isNotEmpty()) {
                return meta.apiKey
            } else {
                throw Exception("The node ${host.details()} is not available and/or api key is missing")
            }


        }

        suspend fun deleteNode(host: Node, node: Node) {
            if (node.type == KrillApp.Client) return
            val meta = host.meta as ServerMetaData
            val apiKey = getApiKey(host)
            val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name


            val url = "https://${name}:${meta.port}/node/${node.id}"

            logger.i("${node.details()}: deleting $url ")
            val client: HttpClient = httpClient


            try {
                val response = client.delete(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(node)

                }
                if (response.status.isSuccess()) {
                    return
                } else {
                    logger.e("${host.details()} error deleting node ${node.details()}: ${response.status.value}")
                    throw Exception("Failed to delete node ${node.host}: ${response.status.value}")
                }
            } catch (e: Exception) {
                throw e
            }


        }


    }

