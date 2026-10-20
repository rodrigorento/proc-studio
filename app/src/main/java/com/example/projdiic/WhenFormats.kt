package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Times, weekdays and dates in the phone's own format, for [WhenText]. */
fun whenFormats(context: Context): WhenText.Formats {
    val locale = Locale.getDefault()
    val time = DateFormat.getTimeFormat(context)
    val weekday = SimpleDateFormat("EEEE", locale)
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    val dateAndYear = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale)
    fun year(time: Long) = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
    return WhenText.Formats(
        time = { time.format(Date(it)) },
        weekday = { weekday.format(Date(it)) },
        date = { (if (year(it) == year(System.currentTimeMillis())) date else dateAndYear).format(Date(it)) },
        // Android leaves out what both share ("Nov 2 – 8") and adds the year when it isn't this one. A span ending at
        // midnight would stop the day before, so it ends at the end of its last day.
        range = { first, last ->
            DateUtils.formatDateRange(
                context, first, DayMath.endOfDay(last, TimeZone.getDefault()),
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
            )
        },
    )
}
