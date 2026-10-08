package com.xnotes.core.tools

import com.xnotes.core.model.Rgba
import com.xnotes.core.shapes.ShapeKind

/** The style carried by the shape tool (spec 04 §6); persisted between sessions. */
data class ShapeConfig(
    val shape: ShapeKind = ShapeKind.RECTANGLE,
    val strokeWidth: Double = 3.0,
    val fill: Boolean = false,
    /** Opacity of the fill in [0, 1], applied to the ink colour; used only when [fill]. */
    val fillAlpha: Double = FILL_ALPHA,
    /** Neon glow: a luminous halo + white-hot core on the shape's outline. */
    val neon: Boolean = false,
    /** Glow intensity in [0, 1] (halo size + brightness); used only when [neon]. */
    val neonStrength: Double = 0.6,
    /** Dashed outline: dash/gap run lengths in content px; used only when [dashed]. */
    val dashed: Boolean = false,
    val dashLength: Double = 10.0,
    val dashGap: Double = 8.0,
) {
    /** The fill a [kind] outlined in [outline] gets, drawn or snapped; null when unfilled or open. */
    fun fillFor(kind: ShapeKind, outline: Rgba): Rgba? =
        if (fill && kind.isClosed) outline.scaleAlpha(fillAlpha) else null

    companion object {
        /** Default reduced opacity applied to the ink colour when a closed shape is filled. */
        const val FILL_ALPHA = 0.25

        /** Settable fill-opacity range; never fully transparent, never fully opaque. */
        const val FILL_ALPHA_MIN = 0.05
        const val FILL_ALPHA_MAX = 1.0
    }
}
