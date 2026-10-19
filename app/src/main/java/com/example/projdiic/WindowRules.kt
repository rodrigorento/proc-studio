package com.example.projdiic

import android.view.accessibility.AccessibilityWindowInfo

/** Whether a window that changed means Instagram left the screen, or something just opened on top of it. */
object WindowRules {

    /** Windows that open on top of Instagram without leaving it. */
    private val PASS_THROUGH = setOf("com.android.systemui", "android", "com.android.intentresolver")

    /** The window every keyboard is drawn in, whichever app provides the keyboard. */
    private const val KEYBOARD_WINDOW = "android.inputmethodservice.SoftInputWindow"

    /**
     * The gate's own windows, system UI and the keyboard appear without Instagram leaving the screen, and so does
     * anything that isn't an app screen ([windowType] other than an application window), such as pop-ups and other
     * apps' overlays. Some phones report the keyboard under a package other than the default keyboard's, so its
     * window is recognised by its class too. [windowType] is null when the phone doesn't say which window it was.
     */
    fun opensOverInstagram(
        pkg: String,
        className: String?,
        windowType: Int?,
        ownPackage: String,
        ownMainScreen: String,
        keyboardPackage: String?,
    ): Boolean =
        (pkg == ownPackage && className != ownMainScreen) ||
            pkg in PASS_THROUGH ||
            pkg == keyboardPackage ||
            className == KEYBOARD_WINDOW ||
            (windowType != null && windowType != AccessibilityWindowInfo.TYPE_APPLICATION)
}
