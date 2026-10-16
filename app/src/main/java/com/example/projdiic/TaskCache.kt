package com.example.projdiic

import android.content.Context

object TaskCache {
    private const val PREF_NAME = "task_cache_prefs"
    private const val KEY_TASK_COUNT = "task_count"
    private const val KEY_TASK_PREFIX = "task_"

    fun getTasks(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_TASK_COUNT, 0)
        val tasks = mutableListOf<String>()
        for (i in 0 until count) {
            prefs.getString("$KEY_TASK_PREFIX$i", null)?.let { tasks.add(it) }
        }
        return tasks
    }

    fun saveTasks(context: Context, tasks: List<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val limitedTasks = tasks.take(3)
        editor.putInt(KEY_TASK_COUNT, limitedTasks.size)
        for (i in 0..5) {
            editor.remove("$KEY_TASK_PREFIX$i")
        }
        limitedTasks.forEachIndexed { index, task ->
            editor.putString("$KEY_TASK_PREFIX$index", task)
        }
        editor.apply()
    }
}
