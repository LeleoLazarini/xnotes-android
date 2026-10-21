package com.xnotes.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.hypot
import kotlin.math.sin

/** One leaf being turned: the page as it looked before the turn, and which way it goes. */
class PageTurn(val leaf: Bitmap, val forward: Boolean, val id: Long)

private const val TURN_MS = 520

/**
 * The page-turn animation, laid over the canvas. The live canvas already shows the new page; the
 * old one is drawn on top and folded away from the corner: a fold line sweeps across the leaf, the
 * part past it flips over onto the leaf (its pale back showing), and the new page comes into view
 * beyond, under a soft shadow along the fold. Turning back runs the same fold from the other edge.
 */
@Composable
internal fun PageTurnOverlay(editor: Editor) {
    val turn = editor.pageTurn ?: return
    val progress = remember(turn.id) { Animatable(0f) }
    LaunchedEffect(turn.id) {
        progress.animateTo(1f, tween(TURN_MS, easing = FastOutSlowInEasing))
        editor.endPageTurn(turn.id)
    }
    val back = LocalPalette.current.paper.toComposeColor().toArgb()
    val painter = remember { LeafPainter() }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { painter.draw(it.nativeCanvas, turn, progress.value, size.width, size.height, back) }
    }
}

private class LeafPainter {
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val flap = Path()
    private val m = Matrix()
    private val dst = RectF()

    fun draw(c: android.graphics.Canvas, turn: PageTurn, t: Float, w: Float, h: Float, backColor: Int) {
        if (t >= 1f) return
        // The fold line, from the top edge to the bottom one: it enters at the bottom corner first
        // and leans over as it sweeps, so the corner lifts before the spine side does.
        val xb = w * (1.04f - 1.42f * t)
        val xt = xb + w * (0.10f + 0.22f * sin(Math.PI * t).toFloat())
        val ax = xt; val ay = 0f
        val bx = xb; val by = h
        dst.set(0f, 0f, w, h)
        c.save()
        // Turning back folds from the other edge: mirror the geometry, never the page itself.
        if (!turn.forward) c.scale(-1f, 1f, w / 2f, h / 2f)

        // The leaf still lying flat: everything on this side of the fold.
        path.reset()
        path.moveTo(-w, -1f); path.lineTo(ax, ay); path.lineTo(bx, by); path.lineTo(-w, h + 1f); path.close()
        c.save()
        c.clipPath(path)
        drawLeaf(c, turn, w, h)
        c.restore()

        // Past the fold: the new page shows, darkened next to the fold where the flap shades it.
        flap.reset()
        flap.moveTo(ax, ay); flap.lineTo(2 * w, -1f); flap.lineTo(2 * w, h + 1f); flap.lineTo(bx, by); flap.close()
        val len = hypot(bx - ax, by - ay).coerceAtLeast(1f)
        val nx = (by - ay) / len // unit normal pointing past the fold
        val ny = -(bx - ax) / len
        c.save()
        c.clipPath(flap)
        fill.shader = LinearGradient(ax, ay, ax + nx * w * 0.07f, ay + ny * w * 0.07f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(dst, fill)
        c.restore()

        // The flap: the part of the leaf past the fold, flipped over onto the leaf. Reflecting
        // across the fold line maps it there; it shows the paper's back, with the ink faintly
        // through, shaded darker where it bends at the fold.
        val ux = (bx - ax) / len; val uy = (by - ay) / len
        val a = 2 * ux * ux - 1; val b = 2 * ux * uy; val d = 2 * uy * uy - 1
        m.setValues(floatArrayOf(a, b, ax - a * ax - b * ay, b, d, ay - b * ax - d * ay, 0f, 0f, 1f))
        c.save()
        c.concat(m)
        c.clipPath(flap)
        c.clipRect(dst)
        fill.shader = null
        fill.color = backColor
        c.drawRect(dst, fill)
        image.alpha = 28
        drawLeaf(c, turn, w, h)
        image.alpha = 255
        fill.shader = LinearGradient(ax, ay, ax + nx * w * 0.35f, ay + ny * w * 0.35f, 0x40000000, 0x08000000, Shader.TileMode.CLAMP)
        c.drawRect(dst, fill)
        c.restore()
        fill.shader = null
        c.restore()
    }

    /** The old page, readable whichever way the geometry is mirrored. */
    private fun drawLeaf(c: android.graphics.Canvas, turn: PageTurn, w: Float, h: Float) {
        if (!turn.forward) {
            c.save()
            c.scale(-1f, 1f, w / 2f, h / 2f)
            c.drawBitmap(turn.leaf, null, dst, image)
            c.restore()
        } else {
            c.drawBitmap(turn.leaf, null, dst, image)
        }
    }
}
