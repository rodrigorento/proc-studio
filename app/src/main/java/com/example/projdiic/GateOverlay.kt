package com.example.projdiic

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.projdiic.ui.theme.ProjDIICTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Draws Task Farm's pause screens over Instagram, one at a time, and reports
 * every tap to [actions]. The service decides which screen to show and when.
 */
class GateOverlay(service: Context, private val actions: Actions) {

    interface Actions {
        /** Harvest it, tapped at [tappedAt] on the gate or the check-in ([where]), once its harvest has played. */
        fun onHarvested(task: Task, where: String, tappedAt: Long)

        fun onSnooze(task: Task)
        fun onCheckInAccepted(task: Task)
        fun onCheckInOff(task: Task)

        /** Plants a task for [window] (null: Anytime). Returns it, or null when it could not (blank, or the field is full). */
        fun onTaskPlanted(title: String, window: TimeWindow?, via: String): Task?
        fun onTaskRemoved(task: Task)

        /** A crop harvested on the farm over Instagram, once its harvest has played. */
        fun onFieldHarvested(task: Task, ripe: Boolean)

        /** A crop watered on the farm over Instagram, by tapping its bed or Water it in its sheet ([via]). */
        fun onFieldWatered(task: Task, via: String)

        /** Undo on the farm over Instagram: [task], just harvested or removed ([after]), back at [index]; false if it couldn't be. */
        fun onTaskRestored(task: Task, index: Int, after: String): Boolean

        /** The farm over Instagram told that [task]'s crop has grown to [stage] since it was last seen. */
        fun onGrowthNews(task: Task, stage: String)
        fun onAddTasksClosed()

        /** "Close the gate until tomorrow" on the ask-for-tasks screen: no gates until midnight. */
        fun onSnoozeAppUntilTomorrow()
    }

    /** The line above the task. The service picks a new one for every gate. */
    var eyebrow: CharSequence = ""

    private val context: Context = ContextThemeWrapper(service, R.style.Theme_ProjDIIC)
    private val inflater = LayoutInflater.from(context)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private val settings = GateSettings(service)
    private val formats = whenFormats(context)

    private var view: View? = null
    private var shown: GateScreen? = null

    /** Harvest it was tapped and its animation is playing; it takes effect when that ends or the screen goes. */
    private var pending: (() -> Unit)? = null

    /** Set when a pause opens (not when it's shown again): the next farm built slides in. */
    var slideIn = false

    /** The farm's Compose host while the farm is up over Instagram (the ask for tasks). */
    private var farmOwner: OverlayOwner? = null
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** A harvest is playing, so walking away isn't passing on its task: it happens either way. */
    val busy: Boolean
        get() = pending != null

    /** Whether Android let us pause Instagram's sound for the screen that is up. */
    var holdsAudioFocus = false
        private set

    /** Returns false when the screen could not be drawn, for example without the overlay permission. */
    fun show(screen: GateScreen, task: Task?): Boolean {
        if (screen == shown && view != null) return true
        if (!PermissionUtils.hasOverlayPermission(context)) {
            Log.e(TAG, "Overlay permission not granted!")
            return false
        }
        val next = when (screen) {
            is GateScreen.Gate -> task?.let(::buildGate)
            is GateScreen.CheckIn -> task?.let(::buildCheckIn)
            GateScreen.AddTasks -> buildFarm()
        } ?: return false
        // Compose looks for its lifecycle on the window's root.
        val owner = if (screen == GateScreen.AddTasks) OverlayOwner() else null
        val host = OverlayRoot(context, backActionFor(screen, task)).apply {
            addView(next, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            owner?.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
                setViewTreeOnBackPressedDispatcherOwner(it)
            }
        }

        removeView()
        farmOwner = owner
        return try {
            windowManager.addView(host, layoutParams(screen))
            view = host
            shown = screen
            holdAudioFocus()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show the ${screen.logName} overlay", e)
            owner?.destroy()
            farmOwner = null
            shown = null
            releaseAudioFocus()
            false
        }
    }

    fun hide() {
        finishPending()
        removeView()
        shown = null
        releaseAudioFocus()
    }

    private fun removeView() {
        farmOwner?.destroy()
        farmOwner = null
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove the overlay", e)
            }
        }
        view = null
    }

    /** The harvest or the watering has played, or the screen is going before it could: it happens either way. */
    private fun finishPending() {
        val choice = pending ?: return
        pending = null
        choice()
    }

    /**
     * The system Back gesture: on the farm, a step back where it has one (planting, a crop's sheet), otherwise back to
     * the feed; the gate itself only closes through its choices.
     */
    private fun backActionFor(screen: GateScreen, task: Task?): (() -> Unit)? = when {
        screen == GateScreen.AddTasks -> { ->
            val back = farmOwner?.onBackPressedDispatcher
            if (back != null && back.hasEnabledCallbacks()) back.onBackPressed() else actions.onAddTasksClosed()
            (view as? OverlayRoot)?.nextBack()
        }
        else -> null
    }

    /** The farm, the same as the app's home screen ([OverlayFarm]), in Compose; its lifecycle is on the window ([show]). */
    private fun buildFarm(): View {
        val farm = ComposeView(context).apply {
            setContent {
                ProjDIICTheme {
                    // The app's Scaffold gives text its colour; here a Surface does.
                    Surface(color = MaterialTheme.colorScheme.background) { OverlayFarm(actions) }
                }
            }
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.sg_bg))
            addView(farm, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        fitSystemBars(root, bottom = root)
        // A farm just opened slides up over the feed rather than cutting to it; one shown again (back from another app)
        // is simply there. It starts below the window, set just before its first frame.
        if (slideIn && motionOn(context)) {
            root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    root.translationY = root.height.toFloat()
                    root.animate().translationY(0f).setDuration(FARM_SLIDE_MS).setInterpolator(DecelerateInterpolator(2f)).start()
                    return true
                }
            })
        }
        slideIn = false
        return root
    }

    private fun buildGate(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_gate, null)
        val now = System.currentTimeMillis()
        val bed = BedStack(root.findViewById(R.id.bed)).apply { show(FarmViews.bed(task, now)) }
        root.findViewById<TextView>(R.id.tvEyebrow).text = eyebrow
        root.findViewById<TextView>(R.id.tvTaskTitle).text = task.title
        root.findViewById<TextView>(R.id.tvTaskMeta).text = metaFor(task)
        // A due crop: Harvest it, or 5 more minutes once.
        val where = GateScreen.Gate(task.id).logName
        val harvest = root.findViewById<Button>(R.id.btnHarvest).apply { withIcon(R.drawable.prop_basket) }
        val snooze = root.findViewById<Button>(R.id.btnSnooze).apply {
            isVisible = !task.snoozeUsed
            setOnClickListener { actions.onSnooze(task) }
        }
        harvest.setOnClickListener { harvestOn(bed, task, where, listOf(harvest, snooze)) }
        root.findViewById<TextView>(R.id.tvSnoozeUsed).isVisible = task.snoozeUsed
        // With the 5 minutes had, Harvest it is the only choice left.
        root.findViewById<TextView>(R.id.tvGateHint).isVisible = !task.snoozeUsed
        root.findViewById<View>(R.id.bed).contentDescription = FarmViews.describe(task, now)
        fitSystemBars(root, bottom = root.findViewById(R.id.sheet))
        return root
    }

    /**
     * Harvest it: the crop ripens first if it isn't ready, flies out of [bed], and then the task is done. The other
     * [choices] are off while it plays.
     */
    private fun harvestOn(bed: BedStack, task: Task, where: String, choices: List<View>) {
        if (pending != null) return
        choices.forEach { it.isEnabled = false }
        val tappedAt = System.currentTimeMillis()
        val crop = FarmRules.crop(task)
        val stage = CropMotion.harvestedAs(crop, FarmRules.stage(task, tappedAt))
        pending = { actions.onHarvested(task, where, tappedAt) }
        val wet = FarmRules.isWatered(task, tappedAt)
        bed.fadeTo(CropArt.bed(crop, stage, wet), FarmViews.RIPEN_MS) {
            FarmSounds.play(context, FarmSound.POP)
            bed.harvest(CropArt.emptyBed(wet), CropMotion.harvest(crop, stage)) { finishPending() }
        }
    }

    /** Shows [icon] just before the button's text, the two centred together: a platform [Button] has no icon of its own. */
    private fun Button.withIcon(@DrawableRes icon: Int) {
        val size = dp(30)
        val picture = ContextCompat.getDrawable(context, icon)?.mutate()?.apply { setBounds(0, 0, size, size) } ?: return
        setCompoundDrawablesRelative(picture, null, null, null)
        compoundDrawablePadding = dp(10)
        // The picture sits at the button's start edge: padding both sides alike brings it in beside the centred text.
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val side = ((width - size - compoundDrawablePadding - paint.measureText(text.toString())) / 2).toInt().coerceAtLeast(dp(16))
            if (paddingStart != side || paddingEnd != side) setPaddingRelative(side, paddingTop, side, paddingBottom)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun buildCheckIn(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_check_in, null)
        val person = settings.trustedPerson.ifBlank { context.getString(R.string.trusted_person_fallback) }
        root.findViewById<TextView>(R.id.tvCheckInBody).text =
            context.getString(R.string.check_in_body, task.title, person)
        root.findViewById<TextView>(R.id.tvCheckInWho).text = context.getString(R.string.check_in_who, person)
        root.findViewById<TextView>(R.id.tvCheckInMessage).text =
            context.getString(R.string.check_in_message, task.title)
        // As on the gate: Harvest it. A check-in only comes for a due crop (TaskRules.needsCheckIn).
        val where = GateScreen.CheckIn(task.id).logName
        val harvest = root.findViewById<Button>(R.id.btnCheckInHarvest).apply { withIcon(R.drawable.prop_basket) }
        val ok = root.findViewById<Button>(R.id.btnCheckInOk).apply {
            text = context.getString(R.string.check_in_ok, person)
            setOnClickListener { actions.onCheckInAccepted(task) }
        }
        val off = root.findViewById<Button>(R.id.btnCheckInOff).apply { setOnClickListener { actions.onCheckInOff(task) } }
        val choices = listOf(harvest, ok, off)
        // The harvest plays where the mailbox was, scrolled into view, set up on the first tap.
        val bed by lazy(LazyThreadSafetyMode.NONE) {
            val now = System.currentTimeMillis()
            root.findViewById<View>(R.id.ivMailbox).isVisible = false
            val view = root.findViewById<View>(R.id.bed).apply {
                isVisible = true
                contentDescription = FarmViews.describe(task, now)
            }
            (root as? ScrollView)?.smoothScrollTo(0, 0)
            BedStack(view).apply { show(FarmViews.bed(task, now)) }
        }
        harvest.setOnClickListener { harvestOn(bed, task, where, choices) }
        fitSystemBars(root, bottom = root)
        return root
    }

    private fun metaFor(task: Task): String {
        val now = System.currentTimeMillis()
        if (task.deferredUntil in 1..now && task.deferredBy == Task.BY_SNOOZE) return context.getString(R.string.gate_meta_snoozed)
        WhenText.gateLine(TaskRules.window(task), now, TimeZone.getDefault(), formats)?.let { return it }
        val day = when (DayMath.daysBetween(task.createdAt, now, TimeZone.getDefault())) {
            0 -> context.getString(R.string.day_today)
            1 -> context.getString(R.string.day_yesterday)
            in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(task.createdAt))
            else -> DateFormat.getMediumDateFormat(context).format(Date(task.createdAt))
        }
        val template = if (task.addedVia == Task.VIA_VOICE) R.string.gate_meta_said else R.string.gate_meta_added
        return context.getString(template, day, FarmViews.cropAndStage(FarmRules.crop(task), FarmRules.stage(task, now)))
    }

    /**
     * The window covers the whole screen (see [layoutParams]); this keeps the content clear of
     * the status and navigation bars. The add-tasks window is resized above the keyboard instead.
     */
    private fun fitSystemBars(root: View, bottom: View) {
        val baseTop = root.paddingTop
        val baseBottom = bottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(types)
            root.updatePadding(top = baseTop + bars.top)
            bottom.updatePadding(bottom = baseBottom + bars.bottom)
            insets
        }
    }

    private fun layoutParams(screen: GateScreen): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // Over the farm the keyboard comes up for planting's text field, and the screen makes room for it.
            softInputMode = if (screen == GateScreen.AddTasks) {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // From Android 11 the window would otherwise stop above the navigation bar, leaving a
            // strip of the live feed under the sheet; SOFT_INPUT_ADJUST_RESIZE also stops working.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = if (screen == GateScreen.AddTasks) WindowInsets.Type.ime() else 0
            }
            // Blur the frozen feed behind the sheet where the phone supports it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && screen.overFeed &&
                windowManager.isCrossWindowBlurEnabled
            ) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = BLUR_RADIUS
            }
        }
    }

    // Asking for audio focus makes Instagram pause its sound while a screen is up. Best effort.
    // Android 15+ may refuse it for a background service; gate_shown logs sound_paused to show what happened.
    private fun holdAudioFocus() {
        if (holdsAudioFocus) return
        holdsAudioFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releaseAudioFocus() {
        if (!holdsAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        holdsAudioFocus = false
    }

    companion object {
        private const val TAG = "ReelsBlocker"
        private const val BLUR_RADIUS = 40
        private const val FARM_SLIDE_MS = 320L
    }
}
