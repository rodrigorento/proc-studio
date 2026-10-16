package com.example.projdiic

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

class InstagramReelsAccessibilityService : AccessibilityService() {

    private var reelsCount = 0
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
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

        if (overlayView != null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val now = System.currentTimeMillis()
            if (now - lastScrollTime > 800L) {
                lastScrollTime = now
                reelsCount++
                currentReelsCount = reelsCount
                Log.d("ReelsBlocker", "Reel counted: $reelsCount/8")

                if (reelsCount >= 8) {
                    Log.d("ReelsBlocker", "Reached 8 reels! Showing overlay.")
                    showGoStudyOverlay()
                    reelsCount = 0
                    currentReelsCount = 0
                }
            }
        }
    }

    private fun showGoStudyOverlay() {
        handler.post {
            try {
                if (!PermissionUtils.hasOverlayPermission(this)) {
                    Log.e("ReelsBlocker", "Overlay permission not granted!")
                    return@post
                }
                val inflater = LayoutInflater.from(this)
                overlayView = inflater.inflate(R.layout.overlay_go_study, null)

                val layoutEmpty = overlayView?.findViewById<View>(R.id.layoutEmptyState)
                val layoutTasks = overlayView?.findViewById<View>(R.id.layoutTasksState)
                val tvFirstTask = overlayView?.findViewById<TextView>(R.id.tvFirstTask)

                val tasks = TaskCache.getTasks(this)
                if (tasks.isEmpty()) {
                    layoutEmpty?.visibility = View.VISIBLE
                    layoutTasks?.visibility = View.GONE

                    val typeItBtn = overlayView?.findViewById<Button>(R.id.btnTypeIt)
                    typeItBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Type it clicked")
                        removeOverlay()
                        val intent = Intent(this, TaskInputActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    }

                    val dismissBtn = overlayView?.findViewById<Button>(R.id.btnDismiss)
                    dismissBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Nothing for now clicked")
                        removeOverlay()
                    }
                } else {
                    layoutEmpty?.visibility = View.GONE
                    layoutTasks?.visibility = View.VISIBLE
                    tvFirstTask?.text = tasks[0]

                    val doItNowBtn = overlayView?.findViewById<Button>(R.id.btnDoItNow)
                    doItNowBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Do it now clicked")
                        removeOverlay()

                        // 1. Remove the first task from cache
                        val currentTasks = TaskCache.getTasks(this).toMutableList()
                        if (currentTasks.isNotEmpty()) {
                            currentTasks.removeAt(0)
                            TaskCache.saveTasks(this, currentTasks)
                        }

                        // 2. Open Instagram App Info settings where user can tap "Force Stop" with 1 click
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:com.instagram.android")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }

                    val pickTimeBtn = overlayView?.findViewById<Button>(R.id.btnPickTime)
                    pickTimeBtn?.setOnClickListener {
                        Log.d("ReelsBlocker", "Pick a time clicked")
                        removeOverlay()
                        val intent = Intent(this, TaskTimePickerActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
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

                windowManager?.addView(overlayView, params)
                Log.d("ReelsBlocker", "Overlay added successfully.")
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to show overlay", e)
            }
        }
    }

    private fun removeOverlay() {
        handler.post {
            try {
                if (overlayView != null) {
                    windowManager?.removeView(overlayView)
                    overlayView = null
                    Log.d("ReelsBlocker", "Review overlay removed.")
                }
            } catch (e: Exception) {
                Log.e("ReelsBlocker", "Failed to remove overlay", e)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isServiceEnabled = false
        removeOverlay()
        super.onDestroy()
    }
}
