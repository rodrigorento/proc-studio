package com.example.projdiic

/** One pending thing on the list. Times are epoch milliseconds. */
data class Task(
    val id: String,
    val title: String,
    val createdAt: Long,
    val addedVia: String = VIA_TEXT,
    /** The task stays off the gate until this time; 0 when it was never put off. */
    val deferredUntil: Long = 0L,
    /** [BY_SNOOZE] or [BY_WHEN]: why the task is held back. */
    val deferredBy: String = "",
    val snoozeUsed: Boolean = false,
    /** Gates for this task that ended without it being done. */
    val passes: Int = 0,
    /** When it is due; 0 for Anytime. See [TaskRules.window]. */
    val dueAt: Long = 0L,
    val dueHasTime: Boolean = false,
    /** The chip it was given ([WhenChoices]), or empty for Anytime. */
    val whenKey: String = "",
    /** When its crop was planted: when its time was last set, or 0 to count from [createdAt]. See [FarmRules]. */
    val plantedAt: Long = 0L,
) {
    companion object {
        const val VIA_TEXT = "text"
        const val VIA_VOICE = "voice"
        const val BY_SNOOZE = "snooze"

        /** Waiting for the start of the time it was given when added ("tonight", "on Monday"). */
        const val BY_WHEN = "when"
    }
}

object TaskRules {
    const val MAX_TASKS = 4  // one per plot of the field
    const val SNOOZE_MS = 5 * 60_000L
    const val CHECK_IN_AFTER_PASSES = 2
    const val MAX_TITLE_LENGTH = 120

    /**
     * The one task the gate shows, among those whose time has started: the one due
     * soonest (one back from its 5 more minutes counts as due then), then the oldest.
     */
    fun next(tasks: List<Task>, now: Long): Task? =
        tasks.filter { it.deferredUntil <= now }
            .minWithOrNull(compareBy<Task>({ dueBy(it) }, { it.createdAt }))

    /**
     * Nothing on the list is for now (or there is no list): the gate asks for tasks instead.
     * Not when the list is full, since then there's nothing to ask for.
     */
    fun shouldAskForTasks(tasks: List<Task>, now: Long): Boolean =
        tasks.size < MAX_TASKS && next(tasks, now) == null

    private fun dueBy(task: Task): Long {
        val picked = if (task.deferredBy != Task.BY_WHEN) task.deferredUntil else 0L
        return listOf(task.dueAt, picked).filter { it > 0L }.minOrNull() ?: Long.MAX_VALUE
    }

    /** The time window the task was given, or null for Anytime. */
    fun window(task: Task): TimeWindow? {
        if (task.dueAt <= 0L) return null
        val start = if (task.deferredBy == Task.BY_WHEN) task.deferredUntil else 0L
        return TimeWindow(task.whenKey.ifEmpty { WhenChoices.CUSTOM }, start, task.dueAt, task.dueHasTime)
    }

    /**
     * Gives the task a time window (null: Anytime) and replants its crop at [now]. 5 more minutes still running
     * are kept.
     */
    fun withWhen(task: Task, window: TimeWindow?, now: Long): Task {
        val putOff = task.deferredBy != Task.BY_WHEN && task.deferredUntil > now
        val base = (if (putOff) task else task.copy(deferredUntil = 0L, deferredBy = "")).copy(plantedAt = now)
        if (window == null) return base.copy(dueAt = 0L, dueHasTime = false, whenKey = "")
        val waits = window.start > now && window.start > base.deferredUntil
        return base.copy(
            deferredUntil = if (waits) window.start else base.deferredUntil,
            deferredBy = if (waits) Task.BY_WHEN else base.deferredBy,
            dueAt = window.end,
            dueHasTime = window.hasTime,
            whenKey = window.key,
        )
    }

    fun snooze(task: Task, now: Long): Task =
        task.copy(deferredUntil = now + SNOOZE_MS, deferredBy = Task.BY_SNOOZE, snoozeUsed = true)

    /** The gate ended without the task being done. */
    fun pass(task: Task): Task = task.copy(passes = task.passes + 1, deferredUntil = 0L, deferredBy = "")

    fun needsCheckIn(task: Task, checkInEnabled: Boolean): Boolean =
        checkInEnabled && task.passes >= CHECK_IN_AFTER_PASSES

    fun cleanTitle(raw: String): String? =
        raw.replace(Regex("\\s+"), " ")
            .take(MAX_TITLE_LENGTH)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercaseChar() }
}
