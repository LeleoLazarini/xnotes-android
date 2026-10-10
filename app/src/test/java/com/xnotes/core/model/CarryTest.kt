package com.xnotes.core.model

import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CarryTest {

    private val ink = Rgba(10, 20, 30, 255)

    private fun box(x: Double, y: Double, w: Double, h: Double, kind: ShapeKind = ShapeKind.RECTANGLE) =
        ShapeItem(kind, Pt(x, y), Pt(x + w, y + h), ink, 2.0)

    private fun ink(vararg pts: Pt) = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), pts.map { Sample(it.x, it.y, 1.0) }.toMutableList())

    @Test fun onlyWhatIsEntirelyInsideIsCarried() {
        val frame = box(0.0, 0.0, 200.0, 100.0)
        val inside = ink(Pt(20.0, 20.0), Pt(80.0, 60.0))
        val across = ink(Pt(150.0, 50.0), Pt(260.0, 50.0))
        val outside = ink(Pt(300.0, 50.0), Pt(320.0, 60.0))
        val locked = ink(Pt(30.0, 30.0), Pt(40.0, 40.0)).apply { this.locked = true }
        val carry = Carry.of(listOf(frame), listOf(frame, inside, across, outside, locked), skip = listOf(frame))
        assertEquals(listOf<CanvasItem>(inside), carry.items)
    }

    @Test fun anEmptyMiddleStillHoldsItsWritingButAnOpenShapeHoldsNothing() {
        val ring = box(0.0, 0.0, 200.0, 200.0, ShapeKind.RING)
        val inHole = ink(Pt(95.0, 95.0), Pt(105.0, 105.0))
        assertTrue("the hole is outside a ring", Carry.of(listOf(ring), listOf(inHole), listOf(ring)).isEmpty)
        val line = ShapeItem(ShapeKind.LINE, Pt(0.0, 0.0), Pt(200.0, 200.0), ink)
        assertTrue(Carry.of(listOf(line), listOf(inHole), listOf(line)).isEmpty)
    }

    @Test fun theInnermostHolderTakesWhatItHolds() {
        val outer = box(0.0, 0.0, 400.0, 400.0)
        val inner = box(100.0, 100.0, 100.0, 100.0)
        val note = ink(Pt(140.0, 140.0), Pt(160.0, 160.0))
        val carry = Carry.of(listOf(outer, inner), listOf(outer, inner, note), skip = listOf(outer, inner))
        assertEquals(listOf<CanvasItem>(note), carry.items)
        // Resizing only the outer leaves the inner's writing where the inner keeps it.
        val was = note.bounds().center
        outer.applyTransform(Affine.scaleAbout(Pt(0.0, 0.0), 2.0, 2.0))
        carry.follow(Affine.scaleAbout(Pt(0.0, 0.0), 2.0, 2.0), resize = true)
        assertEquals(0.0, note.bounds().center.distanceTo(was), 1e-6)
    }

    @Test fun aMoveOrTurnCarriesRigidlyAndATextBoxStaysUpright() {
        val frame = box(0.0, 0.0, 200.0, 100.0)
        val line = ink(Pt(20.0, 50.0), Pt(60.0, 50.0))
        val text = TextItem(Pt(120.0, 40.0), width = 40.0, text = "x", measurer = FakeTextMeasurer())
        val carry = Carry.of(listOf(frame), listOf(line, text), skip = listOf(frame))
        assertEquals(2, carry.items.size)
        val turn = Affine.rotateAbout(Pt(100.0, 50.0), PI / 2)
        val textCentre = turn.apply(text.bounds().center)
        carry.follow(turn, resize = false)
        val s = line.samples
        assertEquals(100.0, s.first().pos.x, 1e-3)
        assertEquals(-30.0, s.first().pos.y, 1e-3)
        assertEquals(textCentre.x, text.bounds().centerX, 1e-9)
        assertEquals(textCentre.y, text.bounds().centerY, 1e-9)
        assertEquals("upright, its own width", 40.0, text.width, 0.0)
    }

    @Test fun aResizeKeepsContentsTheirSizeAndTheirPlaceInTheFrame() {
        val frame = box(0.0, 0.0, 200.0, 100.0)
        val word = ink(Pt(90.0, 45.0), Pt(110.0, 55.0))
        val carry = Carry.of(listOf(frame), listOf(word), skip = listOf(frame))
        val w0 = word.bounds().w
        val c0 = word.bounds().center
        val widen = Affine.scaleAbout(Pt(0.0, 0.0), 2.0, 1.0)
        frame.applyTransform(widen)
        carry.follow(widen, resize = true)
        val b = word.bounds()
        assertEquals("as far across the widened box", c0.x * 2.0, b.centerX, 1e-3)
        assertEquals(c0.y, b.centerY, 1e-3)
        assertEquals("its own size", w0, b.w, 1e-3)
        carry.restore()
        assertEquals(c0.x, word.bounds().centerX, 1e-9)
    }

    @Test fun aTurnedHolderKeepsItsContentsInItsOwnFrameThroughAResize() {
        val frame = box(0.0, 0.0, 200.0, 100.0)
        frame.applyTransform(Affine.rotateAbout(frame.box.center, 0.5))
        val at = frame.toPage(Pt(150.0, 50.0))
        val dot = ink(at, Pt(at.x + 1.0, at.y))
        val carry = Carry.of(listOf(frame), listOf(dot), skip = listOf(frame))
        assertSame(dot, carry.items.single())
        val start = frame.toLocal(dot.bounds().center).let { Pt((it.x - frame.box.left) / frame.box.w, (it.y - frame.box.top) / frame.box.h) }
        val grow = Affine.scaleAbout(frame.box.center, 1.5, 1.5)
        frame.applyTransform(grow)
        carry.follow(grow, resize = true)
        val local = frame.toLocal(dot.bounds().center)
        val b = frame.box
        assertEquals(start.x, (local.x - b.left) / b.w, 1e-9)
        assertEquals(start.y, (local.y - b.top) / b.h, 1e-9)
    }
}
