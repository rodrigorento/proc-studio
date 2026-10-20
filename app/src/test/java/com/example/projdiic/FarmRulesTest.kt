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
    fun aSpokenTimeGrowsTheCropForHowFarAwayItIs() {
        fun spoken(text: String) = crop(WhenParser.parse(text, friday, utc).window)

        assertEquals("radish", spoken("Dentist at 3pm"))
        assertEquals("lettuce", spoken("Dentist tomorrow at 3pm"))
        assertEquals("carrot", spoken("Gym on Monday"))
        assertEquals("pumpkin", spoken("Form on October 25th"))
    }

    @Test
    fun everyCropHasArtForEveryStageItCanReach() {
        for (window in listOf(null, WhenChoices.today(friday, utc), WhenChoices.thisMonth(friday, utc))) {
            val task = planted(window)
            val stages = CropArt.STAGES.getValue(FarmRules.crop(task, utc))
            for (hours in listOf(0, 1, 5, 30, 200, 2000)) {
                assertTrue(stages.contains(FarmRules.stage(task, friday + hours * 3_600_000L)))
            }
        }
    }

    @Test
    fun aCropGrowsTowardsItsDueTimeAndGoesToSeedAfterIt() {
        val task = planted(WhenChoices.thisWeek(friday, utc)) // planted Friday 14:50, due Sunday night

        assertEquals("seed", FarmRules.stage(task, friday))
        assertEquals("sprout", FarmRules.stage(task, at(16, 23)))
        assertEquals("growing", FarmRules.stage(task, at(17, 12)))
        assertEquals("ripe", FarmRules.stage(task, at(18, 12)))
        assertEquals("ripe", FarmRules.stage(task, at(18, 23, 59)))
        assertEquals("overripe", FarmRules.stage(task, at(19, 9)))
    }

    @Test
    fun aCropIsRipeAtItsDueTime() {
        val task = planted(WhenChoices.today(friday, utc))
        val dueAsPlanted = Task(id = "n", title = "Now", createdAt = friday, dueAt = friday, dueHasTime = true)

        assertEquals("ripe", FarmRules.stage(task, task.dueAt))
        assertEquals("overripe", FarmRules.stage(task, task.dueAt + 1))
        assertEquals("ripe", FarmRules.stage(dueAsPlanted, friday))
    }

    @Test
    fun changingTheTimeReplantsTheCrop() {
        val sunflower = planted(null, at = at(12, 9)) // planted on Monday for anytime

        val lettuce = TaskRules.withWhen(sunflower, WhenChoices.tomorrow(friday, utc), friday)
        val gym = TaskRules.withWhen(sunflower, WhenParser.parse("Gym on Monday", friday, utc).window, friday)

        assertEquals("lettuce", FarmRules.crop(lettuce, utc))
        assertEquals("seed", FarmRules.stage(lettuce, friday))
        assertEquals("carrot", FarmRules.crop(gym, utc))
        assertEquals(sunflower.createdAt, lettuce.createdAt)
    }

    @Test
    fun aCropGrowsWithTimeAlone() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))

        assertEquals(
            listOf("seed", "sprout", "growing", "ripe", "overripe"),
            listOf(friday, at(16, 22), at(17, 12), at(18, 9), at(19, 9)).map { FarmRules.stage(carrot, it) },
        )
        assertEquals("seed", FarmRules.stage(planted(null), friday))
    }

    @Test
    fun aCropIsReadyToHarvestOnceItIsRipe() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))

        assertFalse(FarmRules.isReady(carrot, friday))
        assertFalse(FarmRules.isReady(carrot, at(17, 12)))
        assertTrue(FarmRules.isReady(carrot, at(18, 9)))
        assertTrue(FarmRules.isReady(carrot, at(19, 9))) // gone to seed: still harvested
        assertTrue(FarmRules.isReady(planted(null), at(19, 15)))
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

        assertEquals("growing", FarmRules.stage(gym, monday.start - 60_000))
        assertEquals("ripe", FarmRules.stage(gym, monday.start))
    }

    @Test
    fun aSunflowerBloomsAfterAFewDaysAndStaysInBloom() {
        val task = planted(null)

        assertEquals("seed", FarmRules.stage(task, friday))
        assertEquals("growing", FarmRules.stage(task, at(18, 0)))
        assertEquals("bloom", FarmRules.stage(task, at(19, 15)))
        assertEquals("bloom", FarmRules.stage(task, at(30, 15)))
    }
}
