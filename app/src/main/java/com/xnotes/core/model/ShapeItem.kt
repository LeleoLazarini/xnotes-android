package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Obb
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.Pen
import com.xnotes.core.pal.Renderer
import com.xnotes.core.shapes.PathRole
import com.xnotes.core.shapes.ShapePainter
import com.xnotes.core.shapes.ShapePath
import com.xnotes.core.tools.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An editable geometric shape (spec 02 §5.4): open (line/arrow, two endpoints)
 * or closed (rectangle/ellipse/triangle, drawn inside the normalized AABB of
 * start/end). Polygon/polyline additionally carry a vertex list in [points].
 * Every kind but line/arrow is drawn in that box and then turned by [rotation]
 * about its centre, so a turned shape keeps its kind and its own frame.
 * Closed shapes are stroked and optionally filled. Shapes erase like ink: whole
 * on contact in STROKE mode, and cut into outline fragments by the AREA eraser.
 */
class ShapeItem(
    var shape: ShapeKind,
    var start: Pt,
    var end: Pt,
    var strokeRgba: Rgba,
    var strokeWidth: Double = 3.0,
    var fillRgba: Rgba? = null,
    /** Render the outline as a glowing neon tube (halo + white-hot core). */
    var neon: Boolean = false,
    /** Glow intensity in [0, 1] (halo size + brightness); used only when [neon]. */
    var neonStrength: Double = 0.6,
    /**
     * Vertices for [ShapeKind.POLYGON]/[ShapeKind.POLYLINE], stored normalized to the
     * unit box so they scale with [start]/[end] for free; null for every other kind.
     */
    var points: List<Pt>? = null,
    /** Stroke the outline dashed (dash/gap run lengths in content px). */
    var dashed: Boolean = false,
    var dashLength: Double = 10.0,
    var dashGap: Double = 8.0,
    /** Turn about the box centre in radians, clockwise on screen; always zero for line/arrow. */
    var rotation: Double = 0.0,
) : CanvasItem, Resizable {

    override val kind = KIND
    override val resizable = true
    override var locked = false

    /** Normalized AABB of the two drag points: the shape's frame before [rotation] turns it. */
    val box: Rect get() = Rect.fromPoints(start, end)

    /** True for the kinds that hold their outline as [points] rather than drawing it from the box. */
    val isFree: Boolean get() = shape == ShapeKind.POLYGON || shape == ShapeKind.POLYLINE || shape == ShapeKind.CURVE

    /** [p] from the shape's unturned frame onto the page: turned by [rotation] about the box centre. */
    fun toPage(p: Pt): Pt {
        if (rotation == 0.0) return p
        val c = box.center
        val cs = cos(rotation)
        val sn = sin(rotation)
        val dx = p.x - c.x
        val dy = p.y - c.y
        return Pt(c.x + dx * cs - dy * sn, c.y + dx * sn + dy * cs)
    }

    /** [p] from the page into the shape's unturned frame; the inverse of [toPage]. */
    fun toLocal(p: Pt): Pt {
        if (rotation == 0.0) return p
        val c = box.center
        val cs = cos(rotation)
        val sn = sin(rotation)
        val dx = p.x - c.x
        val dy = p.y - c.y
        return Pt(c.x + dx * cs + dy * sn, c.y - dx * sn + dy * cs)
    }

    /** Polygon/polyline vertices mapped from normalized storage into the unturned frame. */
    internal fun absPoints(): List<Pt> {
        val pts = points ?: return emptyList()
        val b = box
        return pts.map { Pt(b.left + it.x * b.w, b.top + it.y * b.h) }
    }

    /** Page-space vertices for polygon/polyline kinds, turned; null for the rest. */
    fun vertices(): List<Pt>? = if (points == null) null else absPoints().map(::toPage)

    /** The outline pen the shape's style describes, before any role adjusts it. */
    internal fun pen() =
        Pen(color = strokeRgba, width = strokeWidth, cosmetic = false, dashed = dashed, dashOn = dashLength, dashGap = dashGap)

    /** Triangle vertices: apex at top-edge midpoint, base along the bottom edge. */
    internal fun triangleVertices(): List<Pt> {
        val b = box
        return listOf(Pt(b.centerX, b.top), Pt(b.left, b.bottom), Pt(b.right, b.bottom))
    }

    /** Open ">" arrowhead as the chevron polyline barbLeft -> tip -> barbRight (stroked, not filled),
     *  sized from the stroke width. The tip sits just past [end] so the point clears the shaft's
     *  round cap and reads slightly forward instead of buried under the line end. */
    internal fun arrowHead(): List<Pt> {
        val dir = (end - start).normalized()
        if (dir.length() < 1e-9) return emptyList()
        val headLen = max(12.0, strokeWidth * 3.5)
        val tip = end + dir * (strokeWidth * 0.5)
        val back = tip - dir * headLen
        val perp = dir.perp() * (headLen * 0.5)
        return listOf(back + perp, tip, back - perp)
    }

    internal fun ellipsePolygon(segments: Int = 48): List<Pt> {
        val b = box
        val cx = b.centerX
        val cy = b.centerY
        val rx = b.w / 2.0
        val ry = b.h / 2.0
        return (0 until segments).map {
            val a = 2.0 * PI * it / segments
            Pt(cx + rx * cos(a), cy + ry * sin(a))
        }
    }

    /**
     * The shape's geometry as paths in page space: the one outline the note, the canvas and the PDF
     * all draw. Open kinds are stroke-only, and an arrow's chevron is a [PathRole.HEAD] so it stays
     * solid on a dashed arrow.
     */
    fun paths(): List<ShapePath> {
        val local = localPaths()
        return if (rotation == 0.0) local else local.map { it.mapped(::toPage) }
    }

    private fun localPaths(): List<ShapePath> {
        val b = box
        return when (shape) {
            ShapeKind.LINE -> listOf(ShapePath.polyline(listOf(start, end)))
            ShapeKind.ARROW -> {
                val head = arrowHead()
                val shaft = ShapePath.polyline(listOf(start, end))
                if (head.size == 3) listOf(shaft, ShapePath.polyline(head, PathRole.HEAD)) else listOf(shaft)
            }
            ShapeKind.RECTANGLE -> listOf(ShapePath.rect(b))
            ShapeKind.ELLIPSE, ShapeKind.CIRCLE -> listOf(ShapePath.ellipse(b.centerX, b.centerY, b.w / 2.0, b.h / 2.0))
            ShapeKind.TRIANGLE -> listOf(ShapePath.polygon(triangleVertices()))
            ShapeKind.POLYGON -> absPoints().takeIf { it.size >= 2 }?.let { listOf(ShapePath.polygon(it)) }.orEmpty()
            ShapeKind.POLYLINE, ShapeKind.CURVE ->
                absPoints().takeIf { it.size >= 2 }?.let { listOf(ShapePath.polyline(it)) }.orEmpty()
        }
    }

    override fun paint(r: Renderer) {
        val paths = paths()
        if (neon) return paintNeon(r, paths)
        fillRgba?.let { ShapePainter.fill(r, paths, it) }
        ShapePainter.stroke(r, paths, pen())
    }

    /**
     * Neon: the outline as a glowing glass tube — a blurred ink-colour **halo**
     * (composited once at a glow-intensity alpha so corners don't blow out), the
     * opaque colour **body**, then a thinner **white-hot core** down the centre so
     * the colour reads at the tube's edges. [neonStrength] scales the halo only.
     */
    internal fun neonGlowRadius(): Double {
        val s = neonStrength.coerceIn(0.0, 1.0)
        return (strokeWidth * (GLOW_FACTOR_MIN + GLOW_FACTOR_SPAN * s)).coerceAtLeast(GLOW_MIN)
    }

    override fun paintBounds(): Rect =
        if (neon) bounds().outset(neonGlowRadius() * 2.0 + 4.0)
        else bounds()

    private fun paintNeon(r: Renderer, paths: List<ShapePath>) {
        val glowR = neonGlowRadius()
        val s = neonStrength.coerceIn(0.0, 1.0)
        val color = strokeRgba.withAlpha(255)
        val white = Rgba(255, 255, 255, 255)
        val glowAlpha = GLOW_ALPHA_MIN + GLOW_ALPHA_SPAN * s
        val coreW = (strokeWidth * CORE_WIDTH_FRAC).coerceAtLeast(CORE_WIDTH_MIN)

        fillRgba?.let { ShapePainter.fill(r, paths, it) }

        // 1) Outer halo (ink colour), bounded in its own glow-alpha layer.
        r.saveLayerAlpha(paintBounds(), glowAlpha)
        ShapePainter.stroke(r, paths, pen().copy(color = color, glowRadius = glowR))
        r.restore()

        // 2) Tube body — saturated colour shows at the rim.
        ShapePainter.stroke(r, paths, pen())

        // 3) White-hot core — a thinner white line down the centre of the shaft and the chevron.
        ShapePainter.stroke(r, paths, pen().copy(color = white, width = coreW))
    }

    override fun bounds(): Rect {
        val pad = strokeWidth / 2.0 + 1.0
        return when (shape) {
            ShapeKind.LINE -> Rect.fromPoints(start, end).outset(pad)
            ShapeKind.ARROW -> Rect.bounding(listOf(start, end) + arrowHead()).outset(pad)
            else -> outlineBounds().outset(pad)
        }
    }

    /**
     * The shape's own frame as a selection box in page space, turned with it and padded like
     * [bounds]; null for a line or arrow, which its two endpoints frame instead.
     */
    fun frameObb(): Obb? {
        if (shape.isEndpointShape) return null
        val b = box
        val pad = strokeWidth / 2.0 + 1.0
        return Obb(b.center, b.w / 2.0 + pad, b.h / 2.0 + pad, rotation)
    }

    /** The turned outline's page-space bounds, before the stroke's own width. */
    private fun outlineBounds(): Rect {
        if (rotation == 0.0) return box
        if (shape == ShapeKind.ELLIPSE || shape == ShapeKind.CIRCLE) {
            val b = box
            val cs = cos(rotation)
            val sn = sin(rotation)
            val ex = hypot(b.w / 2.0 * cs, b.h / 2.0 * sn)
            val ey = hypot(b.w / 2.0 * sn, b.h / 2.0 * cs)
            return Rect(b.centerX - ex, b.centerY - ey, 2.0 * ex, 2.0 * ey)
        }
        val outline = localOutline()
        return if (outline.isEmpty()) box else Rect.bounding(outline.map(::toPage))
    }

    override fun translate(dx: Double, dy: Double) {
        start = Pt(start.x + dx, start.y + dy)
        end = Pt(end.x + dx, end.y + dy)
    }

    override fun contains(p: Pt): Boolean = containsLocal(toLocal(p))

    private fun containsLocal(p: Pt): Boolean {
        val tol = max(strokeWidth / 2.0, HIT_TOLERANCE)
        return when (shape) {
            ShapeKind.LINE, ShapeKind.ARROW -> Geometry.distancePointToSegment(p, start, end) <= tol
            ShapeKind.RECTANGLE -> if (fillRgba != null) box.contains(p) else nearRectOutline(p, tol)
            ShapeKind.TRIANGLE -> {
                val v = triangleVertices()
                if (fillRgba != null) Geometry.pointInPolygon(v, p) else nearPolyOutline(v, p, tol)
            }
            ShapeKind.ELLIPSE, ShapeKind.CIRCLE -> {
                val poly = ellipsePolygon()
                if (fillRgba != null) Geometry.pointInPolygon(poly, p) else nearPolyOutline(poly, p, tol)
            }
            ShapeKind.POLYGON -> {
                val v = absPoints()
                if (fillRgba != null) Geometry.pointInPolygon(v, p) else nearPolyOutline(v, p, tol)
            }
            ShapeKind.POLYLINE, ShapeKind.CURVE -> nearPolyOutline(absPoints(), p, tol, closed = false)
        }
    }

    private fun nearRectOutline(p: Pt, tol: Double): Boolean {
        val b = box
        val corners = listOf(
            Pt(b.left, b.top), Pt(b.right, b.top), Pt(b.right, b.bottom), Pt(b.left, b.bottom),
        )
        return nearPolyOutline(corners, p, tol)
    }

    private fun nearPolyOutline(verts: List<Pt>, p: Pt, tol: Double, closed: Boolean = true): Boolean {
        if (verts.isEmpty()) return false
        val edges = if (closed) verts.size else verts.size - 1
        for (i in 0 until edges) {
            val a = verts[i]
            val b = verts[(i + 1) % verts.size]
            if (Geometry.distancePointToSegment(p, a, b) <= tol) return true
        }
        return false
    }

    override fun centroid(): Pt = bounds().center

    /** True if an eraser circle of [radius] at (cx,cy) touches the shape's geometry. */
    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean {
        if (bounds().distanceTo(Pt(cx, cy)) > radius) return false // cheap AABB reject
        val p = toLocal(Pt(cx, cy)) // a circle turns into itself, so the test runs unturned
        val tol = radius + strokeWidth / 2.0
        return when (shape) {
            ShapeKind.LINE, ShapeKind.ARROW -> Geometry.distancePointToSegment(p, start, end) <= tol
            ShapeKind.RECTANGLE ->
                if (fillRgba != null && box.contains(p)) true else nearRectOutline(p, tol)
            ShapeKind.TRIANGLE -> {
                val v = triangleVertices()
                if (fillRgba != null && Geometry.pointInPolygon(v, p)) true else nearPolyOutline(v, p, tol)
            }
            ShapeKind.ELLIPSE, ShapeKind.CIRCLE -> {
                val poly = ellipsePolygon()
                if (fillRgba != null && Geometry.pointInPolygon(poly, p)) true else nearPolyOutline(poly, p, tol)
            }
            ShapeKind.POLYGON -> {
                val v = absPoints()
                if (fillRgba != null && Geometry.pointInPolygon(v, p)) true else nearPolyOutline(v, p, tol)
            }
            ShapeKind.POLYLINE, ShapeKind.CURVE -> nearPolyOutline(absPoints(), p, tol, closed = false)
        }
    }

    /**
     * AREA-erase: the outline parts that survive an eraser circle (page-local [cx], [cy],
     * [radius]), cut cleanly at the circle boundary — the same contract as [Stroke.erasedBy]:
     *  - `null`      — untouched (keep the original)
     *  - empty list  — remove the whole shape
     *  - fragments   — the surviving outline runs, as polyline shapes keeping this style
     * A filled shape erases whole on any hit: a cut outline cannot hold its fill.
     */
    fun erasedBy(cx: Double, cy: Double, radius: Double): List<ShapeItem>? {
        val c = Pt(cx, cy)
        if (bounds().distanceTo(c) > radius) return null
        if (fillRgba != null) return if (intersectsCircle(cx, cy, radius)) emptyList() else null
        val verts = when (shape) {
            // The arrow clips by its shaft, like its hit tests; a cut arrow loses its head.
            ShapeKind.LINE, ShapeKind.ARROW -> listOf(start, end)
            else -> localOutline().map(::toPage)
        }
        if (verts.size < 2) return if (intersectsCircle(cx, cy, radius)) emptyList() else null
        val closed = shape.isClosed
        val pts = if (closed) verts + verts.first() else verts

        var touched = false
        val runs = mutableListOf<MutableList<Pt>>()
        var current = mutableListOf<Pt>()
        fun flush() {
            if (current.size >= 2 && polylineLength(current) > 1e-6) runs.add(current)
            current = mutableListOf()
        }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val outside = outsideSpans(a, b, c, radius)
            if (outside.size == 1 && outside[0].first == 0.0 && outside[0].second == 1.0) {
                if (current.isEmpty()) current.add(a)
                current.add(b)
                continue
            }
            touched = true
            if (outside.isEmpty()) { // the whole segment sits inside the circle
                flush()
                continue
            }
            for ((ta, tb) in outside) {
                if (ta == 0.0) {
                    if (current.isEmpty()) current.add(a)
                } else {
                    flush()
                    current.add(lerp(a, b, ta))
                }
                current.add(if (tb == 1.0) b else lerp(a, b, tb))
                if (tb < 1.0) flush()
            }
        }
        flush()
        if (!touched) return null

        // A closed outline cut away from the seam vertex leaves the seam intact in two runs;
        // join them so the survivor is one continuous polyline instead of splitting at v0.
        if (closed && runs.size >= 2) {
            val first = runs.first()
            val last = runs.last()
            if (first.first().distanceTo(pts.first()) < 1e-9 && last.last().distanceTo(pts.first()) < 1e-9) {
                runs.removeAt(runs.size - 1)
                runs[0] = (last + first.drop(1)).toMutableList()
            }
        }
        return runs.map {
            poly(
                ShapeKind.POLYLINE, it, strokeRgba, strokeWidth, null, neon, neonStrength,
                dashed, dashLength, dashGap,
            )
        }
    }

    /** Sub-intervals of the segment [a]→[b] (as fractions) lying outside the circle at [c]. */
    private fun outsideSpans(a: Pt, b: Pt, c: Pt, r: Double): List<Pair<Double, Double>> {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val fx = a.x - c.x
        val fy = a.y - c.y
        val qa = dx * dx + dy * dy
        if (qa < 1e-12) return if (fx * fx + fy * fy > r * r) listOf(0.0 to 1.0) else emptyList()
        val qb = 2.0 * (fx * dx + fy * dy)
        val qc = fx * fx + fy * fy - r * r
        val disc = qb * qb - 4.0 * qa * qc
        if (disc <= 0.0) return listOf(0.0 to 1.0) // misses (or grazes) the segment's line
        val sq = sqrt(disc)
        val t1 = (-qb - sq) / (2.0 * qa)
        val t2 = (-qb + sq) / (2.0 * qa)
        if (t2 <= 0.0 || t1 >= 1.0) return listOf(0.0 to 1.0) // chord lies beyond the segment span
        val spans = mutableListOf<Pair<Double, Double>>()
        if (t1 > 0.0) spans.add(0.0 to min(t1, 1.0))
        if (t2 < 1.0) spans.add(max(t2, 0.0) to 1.0)
        return spans
    }

    private fun lerp(a: Pt, b: Pt, t: Double) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun polylineLength(pts: List<Pt>): Double {
        var len = 0.0
        for (i in 0 until pts.size - 1) len += pts[i].distanceTo(pts[i + 1])
        return len
    }

    override fun geometry(): GeoHandle = ShapeHandle(start, end)

    override fun setGeometry(handle: GeoHandle) {
        if (handle is ShapeHandle) {
            start = handle.start
            end = handle.end
        }
    }

    override fun snapshotGeometry(): GeometrySnapshot = ShapeSnapshot(shape, start, end, points, rotation)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap !is ShapeSnapshot) return
        shape = snap.shape
        start = snap.start
        end = snap.end
        points = snap.points
        rotation = snap.rotation
    }

    /**
     * Bake a transform into the shape, keeping its kind wherever the result is still that kind. A
     * line/arrow moves its two endpoints. A box kind whose axes stay square to each other (any
     * scale along them, any turn) gets a new box and [rotation]; an ellipse sheared by a stretch
     * across its axes is still an ellipse, refound from the map's singular values; a sheared
     * rectangle or triangle is a parallelogram's worth of corners, kept as a polygon. Polygon and
     * polyline points map exactly. The outline keeps its width: a shape is redrawn, not magnified.
     */
    override fun applyTransform(t: Affine) {
        if (shape.isEndpointShape) {
            start = t.apply(start)
            end = t.apply(end)
            return
        }
        val b = box
        val cs = cos(rotation)
        val sn = sin(rotation)
        // Where the frame's unit axes land: the transform's linear part after the shape's own turn.
        val ux = Pt(t.a * cs + t.c * sn, t.b * cs + t.d * sn)
        val uy = Pt(-t.a * sn + t.c * cs, -t.b * sn + t.d * cs)
        val square = abs(ux.x * uy.x + ux.y * uy.y) <= 1e-9 * ux.length() * uy.length()
        when {
            isFree -> refit(absPoints().map { t.apply(toPage(it)) }, atan2(ux.y, ux.x))
            square -> {
                // The turn is read off the height axis, so a mirrored triangle keeps its apex.
                setFrame(t.apply(b.center), ux.length() * b.w, uy.length() * b.h, atan2(uy.y, uy.x) - PI / 2.0)
            }
            shape == ShapeKind.ELLIPSE || shape == ShapeKind.CIRCLE -> {
                // The ellipse is the unit circle under A = M diag(rx, ry); its axes are A's singular pairs.
                val rx = b.w / 2.0
                val ry = b.h / 2.0
                val p = ux.x * ux.x * rx * rx + uy.x * uy.x * ry * ry
                val q = ux.x * ux.y * rx * rx + uy.x * uy.y * ry * ry
                val r = ux.y * ux.y * rx * rx + uy.y * uy.y * ry * ry
                val mid = (p + r) / 2.0
                val spread = hypot((p - r) / 2.0, q)
                val major = sqrt(mid + spread)
                val minor = sqrt((mid - spread).coerceAtLeast(0.0))
                setFrame(t.apply(b.center), 2.0 * major, 2.0 * minor, 0.5 * atan2(2.0 * q, p - r))
            }
            else -> {
                val corners = localOutline().map { t.apply(toPage(it)) }
                shape = ShapeKind.POLYGON
                refit(corners, atan2(ux.y, ux.x))
            }
        }
        if (shape == ShapeKind.CIRCLE && abs(box.w - box.h) > 1e-9 * max(box.w, box.h)) shape = ShapeKind.ELLIPSE
    }

    /** Make the unturned box [w] x [h] about [centre], turned by [turn], snapping a quarter turn upright. */
    private fun setFrame(centre: Pt, w: Double, h: Double, turn: Double) {
        var ww = w
        var hh = h
        var a = normalizeTurn(turn)
        val quarters = round(a / (PI / 2.0))
        // A rectangle or ellipse turned a whole number of quarters is just an upright one.
        if (abs(a - quarters * PI / 2.0) < TURN_SNAP && shape != ShapeKind.TRIANGLE) {
            if (quarters.toInt() % 2 != 0) {
                ww = h
                hh = w
            }
            a = 0.0
        }
        if (abs(a) < TURN_SNAP) a = 0.0
        start = Pt(centre.x - ww / 2.0, centre.y - hh / 2.0)
        end = Pt(centre.x + ww / 2.0, centre.y + hh / 2.0)
        rotation = a
    }

    /** Re-derive the box and normalized points from page-space [pts], in a frame turned by [turn]. */
    private fun refit(pts: List<Pt>, turn: Double) {
        if (pts.isEmpty()) return
        var a = normalizeTurn(turn)
        val quarters = round(a / (PI / 2.0))
        if (abs(a - quarters * PI / 2.0) < TURN_SNAP) a = 0.0 // bounds read the same either way
        val cs = cos(a)
        val sn = sin(a)
        val unturned = pts.map { Pt(it.x * cs + it.y * sn, -it.x * sn + it.y * cs) }
        val lb = Rect.bounding(unturned)
        val c = Pt(lb.centerX * cs - lb.centerY * sn, lb.centerX * sn + lb.centerY * cs)
        start = Pt(c.x - lb.w / 2.0, c.y - lb.h / 2.0)
        end = Pt(c.x + lb.w / 2.0, c.y + lb.h / 2.0)
        rotation = a
        points = normalize(unturned, lb)
    }

    /**
     * What a reader that predates [rotation] should draw instead: null when this shape needs no
     * stand-in (upright, or a line or arrow), else its page outline as polygon (or, for an open
     * free kind, polyline) vertices. The codecs write it as the legacy shape and add the real one.
     */
    fun legacyOutline(): List<Pt>? = when {
        rotation == 0.0 || shape.isEndpointShape -> null
        isFree -> vertices()
        shape == ShapeKind.ELLIPSE || shape == ShapeKind.CIRCLE -> ellipsePolygon(LEGACY_ELLIPSE_POINTS).map(::toPage)
        else -> localOutline().map(::toPage)
    }

    /**
     * Turn a stand-in read from a file back into the shape it stands for: its real [kind] (null
     * keeps this one), its unturned [frame] and the [turn] about the frame's centre.
     */
    fun restoreFrame(kind: ShapeKind?, frame: Rect, turn: Double) {
        val target = kind ?: shape
        val finite = frame.x.isFinite() && frame.y.isFinite() && frame.w.isFinite() && frame.h.isFinite()
        if (target.isEndpointShape || !finite || !turn.isFinite()) return
        val pagePoints = vertices()
        shape = target
        start = frame.topLeft
        end = Pt(frame.right, frame.bottom)
        rotation = turn
        points = if (isFree) pagePoints?.let { pts -> normalize(pts.map(::toLocal), box) } else null
    }

    /**
     * Earlier versions baked a turned rectangle into a 4-point polygon and a turned ellipse into a
     * 48-point one. Spot either, exact to rounding, and make it the turned shape it was.
     */
    fun unbake() {
        if (shape != ShapeKind.POLYGON || rotation != 0.0) return
        val p = vertices() ?: return
        if (p.size == 4) unbakeRect(p) else if (p.size == BAKED_ELLIPSE_POINTS) unbakeEllipse(p)
    }

    private fun unbakeRect(p: List<Pt>) {
        val e1 = p[1] - p[0]
        val e2 = p[2] - p[1]
        val e3 = p[3] - p[2]
        val e4 = p[0] - p[3]
        val size = max(e1.length(), e2.length())
        if (size < 1e-9) return
        val tol = 1e-9 * size
        if ((e1 + e3).length() > tol || (e2 + e4).length() > tol) return
        if (abs(e1.x * e2.x + e1.y * e2.y) > tol * size) return
        shape = ShapeKind.RECTANGLE
        points = null
        setFrame((p[0] + p[2]) * 0.5, e1.length(), e2.length(), atan2(e1.y, e1.x))
    }

    private fun unbakeEllipse(p: List<Pt>) {
        var cx = 0.0
        var cy = 0.0
        for (q in p) {
            cx += q.x
            cy += q.y
        }
        val c = Pt(cx / p.size, cy / p.size)
        // The bake sampled angle 2 pi k / 48, so point 0 and point 12 are the images of the two axes.
        val a1 = p[0] - c
        val a2 = p[BAKED_ELLIPSE_POINTS / 4] - c
        val size = max(a1.length(), a2.length())
        if (size < 1e-9) return
        for (k in p.indices) {
            val a = 2.0 * PI * k / BAKED_ELLIPSE_POINTS
            val want = c + a1 * cos(a) + a2 * sin(a)
            if (want.distanceTo(p[k]) > 1e-7 * size) return
        }
        val pp = a1.x * a1.x + a2.x * a2.x
        val q = a1.x * a1.y + a2.x * a2.y
        val r = a1.y * a1.y + a2.y * a2.y
        val mid = (pp + r) / 2.0
        val spread = hypot((pp - r) / 2.0, q)
        val major = sqrt(mid + spread)
        val minor = sqrt((mid - spread).coerceAtLeast(0.0))
        shape = if (major - minor <= 1e-9 * major) ShapeKind.CIRCLE else ShapeKind.ELLIPSE
        points = null
        setFrame(c, 2.0 * major, 2.0 * minor, 0.5 * atan2(2.0 * q, pp - r))
    }

    /** Vertices of the unturned outline; what a bake or the area eraser turns into a vertex list. */
    private fun localOutline(): List<Pt> = when (shape) {
        ShapeKind.POLYGON, ShapeKind.POLYLINE, ShapeKind.CURVE -> absPoints()
        ShapeKind.TRIANGLE -> triangleVertices()
        ShapeKind.ELLIPSE, ShapeKind.CIRCLE -> ellipsePolygon()
        else -> {
            val b = box
            listOf(Pt(b.left, b.top), Pt(b.right, b.top), Pt(b.right, b.bottom), Pt(b.left, b.bottom))
        }
    }

    companion object {
        const val KIND = "shape"
        const val HIT_TOLERANCE = 6.0

        /** A turn this close to a whole number of quarters counts as one, so upright stays upright. */
        private const val TURN_SNAP = 1e-9

        /** Points in the polygon a turned ellipse is stored as, for readers that predate turning. */
        private const val LEGACY_ELLIPSE_POINTS = 64

        /** Points earlier versions baked a turned ellipse into. */
        private const val BAKED_ELLIPSE_POINTS = 48

        /** [a] folded into (-PI, PI]. */
        private fun normalizeTurn(a: Double): Double {
            var r = a % (2.0 * PI)
            if (r <= -PI) r += 2.0 * PI
            if (r > PI) r -= 2.0 * PI
            return r
        }

        /** Build a polygon/polyline from absolute [vertices], stored normalized to their box. */
        fun poly(
            shape: ShapeKind,
            vertices: List<Pt>,
            strokeRgba: Rgba,
            strokeWidth: Double = 3.0,
            fillRgba: Rgba? = null,
            neon: Boolean = false,
            neonStrength: Double = 0.6,
            dashed: Boolean = false,
            dashLength: Double = 10.0,
            dashGap: Double = 8.0,
        ): ShapeItem {
            val box = Rect.bounding(vertices)
            return ShapeItem(
                shape, box.topLeft, Pt(box.right, box.bottom), strokeRgba, strokeWidth,
                fillRgba, neon, neonStrength, normalize(vertices, box), dashed, dashLength, dashGap,
            )
        }

        private fun normalize(vertices: List<Pt>, box: Rect): List<Pt> {
            val w = if (box.w > 1e-9) box.w else 1.0
            val h = if (box.h > 1e-9) box.h else 1.0
            return vertices.map { Pt((it.x - box.left) / w, (it.y - box.top) / h) }
        }

        /** Halo blur = stroke_width × (MIN + SPAN × neonStrength), floored in page px. */
        private const val GLOW_FACTOR_MIN = 1.2
        private const val GLOW_FACTOR_SPAN = 2.6
        private const val GLOW_MIN = 4.0

        /** Halo opacity = MIN + SPAN × neonStrength. */
        internal const val GLOW_ALPHA_MIN = 0.25
        internal const val GLOW_ALPHA_SPAN = 0.55

        /** White-hot core line width as a fraction of the stroke width, with a page-px floor. */
        internal const val CORE_WIDTH_FRAC = 0.4
        internal const val CORE_WIDTH_MIN = 1.0
    }
}

/** Snapshot of a shape's transformable geometry (kind can change when a box shape is rotated). */
private data class ShapeSnapshot(
    val shape: ShapeKind,
    val start: Pt,
    val end: Pt,
    val points: List<Pt>?,
    val rotation: Double,
) : GeometrySnapshot
