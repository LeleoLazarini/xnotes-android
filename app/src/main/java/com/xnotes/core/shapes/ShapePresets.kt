package com.xnotes.core.shapes

/** A chip in the shape picker: a kind, with its own point count, knob values and heads. */
class ShapePreset(
    val key: String,
    val kind: ShapeKind,
    val n: Int = kind.defaultN,
    val adj: DoubleArray = kind.defaultAdj,
    val headStart: Head = kind.defaultHeadStart,
    val headEnd: Head = kind.defaultHeadEnd,
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

    val basic = ShapePage(
        "basic",
        listOf(
            ShapePreset("line", ShapeKind.LINE),
            ShapePreset("arrow", ShapeKind.ARROW),
            ShapePreset("rectangle", ShapeKind.RECTANGLE),
            ShapePreset("ellipse", ShapeKind.ELLIPSE),
            ShapePreset("circle", ShapeKind.CIRCLE),
            ShapePreset("triangle", ShapeKind.TRIANGLE),
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
