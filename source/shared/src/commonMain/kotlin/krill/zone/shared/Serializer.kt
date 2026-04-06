package krill.zone.shared

import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import krill.zone.shared.events.*
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
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.spacer.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.color.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.node.*


val platformSerializerModule = SerializersModule {

    polymorphic(NodeMetaData::class) {

        subclass(ServerMetaData::class)
        subclass(PinMetaData::class)
        subclass(SerialDeviceMetaData::class)
        subclass(ProjectMetaData::class)
        subclass(SpacerMetaData::class)
        subclass(DataPointMetaData::class)
        subclass(CalculationEngineNodeMetaData::class)
        subclass(TriggerMetaData::class)
        subclass(FilterMetaData::class)
        subclass(ExecuteMetaData::class)
        subclass(ClientMetaData::class)
        subclass(SerialDeviceTargetMetaData::class)
        subclass(ComputeMetaData::class)
        subclass(CronMetaData::class)
        subclass(WebHookOutMetaData::class)
        subclass(IncomingWebHookMetaData::class)
        subclass(LambdaSourceMetaData::class)
        subclass(ButtonMetaData::class)
        subclass(LogicGateMetaData::class)
        subclass(MqttMetaData::class)
        subclass(GraphMetaData::class)
        subclass(DiagramMetaData::class)
        subclass(TaskListMetaData::class)
        subclass(JournalMetaData::class)
        subclass(LLMMetaData::class)
        subclass(SMTPMetaData::class)
        subclass(CameraMetaData::class)
        subclass(BackupMetaData::class)
        subclass(ColorTriggerMetaData::class)

    }
    polymorphic(baseClass = KrillApp::class) {
        subclass(KrillApp.Client::class)
        subclass(KrillApp.Client.About::class)
        subclass(KrillApp.Server::class)
        subclass(KrillApp.Server.Pin::class)
        subclass(KrillApp.Server.Peer::class)
        subclass(KrillApp.Server.LLM::class)
        subclass(KrillApp.Server.SerialDevice::class)
        subclass(KrillApp.Server.Backup::class)
        subclass(KrillApp.Project::class)
        subclass(KrillApp.Project.Diagram::class)
        subclass(KrillApp.Project.TaskList::class)
        subclass(KrillApp.Project.Journal::class)
        subclass(KrillApp.Project.Camera::class)
        subclass(KrillApp.MQTT::class)
        subclass(KrillApp.DataPoint::class)
        subclass(KrillApp.DataPoint.Filter::class)
        subclass(KrillApp.DataPoint.Filter.DiscardAbove::class)
        subclass(KrillApp.DataPoint.Filter.DiscardBelow::class)
        subclass(KrillApp.DataPoint.Filter.Deadband::class)
        subclass(KrillApp.DataPoint.Filter.Debounce::class)
        subclass(KrillApp.DataPoint.Graph::class)
        subclass(KrillApp.Executor::class)
        subclass(KrillApp.Executor.LogicGate::class)
        subclass(KrillApp.Executor.OutgoingWebHook::class)
        subclass(KrillApp.Executor.Lambda::class)
        subclass(KrillApp.Executor.Calculation::class)
        subclass(KrillApp.Executor.Compute::class)
        subclass(KrillApp.Executor.SMTP::class)
        subclass(KrillApp.Trigger::class)
        subclass(KrillApp.Trigger.Button::class)
        subclass(KrillApp.Trigger.CronTimer::class)
        subclass(KrillApp.Trigger.SilentAlarmMs::class)
        subclass(KrillApp.Trigger.HighThreshold::class)
        subclass(KrillApp.Trigger.LowThreshold::class)
        subclass(KrillApp.Trigger.IncomingWebHook::class)
        subclass(KrillApp.Trigger.Color::class)
        subclass(MenuCommand.Update::class)
        subclass(MenuCommand.Delete::class)
        subclass(MenuCommand.Expand::class)
        subclass(MenuCommand.Focus::class)
    }
    polymorphic(baseClass = EventPayload::class) {
        subclass(PinEventPayload::class)
        subclass(StateChangeEventPayload::class)
        subclass(EmptyPayload::class)
        subclass(SnapshotUpdatedEventPayload::class)
        subclass(LLMEventPayload::class)
        subclass(NodeCreatedPayload::class)
    }
}

val fastJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    serializersModule = platformSerializerModule
}
