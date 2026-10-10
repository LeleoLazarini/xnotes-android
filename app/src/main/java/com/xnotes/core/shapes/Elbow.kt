package com.xnotes.core.shapes

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import kotlin.math.abs

/** A page axis: the way a square-cornered segment runs. */
enum class Axis {
    H, V;

    val other: Axis get() = if (this == H) V else H

    /** [p]'s coordinate along this axis. */
    fun along(p: Pt): Double = if (this == H) p.x else p.y

    /** [p]'s coordinate across this axis: where a segment running along it sits. */
    fun across(p: Pt): Double = if (this == H) p.y else p.x

    /** The unit step along this axis, forward when [sign] is positive. */
    fun unit(sign: Double): Pt = if (this == H) Pt(if (sign < 0.0) -1.0 else 1.0, 0.0) else Pt(0.0, if (sign < 0.0) -1.0 else 1.0)

    companion object {
        /** The axis [d] mostly runs along; across when it runs along neither. */
        fun of(d: Pt): Axis = if (abs(d.x) >= abs(d.y)) H else V
    }
}

/**
 * A square-cornered route between two ends, kept so that it stays square whatever the ends do. The
 * first segment runs along [first] from the start, and each of [coords] is where the next segment
 * sits across its own axis: alternately an x and a y, an x first when [first] is H. The first and
 * last segments sit where the ends put them, so moving an end changes no coordinate and sliding a
 * middle segment changes exactly one. Immutable.
 */
class Elbow(val first: Axis, val coords: DoubleArray) {

    /** How many segments the route has: two more than it has coordinates. */
    val segments: Int get() = coords.size + 2

    /** The axis segment [i] runs along. */
    fun axisOf(i: Int): Axis = if (i % 2 == 0) first else first.other

    /** The axis the last segment runs along. */
    val last: Axis get() = axisOf(coords.size + 1)

    /** The route from [from] to [to]: the two ends and the corner between each pair of segments. */
    fun points(from: Pt, to: Pt): List<Pt> {
        val out = ArrayList<Pt>(coords.size + 3)
        out += from
        var axis = first
        var at = axis.across(from)
        for (i in 0..coords.size) {
            val next = axis.other
            val nextAt = if (i < coords.size) coords[i] else next.across(to)
            out += if (axis == Axis.H) Pt(nextAt, at) else Pt(at, nextAt)
            axis = next
            at = nextAt
        }
        out += to
        return out
    }

    /** The same route traced from its other end. */
    fun reversed(): Elbow = Elbow(last, coords.reversedArray())

    /** This route with middle segment [segment] (1 to the coordinate count) moved across to [at]. */
    fun withCoord(segment: Int, at: Double): Elbow {
        if (segment < 1 || segment > coords.size) return this
        return Elbow(first, coords.copyOf().also { it[segment - 1] = at })
    }

    /** This route moved by ([dx], [dy]): each middle segment by the part of the move across it. */
    fun translated(dx: Double, dy: Double): Elbow =
        Elbow(first, DoubleArray(coords.size) { coords[it] + if (axisOf(it + 1) == Axis.H) dy else dx })

    /**
     * This route, from [from] to [to], under [t]. The segments stay square to the page: each middle
     * one sits where [t] puts its midpoint, and every axis swaps when [t] turns the horizontal
     * mostly vertical, so a quarter turn turns the route exactly.
     */
    fun transformed(t: Affine, from: Pt, to: Pt): Elbow {
        val pts = points(from, to)
        val swap = abs(t.b) > abs(t.a)
        val turned = if (swap) first.other else first
        val out = DoubleArray(coords.size) { j ->
            val i = j + 1
            val mid = t.apply((pts[i] + pts[i + 1]) * 0.5)
            (if (i % 2 == 0) turned else turned.other).across(mid)
        }
        return Elbow(turned, out)
    }

    override fun equals(other: Any?): Boolean = other is Elbow && other.first == first && other.coords.contentEquals(coords)

    override fun hashCode(): Int = first.hashCode() * 31 + coords.contentHashCode()

    companion object {
        /** A single bend from the start along the horizontal: the plainest route. */
        val PLAIN = Elbow(Axis.H, DoubleArray(0))

        /**
         * The route a square-cornered [path] takes, from its first point to its last. A path with no
         * bend at all keeps a middle segment of no length halfway along, which grows into a step if
         * an end later moves across it.
         */
        fun of(path: List<Pt>): Elbow {
            val pts = corners(path)
            if (pts.size < 3) {
                val a = pts.firstOrNull() ?: Pt.ZERO
                val b = pts.lastOrNull() ?: a
                val axis = Axis.of(b - a)
                return Elbow(axis, doubleArrayOf((axis.along(a) + axis.along(b)) / 2.0))
            }
            val first = Axis.of(pts[1] - pts[0])
            return Elbow(first, DoubleArray(pts.size - 3) { j -> (if ((j + 1) % 2 == 0) first else first.other).across(pts[j + 1]) })
        }

        /** [path] without repeated points or points in the middle of a straight run. */
        fun corners(path: List<Pt>): List<Pt> {
            val out = ArrayList<Pt>(path.size)
            for (p in path) {
                if (out.isNotEmpty() && out.last().distanceTo(p) < EPS) continue
                if (out.size >= 2 && Axis.of(out.last() - out[out.size - 2]) == Axis.of(p - out.last())) {
                    out[out.size - 1] = p
                    if (out.last().distanceTo(out[out.size - 2]) < EPS) out.removeAt(out.size - 1)
                    continue
                }
                out += p
            }
            return out
        }

        private const val EPS = 1e-9
    }
}
