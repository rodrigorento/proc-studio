package com.example.projdiic

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.Leaf
import com.example.projdiic.ui.theme.Water
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the farm does with tasks, wherever it is shown: the app logs as the app, the pause over Instagram as itself. */
interface Farmer {
    /** Plants a task for [window]. Returns it, or null when it could not (the field is full). */
    fun plant(title: String, window: TimeWindow?, via: String): Task?

    /** [task]'s harvest has played on the field: it's done. [ripe] when it didn't have to ripen first. */
    fun harvested(task: Task, ripe: Boolean)

    /** Remove it, on a crop not due yet. */
    fun removed(task: Task)

    /** [task] watered today, by a tap on its dry bed or Water it in its sheet ([via]: "bed" or "sheet"). */
    fun watered(task: Task, via: String)

    /**
     * Undo: [task], just harvested or removed ([after]: "harvest" or "remove"), back in its plot ([index]). False when
     * it couldn't be (the field has filled up since).
     */
    fun restored(task: Task, index: Int, after: String): Boolean
}

/**
 * The farm: the title, the field and a card per crop, a crop's sheet (Harvest it, Water it or Remove it) and planting,
 * and each day's watering (a tap on a dry bed waters it); the same
 * in the app and over Instagram. [header] and [footer] add what only one of them has, above and under the field, and
 * [buttons] what stays at the foot of the screen, given a way to start planting and whether a harvest is playing (Back
 * waits for it too, so it's done before the screen goes). Over Instagram ([overFeed]) the crop's sheet is drawn in the
 * screen, as an overlay window can't open a dialog of its own.
 */
@Composable
fun Farm(
    tasks: List<Task>,
    now: Long,
    farmer: Farmer,
    hearing: Hearing,
    modifier: Modifier = Modifier,
    overFeed: Boolean = false,
    onTitleLongPress: (() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    buttons: @Composable ColumnScope.(plant: () -> Unit, harvesting: Boolean, celebration: Long) -> Unit,
) {
    var planting by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var reap by remember { mutableStateOf<Reap?>(null) }
    // The last harvest or removal, which Undo can take back for a few seconds.
    var undo by remember { mutableStateOf<Undo?>(null) }
    val context = LocalContext.current
    // When the day's last dry bed was watered (0: not yet here): the field sparkles and the buttons may cheer.
    var celebration by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    // Beds being watered: the can's frame on each.
    var splashes by remember { mutableStateOf(mapOf<String, Int>()) }
    // The crops and the time as of the latest frame, for a can that finishes after the field has changed.
    val latest by rememberUpdatedState(tasks)
    val latestNow by rememberUpdatedState(now)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Not while a crop is being harvested: its Harvest it would wait for nothing.
    fun open(task: Task) {
        if (reap == null) openId = task.id
    }

    // Harvest it, from the crop's sheet or a swipe on its card: the crop ripens if it must and flies out of its bed on
    // the field; then it's done, and Undo can bring it back.
    fun harvest(task: Task): Boolean {
        if (reap != null) return false
        openId = null
        val index = tasks.indexOfFirst { it.id == task.id }
        val at = System.currentTimeMillis()
        val crop = FarmRules.crop(task)
        val stage = CropMotion.harvestedAs(crop, FarmRules.stage(task, at))
        val ripe = FarmRules.isReady(task, at)
        val wet = FarmRules.isWatered(task, at)
        reap = Reap(task.id, crop, stage, frame = -1, wet = wet)
        // The harvest plays on the field: bring it into view from a card further down.
        scope.launch { scroll.animateScrollTo(0) }
        scope.launch {
            try {
                delay(FarmViews.RIPEN_MS)
                for (frame in CropMotion.harvest(crop, stage).indices) {
                    reap = Reap(task.id, crop, stage, frame, wet)
                    delay(1000L / CropMotion.FPS)
                }
            } finally {
                // Leaving the screen mid-harvest still harvests it, as on the pause.
                reap = null
                farmer.harvested(task, ripe)
                undo = Undo(task, index, harvested = true)
            }
        }
        return true
    }

    // The day's last dry bed watered: the field sparkles once and the phone gives a success tick; then it's over, so
    // coming back to the field (after planting, say) doesn't play it again.
    fun celebrate() {
        val stamp = System.currentTimeMillis()
        celebration = stamp
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            delay(CELEBRATION_MS)
            if (celebration == stamp) celebration = 0L
        }
    }

    // A dry bed tapped, or Water it in its sheet: watered at once, then the can pours over the bed.
    fun water(task: Task, via: String) {
        openId = null
        if (task.id in splashes || reap?.taskId == task.id) return
        farmer.watered(task, via)
        scope.launch { scroll.animateScrollTo(0) }
        scope.launch {
            try {
                for (frame in CropMotion.water().indices) {
                    splashes = splashes + (task.id to frame)
                    delay(1000L / CropMotion.FPS)
                }
                // The last can still pouring, and no bed dry any more: the day's watering is done.
                if (splashes.keys == setOf(task.id) && !FarmRules.needsWater(latest, latestNow)) celebrate()
            } finally {
                splashes = splashes - task.id
            }
        }
    }

    // Remove it, from the crop's sheet or a swipe on its card; Undo can bring it back. Not while a harvest plays: the
    // beds would move under it.
    fun remove(task: Task): Boolean {
        if (reap != null) return false
        openId = null
        val index = tasks.indexOfFirst { it.id == task.id }
        farmer.removed(task)
        undo = Undo(task, index, harvested = false)
        return true
    }

    // Planting is a new step: the last harvest or removal can't be undone after it.
    fun plant() {
        undo = null
        planting = true
    }

    BackHandler(enabled = reap != null) {}

    if (planting) {
        Box(modifier.fillMaxSize()) {
            PlantFlow(tasks = tasks, hearing = hearing, onPlant = farmer::plant, onClose = { planting = false })
        }
        return
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { heading() }
                        .then(
                            if (onTitleLongPress == null) Modifier
                            else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onTitleLongPress() }) }
                        )
                )
                header()
                // The day's watering: what's left to do, then done (one line either way, so the field doesn't jump).
                if (tasks.isNotEmpty()) {
                    val thirsty = FarmRules.needsWater(tasks, now)
                    Text(
                        text = if (thirsty) "Tap each dry bed to water it." else "All watered for today.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (thirsty) Water else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field(
                        tasks = tasks,
                        now = now,
                        reap = reap,
                        splashes = splashes,
                        celebration = celebration,
                        // Lambdas, not ::water: a reference to a local function equals every other one to it, whatever
                        // it captured, so the field's rememberUpdatedState would keep the first, with its old tasks.
                        onOpen = { open(it) },
                        onWater = { task, via -> water(task, via) },
                        onPlant = { plant() },
                        modifier = Modifier.bleed(20.dp),
                    )
                    CropCards(
                        tasks = tasks,
                        now = now,
                        harvesting = reap != null,
                        // A swipe either way does what the card showed behind it: harvest a due crop, remove the rest.
                        onSwiped = { task, harvests -> if (harvests) harvest(task) else remove(task) },
                        onOpen = ::open,
                    )
                }
                footer()
            }
            undo?.let { last ->
                UndoBar(
                    last,
                    onUndo = {
                        undo = null
                        if (!farmer.restored(last.task, last.index, if (last.harvested) "harvest" else "remove")) {
                            Toast.makeText(context, "Your field is full, so it can't go back.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGone = { if (undo === last) undo = null },
                )
            }
            // Always in reach at the foot of the screen, whatever is above it.
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { buttons(::plant, reap != null, celebration) }
        }

        tasks.firstOrNull { it.id == openId }?.let { task ->
            val onRemove: () -> Unit = { remove(task) }
            val onWater = { water(task, "sheet") }.takeUnless { FarmRules.isWatered(task, now) }
            if (overFeed) {
                InScreenPlotSheet(task, now, onHarvest = { harvest(task) }, onWater = onWater, onRemove = onRemove, onDismiss = { openId = null })
            } else {
                PlotSheet(task, now, onHarvest = { harvest(task) }, onWater = onWater, onRemove = onRemove, onDismiss = { openId = null })
            }
        }
    }
}

/** Widens the content by [by] on each side, over the screen's side padding. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedWidth) by.roundToPx() * 2 else 0
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** What Undo would take back: [task], harvested or removed from its plot ([index]). */
class Undo(val task: Task, val index: Int, val harvested: Boolean)

/** "Harvested · Pay the rent", with Undo, for a few seconds after a harvest or a removal; then [onGone]. */
@Composable
private fun UndoBar(undo: Undo, onUndo: () -> Unit, onGone: () -> Unit) {
    // Longer when a screen reader or the user's accessibility timeout asks for it.
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(undo) {
        delay(accessibility?.calculateRecommendedTimeoutMillis(UNDO_MS, containsIcons = false, containsText = true, containsControls = true) ?: UNDO_MS)
        onGone()
    }
    Snackbar(
        action = { TextButton(onClick = onUndo) { Text("Undo", fontWeight = FontWeight.ExtraBold, color = Leaf) } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = (if (undo.harvested) "Harvested · " else "Removed · ") + undo.task.title,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How long the all-watered sparkles and the button's pulse take. */
private const val CELEBRATION_MS = 1800L

/** How long Undo stays offered. */
private const val UNDO_MS = 5000L
