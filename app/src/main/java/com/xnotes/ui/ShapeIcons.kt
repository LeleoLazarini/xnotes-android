package com.xnotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.shapes.Head
import com.xnotes.core.shapes.PathRole
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.shapes.ShapePath
import com.xnotes.core.shapes.ShapePreset
import com.xnotes.core.vector.VectorSeg

/**
 * Picker glyphs drawn from each kind's own geometry, so a chip always shows exactly what the kind
 * draws. They match the line-icon set: a 24 dp square, a 2 dp round stroke, no fill. The geometry
 * is built on a 48 unit grid so an arrowhead or a corner keeps its proportion at icon size.
 */
object ShapeIcons {

    private val cache = HashMap<String, ImageVector>()

    fun of(kind: ShapeKind, n: Int = kind.defaultN, adj: DoubleArray = kind.defaultAdj): ImageVector =
        cache.getOrPut("${kind.id}/$n/${adj.joinToString(",")}") { glyph(kind, n, adj, null) }

    /** A chip's glyph, a free kind's drawn from the outline the chip carries for it. */
    fun of(preset: ShapePreset): ImageVector =
        if (preset.iconPath == null) of(preset.kind, preset.n, preset.adj)
        else cache.getOrPut("preset/${preset.key}") { glyph(preset.kind, preset.n, preset.adj, preset.iconPath) }

    private fun glyph(kind: ShapeKind, n: Int, adj: DoubleArray, path: List<com.xnotes.core.vector.VectorContour>?): ImageVector {
        val box = boxFor(kind)
        val start = if (kind.isEndpointShape) Pt(box.left, box.bottom) else box.topLeft
        val end = if (kind.isEndpointShape) Pt(box.right, box.top) else Pt(box.right, box.bottom)
        return build("shape-${kind.id}", ShapeItem(kind, start, end, Rgba(255, 255, 255), STROKE, path = path, adj = adj.copyOf(), n = n))
    }

    /** A head picker's glyph: a short line ending in [head], at its left end for a start picker. */
    fun head(head: Head, atStart: Boolean): ImageVector =
        cache.getOrPut("head/${head.id}/$atStart") {
            val line = ShapeItem(
                ShapeKind.LINE, Pt(8.0, 24.0), Pt(40.0, 24.0), Rgba(255, 255, 255), STROKE,
                headStart = if (atStart) head else Head.NONE, headEnd = if (atStart) Head.NONE else head,
            )
            build("head-${head.id}", line)
        }

    private fun build(name: String, item: ShapeItem): ImageVector {
        val paths = item.paths()
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
        )
        builder.addPath(
            pathData = nodes(paths.filter { it.role.strokes }),
            stroke = SolidColor(Color.White),
            strokeLineWidth = STROKE.toFloat(),
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        // A filled head is solid in the glyph too.
        val solid = paths.filter { it.role == PathRole.HEAD_FILLED }
        if (solid.isNotEmpty()) builder.addPath(pathData = nodes(solid), fill = SolidColor(Color.White))
        return builder.build()
    }

    private fun nodes(paths: List<ShapePath>) = PathBuilder().apply {
        for (path in paths) {
            for (c in path.contours) {
                moveTo(c.start.x.toFloat(), c.start.y.toFloat())
                for (s in c.segments) when (s) {
                    is VectorSeg.Line -> lineTo(s.end.x.toFloat(), s.end.y.toFloat())
                    is VectorSeg.Cubic -> curveTo(
                        s.c1.x.toFloat(), s.c1.y.toFloat(), s.c2.x.toFloat(), s.c2.y.toFloat(),
                        s.end.x.toFloat(), s.end.y.toFloat(),
                    )
                }
                if (c.closed) close()
            }
        }
    }.nodes

    /** Where a kind sits on the grid: centred, as big as its aspect allows inside 38 by 34. */
    private fun boxFor(kind: ShapeKind): Rect {
        if (kind.isEndpointShape) return Rect(10.0, 10.0, 28.0, 28.0)
        val aspect = kind.iconAspect.coerceIn(0.3, 3.0)
        val h = minOf(MAX_W / aspect, MAX_H)
        val w = h * aspect
        return Rect((GRID - w) / 2.0, (GRID - h) / 2.0, w, h)
    }

    private const val GRID = 48f
    private const val STROKE = 4.0
    private const val MAX_W = 38.0
    private const val MAX_H = 34.0
}
