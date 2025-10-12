package ai.architect.ui

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
import ai.architect.persona.ArchitectPersona
import ai.architect.tools.ToolResponse
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
    private val busy = AtomicBoolean(false)
    @Volatile
    private var persona: ArchitectPersona = ArchitectPersona.default()

    fun setPersona(persona: ArchitectPersona) {
        val previous = this.persona
        this.persona = persona
        if (previous.id != persona.id) {
            onAppend("Переключение профиля: ${persona.title}")
        }
    }

    fun sendUserMessage(text: String) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                val reply = client.chat(
                    userText = text,
                    toolSchemas = tools.schemas(),
                    systemPreamble = persona.systemPrompt()
                ) { toolName, jsonArgs ->
                    // Вызов инструмента по имени
                    val res: ToolResponse = tools.call(toolName, jsonArgs)
                    res.json // возвращаем строку с JSON tool_result
                }

                onAppend("Архитектор: $reply")
                // Автоэскалация в интернет, если модель "не уверена":
                if (client.lastWasUncertain(reply)) {
                    val auto = tools.call("web_search", """{"query":"$text","top_k":5}""")
                    onAppend("🔎 Поиск в интернете: ${auto.humanReadable}")
                }
            } catch (t: Throwable) {
                onAppend("Ошибка: ${t.message}")
            } finally {
                busy.set(false)
            }
        }
    }
}