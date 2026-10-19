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

    /** How long a sunflower (Anytime, no due time) takes to bloom. */
    const val SUNFLOWER_BLOOMS_AFTER_MS = 3 * 24 * 3_600_000L

    /** Share of the way from planting to the due time where each stage starts. */
    private const val SPROUT_FROM = 0.1
    private const val GROWING_FROM = 0.3
    private const val RIPE_FROM = 0.6

    /** From this hour, a field nobody watered today gets rain on the home screen. */
    const val EVENING_RAIN_HOUR = 18

    /**
     * The crop a task grows: the smaller the crop, the sooner it's due. A chip picks its own crop; a spoken time
     * picks by how many days away it was when planted.
     */
    fun crop(task: Task, zone: TimeZone = TimeZone.getDefault()): String {
        if (task.dueAt <= 0L) return SUNFLOWER
        return when (task.whenKey) {
            WhenChoices.TODAY, WhenChoices.TONIGHT -> RADISH
            WhenChoices.TOMORROW -> LETTUCE
            WhenChoices.THIS_WEEK -> CARROT
            WhenChoices.THIS_MONTH -> PUMPKIN
            else -> when (DayMath.daysBetween(task.createdAt, task.dueAt, zone)) {
                in Int.MIN_VALUE..0 -> RADISH
                1 -> LETTUCE
                in 2..6 -> CARROT
                else -> PUMPKIN
            }
        }
    }

    /** How grown the task's crop is at [now]: seed, sprout, growing, ripe, overripe once past due; a sunflower blooms. */
    fun stage(task: Task, now: Long): String {
        val sunflower = task.dueAt <= 0L
        val end = if (sunflower) task.createdAt + SUNFLOWER_BLOOMS_AFTER_MS else task.dueAt
        if (now > end) return if (sunflower) "bloom" else "overripe"
        val progress = if (end > task.createdAt) (now - task.createdAt).toDouble() / (end - task.createdAt) else 1.0
        return when {
            progress < SPROUT_FROM -> "seed"
            progress < GROWING_FROM -> "sprout"
            progress < RIPE_FROM -> "growing"
            sunflower -> "bloom"
            else -> "ripe"
        }
    }

    /** What to store when the field is watered at [now]: the start of that day. */
    fun wateredOn(now: Long, zone: TimeZone = TimeZone.getDefault()): Long = DayMath.midnight(now, zone)

    /** Each day starts thirsty: the field is watered only if it was watered today. */
    fun isWatered(wateredOn: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        wateredOn == DayMath.midnight(now, zone)

    /** Nobody watered the field today and the evening has come: it rains, so it never stays dry. */
    fun rainsOnHome(wateredOn: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        !isWatered(wateredOn, now, zone) && DayMath.hourOf(now, zone) >= EVENING_RAIN_HOUR
}
