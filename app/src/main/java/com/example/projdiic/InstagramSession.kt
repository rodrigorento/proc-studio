package com.example.projdiic

/**
 * One stretch of Instagram use: it ends once Instagram has gone [gapMs] without being seen (away in another app, or
 * the phone locked inside it), so a quick trip to another app keeps it. With nothing for now, the gate asks for tasks
 * once per session: asking again while there's nothing to do teaches people to tap Not now without reading. A crop
 * not due yet gets one light pause per session ([GateScreen.Nod]) for the same reason.
 */
class InstagramSession(private val gapMs: Long) {
    private var lastSeenAt = -1L

    /** Whether the gate may still ask for tasks in this session. */
    var mayAskForTasks = true
        private set

    /** Whether a crop not due yet may still get its light pause in this session. */
    var mayNod = true
        private set

    /** Instagram is on screen at [now] (it came back, or it scrolled); after a long enough gap, a new session starts. */
    fun seen(now: Long) {
        if (lastSeenAt < 0L || now - lastSeenAt > gapMs) {
            mayAskForTasks = true
            mayNod = true
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

    fun nodded() {
        mayNod = false
    }
}
