package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.Danger
import com.example.projdiic.ui.theme.Ink
import com.example.projdiic.ui.theme.Leaf
import com.example.projdiic.ui.theme.Water
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The share of the lawn image's height the field gives back to the screen above it: the top of the back plot's card,
 * left free for tall crops. The tallest (a sunflower in bloom, up to 0.053) then reaches a little into the gap above
 * the field, still clear of what is above that.
 */
private const val SKY = 0.1f

/**
 * [taskId]'s crop being harvested on the field: ripening into [stage] while [frame] is below 0, then flying out of its
 * bed ([wet] when watered today) at [frame] of [CropMotion.harvest].
 */
class Reap(val taskId: String, val crop: String, val stage: String, val frame: Int, val wet: Boolean)

/**
 * The home screen's field: one lawn with four plots, back to front, a task's bed in each planted one and pegs and
 * string marking out the empty ones. Tapping a dry bed waters it ([onWater]), a watered one opens its crop
 * ([onOpen]); tapping an empty plot plants ([onPlant]). While [reap] is on, that crop is harvested on its bed; each
 * bed in [splashes] is being watered (the can's frame).
 */
@Composable
fun Field(
    tasks: List<Task>,
    now: Long,
    reap: Reap?,
    splashes: Map<String, Int>,
    onOpen: (Task) -> Unit,
    onWater: (task: Task, via: String) -> Unit,
    onPlant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val planted by rememberUpdatedState(tasks)
    val open by rememberUpdatedState(onOpen)
    val water by rememberUpdatedState(onWater)
    val plant by rememberUpdatedState(onPlant)
    val time by rememberUpdatedState(now)
    val watering by rememberUpdatedState(splashes)
    // A dry bed waters; a watered one opens. One being watered takes no taps while the can pours.
    fun tapped(task: Task) {
        when {
            task.id in watering -> Unit
            FarmRules.isWatered(task, time) -> open(task)
            else -> water(task, "bed")
        }
    }
    // Each bed's picture, to tell which bed a tap is on: a tall crop in front covers the bed behind it.
    val pictures by rememberUpdatedState(
        tasks.take(FieldArt.PLOTS.size).map { ImageBitmap.imageResource(FarmViews.bed(it, now)) }
    )
    // The art is laid out left to right; mirrored for a right-to-left language, it would no longer line up.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier
                // Taps only within the field's own box: the strip given back to the screen above isn't the field's.
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val full = size.height / (1f - SKY)
                        val plot = FieldPlots.at(at.x / size.width, (at.y + full * SKY) / full, planted.size) { i, u, v ->
                            pictures.getOrNull(i)?.let { solid(it, u, v) } ?: false
                        } ?: return@detectTapGestures
                        planted.getOrNull(plot)?.let(::tapped) ?: plant()
                    }
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val sky = (placeable.height * SKY).roundToInt()
                    layout(placeable.width, placeable.height - sky) { placeable.place(0, -sky) }
                }
                .fillMaxWidth()
                .aspectRatio(FieldArt.ASPECT)
        ) {
            val width = maxWidth
            val height = maxHeight
            fun Modifier.over(box: FieldArt.Box) = offset(width * box.left, height * box.top).size(width * box.width, height * box.height)

            Layer(FieldArt.MEADOW)
            // Every shadow first, so none falls on a bed in front of the one casting it.
            FieldArt.PLOTS.take(tasks.size).forEach { plot -> Box(Modifier.over(plot.shadow)) { Layer(FieldArt.BED_SHADOW) } }
            FieldArt.PLOTS.forEachIndexed { i, plot ->
                val task = tasks.getOrNull(i)
                Box(Modifier.over(plot.card)) {
                    val splash = task?.let { splashes[it.id] }
                    when {
                        task == null -> Layer(FieldArt.MARKED_PLOT)
                        reap?.taskId == task.id -> Reaping(FarmViews.bed(task, now), reap)
                        splash != null -> Wetting(task, now, splash)
                        else -> Layer(FarmViews.bed(task, now))
                    }
                }
            }
            // The harvest after all the beds, so no bed in front cuts off the crop flying up.
            reap?.takeIf { it.frame >= 0 }?.let { reap ->
                val plot = tasks.indexOfFirst { it.id == reap.taskId }
                if (plot in FieldArt.PLOTS.indices) {
                    Box(Modifier.over(FieldArt.PLOTS[plot].card)) { Layer(CropMotion.harvest(reap.crop, reap.stage)[reap.frame]) }
                }
            }
            // The can over each bed being watered, after all the beds so none in front cuts it off.
            val can = CropMotion.water()
            FieldArt.PLOTS.forEachIndexed { i, plot ->
                val task = tasks.getOrNull(i) ?: return@forEachIndexed
                val frame = splashes[task.id]?.takeIf { reap?.taskId != task.id } ?: return@forEachIndexed
                Box(Modifier.over(plot.card)) { Layer(can[frame]) }
            }
            // Screen readers hear the latest watering from one node that stays put: a live region speaks when it
            // changes, not when it first appears.
            val latest = splashes.keys.lastOrNull()
            val heard = tasks.firstOrNull { it.id == latest }?.let { "${it.title} watered. " + FarmViews.wateredLine(it, now, TimeZone.getDefault(), whenFormats(context)) }
            Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = heard.orEmpty() })
            // For screen readers, one node per plot with its action; they take no touches, the field handles those.
            FieldArt.PLOTS.forEachIndexed { i, plot ->
                val task = tasks.getOrNull(i)
                val rise = if (task == null) 0f else FieldArt.TAP_HEIGHT
                Box(
                    Modifier
                        .offset(width * (plot.middle.x - FieldArt.FOOTPRINT_HALF_WIDTH), height * (plot.middle.y - FieldArt.FOOTPRINT_HALF_HEIGHT - rise))
                        .size(width * FieldArt.FOOTPRINT_HALF_WIDTH * 2, height * (FieldArt.FOOTPRINT_HALF_HEIGHT * 2 + rise))
                        .semantics {
                            role = Role.Button
                            if (task == null) {
                                contentDescription = "Empty plot"
                                onClick("Plant a task") { plant(); true }
                            } else {
                                val dry = !FarmRules.isWatered(task, now) && task.id !in splashes
                                contentDescription = listOfNotNull(
                                    task.title,
                                    FarmViews.cropAndStage(FarmRules.crop(task), FarmRules.stage(task, now)),
                                    statusFor(context, task, now) ?: harvestLine(context, task, now),
                                    "needs water".takeIf { dry },
                                ).joinToString(", ")
                                if (dry) onClick("Water it") { water(task, "screen_reader"); true } else onClick("Open it") { open(task); true }
                            }
                        }
                )
            }
        }
    }
}

/** Whether [picture] is drawn at ([u], [v]), from (0, 0) at its top left to (1, 1). */
private fun solid(picture: ImageBitmap, u: Float, v: Float): Boolean {
    val bitmap = picture.asAndroidBitmap()
    val x = (u * (bitmap.width - 1)).roundToInt()
    val y = (v * (bitmap.height - 1)).roundToInt()
    return android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 64
}

/**
 * Every crop in the field as a card, in the order they come up: its bed, its name and when to harvest it. Tapping a card
 * opens the crop ([onOpen]); swiping it away either way, as in Gmail, does what its sheet offers ([onSwiped]: Harvest
 * it on a due crop, Remove it otherwise) once it has slid off and its gap has closed.
 */
@Composable
fun CropCards(
    tasks: List<Task>,
    now: Long,
    harvesting: Boolean,
    onSwiped: (task: Task, harvests: Boolean) -> Boolean,
    onOpen: (Task) -> Unit,
) {
    Column {
        TaskRules.inOrder(tasks, now).forEach { task ->
            key(task.id) {
                SwipeableCropCard(task, now, enabled = !harvesting, onSwiped = { onSwiped(task, it) }, onOpen = { onOpen(task) })
            }
        }
    }
}

/**
 * A card that swipes away: [onSwiped] (given whether it harvests) once it has slid off and its gap has closed; false
 * puts it back. Not while a harvest plays ([enabled]): the beds would move under it.
 */
@Composable
private fun SwipeableCropCard(task: Task, now: Long, enabled: Boolean, onSwiped: (harvests: Boolean) -> Boolean, onOpen: () -> Unit) {
    // A resting crop (on its 5 more minutes) isn't offered a harvest, as on the gate.
    val harvests = FarmRules.isDue(task, now) && !resting(task, now)
    val state = rememberSwipeToDismissBoxState()
    // What the swipe showed behind the card, kept from the moment it went: that's what happens.
    var gone by remember { mutableStateOf<Boolean?>(null) }
    var done by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onSwiped)
    // Slid off: the gap closes, then it happens; if it can't, the card comes back.
    LaunchedEffect(gone) {
        val went = gone ?: return@LaunchedEffect
        delay(COLLAPSE_MS.toLong())
        done = true
        if (!latest(went)) {
            done = false
            gone = null
            state.reset()
        }
    }
    // The screen going mid-collapse (Back to the feed over Instagram) still does it.
    DisposableEffect(Unit) {
        onDispose { gone?.let { if (!done) latest(it) } }
    }
    AnimatedVisibility(
        visible = gone == null,
        exit = shrinkVertically(tween(COLLAPSE_MS)) + fadeOut(tween(COLLAPSE_MS)),
    ) {
        SwipeToDismissBox(
            state = state,
            gesturesEnabled = enabled,
            onDismiss = { gone = harvests },
            backgroundContent = { SwipeBackground(state, harvests) },
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            CropCard(
                task, now, onOpen,
                Modifier.semantics {
                    if (enabled) customActions = listOf(CustomAccessibilityAction(if (harvests) "Harvest it" else "Remove it") { gone = harvests; true })
                },
            )
        }
    }
}

/** What a swipe on a card does, behind it: green with the basket to harvest, red with the pulled sprout to remove. */
@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState, harvests: Boolean) {
    val direction = state.dismissDirection
    if (direction == SwipeToDismissBoxValue.Settled) return
    // Past the point where letting go does it, the picture grows, as Gmail's icon does.
    val armed = state.targetValue != SwipeToDismissBoxValue.Settled
    val grow by animateFloatAsState(if (armed) 1.15f else 0.85f, label = "swipe icon")
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(if (harvests) Leaf else Danger)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
    ) {
        Image(
            painterResource(if (harvests) R.drawable.prop_basket else R.drawable.prop_pulled),
            contentDescription = null,
            modifier = Modifier.size(40.dp).graphicsLayer(scaleX = grow, scaleY = grow)
        )
        Spacer(Modifier.width(10.dp))
        Text(if (harvests) "Harvest" else "Remove", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

/** How long a swiped card's gap takes to close. */
private const val COLLAPSE_MS = 220

@Composable
private fun CropCard(task: Task, now: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    val line = statusFor(context, task, now) ?: harvestLine(context, task, now)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClickLabel = "Open it", role = Role.Button, onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp)) { Layer(FarmViews.bed(task, now)) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (FarmRules.isDue(task, now) && !resting(task, now)) {
                    // Harvest now, Harvest by tonight: a green pill with the basket, so it can't be missed.
                    Row(
                        Modifier
                            .padding(top = 4.dp)
                            .background(Leaf, RoundedCornerShape(50))
                            .padding(start = 6.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.prop_basket), contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(line, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    }
                } else {
                    Text(line, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!FarmRules.isWatered(task, now)) {
                    Text("Needs water", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Water)
                }
            }
        }
    }
}

/** A dry bed turning wet as the can pours over it, at [frame] of [CropMotion.water]. */
@Composable
private fun Wetting(task: Task, now: Long, frame: Int) {
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    Layer(CropArt.bed(crop, stage, false))
    Layer(CropArt.bed(crop, stage, true), ((frame - CropMotion.WET_FROM).toFloat() / (CropMotion.WET_TO - CropMotion.WET_FROM)).coerceIn(0f, 1f))
}

/** A bed being harvested: [bed] ripening into the crop the harvest starts from, then empty as the crop flies out. */
@Composable
private fun Reaping(@DrawableRes bed: Int, reap: Reap) {
    val ripe = remember(reap.taskId) { Animatable(0f) }
    LaunchedEffect(reap.taskId) { ripe.animateTo(1f, tween(FarmViews.RIPEN_MS.toInt())) }
    if (reap.frame >= 0) {
        Layer(CropArt.emptyBed(reap.wet))
    } else {
        Layer(bed)
        Layer(CropArt.bed(reap.crop, reap.stage, reap.wet), ripe.value)
    }
}

@Composable
internal fun Layer(@DrawableRes image: Int, alpha: Float = 1f) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().alpha(alpha)
    )
}

/** Starts planting a task ([PlantFlow]), at the foot of the screen; a full field says so instead. */
@Composable
fun PlantButton(full: Boolean, onPlant: () -> Unit, modifier: Modifier = Modifier) {
    if (full) {
        Text(
            text = "Your field is full. Harvest one to plant another.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }
    Button(onClick = onPlant, shape = RoundedCornerShape(50), modifier = modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Image(painterResource(R.drawable.prop_seed_carrot), contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Text("Plant a task", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/**
 * A planted task up close: its bed and how it's growing. A due crop ([FarmRules.isDue]) offers Harvest it
 * ([onHarvest]), which plays on the field; one not due yet offers Remove it ([onRemove]) instead. A crop still dry
 * today offers Water it too ([onWater], null once watered), which also plays on the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotSheet(task: Task, now: Long, onHarvest: () -> Unit, onWater: (() -> Unit)?, onRemove: () -> Unit, onDismiss: () -> Unit) {
    // Never half open: Back, or a swipe down, closes it.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PlotSheetContent(task, now, onHarvest, onWater, onRemove)
    }
}

/**
 * [PlotSheet] drawn in the screen, over a dimmed farm, for the farm over Instagram: an overlay window can't open the
 * dialog a [ModalBottomSheet] needs. A tap on the dim, or Back, closes it.
 */
@Composable
fun InScreenPlotSheet(task: Task, now: Long, onHarvest: () -> Unit, onWater: (() -> Unit)?, onRemove: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClickLabel = "Close", onClick = onDismiss)
        )
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Box(Modifier.padding(top = 24.dp)) { PlotSheetContent(task, now, onHarvest, onWater, onRemove) }
        }
    }
}

@Composable
private fun PlotSheetContent(task: Task, now: Long, onHarvest: () -> Unit, onWater: (() -> Unit)?, onRemove: () -> Unit) {
    val context = LocalContext.current
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    val words = cropWords(crop, stage)
    // "Sunflower, in bloom", not "Sunflower, in bloom · In bloom".
    val line = (statusFor(context, task, now) ?: harvestLine(context, task, now)).takeUnless { words.contains(it, ignoreCase = true) }
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp)) { Layer(FarmViews.bed(task, now)) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(task.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = listOfNotNull(words, line).joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (FarmRules.isDue(task, now)) {
            Button(
                onClick = onHarvest,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Image(painterResource(R.drawable.prop_basket), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("Harvest it", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        if (onWater != null) {
            OutlinedButton(
                onClick = onWater,
                shape = RoundedCornerShape(50),
                border = BorderStroke(2.dp, Water),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Water),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Image(painterResource(R.drawable.prop_watering_can), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("Water it", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        if (!FarmRules.isDue(task, now)) {
            OutlinedButton(
                onClick = onRemove,
                shape = RoundedCornerShape(50),
                border = BorderStroke(2.dp, Danger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Image(painterResource(R.drawable.prop_pulled), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("Remove it", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
    }
}

private fun harvestLine(context: Context, task: Task, now: Long): String =
    FarmViews.harvestLine(task, now, TimeZone.getDefault(), whenFormats(context))

/** On its 5 more minutes (or otherwise put off): its card says "Resting till 15:00". */
private fun resting(task: Task, now: Long): Boolean = task.deferredBy != Task.BY_WHEN && task.deferredUntil > now

/** "Resting till 15:00" while the task is put off, otherwise null. */
private fun statusFor(context: Context, task: Task, now: Long): String? =
    if (resting(task, now)) {
        "Resting till " + DateFormat.getTimeFormat(context).format(Date(task.deferredUntil))
    } else null

private fun cropWords(crop: String, stage: String): String = FarmViews.cropAndStage(crop, stage).replaceFirstChar { it.uppercaseChar() }
