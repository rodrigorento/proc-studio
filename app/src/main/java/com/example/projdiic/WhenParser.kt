package com.example.projdiic

import java.util.Calendar
import java.util.TimeZone

data class ParsedTask(val title: String, val window: TimeWindow?)

/**
 * Finds when a task is for in what he said or typed ("tomorrow at 3pm",
 * "by Friday", "next week", "on the 25th"), on the phone, without any
 * network call. The words that set the time are taken out of the title.
 * English only; more languages mean more word lists here.
 */
object WhenParser {

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )
    private val WEEKDAYS = mapOf(
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY,
    )
    private val MONTHS = mapOf(
        "january" to 0, "jan" to 0, "february" to 1, "feb" to 1, "march" to 2, "mar" to 2, "april" to 3,
        "apr" to 3, "may" to 4, "june" to 5, "jun" to 5, "july" to 6, "jul" to 6, "august" to 7, "aug" to 7,
        "september" to 8, "sept" to 8, "sep" to 8, "october" to 9, "oct" to 9, "november" to 10, "nov" to 10,
        "december" to 11, "dec" to 11,
    )

    private val number = "\\d{1,3}|" + NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|")
    private val month = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private val weekday = WEEKDAYS.keys.joinToString("|")
    private const val LEAD = "(?:(by|before|until|due|on|for)\\s+)?"

    // "on" isn't said before today or tomorrow, and "Turn the lights on tomorrow" must keep its "on".
    private const val DAY_WORD_LEAD = "(?:(by|before|until|due|for)\\s+)?"
    private const val PART = "(?:\\s+(morning|afternoon|evening|night))?"
    private const val ORDINAL = "(?:st|nd|rd|th)"
    private val DEADLINE_WORDS = setOf("by", "before", "until", "due")

    private fun pattern(regex: String) = Regex(regex, RegexOption.IGNORE_CASE)

    private val relative = pattern("\\bin\\s+(?:(half)\\s+an?\\s+hour|($number)\\s+(minutes?|mins?|hours?|hrs?|days?|weeks?))\\b")
    private val monthDay = pattern("\\b$LEAD(?:the\\s+)?($month)\\.?\\s+(?:the\\s+)?(\\d{1,2})$ORDINAL?\\b")
    private val dayMonth = pattern("\\b$LEAD(?:the\\s+)?(\\d{1,2})$ORDINAL?\\s+(?:of\\s+)?($month)\\b")
    private val dayOfMonth = pattern("\\b${LEAD}the\\s+(\\d{1,2})$ORDINAL\\b")
    private val weekdayName = pattern("\\b$LEAD(?:(this|next|coming)\\s+)?($weekday)$PART\\b")
    private val dayWord = pattern(
        "\\b$DAY_WORD_LEAD(today|tonight|tomorrow|this\\s+(?:morning|afternoon|evening|weekend|week|month)|next\\s+week|(?:on\\s+)?(?:the\\s+)?weekend)$PART\\b"
    )
    private val clockAmPm = pattern("\\b(?:(?:at|by)\\s+)?(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*([ap])\\.?\\s?m\\b\\.?")
    private val clock24 = pattern("\\b(?:(?:at|by)\\s+)?([01]?\\d|2[0-3])[:h]([0-5]\\d)\\b")
    private val clockWord = pattern("\\b(?:(?:at|by)\\s+)?(noon|midday|midnight)\\b")
    private val clockAt = pattern("\\b(?:at|by)\\s+(\\d{1,2})(?:\\s*o'?clock)?\\b")

    private val danglingAnd = pattern("^and\\b\\s*|\\s*\\band$")

    /** A day (or span of days) found in the text. */
    private class DayMatch(
        val range: IntRange,
        val midnight: Long,
        val key: String,
        val deadline: Boolean,
        val part: String?,
        /** For spans (a week, a weekend): the whole window, already worked out. */
        val span: TimeWindow? = null,
    )

    private class TimeMatch(val range: IntRange, val hour: Int, val minute: Int, val meridiem: Char?)

    fun parse(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): ParsedTask {
        relative.find(text)?.let { match ->
            val window = relativeWindow(match, now, zone) ?: return ParsedTask(text, null)
            return ParsedTask(titleWithout(text, listOf(match.range)), window)
        }
        val day = findDay(text, now, zone)
        val time = findTime(text, day?.range)
        if (day == null && time == null) return ParsedTask(text, null)
        val window = buildWindow(day, time, now, zone) ?: return ParsedTask(text, null)
        return ParsedTask(titleWithout(text, listOfNotNull(day?.range, time?.range)), window)
    }

    private fun relativeWindow(match: MatchResult, now: Long, zone: TimeZone): TimeWindow? {
        val (half, amount, unit) = match.destructured
        if (half.isNotEmpty()) return laterToday(now + 30 * 60_000L, zone)
        val n = amount.toIntOrNull() ?: NUMBER_WORDS[amount.lowercase()] ?: return null
        val u = unit.lowercase()
        return when {
            u.startsWith("min") -> laterToday(now + n * 60_000L, zone)
            u.startsWith("h") -> laterToday(now + n * 3_600_000L, zone)
            else -> {
                val days = if (u.startsWith("week")) n * 7 else n
                wholeDay(DayMath.midnight(now, zone, days), now, zone, WhenChoices.CUSTOM)
            }
        }
    }

    private fun laterToday(start: Long, zone: TimeZone) =
        TimeWindow(WhenChoices.CUSTOM, start, DayMath.endOfDay(start, zone))

    private fun findDay(text: String, now: Long, zone: TimeZone): DayMatch? {
        val today = DayMath.midnight(now, zone)

        monthDay.find(text)?.let { m ->
            val (lead, monthName, day) = m.destructured
            return dateMatch(m.range, lead, MONTHS.getValue(monthName.lowercase()), day.toInt(), now, zone)
        }
        dayMonth.find(text)?.let { m ->
            val (lead, day, monthName) = m.destructured
            return dateMatch(m.range, lead, MONTHS.getValue(monthName.lowercase()), day.toInt(), now, zone)
        }
        dayOfMonth.find(text)?.let { m ->
            val (lead, day) = m.destructured
            val thisMonth = Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.MONTH)
            val dayOfMonth = Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
            val month = if (day.toInt() >= dayOfMonth) thisMonth else thisMonth + 1
            return dateMatch(m.range, lead, month, day.toInt(), now, zone)
        }
        weekdayName.find(text)?.let { m ->
            val (lead, _, name, part) = m.destructured
            val days = DayMath.daysUntil(WEEKDAYS.getValue(name.lowercase()), now, zone)
            return DayMatch(m.range, DayMath.midnight(now, zone, days), WhenChoices.CUSTOM, isDeadline(lead), part.ifEmpty { null })
        }
        dayWord.find(text)?.let { m ->
            val (lead, word, part) = m.destructured
            val words = word.lowercase().replace(Regex("\\s+"), " ")
            val deadline = isDeadline(lead)
            val partOrNull = part.ifEmpty { null }
            return when (words) {
                "today" -> DayMatch(m.range, today, WhenChoices.TODAY, deadline, partOrNull)
                "tonight", "this evening" -> DayMatch(m.range, today, WhenChoices.TONIGHT, deadline, "evening")
                "this morning" -> DayMatch(m.range, today, WhenChoices.CUSTOM, deadline, "morning")
                "this afternoon" -> DayMatch(m.range, today, WhenChoices.CUSTOM, deadline, "afternoon")
                "tomorrow" -> DayMatch(
                    m.range, DayMath.midnight(now, zone, 1),
                    if (deadline || partOrNull != null) WhenChoices.CUSTOM else WhenChoices.TOMORROW,
                    deadline, partOrNull,
                )
                "this week" -> WhenChoices.thisWeek(now, zone).let { DayMatch(m.range, DayMath.midnight(it.end, zone), it.key, deadline, null, it) }
                "this month" -> WhenChoices.thisMonth(now, zone).let { DayMatch(m.range, DayMath.midnight(it.end, zone), it.key, deadline, null, it) }
                "next week" -> {
                    val monday = DayMath.midnight(now, zone, DayMath.daysUntil(Calendar.MONDAY, now, zone).let { if (it == 0) 7 else it })
                    val start = if (deadline) now else DayMath.at(monday, zone, WhenChoices.MORNING_HOUR)
                    val sunday = DayMath.midnight(monday, zone, 6)
                    DayMatch(m.range, sunday, WhenChoices.CUSTOM, deadline, null, TimeWindow(WhenChoices.CUSTOM, start, DayMath.endOfDay(sunday, zone)))
                }
                else -> { // the weekend
                    val toSaturday = DayMath.daysUntil(Calendar.SATURDAY, now, zone)
                    val weekendNow = DayMath.dayOfWeek(now, zone) == Calendar.SUNDAY || toSaturday == 0
                    val saturday = DayMath.midnight(now, zone, if (DayMath.dayOfWeek(now, zone) == Calendar.SUNDAY) -1 else toSaturday)
                    val start = if (weekendNow || deadline) now else DayMath.at(saturday, zone, WhenChoices.MORNING_HOUR)
                    val sunday = DayMath.midnight(saturday, zone, 1)
                    DayMatch(m.range, sunday, WhenChoices.CUSTOM, deadline, null, TimeWindow(WhenChoices.CUSTOM, start, DayMath.endOfDay(sunday, zone)))
                }
            }
        }
        return null
    }

    private fun dateMatch(range: IntRange, lead: String, month: Int, day: Int, now: Long, zone: TimeZone): DayMatch? {
        if (day !in 1..31) return null
        val nowCalendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        val date = Calendar.getInstance(zone).apply {
            clear()
            set(nowCalendar.get(Calendar.YEAR), month, day)
        }
        // A date that has passed this year means next year.
        if (date.timeInMillis < DayMath.midnight(now, zone)) date.add(Calendar.YEAR, 1)
        return DayMatch(range, date.timeInMillis, WhenChoices.CUSTOM, isDeadline(lead), null)
    }

    private fun findTime(text: String, dayRange: IntRange?): TimeMatch? {
        fun MatchResult.outsideDay() = dayRange == null || range.last < dayRange.first || range.first > dayRange.last

        clockAmPm.findAll(text).firstOrNull { it.outsideDay() }?.let { m ->
            val (hour, minute, meridiem) = m.destructured
            return TimeMatch(m.range, hour.toInt(), minute.toIntOrNull() ?: 0, meridiem.lowercase().first())
        }
        clock24.findAll(text).firstOrNull { it.outsideDay() }?.let { m ->
            val (hour, minute) = m.destructured
            val h = hour.toInt()
            return TimeMatch(m.range, h, minute.toInt(), if (h > 12 || hour.startsWith("0")) 'h' else null)
        }
        clockWord.findAll(text).firstOrNull { it.outsideDay() }?.let { m ->
            val midnight = m.groupValues[1].equals("midnight", ignoreCase = true)
            return TimeMatch(m.range, if (midnight) 24 else 12, 0, 'h')
        }
        clockAt.findAll(text).firstOrNull { it.outsideDay() }?.let { m ->
            return TimeMatch(m.range, m.groupValues[1].toInt(), 0, null)
        }
        return null
    }

    private fun buildWindow(day: DayMatch?, time: TimeMatch?, now: Long, zone: TimeZone): TimeWindow? {
        if (day?.span != null) return day.span
        val today = DayMath.midnight(now, zone)
        if (time == null) {
            day ?: return null
            return if (day.part != null) partWindow(day, now, zone) else wholeDay(day.midnight, now, zone, day.key, day.deadline)
        }
        var date = day?.midnight ?: today
        var hour = hour24(time, day?.part)
        if (time.hour !in 0..24 || time.minute !in 0..59 || hour !in 0..24) return null
        // No day said and the time has gone: tomorrow. A bare hour may mean the evening first.
        if (day == null && clockTime(date, hour, time.minute, zone) <= now) {
            if (time.meridiem == null && hour < 12 && clockTime(date, hour + 12, time.minute, zone) > now) {
                hour += 12
            } else {
                date = DayMath.midnight(now, zone, 1)
            }
        }
        val end = clockTime(date, hour, time.minute, zone)
        val start = when {
            date == today || day?.deadline == true -> now
            hour < WhenChoices.MORNING_HOUR -> date
            else -> DayMath.at(date, zone, WhenChoices.MORNING_HOUR)
        }
        return TimeWindow(WhenChoices.CUSTOM, minOf(start, end), end, hasTime = true)
    }

    private fun hour24(time: TimeMatch, part: String?): Int = when (time.meridiem) {
        'h' -> time.hour
        'a' -> if (time.hour == 12) 0 else time.hour
        'p' -> if (time.hour == 12) 12 else time.hour + 12
        else -> when {
            time.hour > 12 -> time.hour
            part == "morning" -> time.hour
            part != null -> if (time.hour == 12) 12 else time.hour + 12
            time.hour in 1..7 -> time.hour + 12 // nobody plans a task for 3 in the morning
            else -> time.hour
        }
    }

    private fun clockTime(dayMidnight: Long, hour: Int, minute: Int, zone: TimeZone): Long =
        if (hour >= 24) DayMath.endOfDay(dayMidnight, zone) else DayMath.at(dayMidnight, zone, hour, minute)

    private fun wholeDay(dayMidnight: Long, now: Long, zone: TimeZone, key: String, deadline: Boolean = false): TimeWindow {
        val start = if (deadline || dayMidnight <= now) now else DayMath.at(dayMidnight, zone, WhenChoices.MORNING_HOUR)
        return TimeWindow(key, start, DayMath.endOfDay(dayMidnight, zone))
    }

    private fun partWindow(day: DayMatch, now: Long, zone: TimeZone): TimeWindow {
        val (from, until) = when (day.part) {
            "morning" -> WhenChoices.MORNING_HOUR to 12
            "afternoon" -> 12 to 18
            else -> WhenChoices.EVENING_HOUR to 24
        }
        val start = if (day.deadline) now else maxOf(now, DayMath.at(day.midnight, zone, from))
        val key = if (day.key == WhenChoices.TONIGHT || (day.key == WhenChoices.TODAY && from == WhenChoices.EVENING_HOUR)) {
            WhenChoices.TONIGHT
        } else {
            WhenChoices.CUSTOM
        }
        return TimeWindow(key, start, clockTime(day.midnight, until, 0, zone))
    }

    private fun isDeadline(lead: String) = lead.lowercase() in DEADLINE_WORDS

    /** The text without the words that set the time, or the text itself if nothing else is left. */
    private fun titleWithout(text: String, ranges: List<IntRange>): String {
        var title = text
        ranges.sortedByDescending { it.first }.forEach { title = title.removeRange(it) }
        repeat(3) {
            title = title.replace(Regex("\\s+"), " ").trim().trim(',', ';', ':', '.', '!', '?', '-', ' ')
            title = danglingAnd.replace(title, "")
        }
        return title.trim().ifEmpty { text }
    }
}
