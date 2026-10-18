package com.example.projdiic

import android.content.Context

/** What the facilitator can tune before and during a session. Stored on the phone. */
class GateSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var swipeThreshold: Int
        get() = prefs.getInt(KEY_SWIPES, DEFAULT_SWIPES)
        set(value) = prefs.edit().putInt(KEY_SWIPES, value.coerceIn(SWIPE_RANGE)).apply()

    var minutesThreshold: Int
        get() = prefs.getInt(KEY_MINUTES, DEFAULT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_MINUTES, value.coerceIn(MINUTE_RANGE)).apply()

    var cooldownMinutes: Int
        get() = prefs.getInt(KEY_COOLDOWN, DEFAULT_COOLDOWN)
        set(value) = prefs.edit().putInt(KEY_COOLDOWN, value.coerceIn(COOLDOWN_RANGE)).apply()

    /** Off until the participant agrees: nobody is contacted without their say. */
    var checkInEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_IN, value).apply()

    var trustedPerson: String
        get() = prefs.getString(KEY_TRUSTED_PERSON, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TRUSTED_PERSON, value.trim()).apply()

    fun triggerConfig() = TriggerConfig(
        swipes = swipeThreshold,
        minutes = minutesThreshold,
        cooldownMinutes = cooldownMinutes,
    )

    /** The gate's wording rotates so it does not go stale; each call moves to the next line. */
    fun nextWordingIndex(): Int {
        val index = prefs.getInt(KEY_WORDING, 0)
        prefs.edit().putInt(KEY_WORDING, index + 1).apply()
        return index
    }

    companion object {
        private const val PREF_NAME = "gate_settings"
        private const val KEY_SWIPES = "swipe_threshold"
        private const val KEY_MINUTES = "minutes_threshold"
        private const val KEY_COOLDOWN = "cooldown_minutes"
        private const val KEY_CHECK_IN = "check_in_enabled"
        private const val KEY_TRUSTED_PERSON = "trusted_person"
        private const val KEY_WORDING = "wording_index"

        const val DEFAULT_SWIPES = 8
        const val DEFAULT_MINUTES = 3
        const val DEFAULT_COOLDOWN = 2

        val SWIPE_RANGE = 1..100
        val MINUTE_RANGE = 1..60
        val COOLDOWN_RANGE = 0..30
    }
}
