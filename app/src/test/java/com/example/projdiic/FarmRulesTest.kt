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
        assertTrue(FarmRules.rainsOnHome(wateredYesterday, at(16, 18), utc))
        assertFalse(FarmRules.rainsOnHome(FarmRules.wateredOn(at(16, 9), utc), at(16, 20), utc))
    }
}
