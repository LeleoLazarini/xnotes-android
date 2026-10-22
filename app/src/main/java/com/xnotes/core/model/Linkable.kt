package com.xnotes.core.model

/** An item a finger tap can follow to another page of its note (a contents entry, a linked post-it). */
interface Linkable {
    /** The page index the item jumps to, or null when it is not a link. */
    var linkPage: Int?
}
