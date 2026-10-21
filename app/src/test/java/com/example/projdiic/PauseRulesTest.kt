package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PauseRulesTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }.timeInMillis

    /** Friday 16 October 2026, 14:50. */
    private val friday = at(16, 14, 50)

    private fun crop(id: String, window: TimeWindow?, watered: Boolean = true) =
        TaskRules.planted(id, id, Task.VIA_TEXT, window, at(16, 9)).copy(wateredAt = if (watered) at(16, 9) else 0L)

    private val today = WhenChoices.today(at(16, 9), utc)
    private val sunday = WhenChoices.thisWeek(at(16, 9), utc)

    private fun pick(tasks: List<Task>, mayAsk: Boolean = true, facilitator: Boolean = false, checkIns: Boolean = false) =
        PauseRules.pick(tasks, friday, mayAskForTasks = mayAsk, facilitator = facilitator, checkInEnabled = checkIns, zone = utc)

    @Test
    fun aDueCropGetsTheFirmPauseBeforeAnyWatering() {
        val due = pick(listOf(crop("dry", sunday, watered = false), crop("due", today)))

        assertEquals(GateScreen.Gate("due"), due.screen)
        assertEquals("due", due.why)
    }

    @Test
    fun aDueCropPassedTwiceGetsTheCheckInWhenItsOn() {
        val passed = crop("due", today).copy(passes = 2)

        assertEquals(GateScreen.CheckIn("due"), pick(listOf(passed), checkIns = true).screen)
        assertEquals(GateScreen.Gate("due"), pick(listOf(passed), checkIns = false).screen)
    }

    @Test
    fun aDryCropOpensTheFarmEveryTime() {
        val water = pick(listOf(crop("a", sunday), crop("b", sunday, watered = false)), mayAsk = false)

        assertEquals(GateScreen.AddTasks, water.screen)
        assertEquals("water", water.why)
    }

    @Test
    fun aWateredFieldWithNothingDueLetsHimScroll() {
        val none = pick(listOf(crop("a", sunday), crop("b", sunday)))

        assertNull(none.screen)
        assertEquals("all_watered", none.why)
    }

    @Test
    fun anEmptyFieldAsksForTasksOnceASession() {
        assertEquals(GateScreen.AddTasks, pick(emptyList()).screen)
        assertEquals("ask", pick(emptyList()).why)

        assertNull(pick(emptyList(), mayAsk = false).screen)
        assertEquals("asked_this_session", pick(emptyList(), mayAsk = false).why)
        assertEquals(GateScreen.AddTasks, pick(emptyList(), mayAsk = false, facilitator = true).screen)
    }

    @Test
    fun aDueCropOnItsFiveMoreMinutesWaits() {
        val snoozed = TaskRules.snooze(crop("due", today), friday - 60_000)

        val skipped = pick(listOf(snoozed))
        assertNull(skipped.screen)
        assertEquals("on_five_more_minutes", skipped.why)
    }

    @Test
    fun fiveMoreMinutesHoldTheWateringToo() {
        val snoozed = TaskRules.snooze(crop("due", today, watered = false), friday - 60_000)

        assertNull(pick(listOf(snoozed, crop("dry", sunday, watered = false))).screen)
        assertEquals("on_five_more_minutes", pick(listOf(snoozed)).why)
    }

    @Test
    fun theFarmUsesUpTheAskWhenThereWasNothingForNow() {
        val tonight = TaskRules.planted("tonight", "tonight", Task.VIA_TEXT, WhenChoices.tonight(friday, utc), friday).copy(wateredAt = 0L)

        val water = pick(listOf(tonight))
        assertEquals("water", water.why)
        assertTrue(water.asks) // it shows what the ask would have: no asking again this session
        assertFalse(pick(listOf(crop("dry", sunday, watered = false))).asks)
        assertTrue(pick(emptyList()).asks)
    }

    @Test
    fun theFacilitatorsPauseAlwaysShowsSomething() {
        val watered = listOf(crop("a", sunday))

        assertNull(pick(watered).screen)
        val forced = pick(watered, facilitator = true)
        assertEquals(GateScreen.AddTasks, forced.screen)
        assertEquals("facilitator", forced.why)
    }
}
