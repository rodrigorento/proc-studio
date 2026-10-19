package com.example.projdiic

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

/**
 * Planting a task, step by step over the home screen: name it (typed or said), pick its seeds (when it's for), then
 * watch the seeds go in. [onPlant] adds the task and returns it, or null when it couldn't (the field filled up).
 */
@Composable
fun PlantFlow(
    tasks: List<Task>,
    onPlant: (title: String, window: TimeWindow?, via: String) -> Task?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(NAME) }
    var draft by rememberSaveable { mutableStateOf("") }
    var via by rememberSaveable { mutableStateOf(Task.VIA_TEXT) }
    var seedsAt by rememberSaveable { mutableLongStateOf(0L) }
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    var plantedId by rememberSaveable { mutableStateOf<String?>(null) }

    // The text, not the screen's last look at it: a voice result arrives and moves on in one go.
    fun toSeeds(text: String) {
        val now = System.currentTimeMillis()
        val planting = SeedPackets.planting(text, now) ?: return
        seedsAt = now
        picked = planting.picked
        step = SEEDS
    }

    BackHandler {
        if (step == SEEDS) step = NAME else onClose()
    }
    when (step) {
        NAME -> NameStep(
            draft = draft,
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
            SeedsStep(
            title = planting.title,
            packets = planting.packets,
            now = seedsAt,
            picked = picked,
            onPick = { picked = it },
            onBack = { step = NAME },
            onPlant = {
                val chosen = planting.packets.getOrNull(picked) ?: return@SeedsStep
                val packet = SeedPackets.current(chosen, System.currentTimeMillis())
                val task = onPlant(planting.title, packet.window, via)
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
    heard: String?,
    onDraft: (String) -> Unit,
    onHeard: (String) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onHeard)
    }
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            FilledIconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What are you planting?")
                    try {
                        voice.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Voice input isn't available on this phone.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Say it", modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text("Or say it", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "\"Gym on Monday\", \"Pay rent this month\"",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeedsStep(
    title: String,
    packets: List<SeedPackets.Packet>,
    now: Long,
    picked: Int,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    onPlant: () -> Unit,
) {
    val context = LocalContext.current
    val formats = remember { whenFormats(context) }
    fun label(packet: SeedPackets.Packet) = WhenText.label(packet.window, now, TimeZone.getDefault(), formats)
    val choice = packets.getOrNull(picked)
    StepScreen(
        pane = "When should it be ripe?",
        top = { StepBar(back = "Name", onBack = onBack, step = 2) },
        bottom = {
            PrimaryButton(
                text = choice?.let { "Plant the " + FarmViews.cropName(it.crop).lowercase() } ?: "Pick the seeds",
                enabled = choice != null,
                packet = choice?.crop,
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
        Body("Each time grows its own crop. Sooner things grow as smaller crops.")
        val said = packets.indexOfFirst { it.said }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (said >= 0) {
            val packet = packets[said]
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (picked == said) LeafContainer else MaterialTheme.colorScheme.surface,
                border = if (picked == said) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .selectable(selected = picked == said, role = Role.RadioButton, onClick = { onPick(said) })
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(CropArt.seedPacket(packet.crop)), contentDescription = null, modifier = Modifier.size(60.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            "YOU SAID",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(label(packet), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(FarmViews.cropName(packet.crop), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (picked == said) Check()
                }
            }
        }
        val others = packets.indices.filter { it != said }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            others.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { index ->
                        PacketCard(packets[index], label(packets[index]), picked == index, { onPick(index) }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        }
    }
}

@Composable
private fun PacketCard(packet: SeedPackets.Packet, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) LeafContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp, start = 4.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(CropArt.seedPacket(packet.crop)), contentDescription = null, modifier = Modifier.size(68.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                FarmViews.cropName(packet.crop),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
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
    val frames = remember(crop) { CropMotion.plant(crop) }
    var frame by remember { mutableStateOf<Int?>(0) }
    LaunchedEffect(crop) {
        delay(200L) // the screen has just come in
        for (i in frames.indices) {
            frame = i
            delay(1000L / CropMotion.FPS)
        }
        frame = null
    }
    val shown = frame
    val seeded = if (shown == null) 1f else ((shown - CropMotion.SEED_FROM).toFloat() / (CropMotion.SEED_TO - CropMotion.SEED_FROM)).coerceIn(0f, 1f)
    Box(modifier.aspectRatio(1f).semantics { contentDescription = FarmViews.cropName(crop) + " seeds, just planted" }) {
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

@Composable
private fun Check() {
    Box(
        Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
    }
}
