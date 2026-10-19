package com.example.projdiic

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * A look at the farm's rendered beds before the farm screens exist: a 2x2 field like the watering
 * screen will show, and every stage of each crop, in any light, watered or dry.
 */
@Composable
fun FarmArtPreview() {
    var light by rememberSaveable { mutableStateOf(CropArt.lightAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) }
    var wet by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropArt.LIGHTS.forEach {
                FilterChip(selected = it == light, onClick = { light = it }, label = { Text(it.label()) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Watered", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = wet, onCheckedChange = { wet = it })
        }

        val field = listOf(
            CropArt.bed("carrot", "ripe", wet, light) to "Carrot, ripe",
            CropArt.bed("carrot", "growing", wet, light) to "Carrot, growing",
            CropArt.bed("carrot", "sprout", wet, light) to "Carrot, sprout",
            CropArt.emptyBed(wet, light) to "Empty bed",
        )
        field.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (image, label) -> BedCard(image, label, Modifier.weight(1f)) }
            }
        }

        CropArt.STAGES.forEach { (crop, stages) ->
            Text("Every ${crop} stage", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { stage ->
                    BedCard(CropArt.bed(crop, stage, wet, light), "${crop.label()}, $stage", Modifier.size(120.dp))
                }
            }
        }
    }
}

@Composable
private fun BedCard(@DrawableRes image: Int, label: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.aspectRatio(1f)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(4.dp)
        )
    }
}

private fun String.label() = replaceFirstChar { it.uppercaseChar() }
