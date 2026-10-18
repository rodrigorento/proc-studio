package com.example.projdiic

/** One pending thing on the list. Times are epoch milliseconds. */
data class Task(
    val id: String,
    val title: String,
    val createdAt: Long,
    val addedVia: String = VIA_TEXT,
    /** The task stays off the gate until this time; 0 when it was never put off. */
    val deferredUntil: Long = 0L,
    /** [BY_SNOOZE] or [BY_TIME]: how the task was last put off. */
    val deferredBy: String = "",
    val snoozeUsed: Boolean = false,
    /** Gates for this task that ended without it being done. */
    val passes: Int = 0,
) {
    companion object {
        const val VIA_TEXT = "text"
        const val VIA_VOICE = "voice"
        const val BY_SNOOZE = "snooze"
        const val BY_TIME = "time"
    }
}

object TaskRules {
    const val MAX_TASKS = 3
    const val SNOOZE_MS = 5 * 60_000L
    const val CHECK_IN_AFTER_PASSES = 2
    const val MAX_TITLE_LENGTH = 120

    /** The one task the gate shows: a task whose picked time has come first, then the oldest. */
    fun next(tasks: List<Task>, now: Long): Task? =
        tasks.filter { it.deferredUntil <= now }
            .sortedWith(
                compareBy<Task>(
                    { if (it.deferredUntil > 0L) 0 else 1 },
                    { it.deferredUntil },
                    { it.createdAt },
                )
            )
            .firstOrNull()

    fun snooze(task: Task, now: Long): Task =
        task.copy(deferredUntil = now + SNOOZE_MS, deferredBy = Task.BY_SNOOZE, snoozeUsed = true)

    fun pickTime(task: Task, at: Long): Task =
        task.copy(deferredUntil = at, deferredBy = Task.BY_TIME)

    /** The gate ended without the task being done. A picked time only jumps the queue once. */
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
