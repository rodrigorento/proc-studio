package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayMathTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val london = TimeZone.getTimeZone("Europe/London")

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis

    @Test
    fun theMonthEndsOnItsLastDayWhateverItsLength() {
        assertEquals(
            at(utc, 2028, Calendar.MARCH, 1, 0) - 1,
            DayMath.endOfMonth(at(utc, 2028, Calendar.FEBRUARY, 10), utc),
        )
        assertEquals(
            at(utc, 2027, Calendar.MARCH, 1, 0) - 1,
            DayMath.endOfMonth(at(utc, 2027, Calendar.FEBRUARY, 10), utc),
        )
        assertEquals(
            at(utc, 2027, Calendar.JANUARY, 1, 0) - 1,
            DayMath.endOfMonth(at(utc, 2026, Calendar.DECEMBER, 31, 23), utc),
        )
    }

    @Test
    fun theMonthEndsAtLocalMidnightInAMonthWithAClockChange() {
        // The clocks go forward on 29 March 2026 and back on 25 October 2026.
        assertEquals(
            at(london, 2026, Calendar.APRIL, 1, 0) - 1,
            DayMath.endOfMonth(at(london, 2026, Calendar.MARCH, 3), london),
        )
        assertEquals(
            at(london, 2026, Calendar.NOVEMBER, 1, 0) - 1,
            DayMath.endOfMonth(at(london, 2026, Calendar.OCTOBER, 3), london),
        )
    }
}
