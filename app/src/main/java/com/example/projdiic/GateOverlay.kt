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
import kotlin.math.roundToInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Draws Task Farm's pause screens over Instagram, one at a time, and reports
 * every tap to [actions]. The service decides which screen to show and when.
 */
class GateOverlay(service: Context, private val actions: Actions) {

    interface Actions {
        /** Harvest it, tapped at [tappedAt] on the gate or the check-in ([where]), once its harvest has played. */
        fun onHarvested(task: Task, where: String, tappedAt: Long)

        /** Water it, tapped at [tappedAt] on the gate or the check-in ([where]), once the can has poured. */
        fun onWatered(task: Task, where: String, tappedAt: Long)
        fun onSnooze(task: Task)
        fun onCheckInAccepted(task: Task)
        fun onCheckInOff(task: Task)

        /** Plants a task for [window] (null: Anytime). Returns it, or null when it could not (blank, or the field is full). */
        fun onTaskPlanted(title: String, window: TimeWindow?, via: String): Task?
        fun onTaskRemoved(task: Task)
        fun onWhenChanged(task: Task, window: TimeWindow?)
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

    /** Harvest it or Water it was tapped and its animation is playing; it takes effect when that ends or the screen goes. */
    private var pending: (() -> Unit)? = null

    /** On a later step of planting (the ask-for-tasks screen), Back goes a step back instead of closing. */
    private var plantBack: (() -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** A harvest or a watering is playing, so walking away isn't passing on its task: it happens either way. */
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
            is GateScreen.Nod -> task?.let(::buildNod)
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
        finishPending()
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

    /** The harvest or the watering has played, or the screen is going before it could: it happens either way. */
    private fun finishPending() {
        val choice = pending ?: return
        pending = null
        choice()
    }

    /** The system Back gesture: a step back where the screen has one; the gate itself only closes through its choices. */
    private fun backActionFor(screen: GateScreen, task: Task?): (() -> Unit)? = when {
        screen == GateScreen.AddTasks -> { -> plantBack?.invoke() ?: actions.onAddTasksClosed() }
        else -> null
    }

    private fun buildGate(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_gate, null)
        val now = System.currentTimeMillis()
        val bed = BedStack(root.findViewById(R.id.bed)).apply { show(FarmViews.bed(task, now)) }
        root.findViewById<TextView>(R.id.tvEyebrow).text = eyebrow
        root.findViewById<TextView>(R.id.tvTaskTitle).text = task.title
        root.findViewById<TextView>(R.id.tvTaskMeta).text = metaFor(task)
        // A due crop (a crop still growing gets the light pause instead, buildNod): Harvest it, or 5 more minutes once.
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
        bed.fadeTo(CropArt.bed(crop, stage, true), FarmViews.RIPEN_MS) {
            bed.harvest(CropArt.emptyBed(true), CropMotion.harvest(crop, stage)) { finishPending() }
        }
    }

    /**
     * The light pause for a crop still growing: the crop, the time left and Water it. The can pours over the small bed
     * (only time grows the crop), the card slides away, and the feed opens.
     */
    private fun buildNod(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_nod, null)
        val now = System.currentTimeMillis()
        val bed = BedStack(root.findViewById(R.id.bed)).apply { show(FarmViews.bed(task, now)) }
        root.findViewById<View>(R.id.bed).contentDescription = FarmViews.describe(task, now)
        root.findViewById<TextView>(R.id.tvNodTitle).text = task.title
        root.findViewById<TextView>(R.id.tvNodLine).text = FarmViews.nodLine(task, now, TimeZone.getDefault(), formats)
        val card = root.findViewById<View>(R.id.card)
        root.findViewById<Button>(R.id.btnNodWater).apply {
            withIcon(R.drawable.prop_watering_can)
            setOnClickListener {
                if (pending != null) return@setOnClickListener
                isEnabled = false
                alpha = 0.5f
                val tappedAt = System.currentTimeMillis()
                pending = { actions.onWatered(task, GateScreen.Nod(task.id).logName, tappedAt) }
                bed.water(CropMotion.water()) {
                    card.animate().translationY(card.height + dp(12).toFloat()).alpha(0f).setDuration(SLIDE_MS)
                        .withEndAction { finishPending() }.start()
                }
            }
        }
        fitSystemBars(root, bottom = root)
        return root
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

    /** Planting, step 1: the field so far, and the next task said or typed ([draft] when coming back to it). */
    private fun buildAddTasks(draft: String = ""): View {
        plantBack = null
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
        // The task whose time chips are open: one whose time pill was tapped.
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

        fun toSeeds(text: String, via: String) {
            val planting = SeedPackets.planting(text, System.currentTimeMillis()) ?: return
            hideKeyboard(input)
            swapTo(buildSeeds(planting, via, text), R.id.tvSeedsTitle)
        }

        fun addTyped() = toSeeds(input.text.toString(), Task.VIA_TEXT)

        input.setText(draft)
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
            toggleListening(mic, micStatus, transcript) { heard -> toSeeds(heard, Task.VIA_VOICE) }
        }
        start.setOnClickListener { actions.onAddTasksClosed() }
        root.findViewById<Button>(R.id.btnSnoozeApp).setOnClickListener { actions.onSnoozeAppUntilTomorrow() }
        fitSystemBars(root, bottom = root)
        return root
    }

    /** Planting, step 2: when the task is for, one seed packet per time, the time said in it first and picked. */
    private fun buildSeeds(planting: SeedPackets.Planting, via: String, draft: String): View {
        val root = inflater.inflate(R.layout.overlay_plant_seeds, null)
        val now = System.currentTimeMillis()
        val title = planting.title
        val packets = planting.packets
        val plant = root.findViewById<Button>(R.id.btnPlant)
        var picked = planting.picked
        val cards = mutableMapOf<Int, View>()
        fun label(packet: SeedPackets.Packet) = WhenText.label(packet.window, now, TimeZone.getDefault(), formats)
        fun bind() {
            cards.forEach { (index, card) -> card.isSelected = index == picked }
            root.findViewById<View>(R.id.ivSaidCheck).isVisible = picked == packets.indexOfFirst { it.said }
            val choice = packets.getOrNull(picked)
            plant.isEnabled = choice != null
            plant.text = choice?.let { context.getString(R.string.plant_button, FarmViews.cropName(it.crop).lowercase()) }
                ?: context.getString(R.string.plant_pick)
        }
        root.findViewById<TextView>(R.id.tvSeedsTask).text = title
        val saidIndex = packets.indexOfFirst { it.said }
        if (saidIndex >= 0) {
            val packet = packets[saidIndex]
            root.findViewById<View>(R.id.saidCard).apply {
                isVisible = true
                contentDescription = context.getString(R.string.plant_packet_said, label(packet), packet.crop)
                setOnClickListener { picked = saidIndex; bind() }
                cards[saidIndex] = this
            }
            root.findViewById<ImageView>(R.id.ivSaidPacket).setImageResource(CropArt.seedPacket(packet.crop))
            root.findViewById<TextView>(R.id.tvSaidWhen).text = label(packet)
            root.findViewById<TextView>(R.id.tvSaidCrop).text = FarmViews.cropName(packet.crop)
        }
        val rows = root.findViewById<LinearLayout>(R.id.packetRows)
        val gap = (10 * context.resources.displayMetrics.density).toInt()
        packets.indices.filter { it != saidIndex }.chunked(3).forEach { indices ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until 3) {
                val index = indices.getOrNull(column)
                val cell = if (index == null) {
                    View(context)
                } else {
                    val packet = packets[index]
                    inflater.inflate(R.layout.item_seed_packet, row, false).apply {
                        findViewById<ImageView>(R.id.ivPacket).setImageResource(CropArt.seedPacket(packet.crop))
                        findViewById<TextView>(R.id.tvPacketWhen).text = label(packet)
                        findViewById<TextView>(R.id.tvPacketCrop).text = FarmViews.cropName(packet.crop)
                        contentDescription = context.getString(R.string.plant_packet, label(packet), packet.crop)
                        setOnClickListener { picked = index; bind() }
                        cards[index] = this
                    }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (column > 0) marginStart = gap
                })
            }
            rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = gap
            })
        }
        val back = { swapTo(buildAddTasks(draft), R.id.tvAddTitle) }
        plantBack = back
        root.findViewById<Button>(R.id.btnSeedsBack).setOnClickListener { back() }
        plant.setOnClickListener {
            val choice = packets.getOrNull(picked) ?: return@setOnClickListener
            val task = actions.onTaskPlanted(title, SeedPackets.current(choice, System.currentTimeMillis()).window, via)
            if (task == null) swapTo(buildAddTasks(draft), R.id.tvAddTitle) else swapTo(buildPlanted(task), R.id.tvPlantedTitle)
        }
        bind()
        fitSystemBars(root, bottom = root)
        return root
    }

    /** Planting, the last step: the seeds pour into the bed; then back to the feed, or plant another. */
    private fun buildPlanted(task: Task): View {
        val root = inflater.inflate(R.layout.overlay_plant_done, null)
        val now = System.currentTimeMillis()
        val crop = FarmRules.crop(task)
        root.findViewById<View>(R.id.bed).contentDescription = context.getString(R.string.planted_bed, FarmViews.cropName(crop))
        BedStack(root.findViewById(R.id.bed)).plant(CropArt.emptyBed(true), CropArt.bed(crop, "seed", true), CropMotion.plant(crop)) {}
        val due = WhenText.due(TaskRules.window(task), now, TimeZone.getDefault(), formats)
        root.findViewById<TextView>(R.id.tvPlantedLine).text =
            FarmViews.plantedLine(task.title, crop, due, FarmViews.bloomDays(task, now, TimeZone.getDefault()))
        root.findViewById<TextView>(R.id.tvPlantedWhen)
            .setText(if (task.deferredUntil > now) R.string.planted_waits else R.string.planted_now)
        root.findViewById<Button>(R.id.btnPlantedScroll).setOnClickListener { actions.onAddTasksClosed() }
        val another = { swapTo(buildAddTasks(), R.id.tvAddTitle) }
        plantBack = another
        root.findViewById<Button>(R.id.btnPlantAnother).apply {
            isVisible = TaskCache.getTasks(context).size < TaskRules.MAX_TASKS
            setOnClickListener { another() }
        }
        fitSystemBars(root, bottom = root)
        return root
    }

    /**
     * Puts [step] where the screen is, in the same window: the steps of planting on the ask-for-tasks screen. TalkBack
     * moves to its [heading], since the button it was on is gone.
     */
    private fun swapTo(step: View, heading: Int) {
        val host = view as? OverlayRoot ?: return
        stopListening()
        host.removeAllViews()
        host.addView(step, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        ViewCompat.requestApplyInsets(step)
        host.nextBack()
        step.post { step.findViewById<View>(heading)?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
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
        row.findViewById<ImageView>(R.id.ivRowBed).apply {
            setImageResource(FarmViews.bed(task, now))
            alpha = if (isNext) 1f else 0.75f
        }
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
            val options = WhenChoices.chips(now).toMutableList()
            // What he said that isn't one of the chips ("Tomorrow 15:00") stays on offer, first so it's in view.
            if (current != null && current.key == WhenChoices.CUSTOM) options.add(0, current)
            options.forEach { window ->
                val chip = inflater.inflate(R.layout.item_when_chip, chips, false) as Button
                chip.text = WhenText.label(window, now, TimeZone.getDefault(), formats)
                chip.isSelected = window.key == task.whenKey && (window.key != WhenChoices.CUSTOM || window == current)
                chip.setOnClickListener { onWhen(window) }
                chips.addView(chip)
            }
        }
        return row
    }

    /** The task's time, or when it comes back after its 5 more minutes. */
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

    companion object {
        private const val TAG = "ReelsBlocker"
        private const val BLUR_RADIUS = 40

        /** How long the light pause's card takes to slide away once watered. */
        private const val SLIDE_MS = 220L
    }
}
