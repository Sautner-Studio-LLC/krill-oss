package krill.zone.shared.node

import kotlinx.serialization.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.filter.*
import krill.zone.shared.krillapp.datapoint.graph.*
import krill.zone.shared.krillapp.executor.*
import krill.zone.shared.krillapp.executor.calculation.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.executor.logicgate.*
import krill.zone.shared.krillapp.executor.mqtt.*
import krill.zone.shared.krillapp.executor.smtp.*
import krill.zone.shared.krillapp.executor.webhook.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.diagram.*
import krill.zone.shared.krillapp.project.camera.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.backup.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.spacer.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*

interface NodeMetaData {
    val error: String
}

/**
 * Identifies a node by its own ID and the ID of its host server.
 * Used in TargetingNodeMetaData source and target lists so callers never need
 * to parse a "host:id" string to obtain the two parts separately.
 */
@Serializable
data class NodeIdentity(
    val nodeId: String,
    val hostId: String
) {


    override fun toString(): String {
        return  "${hostId}:${nodeId}"
    }
}

enum class ExecutionSource(val displayLabel: String) {
    PARENT_EXECUTE_SUCCESS("Parent Execute Success"),
    SOURCE_VALUE_MODIFIED("Source Value Modified"),
    ON_CLICK("On Click")
}
/**
 * Interface for metadata classes that have source and target node references.
 * Used by executor nodes that read from a source data point and write to a target data point.
 */
interface TargetingNodeMetaData : NodeMetaData {
    val sources: List<NodeIdentity>
    val targets: List<NodeIdentity>

    val executionSource: List<ExecutionSource>
}

/**
 * Updates the error field in any NodeMetaData implementation.
 * Since all implementations are data classes, this uses a when expression to call copy().
 */
fun updateMetaWithError(meta: NodeMetaData, error: String): NodeMetaData {
    return when (meta) {
        is ServerMetaData -> meta.copy(error = error)
        is PinMetaData -> meta.copy(error = error)
        is SerialDeviceMetaData -> meta.copy(error = error)
        is SerialDeviceTargetMetaData -> meta.copy(error = error)
        is ProjectMetaData -> meta.copy(error = error)
        is SpacerMetaData -> meta.copy(error = error)
        is DataPointMetaData -> meta.copy(error = error)
        is CalculationEngineNodeMetaData -> meta.copy(error = error)
        is TriggerMetaData -> meta.copy(error = error)
        is FilterMetaData -> meta.copy(error = error)
        is ExecuteMetaData -> meta.copy(error = error)
        is ClientMetaData -> meta.copy(error = error)
        is ComputeMetaData -> meta.copy(error = error)
        is CronMetaData -> meta.copy(error = error)
        is WebHookOutMetaData -> meta.copy(error = error)
        is IncomingWebHookMetaData -> meta.copy(error = error)
        is LambdaSourceMetaData -> meta.copy(error = error)
        is ButtonMetaData -> meta.copy(error = error)
        is LogicGateMetaData -> meta.copy(error = error)
        is MqttMetaData -> meta.copy(error = error)
        is GraphMetaData -> meta.copy(error = error)
        is DiagramMetaData -> meta.copy(error = error)
        is TaskListMetaData -> meta.copy(error = error)
        is JournalMetaData -> meta.copy(error = error)
        is SMTPMetaData -> meta.copy(error = error)
        is CameraMetaData -> meta.copy(error = error)
        is BackupMetaData -> meta.copy(error = error)
        else -> meta // Fallback for unknown types
    }
}


