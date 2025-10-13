package ai.architect.ui

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
import ai.architect.core.toJsonString
import ai.architect.persona.ArchitectPersona
import ai.architect.tools.ToolResponse
import ai.architect.tools.toToolCallResult
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ArchitectChatController(
    private val project: Project,
    private val onAppend: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = DeepSeekClient(project)
    private val tools = ToolRegistry(project)
    private val toolSchemas by lazy { tools.schemas() }
    private val busy = AtomicBoolean(false)
    @Volatile
    private var persona: ArchitectPersona = ArchitectPersona.default()
    @Volatile
    private var conversation: MutableList<DeepSeekClient.Msg> =
        DeepSeekClient.newConversation(persona.systemPrompt())

    fun setPersona(persona: ArchitectPersona) {
        val previous = this.persona
        this.persona = persona
        conversation = DeepSeekClient.newConversation(persona.systemPrompt())
        if (previous.id != persona.id) {
            onAppend("Переключение профиля: ${persona.title}")
        }
    }

    fun sendUserMessage(text: String) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                conversation.add(DeepSeekClient.Msg(role = "user", content = text))
                val first = runChatTurn()
                appendAssistantMessages(first)

                var lastReply = first.appendedMessages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
                if (client.lastWasUncertain(lastReply)) {
                    val query = conversation.lastOrNull { it.role == "user" }?.content ?: text
                    val auto = tools.call("web_search", """{"query":${query.toJsonString()},"top_k":5}""")
                    onAppend("🔎 Автопоиск: ${auto.humanReadable}")

                    conversation.add(
                        DeepSeekClient.Msg(
                            role = "assistant",
                            content = "Результаты веб-поиска по запросу \"$query\":\n${auto.humanReadable}".trim()
                        )
                    )
                    conversation.add(
                        DeepSeekClient.Msg(
                            role = "user",
                            content = "С учётом найденных источников заверши решение и перечисли ссылки."
                        )
                    )

                    val followUp = runChatTurn()
                    appendAssistantMessages(followUp)
                    lastReply = followUp.appendedMessages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
                    if (client.lastWasUncertain(lastReply)) {
                        onAppend("⚠️ Модель по-прежнему не уверена. Попробуйте уточнить запрос.")
                    }
                }
            } catch (t: Throwable) {
                onAppend("Ошибка: ${t.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    private fun runChatTurn(): DeepSeekClient.ChatResult {
        val result = client.chat(
            conversation = conversation,
            toolSchemas = toolSchemas,
            onToolCall = { toolName, jsonArgs ->
                val res: ToolResponse = tools.call(toolName, jsonArgs)
                if (res.humanReadable.isNotBlank()) {
                    onAppend("⚙️ $toolName → ${res.humanReadable}")
                } else {
                    onAppend("⚙️ $toolName выполнен.")
                }
                res.toToolCallResult()
            }
        )
        conversation = result.conversation.toMutableList()
        return result
    }

    private fun appendAssistantMessages(result: DeepSeekClient.ChatResult) {
        result.appendedMessages
            .filter { it.role == "assistant" && it.content.isNotBlank() }
            .forEach { onAppend("Архитектор: ${it.content}") }
    }
}
