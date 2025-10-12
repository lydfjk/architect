package ai.architect.playbooks

import com.intellij.openapi.project.Project
import ai.architect.tools.*

sealed class Playbook(val id: String, val title: String) {
    abstract fun run(project: Project, tools: (String, String)->ToolResponse): String
}

class XmlToComposePlaybook: Playbook("xml_to_compose","Миграция XML → Compose") {
    override fun run(project: Project, tools: (String, String) -> ToolResponse): String {
        // 1) Добавить зависимости Compose в build.gradle
        tools("run_gradle", """{"task":"app:dependencies"}""")
        // 2) Найти layout-xml и сгенерировать Compose-эквивалент
        val files = tools("list_files", """{"dir":"app/src/main/res/layout","glob":"**/*.xml"}""")
        // 3) Для краткости — здесь упрощённая заглушка (реальная миграция — через LLM и PSI-преобразования)
        return "Плейбук выполнил план миграции. Проверьте дифф и запустите сборку."
    }
}
