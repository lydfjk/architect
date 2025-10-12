package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class ReadFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "read_file"
    override fun description() = "Читает текстовый файл по относительному пути от корня проекта."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf("path" to mapOf("type" to "string")),
            "required" to listOf("path")
        ))
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val path = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("path?")
        val base = project.basePath ?: return ToolResponse.error("no project")
        val file = File(base, path)
        val text = if (file.exists()) file.readText() else return ToolResponse.error("not found")
        return ToolResponse.ok("""{"ok":true,"content":${text.trim().escapeJson()}}""", "Прочитано ${file.path}")
    }
}

class WriteFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "write_file"
    override fun description() = "Записывает текст в файл (создаёт при необходимости)."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string"),
                "content" to mapOf("type" to "string")
            ),
            "required" to listOf("path","content")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val path = "\"path\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("path?")
        val content = "\"content\"\\s*:\\s*\"(.*)\"".toRegex(RegexOption.DOT_MATCHES_ALL).find(jsonArgs)?.groupValues?.get(1)?.unescapeJson() ?: ""
        val base = project.basePath ?: return ToolResponse.error("no project")
        val file = File(base, path)
        WriteCommandAction.runWriteCommandAction(project) {
            file.parentFile?.mkdirs()
            file.writeText(content)
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.refresh(false, false)
        }
        return ToolResponse.ok("""{"ok":true}""", "Записано ${file.path}")
    }
}

class ListFilesTool(private val project: Project) : ArchitectTool {
    override fun name() = "list_files"
    override fun description() = "Выводит список файлов по маске в проекте."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "dir" to mapOf("type" to "string"),
                "glob" to mapOf("type" to "string")
            ),
            "required" to listOf("dir","glob")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val dir = "\"dir\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "."
        val glob = "\"glob\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "**/*.kt"
        val base = project.basePath ?: return ToolResponse.error("no project")
        val root = File(base, dir)
        val files = root.walkTopDown().filter { it.isFile && it.toPath().fileName.toString().matchesGlob(glob) }.map { it.relativeTo(File(base)).path }.take(500).toList()
        return ToolResponse.ok("""{"ok":true,"files":${files.toJsonArray()}}""", "Найдено: ${files.size}")
    }
}

// утилиты
private fun String.escapeJson() = "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
private fun String.unescapeJson() = this.replace("\\\"", "\"").replace("\\\\", "\\")
private fun String.matchesGlob(glob: String): Boolean {
    val regex = glob.replace(".", "\\.").replace("**", ".+").replace("*", "[^/]*")
    return Regex("^$regex$").matches(this)
}
private fun List<String>.toJsonArray(): String =
    "[" + this.joinToString(",") { "\"" + it.replace("\\", "\\\\") + "\"" } + "]"
