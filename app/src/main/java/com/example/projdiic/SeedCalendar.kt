package com.example.projdiic

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The seed step's calendar, Monday to Sunday, with only the seed's days open: a seed of a week is that one week; the
 * grapes, a month a page.
 */
object SeedCalendar {
    /** A date: its [dayOfMonth], how many days after today it is ([day]), and whether the seed covers it ([open]). */
    data class Day(val dayOfMonth: Int, val day: Int, val open: Boolean)

    /** Weeks of seven; null where a week runs outside a month's page. */
    data class Page(val weeks: List<List<Day?>>)

    fun pages(seed: SeedPackets.Seed, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Page> {
        val first = DayMath.midnight(now, zone, plusDays = seed.firstDay)
        val last = DayMath.midnight(now, zone, plusDays = seed.lastDay)
        if (DayMath.weeksBetween(first, last, zone) == 0) {
            val monday = DayMath.monday(first, zone)
            return listOf(Page(listOf((0 until 7).map { day(DayMath.midnight(monday, zone, plusDays = it), seed, now, zone) })))
        }
        val pages = mutableListOf<Page>()
        var start = DayMath.startOfMonth(first, zone)
        while (start <= last) {
            pages += month(start, seed, now, zone)
            start = DayMath.endOfMonth(start, zone) + 1
        }
        return pages
    }

    /** "October", "October – November" for a week across two, and the year for a month in another than [now]'s. */
    fun title(page: Page, now: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        val days = page.weeks.flatten().filterNotNull()
        val first = DayMath.midnight(now, zone, plusDays = days.first().day)
        val last = DayMath.midnight(now, zone, plusDays = days.last().day)
        fun format(pattern: String, time: Long) = SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(time)
        fun month(time: Long) = format("LLLL", time).replaceFirstChar { it.titlecase(locale) }
        val months = if (format("MM", first) == format("MM", last)) month(first) else month(first) + " – " + month(last)
        return if (format("yyyy", last) == format("yyyy", now)) months else months + " " + format("yyyy", last)
    }

    /** One letter a weekday, from Monday: M T W T F S S. */
    fun weekdayLetters(locale: Locale = Locale.getDefault()): List<String> {
        val names = DateFormatSymbols.getInstance(locale).shortWeekdays // [1] is Sunday
        return (0 until 7).map { names[(Calendar.MONDAY - 1 + it) % 7 + 1].take(1).uppercase(locale) }
    }

    /** "Tuesday 27 October", for a screen reader: the date [day] days after today. */
    fun spoken(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEEE d MMMM", locale).apply { timeZone = zone }.format(DayMath.midnight(now, zone, plusDays = day))

    private fun day(date: Long, seed: SeedPackets.Seed, now: Long, zone: TimeZone): Day {
        val day = DayMath.daysBetween(now, date, zone)
        return Day(DayMath.dayOfMonth(date, zone), day, day in seed.firstDay..seed.lastDay)
    }

    private fun month(start: Long, seed: SeedPackets.Seed, now: Long, zone: TimeZone): Page {
        val length = Calendar.getInstance(zone).apply { timeInMillis = start }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val days = (0 until length).map { day(DayMath.midnight(start, zone, plusDays = it), seed, now, zone) }
        val cells: List<Day?> = List(DayMath.daysSinceMonday(start, zone)) { null } + days
        return Page(cells.chunked(7).map { week -> week + List(7 - week.size) { null } })
    }
}
