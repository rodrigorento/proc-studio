package com.example.projdiic

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.ProjDIICTheme
import kotlinx.coroutines.delay
import java.util.Date
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProjDIICTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    ScrollGateScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Setup and the task list. Long-pressing the title shows the facilitator
 * panel: trigger settings, check-ins and the log.
 */
@Composable
fun ScrollGateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { GateSettings(context) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasMicPermission by remember { mutableStateOf(PermissionUtils.hasMicPermission(context)) }
    var isAccessibilityRunning by remember { mutableStateOf(InstagramReelsAccessibilityService.isServiceEnabled) }
    var swipesSincePause by remember { mutableIntStateOf(InstagramReelsAccessibilityService.currentReelsCount) }
    var snoozedUntil by remember { mutableLongStateOf(settings.snoozedUntil) }
    var tasks by remember { mutableStateOf(TaskCache.getTasks(context)) }
    var logCount by remember { mutableIntStateOf(EventLog.count(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showFacilitator by rememberSaveable { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)
            hasMicPermission = PermissionUtils.hasMicPermission(context)
            isAccessibilityRunning = InstagramReelsAccessibilityService.isServiceEnabled
            swipesSincePause = InstagramReelsAccessibilityService.currentReelsCount
            snoozedUntil = settings.snoozedUntil
            tasks = TaskCache.getTasks(context)
            logCount = EventLog.count(context)
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    fun addTask(text: String, via: String): Boolean {
        val task = TaskCache.addTask(context, text, via) ?: return false
        EventLog.log(
            context, "task_added",
            "via" to via,
            "where" to "app",
            "task" to task.title,
            "when" to task.whenKey.ifEmpty { "anytime" },
            "due_ms" to task.dueAt.takeIf { it > 0 },
        )
        tasks = TaskCache.getTasks(context)
        expandedId = task.id
        return true
    }

    fun changeWhen(task: Task, window: TimeWindow?) {
        TaskCache.update(context, TaskRules.withWhen(task, window, System.currentTimeMillis()))
        EventLog.log(
            context, "when_changed",
            "where" to "app",
            "task" to task.title,
            "from" to task.whenKey.ifEmpty { "anytime" },
            "to" to (window?.key ?: "anytime"),
            "due_ms" to window?.end,
        )
        tasks = TaskCache.getTasks(context)
        expandedId = null
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            addTask(it, Task.VIA_VOICE)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Scroll Gate",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .semantics { heading() }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showFacilitator = !showFacilitator })
                    }
            )
            Text(
                text = "After a stretch of scrolling in Instagram, it pauses the feed and shows the next thing on your list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (snoozedUntil > now) {
            Section("Snoozed") {
                SetupRow(
                    label = "Scroll Gate is off until tomorrow",
                    detail = "No pauses in Instagram until midnight.",
                    isOn = false,
                    action = "Turn back on",
                    onAction = {
                        settings.snoozedUntil = 0L
                        snoozedUntil = 0L
                        EventLog.log(context, "app_turned_back_on", "where" to "app")
                    }
                )
            }
        }

        Section("Setup") {
            SetupRow(
                label = "Accessibility service",
                detail = "Lets the app count your swipes in Instagram.",
                isOn = isAccessibilityRunning,
                action = "Turn on",
                onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SetupRow(
                label = "Display over other apps",
                detail = "Needed to pause Instagram.",
                isOn = hasOverlayPermission,
                action = "Allow",
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SetupRow(
                label = "Microphone",
                detail = "Optional: add tasks by talking on the pause screen.",
                isOn = hasMicPermission,
                action = "Allow",
                onAction = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }

        Section("Your list · ${tasks.size} of ${TaskRules.MAX_TASKS}") {
            val next = TaskRules.next(tasks, now)
            if (tasks.isEmpty()) {
                Text(
                    text = "Nothing yet. Add the things you keep putting off.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
            tasks.forEach { task ->
                TaskRow(
                    task = task,
                    isNext = task.id == next?.id,
                    now = now,
                    expanded = task.id == expandedId,
                    onToggle = { expandedId = if (expandedId == task.id) null else task.id },
                    onWhen = { changeWhen(task, it) },
                    onRemove = {
                        TaskCache.remove(context, task.id)
                        EventLog.log(context, "task_removed", "where" to "app", "task" to task.title)
                        tasks = TaskCache.getTasks(context)
                    }
                )
            }
            if (tasks.size < TaskRules.MAX_TASKS) {
                AddTaskRow(
                    onAdd = { addTask(it, Task.VIA_TEXT) },
                    onVoice = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you keep putting off?")
                        try {
                            voiceInput.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Voice input isn't available on this phone.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        if (showFacilitator) {
            FacilitatorPanel(
                settings = settings,
                swipesSincePause = swipesSincePause,
                logCount = logCount,
                onReset = {
                    TaskCache.clear(context)
                    FarmCache.clear(context)
                    EventLog.clear(context)
                    EventLog.log(context, "data_reset")
                    tasks = TaskCache.getTasks(context)
                    logCount = EventLog.count(context)
                    Toast.makeText(context, "Tasks and log cleared.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun FacilitatorPanel(settings: GateSettings, swipesSincePause: Int, logCount: Int, onReset: () -> Unit) {
    val context = LocalContext.current
    var swipes by remember { mutableIntStateOf(settings.swipeThreshold) }
    var minutes by remember { mutableIntStateOf(settings.minutesThreshold) }
    var cooldown by remember { mutableIntStateOf(settings.cooldownMinutes) }
    var checkIn by remember { mutableStateOf(settings.checkInEnabled) }
    var person by remember { mutableStateOf(settings.trustedPerson) }
    var confirmReset by remember { mutableStateOf(false) }

    Section("Facilitator · gate trigger") {
        StepperRow("Swipes before a pause (N)", swipes, GateSettings.SWIPE_RANGE) {
            settings.swipeThreshold = it
            swipes = it
            EventLog.log(context, "setting_changed", "swipe_threshold" to it)
        }
        StepperRow("Or minutes of scrolling (T)", minutes, GateSettings.MINUTE_RANGE) {
            settings.minutesThreshold = it
            minutes = it
            EventLog.log(context, "setting_changed", "minutes_threshold" to it)
        }
        StepperRow("Cooldown after a pause (min)", cooldown, GateSettings.COOLDOWN_RANGE) {
            settings.cooldownMinutes = it
            cooldown = it
            EventLog.log(context, "setting_changed", "cooldown_minutes" to it)
        }
        Text(
            text = "Swipes since the last pause: $swipesSincePause of $swipes",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        OutlinedButton(
            onClick = {
                InstagramReelsAccessibilityService.pauseAtNextSwipe = true
                Toast.makeText(context, "The next swipe in Instagram opens the gate.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text("Pause at the next swipe")
        }
    }

    Section("Facilitator · check-in") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Trusted-person check-in", fontSize = 16.sp)
                Text(
                    text = "After 2 passes on the same task. Switch on only with the participant's consent; a teammate sends the real message.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checkIn,
                onCheckedChange = {
                    settings.checkInEnabled = it
                    checkIn = it
                    EventLog.log(context, "setting_changed", "check_in" to it)
                }
            )
        }
        OutlinedTextField(
            value = person,
            onValueChange = {
                person = it
                settings.trustedPerson = it
            },
            label = { Text("Their first name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    Section("Facilitator · farm art preview") {
        FarmArtPreview()
    }

    Section("Facilitator · log") {
        Text(
            text = "$logCount events on this phone",
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { EventLog.share(context, EventLog.Format.JSON) },
                modifier = Modifier.weight(1f)
            ) { Text("Export JSON") }
            OutlinedButton(
                onClick = { EventLog.share(context, EventLog.Format.CSV) },
                modifier = Modifier.weight(1f)
            ) { Text("Export CSV") }
        }
        TextButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) { Text("Reset all data") }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all data?") },
            text = { Text("This deletes the task list and the log on this phone. Settings stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun SetupRow(label: String, detail: String, isOn: Boolean, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 16.sp)
            Text(
                text = if (isOn) "On" else detail,
                fontSize = 13.sp,
                color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isOn) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    isNext: Boolean,
    now: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onWhen: (TimeWindow?) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val formats = remember { whenFormats(context) }
    val window = TaskRules.window(task)
    val status = buildList {
        when {
            task.deferredBy != Task.BY_WHEN && task.deferredUntil > now ->
                add("Back at " + DateFormat.getTimeFormat(context).format(Date(task.deferredUntil)))
            isNext -> add("Next up")
        }
        if (task.snoozeUsed) add("snooze used")
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(task.title, fontSize = 16.sp)
            if (status.isNotEmpty()) {
                Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AssistChip(
            onClick = onToggle,
            label = { Text(WhenText.label(window, now, TimeZone.getDefault(), formats)) },
            modifier = Modifier.semantics { contentDescription = "When: ${WhenText.label(window, now, TimeZone.getDefault(), formats)}. Tap to change." }
        )
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Remove ${task.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (expanded) {
        val options = WhenChoices.chips(now).toMutableList<TimeWindow?>()
        // What he said that isn't one of the chips ("Tomorrow 15:00") stays on offer, first so it's in view.
        if (window != null && window.key == WhenChoices.CUSTOM) options.add(0, window)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = (option?.key ?: "") == task.whenKey &&
                        (option?.key != WhenChoices.CUSTOM || option == window),
                    onClick = { onWhen(option) },
                    label = { Text(WhenText.label(option, now, TimeZone.getDefault(), formats)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )
            }
        }
    }
}

@Composable
private fun AddTaskRow(onAdd: (String) -> Boolean, onVoice: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = {
        if (onAdd(draft)) {
            draft = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("e.g. Gym on Monday") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = onVoice) {
                    Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = "Say a task")
                }
            },
            modifier = Modifier.weight(1f)
        )
        Button(onClick = submit, enabled = draft.isNotBlank()) { Text("Add") }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "Lower: $label" }
        ) { Text("−", fontSize = 18.sp) }
        Text(
            text = value.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp)
        )
        FilledTonalIconButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "Raise: $label" }
        ) { Text("+", fontSize = 18.sp) }
    }
}
