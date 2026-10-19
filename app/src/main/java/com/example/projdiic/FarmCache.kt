package com.example.projdiic

import android.content.Context

/** The field's state between screens: when it was last watered, and a rain still to be shown. */
object FarmCache {
    private const val PREF_NAME = "farm_prefs"
    private const val KEY_WATERED_ON = "watered_on"
    private const val KEY_RAIN_TO_SHOW = "rain_to_show"

    /** Midnight of the day the field was last watered ([FarmRules.wateredOn]), 0 if never. */
    fun wateredOn(context: Context): Long = prefs(context).getLong(KEY_WATERED_ON, 0L)

    fun isWatered(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        FarmRules.isWatered(wateredOn(context), now)

    /** The player watered the field with the can. */
    fun water(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_WATERED_ON, FarmRules.wateredOn(now)).putBoolean(KEY_RAIN_TO_SHOW, false).apply()
    }

    /** It rained on the field; [show] when the rain still has to play the next time the field is on screen. */
    fun rain(context: Context, now: Long = System.currentTimeMillis(), show: Boolean = true) {
        prefs(context).edit().putLong(KEY_WATERED_ON, FarmRules.wateredOn(now)).putBoolean(KEY_RAIN_TO_SHOW, show).apply()
    }

    /** Whether a rain is waiting to be shown; reading it marks it shown. */
    fun takeRainToShow(context: Context): Boolean {
        val waiting = prefs(context).getBoolean(KEY_RAIN_TO_SHOW, false)
        if (waiting) prefs(context).edit().putBoolean(KEY_RAIN_TO_SHOW, false).apply()
        return waiting
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
