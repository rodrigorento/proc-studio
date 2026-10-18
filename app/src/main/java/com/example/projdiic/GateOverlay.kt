package com.example.projdiic

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Draws the Scroll Gate screens over Instagram, one at a time, and reports
 * every tap to [actions]. The service decides which screen to show and when.
 */
class GateOverlay(service: Context, private val actions: Actions) {

    interface Actions {
        fun onDoItNow(task: Task)
        fun onPickTime(task: Task)
        fun onSnooze(task: Task)
        fun onTimeChosen(task: Task, choice: TimeChoice)
        fun onAddToCalendar(task: Task)
        fun onBackToChoices(task: Task, from: GateScreen)
        fun onDone(task: Task, startedAt: Long)
        fun onCloseInstagram(task: Task)
        fun onCheckInAccepted(task: Task)
        fun onCheckInOff(task: Task)

        /** Returns the new task, or null when it could not be added (blank, or the list is full). */
        fun onTaskAdded(title: String, via: String): Task?
        fun onTaskRemoved(task: Task)
        fun onWhenChanged(task: Task, window: TimeWindow?)
        fun onAddTasksClosed()
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
    private var recognizer: SpeechRecognizer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

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
            is GateScreen.PickTime -> task?.let(::buildPickTime)
            is GateScreen.DoingNow -> task?.let { buildDoingNow(it, screen.startedAt) }
            is GateScreen.CheckIn -> task?.let(::buildCheckIn)
            GateScreen.AddTasks -> buildAddTasks()
        } ?: return false
        val host = OverlayRoot(context, backActionFor(screen, task)).apply {
            addView(next, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        removeView()
        return try {
            windowManager.addView(host, layoutParams(screen))
            view = host
            shown = screen
            holdAudioFocus()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show the ${screen.logName} overlay", e)
            shown = null
            releaseAudioFocus()
            false
        }
    }

    fun hide() {
        removeView()
        shown = null
        releaseAudioFocus()
    }

    private fun removeView() {
        stopListening()
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove the overlay", e)
            }
        }
        view = null
    }

    /** The system Back gesture: a step back where the screen has one; the gate itself only closes through its choices. */
    private fun backActionFor(screen: GateScreen, task: Task?): (() -> Unit)? = when {
        screen == GateScreen.AddTasks -> actions::onAddTasksClosed
        task != null && (screen is GateScreen.PickTime || screen is GateScreen.DoingNow) ->
            { -> actions.onBackToChoices(task, screen) }
        else -> null
    }

    private fun buildGate(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_gate, null)
        root.findViewById<TextView>(R.id.tvEyebrow).text = eyebrow
        root.findViewById<TextView>(R.id.tvTaskTitle).text = task.title
        root.findViewById<TextView>(R.id.tvTaskMeta).text = metaFor(task)
        root.findViewById<Button>(R.id.btnDoItNow).setOnClickListener { actions.onDoItNow(task) }
        root.findViewById<Button>(R.id.btnPickTime).setOnClickListener { actions.onPickTime(task) }
        root.findViewById<Button>(R.id.btnSnooze).apply {
            isVisible = !task.snoozeUsed
            setOnClickListener { actions.onSnooze(task) }
        }
        root.findViewById<TextView>(R.id.tvSnoozeUsed).isVisible = task.snoozeUsed
        fitSystemBars(root, bottom = root.findViewById(R.id.sheet))
        return root
    }

    private fun buildPickTime(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_pick_time, null)
        root.findViewById<TextView>(R.id.tvPickTask).text = task.title
        root.findViewById<Button>(R.id.btnPickBack).setOnClickListener {
            actions.onBackToChoices(task, GateScreen.PickTime(task.id))
        }
        val options = root.findViewById<LinearLayout>(R.id.timeOptions)
        TimeChoices.forNow(System.currentTimeMillis()).forEach { choice ->
            val label = context.getString(labelFor(choice.key))
            val time = timeText(choice.at)
            val row = inflater.inflate(R.layout.item_time_option, options, false)
            row.findViewById<TextView>(R.id.tvOptionLabel).text = label
            row.findViewById<TextView>(R.id.tvOptionTime).text = time
            row.contentDescription = "$label, $time"
            row.setOnClickListener { actions.onTimeChosen(task, choice) }
            options.addView(row)
        }
        root.findViewById<Button>(R.id.btnCalendar).setOnClickListener { actions.onAddToCalendar(task) }
        fitSystemBars(root, bottom = root.findViewById(R.id.sheet))
        return root
    }

    private fun buildDoingNow(task: Task, startedAt: Long): View {
        val root = inflater.inflate(R.layout.overlay_doing_now, null)
        root.findViewById<TextView>(R.id.tvDoingTitle).text = task.title
        root.findViewById<TextView>(R.id.tvStarted).text =
            context.getString(R.string.doing_started, timeText(startedAt))
        root.findViewById<Button>(R.id.btnBackToChoices).setOnClickListener {
            actions.onBackToChoices(task, GateScreen.DoingNow(task.id, startedAt))
        }
        root.findViewById<Button>(R.id.btnDone).setOnClickListener { actions.onDone(task, startedAt) }
        root.findViewById<Button>(R.id.btnCloseInstagram).setOnClickListener { actions.onCloseInstagram(task) }
        fitSystemBars(root, bottom = root)
        return root
    }

    private fun buildCheckIn(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_check_in, null)
        val person = settings.trustedPerson.ifBlank { context.getString(R.string.trusted_person_fallback) }
        root.findViewById<TextView>(R.id.tvCheckInBody).text =
            context.getString(R.string.check_in_body, task.title, person)
        root.findViewById<TextView>(R.id.tvCheckInWho).text = context.getString(R.string.check_in_who, person)
        root.findViewById<TextView>(R.id.tvCheckInMessage).text =
            context.getString(R.string.check_in_message, task.title)
        root.findViewById<Button>(R.id.btnCheckInDoNow).setOnClickListener { actions.onDoItNow(task) }
        root.findViewById<Button>(R.id.btnCheckInOk).apply {
            text = context.getString(R.string.check_in_ok, person)
            setOnClickListener { actions.onCheckInAccepted(task) }
        }
        root.findViewById<Button>(R.id.btnCheckInOff).setOnClickListener { actions.onCheckInOff(task) }
        fitSystemBars(root, bottom = root)
        return root
    }

    private fun buildAddTasks(): View {
        val root = inflater.inflate(R.layout.overlay_add_tasks, null)
        val list = root.findViewById<LinearLayout>(R.id.taskList)
        val listTitle = root.findViewById<TextView>(R.id.tvListTitle)
        val voiceBlock = root.findViewById<View>(R.id.voiceBlock)
        val typeBlock = root.findViewById<View>(R.id.typeBlock)
        val listFull = root.findViewById<View>(R.id.tvListFull)
        val input = root.findViewById<EditText>(R.id.etNewTask)
        val mic = root.findViewById<ImageButton>(R.id.btnMic)
        val micStatus = root.findViewById<TextView>(R.id.tvMicStatus)
        val transcript = root.findViewById<TextView>(R.id.tvTranscript)
        val start = root.findViewById<Button>(R.id.btnStartScrolling)
        val title = root.findViewById<TextView>(R.id.tvAddTitle)
        val subtitle = root.findViewById<TextView>(R.id.tvAddSubtitle)
        val voiceReady = PermissionUtils.hasMicPermission(context) &&
            SpeechRecognizer.isRecognitionAvailable(context)
        // The task whose time chips are open: the one just added, or one whose time pill was tapped.
        var expandedId: String? = null

        fun bind() {
            val now = System.currentTimeMillis()
            val tasks = TaskCache.getTasks(context)
            val next = TaskRules.next(tasks, now)
            list.removeAllViews()
            tasks.forEach { task ->
                list.addView(
                    taskRow(
                        parent = list,
                        task = task,
                        isNext = task.id == next?.id,
                        now = now,
                        expanded = task.id == expandedId,
                        onToggle = {
                            expandedId = if (expandedId == task.id) null else task.id
                            bind()
                        },
                        onWhen = { window ->
                            actions.onWhenChanged(task, window)
                            expandedId = null
                            bind()
                        },
                        onRemove = {
                            actions.onTaskRemoved(task)
                            bind()
                        },
                    )
                )
            }
            // Tasks, but none for now: say so, so the screen doesn't look like the list was lost.
            val nothingDue = tasks.isNotEmpty() && next == null
            title.setText(if (nothingDue) R.string.add_title_nothing_due else R.string.add_title)
            subtitle.setText(if (nothingDue) R.string.add_subtitle_nothing_due else R.string.add_subtitle)
            listTitle.isVisible = tasks.isNotEmpty()
            listTitle.text = context.getString(R.string.add_list_title, tasks.size, TaskRules.MAX_TASKS)
            val hasRoom = tasks.size < TaskRules.MAX_TASKS
            if (!hasRoom) hideKeyboard(input)
            voiceBlock.isVisible = hasRoom && voiceReady
            typeBlock.isVisible = hasRoom
            listFull.isVisible = !hasRoom
            start.setText(if (tasks.isEmpty()) R.string.add_not_now else R.string.add_start_scrolling)
        }

        fun addTyped() {
            val task = actions.onTaskAdded(input.text.toString(), Task.VIA_TEXT) ?: return
            input.text.clear()
            expandedId = task.id
            bind()
        }

        bind()
        root.findViewById<Button>(R.id.btnAddTyped).setOnClickListener { addTyped() }
        // Done on the on-screen keyboard, or Enter on a physical one (which reports no action).
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            when {
                actionId == EditorInfo.IME_ACTION_DONE || (enter && event?.action == KeyEvent.ACTION_DOWN) -> {
                    addTyped()
                    true
                }
                enter -> true
                else -> false
            }
        }
        mic.setOnClickListener {
            toggleListening(mic, micStatus, transcript) { heard ->
                val task = actions.onTaskAdded(heard, Task.VIA_VOICE) ?: return@toggleListening
                micStatus.setText(R.string.add_voice_added)
                expandedId = task.id
                bind()
            }
        }
        start.setOnClickListener { actions.onAddTasksClosed() }
        fitSystemBars(root, bottom = root)
        return root
    }

    private fun taskRow(
        parent: ViewGroup,
        task: Task,
        isNext: Boolean,
        now: Long,
        expanded: Boolean,
        onToggle: () -> Unit,
        onWhen: (TimeWindow?) -> Unit,
        onRemove: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.item_task_row, parent, false)
        row.findViewById<View>(R.id.dot)
            .setBackgroundResource(if (isNext) R.drawable.bg_dot_accent else R.drawable.bg_dot_muted)
        row.findViewById<TextView>(R.id.tvRowTitle).text = task.title
        row.findViewById<TextView>(R.id.tvRowWhen).apply {
            text = pillText(task, now)
            contentDescription = context.getString(R.string.when_change, text)
            setOnClickListener { onToggle() }
        }
        row.findViewById<ImageButton>(R.id.btnRemoveTask).apply {
            isVisible = true
            contentDescription = context.getString(R.string.add_remove, task.title)
            setOnClickListener { onRemove() }
        }
        row.findViewById<View>(R.id.whenChipsScroll).isVisible = expanded
        if (expanded) {
            val chips = row.findViewById<LinearLayout>(R.id.whenChips)
            val current = TaskRules.window(task)
            val options = WhenChoices.chips(now).toMutableList<TimeWindow?>()
            // What he said that isn't one of the chips ("Tomorrow 15:00") stays on offer, first so it's in view.
            if (current != null && current.key == WhenChoices.CUSTOM) options.add(0, current)
            options.forEach { window ->
                val chip = inflater.inflate(R.layout.item_when_chip, chips, false) as Button
                chip.text = WhenText.label(window, now, TimeZone.getDefault(), formats)
                chip.isSelected = (window?.key ?: "") == task.whenKey &&
                    (window?.key != WhenChoices.CUSTOM || window == current)
                chip.setOnClickListener { onWhen(window) }
                chips.addView(chip)
            }
        }
        return row
    }

    /** The task's time, or when it comes back after a snooze or a picked time. */
    private fun pillText(task: Task, now: Long): String =
        if (task.deferredBy != Task.BY_WHEN && task.deferredUntil > now) {
            context.getString(R.string.status_back_at, timeText(task.deferredUntil))
        } else {
            WhenText.label(TaskRules.window(task), now, TimeZone.getDefault(), formats)
        }

    private fun hideKeyboard(input: EditText) {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun toggleListening(mic: ImageButton, status: TextView, transcript: TextView, onHeard: (String) -> Unit) {
        // A second tap cancels, even if the recognizer never answered.
        if (recognizer != null) {
            stopListening(mic)
            status.setText(R.string.add_mic_idle)
            return
        }
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = status.setText(R.string.add_listening)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { showTranscript(transcript, it) }
            }

            override fun onResults(results: Bundle?) {
                val heard = firstResult(results)
                stopListening(mic)
                if (heard.isNullOrBlank()) {
                    status.setText(R.string.add_voice_retry)
                } else {
                    showTranscript(transcript, heard)
                    onHeard(heard)
                }
            }

            override fun onError(error: Int) {
                stopListening(mic)
                status.setText(R.string.add_voice_retry)
            }
        })
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
        mic.contentDescription = context.getString(R.string.add_mic_stop)
        status.setText(R.string.add_listening)
    }

    private fun stopListening(mic: ImageButton? = null) {
        recognizer?.destroy()
        recognizer = null
        mic?.contentDescription = context.getString(R.string.add_mic)
    }

    private fun showTranscript(transcript: TextView, text: String) {
        transcript.isVisible = true
        transcript.text = context.getString(R.string.add_transcript, text)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun metaFor(task: Task): String {
        val now = System.currentTimeMillis()
        if (task.deferredUntil in 1..now && task.deferredBy != Task.BY_WHEN) {
            return if (task.deferredBy == Task.BY_SNOOZE) {
                context.getString(R.string.gate_meta_snoozed)
            } else {
                context.getString(R.string.gate_meta_picked, timeText(task.deferredUntil))
            }
        }
        WhenText.gateLine(TaskRules.window(task), now, TimeZone.getDefault(), formats)?.let { return it }
        val day = when (TimeChoices.daysBetween(task.createdAt, now)) {
            0 -> context.getString(R.string.day_today)
            1 -> context.getString(R.string.day_yesterday)
            in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(task.createdAt))
            else -> DateFormat.getMediumDateFormat(context).format(Date(task.createdAt))
        }
        val template = if (task.addedVia == Task.VIA_VOICE) R.string.gate_meta_said else R.string.gate_meta_added
        return context.getString(template, day)
    }

    private fun labelFor(key: String): Int = when (key) {
        TimeChoices.IN_15 -> R.string.time_in_15
        TimeChoices.IN_60 -> R.string.time_in_60
        TimeChoices.TONIGHT -> R.string.time_tonight
        else -> R.string.time_tomorrow
    }

    private fun timeText(time: Long): String = DateFormat.getTimeFormat(context).format(Date(time))

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
            // The keyboard only opens when the text field is tapped.
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

    private companion object {
        const val TAG = "ReelsBlocker"
        const val BLUR_RADIUS = 40
    }
}
