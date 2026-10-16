package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import com.xnotes.core.pal.GRAIN_LEVELS
import com.xnotes.core.tools.Brush
import java.nio.ByteBuffer
import java.util.Random

/**
 * The paper grain the textured pens fill through, generated once per brush and coverage level.
 *
 * A pencil looks like graphite because the paper is not flat: light pressure only touches the
 * peaks of its tooth, harder pressure reaches further down. So each brush has one tileable
 * height field (fine tooth plus a broader fibre wave plus per-texel noise), and level k shows the
 * top [density] share of it. Coverage is cut by rank, not by raw height, so a level covers exactly
 * the share it says whatever the field's distribution; and because every level cuts the same
 * field, a denser level covers every grain of a lighter one, which is what lets runs of different
 * pressure meet without a seam. The shader repeats in page space, so the grain is fixed to the
 * paper: going over a line again darkens it the way a real pencil does.
 */
internal object GrainTextures {
    private const val SIZE = 256
    private const val TONE_FLOOR = 0.55f

    private class Spec(
        val fineCells: Int,
        val midCells: Int,
        val wFine: Float,
        val wMid: Float,
        val wWhite: Float,
        val densityLo: Float,
        val densityHi: Float,
        val softness: Float,
        val maxAlpha: Float,
        /** Page px per texel: how coarse the tooth is on the paper. */
        val texel: Float,
        val seed: Long,
    )

    private fun spec(brush: Brush): Spec = when (brush) {
        // Tuned against an offline render of test strokes: the fine tooth carries the look, the
        // broad wave only breaks up the evenness, and pressure darkens the tone as well as the coverage.
        Brush.CRAYON -> Spec(80, 24, 0.55f, 0.20f, 0.25f, 0.50f, 0.92f, 0.10f, 0.95f, 0.55f, 0xC0FFEE)
        Brush.CHALK -> Spec(64, 16, 0.50f, 0.25f, 0.25f, 0.40f, 0.75f, 0.08f, 0.95f, 0.65f, 0xC4A1C)
        else -> Spec(128, 32, 0.45f, 0.15f, 0.40f, 0.30f, 0.95f, 0.22f, 0.85f, 0.40f, 0x9E9C11)
    }

    private val ranks = HashMap<Brush, FloatArray>()
    private val shaders = HashMap<Int, BitmapShader>()

    fun shader(brush: Brush, level: Int): BitmapShader = synchronized(shaders) {
        val l = level.coerceIn(0, GRAIN_LEVELS - 1)
        shaders.getOrPut(brush.ordinal * GRAIN_LEVELS + l) { build(brush, l) }
    }

    private fun build(brush: Brush, level: Int): BitmapShader {
        val s = spec(brush)
        val rank = ranks.getOrPut(brush) { rankField(s) }
        val density = s.densityLo + (s.densityHi - s.densityLo) * level / (GRAIN_LEVELS - 1).toFloat()
        val cut = 1f - density
        // A light touch leaves less graphite on the grains it does reach, not only fewer grains.
        val tone = TONE_FLOOR + (1f - TONE_FLOOR) * level / (GRAIN_LEVELS - 1).toFloat()
        val bytes = ByteArray(SIZE * SIZE)
        for (i in bytes.indices) {
            val a = smoothstep(cut - s.softness, cut + s.softness, rank[i]) * s.maxAlpha * tone
            bytes[i] = (a * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ALPHA_8)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        bmp.setHasMipMap(true)
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(s.texel, s.texel) })
        }
    }

    /** The brush's height field, replaced by each texel's rank in it (0 = lowest, 1 = highest). */
    private fun rankField(s: Spec): FloatArray {
        val rnd = Random(s.seed)
        val fine = lattice(s.fineCells, rnd)
        val mid = lattice(s.midCells, rnd)
        val h = FloatArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            h[y * SIZE + x] = s.wFine * sample(fine, s.fineCells, x, y) +
                s.wMid * sample(mid, s.midCells, x, y) +
                s.wWhite * rnd.nextFloat()
        }
        val order = (0 until h.size).sortedBy { h[it] }
        val rank = FloatArray(h.size)
        val last = (h.size - 1).toFloat()
        order.forEachIndexed { r, i -> rank[i] = r / last }
        return rank
    }

    private fun lattice(cells: Int, rnd: Random) = FloatArray(cells * cells) { rnd.nextFloat() }

    /** Smooth value noise over a [cells]² lattice that wraps, so the texture tiles seamlessly. */
    private fun sample(grid: FloatArray, cells: Int, x: Int, y: Int): Float {
        val fx = x * cells / SIZE.toFloat()
        val fy = y * cells / SIZE.toFloat()
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val tx = ease(fx - x0)
        val ty = ease(fy - y0)
        fun g(cx: Int, cy: Int) = grid[(cy % cells) * cells + (cx % cells)]
        val top = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * tx
        val bottom = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
