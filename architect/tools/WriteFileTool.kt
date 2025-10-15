package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

private data class WriteFileArgs(
    val path: String,
    val content: String = "",
    val append: Boolean? = null
)

class WriteFileTool(private val project: Project) : ArchitectTool {
    override fun name() = "write_file"
    override fun description() = "Создаёт/перезаписывает текстовый файл (path, content). Можно указать append=true для дозаписи. Путь — относительно корня проекта."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string"),
                    "content" to mapOf("type" to "string"),
                    "append" to mapOf(
                        "type" to "boolean",
                        "description" to "Если true — дозаписать в конец, иначе перезаписать файл"
                    )
                ),
                "required" to listOf("path", "content")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val args = parseToolArgs<WriteFileArgs>(jsonArgs).getOrElse {
            return ToolResponse.error("Invalid arguments: ${it.readableMessage()}")
        }

        val base = project.basePath ?: return ToolResponse.error("Unknown project basePath")
        val baseDir = File(base).canonicalFile
        val target = File(baseDir, args.path).canonicalFile
        if (!target.toPath().startsWith(baseDir.toPath())) {
            return ToolResponse.error("Path escapes project root")
        }

        val appendMode = args.append == true
        WriteCommandAction.runWriteCommandAction(project) {
            target.parentFile.mkdirs()
            if (appendMode && target.exists()) {
                target.appendText(args.content)
            } else {
                target.writeText(args.content)
            }
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)?.let {
                VfsUtil.markDirtyAndRefresh(true, false, false, it)
            }
        }
        val json = buildString {
            append("{\"ok\":true")
            append(",\"path\":${args.path.escapeJson()}")
            append(",\"append\":${if (appendMode) "true" else "false"}")
            append("}")
        }
        val message = if (appendMode) "Appended ${target.path}" else "Wrote ${target.path}"
        return ToolResponse.ok(json, message)
    }
}

