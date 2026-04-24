package krill.zone.screenshot

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Enumerates which scenarios are "docs-canonical" — meaning the PNG should be
 * copied into the checked-in `docs/assets/screenshots/` directory for
 * consumption by the Jekyll site.
 *
 * Adding a new scenario to this list is the ONLY manual step required to
 * promote it into the published set. The harness scenario tests still run
 * either way (and their output lands under `build/outputs/roborazzi/`), so a
 * scenario can exist for local debugging without being docs-canonical.
 *
 * Scenario IDs MUST match the filename passed to `captureScreen(...)` in the
 * test, minus the `.png` extension.
 */
object ScreenshotCatalog {

    /** Docs-canonical scenario identifiers. */
    val docsCanonical: Set<String> = setOf(
        "dashboard__empty",
        "dashboard__populated",
        "editor__trigger",
        "editor__filter",
        "editor__executor",
        "editor__datapoint",
        "editor__diagram",
        "editor__calculation-counter",
        "diagram__live-overlay",
        "theme__light__dashboard__populated",
        "theme__dark__dashboard__populated",
    )

    /**
     * Copy every docs-canonical PNG from Roborazzi's build output into
     * `docs/assets/screenshots/`. Called by the `recordRoborazziDesktop`
     * Gradle task. Missing files are logged but don't fail the task — that
     * lets contributors add a scenario ID before its test body exists.
     */
    @JvmStatic
    fun promoteToDocs(buildOutputDir: File, docsDir: File) {
        if (!docsDir.exists()) docsDir.mkdirs()
        var copied = 0
        var missing = 0
        for (id in docsCanonical) {
            val src = File(buildOutputDir, "$id.png")
            if (!src.exists()) {
                missing++
                println("[screenshots] missing: ${src.absolutePath}")
                continue
            }
            val dst = File(docsDir, "$id.png")
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
            copied++
        }
        println("[screenshots] promoted $copied (missing: $missing) → ${docsDir.absolutePath}")
    }
}
