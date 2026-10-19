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
    fun eachWateringGrowsACropAStageUntilItIsRipe() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))
        val sunflower = planted(null)

        assertEquals(
            listOf("seed", "sprout", "growing", "ripe", "ripe"),
            (0..4).map { FarmRules.stage(carrot.copy(waterings = it), friday) },
        )
        assertEquals(
            listOf("seed", "sprout", "growing", "bloom"),
            (0..3).map { FarmRules.stage(sunflower.copy(waterings = it), friday) },
        )
        assertEquals("ripe", FarmRules.stage(carrot.copy(waterings = 1), at(17, 12))) // growing by itself, then watered
        assertEquals("overripe", FarmRules.stage(carrot.copy(waterings = 3), at(19, 9)))
    }

    @Test
    fun aCropIsReadyToHarvestOnceItIsRipe() {
        val carrot = planted(WhenChoices.thisWeek(friday, utc))

        assertFalse(FarmRules.isReady(carrot, friday))
        assertFalse(FarmRules.isReady(carrot.copy(waterings = 2), friday))
        assertTrue(FarmRules.isReady(carrot.copy(waterings = 3), friday))
        assertTrue(FarmRules.isReady(carrot, at(19, 9))) // gone to seed: still harvested
        assertTrue(FarmRules.isReady(planted(null), at(19, 15)))
    }

    @Test
    fun aSunflowerBloomsAfterAFewDaysAndStaysInBloom() {
        val task = planted(null)

        assertEquals("seed", FarmRules.stage(task, friday))
        assertEquals("growing", FarmRules.stage(task, at(18, 0)))
        assertEquals("bloom", FarmRules.stage(task, at(19, 15)))
        assertEquals("bloom", FarmRules.stage(task, at(30, 15)))
    }

    @Test
    fun theFieldIsThirstyEachMorningUntilItIsWatered() {
        val wateredFriday = FarmRules.wateredOn(friday, utc)

        assertTrue(FarmRules.isWatered(wateredFriday, at(16, 23), utc))
        assertFalse(FarmRules.isWatered(wateredFriday, at(17, 7), utc))
        assertFalse(FarmRules.isWatered(0L, friday, utc))
    }

    @Test
    fun aFieldNobodyWateredGetsRainInTheEvening() {
        val wateredYesterday = FarmRules.wateredOn(at(15, 12), utc)

        assertFalse(FarmRules.rainsOnHome(wateredYesterday, at(16, 15), utc))
        assertFalse(FarmRules.rainsOnHome(wateredYesterday, at(16, 17, 59), utc))
        assertTrue(FarmRules.rainsOnHome(wateredYesterday, at(16, 18), utc))
        assertFalse(FarmRules.rainsOnHome(FarmRules.wateredOn(at(16, 9), utc), at(16, 20), utc))
    }

    @Test
    fun theFieldDriesOutAtLocalMidnightAcrossAClockChange() {
        val london = TimeZone.getTimeZone("Europe/London") // the clocks go back at 02:00 on Sunday 25 October
        fun local(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance(london).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }.timeInMillis
        val wateredBeforeTheChange = FarmRules.wateredOn(local(25, 1, 30), london)

        assertTrue(FarmRules.isWatered(wateredBeforeTheChange, local(25, 23, 59), london))
        assertFalse(FarmRules.isWatered(wateredBeforeTheChange, local(26, 0, 1), london))
        assertFalse(FarmRules.isWatered(FarmRules.wateredOn(local(24, 23), london), local(25, 0, 30), london))
        assertTrue(FarmRules.rainsOnHome(FarmRules.wateredOn(local(24, 9), london), local(25, 18), london))
    }
}
