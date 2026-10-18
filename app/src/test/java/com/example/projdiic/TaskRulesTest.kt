package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {

    private fun task(id: String, createdAt: Long, deferredUntil: Long = 0L) =
        Task(id = id, title = id, createdAt = createdAt, deferredUntil = deferredUntil)

    @Test
    fun nextIsTheOldestWhenNothingIsScheduled() {
        val next = TaskRules.next(listOf(task("b", 2), task("a", 1)), now = 10)

        assertEquals("a", next?.id)
    }

    @Test
    fun nextSkipsTasksScheduledForLater() {
        val next = TaskRules.next(listOf(task("a", 1, deferredUntil = 100), task("b", 2)), now = 50)

        assertEquals("b", next?.id)
    }

    @Test
    fun aTaskWhosePickedTimeHasComeGoesFirst() {
        val next = TaskRules.next(listOf(task("a", 1), task("b", 2, deferredUntil = 40)), now = 50)

        assertEquals("b", next?.id)
    }

    @Test
    fun nextIsNullWhenEverythingIsScheduled() {
        assertNull(TaskRules.next(listOf(task("a", 1, deferredUntil = 100)), now = 50))
    }

    @Test
    fun snoozeIsFiveMinutesAndUsesTheOneSnooze() {
        val snoozed = TaskRules.snooze(task("a", 1), now = 1_000)

        assertEquals(1_000 + 5 * 60_000L, snoozed.deferredUntil)
        assertEquals(Task.BY_SNOOZE, snoozed.deferredBy)
        assertTrue(snoozed.snoozeUsed)
    }

    @Test
    fun pickTimeDefersWithoutUsingTheSnooze() {
        val picked = TaskRules.pickTime(task("a", 1), at = 9_000)

        assertEquals(9_000, picked.deferredUntil)
        assertEquals(Task.BY_TIME, picked.deferredBy)
        assertFalse(picked.snoozeUsed)
    }

    @Test
    fun checkInNeedsTwoPassesAndConsent() {
        val once = TaskRules.pass(task("a", 1))
        val twice = TaskRules.pass(once)

        assertFalse(TaskRules.needsCheckIn(once, checkInEnabled = true))
        assertTrue(TaskRules.needsCheckIn(twice, checkInEnabled = true))
        assertFalse(TaskRules.needsCheckIn(twice, checkInEnabled = false))
    }

    @Test
    fun aPassUsesUpThePickedTimeSoOlderTasksGetTheirTurn() {
        val returned = task("c", 3, deferredUntil = 40).copy(deferredBy = Task.BY_TIME)

        val passed = TaskRules.pass(returned)

        assertEquals(0L, passed.deferredUntil)
        assertEquals("", passed.deferredBy)
        assertEquals("a", TaskRules.next(listOf(task("a", 1), passed), now = 50)?.id)
    }

    @Test
    fun cleanTitleTrimsAndCapitalises() {
        assertEquals("Reply to the group chat", TaskRules.cleanTitle("  reply   to the group chat "))
        assertNull(TaskRules.cleanTitle("   "))
    }

    @Test
    fun cleanTitleCapsLongDictation() {
        val title = TaskRules.cleanTitle("word ".repeat(100))

        assertTrue(title!!.length <= TaskRules.MAX_TITLE_LENGTH)
    }
}
