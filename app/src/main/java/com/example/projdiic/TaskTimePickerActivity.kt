package com.example.projdiic

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import java.util.Calendar

class TaskTimePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_time_picker)

        val titleEt = findViewById<EditText>(R.id.etEventTitle)
        val hourEt = findViewById<EditText>(R.id.etStartHour)
        val minuteEt = findViewById<EditText>(R.id.etStartMinute)
        val durationEt = findViewById<EditText>(R.id.etDurationMinutes)
        val createBtn = findViewById<Button>(R.id.btnCreateCalendarEvent)

        val tasks = TaskCache.getTasks(this)
        if (tasks.isNotEmpty()) {
            titleEt.setText(tasks[0])
        }

        val now = Calendar.getInstance()
        hourEt.setText(now.get(Calendar.HOUR_OF_DAY).toString())
        minuteEt.setText(now.get(Calendar.MINUTE).toString())

        createBtn.setOnClickListener {
            val title = titleEt.text.toString().ifBlank { "Study Session" }
            val startHour = hourEt.text.toString().toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY)
            val startMinute = minuteEt.text.toString().toIntOrNull() ?: 0
            val durationMins = durationEt.text.toString().toIntOrNull() ?: 30

            val startTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endTime = startTime + (durationMins * 60 * 1000L)

            if (tasks.isNotEmpty()) {
                val currentTasks = tasks.toMutableList()
                currentTasks.removeAt(0)
                TaskCache.saveTasks(this, currentTasks)
            }

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                putExtra(CalendarContract.Events.DESCRIPTION, "Scheduled via Instagram Reels Blocker")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        }
    }
}
