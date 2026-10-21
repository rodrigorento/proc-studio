package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GrowthNewsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, 0)
        }.timeInMillis

    /** Carrots planted on Monday the 12th, due on Friday the 16th: a sprout from Tuesday, growing from Wednesday. */
    private val carrots = Task(id = "c", title = "Gym", createdAt = at(12, 0), dueAt = at(16, 23), whenKey = "this_week")

    private fun news(seen: Map<String, String>, now: Long, vararg tasks: Task) =
        GrowthNews.news(tasks.toList(), seen, now, utc).map { it.task.id to it.stage }

    @Test
    fun aCropThatGrewSinceItWasLastSeenIsNews() {
        assertEquals(listOf("c" to "sprout"), news(mapOf("c" to "seed"), at(13, 12), carrots))
        assertEquals(listOf("c" to "ripe"), news(mapOf("c" to "growing"), at(16, 12), carrots))
    }

    @Test
    fun onlyItsLatestStageIsNewsAfterALongWhile() {
        assertEquals(listOf("c" to "ripe"), news(mapOf("c" to "seed"), at(16, 12), carrots))
    }

    @Test
    fun nothingIsNewsWithoutGrowth() {
        assertTrue(news(mapOf("c" to "sprout"), at(13, 12), carrots).isEmpty())
        // Never seen before: just planted, so nothing to tell yet.
        assertTrue(news(emptyMap(), at(13, 12), carrots).isEmpty())
        // Planted again for later, back to seed.
        assertTrue(news(mapOf("c" to "ripe"), at(12, 1), carrots).isEmpty())
    }

    @Test
    fun goingToSeedIsNotNews() {
        assertTrue(news(mapOf("c" to "ripe"), at(18, 12), carrots).isEmpty())
    }

    @Test
    fun whatWasSeenIsEveryCropAsItIsNow() {
        val lettuce = carrots.copy(id = "l", createdAt = at(13, 0))

        assertEquals(mapOf("c" to "sprout", "l" to "seed"), GrowthNews.seen(listOf(carrots, lettuce), at(13, 12), utc))
    }

    @Test
    fun cropsWithTheSameNewsAreToldOnce() {
        val rent = carrots.copy(id = "r", title = "Pay the rent")
        val pumpkin = Task(id = "p", title = "Flights", createdAt = at(1, 0), dueAt = at(31, 23), whenKey = "this_month")
        val told = GrowthNews.told(GrowthNews.news(listOf(carrots, rent, pumpkin), mapOf("c" to "seed", "r" to "seed", "p" to "seed"), at(13, 12), utc))

        assertEquals(listOf("Your carrots sprouted!", "Your pumpkin sprouted!"), told.map { it.headline })
        assertEquals(setOf("c", "r"), told.first().taskIds)
    }

    @Test
    fun theNewsSaysWhatHappenedToWhichCrop() {
        assertEquals("Your carrots sprouted!", GrowthNews.headline(FarmRules.CARROT, "sprout"))
        assertEquals("Your radishes are growing!", GrowthNews.headline(FarmRules.RADISH, "growing"))
        assertEquals("Your lettuce is ripe!", GrowthNews.headline(FarmRules.LETTUCE, "ripe"))
        assertEquals("Your grapes are ripe!", GrowthNews.headline(FarmRules.GRAPE, "ripe"))
        assertEquals("Your pumpkin sprouted!", GrowthNews.headline(FarmRules.PUMPKIN, "sprout"))
        assertEquals("Your corn is growing!", GrowthNews.headline(FarmRules.CORN, "growing"))
        assertEquals("Your sunflower bloomed!", GrowthNews.headline(FarmRules.SUNFLOWER, "bloom"))
    }
}
