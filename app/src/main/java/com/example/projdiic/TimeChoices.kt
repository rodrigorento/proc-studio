package com.example.projdiic

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

data class TimeChoice(val key: String, val at: Long)

/** The quick choices on the Pick a time screen. */
object TimeChoices {
    const val IN_15 = "in_15_min"
    const val IN_60 = "in_1_hour"
    const val TONIGHT = "tonight"
    const val TOMORROW = "tomorrow_morning"

    private const val MINUTE = 60_000L
    private const val TONIGHT_HOUR = 20
    private const val MORNING_HOUR = 9

    fun forNow(now: Long, zone: TimeZone = TimeZone.getDefault()): List<TimeChoice> {
        val tonight = atHour(now, zone, daysAhead = 0, hour = TONIGHT_HOUR)
        return buildList {
            add(TimeChoice(IN_15, now + 15 * MINUTE))
            add(TimeChoice(IN_60, now + 60 * MINUTE))
            if (tonight - now >= 60 * MINUTE) add(TimeChoice(TONIGHT, tonight))
            add(TimeChoice(TOMORROW, atHour(now, zone, daysAhead = 1, hour = MORNING_HOUR)))
        }
    }

    /** Calendar days from [from] to [to]: 0 for the same day, 1 for yesterday. */
    fun daysBetween(from: Long, to: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val start = atHour(from, zone, daysAhead = 0, hour = 0)
        val end = atHour(to, zone, daysAhead = 0, hour = 0)
        return ((end - start) / (24 * 60 * MINUTE).toDouble()).roundToInt()
    }

    private fun atHour(now: Long, zone: TimeZone, daysAhead: Int, hour: Int): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
