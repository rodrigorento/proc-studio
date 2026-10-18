package com.example.projdiic

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

object TaskCache {
    private const val PREF_NAME = "task_cache_prefs"
    private const val KEY_TASKS = "tasks_json"

    // The first version kept up to three plain titles under these keys.
    private const val KEY_LEGACY_COUNT = "task_count"
    private const val KEY_LEGACY_PREFIX = "task_"

    fun getTasks(context: Context): List<Task> {
        val json = prefs(context).getString(KEY_TASKS, null) ?: return migrateLegacy(context)
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun saveTasks(context: Context, tasks: List<Task>) {
        val array = JSONArray()
        tasks.take(TaskRules.MAX_TASKS).forEach { array.put(toJson(it)) }
        prefs(context).edit().putString(KEY_TASKS, array.toString()).apply()
    }

    fun find(context: Context, id: String): Task? = getTasks(context).firstOrNull { it.id == id }

    /** Adds a task at the end of the list. Returns null when the title is blank or the list is full. */
    fun addTask(context: Context, rawTitle: String, via: String): Task? {
        val title = TaskRules.cleanTitle(rawTitle) ?: return null
        val tasks = getTasks(context)
        if (tasks.size >= TaskRules.MAX_TASKS) return null
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            addedVia = via,
        )
        saveTasks(context, tasks + task)
        return task
    }

    fun update(context: Context, task: Task) {
        saveTasks(context, getTasks(context).map { if (it.id == task.id) task else it })
    }

    fun remove(context: Context, id: String) {
        saveTasks(context, getTasks(context).filterNot { it.id == id })
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().putString(KEY_TASKS, "[]").apply()
    }

    private fun migrateLegacy(context: Context): List<Task> {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val tasks = (0 until prefs.getInt(KEY_LEGACY_COUNT, 0)).mapNotNull { i ->
            prefs.getString("$KEY_LEGACY_PREFIX$i", null)?.let { title ->
                Task(id = UUID.randomUUID().toString(), title = title, createdAt = now + i)
            }
        }
        saveTasks(context, tasks)
        return tasks
    }

    private fun toJson(task: Task) = JSONObject()
        .put("id", task.id)
        .put("title", task.title)
        .put("createdAt", task.createdAt)
        .put("addedVia", task.addedVia)
        .put("deferredUntil", task.deferredUntil)
        .put("deferredBy", task.deferredBy)
        .put("snoozeUsed", task.snoozeUsed)
        .put("passes", task.passes)

    private fun fromJson(json: JSONObject) = Task(
        id = json.getString("id"),
        title = json.getString("title"),
        createdAt = json.optLong("createdAt"),
        addedVia = json.optString("addedVia", Task.VIA_TEXT),
        deferredUntil = json.optLong("deferredUntil"),
        deferredBy = json.optString("deferredBy"),
        snoozeUsed = json.optBoolean("snoozeUsed"),
        passes = json.optInt("passes"),
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
