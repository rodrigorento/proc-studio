package com.example.projdiic

import android.content.ActivityNotFoundException
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.widget.TimePicker
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.projdiic.ui.theme.Clay
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.LeafContainer
import kotlinx.coroutines.delay
import java.util.TimeZone

private const val NAME = "name"
private const val SEEDS = "seeds"
private const val PLANTED = "planted"

/** How a task is being heard: [listening] while it listens; [start] begins, or stops while listening. */
class Listen(val listening: Boolean, val start: () -> Unit)

/** A way to hear a task, given what to do with the words heard; null when there is none (no microphone). */
typealias Hearing = @Composable (onHeard: (String) -> Unit) -> Listen?

/** Android's voice screen, which the app can open. */
val VoiceScreenHearing: Hearing = { onHeard ->
    val context = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onHeard)
    }
    Listen(listening = false) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What are you planting?")
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice input isn't available on this phone.", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Planting a task, step by step over the farm: name it (typed or said, through [hearing]), pick its seeds (when it's
 * for), then watch the seeds go in. [onPlant] adds the task and returns it, or null when it couldn't (the field filled
 * up).
 */
@Composable
fun PlantFlow(
    tasks: List<Task>,
    hearing: Hearing,
    onPlant: (title: String, window: TimeWindow?, via: String) -> Task?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(NAME) }
    var draft by rememberSaveable { mutableStateOf("") }
    var via by rememberSaveable { mutableStateOf(Task.VIA_TEXT) }
    var seedsAt by rememberSaveable { mutableLongStateOf(0L) }
    var pick by rememberSaveable(stateSaver = PickSaver) { mutableStateOf<SeedPackets.Pick?>(null) }
    var plantedId by rememberSaveable { mutableStateOf<String?>(null) }

    // The text, not the screen's last look at it: a voice result arrives and moves on in one go.
    fun toSeeds(text: String) {
        val now = System.currentTimeMillis()
        val planting = SeedPackets.planting(text, now) ?: return
        seedsAt = now
        pick = planting.said
        step = SEEDS
    }

    BackHandler {
        if (step == SEEDS) step = NAME else onClose()
    }
    when (step) {
        NAME -> NameStep(
            draft = draft,
            hearing = hearing,
            heard = WhenParser.parse(draft, System.currentTimeMillis()).window?.let {
                WhenText.label(it, System.currentTimeMillis(), TimeZone.getDefault(), whenFormats(context))
            },
            onDraft = {
                draft = it
                via = Task.VIA_TEXT
            },
            onHeard = {
                draft = it
                via = Task.VIA_VOICE
                toSeeds(it)
            },
            onNext = { toSeeds(draft) },
            onClose = onClose,
        )
        SEEDS -> {
            val planting = remember(draft, seedsAt) { SeedPackets.planting(draft, seedsAt) }
            if (planting == null) {
                LaunchedEffect(Unit) { step = NAME }
                return
            }
            // The clock, for a time going while the step is open; past midnight, the step moves to the new day, the
            // date picked with it.
            var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(15_000L)
                    clock = System.currentTimeMillis()
                }
            }
            // A pick for a day that has gone (today's, after midnight) is dropped: the seeds show again.
            fun newDay(at: Long): Boolean {
                if (DayMath.daysBetween(seedsAt, at, TimeZone.getDefault()) == 0) return false
                pick = pick?.let { SeedPackets.rebase(it, from = seedsAt, to = at) }?.takeIf { it.day >= 0 }
                seedsAt = at
                return true
            }
            LaunchedEffect(clock) { newDay(clock) }
            SeedsStep(
            title = planting.title,
            seeds = remember(seedsAt) { SeedPackets.seeds(seedsAt) },
            pick = pick,
            now = seedsAt,
            clock = clock,
            onPick = { pick = it },
            onBack = { step = NAME },
            onPlant = {
                val at = System.currentTimeMillis()
                clock = at
                // A new day first shows its seeds: the crop may be another by now (Monday's lettuce was Sunday's carrot).
                if (newDay(at)) return@SeedsStep
                val chosen = pick ?: return@SeedsStep
                if (SeedPackets.isPast(chosen, at, shownAt = seedsAt)) return@SeedsStep
                val task = onPlant(planting.title, SeedPackets.window(chosen, at, shownAt = seedsAt), via)
                if (task == null) {
                    Toast.makeText(context, "Your field is full. Harvest one to plant another.", Toast.LENGTH_SHORT).show()
                    onClose()
                } else {
                    plantedId = task.id
                    step = PLANTED
                }
            },
            )
        }
        PLANTED -> {
            val task = tasks.firstOrNull { it.id == plantedId }
            if (task == null) {
                LaunchedEffect(Unit) { onClose() }
                return
            }
            PlantedStep(
                task = task,
                hasRoom = tasks.size < TaskRules.MAX_TASKS,
                onAnother = {
                    draft = ""
                    via = Task.VIA_TEXT
                    plantedId = null
                    step = NAME
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun NameStep(
    draft: String,
    hearing: Hearing,
    heard: String?,
    onDraft: (String) -> Unit,
    onHeard: (String) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val listen = hearing(onHeard)
    LaunchedEffect(Unit) { focus.requestFocus() }
    StepScreen(
        pane = "What are you planting?",
        top = { StepBar(back = "Field", onBack = onClose, step = 1) },
        bottom = {
            PrimaryButton(
                "Next: pick the seeds",
                enabled = TaskRules.cleanTitle(WhenParser.parse(draft, System.currentTimeMillis()).title) != null,
                onClick = onNext,
            )
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(CropArt.emptyBed(true)),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
        }
        Heading("What are you planting?")
        Body("One thing you keep putting off. Add when, if it has a time.")
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text("Call the dentist tomorrow at 3") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).focusRequester(focus)
        )
        if (heard != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Heard a time: $heard",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (listen != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            FilledIconButton(onClick = listen.start, modifier = Modifier.size(60.dp)) {
                Icon(
                    painterResource(R.drawable.ic_mic),
                    contentDescription = if (listen.listening) "Stop listening" else "Say it",
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(if (listen.listening) "Listening…" else "Or say it", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "\"Gym on Monday\", \"Pay rent this month\"",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Keeps the seed picked, with its day and time, across a rotation. */
private val PickSaver = Saver<SeedPackets.Pick?, List<Any>>(
    save = { pick ->
        pick?.let { listOf(it.seed.crop, it.seed.firstDay, it.seed.lastDay, it.day, it.minute ?: -1, it.said, it.startsAt) }
    },
    restore = { saved ->
        // Saved by an older version, which kept the seed's name: pick again.
        if (saved.size != 7) {
            null
        } else {
            SeedPackets.Pick(
                SeedPackets.Seed(saved[0] as String, saved[1] as Int, saved[2] as Int),
                day = saved[3] as Int,
                minute = (saved[4] as Int).takeIf { it >= 0 },
                said = saved[5] as Boolean,
                startsAt = saved[6] as Long,
            )
        }
    },
)

/**
 * The seeds, one per stretch of days, each saying its days. Tapping one moves it up where the time said would be, hides
 * the others and opens its days on a calendar and a time; the time said in the task opens that way already. [now] is
 * when the step was shown, which the days count from; [clock] the time now, for a time that has gone.
 */
@Composable
private fun SeedsStep(
    title: String,
    seeds: List<SeedPackets.Seed>,
    pick: SeedPackets.Pick?,
    now: Long,
    clock: Long,
    onPick: (SeedPackets.Pick?) -> Unit,
    onBack: () -> Unit,
    onPlant: () -> Unit,
) {
    val context = LocalContext.current
    val formats = remember { whenFormats(context) }
    val zone = TimeZone.getDefault()
    val window = pick?.let { SeedPackets.window(it, now, zone) }
    val gone = pick != null && SeedPackets.isPast(pick, clock, zone, shownAt = now)
    // Set by hand: no longer the time said, so it starts now.
    fun change(next: SeedPackets.Pick) = onPick(next.copy(said = false, startsAt = 0L))
    StepScreen(
        pane = "When should it be ripe?",
        top = { StepBar(back = "Name", onBack = onBack, step = 2) },
        bottom = {
            PrimaryButton(
                text = pick?.let { "Plant the " + FarmViews.cropName(it.seed.crop).lowercase() } ?: "Pick the seeds",
                enabled = pick != null && !gone,
                packet = pick?.seed?.crop,
                onClick = onPlant,
            )
        },
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(LeafContainer, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Heading("When should it be ripe?")
        if (pick == null) {
            Body("Each seed grows its own crop. Sooner things grow as smaller crops.")
            Column(Modifier.selectableGroup().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                seeds.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { seed ->
                            SeedCard(seed, SeedPackets.label(seed, now, formats), onClick = { onPick(SeedPackets.pick(seed)) }, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            PickedCard(pick, SeedPackets.label(pick.seed, now, formats), onChange = { onPick(null) })
            if (pick.seed.lastDay > pick.seed.firstDay) SeedCalendarView(pick.seed, pick.day, now, onDay = { change(pick.copy(day = it)) })
            TimeRow(
                value = if (pick.minute == null) "Any time" else formats.time(window!!.end),
                on = pick.minute != null,
                onSwitch = { on ->
                    change(pick.copy(minute = if (on) SeedPackets.defaultMinute(pick.day, System.currentTimeMillis(), zone) else null))
                },
            )
            pick.minute?.let { minute -> TimeWheel(minute) { if (it != pick.minute) change(pick.copy(minute = it)) } }
            // Turned on, the wheels open under the fold: bring them, and the line under them, into view.
            val ripeLine = remember { BringIntoViewRequester() }
            val timed = pick.minute != null
            var wasTimed by remember { mutableStateOf(timed) }
            LaunchedEffect(timed) {
                if (timed && !wasTimed) ripeLine.bringIntoView()
                wasTimed = timed
            }
            Text(
                text = if (gone) "That time has gone today. Pick a later one." else "Ripe " + (WhenText.due(window, now, zone, formats) ?: ""),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (gone) Clay else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).bringIntoViewRequester(ripeLine)
            )
        }
    }
}

@Composable
private fun SeedCard(seed: SeedPackets.Seed, days: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.selectable(selected = false, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 14.dp, start = 4.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(CropArt.seedPacket(seed.crop)), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(days, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text(FarmViews.cropName(seed.crop), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** The seed picked, where the time said sits: its packet, its days and crop, and Change to see the others again. */
@Composable
private fun PickedCard(pick: SeedPackets.Pick, label: String, onChange: () -> Unit) {
    val days = if (pick.said) "You said · $label" else label
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LeafContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(CropArt.seedPacket(pick.seed.crop)), contentDescription = null, modifier = Modifier.size(60.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    days.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    // Read as written, not letter by letter.
                    modifier = Modifier.semantics { contentDescription = days }
                )
                Text(FarmViews.cropName(pick.seed.crop), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            TextButton(onClick = onChange) { Text("Change", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** The seed's week, or the grapes' months a page at a time; only the seed's days can be picked. */
@Composable
private fun SeedCalendarView(seed: SeedPackets.Seed, picked: Int, now: Long, onDay: (Int) -> Unit) {
    val zone = TimeZone.getDefault()
    val months = remember(seed, now) { SeedCalendar.pages(seed, now, zone) }
    // Keyed on the seed's days too: the grapes' first week moves on a day at a time, and their pages with it.
    var shown by rememberSaveable(seed.crop, seed.firstDay) {
        mutableIntStateOf(months.indexOfFirst { month -> month.weeks.flatten().any { it?.day == picked } }.coerceAtLeast(0))
    }
    val month = months[shown.coerceIn(0, months.lastIndex)]
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(
                    SeedCalendar.title(month, now, zone),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).padding(start = 6.dp).semantics { heading() }
                )
                if (months.size > 1) {
                    IconButton(onClick = { shown-- }, enabled = shown > 0) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Previous month")
                    }
                    IconButton(onClick = { shown++ }, enabled = shown < months.lastIndex) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Next month", modifier = Modifier.graphicsLayer(scaleX = -1f))
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp).clearAndSetSemantics {}) {
                SeedCalendar.weekdayLetters().forEach {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            month.weeks.forEach { week ->
                Row {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                            if (day != null) DayCell(day, selected = day.day == picked, now = now, onClick = { onDay(day.day) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: SeedCalendar.Day, selected: Boolean, now: Long, onClick: () -> Unit) {
    val circle = Modifier.size(40.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
    val modifier = if (day.open) {
        circle
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = SeedCalendar.spoken(day.day, now) }
    } else {
        circle.clearAndSetSemantics {}
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            day.dayOfMonth.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                day.open -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            }
        )
    }
}

/** Any time that day, or a time: the switch shows Android's wheels under it. */
@Composable
private fun TimeRow(value: String, on: Boolean, onSwitch: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = on, role = Role.Switch, onValueChange = onSwitch)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 12.dp)
        ) {
            Text("Time", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

/** Android's time wheels ([R.layout.view_time_wheel]), the same as over Instagram. */
@Composable
private fun TimeWheel(minute: Int, onMinute: (Int) -> Unit) {
    val latest by rememberUpdatedState(onMinute)
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.view_time_wheel, null) as TimePicker).apply {
                setIs24HourView(DateFormat.is24HourFormat(context))
                hour = minute / 60
                this.minute = minute % 60
                setOnTimeChangedListener { _, h, m -> latest(h * 60 + m) }
            }
        },
        update = { wheel ->
            if (wheel.hour != minute / 60) wheel.hour = minute / 60
            if (wheel.minute != minute % 60) wheel.minute = minute % 60
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PlantedStep(task: Task, hasRoom: Boolean, onAnother: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val crop = FarmRules.crop(task)
    val due = WhenText.due(TaskRules.window(task), now, TimeZone.getDefault(), whenFormats(context))
    StepScreen(
        pane = "Planted",
        top = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Back to the field")
                }
            }
        },
        bottom = {
            PrimaryButton("Back to the field", onClick = onClose)
            if (hasRoom) {
                Button(
                    onClick = onAnother,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Plant another", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        },
    ) {
        PlantingBed(crop, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Text(
            text = "Planted!",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        Text(
            text = FarmViews.plantedLine(task.title, crop, due, FarmViews.bloomDays(task, System.currentTimeMillis(), TimeZone.getDefault())),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = if (task.deferredUntil > now) {
                    "It comes up at a pause in Instagram once its time starts."
                } else {
                    "It can come up at your next pause in Instagram."
                },
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

/** The seeds pouring into an empty bed, which turns into the crop's seed bed as they land; played once. */
@Composable
private fun PlantingBed(crop: String, modifier: Modifier) {
    val context = LocalContext.current
    val frames = remember(crop) { CropMotion.plant(crop) }
    var frame by remember { mutableStateOf<Int?>(0) }
    LaunchedEffect(crop) {
        delay(200L) // the screen has just come in
        for (i in frames.indices) {
            // The seeds rattle as they pour out of the packet.
            if (i == SEEDS_SOUND_FRAME) FarmSounds.play(context, FarmSound.SEEDS)
            frame = i
            delay(1000L / CropMotion.FPS)
        }
        frame = null
    }
    val shown = frame
    val seeded = if (shown == null) 1f else ((shown - CropMotion.SEED_FROM).toFloat() / (CropMotion.SEED_TO - CropMotion.SEED_FROM)).coerceIn(0f, 1f)
    Box(modifier.aspectRatio(1f).semantics { contentDescription = FarmViews.cropAndStage(crop, "seed").replaceFirstChar { it.uppercaseChar() } + ", just planted" }) {
        if (seeded < 1f) Layer(CropArt.emptyBed(true))
        if (seeded > 0f) Layer(CropArt.bed(crop, "seed", true), seeded)
        shown?.let { Layer(frames[it]) }
    }
}

/**
 * A step's screen: a bar on top, its content scrolling in the middle, and its buttons at the bottom. [pane] names it
 * for screen readers, which announce it when the step changes.
 */
@Composable
private fun StepScreen(pane: String, top: @Composable () -> Unit, bottom: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().semantics { paneTitle = pane }) {
        Box(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp)) { top() }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
    }
}

@Composable
private fun StepBar(back: String, onBack: () -> Unit, step: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(back, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Step $step of 2" }
        ) {
            repeat(2) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .width(22.dp)
                        .height(6.dp)
                        .background(if (i < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                )
            }
            Text("$step of 2", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, packet: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        packet?.let {
            Image(painterResource(CropArt.seedPacket(it)), contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The planting's frame where the seeds start to pour, for their rattle. */
private const val SEEDS_SOUND_FRAME = 3
