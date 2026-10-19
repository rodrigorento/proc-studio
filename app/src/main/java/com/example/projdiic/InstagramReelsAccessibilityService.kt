package com.example.projdiic

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Watches Instagram and, after a stretch of scrolling, pauses it with the next
 * pending task. The screens only show while Instagram is on screen: leaving
 * Instagram hides them and coming back brings them back, so the gate blocks
 * the feed and nothing else on the phone.
 */
class InstagramReelsAccessibilityService : AccessibilityService(), GateOverlay.Actions {

    private val trigger = GateTrigger()
    private lateinit var settings: GateSettings
    private lateinit var overlay: GateOverlay

    private var screen: GateScreen? = null
    private var instagramOnScreen = false
    private var leftInstagramAt = 0L
    private var gateOpenedAt = 0L
    private var passCounted = false

    companion object {
        private const val TAG = "ReelsBlocker"
        private const val INSTAGRAM = "com.instagram.android"

        /** A gate he walked away from for longer than this is dropped; the next stretch starts fresh. */
        private const val STALE_GATE_MS = 10 * 60_000L

        var currentReelsCount = 0
            private set
        var isServiceEnabled = false
            private set

        /** Set from the facilitator panel: the next swipe in Instagram opens the gate. */
        @Volatile
        var pauseAtNextSwipe = false
    }

    override fun onCreate() {
        super.onCreate()
        settings = GateSettings(this)
        overlay = GateOverlay(this, this)
        isServiceEnabled = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                onWindowChanged(pkg, event.className?.toString(), event.windowId, event.isFullScreen)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (pkg == INSTAGRAM) onInstagramScroll()
        }
    }

    private fun onWindowChanged(pkg: String, className: String?, windowId: Int, fullScreen: Boolean) {
        if (pkg == INSTAGRAM) {
            if (!instagramOnScreen) onReturnedToInstagram()
            return
        }
        if (!instagramOnScreen) return
        val windowType = windowType(windowId)
        val opensOver = WindowRules.opensOverInstagram(
            pkg, className, windowType, fullScreen, packageName, MainActivity::class.java.name, keyboardPackage()
        )
        if (opensOver) {
            // Which windows opened over a screen without hiding it, to check the rules on a tester's phone.
            if (screen != null && pkg != packageName) {
                EventLog.log(
                    this, "window_ignored",
                    "package" to pkg,
                    "window" to className,
                    "window_type" to windowType,
                    "full_screen" to fullScreen,
                )
            }
            return
        }
        instagramOnScreen = false
        leftInstagramAt = System.currentTimeMillis()
        onLeftInstagram(pkg, className, windowType)
    }

    /**
     * The kind of window an event came from (an app screen, the keyboard, an overlay), or null if it isn't listed.
     * A window that has just opened is often not listed yet, so null is common and is treated as an app screen.
     */
    private fun windowType(windowId: Int): Int? =
        try {
            windows.firstOrNull { it.id == windowId }?.type
        } catch (e: RuntimeException) {
            null
        }

    private fun onReturnedToInstagram() {
        instagramOnScreen = true
        val current = screen
        val now = System.currentTimeMillis()
        if (current != null && now - leftInstagramAt > STALE_GATE_MS) {
            EventLog.log(this, "gate_expired", "screen" to current.logName, "away_ms" to now - leftInstagramAt)
            screen = null
        } else if (current != null) {
            gateOpenedAt = now
            EventLog.log(this, "gate_shown_again", "screen" to current.logName, "away_ms" to now - leftInstagramAt)
        }
        // Opening Instagram with nothing due on the list asks for tasks straight away. The window
        // event itself says Instagram is on screen, so there's no need to check the active window.
        if (screen == null && !settings.isSnoozed(now) && shouldAskNow(now)) {
            openGate(TriggerReason.EMPTY_LIST, now, confirmActive = false)
        } else {
            render()
        }
    }

    private fun shouldAskNow(now: Long): Boolean =
        !trigger.coolingDown(now) && TaskRules.shouldAskForTasks(TaskCache.getTasks(this), now)

    /** [pkg], [className] and [windowType] describe the window that replaced Instagram, for the log. */
    private fun onLeftInstagram(pkg: String, className: String?, windowType: Int?) {
        val current = screen ?: return
        val task = current.taskId?.let { TaskCache.find(this, it) }
        // Walking away from a gate counts as passing on its task, once the task was on screen (and isn't being harvested).
        if (task != null && !overlay.thirsty && !overlay.harvesting) TaskCache.update(this, passOnce(task))
        EventLog.log(
            this, "left_instagram",
            "screen" to current.logName,
            "task" to task?.title,
            "ms_on_gate" to msOnGate(),
            "to_package" to pkg,
            "to_window" to className,
            "window_type" to windowType,
        )
        render()
    }

    private fun onInstagramScroll() {
        // Scroll events can arrive just after Instagram has left the screen; only trust them while it's active.
        if (!instagramOnScreen) {
            if (activePackage() != INSTAGRAM) return
            onReturnedToInstagram()
        }
        if (screen != null) {
            render()
            return
        }
        val now = System.currentTimeMillis()
        val result = trigger.onScroll(now, settings.triggerConfig())
        if (!result.counted) return
        currentReelsCount = trigger.swipes
        EventLog.log(this, "swipe", "count" to result.swipes)

        val reason = when {
            // The facilitator's "Pause at the next swipe" works even while snoozed, for testing.
            pauseAtNextSwipe -> {
                pauseAtNextSwipe = false
                TriggerReason.FACILITATOR
            }
            settings.isSnoozed(now) -> null
            result.reason != null -> result.reason
            else -> null
        }
        if (reason != null) openGate(reason, now)
    }

    private fun openGate(reason: TriggerReason, now: Long, confirmActive: Boolean = true) {
        val tasks = TaskCache.getTasks(this)
        val next = TaskRules.next(tasks, now)
        val opened = when {
            TaskRules.shouldAskForTasks(tasks, now) -> GateScreen.AddTasks
            next == null -> null
            TaskRules.needsCheckIn(next, settings.checkInEnabled) -> GateScreen.CheckIn(next.id)
            else -> GateScreen.Gate(next.id)
        }
        if (opened == null) {
            // A full list, all of it for later: nothing to show or ask for, so let him scroll.
            EventLog.log(this, "gate_skipped", "trigger" to reason.logName, "why" to "full_list_all_for_later")
            trigger.startCooldown(now, settings.triggerConfig())
            return
        }
        if (confirmActive && activePackage() != INSTAGRAM) {
            // A late scroll event after Instagram left: never open the gate over another app.
            instagramOnScreen = false
            return
        }
        Log.d(TAG, "Opening the ${opened.logName} after ${reason.logName}")
        gateOpenedAt = now
        passCounted = false
        val wordings = resources.getStringArray(R.array.gate_eyebrows)
        overlay.eyebrow = wordings[settings.nextWordingIndex() % wordings.size]
        screen = opened
        if (!render()) {
            trigger.startCooldown(now, settings.triggerConfig())
            return
        }
        EventLog.log(
            this, "gate_shown",
            "trigger" to reason.logName,
            "screen" to opened.logName,
            "task" to next?.title,
            "passes" to next?.passes,
            "snooze_left" to next?.let { !it.snoozeUsed },
            "tasks" to tasks.size,
            "sound_paused" to overlay.holdsAudioFocus,
        )
    }

    private fun show(next: GateScreen) {
        screen = next
        render()
    }

    /** Returns false when a screen should be on Instagram but could not be drawn. */
    private fun render(): Boolean {
        val current = screen
        if (current == null || !instagramOnScreen) {
            overlay.hide()
            return true
        }
        val task = current.taskId?.let { TaskCache.find(this, it) }
        if (current.taskId != null && task == null) {
            backToFeed()
            return true
        }
        if (overlay.show(current, task)) return true
        // For example the overlay permission was taken away: drop the gate rather than stop counting for good.
        screen = null
        EventLog.log(this, "overlay_failed", "screen" to current.logName)
        return false
    }

    /**
     * Closes the overlay. After a gate a cooldown follows; after the ask-for-tasks screen
     * ([withCooldown] false) it comes back after the usual number of swipes.
     */
    private fun backToFeed(withCooldown: Boolean = true) {
        screen = null
        val now = System.currentTimeMillis()
        if (withCooldown) trigger.startCooldown(now, settings.triggerConfig()) else trigger.startCountAgain(now)
        currentReelsCount = 0
        overlay.hide()
        EventLog.log(this, "back_to_feed", "cooldown_min" to if (withCooldown) settings.cooldownMinutes else 0)
    }

    /** Counts at most one pass per gate, however it ends. */
    private fun passOnce(task: Task): Task {
        if (passCounted) return task
        passCounted = true
        return TaskRules.pass(task)
    }

    private fun latest(task: Task): Task? = TaskCache.find(this, task.id)

    private fun msOnGate(): Long = System.currentTimeMillis() - gateOpenedAt

    private fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun keyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    // GateOverlay.Actions: what each tap does.

    override fun onHarvested(task: Task, where: String) {
        EventLog.log(this, "choice", "choice" to "harvest", "screen" to where, "task" to task.title, "ms_on_gate" to msOnGate())
        TaskCache.remove(this, task.id)
        EventLog.log(this, "task_done", "task" to task.title, "screen" to where)
        Toast.makeText(this, R.string.toast_done, Toast.LENGTH_SHORT).show()
        backToFeed()
    }

    override fun onPickTime(task: Task) {
        EventLog.log(this, "choice", "choice" to "pick_a_time", "task" to task.title, "ms_on_gate" to msOnGate())
        show(GateScreen.PickTime(task.id))
    }

    override fun onSnooze(task: Task) {
        val current = latest(task) ?: return backToFeed()
        if (current.snoozeUsed) return
        TaskCache.update(this, TaskRules.snooze(passOnce(current), System.currentTimeMillis()))
        EventLog.log(this, "choice", "choice" to "snooze", "task" to task.title, "ms_on_gate" to msOnGate())
        backToFeed()
    }

    override fun onTimeChosen(task: Task, choice: TimeChoice) {
        val current = latest(task) ?: return backToFeed()
        TaskCache.update(this, TaskRules.pickTime(passOnce(current), choice.at))
        EventLog.log(this, "time_picked", "option" to choice.key, "task" to task.title, "back_at_ms" to choice.at)
        backToFeed()
    }

    override fun onAddToCalendar(task: Task) {
        val current = latest(task) ?: return backToFeed()
        val startTime = System.currentTimeMillis() + 15 * 60_000L
        val endTime = startTime + 30 * 60_000L
        TaskCache.update(this, TaskRules.pickTime(passOnce(current), startTime))
        EventLog.log(this, "time_picked", "option" to "calendar", "task" to task.title, "back_at_ms" to startTime)
        backToFeed()

        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, task.title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.calendar_description))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(calendarIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No calendar app", e)
            Toast.makeText(this, R.string.calendar_missing, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackToChoices(task: Task, from: GateScreen) {
        EventLog.log(this, "back_to_choices", "from" to from.logName, "task" to task.title)
        show(GateScreen.Gate(task.id))
    }

    override fun onCheckInAccepted(task: Task) {
        val current = latest(task) ?: return backToFeed()
        TaskCache.update(this, current.copy(passes = 0))
        // The Session 3 team watches for this event and sends the real message by hand.
        EventLog.log(this, "check_in", "choice" to "accepted", "task" to task.title)
        val person = settings.trustedPerson.ifBlank { getString(R.string.trusted_person_fallback) }
        Toast.makeText(this, getString(R.string.toast_check_in, person), Toast.LENGTH_LONG).show()
        backToFeed()
    }

    override fun onCheckInOff(task: Task) {
        settings.checkInEnabled = false
        EventLog.log(this, "check_in", "choice" to "turned_off", "task" to task.title)
        show(GateScreen.Gate(task.id))
    }

    override fun onTaskPlanted(title: String, window: TimeWindow?, via: String): Task? {
        val task = TaskCache.plant(this, title, window, via) ?: return null
        EventLog.log(
            this, "task_added",
            "via" to via,
            "where" to "overlay",
            "task" to task.title,
            "when" to task.whenKey.ifEmpty { "anytime" },
            "due_ms" to task.dueAt.takeIf { it > 0 },
            "crop" to FarmRules.crop(task),
        )
        return task
    }

    override fun onWhenChanged(task: Task, window: TimeWindow?) {
        val current = latest(task) ?: return
        val changed = TaskRules.withWhen(current, window, System.currentTimeMillis())
        TaskCache.update(this, changed)
        EventLog.log(
            this, "when_changed",
            "where" to "overlay",
            "task" to task.title,
            "from" to current.whenKey.ifEmpty { "anytime" },
            "to" to (window?.key ?: "anytime"),
            "due_ms" to window?.end,
            "crop" to FarmRules.crop(changed),
        )
    }

    override fun onTaskRemoved(task: Task) {
        TaskCache.remove(this, task.id)
        EventLog.log(this, "task_removed", "where" to "overlay", "task" to task.title)
    }

    override fun onSnoozeAppUntilTomorrow() {
        val until = WhenChoices.startOfTomorrow(System.currentTimeMillis())
        settings.snoozedUntil = until
        EventLog.log(this, "app_snoozed", "until_ms" to until, "tasks" to TaskCache.getTasks(this).size)
        Toast.makeText(this, R.string.toast_app_snoozed, Toast.LENGTH_LONG).show()
        backToFeed(withCooldown = false)
    }

    override fun onFieldWatered(by: String, how: String) {
        if (by == GateOverlay.BY_CAN) {
            FarmCache.water(this)
        } else {
            // Rain the player chose has played already; rain from leaving the gate shows the next time the field does.
            FarmCache.rain(this, show = how != "chose")
        }
        EventLog.log(this, "field_watered", "by" to by, "where" to "gate", "how" to how)
    }

    override fun onAddTasksClosed() {
        EventLog.log(this, "add_tasks_closed", "tasks" to TaskCache.getTasks(this).size)
        // With nothing for now, it asks again after the next few swipes rather than after a cooldown.
        backToFeed(withCooldown = false)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isServiceEnabled = false
        overlay.hide()
        super.onDestroy()
    }
}
