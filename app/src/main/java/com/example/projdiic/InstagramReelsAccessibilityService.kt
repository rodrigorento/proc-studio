package com.example.projdiic

import android.accessibilityservice.AccessibilityService
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

    /** Why the farm over Instagram opened ([PauseRules.Pick.why]), for the log when it closes. */
    private var farmOpenedFor: String? = null
    private val session = InstagramSession(SESSION_GAP_MS)

    companion object {
        private const val TAG = "ReelsBlocker"
        private const val INSTAGRAM = "com.instagram.android"

        /** A gate he walked away from for longer than this is dropped; the next stretch starts fresh. */
        private const val STALE_GATE_MS = 10 * 60_000L

        /** Instagram away for longer than this ends an Instagram session ([InstagramSession]). */
        private const val SESSION_GAP_MS = 10 * 60_000L

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
        markLeft(System.currentTimeMillis())
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
        session.seen(now)
        if (current != null && now - leftInstagramAt > STALE_GATE_MS) {
            EventLog.log(this, "gate_expired", "screen" to current.logName, "away_ms" to now - leftInstagramAt)
            screen = null
        } else if (current != null) {
            gateOpenedAt = now
            EventLog.log(this, "gate_shown_again", "screen" to current.logName, "away_ms" to now - leftInstagramAt)
        }
        // Opening Instagram with nothing for now on the list asks for tasks straight away, once a session. The window
        // event itself says Instagram is on screen, so there's no need to check the active window.
        if (screen == null && !settings.isSnoozed(now) && shouldAskNow(now)) {
            openGate(TriggerReason.EMPTY_LIST, now, confirmActive = false)
        } else {
            render()
        }
    }

    private fun shouldAskNow(now: Long): Boolean =
        session.mayAskForTasks && !trigger.coolingDown(now) && TaskRules.shouldAskForTasks(TaskCache.getTasks(this), now)

    /** [pkg], [className] and [windowType] describe the window that replaced Instagram, for the log. */
    private fun onLeftInstagram(pkg: String, className: String?, windowType: Int?) {
        val current = screen ?: return
        val task = current.taskId?.let { TaskCache.find(this, it) }
        // Walking away from a gate counts as passing on its task (unless it is being harvested).
        if (task != null && !overlay.busy) TaskCache.update(this, passOnce(task))
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
        val now = System.currentTimeMillis()
        session.seen(now)
        if (screen != null) {
            render()
            return
        }
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
        val pick = PauseRules.pick(
            tasks, now,
            mayAskForTasks = session.mayAskForTasks,
            facilitator = reason == TriggerReason.FACILITATOR,
            checkInEnabled = settings.checkInEnabled,
        )
        val opened = pick.screen
        if (opened == null) {
            // Every crop watered and none due, tasks asked for already this session, or all for later: let him scroll.
            EventLog.log(this, "gate_skipped", "trigger" to reason.logName, "why" to pick.why)
            trigger.startCooldown(now, settings.triggerConfig())
            return
        }
        if (confirmActive && activePackage() != INSTAGRAM) {
            // A late scroll event after Instagram left: never open the gate over another app.
            markLeft(now)
            return
        }
        Log.d(TAG, "Opening the ${opened.logName} after ${reason.logName}")
        gateOpenedAt = now
        passCounted = false
        val wordings = resources.getStringArray(R.array.gate_eyebrows)
        overlay.eyebrow = wordings[settings.nextWordingIndex() % wordings.size]
        screen = opened
        overlay.slideIn = true
        if (!render()) {
            trigger.startCooldown(now, settings.triggerConfig())
            return
        }
        // The farm with nothing for now on it is the ask for tasks too, watering or not: once a session.
        if (pick.asks) session.askedForTasks()
        farmOpenedFor = pick.why
        val firm = opened.taskId != null
        EventLog.log(
            this, "gate_shown",
            "trigger" to reason.logName,
            "screen" to opened.logName,
            "why" to pick.why,
            "task" to next?.title?.takeIf { firm },
            "passes" to next?.passes?.takeIf { firm },
            "due" to next?.let { FarmRules.isDue(it, now) }?.takeIf { firm },
            "snooze_left" to next?.let { opened is GateScreen.Gate && !it.snoozeUsed }?.takeIf { firm },
            "tasks" to tasks.size,
            "dry" to tasks.count { !FarmRules.isWatered(it, now) },
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
     * Closes the overlay. After a gate a cooldown follows; after the ask-for-tasks screen ([withCooldown] false) the
     * count starts again at once, though the ask itself doesn't come back this session.
     */
    private fun backToFeed(withCooldown: Boolean = true) {
        screen = null
        val now = System.currentTimeMillis()
        if (withCooldown) trigger.startCooldown(now, settings.triggerConfig()) else trigger.startCountAgain(now)
        currentReelsCount = 0
        overlay.hide()
        EventLog.log(this, "back_to_feed", "cooldown_min" to if (withCooldown) settings.cooldownMinutes else 0)
    }

    /** Instagram is no longer on screen as of [now]. */
    private fun markLeft(now: Long) {
        instagramOnScreen = false
        leftInstagramAt = now
        session.left(now)
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

    override fun onHarvested(task: Task, where: String, tappedAt: Long) {
        val ready = latest(task)?.let { FarmRules.isReady(it, tappedAt) }
        EventLog.log(
            this, "choice",
            "choice" to "harvest",
            "screen" to where,
            "task" to task.title,
            "ripe" to ready,
            "ms_on_gate" to tappedAt - gateOpenedAt,
        )
        TaskCache.remove(this, task.id)
        EventLog.log(this, "task_done", "task" to task.title, "screen" to where)
        Toast.makeText(this, R.string.toast_done, Toast.LENGTH_SHORT).show()
        backToFeed()
    }

    override fun onSnooze(task: Task) {
        val current = latest(task) ?: return backToFeed()
        if (current.snoozeUsed) return
        TaskCache.update(this, TaskRules.snooze(passOnce(current), System.currentTimeMillis()))
        EventLog.log(this, "choice", "choice" to "snooze", "task" to task.title, "ms_on_gate" to msOnGate())
        backToFeed()
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

    override fun onTaskRemoved(task: Task) {
        TaskCache.remove(this, task.id)
        EventLog.log(
            this, "task_removed",
            "where" to "overlay",
            "task" to task.title,
            "stage" to FarmRules.stage(task, System.currentTimeMillis()),
        )
    }

    override fun onFieldHarvested(task: Task, ripe: Boolean) {
        TaskCache.remove(this, task.id)
        EventLog.log(this, "task_done", "task" to task.title, "screen" to GateScreen.AddTasks.logName, "ripe" to ripe)
    }

    override fun onFieldWatered(task: Task, via: String) {
        val now = System.currentTimeMillis()
        val current = latest(task) ?: return
        TaskCache.update(this, TaskRules.water(current, now))
        EventLog.log(
            this, "watered",
            "where" to "overlay",
            "via" to via,
            "task" to task.title,
            "stage" to FarmRules.stage(current, now),
            "dry_left" to TaskCache.getTasks(this).count { !FarmRules.isWatered(it, now) },
            "ms_on_gate" to now - gateOpenedAt,
        )
    }

    override fun onTaskRestored(task: Task, index: Int, after: String): Boolean {
        if (!TaskCache.restore(this, task, index)) return false
        val now = System.currentTimeMillis()
        EventLog.log(
            this, "task_restored",
            "where" to "overlay",
            "task" to task.title,
            "after" to after,
            "stage" to FarmRules.stage(task, now),
            "ms_on_gate" to now - gateOpenedAt,
        )
        return true
    }

    override fun onSnoozeAppUntilTomorrow() {
        // Until the next farm day (4:00), not midnight: closed late at night, the crops would be dry again at once.
        val until = FarmRules.nextDayStart(System.currentTimeMillis())
        settings.snoozedUntil = until
        EventLog.log(this, "app_snoozed", "until_ms" to until, "tasks" to TaskCache.getTasks(this).size)
        Toast.makeText(this, R.string.toast_app_snoozed, Toast.LENGTH_LONG).show()
        backToFeed(withCooldown = false)
    }

    override fun onAddTasksClosed() {
        val tasks = TaskCache.getTasks(this)
        val now = System.currentTimeMillis()
        EventLog.log(
            this, "add_tasks_closed",
            "why" to farmOpenedFor,
            "tasks" to tasks.size,
            "dry" to tasks.count { !FarmRules.isWatered(it, now) },
        )
        // No cooldown: while a crop is still dry the farm comes back after the usual swipes; it won't ask for tasks
        // again this session.
        backToFeed(withCooldown = false)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isServiceEnabled = false
        overlay.hide()
        super.onDestroy()
    }
}
