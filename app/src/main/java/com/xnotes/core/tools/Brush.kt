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
    RAINBOW("rainbow", grain = false);

    companion object {
        fun fromId(id: String?): Brush? = entries.firstOrNull { it.id == id }
    }
}
