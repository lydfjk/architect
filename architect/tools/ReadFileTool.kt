package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

private data class ReadFileArgs(
    val path: String,
    val offset: Int? = null,
    val limit: Int? = null
)

class ReadFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "read_file"
    override fun description() = "Читает текстовый файл по относительному пути от корня проекта. Опционально можно задать смещение (offset) и количество символов (limit)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string"),
                    "offset" to mapOf(
                        "type" to "integer",
                        "minimum" to 0,
                        "description" to "Символьное смещение, с которого начать чтение"
                    ),
                    "limit" to mapOf(
                        "type" to "integer",
                        "minimum" to 1,
                        "description" to "Максимальное количество символов для чтения"
                    )
                ),
                "required" to listOf("path")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val args = parseToolArgs<ReadFileArgs>(jsonArgs).getOrElse {
            return ToolResponse.error("Invalid arguments: ${it.readableMessage()}")
        }

        val base = project.basePath ?: return ToolResponse.error("Unknown project root")
        val baseDir = File(base).canonicalFile
        val target = File(baseDir, args.path).canonicalFile
        if (!target.toPath().startsWith(baseDir.toPath())) {
            return ToolResponse.error("Path escapes project root")
        }
        if (!target.exists() || !target.isFile) {
            return ToolResponse.error("File not found")
        }

        val content = target.readText()
        val fromIndex = args.offset?.takeIf { it >= 0 }?.coerceAtMost(content.length) ?: 0
        val rawLimit = args.limit?.takeIf { it > 0 }
        val toIndex = rawLimit?.let { (fromIndex + it).coerceAtMost(content.length) } ?: content.length
        val slice = content.substring(fromIndex, toIndex)
        val truncated = toIndex < content.length

        val json = buildString {
            append("{\"ok\":true")
            append(",\"path\":${args.path.escapeJson()}")
            append(",\"offset\":$fromIndex")
            append(",\"end_offset\":$toIndex")
            append(",\"truncated\":${if (truncated) "true" else "false"}")
            append(",\"content\":${slice.escapeJson()}}")
        }

        val human = buildString {
            append("Прочитано ")
            append(target.path)
            append(" [")
            append(fromIndex)
            append("..")
            append(toIndex)
            append(")")
            if (truncated) append(" (truncated)")
        }

        return ToolResponse.ok(json, human)
    }
}
