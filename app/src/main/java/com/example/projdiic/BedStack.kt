package com.example.projdiic

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes

/**
 * A rendered bed on an overlay screen ([R.layout.view_bed]): shows a bed, and plays the watering, rain, planting
 * and harvest frames over it. Each animation calls its `onEnd` once, unless the screen goes away first.
 */
class BedStack(root: View) {
    private val base: ImageView = root.findViewById(R.id.bedBase)
    private val top: ImageView = root.findViewById(R.id.bedTop)
    private val effect: ImageView = root.findViewById(R.id.bedEffect)

    /** The frames playing over the bed, stopped before others start so two never play at once. */
    private var playing: Runnable? = null

    fun show(@DrawableRes bed: Int) {
        base.setImageResource(bed)
        top.animate().cancel()
        top.alpha = 0f
    }

    /** The can or the rain ([frames]) over the bed while it turns from [dry] to [wet]. */
    fun water(@DrawableRes dry: Int, @DrawableRes wet: Int, frames: List<Int>, onEnd: () -> Unit) =
        turn(dry, wet, frames, CropMotion.WET_FROM, CropMotion.WET_TO, onEnd)

    /** A seed packet pouring ([frames]) into the [empty] bed, which turns into the crop's [seeded] one as they land. */
    fun plant(@DrawableRes empty: Int, @DrawableRes seeded: Int, frames: List<Int>, onEnd: () -> Unit) =
        turn(empty, seeded, frames, CropMotion.SEED_FROM, CropMotion.SEED_TO, onEnd)

    /** Plays [frames] over [from], fading it into [to] between frames [fadeFrom] and [fadeTo]. */
    private fun turn(@DrawableRes from: Int, @DrawableRes to: Int, frames: List<Int>, fadeFrom: Int, fadeTo: Int, onEnd: () -> Unit) {
        show(from)
        top.setImageResource(to)
        play(frames, onFrame = { frame ->
            top.alpha = ((frame - fadeFrom) / (fadeTo - fadeFrom).toFloat()).coerceIn(0f, 1f)
        }) {
            show(to)
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
        play(frames, onEnd = onEnd) // first, so the empty bed never shows on its own
        show(emptyBed)
    }

    private fun play(frames: List<Int>, onFrame: (Int) -> Unit = {}, onEnd: () -> Unit) {
        playing?.let(effect::removeCallbacks)
        playing = effect.playFrames(frames, onFrame, onEnd)
    }
}

/**
 * Plays [frames] on this view at [CropMotion.FPS], then clears it and calls [onEnd]. The first frame shows at once
 * when the view is on screen. Stops quietly if the view leaves the screen; removing the returned step from the view's
 * callbacks stops it too.
 */
fun ImageView.playFrames(frames: List<Int>, onFrame: (Int) -> Unit = {}, onEnd: () -> Unit = {}): Runnable {
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
    return step
}
