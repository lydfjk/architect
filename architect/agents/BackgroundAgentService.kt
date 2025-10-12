package ai.architect.agents

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class BackgroundAgentService(private val project: Project) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Task(val priority: Int, val title: String, val instruction: String) : Comparable<Task> {
        override fun compareTo(other: Task) = other.priority.compareTo(priority) // больше — выше
    }

    private val queue = PriorityBlockingQueue<Task>()
    private val tools = ToolRegistry(project)
    private val client = DeepSeekClient(project)
    private val pumping = AtomicBoolean(false)

    /** Публичный API: положить задание в очередь */
    fun submit(priority: Int, title: String, instruction: String) {
        queue.offer(Task(priority, title, instruction))
        pump()
    }

    /** Фоновая откачка очереди — по одному заданию */
    private fun pump() {
        if (!pumping.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val t = withContext(Dispatchers.IO) { queue.take() }
                    runCatching {
                        client.chat(
                            userText = "Выполни задачу: ${t.instruction}",
                            toolSchemas = tools.schemas(),
                            systemPreamble = """
                                Ты фоновой агент. 
                                Делаешь большие изменения через git_branch(worktree=true) + git_commit. 
                                Если не уверен — сначала ищешь в интернете по официальным мануалам и Stack Overflow, 
                                затем применяешь исправления. 
                                В конце — краткий отчёт.
                            """.trimIndent()
                        ) { name, args -> tools.call(name, args).json }
                    }.onFailure {
                        // TODO: сюда можно добавить лог/уведомление об ошибке
                    }
                }
            } finally {
                pumping.set(false)
                // если за время работы накопились новые задачи — перезапустим насос
                if (queue.isNotEmpty()) pump()
            }
        }
    }

    override fun dispose() {
        scope.cancel("Project disposed")
    }
}