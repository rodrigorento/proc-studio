package com.example.projdiic

import android.content.Context
import java.util.TimeZone

/**
 * How the farm is going, under its title: the harvests this week (from Monday's farm day). Kept on this phone apart from
 * the log, which a facilitator exports.
 */
object FarmTally {
    /** A harvest of [taskId] at [at]; Undo takes it back. */
    data class Harvest(val taskId: String, val at: Long)

    /** Harvests kept: more than a week needs. */
    private const val KEEP_HARVESTS_MS = 60 * 24 * 3_600_000L

    /**
     * Harvests since this week's Monday farm day started (4:00, so Sunday night still counts as last week). No end: the
     * screen's [now] moves once a second, so a harvest just made can be a moment after it.
     */
    fun harvestsThisWeek(harvests: List<Harvest>, now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val monday = DayMath.at(DayMath.monday(FarmRules.dayStart(now, zone), zone), zone, FarmRules.DAY_STARTS_AT_HOUR)
        return harvests.count { it.at >= monday }
    }

    /** [harvests] with [taskId]'s harvest at [at] added, letting go of old ones. */
    fun withHarvest(harvests: List<Harvest>, taskId: String, at: Long): List<Harvest> =
        harvests.filter { at - it.at < KEEP_HARVESTS_MS } + Harvest(taskId, at)

    /** [harvests] without [taskId]'s latest harvest: Undo brought the crop back. */
    fun withoutHarvest(harvests: List<Harvest>, taskId: String): List<Harvest> {
        val last = harvests.indexOfLast { it.taskId == taskId }
        return if (last < 0) harvests else harvests.filterIndexed { i, _ -> i != last }
    }

    fun encodeHarvests(harvests: List<Harvest>): String = harvests.joinToString(";") { "${it.taskId}@${it.at}" }

    fun decodeHarvests(text: String): List<Harvest> = text.split(";").mapNotNull { entry ->
        val id = entry.substringBeforeLast("@", "")
        val at = entry.substringAfterLast("@", "").toLongOrNull()
        if (id.isEmpty() || at == null) null else Harvest(id, at)
    }

    /** "3 harvests this week", or null before the first. */
    fun harvestLine(count: Int): String? = when (count) {
        0 -> null
        1 -> "1 harvest this week"
        else -> "$count harvests this week"
    }

    /** The tally on this phone; every harvest, in the app or over Instagram, goes through it. */
    class Store(context: Context) {
        private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val harvests: List<Harvest> get() = decodeHarvests(prefs.getString(KEY_HARVESTS, "").orEmpty())

        fun harvested(task: Task, at: Long) {
            prefs.edit().putString(KEY_HARVESTS, encodeHarvests(withHarvest(harvests, task.id, at))).apply()
        }

        /** Undo after a harvest. */
        fun unharvested(task: Task) {
            prefs.edit().putString(KEY_HARVESTS, encodeHarvests(withoutHarvest(harvests, task.id))).apply()
        }

        /** "Reset all data" in the facilitator panel. */
        fun clear() {
            prefs.edit().clear().apply()
        }

        companion object {
            const val PREF_NAME = "farm_tally"
            private const val KEY_HARVESTS = "harvests"
        }
    }
}
