package com.example.projdiic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes

/** The farm's sounds (made by art/sounds.py): a pop as a crop is harvested, water pouring, seeds going in. */
enum class FarmSound(@param:RawRes val res: Int) {
    POP(R.raw.sound_pop),
    POUR(R.raw.sound_pour),
    SEEDS(R.raw.sound_seeds),
}

/** Whether a farm sound plays: the facilitator's switch is on and the phone isn't on silent or vibrate. */
fun soundAudible(enabled: Boolean, ringerMode: Int): Boolean = enabled && ringerMode == AudioManager.RINGER_MODE_NORMAL

/** Plays [FarmSound]s quietly, as the phone's own touch sounds are, never over the phone's silent mode. */
object FarmSounds {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<FarmSound, Int>()

    /** Loads the sounds, which takes a moment: call it as the farm opens, so the first one isn't lost. */
    fun warmUp(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool -> FarmSound.entries.forEach { ids[it] = pool.load(app, it.res, 1) } }
    }

    fun play(context: Context, sound: FarmSound) {
        val ringer = context.getSystemService(AudioManager::class.java)?.ringerMode ?: return
        if (!soundAudible(GateSettings(context).soundsEnabled, ringer)) return
        warmUp(context)
        val id = ids[sound] ?: return
        pool?.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private const val VOLUME = 0.6f
}
