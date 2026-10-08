package com.xnotes.core.shapes

import com.xnotes.core.geometry.Pt

/** A chip in the shape picker: a kind, with its own point count, knob values and heads. */
class ShapePreset(
    val key: String,
    val kind: ShapeKind,
    val n: Int = kind.defaultN,
    val adj: DoubleArray = kind.defaultAdj,
    val headStart: Head = kind.defaultHeadStart,
    val headEnd: Head = kind.defaultHeadEnd,
    /** For a free kind, the outline its chip shows, normalized to the icon's box. */
    val iconPath: List<com.xnotes.core.vector.VectorContour>? = null,
) {
    /** True when the picker offers a minus/plus for this chip's point or side count. */
    val counted: Boolean get() = kind.nRange != null
}

/** One page of the picker: a family's caption key and up to [ShapePresets.ROWS] rows of chips. */
class ShapePage(val key: String, val presets: List<ShapePreset>)

/** The chips the shape picker offers, page by page. */
object ShapePresets {

    const val ROWS = 4
    const val COLUMNS = 6

    /** The key of the chip that places a polygon corner by corner rather than by dragging a box. */
    const val FREE_POLYGON = "free_polygon"

    val basic = ShapePage(
        "basic",
        listOf(
            ShapePreset("line", ShapeKind.LINE),
            ShapePreset("arrow", ShapeKind.ARROW),
            ShapePreset("rectangle", ShapeKind.RECTANGLE),
            ShapePreset("rounded_rect", ShapeKind.ROUNDED_RECT),
            ShapePreset("square", ShapeKind.SQUARE),
            ShapePreset("capsule", ShapeKind.CAPSULE),
            ShapePreset("ellipse", ShapeKind.ELLIPSE),
            ShapePreset("circle", ShapeKind.CIRCLE),
            ShapePreset("triangle", ShapeKind.TRIANGLE),
            ShapePreset("right_triangle", ShapeKind.RIGHT_TRIANGLE),
            ShapePreset("diamond", ShapeKind.DIAMOND),
            ShapePreset("parallelogram", ShapeKind.PARALLELOGRAM),
            ShapePreset("trapezoid", ShapeKind.TRAPEZOID),
            ShapePreset("pentagon", ShapeKind.REGULAR_POLYGON, n = 5),
            ShapePreset("hexagon", ShapeKind.REGULAR_POLYGON, n = 6),
            ShapePreset("octagon", ShapeKind.REGULAR_POLYGON, n = 8),
            ShapePreset("star", ShapeKind.STAR),
            ShapePreset("cross", ShapeKind.CROSS),
            ShapePreset("heart", ShapeKind.HEART),
            ShapePreset("cloud", ShapeKind.CLOUD),
            ShapePreset("ring", ShapeKind.RING),
            ShapePreset("pie", ShapeKind.PIE),
            ShapePreset("arc", ShapeKind.ARC),
            ShapePreset(
                FREE_POLYGON, ShapeKind.POLYGON,
                iconPath = listOf(
                    FreePath.lines(listOf(Pt(0.05, 0.75), Pt(0.3, 0.05), Pt(0.8, 0.25), Pt(0.95, 0.8), Pt(0.45, 0.95)), closed = true),
                ),
            ),
        ),
    )

    val pages: List<ShapePage> = listOf(basic)

    val all: List<ShapePreset> = pages.flatMap { it.presets }

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String?): ShapePreset? = key?.let { byKey[it] }

    /** The chip a setting written before chips existed names by its bare kind. */
    fun forKind(kind: ShapeKind): ShapePreset = all.firstOrNull { it.kind == kind } ?: byKey.getValue("rectangle")

    /** The page [preset] sits on. */
    fun pageOf(preset: ShapePreset): Int = pages.indexOfFirst { preset in it.presets }.coerceAtLeast(0)
}
