package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.Danger
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * The share of the lawn image's height the field gives back to the screen above it: the top of the back plot's card,
 * left free for tall crops. The tallest (a sunflower in bloom, up to 0.053) then reaches a little into the gap above
 * the field, still clear of what is above that.
 */
private const val SKY = 0.1f

/**
 * [taskId]'s crop being harvested on the field: ripening into [stage] while [frame] is below 0, then flying out of its
 * bed at [frame] of [CropMotion.harvest].
 */
class Reap(val taskId: String, val crop: String, val stage: String, val frame: Int)

/**
 * The home screen's field: one lawn with four plots, back to front, a task's bed in each planted one and pegs and
 * string marking out the empty ones. Tapping a bed opens its crop ([onOpen]); tapping an empty plot plants
 * ([onPlant]). While [reap] is on, that crop is harvested on its bed.
 */
@Composable
fun Field(tasks: List<Task>, now: Long, reap: Reap?, onOpen: (Task) -> Unit, onPlant: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val planted by rememberUpdatedState(tasks)
    val open by rememberUpdatedState(onOpen)
    val plant by rememberUpdatedState(onPlant)
    // Each bed's picture, to tell which bed a tap is on: a tall crop in front covers the bed behind it.
    val pictures by rememberUpdatedState(
        tasks.take(FieldArt.PLOTS.size).map { ImageBitmap.imageResource(CropArt.bed(FarmRules.crop(it), FarmRules.stage(it, now), true)) }
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
                        planted.getOrNull(plot)?.let(open) ?: plant()
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
                    when {
                        task == null -> Layer(FieldArt.MARKED_PLOT)
                        reap?.taskId == task.id -> Reaping(FarmViews.bed(task, now), reap)
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
                                contentDescription = listOfNotNull(
                                    task.title,
                                    FarmViews.cropAndStage(FarmRules.crop(task), FarmRules.stage(task, now)),
                                    statusFor(context, task, now) ?: harvestLine(context, task, now),
                                ).joinToString(", ")
                                onClick("Open it") { open(task); true }
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

/** Every crop in the field as a card, the one the next pause shows first: its bed, its name and when to harvest it. */
@Composable
fun CropCards(tasks: List<Task>, now: Long, onOpen: (Task) -> Unit) {
    val next = TaskRules.next(tasks, now)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (listOfNotNull(next) + tasks.filter { it.id != next?.id }).forEach { task ->
            CropCard(task, task.id == next?.id, now) { onOpen(task) }
        }
    }
}

@Composable
private fun CropCard(task: Task, isNext: Boolean, now: Long, onOpen: () -> Unit) {
    val context = LocalContext.current
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClickLabel = "Open it", role = Role.Button, onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp)) { Layer(CropArt.bed(crop, stage, true)) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                if (isNext) Text("Next up", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = statusFor(context, task, now) ?: harvestLine(context, task, now),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A bed being harvested: [bed] ripening into the crop the harvest starts from, then empty as the crop flies out. */
@Composable
private fun Reaping(@DrawableRes bed: Int, reap: Reap) {
    val ripe = remember(reap.taskId) { Animatable(0f) }
    LaunchedEffect(reap.taskId) { ripe.animateTo(1f, tween(FarmViews.RIPEN_MS.toInt())) }
    if (reap.frame >= 0) {
        Layer(CropArt.emptyBed(true))
    } else {
        Layer(bed)
        Layer(CropArt.bed(reap.crop, reap.stage, true), ripe.value)
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
 * ([onHarvest]), which plays on the field; one not due yet offers Remove it ([onRemove]) instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotSheet(task: Task, now: Long, onHarvest: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    // Never half open: Back, or a swipe down, closes it.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PlotSheetContent(task, now, onHarvest, onRemove)
    }
}

/**
 * [PlotSheet] drawn in the screen, over a dimmed farm, for the farm over Instagram: an overlay window can't open the
 * dialog a [ModalBottomSheet] needs. A tap on the dim, or Back, closes it.
 */
@Composable
fun InScreenPlotSheet(task: Task, now: Long, onHarvest: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
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
            Box(Modifier.padding(top = 24.dp)) { PlotSheetContent(task, now, onHarvest, onRemove) }
        }
    }
}

@Composable
private fun PlotSheetContent(task: Task, now: Long, onHarvest: () -> Unit, onRemove: () -> Unit) {
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
            Box(Modifier.size(112.dp)) { Layer(CropArt.bed(crop, stage, true)) }
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
        } else {
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

/** "Resting till 15:00" while the task is put off, otherwise null. */
private fun statusFor(context: Context, task: Task, now: Long): String? =
    if (task.deferredBy != Task.BY_WHEN && task.deferredUntil > now) {
        "Resting till " + DateFormat.getTimeFormat(context).format(Date(task.deferredUntil))
    } else null

private fun cropWords(crop: String, stage: String): String = FarmViews.cropAndStage(crop, stage).replaceFirstChar { it.uppercaseChar() }
