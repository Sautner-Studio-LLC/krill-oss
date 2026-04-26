/**
 * The root sealed class of every Krill node-type discriminator, plus the
 * lookup tables that let runtime code walk the type hierarchy.
 *
 * `KrillApp` is intentionally a *pure* discriminator — it carries no
 * factories, no DI lookups, no per-type behaviour. The default-metadata
 * factory and processor-emit dispatch live in sidecar extension functions
 * back in the `/shared` module (`KrillAppMeta.kt`, `KrillAppEmit.kt`)
 * because both reach into per-process state (concrete MetaData defaults,
 * Koin) the SDK has no visibility into.
 *
 * Adding a new subtype requires:
 *  1. A new `data object` (or sealed subclass) here.
 *  2. A polymorphic `subclass(...)` registration in the consuming module's
 *     `Serializer.kt`.
 *  3. A `when` arm in the `KrillApp.meta` and `KrillApp.emit` sidecars.
 *  4. The full new-node-type checklist in the consuming project's
 *     `CLAUDE.md`.
 */
package krill.zone.shared

import kotlinx.serialization.*

/**
 * Menu-command discriminators — used as `KrillApp` subtypes when emitting
 * editor commands (Update / Delete / Expand / Focus) on the same SSE stream
 * as real node events. They share a sealed parent so consumers can route
 * them with one `is MenuCommand` check.
 */
@Serializable
sealed class MenuCommand : KrillApp() {
    @Serializable data object Update : MenuCommand()
    @Serializable data object Delete : MenuCommand()
    @Serializable data object Expand : MenuCommand()
    @Serializable data object Focus : MenuCommand()
}

/**
 * Map of every [KrillApp] parent to its direct children.
 *
 * Key `null` holds the top-level `KrillApp` instances (direct subclasses of
 * `KrillApp`). Leaf types have no entry — callers should treat
 * `krillAppChildren[app]` as nullable.
 */
val krillAppChildren: Map<KrillApp?, List<KrillApp>> = mapOf(
    // Top-level
    null to listOf(
        KrillApp.Client, KrillApp.Server, KrillApp.Project,
        KrillApp.MQTT, KrillApp.DataPoint, KrillApp.Executor, KrillApp.Trigger,
    ),

    // Server children
    KrillApp.Server to listOf(
        KrillApp.Server.Pin, KrillApp.Server.Peer,
        KrillApp.Server.LLM, KrillApp.Server.SerialDevice,
        KrillApp.Server.Backup,
    ),

    // Project children
    KrillApp.Project to listOf(
        KrillApp.Project.Diagram, KrillApp.Project.TaskList,
        KrillApp.Project.Journal, KrillApp.Project.Camera,
    ),

    // DataPoint children
    KrillApp.DataPoint to listOf(KrillApp.DataPoint.Filter, KrillApp.DataPoint.Graph),

    // DataPoint.Filter children
    KrillApp.DataPoint.Filter to listOf(
        KrillApp.DataPoint.Filter.DiscardAbove,
        KrillApp.DataPoint.Filter.DiscardBelow,
        KrillApp.DataPoint.Filter.Deadband,
        KrillApp.DataPoint.Filter.Debounce,
    ),

    // Executor children
    KrillApp.Executor to listOf(
        KrillApp.Executor.LogicGate,
        KrillApp.Executor.OutgoingWebHook,
        KrillApp.Executor.Lambda,
        KrillApp.Executor.Calculation,
        KrillApp.Executor.Compute,
        KrillApp.Executor.SMTP,
    ),

    // Trigger children
    KrillApp.Trigger to listOf(
        KrillApp.Trigger.Button,
        KrillApp.Trigger.CronTimer,
        KrillApp.Trigger.SilentAlarmMs,
        KrillApp.Trigger.HighThreshold,
        KrillApp.Trigger.LowThreshold,
        KrillApp.Trigger.IncomingWebHook,
        KrillApp.Trigger.Color,
    ),
)

/** Flat list of every [KrillApp] instance across the hierarchy (all levels). */
val allKrillApps: List<KrillApp> by lazy {
    fun collect(parent: KrillApp?): List<KrillApp> {
        val children = krillAppChildren[parent] ?: return emptyList()
        return children + children.flatMap { collect(it) }
    }
    collect(null)
}

/** Returns all descendants (children, grandchildren, …) of the given [app]. */
fun krillAppDescendants(app: KrillApp): List<KrillApp> {
    val children = krillAppChildren[app] ?: return emptyList()
    return children + children.flatMap { krillAppDescendants(it) }
}

private val krillAppLookupMap: Map<String, KrillApp> by lazy {
    fun hierarchicalName(app: KrillApp): String {
        val parent = krillAppChildren.entries.firstOrNull { (_, children) -> app in children }?.key
        return if (parent != null) "${hierarchicalName(parent)}.${app::class.simpleName}"
        else app::class.simpleName ?: ""
    }
    buildMap {
        for (app in allKrillApps) {
            val simpleName = app::class.simpleName ?: continue
            val hierName = hierarchicalName(app)
            put("KrillApp.$hierName", app)
            put(hierName, app)
            if (!containsKey(simpleName)) put(simpleName, app)
        }
    }
}

/**
 * Resolves a free-form name (`"DataPoint.Filter.Deadband"`,
 * `"KrillApp.DataPoint.Filter.Deadband"`, or just `"Deadband"`) to its
 * [KrillApp] instance. Returns `null` for unknown names.
 */
fun lookup(name: String): KrillApp? = krillAppLookupMap[name]

/**
 * Sealed type discriminator for every Krill node type.
 *
 * Pure value type — every subtype is a `data object`, so equality is
 * identity and the JSON wire form is a single discriminator string. The
 * `meta = {}` and `emit = {}` constructor parameters that this class used
 * to carry have been lifted out into sidecar extension functions in the
 * `/shared` module.
 */
@Serializable
sealed class KrillApp {

    @Serializable
    data object Client : KrillApp() {
        @Serializable
        data object About : KrillApp()
    }

    @Serializable
    data object Server : KrillApp() {

        @Serializable
        data object Pin : KrillApp()

        @Serializable
        data object Peer : KrillApp()

        @Serializable
        data object LLM : KrillApp()

        @Serializable
        data object SerialDevice : KrillApp()

        @Serializable
        data object Backup : KrillApp()
    }

    @Serializable
    data object Project : KrillApp() {

        @Serializable
        data object Diagram : KrillApp()

        @Serializable
        data object TaskList : KrillApp()

        @Serializable
        data object Journal : KrillApp()

        @Serializable
        data object Camera : KrillApp()
    }

    @Serializable
    data object MQTT : KrillApp()

    @Serializable
    data object DataPoint : KrillApp() {

        @Serializable
        data object Filter : KrillApp() {

            @Serializable
            data object DiscardAbove : KrillApp()

            @Serializable
            data object DiscardBelow : KrillApp()

            @Serializable
            data object Deadband : KrillApp()

            @Serializable
            data object Debounce : KrillApp()
        }

        @Serializable
        data object Graph : KrillApp()
    }

    @Serializable
    data object Executor : KrillApp() {

        @Serializable
        data object LogicGate : KrillApp()

        @Serializable
        data object OutgoingWebHook : KrillApp()

        @Serializable
        data object Lambda : KrillApp()

        @Serializable
        data object Calculation : KrillApp()

        @Serializable
        data object Compute : KrillApp()

        @Serializable
        data object SMTP : KrillApp()
    }

    @Serializable
    data object Trigger : KrillApp() {

        @Serializable
        data object Button : KrillApp()

        @Serializable
        data object CronTimer : KrillApp()

        @Serializable
        data object SilentAlarmMs : KrillApp()

        @Serializable
        data object HighThreshold : KrillApp()

        @Serializable
        data object LowThreshold : KrillApp()

        @Serializable
        data object IncomingWebHook : KrillApp()

        @Serializable
        data object Color : KrillApp()
    }
}
