package com.example.projdiic

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity

class TaskInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_input)

        val task1 = findViewById<EditText>(R.id.etTask1)
        val task2 = findViewById<EditText>(R.id.etTask2)
        val task3 = findViewById<EditText>(R.id.etTask3)
        val saveBtn = findViewById<Button>(R.id.btnSaveTasks)

        val existingTasks = TaskCache.getTasks(this)
        if (existingTasks.isNotEmpty()) task1.setText(existingTasks[0])
        if (existingTasks.size > 1) task2.setText(existingTasks[1])
        if (existingTasks.size > 2) task3.setText(existingTasks[2])

        saveBtn.setOnClickListener {
            val list = mutableListOf<String>()
            if (task1.text.isNotBlank()) list.add(task1.text.toString().trim())
            if (task2.text.isNotBlank()) list.add(task2.text.toString().trim())
            if (task3.text.isNotBlank()) list.add(task3.text.toString().trim())

            TaskCache.saveTasks(this, list)
            finish()
        }
    }
}
