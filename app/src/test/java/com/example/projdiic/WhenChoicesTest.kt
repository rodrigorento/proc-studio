package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            listOf(null, WhenChoices.TODAY, WhenChoices.TONIGHT, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK),
            chips.map { it?.key },
        )
    }

    @Test
    fun lateAtNightAndOnSundaysThereAreFewerChips() {
        assertEquals(
            listOf(null, WhenChoices.TODAY, WhenChoices.TOMORROW, WhenChoices.THIS_WEEK),
            WhenChoices.chips(at(16, 23, 30), utc).map { it?.key },
        )
        assertEquals(
            listOf(null, WhenChoices.TODAY, WhenChoices.TONIGHT, WhenChoices.TOMORROW),
            WhenChoices.chips(at(18, 10), utc).map { it?.key },
        )
    }

    @Test
    fun labelsForTheChips() {
        assertEquals("Anytime", label(null))
        assertEquals("Today", label(WhenChoices.today(friday, utc)))
        assertEquals("Tonight", label(WhenChoices.tonight(friday, utc)))
        assertEquals("Tomorrow", label(WhenChoices.tomorrow(friday, utc)))
        assertEquals("This week", label(WhenChoices.thisWeek(friday, utc)))
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
    fun theGateLine() {
        assertNull(WhenText.gateLine(null, friday, utc, formats))
        assertEquals("For today", WhenText.gateLine(WhenChoices.today(friday, utc), friday, utc, formats))
        assertEquals(
            "For tomorrow 15:00",
            WhenText.gateLine(WhenParser.parse("Dentist tomorrow at 3pm", friday, utc).window, friday, utc, formats),
        )
        assertEquals(
            "This was for yesterday",
            WhenText.gateLine(WhenChoices.today(friday, utc), at(17, 10), utc, formats),
        )
    }
}
