# Krill node-type catalog

All node types known to a Krill swarm. Each row: type name, role, side-effect level, one-line behavior, link to full spec.

Group by **role** (`llmRole` field) to find candidates fast — `state` (stores values), `trigger` (fires on conditions), `action` / `executor` (acts on the world), `filter` (gates DataPoint updates), `display` (renders), `container` (groups), `infra` (system glue).

Read the linked JSON for the full `llmInputs`, `llmOutputs`, `llmConnectionHints` (which parents/children are valid), `llmExamples`, and side-effect level **before** suggesting a type to the user or wiring anything around it.

| Type | Role | Side effects | One-liner | Spec |
|------|------|--------------|-----------|------|
| `KrillApp.Executor.Lambda` | action | high | Runs a sandboxed Python script that reads its input nodes and stores its result in its own snapshot. | [KrillApp.Executor.Lambda.json](KrillApp.Executor.Lambda.json) |
| `KrillApp.Executor.OutgoingWebHook` | action | high | Sends an HTTP request to an external URL and stores the response in its own snapshot. | [KrillApp.Executor.OutgoingWebHook.json](KrillApp.Executor.OutgoingWebHook.json) |
| `KrillApp.Executor.SMTP` | action | high | Sends an email via SMTP when a node it observes completes. | [KrillApp.Executor.SMTP.json](KrillApp.Executor.SMTP.json) |
| `KrillApp.MQTT` | action | high | Publishes input values to or subscribes from an MQTT broker topic; received messages are stored in its own snapshot. | [KrillApp.MQTT.json](KrillApp.MQTT.json) |
| `KrillApp.Server.Backup` | action | high | Automated server backup with configurable retention and one-click restore. | [KrillApp.Server.Backup.json](KrillApp.Server.Backup.json) |
| `KrillApp.Server.LLM` | action | medium | Connects to a local Ollama LLM service for AI-assisted server automation. | [KrillApp.Server.LLM.json](KrillApp.Server.LLM.json) |
| `KrillApp.DataPoint.Filter` | container | none | Container for filter nodes; a filter takes effect when listed in the owning DataPoint's inputs. | [KrillApp.DataPoint.Filter.json](KrillApp.DataPoint.Filter.json) |
| `KrillApp.Executor` | container | none | Container that visually groups executor nodes; each executor is invoked via its own sources. | [KrillApp.Executor.json](KrillApp.Executor.json) |
| `KrillApp.Project` | container | none | Container for organizing project diagrams, task lists, and journals. | [KrillApp.Project.json](KrillApp.Project.json) |
| `KrillApp.Trigger` | container | none | Container that visually groups trigger nodes; each trigger wires its own sources to the DataPoint. | [KrillApp.Trigger.json](KrillApp.Trigger.json) |
| `KrillApp.Client.About` | display | none | Informational screen about the Krill platform. | [KrillApp.Client.About.json](KrillApp.Client.About.json) |
| `KrillApp.DataPoint.Graph` | display | none | Visualizes historical DataPoint values as a graph over a configurable time range. | [KrillApp.DataPoint.Graph.json](KrillApp.DataPoint.Graph.json) |
| `KrillApp.Project.Diagram` | display | none | Visual SVG diagram with anchored nodes that update in real time based on node state. | [KrillApp.Project.Diagram.json](KrillApp.Project.Diagram.json) |
| `KrillApp.DataPoint.Filter.Deadband` | logic | none | Discards snapshots where the value change from previous is smaller than a threshold. | [KrillApp.DataPoint.Filter.Deadband.json](KrillApp.DataPoint.Filter.Deadband.json) |
| `KrillApp.DataPoint.Filter.Debounce` | logic | none | Discards snapshots that arrive faster than a configured minimum time interval. | [KrillApp.DataPoint.Filter.Debounce.json](KrillApp.DataPoint.Filter.Debounce.json) |
| `KrillApp.DataPoint.Filter.DiscardAbove` | logic | none | Discards snapshots with values above a configured maximum threshold. | [KrillApp.DataPoint.Filter.DiscardAbove.json](KrillApp.DataPoint.Filter.DiscardAbove.json) |
| `KrillApp.DataPoint.Filter.DiscardBelow` | logic | none | Discards snapshots with values below a configured minimum threshold. | [KrillApp.DataPoint.Filter.DiscardBelow.json](KrillApp.DataPoint.Filter.DiscardBelow.json) |
| `KrillApp.Executor.LogicGate` | logic | medium | Applies boolean logic to its input nodes' values and stores the result in its own snapshot. | [KrillApp.Executor.LogicGate.json](KrillApp.Executor.LogicGate.json) |
| `KrillApp.Project.Camera` | sensor | low | Live camera feed from a Raspberry Pi camera module or USB camera. | [KrillApp.Project.Camera.json](KrillApp.Project.Camera.json) |
| `KrillApp.DataPoint` | state | low | Stores time-series snapshot values; nodes observing it are invoked on each update. | [KrillApp.DataPoint.json](KrillApp.DataPoint.json) |
| `KrillApp.Project.Journal` | state | none | Chronological journal for documenting project progress and observations. | [KrillApp.Project.Journal.json](KrillApp.Project.Journal.json) |
| `KrillApp` | structural | none | Root type for all Krill node types. | [KrillApp.json](KrillApp.json) |
| `KrillApp.Client` | structural | medium | Root client node that discovers and connects to Krill servers on the network. | [KrillApp.Client.json](KrillApp.Client.json) |
| `KrillApp.Server` | structural | medium | A Krill server instance that manages devices, automation, and data on a Raspberry Pi or Linux host. | [KrillApp.Server.json](KrillApp.Server.json) |
| `KrillApp.Server.Peer` | structural | medium | Represents a peer-to-peer connection between two Krill servers in the mesh network. | [KrillApp.Server.Peer.json](KrillApp.Server.Peer.json) |
| `KrillApp.Server.Pin` | target | high | Controls a Raspberry Pi GPIO pin with configurable mode, state, and startup/shutdown behavior. | [KrillApp.Server.Pin.json](KrillApp.Server.Pin.json) |
| `KrillApp.Server.SerialDevice` | target | high | Communicates with hardware devices via serial ports with configurable protocol settings. | [KrillApp.Server.SerialDevice.json](KrillApp.Server.SerialDevice.json) |
| `KrillApp.Executor.Calculation` | transform | low | Computes a mathematical formula from its input nodes' snapshots and stores the result in its own snapshot. | [KrillApp.Executor.Calculation.json](KrillApp.Executor.Calculation.json) |
| `KrillApp.Executor.Compute` | transform | low | Computes a statistical summary of a DataPoint's historical values over a time range and stores the result in its own snapshot. | [KrillApp.Executor.Compute.json](KrillApp.Executor.Compute.json) |
| `KrillApp.Project.TaskList` | trigger | low | Stateful checklist whose observers are invoked when tasks become overdue; supports recurring tasks via cron. | [KrillApp.Project.TaskList.json](KrillApp.Project.TaskList.json) |
| `KrillApp.Trigger.Button` | trigger | none | Fires on user click; observing nodes are invoked with the button's verb (EXECUTE or RESET). | [KrillApp.Trigger.Button.json](KrillApp.Trigger.Button.json) |
| `KrillApp.Trigger.Color` | trigger | low | Triggers when a DataPoint color falls within a configured RGB range. | [KrillApp.Trigger.Color.json](KrillApp.Trigger.Color.json) |
| `KrillApp.Trigger.CronTimer` | trigger | low | Fires on a cron schedule; nodes observing it are invoked. | [KrillApp.Trigger.CronTimer.json](KrillApp.Trigger.CronTimer.json) |
| `KrillApp.Trigger.HighThreshold` | trigger | low | Triggers when a DataPoint value reaches or exceeds a configured upper limit. | [KrillApp.Triggers.HighThreshold.json](KrillApp.Triggers.HighThreshold.json) |
| `KrillApp.Trigger.IncomingWebHook` | trigger | low | Fires when an HTTP request hits its configured endpoint; observing nodes are invoked. | [KrillApp.Trigger.IncomingWebHook.json](KrillApp.Trigger.IncomingWebHook.json) |
| `KrillApp.Trigger.LowThreshold` | trigger | low | Triggers when a DataPoint value falls at or below a configured lower limit. | [KrillApp.Trigger.LowThreshold.json](KrillApp.Trigger.LowThreshold.json) |
| `KrillApp.Trigger.SilentAlarmMs` | trigger | low | Triggers an alarm when no data updates are received within a configured time period. | [KrillApp.Trigger.SilentAlarm.json](KrillApp.Trigger.SilentAlarm.json) |
