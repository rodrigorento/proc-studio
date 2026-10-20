package com.example.projdiic

import java.util.TimeZone

/**
 * The farm game over the task list: each task is a crop that grows towards its due time. Crop and stage names are
 * the ones [CropArt] has images for.
 */
object FarmRules {
    const val RADISH = "radish"
    const val LETTUCE = "lettuce"
    const val CARROT = "carrot"
    const val CORN = "corn"
    const val PUMPKIN = "pumpkin"
    const val GRAPE = "grape"
    const val SUNFLOWER = "sunflower"

    /** The span a sunflower (Anytime, no due time) grows over, as planting to the due time is for the other crops. */
    const val SUNFLOWER_GROWS_OVER_MS = 3 * 24 * 3_600_000L

    /** Share of the way from planting to the due time where each stage starts; a crop is ripe on the day it's due. */
    private const val SPROUT_FROM = 0.25
    private const val GROWING_FROM = 0.5

    /** Share of its span where a sunflower (planted before Anytime was dropped) blooms. */
    private const val SUNFLOWER_BLOOMS_FROM = 0.6

    /** How long after planting a sunflower blooms. */
    val SUNFLOWER_BLOOMS_AFTER_MS: Long = (SUNFLOWER_GROWS_OVER_MS * SUNFLOWER_BLOOMS_FROM).toLong()

    /**
     * The crop a task grows: the smaller the crop, the sooner it's due. A seed's day grows that seed's crop (see
     * [SeedPackets.seeds]); tasks saved with the old time choices keep the crops those gave.
     */
    fun crop(task: Task, zone: TimeZone = TimeZone.getDefault()): String = crop(TaskRules.window(task), plantedAt(task), zone)

    /** The crop a time choice ([window], null for Anytime) grows when planted at [plantedAt]. */
    fun crop(window: TimeWindow?, plantedAt: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (window == null) return SUNFLOWER
        return when (window.key) {
            WhenChoices.TODAY, WhenChoices.TONIGHT -> RADISH
            WhenChoices.TOMORROW -> LETTUCE
            WhenChoices.THIS_WEEK -> CARROT
            WhenChoices.THIS_MONTH -> PUMPKIN
            // The seeds' days: today, the rest of this week, then a week each (Monday to Sunday), then later.
            else -> when {
                DayMath.daysBetween(plantedAt, window.end, zone) <= 0 -> RADISH
                else -> when (DayMath.weeksBetween(plantedAt, window.end, zone)) {
                    0 -> LETTUCE
                    1 -> CARROT
                    2 -> CORN
                    3 -> PUMPKIN
                    else -> GRAPE
                }
            }
        }
    }

    /**
     * How grown the task's crop is at [now]: seed, sprout and growing over the way from planting to its due time,
     * ripe on the day it's due, overripe once past due; a sunflower blooms. Only time grows it.
     */
    fun stage(task: Task, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val sunflower = task.dueAt <= 0L
        val planted = plantedAt(task)
        val end = if (sunflower) planted + SUNFLOWER_GROWS_OVER_MS else task.dueAt
        if (now > end) return if (sunflower) "bloom" else "overripe"
        val progress = if (end > planted) (now - planted).toDouble() / (end - planted) else 1.0
        val grown = when {
            progress < SPROUT_FROM -> 0
            progress < GROWING_FROM -> 1
            sunflower -> if (progress < SUNFLOWER_BLOOMS_FROM) 2 else 3
            DayMath.daysBetween(now, end, zone) > 0 -> 2
            // Not ripe before its time starts ("tonight", in the afternoon): it couldn't come up at a pause yet.
            task.deferredBy == Task.BY_WHEN && task.deferredUntil > now -> 2
            else -> 3
        }
        return listOf("seed", "sprout", "growing", if (sunflower) "bloom" else "ripe")[grown]
    }

    /** Ripe (or gone to seed, or in bloom): its harvest needn't ripen it first. */
    fun isReady(task: Task, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        stage(task, now, zone) in setOf("ripe", "overripe", "bloom")

    /** Ripe, or its task is due by the end of today: the pause offers Harvest it; before that, Water it. */
    fun isDue(task: Task, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        isReady(task, now, zone) || (task.dueAt > 0L && DayMath.daysBetween(now, task.dueAt, zone) <= 0)

    /**
     * The farm's day starts at this hour, not at midnight: a crop watered late in the evening stays watered through a
     * night of scrolling.
     */
    const val DAY_STARTS_AT_HOUR = 4

    /** When the farm day that [now] falls in started: 4:00 today, or yesterday before 4:00. */
    fun dayStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val today = DayMath.at(DayMath.midnight(now, zone), zone, DAY_STARTS_AT_HOUR)
        return if (now >= today) today else DayMath.at(DayMath.midnight(now, zone, plusDays = -1), zone, DAY_STARTS_AT_HOUR)
    }

    /** When the next farm day starts: 4:00 tomorrow, or tonight when [now] is before 4:00. */
    fun nextDayStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        DayMath.at(DayMath.midnight(dayStart(now, zone), zone, plusDays = 1), zone, DAY_STARTS_AT_HOUR)

    /** Watered this farm day. Each day starts dry; watering only changes the look, and crops never die. */
    fun isWatered(task: Task, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        task.wateredAt >= dayStart(now, zone)

    /** Some crop on the field is still dry today: the farm comes up over Instagram until every one is watered. */
    fun needsWater(tasks: List<Task>, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        tasks.any { !isWatered(it, now, zone) }

    /** When an Anytime task's sunflower blooms. */
    fun bloomsAt(task: Task): Long = plantedAt(task) + SUNFLOWER_BLOOMS_AFTER_MS

    /** When the crop was planted: changing the task's time replants it. Tasks saved without that grow from when they were added. */
    private fun plantedAt(task: Task): Long = task.plantedAt.takeIf { it > 0L } ?: task.createdAt
}
