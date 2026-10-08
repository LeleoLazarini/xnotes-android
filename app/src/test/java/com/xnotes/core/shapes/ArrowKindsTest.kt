package com.xnotes.core.shapes

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowKindsTest {

    private val box = Rect(10.0, 20.0, 160.0, 80.0)
    private val ink = Rgba(0, 0, 0)

    private fun corners(k: ShapeKind) = k.build(box, ShapeParams(k.defaultAdj, k.defaultN)).corners

    @Test fun eachDirectionPointsItsOwnWay() {
        assertTrue(corners(ArrowKinds.RIGHT).any { it.distanceTo(Pt(box.right, box.centerY)) < 1e-9 })
        assertTrue(corners(ArrowKinds.LEFT).any { it.distanceTo(Pt(box.left, box.centerY)) < 1e-9 })
        assertTrue(corners(ArrowKinds.UP).any { it.distanceTo(Pt(box.centerX, box.top)) < 1e-9 })
        assertTrue(corners(ArrowKinds.DOWN).any { it.distanceTo(Pt(box.centerX, box.bottom)) < 1e-9 })
        val both = corners(ArrowKinds.LEFT_RIGHT)
        assertTrue(both.any { it.distanceTo(Pt(box.left, box.centerY)) < 1e-9 } && both.any { it.distanceTo(Pt(box.right, box.centerY)) < 1e-9 })
    }

    @Test fun aTurnedArrowsKnobsTurnWithIt() {
        val up = ShapeItem(ArrowKinds.UP, box.topLeft, Pt(box.right, box.bottom), ink, 2.0)
        val head = up.knobDots()[1]
        // Turned up, the head's base runs across the box: the knob sits on its left end.
        assertEquals(box.left, head.x, 1e-9)
        assertEquals("half the shorter side below the tip", box.top + 40.0, head.y, 1e-9)
        up.dragKnob(1, Pt(head.x, head.y + 20.0))
        val longer = up.adj[1]
        assertTrue("dragging it away from the tip lengthens the head: $longer", longer > ArrowKinds.UP.defaultAdj[1])
        assertEquals("the thickness is untouched", ArrowKinds.UP.defaultAdj[0], up.adj[0], 1e-12)
    }

    @Test fun aCalloutsTailReachesItsKnob() {
        val c = ShapeItem(ArrowKinds.RECT_CALLOUT, box.topLeft, Pt(box.right, box.bottom), ink, 2.0)
        val tip = c.knobDots().single()
        assertTrue("the tail starts below the box", tip.y > box.bottom)
        assertTrue("the bounds reach the tip", c.bounds().bottom >= tip.y)
        assertTrue(c.contains(tip))
        c.dragKnob(0, Pt(box.right + 60.0, box.centerY))
        val moved = c.knobDots().single()
        assertEquals(box.right + 60.0, moved.x, 1e-9)
        assertTrue(c.bounds().right >= moved.x)
    }

    @Test fun aThoughtBubbleTrailsBubblesToItsTip() {
        val g = ArrowKinds.THOUGHT.build(box, ShapeParams(ArrowKinds.THOUGHT.defaultAdj))
        assertEquals("the cloud and three bubbles", 4, g.paths.single().contours.size)
    }

    @Test fun aLineCalloutsLeaderIsAStrokeOnly() {
        val g = ArrowKinds.LINE_CALLOUT.build(box, ShapeParams(ArrowKinds.LINE_CALLOUT.defaultAdj))
        assertEquals(listOf(PathRole.OUTLINE, PathRole.STROKE_ONLY), g.paths.map { it.role })
    }

    @Test fun aMissingSecondValueKeepsItsDefault() {
        val k = ArrowKinds.RIGHT.knobs[0]
        val adj = k.drag(Pt(box.right - 40.0, box.centerY - 30.0), box, DoubleArray(0), 0)
        assertEquals(2, adj.size)
        assertEquals(ArrowKinds.RIGHT.defaultAdj[1], adj[1], 0.0)
    }

    @Test fun theArrowsPageHasTwentyFourChips() {
        val keys = ShapePresets.arrows.presets.map { it.key }
        assertEquals(24, keys.size)
        assertEquals("arrow_right", keys.first())
        assertEquals("burst", keys.last())
        assertEquals(3, ShapePresets.pages.size)
    }
}
