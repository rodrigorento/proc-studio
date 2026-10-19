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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.focus.FocusRequester
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
import com.example.projdiic.ui.theme.Wood
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
 * The home screen: the field (one plot per task), its watering, and planting new tasks; setup while something is
 * missing. Long-pressing the title shows the facilitator panel: trigger settings, check-ins and the log.
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
    var watered by remember { mutableStateOf(FarmCache.isWatered(context)) }
    var rainFrame by remember { mutableStateOf<Int?>(null) }
    var showFacilitator by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val plantFocus = remember { FocusRequester() }

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
            // A field nobody watered gets rain in the evening; a rain from a skipped watering shows here too.
            val rains = FarmRules.rainsOnHome(FarmCache.wateredOn(context), now)
            if (rains) {
                FarmCache.rain(context, now, show = false)
                EventLog.log(context, "field_watered", "by" to "rain", "where" to "app")
            }
            if ((rains || FarmCache.takeRainToShow(context)) && rainFrame == null) {
                for (frame in CropMotion.rain(fieldLight(now)).indices) {
                    rainFrame = frame
                    delay(1000L / CropMotion.FPS)
                }
                rainFrame = null
            }
            watered = FarmCache.isWatered(context, now)
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
            "crop" to FarmRules.crop(task),
        )
        tasks = TaskCache.getTasks(context)
        return true
    }

    fun changeWhen(task: Task, window: TimeWindow?) {
        val changed = TaskRules.withWhen(task, window, System.currentTimeMillis())
        TaskCache.update(context, changed)
        EventLog.log(
            context, "when_changed",
            "where" to "app",
            "task" to task.title,
            "from" to task.whenKey.ifEmpty { "anytime" },
            "to" to (window?.key ?: "anytime"),
            "due_ms" to window?.end,
            "crop" to FarmRules.crop(changed),
        )
        tasks = TaskCache.getTasks(context)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            addTask(it, Task.VIA_VOICE)
        }
    }
    val rainWetness = rainFrame?.let { ((it - CropMotion.WET_FROM).toFloat() / (CropMotion.WET_TO - CropMotion.WET_FROM)).coerceIn(0f, 1f) }
    val look = FieldLook(fieldLight(now), watered && rainFrame == null, rainFrame, rainWetness ?: if (watered) 1f else 0f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Scroll Gate",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .semantics { heading() }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showFacilitator = !showFacilitator })
                    }
            )
            Text(
                text = "After a stretch of scrolling in Instagram, it pauses the feed and shows you the next thing growing in your field.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (snoozedUntil > now) {
            GateClosedCard(onOpen = {
                settings.snoozedUntil = 0L
                snoozedUntil = 0L
                EventLog.log(context, "app_turned_back_on", "where" to "app")
            })
        }

        val needsSetup = !isAccessibilityRunning || !hasOverlayPermission
        if (needsSetup) {
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
                MicRow(hasMicPermission) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Your field · ${tasks.size} of ${TaskRules.MAX_TASKS}")
            Field(
                tasks = tasks,
                now = now,
                look = look,
                onOpen = { openId = it.id },
                onPlant = { plantFocus.requestFocus() },
            )
            WateringRow(watered = watered || rainFrame != null)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Plant a task")
            PlantRow(
                full = tasks.size >= TaskRules.MAX_TASKS,
                focus = plantFocus,
                onPlant = { addTask(it, Task.VIA_TEXT) },
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
            if (!needsSetup && !hasMicPermission) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MicRow(hasMicPermission) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                }
            }
        }

        tasks.firstOrNull { it.id == openId }?.let { task ->
            PlotSheet(
                task = task,
                now = now,
                look = look,
                onWhen = { changeWhen(task, it) },
                onRemove = {
                    TaskCache.remove(context, task.id)
                    EventLog.log(context, "task_removed", "where" to "app", "task" to task.title)
                    tasks = TaskCache.getTasks(context)
                    openId = null
                },
                onDismiss = { openId = null },
            )
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

/** Shown while the app is snoozed until tomorrow: the farm gate is closed. */
@Composable
private fun GateClosedCard(onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.prop_gate), contentDescription = null, modifier = Modifier.size(72.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Gate closed until tomorrow", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "No pauses in Instagram until midnight.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpen) { Text("Open it") }
        }
    }
}

@Composable
private fun MicRow(isOn: Boolean, onAllow: () -> Unit) {
    SetupRow(
        label = "Microphone",
        detail = "Optional: plant tasks by talking on the pause screen.",
        isOn = isOn,
        action = "Allow",
        onAction = onAllow
    )
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
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = Wood,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
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
