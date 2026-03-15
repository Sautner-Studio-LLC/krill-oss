package krill.zone.shared.node

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import org.koin.ext.*


interface NodeObserver {
    fun observe(node: MutableStateFlow<Node>)
    fun remove(id: String)
    fun close()
}

class DefaultNodeObserver( private val scope: CoroutineScope) : NodeObserver {
    private val logger = Logger.withTag(this::class.getFullName())

    val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    override fun observe(node: MutableStateFlow<Node>) {

        scope.launch {
            mutex.withLock {
                if (!jobs.containsKey(node.value.id)) {
                    logger.d("Observing ${node.details()}")

                    val collector = FlowCollector<Node> { collectedNode ->
                        logger.d("collected node  ${collectedNode.details()} $collectedNode")
                            try {
                                collectedNode.type.emit(node.value)
                            } catch (e: Exception) {
                                logger.e(" ${collectedNode.details()}: Error during emit", e)
                            }

                       }


                    jobs[node.value.id] = scope.launch {
                        try {

                            logger.d("observing node: ${node.details()} ${node.subscriptionCount.value}")

                            if (node.subscriptionCount.value > 1) {
                                logger.e("node has multuiple observers - probably a bug ${node.details()}")
                            } else {


                                node.collect(collector)
                            }
                        } finally {

                            logger.w("exited node observing job ${node.details()}")
                        }
                    }
                }
            }
        }
    }

    override fun remove(id: String) {
        scope.launch {
            mutex.withLock {
                jobs[id]?.cancel()
                jobs.remove(id)
            }
        }

    }


    override fun close() {
        scope.launch {
            mutex.withLock {
                jobs.values.forEach { it.cancel() }
                jobs.clear()
            }
        }

    }


}
