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
    // As Android writes them in the UK: the year only when it isn't this one (2026), and a span in one month says it once.
    private val formats = WhenText.Formats(
        time = { format("HH:mm", it) },
        weekday = { format("EEEE", it) },
        date = { date(it) },
        range = { first, last ->
            (if (format("MM yyyy", first) == format("MM yyyy", last)) format("d", first) else format("d MMM", first)) + " – " + date(last)
        },
    )

    private fun date(time: Long) = format(if (format("yyyy", time) == "2026") "d MMM" else "d MMM yyyy", time)

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

    private fun label(seed: SeedPackets.Seed, now: Long = friday) = SeedPackets.label(seed, now, formats, utc)

    private fun planting(text: String, now: Long = friday) = SeedPackets.planting(text, now, utc)

    private fun on(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis

    @Test
    fun sixSeedsFromTodayThenAWeekEach() {
        val seeds = seeds()

        assertEquals(listOf("radish", "lettuce", "carrot", "corn", "pumpkin", "grape"), seeds.map { it.crop })
        assertEquals(listOf(0 to 0, 1 to 2, 3 to 9, 10 to 16, 17 to 23, 24 to 365), seeds.map { it.firstDay to it.lastDay })
        assertEquals(
            listOf("Today", "Tomorrow – 18 Oct", "19 – 25 Oct", "26 Oct – 1 Nov", "2 – 8 Nov", "From 9 Nov"),
            seeds.map { label(it) },
        )
    }

    @Test
    fun theLettuceCoversWhatIsLeftOfThisWeekAfterToday() {
        val saturday = at(17, 10)
        val sunday = at(18, 10)
        val monday = at(19, 10)

        assertEquals("Tomorrow", label(seeds(saturday)[1], saturday))
        assertEquals("Tomorrow – 25 Oct", label(seeds(monday)[1], monday))
        // On a Sunday there's nothing left of the week: no lettuce.
        assertEquals(listOf("radish", "carrot", "corn", "pumpkin", "grape"), seeds(sunday).map { it.crop })
        assertEquals(1 to 7, seeds(sunday)[1].let { it.firstDay to it.lastDay })
    }

    @Test
    fun aWeekInAnotherYearSaysItsYear() {
        val wednesday = on(2026, Calendar.DECEMBER, 23, 10)

        assertEquals(
            listOf("Today", "Tomorrow – 27 Dec", "28 Dec – 3 Jan 2027", "4 – 10 Jan 2027", "11 – 17 Jan 2027", "From 18 Jan 2027"),
            seeds(wednesday).map { label(it, wednesday) },
        )
    }

    @Test
    fun aSeedOpensOnTheLastDayOfItsFirstWeekAtAnyTime() {
        val lettuce = SeedPackets.pick(seeds()[1])
        assertEquals(2, lettuce.day)
        assertNull(lettuce.minute)
        assertFalse(lettuce.said)

        assertEquals(9, SeedPackets.pick(seeds()[2]).day)
        assertEquals(30, SeedPackets.pick(seeds()[5]).day) // the grapes: Sunday 15 November
    }

    @Test
    fun aPickIsDueAtTheEndOfItsDayOrAtItsTime() {
        val carrot = seeds()[2]

        val anyTime = SeedPackets.window(SeedPackets.Pick(carrot, day = 7, minute = null), friday, utc)
        assertEquals(friday, anyTime.start)
        assertEquals(at(23, 23, 59) + 59_999, anyTime.end)
        assertFalse(anyTime.hasTime)
        assertEquals("carrot", FarmRules.crop(anyTime, friday, utc))

        val atThree = SeedPackets.window(SeedPackets.Pick(carrot, day = 3, minute = 15 * 60), friday, utc)
        assertEquals(at(19, 15), atThree.end)
        assertTrue(atThree.hasTime)
    }

    @Test
    fun everyDayOfASeedGrowsThatSeedsCrop() {
        for (now in listOf(friday, at(18, 10), at(19, 10), on(2026, Calendar.DECEMBER, 23, 10))) {
            for (seed in seeds(now)) {
                for (day in seed.firstDay..seed.lastDay) {
                    val window = SeedPackets.window(SeedPackets.Pick(seed, day, minute = null), now, utc)
                    assertEquals(seed.crop, FarmRules.crop(window, now, utc))
                }
            }
        }
    }

    @Test
    fun aTimeSaidOpensOnTheSeedOfItsWeekWithItsDayAndTime() {
        val dentist = planting("Dentist tomorrow at 3pm")!!
        val said = dentist.said!!

        assertEquals("Dentist", dentist.title)
        assertEquals("lettuce", said.seed.crop) // Saturday: still this week
        assertEquals(1, said.day)
        assertEquals(15 * 60, said.minute)
        assertTrue(said.said)

        val gym = planting("Gym on Monday")!!.said!!
        assertEquals("carrot", gym.seed.crop)
        assertEquals(3, gym.day)
        assertNull(gym.minute)

        val rent = planting("Pay rent this month")!!.said!!
        assertEquals("corn", rent.seed.crop)
        assertEquals(15, rent.day) // 31 October

        assertNull(planting("read a book")!!.said)
    }

    @Test
    fun aPartOfTheDaySaidKeepsItsDeadline() {
        val morning = planting("Call the bank tomorrow morning")!!.said!!
        assertEquals(12 * 60, morning.minute)
        assertEquals(at(17, 12), SeedPackets.window(morning, friday, utc).end)

        assertEquals(18 * 60, planting("Post the form this afternoon", at(16, 9))!!.said!!.minute)
        assertNull(planting("Call mom tonight")!!.said!!.minute)
    }

    @Test
    fun tonightSaidIsTodayAndStillWaitsForTheEvening() {
        val said = planting("Call mom tonight")!!.said!!

        assertEquals("radish", said.seed.crop)
        assertEquals(0, said.day)
        assertEquals(at(16, 19), SeedPackets.window(said, friday, utc).start)
    }

    @Test
    fun aDaySaidForLaterGrowsFromPlantingLikeADayPicked() {
        val said = planting("Call mom tomorrow")!!.said!!

        assertEquals(friday, SeedPackets.window(said, friday, utc).start)
    }

    @Test
    fun aTimeSaidWeeksAwayIsGrapes() {
        val christmas = planting("Form on December 25th")!!.said!!
        assertEquals("grape", christmas.seed.crop)
        assertEquals(70, christmas.day)

        // Further than the grapes reach (a year), they stretch to it.
        val later = SeedPackets.seedFor(400, friday, utc)
        assertEquals("grape", later.crop)
        assertEquals(400, later.lastDay)
    }

    @Test
    fun aPickLeftOpenPastMidnightKeepsTheDateItShowed() {
        val lateFriday = at(16, 23, 50)
        val saturday = at(17, 0, 10)
        val dentist = planting("Dentist tomorrow at 3pm", lateFriday)!!.said!!
        val tonight = SeedPackets.Pick(seeds(lateFriday)[0], day = 0, minute = 23 * 60 + 30)

        assertEquals(at(17, 15), SeedPackets.window(dentist, saturday, utc, shownAt = lateFriday).end)
        assertTrue(SeedPackets.isPast(tonight, saturday, utc, shownAt = lateFriday))
    }

    @Test
    fun aNewDayMovesThePickToItKeepingItsDate() {
        val lateFriday = at(16, 23, 50)
        val sunday = SeedPackets.Pick(seeds(lateFriday)[1], day = 2, minute = 9 * 60, said = true)

        val moved = SeedPackets.rebase(sunday, from = lateFriday, to = at(17, 0, 10), zone = utc)
        assertEquals(1, moved.day)
        assertEquals("lettuce", moved.seed.crop)
        assertEquals(1 to 1, moved.seed.firstDay to moved.seed.lastDay)
        assertEquals(9 * 60, moved.minute)
        assertTrue(moved.said)

        // Tomorrow's lettuce is today's radish.
        assertEquals("radish", SeedPackets.rebase(moved, from = at(17, 0, 10), to = at(18, 0, 10), zone = utc).seed.crop)

        // Into Monday, next week's carrot is this week's lettuce.
        val lateSunday = at(18, 23, 50)
        val carrot = SeedPackets.rebase(SeedPackets.pick(seeds(lateSunday)[1]), from = lateSunday, to = at(19, 0, 10), zone = utc)
        assertEquals("lettuce", carrot.seed.crop)
        assertEquals(6, carrot.day)
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
    fun aWeeksCalendarIsThatWeekWithOnlyItsDaysOpen() {
        val lettuce = SeedCalendar.pages(seeds()[1], friday, utc).single().weeks.single()

        assertEquals((12..18).toList(), lettuce.map { it?.dayOfMonth }) // Monday to Sunday
        assertEquals(listOf(17, 18), lettuce.filterNotNull().filter { it.open }.map { it.dayOfMonth })
        assertEquals(1, lettuce.first { it?.dayOfMonth == 17 }!!.day)

        val corn = SeedCalendar.pages(seeds()[3], friday, utc).single()
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 1), corn.weeks.single().map { it?.dayOfMonth })
        assertTrue(corn.weeks.single().all { it!!.open })
        assertEquals("October – November", SeedCalendar.title(corn, friday, utc, Locale.UK))
        assertEquals("October", SeedCalendar.title(SeedCalendar.pages(seeds()[1], friday, utc).single(), friday, utc, Locale.UK))

        val wednesday = on(2026, Calendar.DECEMBER, 23, 10)
        val newYear = SeedCalendar.pages(seeds(wednesday)[2], wednesday, utc).single()
        assertEquals("December – January 2027", SeedCalendar.title(newYear, wednesday, utc, Locale.UK))
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), SeedCalendar.weekdayLetters(Locale.UK))
    }

    @Test
    fun theGrapesCalendarIsAMonthAPage() {
        val pages = SeedCalendar.pages(seeds()[5], friday, utc)

        assertEquals(12, pages.size) // November to October next year
        val november = pages.first()
        assertEquals(listOf(null, null, null, null, null, null, 1), november.weeks.first().map { it?.dayOfMonth }) // 1 November is a Sunday
        assertEquals((9..30).toList(), november.weeks.flatten().filterNotNull().filter { it.open }.map { it.dayOfMonth })
        assertEquals("November", SeedCalendar.title(november, friday, utc, Locale.UK))
        assertEquals("January 2027", SeedCalendar.title(pages[2], friday, utc, Locale.UK))
        assertEquals((1..16).toList(), pages.last().weeks.flatten().filterNotNull().filter { it.open }.map { it.dayOfMonth })
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
        assertEquals("corn, ripe", FarmViews.cropAndStage("corn", "ripe"))
        assertEquals("grape seeds", FarmViews.cropAndStage("grape", "seed"))
        assertEquals("grapes, sprouting", FarmViews.cropAndStage("grape", "sprout"))
        assertEquals("Grapes", FarmViews.cropName("grape"))
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
        assertEquals("Taxes grows into corn, ripe by 31 Oct.", FarmViews.plantedLine("Taxes", "corn", "by 31 Oct", bloomDays = 0))
        assertEquals("Trip grows into grapes, ripe by 25 Dec.", FarmViews.plantedLine("Trip", "grape", "by 25 Dec", bloomDays = 0))
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
