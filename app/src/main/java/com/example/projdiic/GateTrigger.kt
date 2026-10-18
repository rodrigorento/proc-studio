package com.example.projdiic

/** How much scrolling it takes before the gate appears. */
data class TriggerConfig(
    val swipes: Int,
    val minutes: Int,
    val cooldownMinutes: Int,
    val debounceMs: Long = 800L,
    /** A break longer than this ends the stretch of scrolling. */
    val stretchGapMs: Long = 3 * 60_000L,
)

enum class TriggerReason(val logName: String) {
    SWIPES("swipes"),
    MINUTES("minutes"),
    FACILITATOR("facilitator"),
}

/**
 * Counts Instagram scroll events and says when the gate should open: after
 * [TriggerConfig.swipes] swipes or [TriggerConfig.minutes] minutes of continuous
 * scrolling, whichever comes first, and never during the cooldown after a gate.
 */
class GateTrigger {

    data class Result(val counted: Boolean, val swipes: Int = 0, val reason: TriggerReason? = null)

    var swipes = 0
        private set
    private var lastSwipeAt = 0L
    private var stretchStartedAt = 0L
    private var cooldownUntil = 0L

    fun onScroll(now: Long, config: TriggerConfig): Result {
        if (lastSwipeAt != 0L && now - lastSwipeAt <= config.debounceMs) return Result(counted = false, swipes = swipes)

        val newStretch = lastSwipeAt == 0L || now - lastSwipeAt > config.stretchGapMs
        lastSwipeAt = now
        if (newStretch || now < cooldownUntil) {
            swipes = 0
            stretchStartedAt = now
        }
        if (now < cooldownUntil) return Result(counted = true)

        swipes++
        val reason = when {
            swipes >= config.swipes -> TriggerReason.SWIPES
            now - stretchStartedAt >= config.minutes * 60_000L -> TriggerReason.MINUTES
            else -> null
        }
        val result = Result(counted = true, swipes = swipes, reason = reason)
        if (reason != null) restart(now)
        return result
    }

    fun startCooldown(now: Long, config: TriggerConfig) {
        cooldownUntil = now + config.cooldownMinutes * 60_000L
        restart(now)
    }

    private fun restart(now: Long) {
        swipes = 0
        stretchStartedAt = now
    }
}
