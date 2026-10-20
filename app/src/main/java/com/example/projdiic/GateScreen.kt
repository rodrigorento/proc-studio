package com.example.projdiic

/** What the overlay shows while Instagram is on screen. */
sealed interface GateScreen {
    /** The task this screen is about; null for [AddTasks]. */
    val taskId: String?
    val logName: String

    /** Drawn as a sheet over the dimmed feed rather than as a full screen. */
    val overFeed: Boolean get() = false

    /** A due crop and its ways out: harvest it, or 5 more minutes once. */
    data class Gate(override val taskId: String) : GateScreen {
        override val logName get() = "gate"
        override val overFeed get() = true
    }

    /**
     * The light pause for a crop that isn't due yet, once a session: a small card over the lightly dimmed feed with
     * the time left and Water it. Walking away from it isn't passing on the task.
     */
    data class Nod(override val taskId: String) : GateScreen {
        override val logName get() = "nod"
    }

    /** After two passes on the same task, when he has switched check-ins on. */
    data class CheckIn(override val taskId: String) : GateScreen {
        override val logName get() = "check_in"
    }

    /** Shown instead of the gate while the list is empty. */
    data object AddTasks : GateScreen {
        override val taskId: String? get() = null
        override val logName get() = "add_tasks"
    }
}
