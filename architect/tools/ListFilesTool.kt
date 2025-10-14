package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

class ListFilesTool(private val project: Project) : ArchitectTool {
    override fun name() = "list_files"
    override fun description() = "Список файлов по маске glob (например **/*.kt). Аргументы: glob, limit."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "glob" to mapOf("type" to "string", "default" to "**/*"),
                    "limit" to mapOf("type" to "integer", "default" to 500)
                ),
                "required" to emptyList<String>()
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val base = project.basePath ?: return ToolResponse.error("Unknown project basePath")
        val glob = "\"glob\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "**/*"
        val limit = "\"limit\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toIntOrNull() ?: 500
        val root = File(base)
        val files = root.walkTopDown()
            .filter { it.isFile && it.relativeTo(root).invariantSeparatorsPath.matchesGlob(glob) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .take(limit)
            .toList()
        return ToolResponse.ok("""{"ok":true,"files":${files.toJsonArray()}}""", "Found: ${files.size}")
    }
}

private fun String.matchesGlob(glob: String): Boolean {
    val regex = glob.replace(".", "\\.")
        .replace("**", ".+")
        .replace("*", "[^/]*")
    return Regex("^$regex$").matches(this)
}
private fun List<String>.toJsonArray(): String = "[" + joinToString(",") { "\"" + it.replace("\\","\\\\") + "\"" } + "]"
