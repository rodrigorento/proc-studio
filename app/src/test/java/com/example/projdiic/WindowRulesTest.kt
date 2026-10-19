package com.example.projdiic

import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowRulesTest {

    private val own = "com.example.projdiic"
    private val mainScreen = MainActivity::class.java.name

    private fun opensOver(
        pkg: String,
        className: String? = null,
        windowType: Int? = AccessibilityWindowInfo.TYPE_APPLICATION,
        keyboard: String? = "com.google.android.inputmethod.latin",
    ) = WindowRules.opensOverInstagram(pkg, className, windowType, own, mainScreen, keyboard)

    @Test
    fun theGatesOwnScreensOpenOverInstagram() {
        assertTrue(opensOver(own, "android.widget.FrameLayout", AccessibilityWindowInfo.TYPE_SYSTEM))
    }

    @Test
    fun theAppsMainScreenMeansInstagramLeft() {
        assertFalse(opensOver(own, mainScreen))
    }

    @Test
    fun theDefaultKeyboardOpensOverInstagram() {
        assertTrue(opensOver("com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow"))
    }

    @Test
    fun aKeyboardFromAnotherPackageStillOpensOverInstagram() {
        // Some phones report the keyboard under a package other than the default input method's.
        assertTrue(opensOver("com.samsung.android.honeyboard", "android.inputmethodservice.SoftInputWindow", null))
    }

    @Test
    fun aKeyboardIsRecognisedWhenTheDefaultCannotBeRead() {
        assertTrue(opensOver("com.touchtype.swiftkey", "android.inputmethodservice.SoftInputWindow", null, keyboard = null))
    }

    @Test
    fun windowsThatAreNotAppScreensOpenOverInstagram() {
        for (type in listOf(
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            AccessibilityWindowInfo.TYPE_SYSTEM,
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
        )) {
            assertTrue("type $type", opensOver("com.google.android.gms", "android.widget.FrameLayout", type))
        }
    }

    @Test
    fun systemUiOpensOverInstagram() {
        assertTrue(opensOver("com.android.systemui", "android.widget.FrameLayout"))
    }

    @Test
    fun anotherAppsScreenMeansInstagramLeft() {
        assertFalse(opensOver("com.whatsapp", "com.whatsapp.HomeActivity"))
    }

    @Test
    fun anotherAppIsAssumedToReplaceInstagramWhenTheWindowIsUnknown() {
        assertFalse(opensOver("com.whatsapp", "com.whatsapp.HomeActivity", windowType = null))
    }
}
