package com.xnotes.core.geometry

/**
 * Which ways a drawing is mirrored inside its box: left to right ([x]), upside down ([y]), both or
 * neither. It mirrors about the box's centre lines, so the box itself stays put. A file names one
 * by [id], the way PowerPoint's flipH/flipV and ODF's mirror-horizontal/vertical store it.
 */
enum class Flip(val x: Boolean, val y: Boolean, val id: String) {
    NONE(false, false, ""),
    X(true, false, "x"),
    Y(false, true, "y"),
    XY(true, true, "xy");

    /** This flip with [other]'s axes mirrored once more. */
    fun toggled(other: Flip): Flip = of(x != other.x, y != other.y)

    /** The axes this flip shares with [other]. */
    fun and(other: Flip): Flip = of(x && other.x, y && other.y)

    /** [p] mirrored about [box]'s centre lines. */
    fun map(p: Pt, box: Rect): Pt = Pt(if (x) box.left + box.right - p.x else p.x, if (y) box.top + box.bottom - p.y else p.y)

    /** [r] mirrored about [box]'s centre lines. */
    fun map(r: Rect, box: Rect): Rect = if (this == NONE) r else Rect.fromPoints(map(r.topLeft, box), map(Pt(r.right, r.bottom), box))

    /** The direction [v] mirrored. */
    fun direction(v: Pt): Pt = Pt(if (x) -v.x else v.x, if (y) -v.y else v.y)

    companion object {
        fun of(x: Boolean, y: Boolean): Flip = when {
            x && y -> XY
            x -> X
            y -> Y
            else -> NONE
        }

        /** The flip a file names by [id]; none for an id this version does not know. */
        fun fromId(id: String?): Flip = entries.firstOrNull { it != NONE && it.id == id } ?: NONE
    }
}
