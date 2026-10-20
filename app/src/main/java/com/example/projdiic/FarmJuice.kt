package com.example.projdiic

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.projdiic.ui.theme.Bark
import com.example.projdiic.ui.theme.Water
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The farm's small touches: a drop over a thirsty bed, a glint on a ripe one, a sparkle when the day's watering is done.
// All of them stay still when Android's "Remove animations" is on.

/** Whether animations are on: Android's "Remove animations" sets the animator scale to 0. */
fun motionOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/**
 * The bob every thirsty drop on the field shares (0 to 1 and back), or null with animations off: one clock for all the
 * drops rather than one each.
 */
@Composable
fun rememberBob(running: Boolean): State<Float>? {
    if (!running) return null
    return rememberInfiniteTransition(label = "drops").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
}

/** A water drop over a dry bed, bobbing with [bob] (still when null), so it's plain which beds to tap. */
@Composable
fun ThirstyDrop(bob: State<Float>?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 18.dp, height = 24.dp)
            .graphicsLayer { translationY = -(bob?.value ?: 0f) * 5.dp.toPx() }
            .drawWithCache {
                val drop = Path().apply {
                    moveTo(size.width / 2, 0f)
                    cubicTo(size.width * 0.95f, size.height * 0.45f, size.width, size.height * 0.62f, size.width / 2, size.height)
                    cubicTo(0f, size.height * 0.62f, size.width * 0.05f, size.height * 0.45f, size.width / 2, 0f)
                    close()
                }
                val outline = Stroke(width = 2.dp.toPx())
                onDrawBehind {
                    drawPath(drop, Water)
                    drawPath(drop, Bark, style = outline)
                    // A highlight, as the toon art has.
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = size.width * 0.12f, center = Offset(size.width * 0.38f, size.height * 0.6f))
                }
            }
    )
}

/** A glint that comes and goes on a ripe crop, every few seconds; [phase] staggers glints on different beds. */
@Composable
fun Glint(phase: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return
    val shine = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400L + phase * 700L)
        while (true) {
            shine.animateTo(1f, tween(260))
            shine.animateTo(0f, tween(360))
            delay(GLINT_EVERY_MS)
        }
    }
    Box(modifier.size(22.dp).graphicsLayer { scaleX = shine.value; scaleY = shine.value; alpha = shine.value }.drawWithCache(starCache))
}

/** Three sparkles that flash once, [delayMs] after [key] changes: the field's moment when the last bed is watered. */
@Composable
fun SparkleBurst(key: Long, delayMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return
    val bursts = remember(key) { List(3) { Animatable(0f) } }
    bursts.forEachIndexed { i, burst ->
        LaunchedEffect(key) {
            delay(delayMs + i * 110L)
            burst.animateTo(1f, tween(220))
            burst.animateTo(0f, tween(380))
        }
    }
    Box(modifier.size(64.dp)) {
        listOf(Offset(0.15f, 0.2f), Offset(0.6f, 0.05f), Offset(0.45f, 0.55f)).forEachIndexed { i, at ->
            // Read in the layer, not here: each frame then only redraws, it doesn't recompose.
            Box(
                Modifier
                    .size(if (i == 1) 26.dp else 20.dp)
                    .graphicsLayer {
                        val burst = bursts[i].value
                        translationX = at.x * 64.dp.toPx()
                        translationY = at.y * 64.dp.toPx()
                        scaleX = burst
                        scaleY = burst
                        alpha = burst
                    }
                    .drawWithCache(starCache)
            )
        }
    }
}

/** A four-pointed sparkle, pale gold with a white heart, as the harvest's sparkles are; its path made once per size. */
private val starCache: CacheDrawScope.() -> DrawResult = {
    val c = Offset(size.width / 2, size.height / 2)
    val long = size.minDimension / 2
    val short = long * 0.22f
    val path = Path().apply {
        moveTo(c.x, c.y - long)
        quadraticTo(c.x + short, c.y - short, c.x + long, c.y)
        quadraticTo(c.x + short, c.y + short, c.x, c.y + long)
        quadraticTo(c.x - short, c.y + short, c.x - long, c.y)
        quadraticTo(c.x - short, c.y - short, c.x, c.y - long)
        close()
    }
    onDrawBehind {
        drawPath(path, Color(0xFFFFF29A))
        drawCircle(Color.White, radius = short * 1.1f, center = c)
    }
}

/** Places the content so that the point ([alignX], [alignY]) of it, as shares of its size, lands on ([x], [y]). */
fun Modifier.anchorAt(x: Dp, y: Dp, alignX: Float = 0.5f, alignY: Float = 1f): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place((x.toPx() - placeable.width * alignX).roundToInt(), (y.toPx() - placeable.height * alignY).roundToInt())
    }
}

/** How long a ripe crop's glint waits before it shines again. */
private const val GLINT_EVERY_MS = 2600L
