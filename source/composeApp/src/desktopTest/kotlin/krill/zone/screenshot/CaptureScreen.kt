package krill.zone.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/**
 * Render [content] off-screen and write a PNG named `$scenarioId.png` to the
 * Roborazzi output directory. Scenario IDs follow the `<area>__<state>`
 * convention documented in `README.md`.
 *
 * Determinism controls:
 *   - `LocalDensity` is pinned via [screenshotDensity] so host DPI doesn't leak.
 *   - Coil image loads are stubbed through [FakeImageLoader] — install it inside
 *     [content] if any composable downstream resolves remote image URLs.
 *   - Fonts are loaded from `resources/fonts/` (see [PinnedFonts]) — not the OS
 *     font registry — so text shaping matches across machines.
 *
 * NOTE on API surface: the exact Roborazzi capture API for Compose Desktop has
 * evolved across releases. This helper uses the `onRoot().captureRoboImage(File)`
 * form which is stable in roborazzi ≥ 1.4x. If the Gradle build reports an
 * unresolved reference for `captureRoboImage`, pin `libs.versions.roborazzi`
 * to a version whose compose-desktop artifact matches this signature, or swap
 * to `captureRoboImage(filePath = ...) { content() }` without the test rule.
 */
@OptIn(ExperimentalTestApi::class)
fun captureScreen(
    scenarioId: String,
    density: Density = screenshotDensity,
    content: @Composable () -> Unit,
) {
    val outputFile = File(outputDirectory(), "$scenarioId.png").apply {
        parentFile?.mkdirs()
    }
    runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                FakeImageLoader.Provide {
                    content()
                }
            }
        }
        onRoot().captureRoboImage(outputFile.absolutePath)
    }
}

/** Fixed density used for every scenario. 2.0 matches a typical HiDPI laptop. */
val screenshotDensity: Density = Density(2.0f)

/**
 * Where Roborazzi writes PNGs. Resolves to
 * `composeApp/build/outputs/roborazzi/` when run via the `desktopTest` task
 * (the path is injected as a system property in `composeApp/build.gradle.kts`).
 */
fun outputDirectory(): File {
    val prop = System.getProperty("roborazzi.output.dir")
    return if (!prop.isNullOrBlank()) File(prop)
    else File("build/outputs/roborazzi").apply { mkdirs() }
}
