package ai.architect.ui

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
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

    fun sendUserMessage(text: String) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                val reply = client.chat(
                    userText = text,
                    toolSchemas = tools.schemas(),
                    systemPreamble = SYSTEM_PROMPT
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

    companion object {
        // Здесь фиксируем поведение: при неуверенности — обязательный web/MCP поиск
        private const val SYSTEM_PROMPT = """
      Ты — ИИ-ассистент Архитектор. Всегда работаешь с проектом IDE.
      Если задача требует действий — используй инструменты (tool calling).
      Если ты НЕ уверен в ответе или столкнулся с ошибкой — обязательно вызови инструмент web_search
      или MCP-документацию, затем продолжай с учетом найденных источников.
      Соблюдай политики ветвления: крупные правки — через feature-ветку и PR; мелкие — inline.
      Экономь токены: не цитируй длинные файлы целиком, используй краткие выжимки.
    """
    }
}
