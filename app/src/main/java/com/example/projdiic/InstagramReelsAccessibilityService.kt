package com.example.projdiic

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
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

                // Update title based on TaskCache
                val titleTv = overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)
                val tasks = TaskCache.getTasks(this)
                if (tasks.isEmpty()) {
                    titleTv?.text = "the list is empty"
                } else {
                    titleTv?.text = "You have ${tasks.size} task(s) on your list:\n" + tasks.joinToString("\n") { "• $it" }
                }

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
                Log.d("ReelsBlocker", "Overlay added successfully (stays until dismissed).")
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
                    Log.d("ReelsBlocker", "Overlay removed.")
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
