/**
 * Cross-cutting helper for updating the `error` field on any concrete
 * [NodeMetaData] without the caller knowing the subtype.
 *
 * Lives in its own file because the `when` over every concrete MetaData
 * subtype pulls in imports from every `krillapp/*` subpackage; keeping it
 * separate from the [NodeMetaData] interface declaration keeps that file
 * dependency-light.
 */
package krill.zone.shared.node

import krill.zone.shared.krillapp.client.ClientMetaData
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.datapoint.filter.FilterMetaData
import krill.zone.shared.krillapp.datapoint.graph.GraphMetaData
import krill.zone.shared.krillapp.executor.ExecuteMetaData
import krill.zone.shared.krillapp.executor.calculation.CalculationEngineNodeMetaData
import krill.zone.shared.krillapp.executor.compute.ComputeMetaData
import krill.zone.shared.krillapp.executor.lambda.LambdaSourceMetaData
import krill.zone.shared.krillapp.executor.logicgate.LogicGateMetaData
import krill.zone.shared.krillapp.executor.mqtt.MqttMetaData
import krill.zone.shared.krillapp.executor.smtp.SMTPMetaData
import krill.zone.shared.krillapp.executor.webhook.WebHookOutMetaData
import krill.zone.shared.krillapp.project.ProjectMetaData
import krill.zone.shared.krillapp.project.camera.CameraMetaData
import krill.zone.shared.krillapp.project.diagram.DiagramMetaData
import krill.zone.shared.krillapp.project.journal.JournalMetaData
import krill.zone.shared.krillapp.project.tasklist.TaskListMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.backup.BackupMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import krill.zone.shared.krillapp.server.serialdevice.SerialDeviceMetaData
import krill.zone.shared.krillapp.server.serialdevice.SerialDeviceTargetMetaData
import krill.zone.shared.krillapp.spacer.SpacerMetaData
import krill.zone.shared.krillapp.trigger.TriggerMetaData
import krill.zone.shared.krillapp.trigger.button.ButtonMetaData
import krill.zone.shared.krillapp.trigger.color.ColorTriggerMetaData
import krill.zone.shared.krillapp.trigger.cron.CronMetaData
import krill.zone.shared.krillapp.trigger.webhook.IncomingWebHookMetaData

/**
 * Returns a copy of [meta] with its `error` field replaced by [error].
 *
 * Centralised because each concrete `MetaData` subtype is a different
 * `data class` — the only way to flip a single field on an unknown subtype
 * without losing the others is per-subtype `copy(error = ...)`. The `else`
 * arm returns the input unchanged, which is the right behaviour for any
 * future MetaData type that hasn't been added to the `when` yet.
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
        is ColorTriggerMetaData -> meta.copy(error = error)
        else -> meta
    }
}
