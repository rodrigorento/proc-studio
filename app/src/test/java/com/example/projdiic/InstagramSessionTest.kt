package com.example.projdiic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramSessionTest {

    private val minute = 60_000L

    @Test
    fun asksForTasksOncePerSession() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)

        assertTrue(session.mayAskForTasks)
        session.askedForTasks()
        assertFalse(session.mayAskForTasks)
    }

    @Test
    fun nodsAtACropStillGrowingOncePerSession() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)

        assertTrue(session.mayNod)
        session.nodded()
        assertFalse(session.mayNod)
        assertTrue(session.mayAskForTasks)

        session.left(now = 1 * minute)
        session.seen(now = 12 * minute)
        assertTrue(session.mayNod)
    }

    @Test
    fun aQuickTripAwayKeepsTheSession() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)
        session.askedForTasks()

        session.left(now = 5 * minute)
        session.seen(now = 14 * minute)

        assertFalse(session.mayAskForTasks)
    }

    @Test
    fun aLongerBreakStartsANewSession() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)
        session.askedForTasks()

        session.left(now = 5 * minute)
        session.seen(now = 16 * minute)

        assertTrue(session.mayAskForTasks)
    }

    @Test
    fun theFirstSightStartsASession() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.askedForTasks()

        session.seen(now = 1_000)

        assertTrue(session.mayAskForTasks)
    }

    @Test
    fun scrollingKeepsTheSessionGoing() {
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)
        session.askedForTasks()

        session.seen(now = 8 * minute)
        session.seen(now = 16 * minute)

        assertFalse(session.mayAskForTasks)
    }

    @Test
    fun aLongQuietSpellEndsTheSessionEvenWithoutLeaving() {
        // The phone locked inside Instagram: the lock screen doesn't count as leaving it.
        val session = InstagramSession(gapMs = 10 * minute)
        session.seen(now = 0)
        session.askedForTasks()

        session.seen(now = 30 * minute)

        assertTrue(session.mayAskForTasks)
    }
}
