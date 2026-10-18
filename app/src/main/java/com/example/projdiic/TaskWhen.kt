package com.example.projdiic

import java.util.Calendar
import java.util.TimeZone

/**
 * When a task is for: it can show at a gate from [start] and is due by [end].
 * [hasTime] when [end] is a clock time he gave ("at 3pm") rather than the end of a day.
 */
data class TimeWindow(
    val key: String,
    val start: Long,
    val end: Long,
    val hasTime: Boolean = false,
)

/** The fixed choices offered when adding or revising a task. Anytime is no window at all. */
object WhenChoices {
    const val TODAY = "today"
    const val TONIGHT = "tonight"
    const val TOMORROW = "tomorrow"
    const val THIS_WEEK = "this_week"

    /** Anything said that isn't one of the chips: "on Monday", "at 3pm", "on the 25th". */
    const val CUSTOM = "custom"

    const val MORNING_HOUR = 8
    const val EVENING_HOUR = 19
    private const val LAST_EVENING_HOUR = 23

    fun today(now: Long, zone: TimeZone) = TimeWindow(TODAY, now, DayMath.endOfDay(now, zone))

    fun tonight(now: Long, zone: TimeZone): TimeWindow {
        val evening = DayMath.at(DayMath.midnight(now, zone), zone, EVENING_HOUR)
        return TimeWindow(TONIGHT, maxOf(now, evening), DayMath.endOfDay(now, zone))
    }

    fun tomorrow(now: Long, zone: TimeZone): TimeWindow {
        val day = DayMath.midnight(now, zone, plusDays = 1)
        return TimeWindow(TOMORROW, DayMath.at(day, zone, MORNING_HOUR), DayMath.endOfDay(day, zone))
    }

    /** Until Sunday night; the week starts on Monday. */
    fun thisWeek(now: Long, zone: TimeZone): TimeWindow {
        val sunday = DayMath.midnight(now, zone, DayMath.daysUntil(Calendar.SUNDAY, now, zone))
        return TimeWindow(THIS_WEEK, now, DayMath.endOfDay(sunday, zone))
    }

    /** Midnight tonight: when "Snooze until tomorrow" ends. */
    fun startOfTomorrow(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        DayMath.midnight(now, zone, plusDays = 1)

    /** Anytime (null) first; Tonight drops off late at night and This week on Sundays. */
    fun chips(now: Long, zone: TimeZone = TimeZone.getDefault()): List<TimeWindow?> = buildList {
        add(null)
        add(today(now, zone))
        if (DayMath.hourOf(now, zone) < LAST_EVENING_HOUR) add(tonight(now, zone))
        add(tomorrow(now, zone))
        if (DayMath.dayOfWeek(now, zone) != Calendar.SUNDAY) add(thisWeek(now, zone))
    }
}

/** Short words for a window, for the task list and the gate. */
object WhenText {
    class Formats(
        val time: (Long) -> String,
        val weekday: (Long) -> String,
        val date: (Long) -> String,
    )

    private val relativeWords = setOf("Today", "Tonight", "Tomorrow", "Yesterday", "This week")

    /** "Anytime", "Today", "Tomorrow 15:00", "Monday", "From 16:50", "Was for yesterday". */
    fun label(window: TimeWindow?, now: Long, zone: TimeZone, formats: Formats): String {
        if (window == null) return "Anytime"
        val time = if (window.hasTime) " " + formats.time(window.end) else ""
        if (window.end < now) {
            val missedToday = DayMath.daysBetween(now, window.end, zone) == 0
            return if (missedToday && window.hasTime) {
                "Was for " + formats.time(window.end)
            } else {
                "Was for " + lower(day(window.end, now, zone, formats)) + time
            }
        }
        val waiting = window.start > now && !window.hasTime
        if (waiting && window.key != WhenChoices.TONIGHT && DayMath.daysBetween(now, window.start, zone) == 0) {
            return "From " + formats.time(window.start)
        }
        return when (window.key) {
            WhenChoices.TODAY -> "Today"
            WhenChoices.TONIGHT -> "Tonight"
            WhenChoices.THIS_WEEK -> "This week"
            else -> day(if (waiting) window.start else window.end, now, zone, formats) + time
        }
    }

    /** The line under the task on the gate, or null for a task without a time. */
    fun gateLine(window: TimeWindow?, now: Long, zone: TimeZone, formats: Formats): String? {
        if (window == null) return null
        val label = label(window, now, zone, formats)
        return when {
            label.startsWith("Was for ") -> "This was for " + label.removePrefix("Was for ")
            label.startsWith("From ") -> label
            else -> "For " + lower(label)
        }
    }

    private fun day(time: Long, now: Long, zone: TimeZone, formats: Formats): String =
        when (DayMath.daysBetween(now, time, zone)) {
            0 -> "Today"
            1 -> "Tomorrow"
            -1 -> "Yesterday"
            in 2..6 -> formats.weekday(time)
            else -> formats.date(time)
        }

    private fun lower(label: String): String {
        val word = relativeWords.firstOrNull { label.startsWith(it) } ?: return label
        return word.lowercase() + label.removePrefix(word)
    }
}
