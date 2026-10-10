package com.xnotes.core.model

import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.TextFlags
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.core.vector.VectorContour

/**
 * A shape's typed label: plain text in a text box's [style], wrapped to the kind's text rect and
 * centred on it, turning with the shape. Immutable, so an edit swaps in a new one and undo can hold
 * both. The [measurer] lays it out exactly as the screen and the PDF set it.
 */
class ShapeLabel(val text: String, val style: TextStyle, val measurer: TextMeasurer) {

    val font: FontSpec get() = FontSpec(style.pointSize, style.face)

    /** One line's height, which also bounds how narrow the label may wrap. */
    val lineHeight: Double by lazy { measurer.lineHeight(font) }

    /** How wide the text sets unwrapped: its longest line. */
    val naturalWidth: Double by lazy { text.split('\n').maxOf { measurer.advances(it, font).sum() } }

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

    @Volatile
    private var outlined: Outlined? = null

    private class Outlined(val width: Double, val contours: List<VectorContour>)

    /** The text as glyph outlines wrapped at [width], from its block's top-left; the last answer is kept. */
    fun outline(width: Double): List<VectorContour> {
        outlined?.let { if (it.width == width) return it.contours }
        val c = measurer.outline(text, font, width, FLAGS)
        outlined = Outlined(width, c)
        return c
    }

    fun withText(text: String) = ShapeLabel(text, style, measurer)

    fun withStyle(style: TextStyle) = ShapeLabel(text, style, measurer)

    /** Same words in the same style: whether an edit changed anything. */
    fun sameAs(other: ShapeLabel?): Boolean = other != null && other.text == text && other.style == style

    companion object {
        /**
         * A new label's style for [shape]: the given type, in the outline's colour, unless a solid
         * fill of that colour would swallow it, when black or white reads better on the fill.
         */
        fun styleFor(shape: ShapeItem, pointSize: Double, face: FontFace): TextStyle {
            val outline = shape.strokeRgba.withAlpha(255)
            val fill = shape.fillRgba
            val swallowed = fill != null && fill.a >= SOLID_FILL_ALPHA && fill.r == outline.r && fill.g == outline.g && fill.b == outline.b
            val color = if (!swallowed) outline else if (outline.luminance() > 0.55) Rgba(0, 0, 0) else Rgba(255, 255, 255)
            return TextStyle(color, pointSize, face)
        }

        /** A fill at least this opaque hides text in its own colour. */
        private const val SOLID_FILL_ALPHA = 160

        /** Wrapped, every line centred. */
        val FLAGS = TextFlags(wordWrap = true, alignLeft = false, alignTop = true)

        /** The narrowest a label wraps, in lines of its own height, however thin its shape. */
        const val MIN_WIDTH_LINES = 2.0
    }
}
