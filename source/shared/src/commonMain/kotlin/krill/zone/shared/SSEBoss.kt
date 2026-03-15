package krill.zone.shared

import co.touchlab.kermit.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*

class SSEBoss(private val nodeManager: ClientNodeManager, private val scope: CoroutineScope) {

    private val logger = Logger.withTag(this::class.getFullName())
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    fun isConnected(id: String) : Boolean {
        return jobs.containsKey(id)
    }

    fun connect(node: Node) {

        if (jobs.contains(node.id)) { return }
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.e(exception) { "${node.details()}: SSEBoss uncaught exception" }
            nodeManager.alarm(node)
        }
        scope.launch(handler) {

            try {
            mutex.withLock {
                val meta = node.meta as ServerMetaData
                logger.i { "${node.details()} establishing sse client" }
                val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {"localhost" } else meta.name
                val sseUrl = URLBuilder(
                    protocol = URLProtocol.HTTPS,
                    host = name,
                    port = meta.port,
                    pathSegments = listOf("sse")
                ).build()
                val apiKey = meta.apiKey
                val job = scope.launch(handler) {
                    try {
                        httpClient.sse(
                            urlString = sseUrl.toString(),
                            request = {
                                header(HttpHeaders.Authorization, "Bearer $apiKey")
                            },
                            deserialize = { _, json ->
                                fastJson.decodeFromString<Node>(json)
                            }

                        ) {
                            while (currentCoroutineContext().isActive) {
                                incoming.collect { incoming ->
                                    deserialize<Node>(incoming.data)?.let { n ->
                                        logger.d{ "${n.details()} SSE $n" }

                                        //we recieved a node we didn't know about before, add it
                                        if (! nodeManager.nodeAvailable(n.id)) {
                                            nodeManager.update(n)
                                        } else {

                                            nodeManager.readNodeStateOrNull(n.id).value?.let { node ->
                                                when (n.state) {

                                                    NodeState.CREATED, NodeState.USER_EDIT, NodeState.USER_SUBMIT -> {

                                                        when (n.type) {
                                                            is KrillApp.DataPoint -> {

                                                            }
                                                            is KrillApp.Server.Pin -> {


                                                            }
                                                            else -> {
                                                                nodeManager.update(
                                                                    node.copy(
                                                                        state = NodeState.NONE,

                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                    NodeState.SNAPSHOT_UPDATE -> {

                                                        val incoming = n.meta as DataPointMetaData
                                                       // nodeManager.updateSnapshot(node, incoming.snapshot)

                                                    }
                                                    NodeState.ERROR -> {
                                                        if (node.state != NodeState.ERROR && node.meta.error != n.meta.error) {
                                                            val meta = updateMetaWithError(n.meta, n.meta.error)
                                                            nodeManager.update(node.copy(state = NodeState.ERROR, meta = meta))
                                                        }
                                                    }



                                                    NodeState.DELETING -> {
                                                        nodeManager.remove(n.id)
                                                    }

                                                    else -> {
                                                        //don't overwrite a node the user is editing
                                                        //TODO update snapshots with incoming data if the user is not activly doing a manual entry
                                                        if (nodeManager.selectedNodeId.value != node.id) {
                                                           // nodeManager.update(n)
                                                        }


                                                    }
                                                }

                                            }
                                        }



                                    }
                                }
                            }

                        }
                    } catch (e: Exception) {
                        logger.e(e) { "${node.details()}: SSEBoss connecting failed" }
                        nodeManager.alarm(node)
                    }
                    finally {
                        logger.w { "${node.details()}: SSEBoss Entered Finally" }
                        jobs[node.id]?.cancel()
                        jobs.remove(node.id)
                    }
                }

                job.invokeOnCompletion {
                    logger.i { "${node.details()}: completing sse job"}
                    jobs.remove(node.id)
                }
                jobs[node.id] = job


            }
            } catch (e: Exception) {
                logger.e(e) { "${node.details()}: SSEBoss outer launch failed" }
                nodeManager.alarm(node)
            }
        }


    }
}