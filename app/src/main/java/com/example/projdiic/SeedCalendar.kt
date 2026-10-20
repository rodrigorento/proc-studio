package com.example.projdiic

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The seed step's calendar: the months a seed's days fall in, a week to a row, with only the seed's days open. */
object SeedCalendar {
    /** A date: its [dayOfMonth], how many days after today it is ([day]), and whether the seed covers it ([open]). */
    data class Day(val dayOfMonth: Int, val day: Int, val open: Boolean)

    /** The month starting at [firstDay] (midnight on the 1st), as weeks of seven; null where a week runs outside it. */
    data class Month(val firstDay: Long, val weeks: List<List<Day?>>)

    fun months(
        seed: SeedPackets.Seed,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        firstDayOfWeek: Int = Calendar.getInstance(zone).firstDayOfWeek,
    ): List<Month> {
        val last = DayMath.midnight(now, zone, plusDays = seed.lastDay)
        val months = mutableListOf<Month>()
        var start = DayMath.startOfMonth(DayMath.midnight(now, zone, plusDays = seed.firstDay), zone)
        while (start <= last) {
            months += month(start, seed, now, zone, firstDayOfWeek)
            start = DayMath.endOfMonth(start, zone) + 1
        }
        return months
    }

    /** "October", or "January 2027" for a month in another year than [now]. */
    fun title(month: Month, now: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        val sameYear = Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.YEAR) ==
            Calendar.getInstance(zone).apply { timeInMillis = month.firstDay }.get(Calendar.YEAR)
        val name = SimpleDateFormat(if (sameYear) "LLLL" else "LLLL yyyy", locale).apply { timeZone = zone }.format(month.firstDay)
        return name.replaceFirstChar { it.titlecase(locale) }
    }

    /** One letter a weekday, from [firstDayOfWeek]: M T W T F S S. */
    fun weekdayLetters(firstDayOfWeek: Int, locale: Locale = Locale.getDefault()): List<String> {
        val names = DateFormatSymbols.getInstance(locale).shortWeekdays // [1] is Sunday
        return (0 until 7).map { names[(firstDayOfWeek - 1 + it) % 7 + 1].take(1).uppercase(locale) }
    }

    /** "Tuesday 27 October", for a screen reader: the date [day] days after today. */
    fun spoken(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEEE d MMMM", locale).apply { timeZone = zone }.format(DayMath.midnight(now, zone, plusDays = day))

    private fun month(start: Long, seed: SeedPackets.Seed, now: Long, zone: TimeZone, firstDayOfWeek: Int): Month {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = start }
        val lead = (calendar.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val days = (1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)).map { date ->
            val day = DayMath.daysBetween(now, DayMath.midnight(start, zone, plusDays = date - 1), zone)
            Day(date, day, day in seed.firstDay..seed.lastDay)
        }
        val cells: List<Day?> = List(lead) { null } + days
        return Month(start, cells.chunked(7).map { week -> week + List(7 - week.size) { null } })
    }
}
