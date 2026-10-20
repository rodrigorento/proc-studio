package com.example.projdiic

import org.junit.Assert.assertEquals
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

    private fun packets(said: TimeWindow?, now: Long = friday) = SeedPackets.forTask(said, now, utc)

    @Test
    fun withoutATimeEveryChipIsAPacketFromSoonest() {
        val packets = packets(null)

        assertEquals(
            listOf(WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK, WhenChoices.THIS_MONTH),
            packets.map { it.window.key },
        )
        assertEquals(listOf("radish", "lettuce", "carrot", "pumpkin"), packets.map { it.crop })
        assertTrue(packets.none { it.said })
    }

    @Test
    fun aTimeSaidInTheTaskComesFirst() {
        val dentist = said("Dentist tomorrow at 3pm")
        val packets = packets(dentist)

        assertEquals(dentist, packets.first().window)
        assertEquals("lettuce", packets.first().crop)
        assertTrue(packets.first().said)
        assertEquals(5, packets.size)
        assertEquals(1, packets.count { it.said })
    }

    @Test
    fun aTimeSaidThatIsAChipMarksThatChip() {
        val rent = said("Pay rent this month")
        val packets = packets(rent)

        assertEquals(4, packets.size)
        assertEquals(listOf(WhenChoices.THIS_MONTH), packets.filter { it.said }.map { it.window.key })
        assertEquals(rent, packets.single { it.said }.window)
    }

    @Test
    fun tonightSaidIsTheTodayPacket() {
        val late = at(16, 23, 30)

        for (now in listOf(friday, late)) {
            val packets = packets(said("Call mom tonight", now), now)
            assertEquals(4, packets.size)
            assertEquals(listOf(WhenChoices.TODAY), packets.filter { it.said }.map { it.window.key })
        }
    }

    @Test
    fun plantingATaskTakesTheTimeOutOfItsTitleAndPicksThatTime() {
        val gym = SeedPackets.planting("gym on Monday", friday, utc)!!
        val book = SeedPackets.planting("read a book", friday, utc)!!

        assertEquals("Gym", gym.title)
        assertEquals(0, gym.picked)
        assertTrue(gym.packets[gym.picked].said)
        assertEquals("Read a book", book.title)
        assertEquals(-1, book.picked)
    }

    @Test
    fun thereIsNothingToPlantWithoutATitle() {
        assertNull(SeedPackets.planting("", friday, utc))
        assertNull(SeedPackets.planting("   ", friday, utc))
    }

    @Test
    fun aChoiceLeftOpenPastMidnightIsMadeAgainForTheNewDay() {
        val saturday = at(17, 0, 30)
        val today = packets(null).first { it.window.key == WhenChoices.TODAY }
        val dentist = packets(said("Dentist tomorrow at 3pm")).first()

        assertEquals(WhenChoices.today(saturday, utc), SeedPackets.current(today, saturday, utc).window)
        assertEquals(dentist, SeedPackets.current(dentist, saturday, utc))
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
