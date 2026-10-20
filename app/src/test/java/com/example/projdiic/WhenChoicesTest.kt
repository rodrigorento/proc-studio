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

class WhenChoicesTest {

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

    private val friday = at(16, 14, 50)

    private fun label(window: TimeWindow?, now: Long = friday) = WhenText.label(window, now, utc, formats)

    @Test
    fun theFixedChips() {
        val chips = WhenChoices.chips(friday, utc)

        assertEquals(
            listOf(WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK, WhenChoices.THIS_MONTH),
            chips.map { it.key },
        )
    }

    @Test
    fun thisMonthRunsToTheLastDayAndIsLeftOutWhenTheWeekReachesAsFar() {
        val month = WhenChoices.thisMonth(friday, utc)

        assertEquals(friday, month.start)
        assertEquals(at(31, 23, 59) + 59_999, month.end)
        assertEquals(
            listOf(WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK),
            WhenChoices.chips(at(27, 10), utc).map { it.key },
        )
    }

    @Test
    fun thisMonthIsLeftOutOnItsLastDayAndKeptWhileTheMonthOutlastsTheWeek() {
        val sunday30August = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.AUGUST, 30, 10, 0)
        }.timeInMillis

        assertFalse(WhenChoices.chips(at(31, 10), utc).any { it.key == WhenChoices.THIS_MONTH })
        assertTrue(WhenChoices.chips(sunday30August, utc).any { it.key == WhenChoices.THIS_MONTH })
    }

    @Test
    fun onSundaysThisWeekIsLeftOut() {
        assertEquals(
            listOf(WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK, WhenChoices.THIS_MONTH),
            WhenChoices.chips(at(16, 23, 30), utc).map { it.key },
        )
        assertEquals(
            listOf(WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_MONTH),
            WhenChoices.chips(at(18, 10), utc).map { it.key },
        )
    }

    @Test
    fun labelsForTheChips() {
        assertEquals("Anytime", label(null))
        assertEquals("Today", label(WhenChoices.today(friday, utc)))
        assertEquals("Tonight", label(WhenChoices.tonight(friday, utc)))
        assertEquals("Tomorrow", label(WhenChoices.tomorrow(friday, utc)))
        assertEquals("This week", label(WhenChoices.thisWeek(friday, utc)))
        assertEquals("This month", label(WhenChoices.thisMonth(friday, utc)))
        assertEquals("15 days, 9 hours left", WhenText.gateLine(WhenChoices.thisMonth(friday, utc), friday, utc, formats))
    }

    @Test
    fun labelsForSpokenTimes() {
        assertEquals("Today 15:00", label(WhenParser.parse("Dentist at 3pm", friday, utc).window))
        assertEquals("Tomorrow 15:00", label(WhenParser.parse("Dentist tomorrow at 3pm", friday, utc).window))
        assertEquals("Monday", label(WhenParser.parse("Gym on Monday", friday, utc).window))
        assertEquals("25 Oct", label(WhenParser.parse("Form on October 25th", friday, utc).window))
        assertEquals("From 16:50", label(WhenParser.parse("Stretch in 2 hours", friday, utc).window))
    }

    @Test
    fun aTomorrowTaskSaysTodayOnTheDay() {
        val tomorrow = WhenChoices.tomorrow(friday, utc)

        assertEquals("Today", label(tomorrow, now = at(17, 10)))
    }

    @Test
    fun aMissedTimeIsSaidCalmly() {
        val today = WhenChoices.today(friday, utc)
        val dentist = WhenParser.parse("Dentist at 3pm", friday, utc).window

        assertEquals("Was for yesterday", label(today, now = at(17, 10)))
        assertEquals("Was for 15:00", label(dentist, now = at(16, 16)))
    }

    @Test
    fun snoozingTheAppLastsUntilMidnight() {
        assertEquals(at(17, 0), WhenChoices.startOfTomorrow(friday, utc))
        assertEquals(at(17, 0), WhenChoices.startOfTomorrow(at(16, 23, 59), utc))
    }

    @Test
    fun timeLeftIsInDaysAndHoursThenMinutes() {
        val sunday = at(18, 23, 59) + 59_999 // the end of this week

        assertEquals("2 days, 9 hours left", WhenText.timeLeft(sunday, friday))
        assertEquals("1 day, 1 hour left", WhenText.timeLeft(at(17, 15, 50), friday))
        assertEquals("1 day left", WhenText.timeLeft(at(17, 14, 50), friday))
        assertEquals("1 hour left", WhenText.timeLeft(sunday, at(18, 23)))
        assertEquals("30 minutes left", WhenText.timeLeft(sunday, at(18, 23, 30)))
        assertEquals("1 minute left", WhenText.timeLeft(sunday, sunday - 1_000))
    }

    @Test
    fun theGateLine() {
        assertNull(WhenText.gateLine(null, friday, utc, formats))
        assertEquals("9 hours left", WhenText.gateLine(WhenChoices.today(friday, utc), friday, utc, formats))
        assertEquals("2 days, 9 hours left", WhenText.gateLine(WhenChoices.thisWeek(friday, utc), friday, utc, formats))
        assertEquals(
            "1 day left",
            WhenText.gateLine(WhenParser.parse("Dentist tomorrow at 3pm", friday, utc).window, friday, utc, formats),
        )
        assertEquals(
            "From 16:50",
            WhenText.gateLine(WhenParser.parse("Stretch in 2 hours", friday, utc).window, friday, utc, formats),
        )
        assertEquals(
            "This was for yesterday",
            WhenText.gateLine(WhenChoices.today(friday, utc), at(17, 10), utc, formats),
        )
    }
}
