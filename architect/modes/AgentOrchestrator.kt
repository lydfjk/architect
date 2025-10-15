package ai.architect.modes

import ai.architect.core.DeepSeekClient
import ai.architect.core.ToolRegistry
import ai.architect.tools.ToolResponse
import com.intellij.openapi.project.Project

/**
 * Оркестратор: в зависимости от режима, пытается применить диффы, запустить тесты и открыть PR.
 * Вызывается из чат-контроллера после получения assistant-ответа.
 */
class AgentOrchestrator(
    private val project: Project,
    private val tools: ToolRegistry,
    private val client: DeepSeekClient
) {

    fun postProcessAssistantReply(assistantText: String) {
        when (AgentModeStore.getInstance().get()) {
            AgentMode.CHAT -> return // ничего не делаем
            AgentMode.APPLY -> applyIfPatch(assistantText, runTests = false, maybePr = false)
            AgentMode.RUN -> applyIfPatch(assistantText, runTests = true, maybePr = false)
            AgentMode.AUTO -> autoFlow(assistantText)
        }
    }

    private fun autoFlow(assistantText: String) {
        // 1) Если в ответе уже есть патч — применим, иначе попросим у модели дифф.
        val hadPatch = applyIfPatch(assistantText, runTests = false, maybePr = false)
        val finalPatch = if (hadPatch) null else generatePatchByModel()

        // 2) Применяем патч, если нужен
        if (finalPatch != null) applyPatch(finalPatch)

        // 3) Запускаем тесты
        val testRes = tools.call("run_tests", """{"task":"test"}""")

        // 4) Если упали — попросим модель исправить (1 итерация)
        if (!testRes.humanReadable.contains("BUILD SUCCESS", ignoreCase = true)) {
            val fix = client.chatOnce(
                system = "Ты — агент-разработчик. Найди и исправь причину падения тестов. Верни unified diff.",
                user = "Логи:\n${testRes.humanReadable.take(6000)}\n\nСгенерируй минимальный unified diff для исправления."
            )
            val diff = extractUnifiedDiff(fix)
            if (diff != null) {
                applyPatch(diff)
                tools.call("run_tests", """{"task":"test"}""")
            }
        }

        // 5) Коммит + PR
        tools.call("git_commit", """{"message":"agent: apply changes & tests"}""")
        val pr = tools.call("create_pr", """{
          "title":"Agent: implement changes",
          "body":"Автоматически создано режимом AUTO",
          "draft": false,
          "allow_update": true
        }""")
        notify("AUTO завершён.\n${pr.humanReadable}")
    }

    private fun applyIfPatch(text: String, runTests: Boolean, maybePr: Boolean): Boolean {
        val diff = extractUnifiedDiff(text) ?: return false
        applyPatch(diff)
        if (runTests) tools.call("run_tests", """{"task":"test"}""")
        if (maybePr) {
            tools.call("git_commit", """{"message":"agent: apply patch"}""")
            tools.call("create_pr", """{"title":"Agent patch","draft":true}""")
        }
        return true
    }

    private fun extractUnifiedDiff(text: String): String? {
        // простое извлечение блока unified diff
        val fence = Regex("```diff\\s+(.*?)```", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
        if (fence != null && fence.contains("diff --git")) return fence.trim()
        val raw = Regex("(?s)diff --git .*").find(text)?.value
        return raw?.trim()
    }

    private fun applyPatch(patch: String) {
        val payload = patch.replace("\n","\\n").replace("\"","\\\"")
        val res = tools.call("apply_patch", """{"patch":"$payload","three_way":true}""")
        notify(res.humanReadable)
    }

    private fun generatePatchByModel(): String? {
        val prompt = """
            Ты — senior-инженер DeepSeek. Пользователь просит внести изменения, но в предыдущем ответе не было diff.
            Сгенерируй минимальный unified diff (формат git diff --stat), который реализует запрос.
            Если изменений несколько — объединяй их в один diff.
            Если diff не нужен, верни пустую строку.
        """.trimIndent()

        val reply = client.chatOnce(
            system = prompt,
            user = "Сформируй unified diff в формате ```diff``` для текущего запроса."
        )
        return extractUnifiedDiff(reply)
    }

    private fun notify(msg: String) {
        // минимальное уведомление — IDE уведомления у тебя уже есть (см. контроллер/UI)
        println("[Architect] $msg")
    }
}

