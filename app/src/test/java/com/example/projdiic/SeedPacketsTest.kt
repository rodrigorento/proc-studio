package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SeedPacketsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val formats = WhenText.Formats(
        time = { format("HH:mm", it) },
        weekday = { format("EEEE", it) },
        date = { format("d MMM", it) },
    )

    private fun format(pattern: String, time: Long) =
        SimpleDateFormat(pattern, Locale.UK).apply { timeZone = utc }.format(time)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }.timeInMillis

    /** Friday 16 October 2026, 14:50. */
    private val friday = at(16, 14, 50)

    private fun said(text: String, now: Long = friday) = WhenParser.parse(text, now, utc).window

    private fun seeds(now: Long = friday) = SeedPackets.seeds(now, utc)

    @Test
    fun fourSeedsBackToBackEachGrowingItsOwnCrop() {
        val seeds = seeds()

        assertEquals(listOf("Today", "In 7 days", "In 15 days", "In 1 month"), seeds.map { it.name })
        assertEquals(listOf("radish", "lettuce", "carrot", "pumpkin"), seeds.map { it.crop })
        assertEquals(listOf(0 to 0, 1 to 7, 8 to 15, 16 to 31), seeds.map { it.firstDay to it.lastDay })
    }

    @Test
    fun aMonthRunsToTheSameDateNextMonthOrItsLastDay() {
        assertEquals(30, seeds(at(31, 10)).last().lastDay) // 31 October to 30 November
    }

    @Test
    fun aSeedOpensOnItsLastDayAtAnyTime() {
        val pick = SeedPackets.pick(seeds()[1])

        assertEquals(7, pick.day)
        assertNull(pick.minute)
        assertFalse(pick.said)
    }

    @Test
    fun aPickIsDueAtTheEndOfItsDayOrAtItsTime() {
        val lettuce = seeds()[1]

        val anyTime = SeedPackets.window(SeedPackets.Pick(lettuce, day = 7, minute = null), friday, utc)
        assertEquals(friday, anyTime.start)
        assertEquals(at(23, 23, 59) + 59_999, anyTime.end)
        assertFalse(anyTime.hasTime)
        assertEquals("lettuce", FarmRules.crop(anyTime, friday, utc))

        val atThree = SeedPackets.window(SeedPackets.Pick(lettuce, day = 3, minute = 15 * 60), friday, utc)
        assertEquals(at(19, 15), atThree.end)
        assertTrue(atThree.hasTime)
    }

    @Test
    fun everyDayOfASeedGrowsThatSeedsCrop() {
        for (seed in seeds()) {
            for (day in seed.firstDay..seed.lastDay) {
                val window = SeedPackets.window(SeedPackets.Pick(seed, day, minute = null), friday, utc)
                assertEquals(seed.name, seed.crop, FarmRules.crop(window, friday, utc))
            }
        }
    }

    @Test
    fun aTimeSaidOpensOnItsSeedWithItsDayAndTime() {
        val dentist = SeedPackets.planting("Dentist tomorrow at 3pm", friday, utc)!!
        val said = dentist.said!!

        assertEquals("Dentist", dentist.title)
        assertEquals("In 7 days", said.seed.name)
        assertEquals(1, said.day)
        assertEquals(15 * 60, said.minute)
        assertTrue(said.said)

        val rent = SeedPackets.planting("Pay rent this month", friday, utc)!!.said!!
        assertEquals("In 15 days", rent.seed.name)
        assertEquals(15, rent.day) // 31 October
        assertNull(rent.minute)

        assertNull(SeedPackets.planting("read a book", friday, utc)!!.said)
    }

    @Test
    fun tonightSaidIsTodayAndStillWaitsForTheEvening() {
        val said = SeedPackets.planting("Call mom tonight", friday, utc)!!.said!!

        assertEquals("Today", said.seed.name)
        assertEquals(0, said.day)
        assertEquals(at(16, 19), SeedPackets.window(said, friday, utc).start)
    }

    @Test
    fun aTimeSaidBeyondAMonthIsAPumpkinOnThatDay() {
        val said = SeedPackets.planting("Form on December 25th", friday, utc)!!.said!!

        assertEquals("In 1 month", said.seed.name)
        assertEquals(70, said.day)
        assertTrue(said.day <= said.seed.lastDay)
    }

    @Test
    fun aTimeAlreadyGoneTodayCantBePlanted() {
        val today = seeds()[0]

        assertTrue(SeedPackets.isPast(SeedPackets.Pick(today, 0, minute = 9 * 60), friday, utc))
        assertFalse(SeedPackets.isPast(SeedPackets.Pick(today, 0, minute = 18 * 60), friday, utc))
        assertFalse(SeedPackets.isPast(SeedPackets.Pick(today, 0, minute = null), friday, utc))
    }

    @Test
    fun theTimeOffersTheNextHourTodayAndNineOnALaterDay() {
        assertEquals(15 * 60, SeedPackets.defaultMinute(day = 0, now = friday, zone = utc))
        assertEquals(9 * 60, SeedPackets.defaultMinute(day = 3, now = friday, zone = utc))
        assertEquals(23 * 60 + 59, SeedPackets.defaultMinute(day = 0, now = at(16, 23, 10), zone = utc))
    }

    @Test
    fun theCalendarOpensOnlyTheSeedsDays() {
        val october = SeedCalendar.months(seeds()[1], friday, utc, firstDayOfWeek = Calendar.MONDAY).single()
        val days = october.weeks.flatten().filterNotNull()

        assertEquals(at(1, 0), october.firstDay)
        assertEquals(listOf(null, null, null, 1, 2, 3, 4), october.weeks.first().map { it?.dayOfMonth }) // 1 October is a Thursday
        assertEquals((17..23).toList(), days.filter { it.open }.map { it.dayOfMonth })
        assertEquals(1, days.first { it.dayOfMonth == 17 }.day)
        assertEquals(31, days.size)
    }

    @Test
    fun aSeedRunningIntoNextMonthShowsBothMonths() {
        val months = SeedCalendar.months(seeds(at(27, 10))[1], at(27, 10), utc, firstDayOfWeek = Calendar.MONDAY)

        assertEquals(2, months.size)
        assertEquals((28..31).toList(), months[0].weeks.flatten().filterNotNull().filter { it.open }.map { it.dayOfMonth })
        assertEquals((1..3).toList(), months[1].weeks.flatten().filterNotNull().filter { it.open }.map { it.dayOfMonth })
    }

    @Test
    fun thereIsNothingToPlantWithoutATitle() {
        assertNull(SeedPackets.planting("", friday, utc))
        assertNull(SeedPackets.planting("   ", friday, utc))
    }

    @Test
    fun whenEachCropIsRipe() {
        fun due(window: TimeWindow?) = WhenText.due(window, friday, utc, formats)

        assertEquals("by tonight", due(WhenChoices.today(friday, utc)))
        assertEquals("by tonight", due(WhenChoices.tonight(friday, utc)))
        assertEquals("by tomorrow night", due(WhenChoices.tomorrow(friday, utc)))
        assertEquals("by Sunday", due(WhenChoices.thisWeek(friday, utc)))
        assertEquals("by 31 Oct", due(WhenChoices.thisMonth(friday, utc)))
        assertEquals("by 15:00", due(said("Dentist at 3pm")))
        assertEquals("by 15:00 tomorrow", due(said("Dentist tomorrow at 3pm")))
        assertEquals("by 18:00 on Monday", due(said("Gym on Monday at 6pm")))
        assertEquals("by 15:00 on 25 Oct", due(said("Form on October 25th at 3pm")))
        assertNull(due(null))
    }

    @Test
    fun theGateSaysTheCropAndHowFarItHasGrown() {
        assertEquals("radish seeds", FarmViews.cropAndStage("radish", "seed"))
        assertEquals("carrot, ripe", FarmViews.cropAndStage("carrot", "ripe"))
        assertEquals("lettuce, gone to seed", FarmViews.cropAndStage("lettuce", "overripe"))
    }

    @Test
    fun theFieldSaysWhenToHarvestEachCrop() {
        fun planted(window: TimeWindow?) = TaskRules.withWhen(Task(id = "t", title = "Gym", createdAt = friday), window, friday)
        fun line(task: Task, now: Long = friday) = FarmViews.harvestLine(task, now, utc, formats)

        val gym = planted(WhenChoices.thisWeek(friday, utc))
        assertEquals("Harvest by Sunday", line(gym))
        assertEquals("Harvest by 15:00 tomorrow", line(planted(said("Dentist tomorrow at 3pm"))))
        assertEquals("Harvest now", line(gym, at(18, 9)))
        assertEquals("Gone to seed", line(gym, at(19, 9)))

        val book = planted(null)
        assertEquals("Blooms in 2 days", line(book))
        assertEquals("Blooms tomorrow", line(book, at(17, 20)))
        assertEquals("Blooms today", line(book, at(18, 9)))
        assertEquals("In bloom", line(book, at(18, 11)))
    }

    @Test
    fun theLightPauseSaysTheCropIsGrowingAndTheTimeLeft() {
        fun planted(window: TimeWindow?) = TaskRules.withWhen(Task(id = "t", title = "Gym", createdAt = friday), window, friday)
        fun line(task: Task) = FarmViews.nodLine(task, friday, utc, formats)

        assertEquals("Carrots growing · 2 days, 9 hours left", line(planted(WhenChoices.thisWeek(friday, utc))))
        assertEquals("Pumpkin growing · 15 days, 9 hours left", line(planted(WhenChoices.thisMonth(friday, utc))))
        assertEquals("Sunflower growing · blooms in 2 days", line(planted(null)))
    }

    @Test
    fun thePlantedLineSaysWhatItGrowsIntoAndWhen() {
        assertEquals(
            "Call the dentist grows into lettuce, ripe by 15:00 tomorrow.",
            FarmViews.plantedLine("Call the dentist", "lettuce", "by 15:00 tomorrow", bloomDays = 0),
        )
        assertEquals("Gym grows into carrots, ripe by Sunday.", FarmViews.plantedLine("Gym", "carrot", "by Sunday", bloomDays = 0))
        assertEquals("Rent grows into a pumpkin, ripe by 31 Oct.", FarmViews.plantedLine("Rent", "pumpkin", "by 31 Oct", bloomDays = 0))
        assertEquals(
            "Read a book grows into a sunflower that blooms in 2 days.",
            FarmViews.plantedLine("Read a book", "sunflower", null, bloomDays = 2),
        )
        // Planted in the small hours, it blooms the next evening: the same day its card names.
        val book = TaskRules.withWhen(Task(id = "t", title = "Read a book", createdAt = at(17, 3)), null, at(17, 3))
        assertEquals(1, FarmViews.bloomDays(book, at(17, 3), utc))
        assertEquals("Read a book grows into a sunflower that blooms tomorrow.", FarmViews.plantedLine("Read a book", "sunflower", null, bloomDays = 1))
    }
}
