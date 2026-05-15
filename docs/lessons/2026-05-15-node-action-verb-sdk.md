# NodeAction verb and ActionNodeMetaData added to krill-sdk

**Issue:** [krill-oss#80](https://github.com/Sautner-Studio-LLC/krill-oss/issues/80)
**Module:** `module:krill-sdk`

## What happened

The Krill swarm had no first-class way to express that a trigger or executor node should *reset* its target rather than *execute* it. The only execution path was the implicit forward-execute; a reset required the server to infer intent from node type or some ad-hoc flag. This made it impossible for the editor and the server processor to branch cleanly on execute-vs-reset without per-type `when` arms.

## Fix

- **`NodeAction` enum** added to `krill.zone.shared.node.NodeMetaData` (values: `EXECUTE`, `RESET`, each with a `displayLabel`). Wire name is the enum name; ordinals are not used for this type.
- **`NodeState.RESET`** appended to `NodeState` (after `EDITING`). Ordinals of all prior values preserved.
- **`ActionNodeMetaData` interface** added to the same file — extends `NodeMetaData` and declares `val nodeAction: NodeAction`. Trigger-family and `TargetingNodeMetaData`-extending metadata all implement it.
- **`TargetingNodeMetaData`** updated to extend `ActionNodeMetaData` instead of `NodeMetaData` directly — every targeting node now carries `nodeAction` as part of its contract.
- **All `TargetingNodeMetaData` implementors** in the SDK updated with `override val nodeAction: NodeAction = NodeAction.EXECUTE`: `GraphMetaData`, `LogicGateMetaData`, `SMTPMetaData`, `WebHookOutMetaData`, `ComputeMetaData`, `LambdaSourceMetaData`, `MqttMetaData`, `CalculationEngineNodeMetaData`, `IncomingWebHookMetaData`, `TaskListMetaData`, `SerialDeviceMetaData`.
- **Trigger-family implementors** (not already in `TargetingNodeMetaData`): `TriggerMetaData`, `ButtonMetaData`, `CronMetaData`, `ColorTriggerMetaData` — all updated to implement `ActionNodeMetaData` and carry the same default.
- **Back-compat test** (`NodeActionTest`) verifies that pre-0.0.23 payloads missing the `nodeAction` field deserialise with `NodeAction.EXECUTE` as the default, for both `NodeMetaData`-only and `TargetingNodeMetaData` subtypes.
- Version bumped `0.0.22 → 0.0.23`.

## Prevention

- **When adding a field with semantic meaning across a whole interface family, push it into the interface rather than each leaf class.** Adding `nodeAction` to `TargetingNodeMetaData` directly rather than to each executor/trigger individually keeps the invariant enforced by the type system instead of relying on convention.
- **Default values on `@Serializable` data class fields give free back-compat.** kotlinx.serialization uses the Kotlin default when the field is absent in the JSON. A back-compat test (pre-field JSON → assert default) is cheap to write and pins the contract permanently.
- **Run `./gradlew compileKotlinJvm` after adding a new interface property** to catch missing implementors before running the full test suite — the compile error is faster feedback than a test failure.
