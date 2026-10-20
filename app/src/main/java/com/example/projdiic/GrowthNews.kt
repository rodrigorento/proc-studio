package com.example.projdiic

import android.content.Context
import java.util.TimeZone

/**
 * "Your carrots sprouted!": a crop that has grown a stage since the farm last showed it. The farm tells it once, under
 * the title, and its bed pops.
 */
object GrowthNews {
    /** [task]'s crop has reached [stage] since it was last seen. */
    class News(val task: Task, val stage: String)

    /** One line of news, [headline], about the crops of [taskIds]. */
    class Told(val headline: String, val taskIds: Set<String>)

    /** How far along each stage is, to tell growth from a crop planted again (back to seed). */
    private val ORDER = listOf("seed", "sprout", "growing", "ripe", "overripe")

    /** Stages worth telling: gone to seed isn't good news. */
    private val TOLD = setOf("sprout", "growing", "ripe", "bloom")

    /**
     * The crops in [tasks] that have grown since [seen] (task id to the stage it was last shown at), each at the stage it
     * has reached. A crop never seen is just planted: nothing to tell yet.
     */
    fun news(tasks: List<Task>, seen: Map<String, String>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<News> =
        tasks.mapNotNull { task ->
            val was = seen[task.id] ?: return@mapNotNull null
            val stage = FarmRules.stage(task, now, zone)
            if (stage in TOLD && rank(stage) > rank(was)) News(task, stage) else null
        }

    /** [news] as lines to tell: two crops with the same news ("Your radishes are ripe!") share one. */
    fun told(news: List<News>): List<Told> =
        news.groupBy { headline(FarmRules.crop(it.task), it.stage) }.map { (line, same) -> Told(line, same.map { it.task.id }.toSet()) }

    /** Every crop in [tasks] as it is at [now]: what has been seen once the farm has shown them. */
    fun seen(tasks: List<Task>, now: Long, zone: TimeZone = TimeZone.getDefault()): Map<String, String> =
        tasks.associate { it.id to FarmRules.stage(it, now, zone) }

    /** "Your carrots sprouted!", "Your lettuce is ripe!", "Your sunflower bloomed!" */
    fun headline(crop: String, stage: String): String {
        val (name, many) = when (crop) {
            FarmRules.RADISH -> "radishes" to true
            FarmRules.CARROT -> "carrots" to true
            FarmRules.GRAPE -> "grapes" to true
            else -> crop to false
        }
        val verb = if (many) "are" else "is"
        return when (stage) {
            "sprout" -> "Your $name sprouted!"
            "bloom" -> "Your $name bloomed!"
            else -> "Your $name $verb $stage!"
        }
    }

    // A sunflower's bloom stands where the others ripen.
    private fun rank(stage: String): Int = ORDER.indexOf(if (stage == "bloom") "ripe" else stage)

    /** The stage each crop was last shown at, on this phone. */
    class Seen(context: Context) {
        private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun get(): Map<String, String> = prefs.all.mapNotNull { (id, stage) -> (stage as? String)?.let { id to it } }.toMap()

        /** Keeps only [seen]: a crop harvested or removed is forgotten. */
        fun set(seen: Map<String, String>) {
            prefs.edit().clear().apply { seen.forEach { (id, stage) -> putString(id, stage) } }.apply()
        }

        companion object {
            const val PREF_NAME = "growth_seen"
        }
    }
}
