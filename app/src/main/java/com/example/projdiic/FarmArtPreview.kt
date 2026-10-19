package com.example.projdiic

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A crop growing in one of the field's beds. */
private data class Plot(val crop: String, val stage: String)

/** The field shown in the preview: ripe crops, one still growing (it ripens first) and one left too long. */
private val FIELD = listOf(
    Plot("radish", "ripe"),
    Plot("lettuce", "growing"),
    Plot("carrot", "ripe"),
    Plot("pumpkin", "overripe"),
)

/** What is playing on the field. */
private enum class Moment { WATER, RAIN, HARVEST, PLANT }

/**
 * A look at the farm's rendered beds before the farm screens exist: a 2x2 field like the watering screen will
 * show, which can be watered (by can or rain), harvested and planted again, and every stage of each crop, watered
 * or dry.
 */
@Composable
fun FarmArtPreview() {
    var wet by rememberSaveable { mutableStateOf(true) }
    var harvested by rememberSaveable { mutableStateOf(false) }
    var planted by rememberSaveable { mutableStateOf(false) }
    var moment by remember { mutableStateOf<Moment?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    var ripening by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(moment) {
        val playing = moment ?: return@LaunchedEffect
        val frames = when (playing) {
            Moment.WATER -> CropMotion.water().size
            Moment.RAIN -> CropMotion.rain().size
            Moment.HARVEST -> {
                animate(0f, 1f, animationSpec = tween(RIPEN_MILLIS)) { value, _ -> ripening = value }
                wet = true  // harvests play on a watered bed, and leave it so
                CropMotion.harvest("radish", "ripe").size
            }
            Moment.PLANT -> CropMotion.plant("radish").size
        }
        for (f in 0 until frames) {
            frame = f
            delay(1000L / CropMotion.FPS)
        }
        when (playing) {
            Moment.HARVEST -> harvested = true
            Moment.PLANT -> {
                harvested = false
                planted = true
            }
            else -> wet = true
        }
        moment = null
        frame = 0
        ripening = 0f
    }

    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Watered", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = wet, onCheckedChange = { wet = it }, enabled = moment == null)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val idle = moment == null
            OutlinedButton(onClick = { wet = false; moment = Moment.WATER }, enabled = idle) { Text("Water") }
            OutlinedButton(onClick = { wet = false; moment = Moment.RAIN }, enabled = idle) { Text("Rain") }
            OutlinedButton(onClick = { moment = Moment.HARVEST }, enabled = idle && !harvested) { Text("Harvest") }
            OutlinedButton(onClick = { moment = Moment.PLANT }, enabled = idle && harvested) { Text("Plant") }
            OutlinedButton(onClick = { harvested = false; planted = false; wet = false }, enabled = idle) { Text("Reset") }
        }

        FIELD.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { plot ->
                    PlotCard(if (planted) plot.copy(stage = "seed") else plot, wet, harvested, moment, frame, ripening, Modifier.weight(1f))
                }
            }
        }

        Text("Props", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val props = listOf(R.drawable.prop_watering_can to "Watering can") +
                CropArt.STAGES.keys.map { CropArt.seedPacket(it) to "${it.label()} seeds" } +
                listOf(
                    R.drawable.prop_basket to "Harvest basket",
                    R.drawable.prop_gate to "Gate, closed until tomorrow",
                    R.drawable.prop_mailbox to "Mailbox",
                )
            props.forEach { (image, label) -> BedCard(Modifier.size(96.dp)) { Layer(image, label) } }
        }

        CropArt.STAGES.forEach { (crop, stages) ->
            Text("Every ${crop} stage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { stage ->
                    BedCard(Modifier.size(120.dp)) { Layer(CropArt.bed(crop, stage, wet), "${crop.label()}, $stage") }
                }
            }
        }
    }
}

/** How long a crop that isn't ready takes to ripen before its harvest. */
private const val RIPEN_MILLIS = 300

/**
 * One bed of the field, at rest or in the middle of a moment. Watering fades the dry card into the wet one under
 * the can or the rain; a harvest first ripens a crop that isn't ready (on a watered bed), then plays the crop
 * flying out over the empty bed; planting pours the crop's seed packet into the empty bed as it fades into the
 * seed card.
 */
@Composable
private fun PlotCard(
    plot: Plot,
    wet: Boolean,
    harvested: Boolean,
    moment: Moment?,
    frame: Int,
    ripening: Float,
    modifier: Modifier,
) {
    val label = if (harvested) "Empty bed" else "${plot.crop.label()}, ${plot.stage}"
    fun card(watered: Boolean) =
        if (harvested) CropArt.emptyBed(watered) else CropArt.bed(plot.crop, plot.stage, watered)
    BedCard(modifier) {
        when {
            moment == Moment.WATER || moment == Moment.RAIN -> {
                val wetness = (frame - CropMotion.WET_FROM).toFloat() / (CropMotion.WET_TO - CropMotion.WET_FROM)
                Layer(card(false), label)
                Layer(card(true), null, wetness.coerceIn(0f, 1f))
                val frames = if (moment == Moment.WATER) CropMotion.water() else CropMotion.rain()
                Layer(frames[frame], null)
            }
            moment == Moment.PLANT -> {
                val seeded = (frame - CropMotion.SEED_FROM).toFloat() / (CropMotion.SEED_TO - CropMotion.SEED_FROM)
                Layer(CropArt.emptyBed(wet), label)
                Layer(CropArt.bed(plot.crop, "seed", wet), null, seeded.coerceIn(0f, 1f))
                Layer(CropMotion.plant(plot.crop)[frame], null)
            }
            harvested -> Layer(card(wet), label)
            moment == Moment.HARVEST -> {
                val stage = CropMotion.harvestedAs(plot.crop, plot.stage)
                if (ripening < 1f) {
                    Layer(CropArt.bed(plot.crop, plot.stage, wet), label)
                    Layer(CropArt.bed(plot.crop, stage, true), null, ripening)
                } else {
                    Layer(CropArt.emptyBed(true), label)
                    Layer(CropMotion.harvest(plot.crop, stage)[frame], null)
                }
            }
            else -> Layer(card(wet), label)
        }
    }
}

@Composable
private fun BedCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(Modifier.padding(4.dp)) { content() }
    }
}

/** One image of a card's stack, scaled to fit it: the still cards and the animation frames share their framing. */
@Composable
private fun Layer(@DrawableRes image: Int, description: String?, alpha: Float = 1f) {
    Image(
        painter = painterResource(image),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().alpha(alpha)
    )
}

private fun String.label() = replaceFirstChar { it.uppercaseChar() }
