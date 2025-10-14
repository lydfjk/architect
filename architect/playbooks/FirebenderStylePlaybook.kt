package ai.architect.playbooks

import ai.architect.core.ToolRegistry
import ai.architect.tools.ToolResponse
import com.intellij.openapi.project.Project

/**
 * Грубая имитация сценария «как у Firebender/ Copilot coding agent»:
 * 1) Создать ветку, 2) Сгенерить/применить патч, 3) Запустить тесты, 4) Закоммитить.
 * PR можно открыть через gh CLI (если установлен) или через REST.
 */
class FirebenderStylePlaybook : Playbook("fb_style", "Agent E2E: ветка → патч → тест → коммит") {
    override fun run(project: Project, tools: (String, String) -> ToolResponse): String {
        tools("git_branch", """{"name":"feat/agent-fix","worktree":true}""")
        // Здесь чат‑агент генерит unified diff как строку:
        val patch = """
            *** ПРИМЕР ***
            diff --git a/README.md b/README.md
            index 1111111..2222222 100644
            --- a/README.md
            +++ b/README.md
            @@ -1,1 +1,2 @@
            -Old
            +New
            +Line
        """.trimIndent()
        tools("apply_patch", """{"patch":${patch.replace("\n","\\n").replace("\"","\\\"").let { "\"$it\"" }}}""")
        val tests = tools("run_tests", """{"task":"test"}""")
        tools("git_commit", """{"message":"agent: apply patch & tests"}""")
        // Если есть GitHub CLI:
        // tools("run_command", """{"cmd":"gh pr create --fill"}""")
        return "Ветка, патч, тесты, коммит — выполнено.\n${tests.humanReadable}"
    }
}
