/**
 * Metadata for the `Color` trigger — fires when its source DataPoint's COLOR
 * value falls inside the configured RGB axis-aligned bounding box.
 *
 * Each channel is bounded independently with `min`/`max` pairs in `[0, 255]`,
 * so the trigger window is a 3D cube in RGB space. The [midpointArgb] helper
 * exposes the centre of that cube as a packed ARGB value for the editor's
 * colour-swatch icon.
 */
package krill.zone.shared.krillapp.trigger.color

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `Color` trigger.
 */
@Serializable
data class ColorTriggerMetaData(
    /** Display name shown on the node chip. */
    val name: String = "Color Trigger",
    /** Lower bound for the red channel `[0, 255]`. */
    val rMin: Int = 0,
    /** Upper bound for the red channel `[0, 255]`. */
    val rMax: Int = 255,
    /** Lower bound for the green channel `[0, 255]`. */
    val gMin: Int = 0,
    /** Upper bound for the green channel `[0, 255]`. */
    val gMax: Int = 255,
    /** Lower bound for the blue channel `[0, 255]`. */
    val bMin: Int = 0,
    /** Upper bound for the blue channel `[0, 255]`. */
    val bMax: Int = 255,
    override val error: String = "",
) : NodeMetaData {
    /**
     * Returns the centre of the configured RGB bounding box as a packed ARGB
     * `Long` (`0xFFRRGGBB`) suitable for use as a Compose colour. Used by the
     * editor to render a one-glance swatch of "what colour does this trigger
     * approximately match".
     */
    fun midpointArgb(): Long {
        val r = ((rMin + rMax) / 2).coerceIn(0, 255)
        val g = ((gMin + gMax) / 2).coerceIn(0, 255)
        val b = ((bMin + bMax) / 2).coerceIn(0, 255)
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
