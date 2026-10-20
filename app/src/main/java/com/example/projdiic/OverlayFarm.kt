package com.example.projdiic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.delay

/**
 * The farm over Instagram, when nothing on the list is for now: the same farm as the app's home screen, with a way back
 * to the feed and a way to close the gate until tomorrow. Everything it does goes through [actions].
 */
@Composable
fun OverlayFarm(actions: GateOverlay.Actions) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskCache.getTasks(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tasks = TaskCache.getTasks(context)
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val farmer = remember {
        object : Farmer {
            override fun plant(title: String, window: TimeWindow?, via: String): Task? =
                actions.onTaskPlanted(title, window, via).also { tasks = TaskCache.getTasks(context) }

            override fun harvested(task: Task, ripe: Boolean) {
                actions.onFieldHarvested(task, ripe)
                tasks = TaskCache.getTasks(context)
            }

            override fun removed(task: Task) {
                actions.onTaskRemoved(task)
                tasks = TaskCache.getTasks(context)
            }
        }
    }
    Farm(
        tasks = tasks,
        now = now,
        farmer = farmer,
        hearing = RecognizerHearing,
        overFeed = true,
        header = {
            Text(
                text = if (tasks.isEmpty()) "What needs planting?" else "Nothing's ripe right now.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        buttons = { plant ->
            PlantButton(full = tasks.size >= TaskRules.MAX_TASKS, onPlant = plant)
            Button(
                onClick = actions::onAddTasksClosed,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(if (tasks.isEmpty()) "Not now" else "Back to the feed", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = actions::onSnoozeAppUntilTomorrow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Close the gate until tomorrow", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * The phone's speech recognizer, listening right on the screen: over Instagram, Android's voice screen can't open.
 * None without the microphone permission or a recognizer.
 */
val RecognizerHearing: Hearing = { onHeard ->
    val context = LocalContext.current
    val ready = remember { PermissionUtils.hasMicPermission(context) && SpeechRecognizer.isRecognitionAvailable(context) }
    if (ready) {
        val ear = remember { Ear(context) }
        DisposableEffect(ear) { onDispose { ear.stop() } }
        val latest by rememberUpdatedState(onHeard)
        Listen(ear.listening) { ear.toggle { latest(it) } }
    } else {
        null
    }
}

/** One recognizer at a time; a second tap stops it, even if it never answered. */
private class Ear(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    private var recognizer: SpeechRecognizer? = null

    fun toggle(onHeard: (String) -> Unit) {
        if (recognizer != null) return stop()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        listening = true
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                stop()
                if (heard.isNullOrBlank()) retry() else onHeard(heard)
            }

            override fun onError(error: Int) {
                stop()
                retry()
            }
        })
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        )
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun retry() = Toast.makeText(context, R.string.add_voice_retry, Toast.LENGTH_SHORT).show()
}

/**
 * What a Compose screen needs from its host, for an overlay window that has no activity to lend it: a lifecycle (on
 * while the window is up), saved state, and Back, which the window's root hands over ([OverlayRoot]).
 */
class OverlayOwner : SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
