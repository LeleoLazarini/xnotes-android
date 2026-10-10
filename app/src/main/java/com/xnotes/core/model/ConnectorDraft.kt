package com.xnotes.core.model

import com.xnotes.core.geometry.Pt

/**
 * A connector being drawn. Its start stays where the pen came down, glued to the connection point
 * it landed on, if any; its far end follows the pen and glues to whatever point it comes near. Each
 * step routes the line afresh around the glue targets in its way: this is when a connector is
 * smart. Once it is down it only ever follows what it joins.
 */
class ConnectorDraft(val connector: ConnectorItem) {

    /** The point the far end is glued to now, for the chrome to light up; null while it is loose. */
    var hot: Glue.Hit? = null
        private set

    /**
     * Follow the pen to [at]: glue to the nearest connection point of [items] within [reach] (never
     * the point it set off from), then route around [items] keeping [margin] clear.
     */
    fun drag(at: Pt, items: List<CanvasItem>, reach: Double, margin: Double) {
        val c = connector
        val hit = Glue.nearest(items, at, reach, skip = c)?.takeUnless { it.target === c.start.target && it.index == c.start.point }
        hot = hit
        c.end = hit?.let { ConnectorEnd(it.point.at, it.target, it.index) } ?: ConnectorEnd(at)
        Glue.reroute(c, items, margin)
    }

    /** True when what was drawn is worth keeping: it joins two points, or runs at least [minLength]. */
    fun worthKeeping(minLength: Double): Boolean {
        val c = connector
        val joins = c.start.glued && c.end.glued && !(c.start.target === c.end.target && c.start.point == c.end.point)
        return joins || c.start.at.distanceTo(c.end.at) >= minLength
    }

    companion object {
        /** Start a draft at [at]: glued to the nearest connection point of [items] within [reach], else loose. */
        fun startAt(at: Pt, items: List<CanvasItem>, reach: Double, make: (ConnectorEnd) -> ConnectorItem?): ConnectorDraft? {
            val hit = Glue.nearest(items, at, reach)
            val from = hit?.let { ConnectorEnd(it.point.at, it.target, it.index) } ?: ConnectorEnd(at)
            return make(from)?.let(::ConnectorDraft)
        }
    }
}
