package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class WriteFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "write_file"
    override fun description() = "Создаёт/перезаписывает текстовый файл (path, content). Путь — относительно корня проекта."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string"),
                    "content" to mapOf("type" to "string")
                ),
                "required" to listOf("path", "content")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val path = "\"path\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("path required")
        val content = "\"content\"\\s*:\\s*\"([\\s\\S]*)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)?.unescapeJson()
            ?: ""

        val base = project.basePath ?: return ToolResponse.error("Unknown project basePath")
        val target = File(base, path).canonicalFile
        if (!target.path.startsWith(File(base).canonicalPath)) {
            return ToolResponse.error("Path escapes project root")
        }

        WriteCommandAction.runWriteCommandAction(project) {
            target.parentFile.mkdirs()
            target.writeText(content)
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)?.let {
                VfsUtil.markDirtyAndRefresh(true, false, false, it)
            }
        }
        return ToolResponse.ok("""{"ok":true,"path":${path.escapeJson()}}""", "Wrote $path")
    }
}
