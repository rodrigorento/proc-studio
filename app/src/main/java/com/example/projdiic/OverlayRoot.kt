package com.example.projdiic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * The root of every overlay window. It turns the system Back gesture into the
 * screen's own Back action; with [onBack] null (the gate), Back does nothing.
 */
@SuppressLint("ViewConstructor")
class OverlayRoot(context: Context, private val onBack: (() -> Unit)?) : FrameLayout(context) {

    // Android 16 can deliver one Back both as a callback and as a key; act on it once per screen.
    private var backHandled = false

    // From Android 13 the gesture arrives as a callback; apps targeting Android 16 no longer get the key.
    private val backCallback: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onBack != null) {
            OnBackInvokedCallback { handleBack() }
        } else {
            null
        }

    private fun handleBack() {
        if (backHandled) return
        backHandled = true
        onBack?.invoke()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback is OnBackInvokedCallback) {
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback
            )
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback is OnBackInvokedCallback) {
            findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback)
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
