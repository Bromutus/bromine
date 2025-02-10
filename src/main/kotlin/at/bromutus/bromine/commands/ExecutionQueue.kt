package at.bromutus.bromine.commands

import java.util.concurrent.ConcurrentLinkedQueue

class ExecutionQueueInfo {
    private val tasks = ConcurrentLinkedQueue<Task>()

    private var _tgActive = false
    val isTextGenerationActive: Boolean
        get() = _tgActive

    suspend fun register(isTextGeneration: Boolean, onIndexChanged: suspend (Int) -> Unit) {
        val task = Task(isTextGeneration, onIndexChanged)
        tasks.add(task)
        task.onIndexChanged(tasks.size - 1)
    }

    suspend fun complete() {
        val task = tasks.remove()!!
        _tgActive = task.isTextGeneration
        tasks.forEachIndexed { index, t -> t.onIndexChanged(index) }
    }
}

data class Task(
    val isTextGeneration: Boolean,
    val onIndexChanged: suspend (Int) -> Unit
)