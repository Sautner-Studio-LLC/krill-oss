/**
 * Metadata for the `SMTP` executor — sends an email via a configured SMTP
 * server when fired. The server holds the credentials and dispatches the
 * message; clients just compose the configuration here.
 */
package krill.zone.shared.krillapp.executor.smtp

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for an `SMTP` executor node.
 */
@Serializable
data class SMTPMetaData(
    /** SMTP relay hostname (e.g. `"smtp.gmail.com"`). */
    val host: String = "",
    /** SMTP submission port — defaults to 587 (STARTTLS). */
    val port: Int = 587,
    /** SMTP authentication username. */
    val username: String = "",
    /** SMTP authentication password / app-token. Stored server-side; never sent to read clients. */
    val token: String = "",
    /** `From:` address shown on outgoing mail. */
    val fromAddress: String = "",
    /** `To:` address(es) — comma-separated list. */
    val toAddress: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
