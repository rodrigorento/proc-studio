package com.example.projdiic

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.LeafContainer
import com.example.projdiic.ui.theme.Water
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** The field as it looks at one moment: the light, whether it is watered, and a rain frame while it rains. */
class FieldLook(val light: String, val wet: Boolean, val rainFrame: Int? = null, val wetness: Float = if (wet) 1f else 0f)

fun fieldLight(now: Long): String =
    CropArt.lightAt(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY))

/**
 * The home screen's field: one plot per task, empty plots to plant in, up to [TaskRules.MAX_TASKS].
 * Tapping a planted plot opens [onOpen]; an empty one, [onPlant].
 */
@Composable
fun Field(tasks: List<Task>, now: Long, look: FieldLook, onOpen: (Task) -> Unit, onPlant: () -> Unit) {
    val next = TaskRules.next(tasks, now)
    val plots: List<Task?> = tasks + List(TaskRules.MAX_TASKS - tasks.size) { null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plots.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { task ->
                    val modifier = Modifier.weight(1f)
                    if (task == null) {
                        EmptyPlot(look, onPlant, modifier)
                    } else {
                        PlotCard(task, task.id == next?.id, now, look, { onOpen(task) }, modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotCard(task: Task, isNext: Boolean, now: Long, look: FieldLook, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val label = whenLabel(context, task, now)
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .clickable(onClickLabel = "Change or remove", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${task.title}, $label, $crop ${FarmViews.stageWords(stage)}" + if (isNext) ", next up" else ""
            }
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Bed({ wet -> CropArt.bed(crop, stage, wet, look.light) }, look)
                if (isNext) {
                    Text(
                        text = "Next up",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = task.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusFor(context, task, now) ?: label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(LeafContainer, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun EmptyPlot(look: FieldLook, onPlant: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.clickable(onClickLabel = "Plant a task", role = Role.Button, onClick = onPlant)
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.alpha(0.55f)) { Bed({ wet -> CropArt.emptyBed(wet, look.light) }, look) }
            Text(
                text = "+ Plant a task",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, bottom = 22.dp)
            )
        }
    }
}

/** A bed image in the field's light: dry fading into wet while it is watered, with the rain falling over it. */
@Composable
private fun Bed(image: (wet: Boolean) -> Int, look: FieldLook) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Layer(image(look.wetness >= 1f))
        if (look.wetness > 0f && look.wetness < 1f) Layer(image(true), look.wetness)
        look.rainFrame?.let { Layer(CropMotion.rain(look.light)[it]) }
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

/** Whether the field was watered today, with the can's picture. */
@Composable
fun WateringRow(watered: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.prop_watering_can), contentDescription = null, modifier = Modifier.size(52.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = if (watered) "Watered today" else "Thirsty",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (watered) Water else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (watered) "It dries out overnight." else "You'll water it at your next pause in Instagram.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Starts planting a task ([PlantFlow]); a full field says so instead. */
@Composable
fun PlantButton(full: Boolean, onPlant: () -> Unit) {
    if (full) {
        Text(
            text = "Your field is full. Finish one to plant another.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }
    Button(onClick = onPlant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Image(painterResource(R.drawable.prop_seed_carrot), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("Plant a task", fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** A planted task up close: when it is for, and a way to remove it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlotSheet(task: Task, now: Long, look: FieldLook, onWhen: (TimeWindow?) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val formats = remember { whenFormats(context) }
    val window = TaskRules.window(task)
    val crop = FarmRules.crop(task)
    val stage = FarmRules.stage(task, now)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp)) { Layer(CropArt.bed(crop, stage, look.wet, look.light)) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(task.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = listOfNotNull(cropWords(crop, stage), statusFor(context, task, now), if (task.snoozeUsed) "snooze used" else null)
                            .joinToString(" · "),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("When is it for?", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val options = WhenChoices.chips(now).toMutableList<TimeWindow?>()
            // What was said that isn't one of the chips ("Tomorrow 15:00") stays on offer, first so it's in view.
            if (window != null && window.key == WhenChoices.CUSTOM) options.add(0, window)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = (option?.key ?: "") == task.whenKey && (option?.key != WhenChoices.CUSTOM || option == window),
                        onClick = { onWhen(option) },
                        label = { Text(WhenText.label(option, now, TimeZone.getDefault(), formats)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                }
            }
            Text(
                text = "Changing it replants the crop that fits the new time.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onRemove,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Remove from the field", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun whenLabel(context: Context, task: Task, now: Long): String =
    WhenText.label(TaskRules.window(task), now, TimeZone.getDefault(), whenFormats(context))

/** "Back at 15:00" while the task is put off, otherwise null. */
private fun statusFor(context: Context, task: Task, now: Long): String? =
    if (task.deferredBy != Task.BY_WHEN && task.deferredUntil > now) {
        "Back at " + DateFormat.getTimeFormat(context).format(Date(task.deferredUntil))
    } else null

private fun cropWords(crop: String, stage: String): String = crop.replaceFirstChar { it.uppercaseChar() } + ", " + FarmViews.stageWords(stage)
