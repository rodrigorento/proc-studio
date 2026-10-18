package com.example.projdiic

data class LogEvent(val time: Long, val type: String, val fields: Map<String, String> = emptyMap())

/** Turns the on-device log into the files the facilitator exports. */
object LogFormat {
    const val CSV_HEADER = "time,epoch_ms,event,task,details"

    fun toCsv(events: List<LogEvent>, formatTime: (Long) -> String): String = buildString {
        append(CSV_HEADER).append('\n')
        events.forEach { event ->
            val details = event.fields
                .filterKeys { it != "task" }
                .entries
                .joinToString("; ") { "${it.key}=${it.value}" }
            val cells = listOf(
                formatTime(event.time),
                event.time.toString(),
                event.type,
                event.fields["task"].orEmpty(),
                details,
            )
            append(cells.joinToString(",") { csvCell(it) }).append('\n')
        }
    }

    fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** The log keeps one JSON object per line; the export is one JSON array. */
    fun jsonArray(lines: List<String>): String =
        if (lines.isEmpty()) "[]" else lines.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]")
}
