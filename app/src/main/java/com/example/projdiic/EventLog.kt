package com.example.projdiic

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every interaction, with a timestamp, kept in a file on the phone. It only
 * leaves the phone when the facilitator exports it.
 */
object EventLog {
    private const val TAG = "ScrollGate"
    private const val FILE_NAME = "scroll_gate_log.jsonl"
    private const val EXPORT_DIR = "exports"
    private val reserved = setOf("time", "iso", "event")
    private val lock = Any()

    enum class Format(val extension: String, val mimeType: String) {
        JSON("json", "application/json"),
        CSV("csv", "text/csv"),
    }

    fun log(context: Context, event: String, vararg fields: Pair<String, Any?>) {
        val now = System.currentTimeMillis()
        val json = JSONObject().put("time", now).put("iso", isoTime(now)).put("event", event)
        fields.forEach { (key, value) -> if (value != null) json.put(key, value.toString()) }
        Log.d(TAG, json.toString())
        synchronized(lock) {
            try {
                file(context).appendText(json.toString() + "\n")
            } catch (e: IOException) {
                Log.e(TAG, "Could not write to the log", e)
            }
        }
    }

    fun count(context: Context): Int = synchronized(lock) { lines(context).size }

    fun clear(context: Context) {
        synchronized(lock) { file(context).delete() }
    }

    /** Writes the log as a JSON or CSV file and opens the share sheet. */
    fun share(context: Context, format: Format) {
        val lines = synchronized(lock) { lines(context) }
        val text = when (format) {
            Format.JSON -> LogFormat.jsonArray(lines)
            Format.CSV -> LogFormat.toCsv(lines.mapNotNull(::parse), ::isoTime)
        }
        try {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val out = File(dir, "scroll-gate-log-$stamp.${format.extension}").apply { writeText(text) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
            val send = Intent(Intent.ACTION_SEND)
                .setType(format.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Export the log"))
        } catch (e: IOException) {
            Log.e(TAG, "Could not export the log", e)
            Toast.makeText(context, "Could not export the log.", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can receive the file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parse(line: String): LogEvent? = try {
        val json = JSONObject(line)
        val fields = buildMap {
            json.keys().forEach { key -> if (key !in reserved) put(key, json.optString(key)) }
        }
        LogEvent(json.getLong("time"), json.getString("event"), fields)
    } catch (e: JSONException) {
        null
    }

    private fun lines(context: Context): List<String> {
        val file = file(context)
        return if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun isoTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(time))
}
