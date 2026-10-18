package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateTriggerTest {

    private val config = TriggerConfig(swipes = 3, minutes = 2, cooldownMinutes = 1)

    @Test
    fun opensAfterNSwipes() {
        val trigger = GateTrigger()

        assertNull(trigger.onScroll(1_000, config).reason)
        assertNull(trigger.onScroll(3_000, config).reason)
        val third = trigger.onScroll(5_000, config)

        assertEquals(TriggerReason.SWIPES, third.reason)
        assertEquals(3, third.swipes)
        assertEquals(0, trigger.swipes)
    }

    @Test
    fun ignoresScrollEventsInsideTheDebounce() {
        val trigger = GateTrigger()
        trigger.onScroll(1_000, config)

        val bounce = trigger.onScroll(1_500, config)

        assertFalse(bounce.counted)
        assertEquals(1, trigger.swipes)
    }

    @Test
    fun opensAfterMinutesOfContinuousScrolling() {
        val trigger = GateTrigger()
        val manySwipes = config.copy(swipes = 100)

        trigger.onScroll(1_000, manySwipes)
        assertNull(trigger.onScroll(41_000, manySwipes).reason)
        assertNull(trigger.onScroll(81_000, manySwipes).reason)

        assertEquals(TriggerReason.MINUTES, trigger.onScroll(121_000, manySwipes).reason)
    }

    @Test
    fun aLongBreakStartsANewStretch() {
        val trigger = GateTrigger()
        trigger.onScroll(1_000, config)
        trigger.onScroll(3_000, config)

        trigger.onScroll(3_000 + 4 * 60_000, config)

        assertEquals(1, trigger.swipes)
    }

    @Test
    fun cooldownKeepsTheGateAway() {
        val trigger = GateTrigger()
        trigger.startCooldown(0, config)

        listOf(1_000L, 3_000L, 5_000L, 7_000L).forEach { now ->
            val result = trigger.onScroll(now, config)
            assertTrue(result.counted)
            assertNull(result.reason)
        }
        assertEquals(0, trigger.swipes)

        assertNull(trigger.onScroll(61_000, config).reason)
        assertNull(trigger.onScroll(63_000, config).reason)
        assertEquals(TriggerReason.SWIPES, trigger.onScroll(65_000, config).reason)
    }
}
