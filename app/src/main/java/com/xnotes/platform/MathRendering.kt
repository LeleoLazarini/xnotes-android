package com.xnotes.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexFontFamilies
import com.hrm.latex.renderer.model.LatexTheme
import com.xnotes.core.model.Rgba
import com.xnotes.core.pal.MathBox
import com.xnotes.core.pal.MathTypesetter
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Typesets and draws the flow's LaTeX, through a renderer that can only be built
 * inside a composition. One per process like [com.xnotes.settings.LiveSettings],
 * because a [AndroidRenderer] is made fresh around each canvas and there is
 * nowhere else for the formula cache to live. Until [install] runs, measuring
 * returns null and maths reads as its own source, which is also what happens to
 * LaTeX that will not parse.
 *
 * A formula is laid out once, and that one layout gives both its box and its rasters. Rasters are
 * alpha masks the text colour tints when drawn, so a new colour or theme costs nothing, except for
 * the few formulas that paint in colours of their own.
 */
object MathRendering : MathTypesetter {

    /** The renderer's pieces, all built against one density. */
    private class Engine(
        val spPx: Float,
        val density: Density,
        val text: TextMeasurer,
        val fonts: LatexFontFamilies,
        val metrics: Any,
    )

    @Volatile private var engine: Engine? = null

    /** A formula as the caches know it. */
    private data class Formula(val latex: String, val sizePt: Double, val display: Boolean)

    /**
     * What setting a formula found: the box it fills, null for LaTeX that will not set, and whether
     * it paints in colours of its own, which a tinted mask cannot carry.
     */
    private class Setting(val box: MathBox?, val colored: Boolean)

    /** A raster of [formula] at [bucket]; [argb] is the colour it was painted in, or 0 for a mask. */
    private data class RasterKey(val formula: Formula, val bucket: Int, val argb: Int)

    // Settings are a few dozen bytes each and the layout pass asks after every formula in the note,
    // so this holds far more than any note has.
    private const val SETTINGS = 8192

    // Layouts hold the library's text layouts, so only the latest few are kept: enough to raster a
    // screenful again at a new zoom without setting any of it twice.
    private const val LAYOUTS = 48

    private const val RASTER_BYTES = 32 shl 20

    // No raster side may pass this; a long formula at a deep zoom softens rather than costing tens
    // of megabytes.
    private const val MAX_SIDE_PX = 4096.0

    // Rasters come in powers of two of their page size, from a quarter to eight times.
    private const val MIN_BUCKET = -2
    private const val MAX_BUCKET = 3

    // The bitmap a PDF export falls back to when it cannot take a formula as outlines.
    private const val EXPORT_SCALE = 3f

    /** What a mask is painted in: its alpha is all a mask keeps. */
    private val MASK_INK = Rgba(0, 0, 0, 255)

    private val settings = LruCache<Formula, Setting>(SETTINGS)

    /** Touched only holding the lock, like everything else that reaches the library. */
    private val layouts = LruCache<Formula, Any>(LAYOUTS)

    private val rasters = object : LruCache<RasterKey, Bitmap>(RASTER_BYTES) {
        override fun sizeOf(key: RasterKey, value: Bitmap): Int = value.allocationByteCount
    }

    /**
     * What a drawing thread paints formulas with. Each renderer keeps its own, so the colour a mask
     * is tinted with is never set by two threads at once.
     */
    class Ink {
        internal val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        internal val rect = RectF()
    }

    /**
     * Adopt the renderer built in the composition. Called again whenever the
     * density or the fonts change, which invalidates every formula set so far.
     */
    fun install(density: Density, text: TextMeasurer, fonts: LatexFontFamilies) {
        val spPx = with(density) { 1.sp.toPx() }
        synchronized(this) {
            engine = Engine(spPx, density, text, fonts, LatexInternals.metrics(fonts))
            settings.evictAll()
            layouts.evictAll()
            rasters.evictAll()
        }
    }

    override fun ready(): Boolean = engine != null

    override fun measure(latex: String, sizePt: Double, display: Boolean): MathBox? {
        val e = engine ?: return null
        if (latex.isBlank() || e.spPx <= 0f) return null
        val f = Formula(latex, sizePt, display)
        settings.get(f)?.let { return it.box }
        // The library caches in plain maps, and page caches are built off the main
        // thread, so every call into it is serialized here rather than there.
        return synchronized(this) { settings.get(f) ?: layOut(e, f).first }.box
    }

    /** Set [f] afresh, remembering the setting and the layout it came from. Call holding the lock. */
    private fun layOut(e: Engine, f: Formula): Pair<Setting, Any?> {
        val nodes = runCatching { LatexInternals.parse(source(f.latex, f.display)) }.getOrDefault(emptyList())
        val layout = runCatching {
            LatexInternals.layout(nodes, config(e, f.sizePt, MASK_INK), e.fonts, e.metrics, e.text, e.density)
        }.getOrNull()
        val setting = Setting(layout?.let(::boxOf), LatexInternals.ownColors(nodes))
        settings.put(f, setting)
        if (layout != null) layouts.put(f, layout)
        return setting to layout
    }

    private fun boxOf(layout: Any): MathBox {
        val baseline = LatexInternals.baseline(layout).toDouble()
        return MathBox(
            width = LatexInternals.width(layout).toDouble(),
            ascent = baseline,
            descent = LatexInternals.height(layout) - baseline,
        )
    }

    /**
     * Draw [latex] with its left edge at [x], sitting on [baseline], in [color], on a canvas that
     * shows one page px as [scale] of its own.
     */
    fun draw(
        canvas: Canvas,
        ink: Ink,
        latex: String,
        x: Double,
        baseline: Double,
        sizePt: Double,
        color: Rgba,
        display: Boolean,
        scale: Float,
    ) {
        val e = engine ?: return
        if (latex.isBlank() || e.spPx <= 0f) return
        val f = Formula(latex, sizePt, display)
        val bucket = bucketFor(scale)
        val cached = settings.get(f)?.let { s -> rasters.get(RasterKey(f, bucket, argbFor(s, color))) }
        val bmp = cached ?: synchronized(this) { rasterLocked(e, f, bucket, color) } ?: return
        val box = settings.get(f)?.box ?: return
        blit(canvas, ink, bmp, x, baseline, box, color)
    }

    private fun argbFor(setting: Setting, color: Rgba): Int = if (setting.colored) color.toArgb() else 0

    /** [f]'s raster at [bucket] for [color], painted now unless it is cached. Call holding the lock. */
    private fun rasterLocked(e: Engine, f: Formula, bucket: Int, color: Rgba): Bitmap? {
        var setting = settings.get(f)
        var layout = layouts.get(f)
        if (setting == null || (setting.box != null && layout == null)) {
            val (s, l) = layOut(e, f)
            setting = s
            layout = l
        }
        val box = setting.box ?: return null
        val key = RasterKey(f, bucket, argbFor(setting, color))
        rasters.get(key)?.let { return it }
        // The colours a formula paints in sit in its layout, so one that has its own is set again in
        // the text colour; any other paints its mask from the layout it was measured with.
        val painted = if (setting.colored) layoutIn(e, f, color) else layout
        val bmp = painted?.let { paint(e, it, box, scaleOf(bucket), setting.colored) } ?: return null
        rasters.put(key, bmp)
        return bmp
    }

    private fun layoutIn(e: Engine, f: Formula, color: Rgba): Any? = runCatching {
        LatexInternals.layout(LatexInternals.parse(source(f.latex, f.display)), config(e, f.sizePt, color), e.fonts, e.metrics, e.text, e.density)
    }.getOrNull()

    /** [layout] painted to fill [box] at [scale], as a mask unless it is [colored]. */
    private fun paint(e: Engine, layout: Any, box: MathBox, scale: Float, colored: Boolean): Bitmap? {
        val longest = max(box.width, box.height)
        if (longest <= 0.0) return null
        val s = min(scale.toDouble(), MAX_SIDE_PX / longest)
        val w = ceil(box.width * s).toInt().coerceAtLeast(1)
        val h = ceil(box.height * s).toInt().coerceAtLeast(1)
        val bmp = runCatching {
            Bitmap.createBitmap(w, h, if (colored) Bitmap.Config.ARGB_8888 else Bitmap.Config.ALPHA_8)
        }.getOrNull() ?: return null
        val canvas = Canvas(bmp)
        canvas.scale((w / box.width).toFloat(), (h / box.height).toFloat())
        val size = Size(box.width.toFloat(), box.height.toFloat())
        CanvasDrawScope().draw(e.density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), size) {
            LatexInternals.draw(this, layout)
        }
        return bmp
    }

    private fun blit(canvas: Canvas, ink: Ink, bmp: Bitmap, x: Double, baseline: Double, box: MathBox, color: Rgba) {
        // A mask takes the text colour from the paint; a coloured raster already carries it.
        ink.paint.color = if (bmp.config == Bitmap.Config.ALPHA_8) color.toArgb() else android.graphics.Color.BLACK
        ink.rect.set(
            x.toFloat(),
            (baseline - box.ascent).toFloat(),
            (x + box.width).toFloat(),
            (baseline + box.descent).toFloat(),
        )
        canvas.drawBitmap(bmp, null, ink.rect, ink.paint)
    }

    /**
     * Draw [latex] with the renderer's own drawing calls into [canvas], its box's top-left at the
     * canvas origin and in content px: the same box [measure] reports, so a backend recording the
     * calls gets the formula as outlines where the bitmap would have gone. False when there is no
     * engine yet or the LaTeX does not set.
     */
    fun drawVector(canvas: Canvas, latex: String, sizePt: Double, color: Rgba, display: Boolean): Boolean {
        val e = engine ?: return false
        if (latex.isBlank() || e.spPx <= 0f) return false
        return synchronized(this) {
            runCatching {
                val layout = layoutIn(e, Formula(latex, sizePt, display), color) ?: return@runCatching false
                val size = Size(LatexInternals.width(layout), LatexInternals.height(layout))
                CanvasDrawScope().draw(e.density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), size) {
                    LatexInternals.draw(this, layout)
                }
                true
            }.getOrDefault(false)
        }
    }

    /**
     * [latex] painted in [color] and the box it fills, for a backend that places the image itself.
     * The bitmap is a fresh one the caller owns.
     */
    fun formulaBitmap(latex: String, sizePt: Double, color: Rgba, display: Boolean): Pair<Bitmap, MathBox>? {
        val e = engine ?: return null
        if (latex.isBlank() || e.spPx <= 0f) return null
        return synchronized(this) {
            val f = Formula(latex, sizePt, display)
            val box = (settings.get(f) ?: layOut(e, f).first).box ?: return@synchronized null
            val layout = layoutIn(e, f, color) ?: return@synchronized null
            paint(e, layout, box, EXPORT_SCALE, colored = true)?.let { it to box }
        }
    }

    /** The power of two at least [scale], so a pinch rasters a formula again only at doublings. */
    private fun bucketFor(scale: Float): Int =
        ceil(log2(scale.coerceAtLeast(1e-3f).toDouble())).toInt().coerceIn(MIN_BUCKET, MAX_BUCKET)

    private fun scaleOf(bucket: Int): Float = 2f.pow(bucket)

    /**
     * What actually goes to the renderer. Display form is asked for in LaTeX's own
     * terms rather than a render flag, so it is the same string the user would
     * have written by hand and the measurement and the raster cannot disagree.
     */
    private fun source(latex: String, display: Boolean): String =
        if (display) "\\displaystyle $latex" else latex

    /**
     * Ask for the formula in page pixels rather than screen ones: dividing the
     * size back out of the measurer's own density is what keeps an equation the
     * same size on the page whatever the display or the reader's font scale.
     */
    private fun config(e: Engine, sizePt: Double, color: Rgba): LatexConfig {
        val px = sizePt * AndroidText.POINTS_TO_PX
        return LatexConfig(
            fontSize = (px / e.spPx).sp,
            theme = LatexTheme.light(color = Color(color.toArgb()), backgroundColor = Color.Transparent),
        )
    }
}
