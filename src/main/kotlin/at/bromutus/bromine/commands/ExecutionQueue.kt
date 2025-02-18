package at.bromutus.bromine.commands

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExecutionQueueInfo {
    private val tasks = mutableListOf<Task>()
    private val mutex = Mutex()

    private var _tgActive = false
    val isTextGenerationActive: Boolean
        get() = _tgActive

    suspend fun register(isTextGeneration: Boolean, onIndexChanged: suspend (Int) -> Unit, onRun: suspend () -> Unit) {
        val task = Task(isTextGeneration, onIndexChanged, onRun)
        val index = mutex.withLock {
            tasks.add(task)
            tasks.size - 1
        }
        task.updateAndMaybeRun(index, this::complete)
    }

    suspend fun complete() {
        val list = mutex.withLock {
            val task = tasks.removeFirst()
            _tgActive = task.isTextGeneration
            tasks.toList()
        }
        coroutineScope {
            list.mapIndexed { index, t -> async { t.updateAndMaybeRun(index) { complete() } } }.awaitAll()
        }
    }
}

suspend fun Task.updateAndMaybeRun(index: Int, onComplete: suspend () -> Unit): Unit = mutex.withLock {
    try {
        onIndexChanged(index)
    } catch (e: Exception) {
        // ignored
    }
    if (index == 0) {
        try {
            onRun()
        } finally {
            onComplete()
        }
    }
}

data class Task(
    val isTextGeneration: Boolean,
    val onIndexChanged: suspend (Int) -> Unit,
    val onRun: suspend () -> Unit,
    val mutex: Mutex = Mutex(),
)