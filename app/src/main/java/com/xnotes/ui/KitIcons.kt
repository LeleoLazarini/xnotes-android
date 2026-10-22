package com.xnotes.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.xnotes.core.tools.PenFamily

/**
 * Full-colour pictures of the kit's drawers: each object drawn upright with the tip down, shaded
 * like a cylinder (light from the left), then laid on the diagonal. The part that leaves ink — the
 * lead, nib, chisel, chalk or bristles — takes [ink], so the toolbar shows what the pen will draw.
 * Built once per drawer and colour.
 */
internal object KitIcons {
    private val cache = HashMap<Pair<PenFamily, Int>, ImageVector>()

    fun get(family: PenFamily, ink: Color): ImageVector = synchronized(cache) {
        cache.getOrPut(family to ink.hashCode()) { build(family, ink) }
    }

    private fun build(f: PenFamily, ink: Color): ImageVector =
        ImageVector.Builder("kit-${f.id}", 24.dp, 24.dp, 100f, 100f).apply {
            group(rotate = 45f, pivotX = 50f, pivotY = 50f) {
                when (f) {
                    PenFamily.PEN -> pen(ink)
                    PenFamily.PENCIL -> pencil(ink)
                    PenFamily.MARKER -> marker(ink)
                    PenFamily.HIGHLIGHTER -> highlighter(ink)
                    PenFamily.CHALK -> chalk(ink)
                    PenFamily.BRUSH -> brush(ink)
                }
            }
            if (f == PenFamily.CHALK) dust(ink)
        }.build()

    /** A pink-and-blue school eraser, lying on the diagonal. */
    val eraser: ImageVector by lazy {
        ImageVector.Builder("kit-eraser", 24.dp, 24.dp, 100f, 100f).apply {
            group(rotate = 45f, pivotX = 50f, pivotY = 50f) {
                shape(outline) { roundTop(33f, 9f, 67f, 91f, 9f) }
                shape(round(Color(0xFFF7A1B3), 34f, 66f)) { roundTop(34f, 10f, 66f, 52f, 8f) }
                shape(round(Color(0xFF5B8DEF), 34f, 66f)) { moveTo(34f, 52f); lineTo(66f, 52f); lineTo(66f, 82f); quadTo(66f, 90f, 58f, 90f); lineTo(42f, 90f); quadTo(34f, 90f, 34f, 82f); close() }
                shape(Color(0x55FFFFFF)) { rect(38f, 16f, 42f, 84f) }
            }
        }.build()
    }

    /** A yellow sticky note with its corner lifting. */
    val postIt: ImageVector by lazy {
        ImageVector.Builder("kit-postit", 24.dp, 24.dp, 100f, 100f).apply {
            shape(Color(0x33000000)) { rect(16f, 18f, 90f, 92f) }
            shape(Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFE45C)), startY = 10f, endY = 88f)) {
                moveTo(10f, 10f); lineTo(86f, 10f); lineTo(86f, 68f); lineTo(66f, 88f); lineTo(10f, 88f); close()
            }
            shape(Color(0x14000000)) { rect(10f, 10f, 86f, 24f) }
            shape(Color(0xFFFFF9C4)) { moveTo(66f, 88f); lineTo(86f, 68f); lineTo(64f, 66f); close() }
            shape(Color(0x40806A00)) { moveTo(66f, 88f); lineTo(64f, 66f); lineTo(62f, 72f); close() }
            shape(Color(0x998A6D00)) { rect(20f, 38f, 64f, 42f); rect(20f, 50f, 56f, 54f) }
        }.build()
    }

    /** A sheet of ruled paper with a red margin line. */
    val paper: ImageVector by lazy {
        ImageVector.Builder("kit-paper", 24.dp, 24.dp, 100f, 100f).apply {
            shape(Color(0x33000000)) { rect(20f, 10f, 86f, 96f) }
            shape(Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF1F5)), startY = 6f, endY = 92f)) { rect(16f, 6f, 82f, 92f) }
            shape(Color(0xFF8DB3E8)) { for (y in listOf(24f, 36f, 48f, 60f, 72f, 84f)) rect(16f, y, 82f, y + 1.8f) }
            shape(Color(0xFFE57373)) { rect(28f, 6f, 29.8f, 92f) }
            shape(Color(0x40000000)) { rect(16f, 6f, 82f, 7f); rect(81f, 6f, 82f, 92f); rect(16f, 91f, 82f, 92f); rect(16f, 6f, 17f, 92f) }
        }.build()
    }

    // --- shading helpers ---

    /** A cylinder across [x0]..[x1]: lit on the left, a highlight, then falling into shadow. */
    private fun round(base: Color, x0: Float, x1: Float): Brush = Brush.linearGradient(
        0f to lerp(base, Color.White, 0.35f),
        0.3f to lerp(base, Color.White, 0.12f),
        0.65f to base,
        1f to lerp(base, Color.Black, 0.35f),
        start = Offset(x0, 0f), end = Offset(x1, 0f),
    )

    private val metal = listOf(Color(0xFFF4F5F7), Color(0xFFB9BDC4), Color(0xFF7D828B), Color(0xFFC9CDD3))
    private fun metal(x0: Float, x1: Float): Brush = Brush.linearGradient(metal, Offset(x0, 0f), Offset(x1, 0f))

    private fun ImageVector.Builder.shape(fill: Brush, draw: PathBuilder.() -> Unit) = path(fill = fill, pathBuilder = draw)
    private fun ImageVector.Builder.shape(fill: Color, draw: PathBuilder.() -> Unit) = path(fill = SolidColor(fill), pathBuilder = draw)

    private fun PathBuilder.rect(x0: Float, y0: Float, x1: Float, y1: Float) {
        moveTo(x0, y0); lineTo(x1, y0); lineTo(x1, y1); lineTo(x0, y1); close()
    }

    private fun PathBuilder.roundTop(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) {
        moveTo(x0, y1); lineTo(x0, y0 + r)
        quadTo(x0, y0, x0 + r, y0); lineTo(x1 - r, y0)
        quadTo(x1, y0, x1, y0 + r); lineTo(x1, y1); close()
    }

    private val outline = Color(0x55000000)

    // --- the objects ---

    /** Ballpoint: barrel in the ink, a metal clip, a dark grip, a metal cone and the ink tip. */
    private fun ImageVector.Builder.pen(ink: Color) {
        shape(outline) { roundTop(41f, 1f, 59f, 66f, 7f) }
        shape(round(ink, 42f, 58f)) { roundTop(42f, 2f, 58f, 65f, 6f) }
        shape(round(lerp(ink, Color.Black, 0.35f), 42f, 58f)) { rect(42f, 18f, 58f, 21f) }
        shape(metal(57f, 62f)) { moveTo(57f, 6f); lineTo(61.5f, 6f); lineTo(61.5f, 40f); quadTo(61.5f, 43f, 59.5f, 43f); lineTo(57f, 43f); close() }
        shape(round(Color(0xFF2B2D31), 43f, 57f)) { rect(43f, 65f, 57f, 77f) }
        shape(metal(43f, 57f)) { moveTo(43f, 77f); lineTo(57f, 77f); lineTo(52f, 92f); lineTo(48f, 92f); close() }
        shape(ink) { moveTo(48f, 92f); lineTo(52f, 92f); lineTo(50f, 98f); close() }
    }

    /** Wooden pencil: yellow hex barrel, metal ferrule, pink eraser, bare wood and the coloured lead. */
    private fun ImageVector.Builder.pencil(ink: Color) {
        val yellow = Color(0xFFF6C443)
        shape(outline) { roundTop(39f, 1f, 61f, 72f, 7f) }
        shape(round(Color(0xFFF08DA0), 40f, 60f)) { roundTop(40f, 2f, 60f, 12f, 6f) }
        shape(metal(40f, 60f)) { rect(40f, 12f, 60f, 22f) }
        shape(Color(0x33000000)) { rect(40f, 15f, 60f, 16f); rect(40f, 18.5f, 60f, 19.5f) }
        // Three facets of the hexagonal barrel, each its own shade.
        shape(lerp(yellow, Color.White, 0.25f)) { rect(40f, 22f, 46.7f, 71f) }
        shape(yellow) { rect(46.7f, 22f, 53.3f, 71f) }
        shape(lerp(yellow, Color.Black, 0.22f)) { rect(53.3f, 22f, 60f, 71f) }
        shape(Brush.linearGradient(listOf(Color(0xFFF5DEB8), Color(0xFFE2BF8E), Color(0xFFC79E6A)), Offset(40f, 0f), Offset(60f, 0f))) {
            moveTo(40f, 71f); lineTo(60f, 71f); lineTo(53.6f, 86f); lineTo(46.4f, 86f); close()
        }
        shape(ink) { moveTo(46.4f, 86f); lineTo(53.6f, 86f); lineTo(50f, 97f); close() }
    }

    /** Felt marker: pale barrel with a band in the ink, cap and chisel tip in the ink. */
    private fun ImageVector.Builder.marker(ink: Color) {
        shape(outline) { roundTop(35f, 2f, 65f, 72f, 8f) }
        shape(round(lerp(ink, Color.Black, 0.1f), 36f, 64f)) { roundTop(36f, 3f, 64f, 26f, 7f) }
        shape(round(Color(0xFFF1F2F4), 36f, 64f)) { rect(36f, 26f, 64f, 71f) }
        shape(round(ink, 36f, 64f)) { rect(36f, 38f, 64f, 56f) }
        shape(round(Color(0xFFD4D7DC), 41f, 59f)) { rect(41f, 71f, 59f, 79f) }
        shape(round(ink, 44f, 56f)) { moveTo(44f, 79f); lineTo(56f, 79f); lineTo(56f, 88f); lineTo(44f, 96f); close() }
    }

    /** Highlighter: a wide translucent body and cap in the ink, a slanted chisel. */
    private fun ImageVector.Builder.highlighter(ink: Color) {
        val bright = lerp(ink, Color.White, 0.15f)
        shape(outline) { roundTop(33f, 2f, 67f, 64f, 8f) }
        shape(round(lerp(ink, Color.Black, 0.2f), 34f, 66f)) { roundTop(34f, 3f, 66f, 22f, 7f) }
        shape(round(bright, 34f, 66f)) { rect(34f, 22f, 66f, 63f) }
        shape(Color(0x66FFFFFF)) { rect(40f, 28f, 44f, 58f) }
        shape(round(Color(0xFF3A3D42), 37f, 63f)) { moveTo(36f, 63f); lineTo(64f, 63f); lineTo(58f, 75f); lineTo(42f, 75f); close() }
        shape(round(ink, 43f, 57f)) { moveTo(43f, 75f); lineTo(57f, 75f); lineTo(57f, 83f); lineTo(43f, 94f); close() }
    }

    /** A stick of chalk in the ink: chalky, with a worn rounded tip. */
    private fun ImageVector.Builder.chalk(ink: Color) {
        val body = if (ink.luminance() < 0.08f) Color(0xFFF2F0EA) else ink
        shape(outline) { moveTo(39f, 8f); quadTo(39f, 3f, 45f, 3f); lineTo(55f, 3f); quadTo(61f, 3f, 61f, 8f); lineTo(61f, 80f); quadTo(61f, 95f, 50f, 96f); quadTo(39f, 95f, 39f, 80f); close() }
        shape(round(body, 40f, 60f)) { moveTo(40f, 8f); quadTo(40f, 4f, 45f, 4f); lineTo(55f, 4f); quadTo(60f, 4f, 60f, 8f); lineTo(60f, 80f); quadTo(60f, 94f, 50f, 95f); quadTo(40f, 94f, 40f, 80f); close() }
        shape(Color(0x22000000)) { rect(40f, 30f, 60f, 31.5f); rect(40f, 56f, 60f, 57.5f) }
        shape(Color(0x55FFFFFF)) { rect(43f, 8f, 46f, 84f) }
    }

    private fun ImageVector.Builder.dust(ink: Color) {
        val c = (if (ink.luminance() < 0.08f) Color(0xFFBFBDB6) else ink).copy(alpha = 0.6f)
        shape(c) {
            moveTo(9f, 86f); arcTo(3f, 3f, 0f, true, true, 15f, 86f); arcTo(3f, 3f, 0f, true, true, 9f, 86f)
            moveTo(19.6f, 94f); arcTo(2.4f, 2.4f, 0f, true, true, 24.4f, 94f); arcTo(2.4f, 2.4f, 0f, true, true, 19.6f, 94f)
            moveTo(6f, 74f); arcTo(2f, 2f, 0f, true, true, 10f, 74f); arcTo(2f, 2f, 0f, true, true, 6f, 74f)
        }
    }

    /** Paintbrush: lacquered wooden handle, crimped metal ferrule, bristles dipped in the ink. */
    private fun ImageVector.Builder.brush(ink: Color) {
        shape(outline) { moveTo(44f, 1f); lineTo(56f, 1f); lineTo(55f, 56f); lineTo(45f, 56f); close() }
        shape(round(Color(0xFFB5452C), 45f, 55f)) { moveTo(45f, 2f); lineTo(55f, 2f); lineTo(54.2f, 55f); lineTo(45.8f, 55f); close() }
        shape(metal(42f, 58f)) { rect(42f, 55f, 58f, 70f) }
        shape(Color(0x33000000)) { rect(42f, 61f, 58f, 62.2f); rect(42f, 64.5f, 58f, 65.7f) }
        shape(Brush.verticalGradient(listOf(Color(0xFFE8D3A9), lerp(Color(0xFFE8D3A9), ink, 0.5f), ink), startY = 70f, endY = 98f)) {
            moveTo(42f, 70f); lineTo(58f, 70f); curveTo(59f, 80f, 55f, 89f, 50f, 98f); curveTo(45f, 89f, 41f, 80f, 42f, 70f); close()
        }
    }

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
