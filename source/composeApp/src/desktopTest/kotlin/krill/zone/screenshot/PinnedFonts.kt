package krill.zone.screenshot

import co.touchlab.kermit.Logger
import java.awt.Font
import java.awt.GraphicsEnvironment

/**
 * Loads a pinned application font from `composeApp/src/desktopTest/resources/fonts/`
 * into the JVM's graphics environment so text shaping does not depend on the
 * host OS font registry.
 *
 * Why this matters: Skia (the Compose Desktop renderer) falls back to
 * platform-provided fonts when an explicit `FontFamily` isn't set. A laptop
 * running "Helvetica" vs a CI container running "Liberation Sans" will
 * produce byte-different PNGs even if the source code is identical.
 *
 * ## Contributor action required
 *
 * For determinism we need a redistributable font file committed under
 * `composeApp/src/desktopTest/resources/fonts/`. This harness expects a file
 * named `app-default.ttf`. Inter (OFL) or Roboto (Apache 2.0) are good
 * choices. Once the font file is in place, re-run
 * `./gradlew :composeApp:recordRoborazziDesktop`.
 *
 * Until a font file is added, this loader no-ops with a warning so the first
 * record run still completes end-to-end (it just may produce a screenshot
 * that differs across machines).
 */
object PinnedFonts {

    private val logger = Logger.withTag("PinnedFonts")
    private const val RESOURCE_PATH = "fonts/app-default.ttf"

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH)
            if (stream == null) {
                logger.w(
                    "Pinned font $RESOURCE_PATH not found on classpath. Screenshots " +
                        "may differ across machines until a font file is committed. " +
                        "See composeApp/src/desktopTest/kotlin/krill/zone/screenshot/README.md."
                )
                loaded = true
                return
            }
            try {
                val font = Font.createFont(Font.TRUETYPE_FONT, stream)
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
                logger.i("Registered pinned font: ${font.fontName}")
            } catch (t: Throwable) {
                logger.e(t) { "Failed to register pinned font from $RESOURCE_PATH" }
            } finally {
                stream.close()
                loaded = true
            }
        }
    }
}
