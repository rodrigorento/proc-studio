package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFormatTest {

    @Test
    fun csvCellQuotesOnlyWhenNeeded() {
        assertEquals("swipe", LogFormat.csvCell("swipe"))
        assertEquals("\"Reply, then call\"", LogFormat.csvCell("Reply, then call"))
        assertEquals("\"Say \"\"hi\"\"\"", LogFormat.csvCell("Say \"hi\""))
        assertEquals("\"two\nlines\"", LogFormat.csvCell("two\nlines"))
    }

    @Test
    fun csvHasOneRowPerEventWithTheTaskInItsOwnColumn() {
        val events = listOf(
            LogEvent(1_000, "gate_shown", mapOf("task" to "Dentist, 15:00", "trigger" to "swipes")),
            LogEvent(2_000, "choice", mapOf("choice" to "snooze")),
        )

        val csv = LogFormat.toCsv(events) { "t$it" }

        assertEquals(
            listOf(
                LogFormat.CSV_HEADER,
                "t1000,1000,gate_shown,\"Dentist, 15:00\",trigger=swipes",
                "t2000,2000,choice,,choice=snooze",
                "",
            ),
            csv.split("\n"),
        )
    }

    @Test
    fun jsonExportWrapsTheLinesInAnArray() {
        assertEquals("[\n{\"a\":1},\n{\"b\":2}\n]", LogFormat.jsonArray(listOf("{\"a\":1}", "{\"b\":2}")))
        assertEquals("[]", LogFormat.jsonArray(emptyList()))
    }
}
