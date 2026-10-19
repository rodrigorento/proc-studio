package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {

    @Test
    fun theListHoldsAsManyTasksAsTheFieldHasPlots() {
        assertEquals(4, TaskRules.MAX_TASKS)
    }

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
    fun aTaskBackFromItsSnoozeGoesFirst() {
        val next = TaskRules.next(listOf(task("a", 1), task("b", 2, deferredUntil = 40).copy(deferredBy = Task.BY_SNOOZE)), now = 50)

        assertEquals("b", next?.id)
    }

    @Test
    fun theTaskDueSoonestGoesFirst() {
        val anytime = task("a", 1)
        val thisWeek = task("b", 2).copy(dueAt = 900)
        val today = task("c", 3).copy(dueAt = 200)

        assertEquals("c", TaskRules.next(listOf(anytime, thisWeek, today), now = 50)?.id)
    }

    @Test
    fun aTaskWaitsForItsWindowToStart() {
        val tonight = TaskRules.withWhen(task("a", 1), TimeWindow(WhenChoices.TONIGHT, start = 100, end = 200), now = 50)

        assertNull(TaskRules.next(listOf(tonight), now = 50))
        assertEquals("a", TaskRules.next(listOf(tonight), now = 120)?.id)
        assertEquals(TimeWindow(WhenChoices.TONIGHT, 100, 200), TaskRules.window(tonight))
    }

    @Test
    fun choosingAnytimeClearsTheWindowButKeepsASnooze() {
        val snoozed = TaskRules.snooze(task("a", 1), now = 0)
        val dated = TaskRules.withWhen(snoozed, TimeWindow(WhenChoices.TODAY, start = 0, end = 900), now = 10)

        val anytime = TaskRules.withWhen(dated, null, now = 10)

        assertNull(TaskRules.window(anytime))
        assertEquals(snoozed.deferredUntil, anytime.deferredUntil)
        assertEquals(Task.BY_SNOOZE, anytime.deferredBy)
    }

    @Test
    fun asksForTasksWhenNothingIsDueNowAndThereIsRoom() {
        val forTomorrow = task("a", 1, deferredUntil = 100)

        assertTrue(TaskRules.shouldAskForTasks(emptyList(), now = 50))
        assertTrue(TaskRules.shouldAskForTasks(listOf(forTomorrow), now = 50))
        assertFalse(TaskRules.shouldAskForTasks(listOf(forTomorrow, task("b", 2)), now = 50))
        assertFalse(TaskRules.shouldAskForTasks(List(TaskRules.MAX_TASKS) { forTomorrow.copy(id = "$it") }, now = 50))
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
    fun checkInNeedsTwoPassesAndConsent() {
        val once = TaskRules.pass(task("a", 1))
        val twice = TaskRules.pass(once)

        assertFalse(TaskRules.needsCheckIn(once, checkInEnabled = true))
        assertTrue(TaskRules.needsCheckIn(twice, checkInEnabled = true))
        assertFalse(TaskRules.needsCheckIn(twice, checkInEnabled = false))
    }

    @Test
    fun aPassUsesUpTheSnoozeSoOlderTasksGetTheirTurn() {
        val returned = task("c", 3, deferredUntil = 40).copy(deferredBy = Task.BY_SNOOZE)

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
