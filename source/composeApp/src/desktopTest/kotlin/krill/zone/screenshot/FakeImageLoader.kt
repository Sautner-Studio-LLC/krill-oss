package krill.zone.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Deterministic Coil image loader for screenshot scenarios.
 *
 * v1 status: stub. The v1 scenario catalog renders theme + text + node rows
 * that do not issue remote image loads, so no wiring is required yet. The
 * `Provide` wrapper is kept as an extension point so that when scenarios
 * start rendering `AsyncImage` (e.g. the Camera node editor), a deterministic
 * `ImageLoader` can be installed here without touching scenario code.
 *
 * When adding real wiring, pick one of:
 *  - `setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).build() }`
 *    once per JVM (Coil singletons are idempotent per-classloader in test
 *    forks — fine for our `desktopTest` fork).
 *  - `AsyncImagePreviewHandler` on a `LocalAsyncImagePreviewHandler` provider —
 *    this is the path Coil 3 recommends for tests/previews.
 */
object FakeImageLoader {

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        // TODO(screenshots): install a deterministic Coil ImageLoader when
        // scenarios begin rendering AsyncImage.
        content()
    }
}

/** Solid fill used wherever a remote image would otherwise appear in a scenario. */
fun Modifier.placeholderFill(color: Color = Color(0xFF2A2A2A)): Modifier =
    this.then(Modifier.fillMaxSize().background(color))
