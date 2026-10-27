package com.xnotes.core.model

/** One layer of a note, bottom first in [Document.layers]: its name, and whether it is hidden or locked. */
data class LayerInfo(var name: String, var hidden: Boolean = false, var locked: Boolean = false)

/** The layer new items are made on: the open note's active layer (0 in a note without layers). */
object Layers {
    @Volatile
    var active: Int = 0
}
