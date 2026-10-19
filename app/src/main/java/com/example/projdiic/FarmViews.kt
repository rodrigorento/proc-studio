package com.example.projdiic

import androidx.annotation.DrawableRes

/** The farm art for a task on the overlay screens. */
object FarmViews {
    /** The task's bed as it looks at [now]: its crop at its stage, watered or not. */
    @DrawableRes
    fun bed(task: Task, now: Long, wet: Boolean): Int = CropArt.bed(FarmRules.crop(task), FarmRules.stage(task, now), wet)

    /** For screen readers, in the words the gate shows: "Radish seeds", "Carrot, ripe". */
    fun describe(task: Task, now: Long): String =
        cropAndStage(FarmRules.crop(task), FarmRules.stage(task, now)).replaceFirstChar { it.uppercaseChar() }

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

    /** "radish seeds", "carrot, ripe": a crop and how far it has grown, for the gate and the plot sheet. */
    fun cropAndStage(crop: String, stage: String): String =
        if (stage == "seed") "$crop seeds" else "$crop, ${stageWords(stage)}"

    /** A [FarmRules.stage] in words. */
    private fun stageWords(stage: String): String = when (stage) {
        "seed" -> "just planted"
        "sprout" -> "sprouting"
        "growing" -> "growing"
        "ripe" -> "ripe"
        "bloom" -> "in bloom"
        else -> "gone to seed"
    }
}
