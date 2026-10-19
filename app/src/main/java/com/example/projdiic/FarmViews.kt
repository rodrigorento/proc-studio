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
        cropName(FarmRules.crop(task)) + " bed, " + stageWords(FarmRules.stage(task, now))

    /** "Radish", "Sunflower": a crop as a seed packet names it. */
    fun cropName(crop: String): String = crop.replaceFirstChar { it.uppercaseChar() }

    /** "Gym grows into carrots, ripe by Sunday.", for the Planted screen; [due] is [WhenText.due], null for Anytime. */
    fun plantedLine(title: String, crop: String, due: String?): String {
        val into = when (crop) {
            FarmRules.RADISH -> "radishes"
            FarmRules.LETTUCE -> "lettuce"
            FarmRules.CARROT -> "carrots"
            FarmRules.PUMPKIN -> "a pumpkin"
            else -> "a sunflower"
        }
        val days = FarmRules.SUNFLOWER_BLOOMS_AFTER_MS / (24 * 3_600_000L)
        return if (due == null) "$title grows into $into that blooms in $days days." else "$title grows into $into, ripe $due."
    }

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
