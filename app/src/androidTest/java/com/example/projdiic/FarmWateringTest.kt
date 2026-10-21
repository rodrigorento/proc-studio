package com.example.projdiic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FarmWateringTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun crop(id: String, title: String) =
        Task(id = id, title = title, createdAt = now - DAY_MS, dueAt = now + 3 * DAY_MS, whenKey = "this_week")

    private var tasks by mutableStateOf(listOf(crop("a", "Call the dentist"), crop("b", "Finish the report")))
    private var cheered = false

    private val farmer = object : Farmer {
        override fun plant(title: String, window: TimeWindow?, via: String): Task? = null
        override fun harvested(task: Task, ripe: Boolean) {}
        override fun removed(task: Task) {}
        override fun restored(task: Task, index: Int, after: String) = false
        override fun noticed(task: Task, stage: String) {}
        override fun watered(task: Task, via: String) {
            tasks = tasks.map { if (it.id == task.id) TaskRules.water(it, System.currentTimeMillis()) else it }
        }
    }

    private fun showFarm() {
        // The clock moves only when the test moves it, so the can's length is exact.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Farm(tasks, now, farmer, VoiceScreenHearing, buttons = { _, _, celebration -> if (celebration != 0L) cheered = true })
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun wateringTheLastDryBedCelebrates() {
        showFarm()

        water("Call the dentist")
        pour()
        water("Finish the report")
        pour()

        assertTrue("watering the last dry bed should make the field sparkle", cheered)
    }

    @Test
    fun wateringBothBedsAtOnceStillCelebrates() {
        showFarm()

        // Two quick screen reader actions, before the field has drawn again.
        water("Call the dentist")
        water("Finish the report")
        pour()

        assertTrue("the field should sparkle once both cans have poured", cheered)
    }

    // The bed's screen reader action, as TalkBack's double tap: it waters as a tap on the bed does.
    private fun water(title: String) {
        rule.onNode(hasContentDescription(title, substring = true) and hasContentDescription("needs water", substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    // The can pours over the bed.
    private fun pour() = rule.mainClock.advanceTimeBy(CropMotion.water().size * 1000L / CropMotion.FPS + 500L)

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
