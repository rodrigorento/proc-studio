package com.example.projdiic

import androidx.annotation.DrawableRes
import java.util.TimeZone

/** The farm art for a task on the overlay screens and the home field. */
object FarmViews {
    /** How long a crop that isn't ripe yet takes to ripen before its harvest. */
    const val RIPEN_MS = 300L

    /** The task's bed as it looks at [now]: its crop at its stage, watered today or dry. */
    @DrawableRes
    fun bed(task: Task, now: Long): Int =
        CropArt.bed(FarmRules.crop(task), FarmRules.stage(task, now), FarmRules.isWatered(task, now))

    /** For screen readers, in the words the gate shows: "Radish seeds", "Carrot, ripe". */
    fun describe(task: Task, now: Long): String =
        cropAndStage(FarmRules.crop(task), FarmRules.stage(task, now)).replaceFirstChar { it.uppercaseChar() }

    /** "Radish", "Grapes": a crop as a seed packet names it. */
    fun cropName(crop: String): String = if (crop == FarmRules.GRAPE) "Grapes" else crop.replaceFirstChar { it.uppercaseChar() }

    /**
     * "Gym grows into carrots, ripe by Sunday.", for the Planted screen; [due] is [WhenText.due], null for Anytime, when
     * the sunflower blooms in [bloomDays] ([bloomDays] of the task just planted).
     */
    fun plantedLine(title: String, crop: String, due: String?, bloomDays: Int): String {
        val into = when (crop) {
            FarmRules.RADISH -> "radishes"
            FarmRules.LETTUCE -> "lettuce"
            FarmRules.CARROT -> "carrots"
            FarmRules.CORN -> "corn"
            FarmRules.PUMPKIN -> "a pumpkin"
            FarmRules.GRAPE -> "grapes"
            else -> "a sunflower"
        }
        return if (due == null) "$title grows into $into that ${blooms(bloomDays)}." else "$title grows into $into, ripe $due."
    }

    /** "Harvest by Sunday", "Harvest now", "Gone to seed", "Blooms in 2 days": when the crop is ready, under its name on the field. */
    fun harvestLine(task: Task, now: Long, zone: TimeZone, formats: WhenText.Formats): String = when (FarmRules.stage(task, now, zone)) {
        "ripe" -> "Harvest now"
        "overripe" -> "Gone to seed"
        "bloom" -> "In bloom"
        else -> WhenText.due(TaskRules.window(task), now, zone, formats)?.let { "Harvest $it" }
            ?: blooms(bloomDays(task, now, zone)).replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * "Carrots growing · 2 days, 9 hours left", "Radishes ripe · 5 hours left", "Sunflower growing · blooms in 2 days":
     * shown over a bed just watered, under its task's name.
     */
    fun wateredLine(task: Task, now: Long, zone: TimeZone, formats: WhenText.Formats): String {
        val crop = when (FarmRules.crop(task)) {
            FarmRules.RADISH -> "Radishes"
            FarmRules.LETTUCE -> "Lettuce"
            FarmRules.CARROT -> "Carrots"
            FarmRules.CORN -> "Corn"
            FarmRules.PUMPKIN -> "Pumpkin"
            FarmRules.GRAPE -> "Grapes"
            else -> "Sunflower"
        }
        val doing = when (FarmRules.stage(task, now, zone)) {
            "ripe" -> "ripe"
            "overripe" -> "gone to seed"
            "bloom" -> "in bloom"
            else -> "growing"
        }
        val left = WhenText.gateLine(TaskRules.window(task), now, zone, formats) ?: blooms(bloomDays(task, now, zone))
        return "$crop $doing · $left"
    }

    /** Calendar days from [now] until an Anytime task's sunflower blooms. */
    fun bloomDays(task: Task, now: Long, zone: TimeZone): Int = DayMath.daysBetween(now, FarmRules.bloomsAt(task), zone)

    private fun blooms(days: Int): String = when (days) {
        0 -> "blooms today"
        1 -> "blooms tomorrow"
        else -> "blooms in $days days"
    }

    /** "radish seeds", "carrot, ripe", "grapes, ripe": a crop and how far it has grown, for the gate and the plot sheet. */
    fun cropAndStage(crop: String, stage: String): String = when {
        stage == "seed" -> "$crop seeds"
        crop == FarmRules.GRAPE -> "grapes, ${stageWords(stage)}"
        else -> "$crop, ${stageWords(stage)}"
    }

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
