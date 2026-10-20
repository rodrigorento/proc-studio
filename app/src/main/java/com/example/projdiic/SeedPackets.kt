package com.example.projdiic

import java.util.TimeZone

/**
 * The seeds offered when planting a task: four, back to back, each growing its own crop over its own days. Picking one
 * opens its days on a calendar ([SeedCalendar]) and a time: any time that day, unless one is set.
 */
object SeedPackets {
    /** A seed: its [name], the [crop] it grows, and the days it covers, [firstDay] to [lastDay] after today (0: today). */
    data class Seed(val name: String, val crop: String, val firstDay: Int, val lastDay: Int)

    /**
     * A [seed] with the [day] picked (days after today) and the [minute] of that day (null: any time that day). [said]
     * when it is the time said in the task, which can start later than now ([startsAt]: "tonight" waits for the evening).
     */
    data class Pick(val seed: Seed, val day: Int, val minute: Int?, val said: Boolean = false, val startsAt: Long = 0L)

    /** A task about to be planted: its [title] without the time said in it, and that time as a pick, if there was one. */
    data class Planting(val title: String, val said: Pick?)

    private const val LAST_MINUTE = 23 * 60 + 59

    fun seeds(now: Long, zone: TimeZone = TimeZone.getDefault()): List<Seed> = listOf(
        Seed("Today", FarmRules.RADISH, 0, 0),
        Seed("In 7 days", FarmRules.LETTUCE, 1, 7),
        Seed("In 15 days", FarmRules.CARROT, 8, 15),
        Seed("In 1 month", FarmRules.PUMPKIN, 16, DayMath.daysBetween(now, DayMath.oneMonthOn(now, zone), zone)),
    )

    /** The seed whose days include [day]; a day further than a month is a pumpkin, its days stretched to reach it. */
    fun seedFor(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Seed {
        val seeds = seeds(now, zone)
        seeds.firstOrNull { day in it.firstDay..it.lastDay }?.let { return it }
        return if (day < 0) seeds.first() else seeds.last().let { it.copy(lastDay = maxOf(it.lastDay, day)) }
    }

    /** [seed], just tapped: its last day, any time. */
    fun pick(seed: Seed): Pick = Pick(seed, seed.lastDay, minute = null)

    /** What planting [text] (typed or said) offers at [now], or null when it has no title, only a time. */
    fun planting(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): Planting? {
        val parsed = WhenParser.parse(text, now, zone)
        val title = TaskRules.cleanTitle(parsed.title) ?: return null
        return Planting(title, parsed.window?.let { said(it, now, zone) })
    }

    private fun said(window: TimeWindow, now: Long, zone: TimeZone): Pick {
        val day = DayMath.daysBetween(now, window.end, zone).coerceAtLeast(0)
        val minute = if (window.hasTime) DayMath.minuteOf(window.end, zone) else null
        return Pick(seedFor(day, now, zone), day, minute, said = true, startsAt = window.start.takeIf { it > now } ?: 0L)
    }

    /** The time [pick] gives a task planted at [now]: due at the end of its day, or at its time. */
    fun window(pick: Pick, now: Long, zone: TimeZone = TimeZone.getDefault()): TimeWindow {
        val day = DayMath.midnight(now, zone, plusDays = pick.day)
        val end = pick.minute?.let { DayMath.at(day, zone, it / 60, it % 60) } ?: DayMath.endOfDay(day, zone)
        return TimeWindow(WhenChoices.CUSTOM, maxOf(now, pick.startsAt), end, hasTime = pick.minute != null)
    }

    /** Whether [pick]'s time has gone already (a time earlier today), so it can't be planted. */
    fun isPast(pick: Pick, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean = window(pick, now, zone).end <= now

    /** The time offered once one is set for [day]: the next full hour today (23:59 at the latest), otherwise 9:00. */
    fun defaultMinute(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        if (day > 0) 9 * 60 else minOf((DayMath.hourOf(now, zone) + 1) * 60, LAST_MINUTE)
}
