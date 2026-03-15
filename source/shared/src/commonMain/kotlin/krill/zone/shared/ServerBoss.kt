package krill.zone.shared

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.krillapp.server.*
import org.koin.core.component.*
import org.koin.ext.*

class ServerBoss(private val scope: CoroutineScope) : KoinComponent {
    private var serverJob: Job? = null
    private val logger = Logger.withTag(this::class.getFullName())

    private val tasks = mutableListOf<ServerTask>()

    private val mutex = Mutex()

    suspend fun addTask(task: ServerTask) {
        mutex.withLock {
            tasks.add(task)
        }

    }

    suspend fun start() {

        mutex.withLock {

            if (serverJob == null) {
                serverJob = scope.launch {
                    try {
                        logger.d("Server Boss is awake and ready")
                        tasks.forEach { task ->
                            logger.i("Server Boss task started: $task")
                            scope.launch { task.start() }

                        }
                        awaitCancellation()
                    } catch (t: Throwable) {
                        logger.e("Server Boss got an exception", t)
                    } finally {
                        logger.e("Server Boss Job exited")
                        serverJob?.cancel()
                        serverJob = null
                    }

                }
            }
        }
    }


}