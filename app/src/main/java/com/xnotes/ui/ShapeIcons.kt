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
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.vector.VectorSeg

/**
 * Picker glyphs drawn from each kind's own geometry, so a chip always shows exactly what the kind
 * draws. They match the line-icon set: a 24 dp square, a 2 dp round stroke, no fill. The geometry
 * is built on a 48 unit grid so an arrowhead or a corner keeps its proportion at icon size.
 */
object ShapeIcons {

    private val cache = HashMap<String, ImageVector>()

    fun of(kind: ShapeKind, n: Int = kind.defaultN, adj: DoubleArray = kind.defaultAdj): ImageVector =
        cache.getOrPut("${kind.id}/$n/${adj.joinToString(",")}") { build(kind, n, adj) }

    private fun build(kind: ShapeKind, n: Int, adj: DoubleArray): ImageVector {
        val box = boxFor(kind)
        val start = if (kind.isEndpointShape) Pt(box.left, box.bottom) else box.topLeft
        val end = if (kind.isEndpointShape) Pt(box.right, box.top) else Pt(box.right, box.bottom)
        val item = ShapeItem(kind, start, end, Rgba(255, 255, 255), STROKE, adj = adj.copyOf(), n = n)
        val nodes = PathBuilder().apply {
            for (path in item.paths()) {
                if (!path.role.strokes) continue
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
        return ImageVector.Builder(
            name = "shape-${kind.id}",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
        ).addPath(
            pathData = nodes,
            stroke = SolidColor(Color.White),
            strokeLineWidth = STROKE.toFloat(),
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    /** Where each kind sits on the grid: the proportions the hand-drawn glyphs had. */
    private fun boxFor(kind: ShapeKind): Rect = when (kind) {
        ShapeKind.LINE, ShapeKind.ARROW -> Rect(10.0, 10.0, 28.0, 28.0)
        ShapeKind.RECTANGLE -> Rect(8.0, 12.0, 32.0, 24.0)
        ShapeKind.ELLIPSE -> Rect(6.0, 12.0, 36.0, 24.0)
        ShapeKind.TRIANGLE -> Rect(6.0, 8.0, 36.0, 30.0)
        else -> Rect(8.0, 8.0, 32.0, 32.0)
    }

    private const val GRID = 48f
    private const val STROKE = 4.0
}
