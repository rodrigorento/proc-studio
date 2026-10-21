package com.example.projdiic

/**
 * One stretch of Instagram use: it ends once Instagram has gone [gapMs] without being seen (away in another app, or
 * the phone locked inside it), so a quick trip to another app keeps it. With nothing for now, the gate asks for tasks
 * once per session: asking again while there's nothing to do teaches people to tap Not now without reading.
 */
class InstagramSession(private val gapMs: Long) {
    private var lastSeenAt = -1L

    /** Whether the gate may still ask for tasks in this session. */
    var mayAskForTasks = true
        private set

    /** Instagram is on screen at [now] (it came back, or it scrolled); after a long enough gap, a new session starts. */
    fun seen(now: Long) {
        if (lastSeenAt < 0L || now - lastSeenAt > gapMs) {
            mayAskForTasks = true
        }
        lastSeenAt = now
    }

    /** Instagram left the screen at [now]: the gap counts from here. */
    fun left(now: Long) {
        lastSeenAt = now
    }

    fun askedForTasks() {
        mayAskForTasks = false
    }
}
