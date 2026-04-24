package krill.zone.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import krill.zone.app.DarkBlueGrayTheme
import krill.zone.app.krillapp.executor.calculation.EditCalc
import krill.zone.shared.KrillApp
import krill.zone.shared.node.Node
import krill.zone.shared.node.manager.ClientNodeManager
import org.koin.java.KoinJavaComponent.getKoin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * v1 scenario catalog. Each `@Test` produces one PNG named after the scenario
 * ID declared in [ScreenshotCatalog.docsCanonical].
 *
 * Current rendering strategy: wrap a real [DarkBlueGrayTheme] around a
 * compact node summary panel constructed from seeded fixtures. This is
 * intentionally simpler than booting the full `App()` composable — `App()`
 * requires an async Koin-backed initialization flow (see `AppScaffold` in
 * `composeApp/src/commonMain/kotlin/krill/zone/app/App.kt`) that doesn't
 * finish within a single `setContent { }` pass. Once that bootstrap is made
 * testable, these scenarios can swap in `KrillScreen()` directly and the
 * PNG naming + `ScreenshotCatalog` entries stay identical.
 */
@ExtendWith(ScreenshotScope::class)
class Scenarios {

    // -- Dashboard -----------------------------------------------------------

    @Test fun dashboardEmpty() {
        ScreenshotScope.seed(Fixtures.emptySwarm())
        captureScreen("dashboard__empty") {
            DarkBlueGrayTheme {
                DashboardSurface(nodes = emptyList(), title = "No nodes yet")
            }
        }
    }

    @Test fun dashboardPopulated() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("dashboard__populated") {
            DarkBlueGrayTheme {
                DashboardSurface(nodes = nodes, title = "pi-krill swarm")
            }
        }
    }

    // -- Per-node editors ----------------------------------------------------

    @Test fun editorTrigger() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("editor__trigger") {
            DarkBlueGrayTheme {
                SingleNodeEditorSurface(nodes.first { it.type == KrillApp.Trigger.HighThreshold })
            }
        }
    }

    @Test fun editorFilter() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("editor__filter") {
            DarkBlueGrayTheme {
                SingleNodeEditorSurface(nodes.first { it.type == KrillApp.DataPoint.Filter.Deadband })
            }
        }
    }

    @Test fun editorExecutor() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("editor__executor") {
            DarkBlueGrayTheme {
                SingleNodeEditorSurface(nodes.first { it.type == KrillApp.Executor.OutgoingWebHook })
            }
        }
    }

    @Test fun editorDatapoint() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("editor__datapoint") {
            DarkBlueGrayTheme {
                // Match the bare DataPoint parent, not a nested Filter.
                SingleNodeEditorSurface(nodes.first { it.type == KrillApp.DataPoint })
            }
        }
    }

    @Test fun editorDiagram() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("editor__diagram") {
            DarkBlueGrayTheme {
                SingleNodeEditorSurface(nodes.first { it.type == KrillApp.Project.Diagram })
            }
        }
    }

    /**
     * Calculation editor with a self-incrementing `counter` data point:
     * target = source + 1, where both source and target are the same
     * `counter` DataPoint. Used in the Calculation Executor blog post.
     */
    @Test fun editorCalculationCounter() {
        val nodes = Fixtures.calculationCounterSwarm()
        ScreenshotScope.seed(nodes)
        val nodeManager = getKoin().get<ClientNodeManager>()
        nodeManager.selectNode(Fixtures.CALCULATION_ID)
        captureScreen("editor__calculation-counter") {
            DarkBlueGrayTheme {
                EditCalc()
            }
        }
    }

    // -- Diagram live overlay ------------------------------------------------

    @Test fun diagramLiveOverlay() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("diagram__live-overlay") {
            DarkBlueGrayTheme {
                DiagramOverlaySurface(nodes)
            }
        }
    }

    // -- Theme variants ------------------------------------------------------

    @Test fun themeDarkDashboardPopulated() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("theme__dark__dashboard__populated") {
            DarkBlueGrayTheme(darkTheme = true) {
                DashboardSurface(nodes = nodes, title = "pi-krill (dark)")
            }
        }
    }

    @Test fun themeLightDashboardPopulated() {
        val nodes = Fixtures.populatedSwarm()
        ScreenshotScope.seed(nodes)
        captureScreen("theme__light__dashboard__populated") {
            DarkBlueGrayTheme(darkTheme = false) {
                DashboardSurface(nodes = nodes, title = "pi-krill (light)")
            }
        }
    }
}

// ---- Private rendering surfaces -------------------------------------------
// Kept local to the test source set; they are deliberately structural — just
// enough to make the PNG distinguishable per scenario. Swap in real screens
// (KrillScreen, NodeSummaryAndEditor) once the async bootstrap is testable.

@Composable
private fun DashboardSurface(nodes: List<Node>, title: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(16.dp))
            if (nodes.isEmpty()) {
                Text(
                    text = "Add a server to get started",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                )
            } else {
                nodes.forEach { NodeRow(it) }
            }
        }
    }
}

@Composable
private fun NodeRow(node: Node) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = node.type.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
        Text(
            text = "id=${node.id}  state=${node.state}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SingleNodeEditorSurface(node: Node) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Edit: ${node.type}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
            )
            Text(
                text = "id: ${node.id}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            )
            Text(
                text = "parent: ${node.parent}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            )
            Text(
                text = "meta: ${node.meta}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DiagramOverlaySurface(nodes: List<Node>) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Kitchen diagram (live)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(12.dp))
            // Stylized SVG-placeholder so the PNG is not blank.
            Column(modifier = Modifier.size(320.dp, 220.dp).background(Color(0xFF1C2230))) {
                nodes.forEach { NodeRow(it) }
            }
        }
    }
}
