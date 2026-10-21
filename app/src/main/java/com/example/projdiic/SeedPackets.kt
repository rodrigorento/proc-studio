package com.example.projdiic

import java.util.TimeZone

/**
 * The seeds offered when planting a task, back to back, each growing its own crop: the radish for today, the lettuce
 * for the rest of this week, then a week each (Monday to Sunday) for the carrot, the corn and the pumpkin, and the
 * grapes for anything later. Picking one opens its days on a calendar ([SeedCalendar]) and a time: any time that day,
 * unless one is set.
 */
object SeedPackets {
    /** A seed: the [crop] it grows and the days it covers, [firstDay] to [lastDay] after today (0: today). */
    data class Seed(val crop: String, val firstDay: Int, val lastDay: Int)

    /**
     * A [seed] with the [day] picked (days after today) and the [minute] of that day (null: any time that day). [said]
     * when it is the time said in the task, which can start later today ([startsAt]: "tonight" waits for the evening).
     */
    data class Pick(val seed: Seed, val day: Int, val minute: Int?, val said: Boolean = false, val startsAt: Long = 0L)

    /** A task about to be planted: its [title] without the time said in it, and that time as a pick, if there was one. */
    data class Planting(val title: String, val said: Pick?)

    private const val LAST_MINUTE = 23 * 60 + 59

    /** The crops of the weeks after this one: next week's first. Any later week grows grapes. */
    private val WEEK_CROPS = listOf(FarmRules.CARROT, FarmRules.CORN, FarmRules.PUMPKIN)

    /** The seeds at [now]. On a Sunday there's nothing left of the week after today, so no lettuce. */
    fun seeds(now: Long, zone: TimeZone = TimeZone.getDefault()): List<Seed> {
        val sunday = 6 - DayMath.daysSinceMonday(now, zone)
        val weeks = WEEK_CROPS.mapIndexed { i, crop -> Seed(crop, sunday + 1 + 7 * i, sunday + 7 * (i + 1)) }
        val grapesFrom = sunday + 1 + 7 * WEEK_CROPS.size
        return listOfNotNull(
            Seed(FarmRules.RADISH, 0, 0),
            Seed(FarmRules.LETTUCE, 1, sunday).takeIf { sunday > 0 },
        ) + weeks + Seed(FarmRules.GRAPE, grapesFrom, maxOf(grapesFrom, DayMath.daysBetween(now, DayMath.oneYearOn(now, zone), zone)))
    }

    /** The seed whose days include [day]: a day gone is today's radish; one further than the grapes reach stretches them. */
    fun seedFor(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Seed {
        val seeds = seeds(now, zone)
        seeds.firstOrNull { day in it.firstDay..it.lastDay }?.let { return it }
        return if (day < 0) seeds.first() else seeds.last().let { it.copy(lastDay = maxOf(it.lastDay, day)) }
    }

    /** [seed], just tapped: the last day of its first week, any time. */
    fun pick(seed: Seed): Pick = Pick(seed, minOf(seed.lastDay, seed.firstDay + 6), minute = null)

    /**
     * The days [seed] covers, as its packet says them, in the phone's [formats]: "Today", "Tomorrow – 18 Oct",
     * "26 Oct – 1 Nov", "2 – 8 Nov", "From 9 Nov".
     */
    fun label(seed: Seed, now: Long, formats: WhenText.Formats, zone: TimeZone = TimeZone.getDefault()): String {
        val first = DayMath.midnight(now, zone, plusDays = seed.firstDay)
        val last = DayMath.midnight(now, zone, plusDays = seed.lastDay)
        return when {
            seed.firstDay == 0 -> "Today"
            seed.crop == FarmRules.GRAPE -> "From " + formats.date(first)
            seed.firstDay == 1 -> if (seed.lastDay == 1) "Tomorrow" else "Tomorrow – " + formats.date(last)
            else -> formats.range(first, last)
        }
    }

    /** What planting [text] (typed or said) offers at [now], or null when it has no title, only a time. */
    fun planting(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): Planting? {
        val parsed = WhenParser.parse(text, now, zone)
        val title = TaskRules.cleanTitle(parsed.title) ?: return null
        return Planting(title, parsed.window?.let { said(it, now, zone) })
    }

    private fun said(window: TimeWindow, now: Long, zone: TimeZone): Pick {
        val day = DayMath.daysBetween(now, window.end, zone).coerceAtLeast(0)
        // A time, or a part of the day: "tomorrow morning" is by 12:00.
        val timed = window.hasTime || window.end != DayMath.endOfDay(window.end, zone)
        // Only a start later today holds the task back ("tonight"); a later day grows from planting, as a day picked does.
        val startsAt = window.start.takeIf { it > now && DayMath.daysBetween(now, it, zone) == 0 } ?: 0L
        return Pick(seedFor(day, now, zone), day, if (timed) DayMath.minuteOf(window.end, zone) else null, said = true, startsAt = startsAt)
    }

    /**
     * The time [pick] gives a task planted at [now]: due at the end of its day, or at its time. Its day counts from when
     * it was shown ([shownAt]), so a step left open past midnight still plants the date it showed.
     */
    fun window(pick: Pick, now: Long, zone: TimeZone = TimeZone.getDefault(), shownAt: Long = now): TimeWindow {
        val day = DayMath.midnight(shownAt, zone, plusDays = pick.day)
        val end = pick.minute?.let { DayMath.at(day, zone, it / 60, it % 60) } ?: DayMath.endOfDay(day, zone)
        return TimeWindow(WhenChoices.CUSTOM, maxOf(now, pick.startsAt), end, hasTime = pick.minute != null)
    }

    /** Whether [pick]'s time has gone already (a time earlier today), so it can't be planted. */
    fun isPast(pick: Pick, now: Long, zone: TimeZone = TimeZone.getDefault(), shownAt: Long = now): Boolean =
        window(pick, now, zone, shownAt).end <= now

    /** [pick], shown at [from], on a screen moved on to the day of [to]: the same date, on the seed that covers it then. */
    fun rebase(pick: Pick, from: Long, to: Long, zone: TimeZone = TimeZone.getDefault()): Pick {
        val day = pick.day - DayMath.daysBetween(from, to, zone)
        return pick.copy(seed = seedFor(day, to, zone), day = day)
    }

    /** The time offered once one is set for [day]: the next full hour today (23:59 at the latest), otherwise 9:00. */
    fun defaultMinute(day: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        if (day > 0) 9 * 60 else minOf((DayMath.hourOf(now, zone) + 1) * 60, LAST_MINUTE)
}
