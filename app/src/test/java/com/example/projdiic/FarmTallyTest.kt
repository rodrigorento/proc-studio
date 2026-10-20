package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FarmTallyTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, 0)
        }.timeInMillis

    @Test
    fun harvestsCountFromMondaysFarmDay() {
        // Monday the 19th to Sunday the 25th; a harvest at 2:00 on Monday is still Sunday's.
        val harvests = listOf(
            FarmTally.Harvest("a", at(18, 20)),
            FarmTally.Harvest("b", at(19, 2)),
            FarmTally.Harvest("c", at(19, 9)),
            FarmTally.Harvest("d", at(21, 15)),
        )
        assertEquals(2, FarmTally.harvestsThisWeek(harvests, at(22, 10), utc))
    }

    @Test
    fun aHarvestJustAfterTheScreensClockTickedCounts() {
        // The screen's time moves once a second; a harvest can be stamped a moment after it.
        val harvests = listOf(FarmTally.Harvest("a", at(22, 10) + 400))
        assertEquals(1, FarmTally.harvestsThisWeek(harvests, at(22, 10), utc))
    }

    @Test
    fun undoTakesBackThatTasksHarvest() {
        val harvests = FarmTally.withHarvest(FarmTally.withHarvest(emptyList(), "a", at(20, 9)), "b", at(20, 10))

        assertEquals(listOf(FarmTally.Harvest("b", at(20, 10))), FarmTally.withoutHarvest(harvests, "a"))
    }

    @Test
    fun oldRecordsAreLetGo() {
        val old = FarmTally.Harvest("old", at(20, 9) - 61L * 24 * 3_600_000)
        assertEquals(listOf("new"), FarmTally.withHarvest(listOf(old), "new", at(20, 9)).map { it.taskId })
    }

    @Test
    fun recordsSurviveBeingSaved() {
        val harvests = listOf(FarmTally.Harvest("a-1", 1L), FarmTally.Harvest("b-2", 2L))
        assertEquals(harvests, FarmTally.decodeHarvests(FarmTally.encodeHarvests(harvests)))
        assertEquals(emptyList<FarmTally.Harvest>(), FarmTally.decodeHarvests(""))
        assertEquals(emptyList<FarmTally.Harvest>(), FarmTally.decodeHarvests("garbage;"))
    }

    @Test
    fun theLinesSayHowItsGoing() {
        assertEquals("1 harvest this week", FarmTally.harvestLine(1))
        assertEquals("3 harvests this week", FarmTally.harvestLine(3))
        assertNull(FarmTally.harvestLine(0))
    }
}
