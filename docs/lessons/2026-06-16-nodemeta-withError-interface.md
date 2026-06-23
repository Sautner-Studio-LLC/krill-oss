## What happened

`NodeMetaDataUpdate.updateMetaWithError` used an exhaustive `when (meta)` with `else -> meta` to copy the error field onto a node's metadata. The `else` arm silently returned the *unchanged* metadata for any subtype not in the dispatch list. `LLMMetaData` was confirmed missing from that list, meaning errors set on LLM nodes were silently discarded at runtime — no exception, no log line, just the wrong state. Symmetrically, `NodeFunctions.name()` and `isDigital()` used exhaustive `when (this.type)` over all KrillApp subtypes, coupling the shared helper layer to every concrete MetaData class via direct casts. Every new node type required manual updates to three separate functions, and forgetting any one of them produced silent misbehaviour.

## Fix

- Added three methods to the `NodeMetaData` interface (`krill-sdk/src/commonMain/kotlin/krill/zone/shared/node/NodeMetaData.kt`):
  - `withError(error: String): NodeMetaData` — **abstract** (no default), implemented by each `data class` as `copy(error = error)`. Compile-time enforced: a new subtype that omits this fails to build.
  - `displayName(): String = ""` — overridden in types that carry a display name field; callers fall back to the `KrillApp` type string when empty.
  - `isDigital(): Boolean = false` — overridden to `true` in `PinMetaData`, `LogicGateMetaData`, `TaskListMetaData`; overridden to `dataType == DataType.DIGITAL` in `DataPointMetaData`.
- All 30 concrete `NodeMetaData` data classes received `override fun withError(error: String) = copy(error = error)` plus the relevant `displayName()` / `isDigital()` overrides where applicable.
- `NodeMetaDataUpdate.updateMetaWithError` replaced its 29-arm `when` + import block with a single delegation: `meta.withError(error)`.
- `NodeFunctions.name()` replaced its 14-arm `when` with `meta.displayName().ifEmpty { this.type.toString() }`.
- `NodeFunctions.isDigital()` replaced its `when` with `this.meta.isDigital()`.
- Regression test in `krill-sdk/src/commonTest/kotlin/krill/zone/shared/node/NodeMetaDataInterfaceTest.kt` covers `withError` (including `LLMMetaData` which was the silent-failure victim), `displayName`, `isDigital`, and the end-to-end `Node.name()` / `Node.isDigital()` extensions.

## Prevention

- Prefer abstract interface methods over `when`-based dispatch tables for per-type polymorphism. An abstract method causes a compile error when a new subtype is added without an implementation; `when (meta) { ... else -> meta }` silently does the wrong thing.
- When a function must dispatch on an interface type, check whether the dispatch can be pushed down into the interface itself (the "tell, don't ask" pattern). If each concrete type knows how to perform the operation (copy-with-changed-field, extract display name, signal digital-ness), the orchestrating function becomes a one-liner with no concrete-type knowledge.
- `else -> <unchanged value>` in a `when` that touches a metadata object is a red flag — it means "I silently do nothing for types I don't know about." Prefer abstract interface methods or `error("unknown subtype $meta")` to make the gap visible at compile or run time.
