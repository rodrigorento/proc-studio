package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * The field as it looks at one moment: whether it is watered (and how far a watering has got), a rain frame while it
 * rains, and the can while it pours over one bed.
 */
class FieldLook(val wet: Boolean, val rainFrame: Int? = null, val wetness: Float = if (wet) 1f else 0f, val pour: Pour? = null)

/** The can pouring over [taskId]'s bed, at [frame] of [CropMotion.water]; [wasDry] when it is watering a dry field. */
class Pour(val taskId: String, val frame: Int, val wasDry: Boolean)

/**
 * The home screen's field: one lawn with four plots, back to front, a task's bed in each planted one and pegs and
 * string marking out the empty ones. Tapping a bed waters it ([onWater]); tapping an empty plot plants ([onPlant]).
 * The crop cards under it name the crops and open them.
 */
@Composable
fun Field(tasks: List<Task>, now: Long, look: FieldLook, onWater: (Task) -> Unit, onPlant: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val planted by rememberUpdatedState(tasks)
    val water by rememberUpdatedState(onWater)
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
                        planted.getOrNull(plot)?.let(water) ?: plant()
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
                    if (task == null) {
                        Layer(FieldArt.MARKED_PLOT)
                    } else {
                        Bed({ wet -> CropArt.bed(FarmRules.crop(task), FarmRules.stage(task, now), wet) }, look)
                    }
                }
            }
            // Rain and the can after all the beds, so no bed in front cuts them off; rain falls on empty plots too.
            look.rainFrame?.let { frame ->
                FieldArt.PLOTS.forEach { plot -> Box(Modifier.over(plot.card)) { Layer(CropMotion.rain()[frame]) } }
            }
            look.pour?.let { pour ->
                val plot = tasks.indexOfFirst { it.id == pour.taskId }
                if (plot in FieldArt.PLOTS.indices) Box(Modifier.over(FieldArt.PLOTS[plot].card)) { Layer(CropMotion.water()[pour.frame]) }
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
                                if (!FarmRules.isReady(task, now)) onClick("Water it") { water(task); true }
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
fun CropCards(tasks: List<Task>, now: Long, look: FieldLook, onOpen: (Task) -> Unit) {
    val next = TaskRules.next(tasks, now)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (listOfNotNull(next) + tasks.filter { it.id != next?.id }).forEach { task ->
            CropCard(task, task.id == next?.id, now, look) { onOpen(task) }
        }
    }
}

@Composable
private fun CropCard(task: Task, isNext: Boolean, now: Long, look: FieldLook, onOpen: () -> Unit) {
    val context = LocalContext.current
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = if (isNext) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClickLabel = "Change it or pull it up", role = Role.Button, onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp)) { Layer(CropArt.bed(crop, stage, look.wet)) }
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

/** A bed image: dry fading into wet while it is watered. */
@Composable
private fun Bed(image: (wet: Boolean) -> Int, look: FieldLook) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Layer(image(look.wetness >= 1f))
        if (look.wetness > 0f && look.wetness < 1f) Layer(image(true), look.wetness)
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
 * A planted task up close: its bed and how it's growing, then two ways to change it. **Change crop** shows the seed
 * packets, one per time ([onWhen] replants it); **Pull it up** removes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotSheet(task: Task, now: Long, look: FieldLook, onWhen: (TimeWindow?) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    var changing by rememberSaveable(task.id) { mutableStateOf(false) }
    // Never half open: Back, or a swipe down, closes it.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp)) { Layer(CropArt.bed(crop, stage, look.wet)) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(task.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = listOfNotNull(cropWords(crop, stage), statusFor(context, task, now) ?: harvestLine(context, task, now))
                            .joinToString(" · "),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (changing) {
                SeedChoices(task, now, onPick = { window -> onWhen(window); onDismiss() }, onKeep = { changing = false })
            } else {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { changing = true },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Change crop", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
                Button(
                    onClick = onRemove,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Pull it up", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            }
        }
    }
}

/** The seed packets for a new time, the task's own one marked; picking it again, or Back, keeps the crop ([onKeep]). */
@Composable
private fun SeedChoices(task: Task, now: Long, onPick: (TimeWindow?) -> Unit, onKeep: () -> Unit) {
    val context = LocalContext.current
    val formats = remember { whenFormats(context) }
    val zone = TimeZone.getDefault()
    val window = TaskRules.window(task)
    // A time that was said rather than picked ("Tomorrow 15:00") stays on offer, first.
    val packets = SeedPackets.forTask(window?.takeIf { it.key == WhenChoices.CUSTOM }, now, zone)
    val current = packets.indexOfFirst { (it.window?.key ?: "") == task.whenKey && (it.window?.key != WhenChoices.CUSTOM || it.window == window) }
    Text("Pick new seeds", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    Text(
        "Each time grows its own crop. Changing it replants the bed.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        packets.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    val packet = packets[i]
                    PacketCard(packet, WhenText.label(packet.window, now, zone, formats), i == current, {
                        if (i == current) onKeep() else onPick(packet.window)
                    }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    TextButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

private fun harvestLine(context: Context, task: Task, now: Long): String =
    FarmViews.harvestLine(task, now, TimeZone.getDefault(), whenFormats(context))

/** "Resting till 15:00" while the task is put off, otherwise null. */
private fun statusFor(context: Context, task: Task, now: Long): String? =
    if (task.deferredBy != Task.BY_WHEN && task.deferredUntil > now) {
        "Resting till " + DateFormat.getTimeFormat(context).format(Date(task.deferredUntil))
    } else null

private fun cropWords(crop: String, stage: String): String = FarmViews.cropAndStage(crop, stage).replaceFirstChar { it.uppercaseChar() }
