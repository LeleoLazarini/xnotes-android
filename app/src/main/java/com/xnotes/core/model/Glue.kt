package com.xnotes.core.model

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.shapes.ConnectionPoint
import com.xnotes.core.shapes.ShapeKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where connectors glue: the connection points every kind of target offers, in page space with the
 * ways out of it there, and the one a pen is nearest. A connector holds its targets by reference, so
 * nothing here needs an id; files give them ids as they are written.
 */
object Glue {

    /** The points a connector may glue to on [item], with their outward normals; none for ink. */
    fun points(item: CanvasItem): List<ConnectionPoint> = when (item) {
        is ShapeItem -> if (item.shape == ShapeKind.GLYPHS) emptyList() else item.connectionPoints()
        is TextItem -> box(item.bounds(), 0.0)
        is ImageItem -> box(item.rect, item.angle)
        is ConnectorItem -> item.connectionPoints()
        else -> emptyList()
    }

    /** Connection point [index] of [item], or null when it has no such point (any more). */
    fun point(item: CanvasItem, index: Int): ConnectionPoint? = if (index < 0) null else points(item).getOrNull(index)

    /** Whether a connector can glue to [item] at all. */
    fun accepts(item: CanvasItem): Boolean = when (item) {
        is TextItem, is ImageItem, is ConnectorItem -> true
        is ShapeItem -> points(item).isNotEmpty()
        else -> false
    }

    /** A connection point a pen picked: on [target], its point [index], at [point]. */
    class Hit(val target: CanvasItem, val index: Int, val point: ConnectionPoint)

    /**
     * The connection point nearest [p] within [reach] on any of [items] (back to front, so the
     * topmost wins a tie), leaving out [skip] and the connectors hanging from it, so no connector
     * glues to itself or back onto one glued to it. Null when none is close enough.
     */
    fun nearest(items: List<CanvasItem>, p: Pt, reach: Double, skip: CanvasItem? = null): Hit? {
        var best: Hit? = null
        var bestDist = reach
        for (item in items) {
            if (item === skip || (skip != null && item is ConnectorItem && (item.start.target === skip || item.end.target === skip))) continue
            if (item.bounds().distanceTo(p) > reach) continue
            for ((i, cp) in points(item).withIndex()) {
                val d = cp.at.distanceTo(p)
                if (d <= bestDist) {
                    bestDist = d
                    best = Hit(item, i, cp)
                }
            }
        }
        return best
    }

    /** A box's corners and edge middles, turned by [angle] about its centre. */
    private fun box(r: Rect, angle: Double): List<ConnectionPoint> {
        val corners = listOf(r.topLeft, Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))
        if (angle == 0.0) return ShapeKind.polygonConnections(corners)
        val c = r.center
        val cs = cos(angle)
        val sn = sin(angle)
        return ShapeKind.polygonConnections(corners.map { Pt(c.x + (it.x - c.x) * cs - (it.y - c.y) * sn, c.y + (it.x - c.x) * sn + (it.y - c.y) * cs) })
    }
}
