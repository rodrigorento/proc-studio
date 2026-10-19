package com.example.projdiic

import java.util.TimeZone

/**
 * The farm game over the task list: each task is a crop that grows towards its due time, and the field is
 * watered once a day. Crop and stage names are the ones [CropArt] has images for.
 */
object FarmRules {
    const val RADISH = "radish"
    const val LETTUCE = "lettuce"
    const val CARROT = "carrot"
    const val PUMPKIN = "pumpkin"
    const val SUNFLOWER = "sunflower"

    /** The span a sunflower (Anytime, no due time) grows over, as planting to the due time is for the other crops. */
    const val SUNFLOWER_GROWS_OVER_MS = 3 * 24 * 3_600_000L

    /** Share of the way from planting to the due time where each stage starts. */
    private const val SPROUT_FROM = 0.1
    private const val GROWING_FROM = 0.3
    private const val RIPE_FROM = 0.6

    /** How long after planting a sunflower blooms: when it is as grown as a ripe crop. */
    val SUNFLOWER_BLOOMS_AFTER_MS: Long = (SUNFLOWER_GROWS_OVER_MS * RIPE_FROM).toLong()

    /** From this hour, a field nobody watered today gets rain on the home screen. */
    const val EVENING_RAIN_HOUR = 18

    /**
     * The crop a task grows: the smaller the crop, the sooner it's due. A chip picks its own crop; a spoken time
     * picks by how many days away it was when planted.
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
            else -> when (DayMath.daysBetween(plantedAt, window.end, zone)) {
                in Int.MIN_VALUE..0 -> RADISH
                1 -> LETTUCE
                in 2..6 -> CARROT
                else -> PUMPKIN
            }
        }
    }

    /**
     * How grown the task's crop is at [now]: seed, sprout, growing, ripe, overripe once past due; a sunflower blooms.
     * Only time grows it: watering just keeps the field from drying out.
     */
    fun stage(task: Task, now: Long): String {
        val sunflower = task.dueAt <= 0L
        val planted = plantedAt(task)
        val end = if (sunflower) planted + SUNFLOWER_GROWS_OVER_MS else task.dueAt
        if (now > end) return if (sunflower) "bloom" else "overripe"
        val progress = if (end > planted) (now - planted).toDouble() / (end - planted) else 1.0
        val grown = when {
            progress < SPROUT_FROM -> 0
            progress < GROWING_FROM -> 1
            progress < RIPE_FROM -> 2
            else -> 3
        }
        return listOf("seed", "sprout", "growing", if (sunflower) "bloom" else "ripe")[grown]
    }

    /** Ripe (or gone to seed, or in bloom): it can be harvested. */
    fun isReady(task: Task, now: Long): Boolean = stage(task, now) in setOf("ripe", "overripe", "bloom")

    /** Ripe, or its task is due by the end of today: the pause offers Harvest it first, then Water it. */
    fun isDue(task: Task, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        isReady(task, now) || (task.dueAt > 0L && DayMath.daysBetween(now, task.dueAt, zone) <= 0)

    /** When an Anytime task's sunflower blooms. */
    fun bloomsAt(task: Task): Long = plantedAt(task) + SUNFLOWER_BLOOMS_AFTER_MS

    /** When the crop was planted: changing the task's time replants it. Tasks saved without that grow from when they were added. */
    private fun plantedAt(task: Task): Long = task.plantedAt.takeIf { it > 0L } ?: task.createdAt

    /** What to store when the field is watered at [now]: the start of that day. */
    fun wateredOn(now: Long, zone: TimeZone = TimeZone.getDefault()): Long = DayMath.midnight(now, zone)

    /** Each day starts thirsty: the field is watered only if it was watered today. */
    fun isWatered(wateredOn: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        wateredOn == DayMath.midnight(now, zone)

    /** Nobody watered the field today and the evening has come: it rains, so it never stays dry. */
    fun rainsOnHome(wateredOn: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        !isWatered(wateredOn, now, zone) && DayMath.hourOf(now, zone) >= EVENING_RAIN_HOUR
}
