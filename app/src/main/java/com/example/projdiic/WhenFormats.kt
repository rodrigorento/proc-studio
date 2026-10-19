package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Times, weekdays and dates in the phone's own format, for [WhenText]. */
fun whenFormats(context: Context): WhenText.Formats {
    val locale = Locale.getDefault()
    val time = DateFormat.getTimeFormat(context)
    val weekday = SimpleDateFormat("EEEE", locale)
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    return WhenText.Formats(
        time = { time.format(Date(it)) },
        weekday = { weekday.format(Date(it)) },
        date = { date.format(Date(it)) },
    )
}
