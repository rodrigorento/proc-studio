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
        FarmRules.crop(task).replaceFirstChar { it.uppercaseChar() } + " bed, " + FarmRules.stage(task, now)
}
