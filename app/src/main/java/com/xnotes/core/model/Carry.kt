package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.vector.PathFlattener

/**
 * What closed shapes take along when they move, turn or resize: everything entirely inside their
 * outline, so a frame, a swimlane or a nested box keeps its contents. A move or a turn carries the
 * contents rigidly (a text box stays upright and only its place turns); a resize keeps each item
 * its own size and puts its centre back at the same place in its holder's frame, so writing stays
 * centred in a widened box. Holds each item's gesture-start geometry, so every frame is one move
 * of the original.
 */
class Carry private constructor(private val entries: List<Entry>) {

    private class Entry(val item: CanvasItem, val holder: ShapeItem, val u: Double, val v: Double, val start: GeometrySnapshot)

    val items: List<CanvasItem> = entries.map { it.item }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** The shape that carries [item], or null when it is not carried. */
    fun holderOf(item: CanvasItem): ShapeItem? = entries.firstOrNull { it.item === item }?.holder

    /** The carried items' geometry as the gesture found it, in [items] order. */
    val startSnapshots: List<GeometrySnapshot> get() = entries.map { it.start }

    /** Put every carried item back where the gesture began. */
    fun restore() {
        for (e in entries) e.item.restoreGeometry(e.start)
    }

    /**
     * Follow holders that this frame restored and then moved by [t]: rigidly, or for a [resize]
     * keeping each item's size and its centre's place in its holder's new frame.
     */
    fun follow(t: Affine, resize: Boolean) {
        for (e in entries) {
            val item = e.item
            item.restoreGeometry(e.start)
            when {
                resize -> {
                    val b = e.holder.box
                    val to = e.holder.toPage(Pt(b.left + e.u * b.w, b.top + e.v * b.h))
                    val c = item.bounds().center
                    item.translate(to.x - c.x, to.y - c.y)
                }
                item is TextItem -> {
                    val c = item.bounds().center
                    val to = t.apply(c)
                    item.translate(to.x - c.x, to.y - c.y)
                }
                else -> item.applyTransform(t)
            }
        }
    }

    /** Move every carried item by ([dx], [dy]) from where the gesture began. */
    fun translate(dx: Double, dy: Double) {
        for (e in entries) {
            e.item.restoreGeometry(e.start)
            e.item.translate(dx, dy)
        }
    }

    companion object {
        val NONE = Carry(emptyList())

        /**
         * The [candidates] entirely inside one of [holders]' outlines, each going with the
         * innermost holder that has it. Ink counts by all its points, a shape by its outline and
         * label, anything else by its bounds' corners. Locked items and [skip] (the selection
         * itself) stay where they are.
         */
        fun of(holders: List<CanvasItem>, candidates: List<CanvasItem>, skip: Collection<CanvasItem>): Carry {
            val shapes = holders.filterIsInstance<ShapeItem>().filter { it.shape.isClosed && it.shape != ShapeKind.GLYPHS }
            if (shapes.isEmpty()) return NONE
            val regions = shapes.map { Region(it) }.sortedBy { it.area }
            val skipped = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CanvasItem, Boolean>()).apply { addAll(skip) }
            val out = ArrayList<Entry>()
            for (item in candidates) {
                if (item.locked || item in skipped) continue
                val region = regions.firstOrNull { it.holds(item) } ?: continue
                val b = region.shape.box
                val local = region.shape.toLocal(item.bounds().center)
                val u = if (b.w > 0.0) (local.x - b.left) / b.w else 0.5
                val v = if (b.h > 0.0) (local.y - b.top) / b.h else 0.5
                out += Entry(item, region.shape, u, v, item.snapshotGeometry())
            }
            return if (out.isEmpty()) NONE else Carry(out)
        }

        /** How far a curve may stray when flattened to test it, in content px. */
        private const val OUTLINE_FLATTEN = 1.0

        /** The points an item has to have inside a holder to count as in it. */
        private fun pointsOf(item: CanvasItem): List<Pt> = when (item) {
            is Stroke -> item.samples.map { it.pos }
            is ShapeItem -> item.paths().flatMap { p -> p.contours.flatMap { PathFlattener.flatten(it, OUTLINE_FLATTEN) } } + item.labelCorners()
            else -> item.bounds().let { r -> listOf(r.topLeft, Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom)) }
        }

        private class Region(val shape: ShapeItem) {
            private val inside = shape.interior()
            private val bounds: Rect = shape.bounds()
            val area: Double = shape.box.w * shape.box.h

            fun holds(item: CanvasItem): Boolean {
                if (item === shape) return false
                val b = item.bounds()
                if (b.left < bounds.left || b.top < bounds.top || b.right > bounds.right || b.bottom > bounds.bottom) return false
                val pts = pointsOf(item)
                return pts.isNotEmpty() && pts.all(inside)
            }
        }
    }
}
