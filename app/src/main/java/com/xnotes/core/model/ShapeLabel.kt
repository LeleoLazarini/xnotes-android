package com.xnotes.core.model

import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.TextFlags
import com.xnotes.core.pal.TextMeasurer

/**
 * A shape's typed label: plain text in a text box's [style], wrapped to the kind's text rect and
 * centred on it, turning with the shape. Immutable, so an edit swaps in a new one and undo can hold
 * both. The [measurer] lays it out exactly as the screen and the PDF set it.
 */
class ShapeLabel(val text: String, val style: TextStyle, val measurer: TextMeasurer) {

    val font: FontSpec get() = FontSpec(style.pointSize, style.face)

    /** One line's height, which also bounds how narrow the label may wrap. */
    val lineHeight: Double by lazy { measurer.lineHeight(font) }

    @Volatile
    private var measured: Measured? = null

    private class Measured(val width: Double, val height: Double)

    /** How tall the text sets wrapped at [width]; the last answer is kept, since every repaint asks. */
    fun height(width: Double): Double {
        measured?.let { if (it.width == width) return it.height }
        val h = measurer.measure(text.ifEmpty { " " }, font, width, FLAGS).h
        measured = Measured(width, h)
        return h
    }

    fun withText(text: String) = ShapeLabel(text, style, measurer)

    fun withStyle(style: TextStyle) = ShapeLabel(text, style, measurer)

    /** Same words in the same style: whether an edit changed anything. */
    fun sameAs(other: ShapeLabel?): Boolean = other != null && other.text == text && other.style == style

    companion object {
        /** Wrapped, every line centred. */
        val FLAGS = TextFlags(wordWrap = true, alignLeft = false, alignTop = true)

        /** The narrowest a label wraps, in lines of its own height, however thin its shape. */
        const val MIN_WIDTH_LINES = 2.0
    }
}
