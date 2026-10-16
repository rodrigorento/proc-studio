package com.example.projdiic

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class InstagramReelsAccessibilityService : AccessibilityService() {

    private var reelsCount = 0
    private var windowManager: WindowManager? = null
    private var reminderOverlayView: View? = null
    private var taskInputOverlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastScrollTime = 0L

    companion object {
        var currentReelsCount = 0
            private set
        var isServiceEnabled = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isServiceEnabled = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.instagram.android") return

        if (reminderOverlayView != null || taskInputOverlayView != null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val now = System.currentTimeMillis()
            if (now - lastScrollTime > 800L) {
                lastScrollTime = now
                reelsCount++
                currentReelsCount = reelsCount
                Log.d("ReelsBlocker", "Reel counted: $reelsCount/8")

                if (reelsCount >= 8) {
                    Log.d("ReelsBlocker", "Reached 8 reels! Showing reminder overlay.")
                    showReminderOverlay()
                    reelsCount = 0
                    currentReelsCount = 0
                }
            }
        }
    }

    private fun showTaskInputOverlay() {
        handler.post {
            try {
                if (!PermissionUtils.hasOverlayPermission(this)) {
                    Log.e("ReelsBlocker", "Overlay permission not granted!")
                    return@post
                }

                removeReminderOverlay()

                val inflater = LayoutInflater.from(this)
                taskInputOverlayView = inflater.inflate(R.layout.overlay_task_input, null)

                val etTask1 = taskInputOverlayView?.findViewById<EditText>(R.id.etTask1)
                val etTask2 = taskInputOverlayView?.findViewById<EditText>(R.id.etTask2)
                val etTask3 = taskInputOverlayView?.findViewById<EditText>(R.id.etTask3)
                val saveBtn = taskInputOverlayView?.findViewById<Button>(R.id.btnSaveTasks)

                val existingTasks = TaskCache.getTasks(this)
                if (existingTasks.isNotEmpty()) etTask1?.setText(existingTasks[0])
                if (existingTasks.size > 1) etTask2?.setText(existingTasks[1])
                if (existingTasks.size > 2) etTask3?.setText(existingTasks[2])

                saveBtn?.setOnClickListener {
                    Log.d("ReelsBlocker", "Save tasks clicked in task input overlay")
                    val list = mutableListOf<String>()
                    val t1 = etTask1?.text?.toString()?.trim()
                    val t2 = etTask2?.text?.toString()?.trim()
                    val t3 = etTask3?.text?.toString()?.trim()

                    if (!t1.isNullOrBlank()) list.add(t1)
                    if (!t2.isNullOrBlank()) list.add(t2)
                    if (!t3.isNullOrBlank()) list.add(t3)

                    TaskCache.saveTasks(this, list)
                    removeTaskInputOverlay()
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(taskInputOverlayView, params)
                Log.d("ReelsBlocker", "Task input overlay added successfully.")
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to show task input overlay", e)
            }
        }
    }

    private fun showReminderOverlay() {
        handler.post {
            try {
                if (!PermissionUtils.hasOverlayPermission(this)) {
                    Log.e("ReelsBlocker", "Overlay permission not granted!")
                    return@post
                }


                val inflater = LayoutInflater.from(this)
                reminderOverlayView = inflater.inflate(R.layout.overlay_go_study, null)

                val layoutEmpty = reminderOverlayView?.findViewById<View>(R.id.layoutEmptyState)
                val layoutTasks = reminderOverlayView?.findViewById<View>(R.id.layoutTasksState)
                val tvFirstTask = reminderOverlayView?.findViewById<TextView>(R.id.tvFirstTask)

                val tasks = TaskCache.getTasks(this)
                if (tasks.isEmpty()) {
                    layoutEmpty?.visibility = View.VISIBLE
                    layoutTasks?.visibility = View.GONE

                    val typeItBtn = reminderOverlayView?.findViewById<Button>(R.id.btnTypeIt)
                    typeItBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Type it clicked")
                        showTaskInputOverlay()
                        removeReminderOverlay()
                    }

                    val dismissBtn = reminderOverlayView?.findViewById<Button>(R.id.btnDismiss)
                    dismissBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Nothing for now clicked")
                        removeReminderOverlay()
                    }
                } else {
                    layoutEmpty?.visibility = View.GONE
                    layoutTasks?.visibility = View.VISIBLE
                    tvFirstTask?.text = tasks[0]

                    val doItNowBtn = reminderOverlayView?.findViewById<Button>(R.id.btnDoItNow)
                    doItNowBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Do it now clicked")
                        removeReminderOverlay()

                        val currentTasks = TaskCache.getTasks(this).toMutableList()
                        if (currentTasks.isNotEmpty()) {
                            currentTasks.removeAt(0)
                            TaskCache.saveTasks(this, currentTasks)
                        }

                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }

                    val pickTimeBtn = reminderOverlayView?.findViewById<Button>(R.id.btnPickTime)
                    pickTimeBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Pick a time clicked")
                        removeReminderOverlay()

                        val currentTasks = TaskCache.getTasks(this)
                        val title = if (currentTasks.isNotEmpty()) currentTasks[0] else "Study Session"

                        if (currentTasks.isNotEmpty()) {
                            val mutableTasks = currentTasks.toMutableList()
                            mutableTasks.removeAt(0)
                            TaskCache.saveTasks(this, mutableTasks)
                        }

                        val startTime = System.currentTimeMillis() + (5 * 60 * 1000L)
                        val endTime = startTime + (30 * 60 * 1000L)

                        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, title)
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                            putExtra(CalendarContract.Events.DESCRIPTION, "Scheduled via Instagram Reels Blocker")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            startActivity(calendarIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(reminderOverlayView, params)
                Log.d("ReelsBlocker", "Reminder overlay added successfully.")
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to show reminder overlay", e)
            }
        }
    }

    private fun removeReminderOverlay() {
        handler.post {
            try {
                if (reminderOverlayView != null) {
                    windowManager?.removeView(reminderOverlayView)
                    reminderOverlayView = null
                    Log.d("ReelsBlocker", "Reminder overlay removed.")
                }
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to remove reminder overlay", e)
            }
        }
    }

    private fun removeTaskInputOverlay() {
        handler.post {
            try {
                if (taskInputOverlayView != null) {
                    windowManager?.removeView(taskInputOverlayView)
                    taskInputOverlayView = null
                    Log.d("ReelsBlocker", "Task input overlay removed.")
                }
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to remove task input overlay", e)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isServiceEnabled = false
        removeReminderOverlay()
        removeTaskInputOverlay()
        super.onDestroy()
    }
}
