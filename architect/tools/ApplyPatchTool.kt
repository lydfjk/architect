package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.io.path.createTempFile

class ApplyPatchTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "apply_patch"

    override fun description() = "Применяет unified diff через git apply (с поддержкой --3way)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "patch" to mapOf("type" to "string"),
                    "three_way" to mapOf("type" to "boolean", "default" to true)
                ),
                "required" to listOf("patch")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val patchRaw = "\"patch\"\\s*:\\s*\"([\\s\\S]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("patch?")
        val patch = patchRaw.replace("\\n", "\n").replace("\\\"", "\"")
        val threeWay = "\"three_way\"\\s*:\\s*(true|false)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toBoolean() ?: true

        val base = project.basePath ?: return ToolResponse.error("no project")
        val root = File(base)
        val tempFile = createTempFile("architect", ".patch").toFile()
        tempFile.writeText(patch)

        return try {
            val args = buildList {
                add("git")
                add("apply")
                add("--whitespace=nowarn")
                if (threeWay) add("--3way")
                add(tempFile.absolutePath)
            }
            val result = runCommand(args, root)
            if (result.exitCode == 0) {
                ToolResponse.ok("""{"ok":true}""", "Патч применён")
            } else {
                val fallback = runCommand(listOf("patch", "-p1", "-i", tempFile.absolutePath), root)
                if (fallback.exitCode == 0) {
                    ToolResponse.ok("""{"ok":true,"fallback":"patch"}""", "Патч применён через patch")
                } else {
                    val message = (result.stderr + "\n" + fallback.stderr).trim()
                    ToolResponse.error(message.ifBlank { "git apply failed" })
                }
            }
        } finally {
            tempFile.delete()
        }
    }
}