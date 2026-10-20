package com.example.projdiic

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class FarmNewsTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val seen = GrowthNews.Seen(InstrumentationRegistry.getInstrumentation().targetContext)

    // The phone's own record of what was seen, put back after the test.
    private lateinit var before: Map<String, String>

    @Before
    fun keepSeen() {
        before = seen.get()
    }

    @After
    fun putSeenBack() {
        seen.set(before)
    }

    @Test
    fun aCropThatGrewIsToldOnce() {
        // Planted two days ago and due tonight: ripe today, last seen as a seed.
        val crop = Task(id = "news-test", title = "Pay the rent", createdAt = now - 2 * DAY_MS, dueAt = DayMath.endOfDay(now, TimeZone.getDefault()), whenKey = "custom")
        seen.set(mapOf(crop.id to "seed"))
        val told = mutableListOf<String>()
        val farmer = object : Farmer {
            override fun plant(title: String, window: TimeWindow?, via: String): Task? = null
            override fun harvested(task: Task, ripe: Boolean) {}
            override fun removed(task: Task) {}
            override fun watered(task: Task, via: String) {}
            override fun restored(task: Task, index: Int, after: String) = false
            override fun noticed(task: Task, stage: String) {
                told += "${task.id}:$stage"
            }
        }
        // The clock moves only when the test moves it: the drops bob forever.
        rule.mainClock.autoAdvance = false
        rule.setContent { Farm(listOf(crop), now, farmer, VoiceScreenHearing, buttons = { _, _, _ -> }) }
        repeat(5) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.advanceTimeBy(10_000L)

        assertEquals(listOf("news-test:ripe"), told)
        assertEquals("ripe", seen.get()[crop.id])
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
