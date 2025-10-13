package ai.architect.agents

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
import ai.architect.core.toJsonString
import ai.architect.tools.toToolCallResult
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
                        val basePrompt = """
                            Ты фоновой агент.
                            Делаешь большие изменения через git_branch(worktree=true) + git_commit.
                            Если не уверен — сначала ищешь в интернете по официальным мануалам и Stack Overflow,
                            затем применяешь исправления.
                            В конце — краткий отчёт.
                        """.trimIndent()

                        var convo = DeepSeekClient.newConversation(basePrompt)
                        convo.add(DeepSeekClient.Msg(role = "user", content = "Выполни задачу: ${t.instruction}"))

                        val toolSchemas = tools.schemas()

                        var result = client.chat(
                            conversation = convo,
                            toolSchemas = toolSchemas,
                            onToolCall = { name, args -> tools.call(name, args).toToolCallResult() }
                        )

                        convo = result.conversation.toMutableList()

                        if (client.lastWasUncertain(result.reply)) {
                            val query = t.instruction
                            val search = tools.call(
                                "web_search",
                                """{"query":${query.toJsonString()},"top_k":5}"""
                            )
                            convo.add(
                                DeepSeekClient.Msg(
                                    role = "assistant",
                                    content = "Автопоиск подтверждает: ${search.humanReadable}".trim()
                                )
                            )
                            convo.add(
                                DeepSeekClient.Msg(
                                    role = "user",
                                    content = "С опорой на источники выше заверши задачу и перечисли ссылки."
                                )
                            )

                            result = client.chat(
                                conversation = convo,
                                toolSchemas = toolSchemas,
                                onToolCall = { name, args -> tools.call(name, args).toToolCallResult() }
                            )
                        }
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
