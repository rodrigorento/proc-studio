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

    @Test
    fun countsCalendarDaysNotHours() {
        assertEquals(0, DayMath.daysBetween(at(utc, 2026, Calendar.OCTOBER, 16, 1), at(utc, 2026, Calendar.OCTOBER, 16, 23), utc))
        assertEquals(1, DayMath.daysBetween(at(utc, 2026, Calendar.OCTOBER, 15, 23), at(utc, 2026, Calendar.OCTOBER, 16, 1), utc))
        assertEquals(3, DayMath.daysBetween(at(utc, 2026, Calendar.OCTOBER, 13, 12), at(utc, 2026, Calendar.OCTOBER, 16, 9), utc))
    }

    @Test
    fun countsWeeksFromMondayToSunday() {
        fun weeks(fromDay: Int, toDay: Int, toMonth: Int = Calendar.OCTOBER) =
            DayMath.weeksBetween(at(london, 2026, Calendar.OCTOBER, fromDay, 22), at(london, 2026, toMonth, toDay, 1), london)

        assertEquals(0, weeks(16, 18)) // Friday to Sunday
        assertEquals(1, weeks(16, 19)) // Friday to Monday
        assertEquals(1, weeks(18, 19)) // Sunday to Monday
        assertEquals(0, weeks(19, 25)) // Monday to Sunday
        assertEquals(2, weeks(23, 2, Calendar.NOVEMBER)) // over the clocks going back on 25 October
        assertEquals(-1, weeks(19, 18))
    }
}
