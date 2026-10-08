package com.xnotes.core.tools

import com.xnotes.core.geometry.Pt
import com.xnotes.core.history.RefillShapes
import com.xnotes.core.model.DrawStyle
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.shapes.Head
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.shapes.ShapePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeConfigTest {

    private val ink = Rgba(10, 20, 30)
    private val teal = Rgba(0, 128, 128)

    @Test fun everyChipHasItsOwnKeyAndAPage() {
        assertEquals(ShapePresets.all.size, ShapePresets.all.map { it.key }.toSet().size)
        assertTrue(ShapePresets.pages.all { it.presets.size <= ShapePresets.ROWS * ShapePresets.COLUMNS })
        assertSame(ShapePresets.byKey("circle"), ShapePresets.forKind(ShapeKind.CIRCLE))
        assertEquals("rectangle", ShapePresets.forKind(ShapeKind.CURVE).key)
    }

    @Test fun choosingAChipTakesItsHeadsAndCount() {
        val c = ShapeConfig(n = 7, headStart = Head.FILLED).choose(ShapePresets.byKey("arrow")!!)
        assertEquals("arrow", c.preset)
        assertSame(ShapeKind.ARROW, c.shape)
        assertEquals(Head.NONE, c.headStart)
        assertEquals(Head.OPEN, c.headEnd)
        assertEquals(0, c.n)
    }

    @Test fun anUnknownChipFallsBackToTheRectangle() {
        assertSame(ShapeKind.RECTANGLE, ShapeConfig(preset = "hexagram").shape)
    }

    @Test fun theFillTakesItsOwnColourAtItsOpacity() {
        val c = ShapeConfig(fill = true, fillAlpha = 0.5, fillColor = teal)
        assertEquals(Rgba(0, 128, 128, 127), c.fillFor(ShapeKind.RECTANGLE, ink))
        assertEquals(Rgba(10, 20, 30, 127), c.copy(fillColor = null).fillFor(ShapeKind.RECTANGLE, ink))
        assertNull(c.fillFor(ShapeKind.LINE, ink))
    }

    @Test fun aStartedShapeCarriesTheChipsSettings() {
        val arrow = ShapeConfig().choose(ShapePresets.byKey("arrow")!!).copy(headStart = Head.HOLLOW_DIAMOND)
            .startShape(Pt(1.0, 2.0), ink, 3.0)
        assertSame(ShapeKind.ARROW, arrow.shape)
        assertEquals(Head.HOLLOW_DIAMOND, arrow.headStart)
        assertEquals(Pt(1.0, 2.0), arrow.end)
        val tri = ShapeConfig(preset = "triangle", headEnd = Head.OPEN, fill = true).startShape(Pt.ZERO, ink, 3.0)
        assertEquals("a closed shape never carries heads", Head.NONE, tri.headEnd)
        assertEquals(0.5, tri.adj[0], 0.0)
        assertTrue(tri.fillRgba != null)
    }

    @Test fun recolouringTheOutlineLeavesAFillOfItsOwnAlone() {
        val matching = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, ink.copy(a = 64))
        val own = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, teal.copy(a = 64))
        val red = Rgba(200, 0, 0)
        DrawStyle(red, 2.0).applyTo(matching)
        DrawStyle(red, 2.0).applyTo(own)
        assertEquals(red.copy(a = 64), matching.fillRgba)
        assertEquals(teal.copy(a = 64), own.fillRgba)
    }

    @Test fun refillingKeepsEachFillsOpacityAndUndoes() {
        val a = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, ink.copy(a = 64))
        val b = ShapeItem(ShapeKind.ELLIPSE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, teal.copy(a = 200))
        val bare = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0)
        val entries = RefillShapes.of(listOf(a, b, bare), teal)
        assertEquals("only fills that change", 1, entries.size)
        val cmd = RefillShapes(entries)
        cmd.redo()
        assertEquals(teal.copy(a = 64), a.fillRgba)
        assertNull(bare.fillRgba)
        cmd.undo()
        assertEquals(ink.copy(a = 64), a.fillRgba)
        val matched = RefillShapes.of(listOf(b), null).single()
        assertEquals(ink.copy(a = 200), matched.after)
    }
}
