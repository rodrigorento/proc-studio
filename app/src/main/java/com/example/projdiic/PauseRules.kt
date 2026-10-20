package com.example.projdiic

import java.util.TimeZone

/**
 * Which pause a trigger (the swipes, the minutes, or the facilitator) opens over Instagram:
 * - a due crop ([FarmRules.isDue]) gets the firm pause, or its check-in: harvesting matters more than watering;
 * - nothing while a crop is on its 5 more minutes: they're his to scroll;
 * - while any crop is still dry today, the farm, every time, until every bed is watered;
 * - with nothing for now (no tasks, or all of them for later today), the farm to ask for tasks, once a session;
 * - otherwise nothing: he scrolls on. The facilitator's pause opens the farm all the same, for testing.
 */
object PauseRules {
    /**
     * The [screen] to open, or null to let him scroll, and [why], for the log. [asks] when it is also the ask for tasks
     * (the farm with nothing for now on it), which comes once a session.
     */
    data class Pick(val screen: GateScreen?, val why: String, val asks: Boolean = false)

    fun pick(
        tasks: List<Task>,
        now: Long,
        mayAskForTasks: Boolean,
        facilitator: Boolean,
        checkInEnabled: Boolean,
        zone: TimeZone = TimeZone.getDefault(),
    ): Pick {
        val next = TaskRules.next(tasks, now, zone)
        if (next != null && FarmRules.isDue(next, now, zone)) {
            return if (TaskRules.needsCheckIn(next, checkInEnabled, now, zone)) {
                Pick(GateScreen.CheckIn(next.id), "check_in")
            } else {
                Pick(GateScreen.Gate(next.id), "due")
            }
        }
        if (tasks.any { TaskRules.onFiveMoreMinutes(it, now) }) return Pick(null, "on_five_more_minutes")
        val ask = TaskRules.shouldAskForTasks(tasks, now)
        if (FarmRules.needsWater(tasks, now, zone)) return Pick(GateScreen.AddTasks, "water", asks = ask)
        if (ask && (mayAskForTasks || facilitator)) return Pick(GateScreen.AddTasks, "ask", asks = true)
        if (facilitator) return Pick(GateScreen.AddTasks, "facilitator")
        val why = when {
            ask -> "asked_this_session"
            tasks.isNotEmpty() && next == null -> "full_list_all_for_later"
            else -> "all_watered"
        }
        return Pick(null, why)
    }
}
