package com.example.projdiic

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes

/**
 * A rendered bed on an overlay screen ([R.layout.view_bed]): shows a bed, and plays the watering, rain and
 * harvest frames over it. Each animation calls its `onEnd` once, unless the screen goes away first.
 */
class BedStack(root: View) {
    private val base: ImageView = root.findViewById(R.id.bedBase)
    private val top: ImageView = root.findViewById(R.id.bedTop)
    private val effect: ImageView = root.findViewById(R.id.bedEffect)

    fun show(@DrawableRes bed: Int) {
        base.setImageResource(bed)
        top.animate().cancel()
        top.alpha = 0f
    }

    /** The can or the rain ([frames]) over the bed while it turns from [dry] to [wet]. */
    fun water(@DrawableRes dry: Int, @DrawableRes wet: Int, frames: List<Int>, onEnd: () -> Unit) {
        show(dry)
        top.setImageResource(wet)
        effect.playFrames(frames, onFrame = { frame ->
            val span = (CropMotion.WET_TO - CropMotion.WET_FROM).toFloat()
            top.alpha = ((frame - CropMotion.WET_FROM) / span).coerceIn(0f, 1f)
        }) {
            show(wet)
            onEnd()
        }
    }

    /** Fades the bed into [next] (a crop ripening before its harvest). */
    fun fadeTo(@DrawableRes next: Int, millis: Long, onEnd: () -> Unit) {
        top.setImageResource(next)
        top.alpha = 0f
        top.animate().alpha(1f).setDuration(millis).withEndAction {
            show(next)
            onEnd()
        }.start()
    }

    /** The crop flies out ([frames]) and leaves [emptyBed]. */
    fun harvest(@DrawableRes emptyBed: Int, frames: List<Int>, onEnd: () -> Unit) {
        effect.playFrames(frames, onEnd = onEnd) // first, so the empty bed never shows on its own
        show(emptyBed)
    }
}

/**
 * Plays [frames] on this view at [CropMotion.FPS], then clears it and calls [onEnd]. The first frame shows at once
 * when the view is on screen. Stops quietly if the view leaves the screen.
 */
fun ImageView.playFrames(frames: List<Int>, onFrame: (Int) -> Unit = {}, onEnd: () -> Unit = {}) {
    var index = 0
    val step = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            if (index >= frames.size) {
                setImageDrawable(null)
                onEnd()
                return
            }
            setImageResource(frames[index])
            onFrame(index)
            index++
            postDelayed(this, 1000L / CropMotion.FPS)
        }
    }
    if (isAttachedToWindow) step.run() else post(step)
}
