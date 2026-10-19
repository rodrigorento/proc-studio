package com.example.projdiic

import androidx.annotation.DrawableRes

/** The farm art for a task on the overlay screens. */
object FarmViews {
    /** The task's bed as it looks at [now]: its crop at its stage, in [light], watered or not. */
    @DrawableRes
    fun bed(task: Task, now: Long, wet: Boolean, light: String = fieldLight(now)): Int =
        CropArt.bed(FarmRules.crop(task), FarmRules.stage(task, now), wet, light)

    /** For screen readers: "Radish bed, ripe". */
    fun describe(task: Task, now: Long): String =
        FarmRules.crop(task).replaceFirstChar { it.uppercaseChar() } + " bed, " + stageWords(FarmRules.stage(task, now))

    /** A [FarmRules.stage] in words, for the home screen and screen readers. */
    fun stageWords(stage: String): String = when (stage) {
        "seed" -> "just planted"
        "sprout" -> "sprouting"
        "growing" -> "growing"
        "ripe" -> "ripe"
        "bloom" -> "in bloom"
        else -> "gone to seed"
    }
}
