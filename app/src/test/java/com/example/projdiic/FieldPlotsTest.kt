package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldPlotsTest {

    private val back = FieldArt.PLOTS[0]
    private val left = FieldArt.PLOTS[1]

    @Test
    fun aTapOnABedsMiddleIsThatPlot() {
        FieldArt.PLOTS.forEachIndexed { i, plot -> assertEquals(i, FieldPlots.at(plot.middle.x, plot.middle.y)) }
    }

    @Test
    fun aBedsOutlineStandsAboveItsFootprintButAnEmptyPlotsDoesNot() {
        val aboveBack = back.middle.y - FieldArt.FOOTPRINT_HALF_HEIGHT - FieldArt.TAP_HEIGHT * 0.9f

        assertEquals(0, FieldPlots.at(back.middle.x, aboveBack))
        assertNull(FieldPlots.at(back.middle.x, aboveBack, planted = 0))
    }

    @Test
    fun withTheBedsPicturesATapGoesToTheFrontBedDrawnThere() {
        val front = FieldArt.PLOTS[3]
        // A tall crop in the front bed, reaching up over the back bed: its card is solid there, the back bed's too.
        val x = front.middle.x
        val y = back.middle.y
        val tallFront = { plot: Int, _: Float, v: Float -> plot == 3 && v > 0.05f || plot == 0 }

        assertEquals(3, FieldPlots.at(x, y, solid = tallFront))
        assertEquals(0, FieldPlots.at(x, y, solid = { plot: Int, _: Float, _: Float -> plot == 0 }))
        // The card's own coordinates: its left and top edges are 0, its right and bottom 1.
        val seen = mutableListOf<Pair<Float, Float>>()
        FieldPlots.at(front.card.left + front.card.width / 4, front.card.top + front.card.height / 2, planted = 4,
            solid = { plot: Int, u: Float, v: Float -> if (plot == 3) seen += u to v; false })
        assertEquals(0.25f, seen.single().first, 0.001f)
        assertEquals(0.5f, seen.single().second, 0.001f)
    }

    @Test
    fun whereBedsOverlapTheOneInFrontIsTapped() {
        // Between the back bed's left side and the left bed's back: inside both outlines, and the left bed is in front.
        val x = (back.middle.x + left.middle.x) / 2
        val y = back.middle.y + FieldArt.FOOTPRINT_HALF_HEIGHT * 0.3f
        assertEquals(true, FieldPlots.inside(back, x, y))
        assertEquals(true, FieldPlots.inside(left, x, y))
        assertEquals(1, FieldPlots.at(x, y))
    }

    @Test
    fun anEmptyPlotInFrontIsFlatSoItDoesNotTakeTapsOnTheBedBehindIt() {
        val x = (back.middle.x + left.middle.x) / 2
        val y = back.middle.y + FieldArt.FOOTPRINT_HALF_HEIGHT * 0.3f

        assertEquals(0, FieldPlots.at(x, y, planted = 1))
        assertEquals(1, FieldPlots.at(left.middle.x, left.middle.y, planted = 1))
    }

    @Test
    fun theLawnsCornersAndTheSkyAboveAreNoPlot() {
        assertNull(FieldPlots.at(0.01f, 0.6f))
        assertNull(FieldPlots.at(0.5f, 0.0f))
        assertNull(FieldPlots.at(0.5f, 0.99f))
    }
}
