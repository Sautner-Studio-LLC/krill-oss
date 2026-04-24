package krill.zone.screenshot

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import krill.zone.app.di.composeModule
import krill.zone.shared.SystemInfo
import krill.zone.shared.di.appModule
import krill.zone.shared.di.clientNodeManagerModule
import krill.zone.shared.di.clientProcessModule
import krill.zone.shared.di.platformModule
import krill.zone.shared.di.sharedModule
import krill.zone.shared.node.Node
import krill.zone.shared.node.manager.ClientNodeManager
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin

/**
 * JUnit 5 extension that owns the Koin + `ClientNodeManager` lifecycle for a
 * single screenshot scenario.
 *
 * Usage:
 * ```
 * @ExtendWith(ScreenshotScope::class)
 * class DashboardScenarios {
 *     @Test fun dashboardEmpty() { /* render from empty state */ }
 * }
 * ```
 *
 * Rationale: the real Krill client DI graph (`ClientNodeManager`, node
 * observers, etc.) is hydrated in-memory with no network or filesystem calls
 * originating from the screenshot tests themselves. Scenarios seed node state
 * by calling `seed(listOf(...))`. Koin is fully torn down between scenarios so
 * fixture state never leaks.
 */
class ScreenshotScope : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        SystemInfo.setServer(false)
        PinnedFonts.ensureLoaded()
        startKoin {
            // Kermit's own JVM logger is fine for tests; we don't wire the
            // JvmLogWriter used by `main.kt` because that requires logback
            // XML we don't need here.
            modules(
                sharedModule,
                appModule,
                composeModule,
                platformModule,
                clientProcessModule,
                clientNodeManagerModule,
            )
        }
    }

    override fun afterEach(context: ExtensionContext) {
        try {
            stopKoin()
        } catch (_: Throwable) {
            // Already stopped; swallow.
        }
    }

    companion object {
        private val logger = Logger.withTag("ScreenshotScope")

        /** Shared supervisor scope for test-side seeding coroutines. */
        val testScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Push a list of [Node]s into the running `ClientNodeManager` as if
         * they had arrived via SSE. Bypasses the network.
         */
        fun seed(nodes: List<Node>) {
            val manager = getKoin().get<ClientNodeManager>()
            nodes.forEach { manager.update(it) }
            logger.i("Seeded ${nodes.size} nodes")
        }
    }
}
