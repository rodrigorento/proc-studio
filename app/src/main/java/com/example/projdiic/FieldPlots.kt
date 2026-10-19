package com.example.projdiic

import kotlin.math.abs

/** Which plot of the home field a point is on. Points are shares of the lawn image's width (x) and height (y). */
object FieldPlots {
    /**
     * The plot at ([x], [y]); where plots overlap on screen, the one in front. The first [planted] plots hold beds; the
     * rest are flat grass. With [solid] (whether a plot's bed picture is drawn at a point of its card, from (0, 0) at
     * the card's top left to (1, 1)), a bed is wherever its picture is, tall crops included; without it, its outline.
     * Null off every plot.
     */
    fun at(
        x: Float,
        y: Float,
        planted: Int = FieldArt.PLOTS.size,
        solid: ((plot: Int, u: Float, v: Float) -> Boolean)? = null,
    ): Int? = FieldArt.PLOTS.indices.reversed().firstOrNull { i ->
        val plot = FieldArt.PLOTS[i]
        when {
            i >= planted -> inside(plot, x, y, height = 0f)
            solid == null -> inside(plot, x, y)
            else -> {
                val u = (x - plot.card.left) / plot.card.width
                val v = (y - plot.card.top) / plot.card.height
                u in 0f..1f && v in 0f..1f && solid(i, u, v)
            }
        }
    }

    /** Whether the point is on the plot as the camera sees it: its footprint, raised [height] for a bed. */
    fun inside(plot: FieldArt.Plot, x: Float, y: Float, height: Float = FieldArt.TAP_HEIGHT): Boolean {
        val across = abs(x - plot.middle.x) / FieldArt.FOOTPRINT_HALF_WIDTH
        val below = y - plot.middle.y
        val off = when {
            below > 0f -> below
            below < -height -> -height - below
            else -> 0f
        }
        return across + off / FieldArt.FOOTPRINT_HALF_HEIGHT <= 1f
    }
}
