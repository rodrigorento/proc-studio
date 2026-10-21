package com.example.projdiic

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmSoundsTest {

    @Test
    fun soundsPlayOnlyWithTheRingerOnAndTheSwitchOn() {
        assertTrue(soundAudible(enabled = true, ringerMode = AudioManager.RINGER_MODE_NORMAL))
        assertFalse(soundAudible(enabled = true, ringerMode = AudioManager.RINGER_MODE_VIBRATE))
        assertFalse(soundAudible(enabled = true, ringerMode = AudioManager.RINGER_MODE_SILENT))
        assertFalse(soundAudible(enabled = false, ringerMode = AudioManager.RINGER_MODE_NORMAL))
    }
}
