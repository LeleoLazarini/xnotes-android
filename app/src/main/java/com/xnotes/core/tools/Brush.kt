package com.xnotes.core.tools

/**
 * What the textured pen ([Tool.BRUSH]) lays down. The grain kinds fill the ribbon with paper
 * grain whose coverage follows pressure; the rest are their own painters. [id] is stored in the
 * note, so never rename one without a migration.
 */
enum class Brush(val id: String, val grain: Boolean) {
    /** Graphite: fine paper tooth, light pressure catches only its peaks. */
    PENCIL("pencil", grain = true),

    /** Wax crayon: coarse tooth, heavy coverage even when pressed lightly. */
    CRAYON("crayon", grain = true),

    /** Chalk on a board: coarse, high-contrast, broken edges. */
    CHALK("chalk", grain = true),

    /** Translucent wash with a darker drying edge; washes mix where they cross. */
    WATERCOLOR("watercolor", grain = false),

    /** Airbrush: a cloud of fine dots around the line. */
    SPRAY("spray", grain = false),

    /** The hue cycles along the line as it is drawn. */
    RAINBOW("rainbow", grain = false),

    /** Gouache or oil: opaque paint with the streaks of the bristles along it. */
    GOUACHE("gouache", grain = false),

    /** Airbrush: soft paint fading out at its edges. */
    AIRBRUSH("airbrush", grain = false),

    /** Stamps repeated along the line. */
    STAMP_STAR("stamp_star", grain = false),
    STAMP_HEART("stamp_heart", grain = false),
    STAMP_DOTS("stamp_dots", grain = false),
    STAMP_HATCH("stamp_hatch", grain = false);

    /** Paints in one pass from start to end, so it is never baked in runs. */
    val wholeStrokeOnly: Boolean get() = this == WATERCOLOR || this == GOUACHE || this == AIRBRUSH || isStamp

    val isStamp: Boolean get() = this == STAMP_STAR || this == STAMP_HEART || this == STAMP_DOTS || this == STAMP_HATCH

    companion object {
        fun fromId(id: String?): Brush? = entries.firstOrNull { it.id == id }
    }
}
