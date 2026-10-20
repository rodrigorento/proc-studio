package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FarmRulesTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = Calendar.OCTOBER): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, month, day, hour, minute)
        }.timeInMillis

    /** Friday 16 October 2026, 14:50. */
    private val friday = at(16, 14, 50)

    private fun planted(window: TimeWindow?, at: Long = friday) =
        TaskRules.withWhen(Task(id = "t", title = "Task", createdAt = at), window, at)

    private fun crop(window: TimeWindow?) = FarmRules.crop(planted(window), utc)

    @Test
    fun eachTimeChoiceGrowsItsOwnCrop() {
        assertEquals("radish", crop(WhenChoices.today(friday, utc)))
        assertEquals("radish", crop(WhenChoices.tonight(friday, utc)))
        assertEquals("lettuce", crop(WhenChoices.tomorrow(friday, utc)))
        assertEquals("carrot", crop(WhenChoices.thisWeek(friday, utc)))
        assertEquals("pumpkin", crop(WhenChoices.thisMonth(friday, utc)))
        assertEquals("sunflower", crop(null))
    }

    @Test
    fun aPickedDayGrowsTheCropOfItsWeek() {
        fun on(day: Int, month: Int = Calendar.OCTOBER) = crop(TimeWindow(WhenChoices.CUSTOM, friday, at(day, 23, month = month)))

        assertEquals("radish", on(16)) // today
        assertEquals("lettuce", on(17)) // the rest of this week, to Sunday
        assertEquals("lettuce", on(18))
        assertEquals("carrot", on(19)) // next week, Monday to Sunday
        assertEquals("carrot", on(25))
        assertEquals("corn", on(26)) // the week after
        assertEquals("corn", on(1, Calendar.NOVEMBER))
        assertEquals("pumpkin", on(2, Calendar.NOVEMBER)) // three weeks on
        assertEquals("pumpkin", on(8, Calendar.NOVEMBER))
        assertEquals("grape", on(9, Calendar.NOVEMBER)) // and later
        assertEquals("grape", on(25, Calendar.DECEMBER))
    }

    @Test
    fun everyCropHasArtForEveryStageItCanReach() {
        val corn = TimeWindow(WhenChoices.CUSTOM, friday, at(30, 23))
        val grapes = TimeWindow(WhenChoices.CUSTOM, friday, at(30, 23, month = Calendar.NOVEMBER))
        for (window in listOf(null, WhenChoices.today(friday, utc), WhenChoices.thisMonth(friday, utc), corn, grapes)) {
            val task = planted(window)
            val stages = CropArt.STAGES.getValue(FarmRules.crop(task, utc))
            for (hours in listOf(0, 1, 5, 30, 200, 2000)) {
                assertTrue(stages.contains(FarmRules.stage(task, friday + hours * 3_600_000L, utc)))
            }
        }
    }

    @Test
    fun aCropGrowsTowardsItsDueTimeAndGoesToSeedAfterIt() {
        val task = planted(WhenChoices.thisWeek(friday, utc)) // planted Friday 14:50, due Sunday night

        assertEquals("seed", FarmRules.stage(task, friday, utc))
        assertEquals("seed", FarmRules.stage(task, at(16, 23), utc)) // under a quarter of the way
        assertEquals("sprout", FarmRules.stage(task, at(17, 6), utc))
        assertEquals("growing", FarmRules.stage(task, at(17, 22), utc)) // past halfway, but not its day yet
        assertEquals("ripe", FarmRules.stage(task, at(18, 0), utc)) // ripe on the day it's due
        assertEquals("ripe", FarmRules.stage(task, at(18, 12), utc))
        assertEquals("ripe", FarmRules.stage(task, at(18, 23, 59), utc))
        assertEquals("overripe", FarmRules.stage(task, at(19, 9), utc))
    }

    @Test
    fun aCropIsRipeAtItsDueTime() {
        val task = planted(WhenChoices.today(friday, utc))
        val dueAsPlanted = Task(id = "n", title = "Now", createdAt = friday, dueAt = friday, dueHasTime = true)

        assertEquals("ripe", FarmRules.stage(task, task.dueAt, utc))
        assertEquals("overripe", FarmRules.stage(task, task.dueAt + 1, utc))
        assertEquals("ripe", FarmRules.stage(dueAsPlanted, friday, utc))
    }

    @Test
    fun changingTheTimeReplantsTheCrop() {
        val sunflower = planted(null, at = at(12, 9)) // planted on Monday for anytime

        val lettuce = TaskRules.withWhen(sunflower, WhenChoices.tomorrow(friday, utc), friday)
        val gym = TaskRules.withWhen(sunflower, WhenParser.parse("Gym on Monday", friday, utc).window, friday)

        assertEquals("lettuce", FarmRules.crop(lettuce, utc))
        assertEquals("seed", FarmRules.stage(lettuce, friday, utc))
        assertEquals("carrot", FarmRules.crop(gym, utc)) // next week
        assertEquals(sunflower.createdAt, lettuce.createdAt)
    }

    @Test
    fun aCropGrowsWithTimeAlone() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))

        assertEquals(
            listOf("seed", "sprout", "growing", "ripe", "overripe"),
            listOf(friday, at(17, 6), at(17, 22), at(18, 9), at(19, 9)).map { FarmRules.stage(carrot, it, utc) },
        )
        assertEquals("seed", FarmRules.stage(planted(null), friday, utc))
    }

    @Test
    fun aCropIsReadyToHarvestOnceItIsRipe() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))

        assertFalse(FarmRules.isReady(carrot, friday, utc))
        assertFalse(FarmRules.isReady(carrot, at(17, 12), utc))
        assertTrue(FarmRules.isReady(carrot, at(18, 9), utc))
        assertTrue(FarmRules.isReady(carrot, at(19, 9), utc)) // gone to seed: still harvested
        assertTrue(FarmRules.isReady(planted(null), at(19, 15), utc))
    }

    @Test
    fun aCropForADayFarOffStaysGrowingUntilItsDay() {
        val form = planted(WhenParser.parse("Form on November 10th", friday, utc).window)

        assertEquals("growing", FarmRules.stage(form, at(31, 12), utc)) // 60% of the way, but not its day yet
        assertEquals("ripe", FarmRules.stage(form, at(41, 9), utc)) // 10 November
    }

    @Test
    fun aCropIsDueOnceItIsRipeOrItsTaskIsDueToday() {
        assertTrue(FarmRules.isDue(planted(WhenChoices.today(friday, utc)), friday, utc))
        val carrot = planted(WhenChoices.thisWeek(friday, utc))
        assertFalse(FarmRules.isDue(carrot, friday, utc))
        assertTrue(FarmRules.isDue(carrot, at(18, 9), utc)) // ripe on Sunday morning, due that night
        assertFalse(FarmRules.isDue(planted(null), friday, utc))
        assertTrue(FarmRules.isDue(planted(null), at(19, 15), utc)) // in bloom
    }

    @Test
    fun aDueCropComesUpBeforeOneThatIsNot() {
        val monday = at(19, 9)
        val sunflower = planted(null).copy(id = "sunflower") // in bloom by Monday afternoon
        val carrot = planted(WhenChoices.thisWeek(monday, utc), at = monday).copy(id = "carrot") // a seed till Sunday

        assertEquals("sunflower", TaskRules.next(listOf(carrot, sunflower), at(19, 15), utc)?.id)
        assertEquals("carrot", TaskRules.next(listOf(carrot, sunflower), at(17, 9), utc)?.id) // neither due: soonest
    }

    @Test
    fun aCropWaitingForItsTimeIsNotRipeBeforeThen() {
        val monday = WhenParser.parse("Gym on Monday", friday, utc).window!!
        val gym = planted(monday)

        assertEquals("growing", FarmRules.stage(gym, monday.start - 60_000, utc))
        assertEquals("ripe", FarmRules.stage(gym, monday.start, utc))
    }

    @Test
    fun aSunflowerBloomsAfterAFewDaysAndStaysInBloom() {
        val task = planted(null)

        assertEquals("seed", FarmRules.stage(task, friday, utc))
        assertEquals("growing", FarmRules.stage(task, at(18, 5), utc))
        assertEquals("bloom", FarmRules.stage(task, at(19, 15), utc))
        assertEquals("bloom", FarmRules.stage(task, at(30, 15), utc))
    }
}
