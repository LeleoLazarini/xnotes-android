package com.xnotes.core.shapes

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.vector.PathFlattener
import com.xnotes.core.vector.VectorContour
import com.xnotes.core.vector.VectorSeg

/**
 * The stored outline of a free shape (polygon, polyline, curve): contours of lines and cubics kept
 * normalized to the shape's box, so resizing the box reshapes the outline for free and an affine
 * map of the box maps every curve exactly.
 */
object FreePath {

    /** A contour of straight edges through [points]. */
    fun lines(points: List<Pt>, closed: Boolean): VectorContour = ShapePath.lines(points, closed)

    /** [contours] moved by [f], control points included. */
    fun map(contours: List<VectorContour>, f: (Pt) -> Pt): List<VectorContour> =
        ShapePath(contours).mapped(f).contours

    /** Page-space [contours] as fractions of [box]. A box with no width or height keeps that axis as is. */
    fun normalize(contours: List<VectorContour>, box: Rect): List<VectorContour> {
        val w = if (box.w > 1e-9) box.w else 1.0
        val h = if (box.h > 1e-9) box.h else 1.0
        return map(contours) { Pt((it.x - box.left) / w, (it.y - box.top) / h) }
    }

    /** Normalized [contours] placed in [box]. */
    fun place(contours: List<VectorContour>, box: Rect): List<VectorContour> =
        map(contours) { Pt(box.left + it.x * box.w, box.top + it.y * box.h) }

    /** The points the contour passes through: its start and every segment's end, a closing repeat dropped. */
    fun vertices(c: VectorContour): List<Pt> {
        val out = ArrayList<Pt>(c.segments.size + 1)
        out.add(c.start)
        for (s in c.segments) out.add(s.end)
        if (c.closed && out.size > 1 && out.last().distanceTo(out.first()) < 1e-12) out.removeAt(out.size - 1)
        return out
    }

    /** True for one contour of straight edges: what the plain vertex list of older files can hold. */
    fun isVertexList(contours: List<VectorContour>): Boolean =
        contours.size == 1 && contours[0].segments.all { it is VectorSeg.Line }

    /** The tight bounds of what [contours] draw: curves flattened, so control points never pad them. */
    fun bounds(contours: List<VectorContour>): Rect? {
        val pts = contours.flatMap { PathFlattener.flatten(it, BOUNDS_TOLERANCE) }
        return if (pts.isEmpty()) null else Rect.bounding(pts)
    }

    /** [contours] as SVG path data (absolute M, L, C and Z), the form files store a free outline in. */
    fun toSvg(contours: List<VectorContour>): String {
        val sb = StringBuilder()
        for (c in contours) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append('M').append(num(c.start.x)).append(',').append(num(c.start.y))
            for (s in c.segments) {
                when (s) {
                    is VectorSeg.Line -> sb.append(" L").append(num(s.end.x)).append(',').append(num(s.end.y))
                    is VectorSeg.Cubic -> sb.append(" C").append(num(s.c1.x)).append(',').append(num(s.c1.y))
                        .append(' ').append(num(s.c2.x)).append(',').append(num(s.c2.y))
                        .append(' ').append(num(s.end.x)).append(',').append(num(s.end.y))
                }
            }
            if (c.closed) sb.append(" Z")
        }
        return sb.toString()
    }

    /** SVG path data written by [toSvg] read back; null when it holds nothing usable. */
    fun fromSvg(data: String?): List<VectorContour>? {
        if (data.isNullOrBlank()) return null
        val tokens = TOKEN.findAll(data).map { it.value }.toList()
        val out = ArrayList<VectorContour>()
        var start: Pt? = null
        var segs = ArrayList<VectorSeg>()
        var i = 0
        fun flush(closed: Boolean) {
            start?.let { out.add(VectorContour(it, segs, closed)) }
            start = null
            segs = ArrayList()
        }
        fun num(): Double? = tokens.getOrNull(i++)?.toDoubleOrNull()
        fun pt(): Pt? {
            val x = num() ?: return null
            val y = num() ?: return null
            return if (x.isFinite() && y.isFinite()) Pt(x, y) else null
        }
        while (i < tokens.size) {
            val cmd = tokens[i++]
            if (cmd != "M" && start == null) return null // every contour opens with a move
            when (cmd) {
                "M" -> {
                    flush(false)
                    start = pt() ?: return null
                }
                "L" -> segs.add(VectorSeg.Line(pt() ?: return null))
                "C" -> {
                    val c1 = pt() ?: return null
                    val c2 = pt() ?: return null
                    segs.add(VectorSeg.Cubic(c1, c2, pt() ?: return null))
                }
                "Z" -> flush(true)
                else -> return null
            }
        }
        flush(false)
        return out.takeIf { it.isNotEmpty() }
    }

    /** Six places is a millionth of the box: far finer than a pixel on any box that fits a screen. */
    private fun num(v: Double): String {
        val r = Math.round(v * 1e6) / 1e6
        return if (r == Math.rint(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    private val TOKEN = Regex("[MLCZ]|-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?")

    private const val BOUNDS_TOLERANCE = 0.05
}
