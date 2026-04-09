package krill.zone.app.startup

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.io.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import krill.zone.shared.security.*
import org.koin.compose.*
import kotlin.time.Clock

/**
 * Represents the current phase of app startup.
 * Derived reactively from FTUE flag, PIN state, swarm contents, and SSE connections.
 */
sealed class StartupPhase {
    data object Initializing : StartupPhase()
    data object AcceptingTerms : StartupPhase()
    data object EnteringPin : StartupPhase()
    data object DiscoveringServers : StartupPhase()
    data class ConnectingToServers(val servers: List<Node>, val timedOut: Boolean = false) : StartupPhase()
    data object Ready : StartupPhase()
}

/**
 * Computes the current [StartupPhase] from reactive state sources.
 *
 * @param showFtue whether the first-time user experience (TOS) is active
 * @param needsPin whether the user needs to enter a PIN
 * @param allNodes current nodes from the swarm
 * @param connectedServerIds set of server IDs with active SSE connections
 */
@Composable
fun rememberStartupPhase(
    showFtue: Boolean,
    needsPin: Boolean,
    allNodes: List<Node>,
    connectedServerIds: Set<String>
): State<StartupPhase> {
    val servers = remember(allNodes) { allNodes.filter { it.type is KrillApp.Server } }
    val hasConnectedServer = servers.any { it.id in connectedServerIds || it.state == NodeState.NONE || it.state == NodeState.INFO }
    val hasConnectingServer = servers.any { it.state == NodeState.PAIRING || it.state == NodeState.WARN || it.state == NodeState.EXECUTED }

    // Track timeout for connecting phase
    var connectingTimedOut by remember { mutableStateOf(false) }
    var connectingStartTime by remember { mutableStateOf(0L) }

    // Reset timeout tracking when we enter connecting state
    LaunchedEffect(hasConnectingServer, hasConnectedServer) {
        if (hasConnectingServer && !hasConnectedServer) {
            connectingStartTime = Clock.System.now().toEpochMilliseconds()
            connectingTimedOut = false
            delay(15_000)
            connectingTimedOut = true
        }
    }

    return remember(showFtue, needsPin, servers, hasConnectedServer, hasConnectingServer, connectedServerIds, connectingTimedOut) {
        derivedStateOf {
            when {
                showFtue -> StartupPhase.AcceptingTerms
                needsPin -> StartupPhase.EnteringPin
                // Has at least one connected server — ready to use
                hasConnectedServer -> StartupPhase.Ready
                // Servers exist but none connected yet (and haven't timed out)
                hasConnectingServer && !connectingTimedOut -> StartupPhase.ConnectingToServers(servers)
                // Timed out waiting for servers to connect
                hasConnectingServer && connectingTimedOut -> StartupPhase.ConnectingToServers(servers, timedOut = true)
                // No servers at all — still discovering
                servers.isEmpty() && !needsPin -> StartupPhase.DiscoveringServers
                // Fallback
                else -> StartupPhase.Ready
            }
        }
    }
}
