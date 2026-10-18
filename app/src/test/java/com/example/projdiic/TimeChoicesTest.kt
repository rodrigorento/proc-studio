package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeChoicesTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }.timeInMillis

    @Test
    fun offersFourChoicesInTheAfternoon() {
        val now = at(16, 14, 50)

        val choices = TimeChoices.forNow(now, utc)

        assertEquals(
            listOf(TimeChoices.IN_15, TimeChoices.IN_60, TimeChoices.TONIGHT, TimeChoices.TOMORROW),
            choices.map { it.key },
        )
        assertEquals(at(16, 15, 5), choices[0].at)
        assertEquals(at(16, 15, 50), choices[1].at)
        assertEquals(at(16, 20, 0), choices[2].at)
        assertEquals(at(17, 9, 0), choices[3].at)
    }

    @Test
    fun dropsTonightWhenItIsLessThanAnHourAway() {
        val choices = TimeChoices.forNow(at(16, 19, 30), utc)

        assertEquals(listOf(TimeChoices.IN_15, TimeChoices.IN_60, TimeChoices.TOMORROW), choices.map { it.key })
    }

    @Test
    fun countsCalendarDaysNotHours() {
        assertEquals(0, TimeChoices.daysBetween(at(16, 1, 0), at(16, 23, 0), utc))
        assertEquals(1, TimeChoices.daysBetween(at(15, 23, 0), at(16, 1, 0), utc))
        assertEquals(3, TimeChoices.daysBetween(at(13, 12, 0), at(16, 9, 0), utc))
    }
}
