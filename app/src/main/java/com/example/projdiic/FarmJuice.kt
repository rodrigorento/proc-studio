package com.example.projdiic

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.Bark
import com.example.projdiic.ui.theme.Leaf
import com.example.projdiic.ui.theme.Water
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// The farm's small touches: a drop over a thirsty bed, a glint on a ripe one, a sparkle when the day's watering is done,
// a splash as it's watered, a puff as a new bed lands, the crops swaying, a butterfly, the basket catching a harvest.
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
                val drop = dropPath(size)
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

/** A water drop's outline, point up, filling [size]. */
private fun dropPath(size: Size): Path = Path().apply {
    moveTo(size.width / 2, 0f)
    cubicTo(size.width * 0.95f, size.height * 0.45f, size.width, size.height * 0.62f, size.width / 2, size.height)
    cubicTo(0f, size.height * 0.62f, size.width * 0.05f, size.height * 0.45f, size.width / 2, 0f)
    close()
}

/** Drops of water springing off a bed as the can's water lands on it, once. */
@Composable
fun SplashBurst(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return
    val flight = remember { Animatable(0f) }
    LaunchedEffect(Unit) { flight.animateTo(1f, tween(SPLASH_MS, easing = LinearEasing)) }
    Box(
        modifier
            .size(width = 96.dp, height = 56.dp)
            .drawWithCache {
                val dropSize = Size(7.dp.toPx(), 9.dp.toPx())
                val drop = dropPath(dropSize)
                val outline = Stroke(width = 1.4.dp.toPx())
                onDrawBehind {
                    val t = flight.value
                    if (t >= 1f) return@onDrawBehind
                    val fade = 1f - t * t
                    // Out from the bed's middle, up and falling back, as thrown water does.
                    SPLASH.forEach { (angle, speed) ->
                        val x = size.width / 2 + cos(angle) * speed * t * size.width / 2
                        val y = size.height - sin(angle) * speed * t * size.height * 1.6f + t * t * size.height * 1.2f
                        translate(x - dropSize.width / 2, y - dropSize.height) {
                            drawPath(drop, Water.copy(alpha = fade))
                            drawPath(drop, Bark.copy(alpha = fade), style = outline)
                        }
                    }
                }
            }
    )
}

/** A puff of soil around a bed's foot as it lands on its plot, once per [key]; the box is the bed's footprint. */
@Composable
fun SoilPuff(key: Any, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return
    val puff = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { puff.animateTo(1f, tween(PUFF_MS, easing = FastOutSlowInEasing)) }
    Box(
        modifier.drawWithCache {
            val outline = Stroke(width = 1.5.dp.toPx())
            val radius = size.height * 0.16f
            onDrawBehind {
                val t = puff.value
                if (t >= 1f) return@onDrawBehind
                val fade = 1f - t
                // Along the front of the bed, where it meets the grass, spreading out as they rise: each a little cloud
                // of three, the outlines first so the cloud reads as one shape.
                PUFF_AT.forEachIndexed { i, angle ->
                    val reach = 0.75f + 0.45f * t
                    val at = Offset(
                        size.width / 2 + cos(angle) * size.width / 2 * reach,
                        size.height / 2 + sin(angle) * size.height / 2 * reach - t * size.height * 0.25f,
                    )
                    val r = radius * (0.55f + 0.75f * t) * (if (i % 2 == 0) 1.1f else 0.85f)
                    val cloud = listOf(at to r, at + Offset(-r * 0.75f, -r * 0.35f) to r * 0.62f, at + Offset(r * 0.7f, -r * 0.45f) to r * 0.5f)
                    cloud.forEach { (c, cr) -> drawCircle(Bark.copy(alpha = 0.45f * fade), cr, c, style = outline) }
                    cloud.forEach { (c, cr) -> drawCircle(Dust.copy(alpha = 0.95f * fade), cr - outline.width / 2, c) }
                }
            }
        }
    )
}

/**
 * A light that sweeps across now and then: 0 to 1 while it crosses, below 0 between, null with animations off. [phase]
 * staggers sweeps on different cards.
 */
@Composable
fun rememberShine(phase: Int): State<Float>? {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return null
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        delay(900L + phase * 600L)
        while (true) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            shine.snapTo(-1f)
            delay(SHINE_EVERY_MS)
        }
    }
    return shine.asState()
}

/** Draws [shine]'s band of light over what's drawn after it, inside [shape]. */
fun Modifier.shine(shine: State<Float>?, shape: Shape): Modifier = if (shine == null) this else clip(shape).drawWithContent {
    drawContent()
    val t = shine.value
    if (t < 0f) return@drawWithContent
    val band = size.width * 0.35f
    val x = -band + t * (size.width + 2 * band)
    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent), Offset(x - band, 0f), Offset(x, size.height)))
}

/**
 * The breeze every crop on the field sways in: a gust of a few sways as the field opens and now and then after,
 * resting between so the field isn't redrawn all the time. Its value runs from 0 to [GUST_SWAYS] through a gust and
 * rests at 0; null with animations off.
 */
@Composable
fun rememberBreeze(running: Boolean): State<Float>? {
    if (!running) return null
    val gust = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime())
        while (true) {
            gust.animateTo(GUST_SWAYS, tween((GUST_SWAYS * BREEZE_MS).toInt(), easing = LinearEasing))
            gust.snapTo(0f)
            delay(random.nextLong(14_000, 28_000))
        }
    }
    return gust.asState()
}

/**
 * Leans what is drawn above [line] (a share of the height: a bed's back corner on the grass) with [breeze], the more the
 * higher, so the crops sway and the bed stays put; [phase] (0 to 1) sets the beds apart.
 */
fun Modifier.sway(breeze: State<Float>?, phase: Float, line: Float): Modifier = if (breeze == null) this else drawWithContent {
    val pivot = size.height * line
    // Rising into the gust and dying away at its end.
    val strength = sin(PI * breeze.value / GUST_SWAYS).toFloat()
    val lean = SWAY * strength * sin(2 * PI * (breeze.value + phase)).toFloat()
    clipRect(top = pivot) { this@drawWithContent.drawContent() }
    clipRect(bottom = pivot) {
        // x moves by lean × (pivot − y): nothing on the line, most at the top.
        val shear = Matrix().apply {
            values[Matrix.SkewX] = -lean
            values[Matrix.TranslateX] = lean * pivot
        }
        withTransform({ transform(shear) }) { this@drawWithContent.drawContent() }
    }
}

/** Now and then a butterfly flutters across the field; never with animations off. Fills the field. */
@Composable
fun Butterfly(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember { motionOn(context) }) return
    val flight = remember { Animatable(0f) }
    var path by remember { mutableStateOf<Flight?>(null) }
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime())
        delay(random.nextLong(4_000, 9_000))
        while (true) {
            path = Flight(rightward = random.nextBoolean(), height = 0.18f + random.nextFloat() * 0.4f, wave = random.nextFloat() * 6f)
            flight.snapTo(0f)
            flight.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
            path = null
            delay(random.nextLong(18_000, 35_000))
        }
    }
    val current = path ?: return
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val wing = 13.dp.toPx()
                val outline = Stroke(width = 1.4.dp.toPx())
                val upper = Path().apply { addOval(Rect(-wing * 1.05f, -wing * 0.95f, -wing * 0.05f, wing * 0.05f)) }
                val lower = Path().apply { addOval(Rect(-wing * 0.8f, -wing * 0.1f, -wing * 0.05f, wing * 0.65f)) }
                onDrawBehind {
                    val t = flight.value
                    val across = if (current.rightward) t else 1f - t
                    val flap = abs(cos(t * FLAPS * 2 * PI)).toFloat()
                    val x = size.width * (-0.08f + 1.16f * across)
                    val y = size.height * (current.height + 0.05f * sin(across * 13f + current.wave)) - flap * 2.dp.toPx()
                    translate(x, y) {
                        rotate(if (current.rightward) 12f else -12f, pivot = Offset.Zero) {
                            // Both sides, each folding towards the body as the wings beat.
                            for (side in listOf(-1f, 1f)) {
                                scale(side * (0.25f + 0.75f * flap), 1f, pivot = Offset.Zero) {
                                    drawPath(upper, WingTop)
                                    drawPath(upper, Bark, style = outline)
                                    drawPath(lower, WingBottom)
                                    drawPath(lower, Bark, style = outline)
                                    drawCircle(Color.White, wing * 0.14f, Offset(-wing * 0.6f, -wing * 0.5f))
                                }
                            }
                            drawOval(Bark, Offset(-wing * 0.12f, -wing * 0.6f), Size(wing * 0.24f, wing * 1.2f))
                            drawLine(Bark, Offset(0f, -wing * 0.55f), Offset(-wing * 0.3f, -wing * 1.05f), outline.width)
                            drawLine(Bark, Offset(0f, -wing * 0.55f), Offset(wing * 0.3f, -wing * 1.05f), outline.width)
                        }
                    }
                }
            }
    )
}

/** One butterfly's way across: which way, how high (a share of the field), and where its waver starts. */
private class Flight(val rightward: Boolean, val height: Float, val wave: Float)

/**
 * The harvest basket popping up where the crops fly out of their bed, catching them; once they're all in ([caught]) it
 * wobbles, says "+1" and goes. Still, with animations off.
 */
@Composable
fun BasketCatch(caught: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val moving = remember { motionOn(context) }
    val grow = remember { Animatable(if (moving) 0f else 1f) }
    val tilt = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val away = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (moving) grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(caught) {
        if (!caught || !moving) return@LaunchedEffect
        launch {
            tilt.snapTo(10f)
            tilt.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = Spring.StiffnessMediumLow))
        }
        launch { rise.animateTo(1f, tween(BASKET_LINGER_MS.toInt() - 100)) }
        delay(BASKET_LINGER_MS - 320L)
        away.animateTo(0f, tween(260))
    }
    Box(modifier.size(64.dp)) {
        Image(
            painterResource(R.drawable.prop_basket),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = grow.value * (0.6f + 0.4f * away.value)
                scaleY = grow.value * (0.6f + 0.4f * away.value)
                rotationZ = tilt.value
                alpha = away.value
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            }
        )
        if (caught) {
            Text(
                "+1",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Leaf,
                modifier = Modifier.align(Alignment.TopEnd).clearAndSetSemantics {}.graphicsLayer {
                    translationY = -rise.value * 16.dp.toPx()
                    alpha = if (moving) 1f - rise.value * rise.value else 1f
                }
            )
        }
    }
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

/** How long the splash's drops fly. */
private const val SPLASH_MS = 520

/** The splash's drops: the angle each leaves at (radians, up is π/2) and how fast, as a share of the splash's box. */
private val SPLASH = listOf(0.35f to 0.9f, 0.9f to 0.7f, 1.35f to 0.55f, 1.8f to 0.65f, 2.3f to 0.75f, 2.8f to 0.95f)

/** How long a landing bed's puff of soil takes. */
private const val PUFF_MS = 560

/** Where the puffs rise around a bed's footprint: along its front half (radians, 0 to the right, π/2 to the front). */
private val PUFF_AT = listOf(0.15f, 0.8f, 1.57f, 2.35f, 3.0f)

/** The soil's dust: a light tan, as the dry soil is. */
private val Dust = Color(0xFFE2C59A)

/** How long a sweep of light waits before crossing a Harvest now pill again. */
private const val SHINE_EVERY_MS = 3600L

/** One sway of the breeze, there and back, and how many make a gust. */
private const val BREEZE_MS = 3400
private const val GUST_SWAYS = 2f

/** The most the crops lean: the shear at its peak. */
private const val SWAY = 0.06f

/** How long a butterfly takes to cross the field, and its wingbeats on the way. */
private const val FLIGHT_MS = 7000
private const val FLAPS = 38

/** The butterfly's wings: a sunny yellow over orange. */
private val WingTop = Color(0xFFFFCB45)
private val WingBottom = Color(0xFFFF9A3C)

/** How long the basket stays once the crops are in, its wobble and "+1" included. */
const val BASKET_LINGER_MS = 900L
