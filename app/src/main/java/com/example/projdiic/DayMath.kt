package com.example.projdiic

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Calendar arithmetic shared by the time choices, the chips and the parser. */
internal object DayMath {
    private const val DAY_MS = 24 * 60 * 60_000L

    /** Midnight at the start of the day [plusDays] after the day of [time]. */
    fun midnight(time: Long, zone: TimeZone, plusDays: Int = 0): Long =
        calendar(time, zone).apply {
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** [hour]:[minute] on the day that starts at [dayMidnight]. */
    fun at(dayMidnight: Long, zone: TimeZone, hour: Int, minute: Int = 0): Long =
        calendar(dayMidnight, zone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    /** The last millisecond of the day of [time]. */
    fun endOfDay(time: Long, zone: TimeZone): Long = midnight(time, zone, plusDays = 1) - 1

    fun dayOfWeek(time: Long, zone: TimeZone): Int = calendar(time, zone).get(Calendar.DAY_OF_WEEK)

    fun hourOf(time: Long, zone: TimeZone): Int = calendar(time, zone).get(Calendar.HOUR_OF_DAY)

    /** Days from [weekday] to the next one ([Calendar.MONDAY] and so on), 0 when it is today. */
    fun daysUntil(weekday: Int, from: Long, zone: TimeZone): Int = (weekday - dayOfWeek(from, zone) + 7) % 7

    /** Calendar days from [from] to [to]: 0 for the same day, 1 for the next, -1 for the one before. */
    fun daysBetween(from: Long, to: Long, zone: TimeZone): Int =
        ((midnight(to, zone) - midnight(from, zone)) / DAY_MS.toDouble()).roundToInt()

    private fun calendar(time: Long, zone: TimeZone) = Calendar.getInstance(zone).apply { timeInMillis = time }
}
