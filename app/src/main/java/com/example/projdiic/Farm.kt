package com.example.projdiic

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
}

/**
 * The farm: the title, the field and a card per crop, a crop's sheet (Harvest it or Remove it) and planting; the same
 * in the app and over Instagram. [header] and [footer] add what only one of them has, above and under the field, and
 * [buttons] what stays at the foot of the screen, given a way to start planting. Over Instagram ([overFeed]) the crop's
 * sheet is drawn in the screen, as an overlay window can't open a dialog of its own.
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
    buttons: @Composable ColumnScope.(plant: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var planting by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var reap by remember { mutableStateOf<Reap?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Not while a crop is being harvested: its Harvest it would wait for nothing.
    fun open(task: Task) {
        if (reap == null) openId = task.id
    }

    // Harvest it from the crop's sheet: the crop ripens if it must and flies out of its bed on the field; then it's done.
    fun harvest(task: Task) {
        if (reap != null) return
        openId = null
        val at = System.currentTimeMillis()
        val crop = FarmRules.crop(task)
        val stage = CropMotion.harvestedAs(crop, FarmRules.stage(task, at))
        val ripe = FarmRules.isReady(task, at)
        reap = Reap(task.id, crop, stage, frame = -1)
        // The harvest plays on the field: bring it into view from a card further down.
        scope.launch { scroll.animateScrollTo(0) }
        scope.launch {
            try {
                delay(FarmViews.RIPEN_MS)
                for (frame in CropMotion.harvest(crop, stage).indices) {
                    reap = Reap(task.id, crop, stage, frame)
                    delay(1000L / CropMotion.FPS)
                }
            } finally {
                // Leaving the screen mid-harvest still harvests it, as on the pause.
                reap = null
                farmer.harvested(task, ripe)
                Toast.makeText(context, R.string.toast_done, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Field(
                        tasks = tasks,
                        now = now,
                        reap = reap,
                        onOpen = ::open,
                        onPlant = { planting = true },
                        modifier = Modifier.bleed(20.dp),
                    )
                    CropCards(tasks = tasks, now = now, onOpen = ::open)
                }
                footer()
            }
            // Always in reach at the foot of the screen, whatever is above it.
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { buttons { planting = true } }
        }

        tasks.firstOrNull { it.id == openId }?.let { task ->
            val onRemove = {
                farmer.removed(task)
                openId = null
            }
            if (overFeed) {
                InScreenPlotSheet(task, now, onHarvest = { harvest(task) }, onRemove = onRemove, onDismiss = { openId = null })
            } else {
                PlotSheet(task, now, onHarvest = { harvest(task) }, onRemove = onRemove, onDismiss = { openId = null })
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
