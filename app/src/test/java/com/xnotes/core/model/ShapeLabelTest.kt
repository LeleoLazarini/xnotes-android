package com.xnotes.core.model

import com.xnotes.core.FakeRenderer
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.history.EditLabel
import com.xnotes.core.pal.FontFace
import com.xnotes.core.shapes.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ShapeLabelTest {

    private val measurer = FakeTextMeasurer()
    private val ink = Rgba(200, 30, 30, 255)
    private val style = TextStyle(ink, 10.0, FontFace.SANS)

    private fun labelled(text: String = "Hello", kind: ShapeKind = ShapeKind.RECTANGLE, w: Double = 200.0, h: Double = 100.0) =
        ShapeItem(kind, Pt(0.0, 0.0), Pt(w, h), ink, 2.0).apply { label = ShapeLabel(text, style, measurer) }

    @Test fun theLabelWrapsToTheTextRectAndCentresOnIt() {
        val s = labelled("one\ntwo")
        val t = s.localGeometry().textRect
        val r = s.labelRect()!!
        assertEquals(t.w, r.w, 1e-9)
        assertEquals("two lines of 13 px", 26.0, r.h, 1e-9)
        assertEquals(t.centerX, r.centerX, 1e-9)
        assertEquals(t.centerY, r.centerY, 1e-9)
    }

    @Test fun aThinShapeStillWrapsTwoLinesWide() {
        val s = labelled(w = 4.0)
        assertEquals(2 * 13.0, s.labelRect()!!.w, 1e-9)
    }

    @Test fun aLongLabelSpillsEvenlyAndTheBoundsHoldIt() {
        val s = labelled("a\nb\nc\nd\ne\nf\ng\nh\ni\nj", h = 40.0)
        val r = s.labelRect()!!
        assertEquals(130.0, r.h, 1e-9)
        assertTrue(s.bounds().top <= r.top && s.bounds().bottom >= r.bottom)
        assertEquals(20.0, r.centerY, 1e-9)
    }

    @Test fun noLabelOrAnEmptyOneAddsNothing() {
        val s = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(200.0, 100.0), ink, 2.0)
        val plain = s.bounds()
        assertNull(s.labelRect())
        s.label = ShapeLabel("", style, measurer)
        assertNull(s.labelRect())
        assertEquals(plain, s.bounds())
    }

    @Test fun pressingTheLabelHitsTheShapeButTheEraserLeavesTextAlone() {
        val s = labelled()
        val middle = Pt(100.0, 50.0)
        assertTrue("the label is part of the shape", s.contains(middle))
        assertTrue(s.touches(middle, 1.0))
        assertFalse("an eraser over the words alone erases nothing", s.intersectsCircle(100.0, 50.0, 2.0))
        assertNull(s.erasedBy(100.0, 50.0, 2.0))
    }

    @Test fun theLabelPaintsTurnedWithItsShapeAboutTheRightCentre() {
        val s = labelled()
        s.applyTransform(Affine.rotateAbout(Pt(0.0, 0.0), 0.5))
        val r = FakeRenderer()
        s.paint(r)
        assertTrue(r.ops.any { it.startsWith("drawTextTurned:Hello@") && it.endsWith("${s.rotation}") })
        val placed = r.textRects.single()
        val want = s.toPage(s.labelRect()!!.center)
        assertEquals(want.x, placed.centerX, 1e-9)
        assertEquals(want.y, placed.centerY, 1e-9)
    }

    @Test fun aHiddenLabelAndTheBodyAlonePaintNoText() {
        val s = labelled()
        s.labelHidden = true
        assertTrue(FakeRenderer().also(s::paint).textRects.isEmpty())
        s.labelHidden = false
        assertTrue(FakeRenderer().also(s::paintBody).textRects.isEmpty())
        assertEquals(1, FakeRenderer().also(s::paint).textRects.size)
    }

    @Test fun aQuarterTurnKeepsALabelledRectangleTurned() {
        val plain = ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(200.0, 100.0), ink, 2.0)
        plain.applyTransform(Affine.rotateAbout(Pt(100.0, 50.0), PI / 2))
        assertEquals("an unlabelled one is just upright", 0.0, plain.rotation, 0.0)
        val s = labelled()
        s.applyTransform(Affine.rotateAbout(Pt(100.0, 50.0), PI / 2))
        assertEquals("its words turn with it", PI / 2, s.rotation, 1e-9)
        assertEquals(200.0, s.box.w, 1e-9)
    }

    @Test fun aRestyleRecoloursALabelInTheOutlinesColourOnly() {
        val s = labelled()
        DrawStyle(Rgba(0, 0, 255, 255), 2.0).applyTo(s)
        assertEquals(Rgba(0, 0, 255, 255), s.label!!.style.rgba)
        s.label = s.label!!.withStyle(style.copy(rgba = Rgba(0, 200, 0, 255)))
        DrawStyle(Rgba(9, 9, 9, 255), 2.0).applyTo(s)
        assertEquals(Rgba(0, 200, 0, 255), s.label!!.style.rgba)
    }

    @Test fun aCopyKeepsTheLabel() {
        val s = labelled()
        val copy = s.deepCopy(measurer) as ShapeItem
        assertSame(s.label, copy.label)
        assertFalse(copy.labelHidden)
    }

    @Test fun editingALabelUndoes() {
        val s = labelled()
        val before = s.label
        val after = before!!.withText("Bye")
        s.label = after
        val cmd = EditLabel(s, before, after)
        cmd.undo()
        assertSame(before, s.label)
        cmd.redo()
        assertSame(after, s.label)
        val page = Page(500.0, 500.0, mutableListOf(s))
        assertNotNull(cmd.touched { page })
        assertTrue(before.sameAs(before.withText("Hello")))
        assertFalse(before.sameAs(after))
    }
}
