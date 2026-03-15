package krill.zone.server

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.node.*
import org.koin.ext.*
import kotlin.uuid.*

class ServerServerProcessor(
    private val beaconSupervisor: BeaconSupervisor,
    private val serverConnector: ServerConnector,
    private val scope: CoroutineScope
) : ServerProcessor {

    private val logger = Logger.withTag(this::class.getFullName())

    @OptIn(ExperimentalUuidApi::class)
    override fun post(node: Node) {

        logger.d("${node.details()}: server posted ")


            scope.launch {
                when (node.state) {
                    NodeState.EXECUTED -> {

                            process(node)

                    }


                    else -> {}
                }
            }

    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun process(node: Node): Boolean {
        if (! node.isMine()) {
            scope.launch {
                serverConnector.connectNode(node)
            }
        }   else {
            beaconSupervisor.startBeaconProcess()
        }

        return true
    }


}
