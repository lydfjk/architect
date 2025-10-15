package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import java.io.File
import com.intellij.openapi.project.Project

class ReadFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "read_file"
    override fun description() = "Читает текстовый файл по относительному пути от корня проекта."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("path" to mapOf("type" to "string")),
                "required" to listOf("path")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val path = "\"path\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("path?")
        val base = project.basePath ?: return ToolResponse.error("no project")
        val file = File(base, path)
        if (!file.exists()) return ToolResponse.error("not found")
        val text = file.readText()
        return ToolResponse.ok("""{"ok":true,"content":${text.trim().escapeJson()}}""", "Прочитано ${file.path}")
    }
}
